package ma.daba.canaux;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;

import ma.daba.canaux.Canaux.Canal;
import ma.daba.canaux.Canaux.Fournisseur;
import ma.daba.canaux.Canaux.JetonPushInvalide;
import ma.daba.canaux.Canaux.Message;
import tools.jackson.databind.JsonNode;

/**
 * Push par Firebase Cloud Messaging, API HTTP v1 :
 * {@code POST https://fcm.googleapis.com/v1/projects/{projectId}/messages:send}, jeton d'accès OAuth 2
 * d'un compte de service (portée firebase.messaging, Google Auth Library). Un envoi par jeton push du
 * destinataire ; accepté si au moins un appareil l'a reçu. Un jeton UNREGISTERED, ou INVALID_ARGUMENT
 * portant sur le jeton, est signalé ({@link JetonPushInvalide}) pour être effacé de l'appareil.
 */
@Component
class FournisseurFcm implements Fournisseur {

	private static final Logger log = LoggerFactory.getLogger(FournisseurFcm.class);

	static final String PORTEE = "https://www.googleapis.com/auth/firebase.messaging";

	private final String projet;
	private final String url;
	private final GoogleCredentials compte;
	private final ApplicationEventPublisher evenements;
	private final RestClient http;

	FournisseurFcm(@Value("${daba.canaux.fcm.project-id:}") String projet,
			@Value("${daba.canaux.fcm.credentials:}") String fichierCompte,
			@Value("${daba.canaux.fcm.url:https://fcm.googleapis.com}") String url, ApplicationEventPublisher evenements) {
		this.projet = projet.strip();
		this.url = url.replaceFirst("/+$", "");
		this.compte = charger(fichierCompte.strip());
		this.evenements = evenements;
		var fabrique = new SimpleClientHttpRequestFactory();
		fabrique.setConnectTimeout(Duration.ofSeconds(5));
		fabrique.setReadTimeout(Duration.ofSeconds(10));
		this.http = RestClient.builder().requestFactory(fabrique).build();
	}

	private static GoogleCredentials charger(String fichier) {
		if (fichier.isBlank()) {
			return null;
		}
		try (var flux = Files.newInputStream(Path.of(fichier))) {
			return ServiceAccountCredentials.fromStream(flux).createScoped(List.of(PORTEE));
		}
		catch (IOException | RuntimeException e) {
			log.error("Compte de service FCM illisible ({}) : push FCM non configuré", fichier);
			return null;
		}
	}

	@Override
	public Canal canal() {
		return Canal.PUSH;
	}

	@Override
	public Code code() {
		return Code.FCM;
	}

	@Override
	public boolean configure() {
		return compte != null && !projet.isBlank();
	}

	@Override
	public boolean envoyer(Message message) {
		String acces;
		try {
			compte.refreshIfExpired();
			acces = compte.getAccessToken().getTokenValue();
		}
		catch (IOException e) {
			throw new UncheckedIOException("Jeton d'accès FCM indisponible", e);
		}
		var recu = false;
		for (var jeton : message.destinataire().jetonsPush()) {
			recu |= envoyer(message, jeton, acces);
		}
		return recu;
	}

	private boolean envoyer(Message message, String jeton, String acces) {
		var data = new LinkedHashMap<String, String>();
		data.put("type", message.type().name());
		if (message.cibleId() != null) {
			data.put("cibleId", message.cibleId().toString());
		}
		var priorite = message.type().priorite;
		var corps = Map.of("message", Map.of("token", jeton,
				"notification", Map.of("title", message.titre(), "body", Canaux.corps(message.texte(), message.titre())),
				"data", data,
				"android", Map.of("priority", priorite.prioriteFcm, "notification", Map.of("channel_id", priorite.canalAndroid))));
		try {
			http.post().uri(url + "/v1/projects/{projet}/messages:send", projet)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + acces)
				.contentType(MediaType.APPLICATION_JSON)
				.body(corps)
				.retrieve().toBodilessEntity();
			return true;
		}
		catch (RestClientResponseException e) {
			JsonNode erreur;
			try {
				erreur = e.getResponseBodyAs(JsonNode.class);
			}
			catch (RuntimeException illisible) {
				erreur = null;
			}
			if (erreur != null && jetonInvalide(erreur.path("error"))) {
				log.info("Jeton push refusé par FCM ({}) : effacé de l'appareil", erreur.path("error").path("status").asString(""));
				evenements.publishEvent(new JetonPushInvalide(jeton));
			}
			else {
				log.warn("FCM a refusé le push {} : HTTP {}", message.type(), e.getStatusCode().value());
			}
			return false;
		}
		catch (RuntimeException e) {
			// Délai dépassé, connexion coupée : cet appareil seulement, les suivants sont tentés.
			log.warn("Push {} non remis à un appareil : {}", message.type(), e.getClass().getSimpleName());
			return false;
		}
	}

	/**
	 * UNREGISTERED : l'application a été désinstallée ou le jeton a expiré. INVALID_ARGUMENT ne vise le
	 * jeton que si l'erreur le dit (la même erreur couvre un contenu invalide, qui ne doit rien effacer).
	 */
	static boolean jetonInvalide(JsonNode erreur) {
		var details = StreamSupport.stream(erreur.path("details").spliterator(), false).toList();
		var codes = details.stream().map(d -> d.path("errorCode").asString("")).toList();
		if (codes.contains("UNREGISTERED") || "UNREGISTERED".equals(erreur.path("status").asString(""))) {
			return true;
		}
		var invalide = codes.contains("INVALID_ARGUMENT") || "INVALID_ARGUMENT".equals(erreur.path("status").asString(""));
		var surLeJeton = erreur.path("message").asString("").toLowerCase().contains("registration token")
				|| details.stream().flatMap(d -> StreamSupport.stream(d.path("fieldViolations").spliterator(), false))
					.anyMatch(v -> "message.token".equals(v.path("field").asString("")));
		return invalide && surLeJeton;
	}

}

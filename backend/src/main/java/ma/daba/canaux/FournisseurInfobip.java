package ma.daba.canaux;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import ma.daba.canaux.Canaux.Canal;
import ma.daba.canaux.Canaux.Fournisseur;
import ma.daba.canaux.Canaux.Message;
import tools.jackson.databind.JsonNode;

/**
 * SMS par Infobip, API SMS v3 : {@code POST {baseUrl}/sms/3/messages}, en-tête
 * {@code Authorization: App <clé>}, numéro international sans « + ». Base URL propre au compte
 * ({@code xxxxx.api.infobip.com}). Un refus (HTTP non 2xx, statut REJECTED, UNDELIVERABLE ou EXPIRED)
 * est un échec : le canal de repli prend le relais. Ni le numéro ni le texte (codes OTP) ne sont journalisés.
 */
@Component
class FournisseurInfobip implements Fournisseur {

	private static final Logger log = LoggerFactory.getLogger(FournisseurInfobip.class);

	/** Groupes de statut Infobip acceptés à l'envoi : 1 PENDING, 3 DELIVERED. */
	private static final List<Integer> ACCEPTES = List.of(1, 3);

	private final String baseUrl;
	private final String cle;
	private final String expediteur;
	private final RestClient http;

	FournisseurInfobip(@Value("${daba.canaux.infobip.base-url:}") String baseUrl,
			@Value("${daba.canaux.infobip.api-key:}") String cle, @Value("${daba.canaux.infobip.sender:}") String expediteur) {
		var url = baseUrl.isBlank() || baseUrl.contains("://") ? baseUrl.strip() : "https://" + baseUrl.strip();
		// La clé part dans l'en-tête : HTTPS obligatoire (HTTP toléré seulement en boucle locale, pour les tests).
		if (!url.isBlank() && !url.startsWith("https://")
				&& !(url.startsWith("http://") && java.util.Set.of("127.0.0.1", "localhost", "[::1]")
					.contains(String.valueOf(java.net.URI.create(url).getHost())))) {
			log.warn("Base URL Infobip refusée (HTTPS obligatoire) : SMS Infobip non configuré");
			url = "";
		}
		this.baseUrl = url;
		this.cle = cle.strip();
		this.expediteur = expediteur.strip();
		var fabrique = new SimpleClientHttpRequestFactory();
		fabrique.setConnectTimeout(Duration.ofSeconds(5));
		fabrique.setReadTimeout(Duration.ofSeconds(10));
		this.http = RestClient.builder().requestFactory(fabrique).build();
	}

	@Override
	public Canal canal() {
		return Canal.SMS;
	}

	@Override
	public Code code() {
		return Code.INFOBIP;
	}

	@Override
	public boolean configure() {
		return !baseUrl.isBlank() && !cle.isBlank();
	}

	@Override
	public boolean envoyer(Message message) {
		var sms = new LinkedHashMap<String, Object>();
		if (!expediteur.isBlank()) {
			sms.put("sender", expediteur);
		}
		sms.put("destinations", List.of(Map.of("to", message.destinataire().telephone().replaceFirst("^\\+", ""))));
		sms.put("content", Map.of("text", message.texte()));
		JsonNode reponse;
		try {
			reponse = http.post().uri(baseUrl.replaceFirst("/+$", "") + "/sms/3/messages")
				.header(HttpHeaders.AUTHORIZATION, "App " + cle)
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
				.body(Map.of("messages", List.of(sms)))
				.retrieve().body(JsonNode.class);
		}
		catch (RestClientResponseException e) {
			log.warn("Infobip a refusé le SMS {} : HTTP {}", message.type(), e.getStatusCode().value());
			return false;
		}
		var statut = reponse == null ? null : reponse.path("messages").path(0).path("status");
		if (statut == null || !ACCEPTES.contains(statut.path("groupId").asInt(-1))) {
			log.warn("Infobip a refusé le SMS {} : {}", message.type(), statut == null ? "réponse vide" : statut.path("name").asString(""));
			return false;
		}
		return true;
	}

}

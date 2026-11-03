package ma.daba.canaux;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import ma.daba.canaux.Canaux.Destinataire;
import ma.daba.canaux.Canaux.JetonPushInvalide;
import ma.daba.canaux.Canaux.Message;
import ma.daba.canaux.Canaux.TypeMessage;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Fournisseurs Infobip (SMS) et FCM (push) contre un faux serveur HTTP local. */
@org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
class FournisseursHttpTest {

	record Requete(String chemin, String autorisation, String corps) {
	}

	/** Réponse du faux serveur : statut et corps JSON. */
	record Reponse(int statut, String corps) {
	}

	private final JsonMapper json = JsonMapper.builder().build();
	private final List<Requete> requetes = new CopyOnWriteArrayList<>();
	private final Map<String, Function<Requete, Reponse>> routes = new ConcurrentHashMap<>();
	private HttpServer serveur;
	private String base;

	@BeforeEach
	void demarrer() throws IOException {
		serveur = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		serveur.createContext("/", this::repondre);
		serveur.start();
		base = "http://127.0.0.1:" + serveur.getAddress().getPort();
	}

	@AfterEach
	void arreter() {
		serveur.stop(0);
	}

	private void repondre(HttpExchange echange) throws IOException {
		var requete = new Requete(echange.getRequestURI().getPath(), echange.getRequestHeaders().getFirst("Authorization"),
				new String(echange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
		requetes.add(requete);
		var route = routes.getOrDefault(requete.chemin(), r -> new Reponse(404, "{}"));
		var reponse = route.apply(requete);
		var octets = reponse.corps().getBytes(StandardCharsets.UTF_8);
		echange.getResponseHeaders().add("Content-Type", "application/json");
		echange.sendResponseHeaders(reponse.statut(), octets.length);
		echange.getResponseBody().write(octets);
		echange.close();
	}

	private static Message message(TypeMessage type, UUID cible, List<String> jetons) {
		return new Message(type, cible, new Destinataire(UUID.randomUUID(), null, "+212612345678", jetons, "fr"), "Daba",
				"Daba : votre code est 123456.");
	}

	private JsonNode corps(Requete r) {
		return json.readTree(r.corps());
	}

	// ------------------------------------------------------------------ Infobip

	@Test
	void infobipEnvoieLeSmsAvecLaCleEtLeNumeroInternationalSansPlus() {
		routes.put("/sms/3/messages", r -> new Reponse(200, """
				{"bulkId":"b1","messages":[{"messageId":"m1","status":{"groupId":1,"groupName":"PENDING","id":26,
				"name":"PENDING_ACCEPTED"},"destination":"212612345678"}]}"""));
		var infobip = new FournisseurInfobip(base + "/", "cle-test", "Daba");
		assertThat(infobip.configure()).isTrue();
		assertThat(infobip.envoyer(message(TypeMessage.OTP, null, List.of()))).isTrue();

		var requete = requetes.getFirst();
		assertThat(requete.autorisation()).isEqualTo("App cle-test");
		var sms = corps(requete).path("messages").path(0);
		assertThat(sms.path("sender").asString()).isEqualTo("Daba");
		assertThat(sms.path("destinations").path(0).path("to").asString()).isEqualTo("212612345678");
		assertThat(sms.path("content").path("text").asString()).isEqualTo("Daba : votre code est 123456.");
	}

	@Test
	void infobipRefusOuErreurHttpEstUnEchecPourLeRepli() {
		var infobip = new FournisseurInfobip(base, "cle-test", "");
		routes.put("/sms/3/messages", r -> new Reponse(200, """
				{"messages":[{"status":{"groupId":5,"groupName":"REJECTED","id":12,"name":"REJECTED_NOT_ENOUGH_CREDITS"}}]}"""));
		assertThat(infobip.envoyer(message(TypeMessage.OTP, null, List.of()))).isFalse();
		assertThat(corps(requetes.getFirst()).path("messages").path(0).has("sender")).isFalse();

		routes.put("/sms/3/messages", r -> new Reponse(401, """
				{"requestError":{"serviceException":{"messageId":"UNAUTHORIZED","text":"Invalid login details"}}}"""));
		assertThat(infobip.envoyer(message(TypeMessage.OTP, null, List.of()))).isFalse();
	}

	@Test
	void infobipNonConfigureSansCleOuBaseUrl() {
		assertThat(new FournisseurInfobip("", "cle", "").configure()).isFalse();
		assertThat(new FournisseurInfobip("xxxxx.api.infobip.com", "", "").configure()).isFalse();
		assertThat(new FournisseurInfobip("xxxxx.api.infobip.com", "cle", "").configure()).isTrue();
		// La clé voyagerait en clair : HTTP refusé hors boucle locale.
		assertThat(new FournisseurInfobip("http://xxxxx.api.infobip.com", "cle", "").configure()).isFalse();
	}

	@Test
	void journalEcritLeTexteSeulementSansVraiFournisseurConfigure(org.springframework.boot.test.system.CapturedOutput sortie) {
		var dev = new FournisseursJournal.Journalise(Canaux.Canal.SMS, () -> true);
		assertThat(dev.envoyer(message(TypeMessage.OTP, null, List.of()))).isTrue();
		assertThat(sortie.getOut()).contains("[SMS] +212612345678 : Daba : votre code est 123456.");

		var production = new FournisseursJournal.Journalise(Canaux.Canal.SMS, () -> false);
		assertThat(production.envoyer(message(TypeMessage.OTP, null, List.of()))).isTrue();
		var ligne = sortie.getOut().lines().filter(l -> l.contains("texte non journalisé")).findFirst().orElseThrow();
		assertThat(ligne).contains("[SMS] +2126•••••678 : OTP").doesNotContain("123456");
	}

	// ---------------------------------------------------------------------- FCM

	/** Compte de service de test : vraie clé RSA, jeton d'accès demandé au faux serveur. */
	private String compteDeService(Path dossier) throws Exception {
		var generateur = KeyPairGenerator.getInstance("RSA");
		generateur.initialize(2048);
		var cle = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
			.encodeToString(generateur.generateKeyPair().getPrivate().getEncoded());
		var compte = Map.of("type", "service_account", "project_id", "daba-test", "private_key_id", "cle1",
				"private_key", "-----BEGIN PRIVATE KEY-----\n" + cle + "\n-----END PRIVATE KEY-----\n",
				"client_email", "fcm@daba-test.iam.gserviceaccount.com", "client_id", "1", "token_uri", base + "/token");
		var fichier = dossier.resolve("compte.json");
		Files.writeString(fichier, json.writeValueAsString(compte));
		routes.put("/token", r -> new Reponse(200, """
				{"access_token":"ya29.test","expires_in":3600,"token_type":"Bearer"}"""));
		return fichier.toString();
	}

	@Test
	void fcmEnvoieAChaqueJetonEtSignaleLesJetonsInvalides(@TempDir Path dossier) throws Exception {
		var invalides = new ArrayList<Object>();
		var fcm = new FournisseurFcm("daba-test", compteDeService(dossier), base, invalides::add);
		assertThat(fcm.configure()).isTrue();
		routes.put("/v1/projects/daba-test/messages:send", r -> switch (corps(r).path("message").path("token").asString()) {
			case "valide" -> new Reponse(200, "{\"name\":\"projects/daba-test/messages/1\"}");
			case "desinstalle" -> new Reponse(404, """
					{"error":{"code":404,"message":"Requested entity was not found.","status":"NOT_FOUND",
					"details":[{"@type":"type.googleapis.com/google.firebase.fcm.v1.FcmError","errorCode":"UNREGISTERED"}]}}""");
			default -> new Reponse(400, """
					{"error":{"code":400,"message":"The registration token is not a valid FCM registration token",
					"status":"INVALID_ARGUMENT","details":[{"@type":"type.googleapis.com/google.firebase.fcm.v1.FcmError",
					"errorCode":"INVALID_ARGUMENT"}]}}""");
		});
		var demande = UUID.randomUUID();
		assertThat(fcm.envoyer(message(TypeMessage.RUPTURE_SIGNALEE, demande, List.of("valide", "desinstalle", "casse")))).isTrue();
		assertThat(invalides).containsExactly(new JetonPushInvalide("desinstalle"), new JetonPushInvalide("casse"));
		var envois = requetes.stream().filter(r -> r.chemin().endsWith("messages:send")).toList();

		var envoi = envois.getFirst();
		assertThat(envoi.autorisation()).isEqualTo("Bearer ya29.test");
		var push = corps(envoi).path("message");
		assertThat(push.path("notification").path("title").asString()).isEqualTo("Daba");
		assertThat(push.path("notification").path("body").asString()).isEqualTo("votre code est 123456.");
		assertThat(push.path("data").path("type").asString()).isEqualTo("RUPTURE_SIGNALEE");
		assertThat(push.path("data").path("cibleId").asString()).isEqualTo(demande.toString());
		// Priorité §9 : rupture signalée = haute (notification en tête d'écran sur Android).
		assertThat(push.path("android").path("priority").asString()).isEqualTo("HIGH");
		assertThat(push.path("android").path("notification").path("channel_id").asString()).isEqualTo("haute");
		// Un seul jeton d'accès pour les trois envois (mis en cache jusqu'à expiration).
		assertThat(requetes.stream().filter(r -> r.chemin().equals("/token")).count()).isEqualTo(1);

		// Connexion coupée sur un appareil : les suivants sont tentés, un seul reçu suffit (pas de SMS de repli).
		routes.put("/v1/projects/daba-test/messages:send", r -> {
			if ("coupe".equals(corps(r).path("message").path("token").asString())) {
				throw new IllegalStateException("connexion coupée");
			}
			return new Reponse(200, "{}");
		});
		assertThat(fcm.envoyer(message(TypeMessage.RUPTURE_SIGNALEE, demande, List.of("coupe", "valide")))).isTrue();
		assertThat(fcm.envoyer(message(TypeMessage.RUPTURE_SIGNALEE, demande, List.of("coupe")))).isFalse();
	}

	@Test
	void fcmCanalAndroidSelonLaPrioriteDuTableau9(@TempDir Path dossier) throws Exception {
		var fcm = new FournisseurFcm("daba-test", compteDeService(dossier), base, e -> { });
		routes.put("/v1/projects/daba-test/messages:send", r -> new Reponse(200, "{}"));
		assertThat(fcm.envoyer(message(TypeMessage.RETRAIT_EFFECTUE, null, List.of("valide")))).isTrue();
		var android = corps(requetes.getLast()).path("message").path("android");
		assertThat(android.path("priority").asString()).isEqualTo("NORMAL");
		assertThat(android.path("notification").path("channel_id").asString()).isEqualTo("moyenne");
		assertThat(TypeMessage.PROMO_CONFIRMEE.priorite).isEqualTo(Canaux.Priorite.HAUTE);
		assertThat(TypeMessage.RESERVATION_EXPIRE_BIENTOT.priorite).isEqualTo(Canaux.Priorite.HAUTE);
		assertThat(TypeMessage.PROPOSITION_DEPOT.priorite).isEqualTo(Canaux.Priorite.HAUTE);
		assertThat(List.of(TypeMessage.DEMANDE_ACCEPTEE, TypeMessage.DEMANDE_EN_ROUTE, TypeMessage.DEMANDE_LIVREE))
			.allMatch(t -> t.priorite == Canaux.Priorite.HAUTE);
		assertThat(List.of(TypeMessage.DEMANDE_SANS_REPONSE, TypeMessage.DOCUMENT_EMIS, TypeMessage.DEMANDE_PRISE,
				TypeMessage.RECEPTION_ECART, TypeMessage.CONTESTATION)).allMatch(t -> t.priorite == Canaux.Priorite.MOYENNE);
	}

	@Test
	void fcmContenuInvalideOuPanneNEffacePasLeJeton(@TempDir Path dossier) throws Exception {
		var invalides = new ArrayList<Object>();
		var fcm = new FournisseurFcm("daba-test", compteDeService(dossier), base, invalides::add);
		routes.put("/v1/projects/daba-test/messages:send", r -> new Reponse(400, """
				{"error":{"code":400,"message":"Invalid value at 'message.data[0].value'","status":"INVALID_ARGUMENT",
				"details":[{"@type":"type.googleapis.com/google.rpc.BadRequest",
				"fieldViolations":[{"field":"message.data[0].value","description":"Invalid value"}]}]}}"""));
		assertThat(fcm.envoyer(message(TypeMessage.PROMO_CONFIRMEE, null, List.of("valide")))).isFalse();
		routes.put("/v1/projects/daba-test/messages:send", r -> new Reponse(503, "{\"error\":{\"status\":\"UNAVAILABLE\"}}"));
		assertThat(fcm.envoyer(message(TypeMessage.PROMO_CONFIRMEE, null, List.of("valide")))).isFalse();
		assertThat(invalides).isEmpty();
	}

	@Test
	void fcmNonConfigureSansProjetOuCompteDeServiceLisible(@TempDir Path dossier) throws Exception {
		assertThat(new FournisseurFcm("", "", base, e -> { }).configure()).isFalse();
		assertThat(new FournisseurFcm("daba-test", dossier.resolve("absent.json").toString(), base, e -> { }).configure()).isFalse();
		assertThat(new FournisseurFcm("", compteDeService(dossier), base, e -> { }).configure()).isFalse();
	}

}

package ma.daba.comptes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import ma.daba.TestIntegration;
import ma.daba.canaux.Canaux;
import tools.jackson.databind.JsonNode;

/** Connexion des portails web par mot de passe (§10, 3 novembre 2026) ; ambulants et hanouts restent sur le code SMS (§6). */
@ExtendWith(OutputCaptureExtension.class)
class MotDePasseTest extends TestIntegration {

	private static final String MDP = "sable teal zellige 2026";
	private static final String AUTRE_MDP = "encre et safran du souk";

	@Autowired
	MotsDePasse motsDePasse;

	@Autowired
	TransactionTemplate transactions;

	@AfterEach
	void configurationParDefaut() {
		jdbc.sql("DELETE FROM configuration_authentification").update();
	}

	// ---------------------------------------------------------------- outils

	private String codeEnvoye(String telephone) {
		var message = ArgumentCaptor.forClass(Canaux.Message.class);
		verify(sms, atLeastOnce()).envoyer(message.capture());
		return message.getAllValues().stream().filter(m -> telephone.equals(m.destinataire().telephone())).toList().getLast()
			.texte().replaceAll(".*code est ([0-9]{6}).*", "$1");
	}

	private String demanderCode(String telephone) {
		assertThat(post(null, "/v1/auth/otp", m("telephone", telephone))).hasStatus(202);
		return codeEnvoye(telephone);
	}

	private MvcTestResult verifierCode(String telephone, String code, UUID appareil, String defi) {
		return post(null, "/v1/auth/otp/verify", m("telephone", telephone, "code", code, "appareilId", appareil, "defi", defi));
	}

	private MvcTestResult connexion(String telephone, String motDePasse, UUID appareil) {
		return post(null, "/v1/auth/mot-de-passe", m("telephone", telephone, "motDePasse", motDePasse, "appareilId", appareil));
	}

	private MvcTestResult definition(String telephone, String code, String motDePasse, UUID appareil) {
		return post(null, "/v1/auth/mot-de-passe/definition",
				m("telephone", telephone, "code", code, "motDePasse", motDePasse, "appareilId", appareil));
	}

	/** Mot de passe défini par code SMS (première connexion) ; renvoie la session. */
	private JsonNode enroler(String telephone, String motDePasse) {
		var r = definition(telephone, demanderCode(telephone), motDePasse, UUID.randomUUID());
		assertThat(r).hasStatusOk();
		return lire(r);
	}

	private void configurer(Compte admin, Object roles, Object organisations) {
		assertThat(put(admin, "/v1/admin/authentification", m("roles", roles, "organisations", organisations))).hasStatusOk();
	}

	private static Object role(String role, String methode) {
		return m("role", role, "methode", methode);
	}

	private static Object organisation(UUID id, String methode) {
		return m("organisationId", id, "methode", methode);
	}

	/** Statut et code du Problem, ex. « 401 identifiants-invalides ». */
	private static String type(MvcTestResult r) {
		return r.getResponse().getStatus() + " " + typeDe(r);
	}

	private static String typeDe(MvcTestResult r) {
		try {
			var corps = r.getResponse().getContentAsString();
			return corps.replaceAll(".*\"type\":\"https://daba.ma/problemes/([a-z-]+)\".*", "$1");
		}
		catch (java.io.UnsupportedEncodingException e) {
			throw new IllegalStateException(e);
		}
	}

	private MvcTestResult moi(String accessToken) {
		return mvc.get().uri("/v1/moi/donnees").header("Authorization", "Bearer " + accessToken).exchange();
	}

	private static String jeton(JsonNode session) {
		return session.get("accessToken").asString();
	}

	private JsonNode utilisateurAdmin(Compte admin, Compte cible) {
		return lire(get(admin, "/v1/admin/utilisateurs?q=" + cible.telephone().substring(1))).get("contenu").get(0);
	}

	// ---------------------------------------------------------------- OTP par défaut, inchangé

	@Test
	void otpParDefautPourLesTroisRolesDePortailEtRienNeChange() {
		var admin = admin();
		var config = lire(get(admin, "/v1/admin/authentification"));
		assertThat(config.get("roles").toString())
			.contains("{\"role\":\"GROSSISTE\",\"methode\":\"OTP\"}", "{\"role\":\"GRAND_CLIENT\",\"methode\":\"OTP\"}",
					"{\"role\":\"ADMIN\",\"methode\":\"OTP\"}");
		assertThat(config.get("organisations")).isEmpty();

		var grossiste = grossiste();
		assertThat(verifierCode(grossiste.telephone(), demanderCode(grossiste.telephone()), UUID.randomUUID(), null))
			.hasStatusOk();
		// Pas de mot de passe : le même 401 qu'un numéro inconnu.
		assertThat(type(connexion(grossiste.telephone(), MDP, UUID.randomUUID()))).isEqualTo("401 identifiants-invalides");
		assertThat(utilisateurAdmin(admin, grossiste).get("methodeConnexion").asString()).isEqualTo("OTP");
		assertThat(utilisateurAdmin(admin, grossiste).get("motDePasseDefini").asBoolean()).isFalse();

		// Un mot de passe défini en méthode OTP ne connecte pas : le compte reste au code SMS.
		enroler(grossiste.telephone(), MDP);
		assertThat(type(connexion(grossiste.telephone(), MDP, UUID.randomUUID()))).isEqualTo("403 connexion-par-code");
		assertThat(verifierCode(grossiste.telephone(), demanderCode(grossiste.telephone()), UUID.randomUUID(), null))
			.hasStatusOk();
	}

	@Test
	void ambulantEtHanoutRestentSurLeCodeSmsQuelleQueSoitLaConfiguration() {
		var admin = admin();
		configurer(admin, List.of(role("GROSSISTE", "MOT_DE_PASSE"), role("GRAND_CLIENT", "MOT_DE_PASSE"),
				role("ADMIN", "MOT_DE_PASSE_ET_OTP")), List.of());
		var ambulant = ambulant();
		var hanout = compteHanout(hanout(33.5, -7.6));
		for (var c : List.of(ambulant, hanout)) {
			var session = verifierCode(c.telephone(), demanderCode(c.telephone()), UUID.randomUUID(), null);
			assertThat(session).hasStatusOk();
			assertThat(lire(session).get("utilisateur").has("motDePasse")).isFalse();
			assertThat(definition(c.telephone(), demanderCode(c.telephone()), MDP, UUID.randomUUID())).hasStatus(403).bodyJson()
				.extractingPath("$.type").asString().endsWith("mot-de-passe-non-applicable");
			assertThat(type(connexion(c.telephone(), MDP, UUID.randomUUID()))).isEqualTo("401 identifiants-invalides");
			assertThat(utilisateurAdmin(admin, c).get("methodeConnexion").asString()).isEqualTo("OTP");
			assertThat(post(admin, "/v1/admin/utilisateurs/" + c.id() + "/reinitialisation-mot-de-passe", null)).hasStatus(409);
		}
		assertThat(put(ambulant, "/v1/moi/mot-de-passe", m("motDePasseActuel", MDP, "nouveauMotDePasse", AUTRE_MDP)))
			.hasStatus(403).bodyJson().extractingPath("$.type").asString().endsWith("role-non-autorise");
		// La configuration refuse ces rôles et leurs organisations personnelles.
		assertThat(put(admin, "/v1/admin/authentification", m("roles", List.of(role("AMBULANT", "MOT_DE_PASSE")),
				"organisations", List.of()))).hasStatus(400);
		assertThat(put(admin, "/v1/admin/authentification", m("roles", List.of(), "organisations",
				List.of(organisation(hanout.organisationId(), "MOT_DE_PASSE"))))).hasStatus(400);
		// Et la base aussi.
		assertThatThrownBy(() -> jdbc.sql("UPDATE utilisateur SET mot_de_passe_hash = 'x' WHERE id = :u")
			.param("u", ambulant.id()).update()).hasMessageContaining("mot_de_passe_portails");
	}

	// ---------------------------------------------------------------- MOT_DE_PASSE

	@Test
	void motDePasse_premiereConnexionParCodePuisConnexionParMotDePasse(CapturedOutput sortie) throws Exception {
		var admin = admin();
		configurer(admin, List.of(role("GROSSISTE", "MOT_DE_PASSE")), List.of());
		var grossiste = grossiste();
		var tel = grossiste.telephone();

		// Le code seul ne connecte plus ; il reste valable pour définir le mot de passe.
		var code = demanderCode(tel);
		assertThat(type(verifierCode(tel, code, UUID.randomUUID(), null))).isEqualTo("403 mot-de-passe-a-definir");
		var appareil = UUID.randomUUID();
		var definie = definition(tel, code, MDP, appareil);
		assertThat(definie).hasStatusOk();
		var session = lire(definie);
		assertThat(session.get("utilisateur").get("id").asString()).isEqualTo(grossiste.id().toString());
		// Toutes les autres sessions du compte sont fermées.
		assertThat(get(grossiste, "/v1/moi/donnees")).hasStatus(401);
		assertThat(moi(jeton(session))).hasStatusOk();

		var connexion = connexion(tel, MDP, UUID.randomUUID());
		assertThat(connexion).hasStatusOk();
		var parMotDePasse = lire(connexion);
		assertThat(parMotDePasse.has("defi")).isFalse();
		assertThat(parMotDePasse.get("session").get("utilisateur").get("roles").get(0).asString()).isEqualTo("GROSSISTE");
		assertThat(post(null, "/v1/auth/refresh", m("refreshToken", parMotDePasse.get("session").get("refreshToken").asString())))
			.hasStatusOk();
		assertThat(type(connexion(tel, AUTRE_MDP, UUID.randomUUID()))).isEqualTo("401 identifiants-invalides");
		// Avec un mot de passe défini, le code seul répond « requis ».
		assertThat(type(verifierCode(tel, demanderCode(tel), UUID.randomUUID(), null))).isEqualTo("403 mot-de-passe-requis");

		// CNDP : l'export dit qu'un mot de passe existe et quand il a changé, jamais l'empreinte.
		var export = moi(jeton(parMotDePasse.get("session")));
		assertThat(export).bodyJson().extractingPath("$.utilisateur.mot_de_passe_defini").isEqualTo(true);
		assertThat(lire(export).get("utilisateur").get("mot_de_passe_modifie_le").isNull()).isFalse();
		var fiche = utilisateurAdmin(admin, grossiste);
		assertThat(fiche.get("motDePasseDefini").asBoolean()).isTrue();
		assertThat(fiche.get("methodeConnexion").asString()).isEqualTo("MOT_DE_PASSE");
		for (var corps : List.of(export.getResponse().getContentAsString(), fiche.toString(), connexion.getResponse().getContentAsString(),
				definie.getResponse().getContentAsString(), get(admin, "/v1/admin/audit").getResponse().getContentAsString())) {
			assertThat(corps).doesNotContain("argon2", MDP, "mot_de_passe_hash", "motDePasseHash");
		}
		assertThat(jdbc.sql("SELECT mot_de_passe_hash FROM utilisateur WHERE id = :u").param("u", grossiste.id())
			.query(String.class).single()).startsWith("$argon2id$v=19$m=19456,t=2,p=1$");
		// Jamais dans les logs.
		assertThat(sortie.getAll()).doesNotContain(MDP);
	}

	// ---------------------------------------------------------------- MOT_DE_PASSE_ET_OTP

	@Test
	void motDePasseEtOtp_leDefiDuMotDePassePuisLeCodeSms() {
		var admin = admin();
		var marque = grandClient();
		var tel = marque.telephone();
		// Exception de l'organisation (le rôle reste en OTP).
		configurer(admin, List.of(), List.of(organisation(marque.organisationId(), "MOT_DE_PASSE_ET_OTP")));
		assertThat(type(verifierCode(tel, demanderCode(tel), UUID.randomUUID(), null))).isEqualTo("403 mot-de-passe-a-definir");
		enroler(tel, MDP);

		var appareil = UUID.randomUUID();
		var etape = connexion(tel, MDP, appareil);
		assertThat(etape).hasStatusOk();
		var defi = lire(etape);
		assertThat(defi.has("session")).isFalse();
		assertThat(defi.get("expireDans").asInt()).isEqualTo(300);
		var code = codeEnvoye(tel);

		// Le code sans le défi, ou le défi sur un autre appareil, ne suffit pas (et le code reste valable).
		assertThat(type(verifierCode(tel, code, appareil, null))).isEqualTo("403 mot-de-passe-requis");
		assertThat(type(verifierCode(tel, code, UUID.randomUUID(), defi.get("defi").asString())))
			.isEqualTo("403 mot-de-passe-requis");
		var session = verifierCode(tel, code, appareil, defi.get("defi").asString());
		assertThat(session).hasStatusOk();
		assertThat(lire(session).get("utilisateur").get("id").asString()).isEqualTo(marque.id().toString());
		// Code et défi consommés ensemble : ils ne resservent pas.
		assertThat(type(verifierCode(tel, code, appareil, defi.get("defi").asString()))).isEqualTo("401 code-invalide");

		// Un défi devient caduc quand le mot de passe change.
		var ancien = lire(connexion(tel, MDP, appareil)).get("defi").asString();
		var code2 = codeEnvoye(tel);
		assertThat(put(new Compte(marque.id(), marque.organisationId(), appareil, tel, jeton(lire(session)), null, null),
				"/v1/moi/mot-de-passe", m("motDePasseActuel", MDP, "nouveauMotDePasse", AUTRE_MDP))).hasStatus(204);
		assertThat(type(verifierCode(tel, code2, appareil, ancien))).isEqualTo("403 mot-de-passe-requis");
	}

	@Test
	void exceptionDeLOrganisationPrioritaireSurLeDefautDuRole() {
		var admin = admin();
		var a = grossiste();
		var b = grossiste();
		configurer(admin, List.of(role("GROSSISTE", "MOT_DE_PASSE")), List.of(organisation(a.organisationId(), "OTP")));
		assertThat(verifierCode(a.telephone(), demanderCode(a.telephone()), UUID.randomUUID(), null)).hasStatusOk();
		assertThat(type(verifierCode(b.telephone(), demanderCode(b.telephone()), UUID.randomUUID(), null)))
			.isEqualTo("403 mot-de-passe-a-definir");
		assertThat(utilisateurAdmin(admin, a).get("methodeConnexion").asString()).isEqualTo("OTP");
		assertThat(utilisateurAdmin(admin, b).get("methodeConnexion").asString()).isEqualTo("MOT_DE_PASSE");
		var config = lire(get(admin, "/v1/admin/authentification"));
		assertThat(config.get("organisations").toString()).contains(a.organisationId().toString());
		assertThat(config.get("roles").toString()).contains("{\"role\":\"GROSSISTE\",\"methode\":\"MOT_DE_PASSE\"}",
				"{\"role\":\"ADMIN\",\"methode\":\"OTP\"}");
	}

	// ---------------------------------------------------------------- oubli, changement, blocage

	@Test
	void motDePasseOublie_codeSmsPuisNouveauMotDePasseFermeLesAutresSessions() {
		var admin = admin();
		configurer(admin, List.of(role("GRAND_CLIENT", "MOT_DE_PASSE")), List.of());
		var marque = grandClient();
		var tel = marque.telephone();
		var premiere = enroler(tel, MDP);
		var autre = lire(connexion(tel, MDP, UUID.randomUUID())).get("session");

		var nouvelle = enroler(tel, AUTRE_MDP);
		assertThat(moi(jeton(premiere))).hasStatus(401);
		assertThat(moi(jeton(autre))).hasStatus(401);
		assertThat(moi(jeton(nouvelle))).hasStatusOk();
		assertThat(type(connexion(tel, MDP, UUID.randomUUID()))).isEqualTo("401 identifiants-invalides");
		assertThat(connexion(tel, AUTRE_MDP, UUID.randomUUID())).hasStatusOk();
		assertThat(lire(get(admin, "/v1/admin/audit?operation=MOT_DE_PASSE_DEFINI&auteurId=" + marque.id())).get("total").asLong())
			.isEqualTo(2);
	}

	@Test
	void changementDuMotDePasseExigeLActuelEtFermeLesAutresSessions() {
		var admin = admin();
		configurer(admin, List.of(role("ADMIN", "MOT_DE_PASSE")), List.of());
		var autreAdmin = admin();
		// Sans mot de passe : 409, passer par le code SMS.
		assertThat(put(autreAdmin, "/v1/moi/mot-de-passe", m("motDePasseActuel", MDP, "nouveauMotDePasse", AUTRE_MDP)))
			.hasStatus(409).bodyJson().extractingPath("$.type").asString().endsWith("mot-de-passe-absent");
		var ici = enroler(autreAdmin.telephone(), MDP);
		var ailleurs = lire(connexion(autreAdmin.telephone(), MDP, UUID.randomUUID())).get("session");
		var appelant = new Compte(autreAdmin.id(), autreAdmin.organisationId(), null, autreAdmin.telephone(), jeton(ici), null, null);

		assertThat(type(put(appelant, "/v1/moi/mot-de-passe", m("motDePasseActuel", AUTRE_MDP, "nouveauMotDePasse", AUTRE_MDP))))
			.isEqualTo("403 identifiants-invalides");
		assertThat(type(put(appelant, "/v1/moi/mot-de-passe", m("motDePasseActuel", MDP, "nouveauMotDePasse", "court"))))
			.isEqualTo("422 mot-de-passe-trop-court");
		assertThat(put(appelant, "/v1/moi/mot-de-passe", m("motDePasseActuel", MDP, "nouveauMotDePasse", AUTRE_MDP))).hasStatus(204);
		// La session de l'appel reste ouverte, l'autre se ferme.
		assertThat(get(appelant, "/v1/admin/authentification")).hasStatusOk();
		assertThat(mvc.get().uri("/v1/admin/authentification").header("Authorization", "Bearer " + jeton(ailleurs)).exchange())
			.hasStatus(401);
		assertThat(type(connexion(autreAdmin.telephone(), MDP, UUID.randomUUID()))).isEqualTo("401 identifiants-invalides");
		assertThat(connexion(autreAdmin.telephone(), AUTRE_MDP, UUID.randomUUID())).hasStatusOk();
		assertThat(lire(get(admin, "/v1/admin/audit?operation=MOT_DE_PASSE_MODIFIE&auteurId=" + autreAdmin.id())).get("total")
			.asLong()).isEqualTo(1);
	}

	@Test
	void ralentissementProgressifPourToutNumeroSansBloquerLeNavigateurHabituel() {
		configurer(admin(), List.of(role("GROSSISTE", "MOT_DE_PASSE")), List.of());
		var tel = grossiste().telephone();
		var habituel = UUID.randomUUID();
		assertThat(definition(tel, demanderCode(tel), MDP, habituel)).hasStatusOk();
		var attaquant = UUID.randomUUID();
		for (int i = 0; i < MotsDePasse.ECHECS_LIBRES; i++) {
			assertThat(type(connexion(tel, AUTRE_MDP, attaquant))).isEqualTo("401 identifiants-invalides");
		}
		// Après 5 échecs : 30 s d'attente, même avec le bon mot de passe, annoncée par Retry-After.
		var attente = connexion(tel, MDP, attaquant);
		assertThat(type(attente)).isEqualTo("429 trop-de-demandes");
		assertThat(Integer.parseInt(attente.getResponse().getHeader("Retry-After"))).isBetween(1, 30);
		// Le navigateur habituel du titulaire n'est pas ralenti par l'attaquant.
		assertThat(connexion(tel, MDP, habituel)).hasStatusOk();

		// Après l'attente, un nouvel échec double l'attente (60 s), plafonnée à 15 min.
		passerAttente(tel);
		assertThat(type(connexion(tel, AUTRE_MDP, attaquant))).isEqualTo("401 identifiants-invalides");
		assertThat(attenteRestante(tel)).isBetween(55L, 60L);
		assertThat(Integer.parseInt(connexion(tel, MDP, attaquant).getResponse().getHeader("Retry-After"))).isBetween(31, 60);
		jdbc.sql("UPDATE tentative_mot_de_passe SET echecs = 40, prochain_essai = now() - interval '1 second' WHERE telephone = :t")
			.param("t", tel).update();
		assertThat(type(connexion(tel, AUTRE_MDP, attaquant))).isEqualTo("401 identifiants-invalides");
		assertThat(attenteRestante(tel)).isBetween(890L, 900L);
		// Après l'attente, le bon mot de passe passe et remet le compteur à zéro.
		passerAttente(tel);
		assertThat(connexion(tel, MDP, attaquant)).hasStatusOk();
		assertThat(jdbc.sql("SELECT count(*) FROM tentative_mot_de_passe WHERE telephone = :t AND appareil = :a")
			.param("t", tel).param("a", MotsDePasse.APPAREIL_INCONNU).query(Long.class).single()).isZero();

		// Un numéro inconnu est ralenti de la même façon (pas d'énumération).
		var inconnu = telephone();
		for (int i = 0; i < MotsDePasse.ECHECS_LIBRES; i++) {
			assertThat(type(connexion(inconnu, AUTRE_MDP, attaquant))).isEqualTo("401 identifiants-invalides");
		}
		assertThat(type(connexion(inconnu, MDP, attaquant))).isEqualTo("429 trop-de-demandes");

		// Le mot de passe oublié par code SMS remet tous les compteurs du numéro à zéro.
		for (int i = 0; i < MotsDePasse.ECHECS_LIBRES; i++) {
			connexion(tel, AUTRE_MDP, attaquant);
		}
		enroler(tel, AUTRE_MDP);
		assertThat(connexion(tel, AUTRE_MDP, attaquant)).hasStatusOk();
	}

	private void passerAttente(String telephone) {
		jdbc.sql("UPDATE tentative_mot_de_passe SET prochain_essai = now() - interval '1 second' WHERE telephone = :t")
			.param("t", telephone).update();
	}

	private long attenteRestante(String telephone) {
		return jdbc.sql("""
				SELECT extract(epoch FROM prochain_essai - now())::bigint FROM tentative_mot_de_passe
				WHERE telephone = :t AND appareil = :a
				""").param("t", telephone).param("a", MotsDePasse.APPAREIL_INCONNU).query(Long.class).single();
	}

	@Test
	void motDePasseEtOtp_defiLieAuCodeEnvoiRefuseSansEcritureEtEssaisJamaisRemisAZero() {
		var admin = admin();
		var marque = grandClient();
		var tel = marque.telephone();
		configurer(admin, List.of(), List.of(organisation(marque.organisationId(), "MOT_DE_PASSE_ET_OTP")));
		enroler(tel, MDP); // code consommé : la ligne du code (et son compteur d'envois) disparaît
		var appareil = UUID.randomUUID();

		// Un nouveau défi rend caduc le précédent : un défi ne vaut qu'avec le code qu'il a fait envoyer.
		var d1 = lire(connexion(tel, MDP, appareil)).get("defi").asString(); // 1er code de l'heure
		var d2 = lire(connexion(tel, MDP, appareil)).get("defi").asString(); // 2e code
		var code = codeEnvoye(tel);
		assertThat(type(verifierCode(tel, code, appareil, d1))).isEqualTo("403 mot-de-passe-requis");

		// L'attaque : 5 essais par code, puis un nouveau défi pour remettre les essais à zéro.
		for (int i = 0; i < Authentification.ESSAIS_MAX; i++) {
			assertThat(type(verifierCode(tel, faux(code), appareil, d2))).isEqualTo("401 code-invalide");
		}
		assertThat(type(verifierCode(tel, code, appareil, d2))).isEqualTo("429 trop-de-demandes");
		lire(connexion(tel, MDP, appareil)); // 3e code
		lire(connexion(tel, MDP, appareil)); // 4e code
		var d5 = lire(connexion(tel, MDP, appareil)).get("defi").asString(); // 5e code : la limite de l'heure
		var code5 = codeEnvoye(tel);
		for (int i = 0; i < Authentification.ESSAIS_MAX; i++) {
			verifierCode(tel, faux(code5), appareil, d5);
		}
		var avant = otp(tel);
		// 6e envoi refusé : rien n'est écrit, le code en cours garde ses essais épuisés et son défi.
		assertThat(type(connexion(tel, MDP, appareil))).isEqualTo("429 trop-de-demandes");
		assertThat(post(null, "/v1/auth/otp", m("telephone", tel))).hasStatus(429);
		assertThat(otp(tel)).isEqualTo(avant);
		assertThat(type(verifierCode(tel, code5, appareil, d5))).isEqualTo("429 trop-de-demandes");
		// Au total, au plus ESSAIS_MAX essais par code envoyé, et ENVOIS_MAX_PAR_HEURE codes par heure.
		assertThat(avant.get("essais")).isEqualTo(Authentification.ESSAIS_MAX);
	}

	private static String faux(String code) {
		return "%06d".formatted((Integer.parseInt(code) + 1) % 1_000_000);
	}

	private java.util.Map<String, Object> otp(String telephone) {
		return jdbc.sql("SELECT code_hash, essais, defi, expire_le, envois FROM otp WHERE telephone = :t").param("t", telephone)
			.query().singleRow();
	}

	@Test
	void calculsArgon2PlafonnesSansConnexionRetenueEtMemeReponsePourTous() throws Exception {
		configurer(admin(), List.of(role("GROSSISTE", "MOT_DE_PASSE")), List.of());
		var connu = grossiste();
		enroler(connu.telephone(), MDP);
		var inconnu = telephone();
		var permis = motsDePasse.calculs().availablePermits();
		motsDePasse.calculs().acquire(permis);
		try {
			var reponses = new java.util.HashSet<String>();
			for (var tel : List.of(inconnu, connu.telephone())) {
				var r = connexion(tel, MDP, UUID.randomUUID());
				assertThat(r).hasStatus(429).hasHeader("Retry-After", "1");
				reponses.add(r.getResponse().getContentAsString());
			}
			assertThat(reponses).hasSize(1);
			// Saturé : aucun échec compté, même pour le bon mot de passe.
			assertThat(jdbc.sql("SELECT count(*) FROM tentative_mot_de_passe WHERE telephone IN (:t)")
				.param("t", List.of(inconnu, connu.telephone())).query(Long.class).single()).isZero();
		}
		finally {
			motsDePasse.calculs().release(permis);
		}
		assertThat(connexion(connu.telephone(), MDP, UUID.randomUUID())).hasStatusOk();
		// Jamais de calcul Argon2 dans une transaction (il y retiendrait une connexion du pool).
		assertThatThrownBy(() -> transactions.execute(s -> motsDePasse.verifier(inconnu, MDP, null)))
			.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> transactions.execute(s -> motsDePasse.hacher(MDP))).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void fermerSessionsFermeLesComptesDontLaMethodeDevientPlusStricteSaufLAppelant() {
		var admin = admin();
		var autreAdmin = admin();
		var grossiste = grossiste();
		var marque = grandClient();
		// Par défaut, une nouvelle méthode ne ferme aucune session.
		configurer(admin, List.of(role("GRAND_CLIENT", "MOT_DE_PASSE")), List.of());
		assertThat(get(marque, "/v1/moi/donnees")).hasStatusOk();

		var enregistree = put(admin, "/v1/admin/authentification", m("roles", List.of(role("GROSSISTE", "MOT_DE_PASSE"),
				role("GRAND_CLIENT", "MOT_DE_PASSE"), role("ADMIN", "MOT_DE_PASSE_ET_OTP")), "organisations", List.of(),
				"fermerSessions", true));
		assertThat(enregistree).hasStatusOk();
		assertThat(lire(enregistree).has("fermerSessions")).isFalse();
		assertThat(get(grossiste, "/v1/moi/donnees")).hasStatus(401); // OTP → MOT_DE_PASSE
		assertThat(get(autreAdmin, "/v1/admin/audit")).hasStatus(401); // OTP → MOT_DE_PASSE_ET_OTP
		assertThat(get(marque, "/v1/moi/donnees")).hasStatusOk(); // inchangée
		assertThat(get(admin, "/v1/admin/audit")).hasStatusOk(); // l'appelant reste connecté
		var details = lire(get(admin, "/v1/admin/audit?operation=CONFIGURATION_AUTHENTIFICATION&auteurId=" + admin.id()))
			.get("contenu").get(0).get("details");
		assertThat(details.get("fermerSessions").asBoolean()).isTrue();
		assertThat(details.get("sessionsFermees").toString()).contains(grossiste.id().toString(), autreAdmin.id().toString())
			.doesNotContain(marque.id().toString());
		assertThat(MotsDePasse.Methode.OTP.plusStricteQue(MotsDePasse.Methode.MOT_DE_PASSE_ET_OTP)).isFalse();
		assertThat(MotsDePasse.Methode.OTP.plusStricteQue(MotsDePasse.Methode.MOT_DE_PASSE)).isTrue();
	}

	@Test
	void cndp_compteursDEssaisPurgesVingtQuatreHeuresApresLeDernierEssai() {
		var ancien = telephone();
		var recent = telephone();
		jdbc.sql("""
				INSERT INTO tentative_mot_de_passe (telephone, appareil, echecs, modifie_le) VALUES
				  (:a, :i, 3, now() - interval '25 hours'), (:r, :i, 1, now() - interval '1 hour')
				""").param("a", ancien).param("r", recent).param("i", MotsDePasse.APPAREIL_INCONNU).update();
		motsDePasse.purger();
		assertThat(jdbc.sql("SELECT telephone FROM tentative_mot_de_passe WHERE telephone IN (:t)")
			.param("t", List.of(ancien, recent)).query(String.class).list()).containsExactly(recent);
	}

	@Test
	void politiqueLongueurTelephoneEtMotsDePasseCourantsSansRegleDeComposition() {
		var grossiste = grossiste();
		var tel = grossiste.telephone();
		var code = demanderCode(tel);
		var appareil = UUID.randomUUID();
		assertThat(type(definition(tel, code, "onze-carac", appareil))).isEqualTo("422 mot-de-passe-trop-court");
		assertThat(type(definition(tel, code, "quatorze carac", appareil))).isEqualTo("422 mot-de-passe-trop-court");
		assertThat(type(definition(tel, code, "a".repeat(129), appareil))).isEqualTo("422 mot-de-passe-trop-long");
		assertThat(type(definition(tel, code, tel, appareil))).isEqualTo("422 mot-de-passe-trop-court");
		var local = tel.substring(4);
		assertThat(type(definition(tel, code, "+212 " + local.substring(0, 3) + " " + local.substring(3, 6) + " " + local.substring(6),
				appareil))).isEqualTo("422 mot-de-passe-telephone");
		assertThat(type(definition(tel, code, "0" + tel.substring(4), appareil))).isEqualTo("422 mot-de-passe-trop-court");
		assertThat(type(definition(tel, code, "00212 " + tel.substring(4), appareil))).isEqualTo("422 mot-de-passe-telephone");
		assertThat(type(definition(tel, code, "MotDePasseMotDePasse", appareil))).isEqualTo("422 mot-de-passe-courant");
		assertThat(type(definition(tel, code, "123456789012345", appareil))).isEqualTo("422 mot-de-passe-courant");
		// Triviaux : un motif court répété, une suite de caractères consécutifs.
		for (var trivial : List.of("aaaaaaaaaaaaaaaa", "abababababababab", "abcdabcdabcdabcd", "abcdefghijklmnop",
				"ponmlkjihgfedcba", "ABCDEFGHIJKLMNOP")) {
			assertThat(type(definition(tel, code, trivial, appareil))).as(trivial).isEqualTo("422 mot-de-passe-courant");
		}
		// 17 points de code en arabe (30 octets UTF-8) : accepté, sans majuscule ni chiffre ; le code n'a pas été consommé.
		assertThat(definition(tel, code, "كلمة سر طويلة جدا", appareil)).hasStatusOk();
		assertThat(connexion(tel, "كلمة سر طويلة جدا", UUID.randomUUID())).hasStatus(403); // méthode OTP par défaut
		// NFC : « é » composé ou décomposé, c'est le même mot de passe.
		enroler(tel, "café au lait du matin");
		configurer(admin(), List.of(role("GROSSISTE", "MOT_DE_PASSE")), List.of());
		assertThat(connexion(tel, "café au lait du matin", UUID.randomUUID())).hasStatusOk();
	}

	// ---------------------------------------------------------------- pas d'énumération

	@Test
	void pasDEnumeration_inconnuAmbulantSansMotDePasseOuMauvaisMotDePasseMemeReponse() throws Exception {
		configurer(admin(), List.of(role("GROSSISTE", "MOT_DE_PASSE"), role("GRAND_CLIENT", "MOT_DE_PASSE_ET_OTP")), List.of());
		var avecMotDePasse = grossiste();
		enroler(avecMotDePasse.telephone(), MDP);
		var numeros = List.of(telephone(), ambulant().telephone(), compteHanout(hanout(33.5, -7.6)).telephone(),
				grossiste().telephone(), grandClient().telephone(), avecMotDePasse.telephone());
		var reponses = new java.util.HashSet<String>();
		var appareil = UUID.randomUUID();
		for (var tel : numeros) {
			var r = connexion(tel, AUTRE_MDP, appareil);
			reponses.add(r.getResponse().getStatus() + " " + r.getResponse().getContentAsString());
		}
		assertThat(reponses).hasSize(1);
		assertThat(reponses.iterator().next()).startsWith("401 ").contains("identifiants-invalides");

		// Premier pas par code : 202 identique pour tout numéro.
		var otp = new java.util.HashSet<String>();
		for (var tel : List.of(telephone(), avecMotDePasse.telephone())) {
			var r = post(null, "/v1/auth/otp", m("telephone", tel));
			otp.add(r.getResponse().getStatus() + " " + r.getResponse().getContentAsString());
		}
		assertThat(otp).hasSize(1);

		// Même coût Argon2id pour un numéro inconnu que pour un mauvais mot de passe (à un facteur près, sans attendre l'égalité).
		var inconnu = telephone();
		connexion(inconnu, AUTRE_MDP, appareil);
		connexion(avecMotDePasse.telephone(), AUTRE_MDP, appareil);
		long t0 = System.nanoTime();
		connexion(inconnu, AUTRE_MDP, appareil);
		long dureeInconnu = System.nanoTime() - t0;
		t0 = System.nanoTime();
		connexion(avecMotDePasse.telephone(), AUTRE_MDP, appareil);
		long dureeConnu = System.nanoTime() - t0;
		assertThat(dureeInconnu).isGreaterThan(dureeConnu / 4);
	}

	// ---------------------------------------------------------------- administration

	@Test
	void configurationReserveeALAdminValideeAuditeeEtSansDeconnecterLAdmin() {
		var admin = admin();
		var grossiste = grossiste();
		for (var autre : List.of(ambulant(), grossiste, grandClient(), compteHanout(hanout(33.5, -7.6)))) {
			assertThat(get(autre, "/v1/admin/authentification")).hasStatus(403);
			assertThat(put(autre, "/v1/admin/authentification", m("roles", List.of(), "organisations", List.of()))).hasStatus(403);
		}
		assertThat(put(admin, "/v1/admin/authentification", m("roles", List.of(role("ADMIN", "OTP"), role("ADMIN", "MOT_DE_PASSE")),
				"organisations", List.of()))).hasStatus(400);
		assertThat(put(admin, "/v1/admin/authentification", m("roles", List.of(), "organisations",
				List.of(organisation(grossiste.organisationId(), "OTP"), organisation(grossiste.organisationId(), "MOT_DE_PASSE")))))
			.hasStatus(400);
		assertThat(put(admin, "/v1/admin/authentification", m("roles", List.of(), "organisations",
				List.of(organisation(UUID.randomUUID(), "OTP"))))).hasStatus(400);
		assertThat(put(admin, "/v1/admin/authentification", m("roles", List.of(), "organisations",
				List.of(organisation(admin.organisationId(), "OTP"))))).hasStatus(400);
		assertThat(put(admin, "/v1/admin/authentification", m("roles", List.of(role("ADMIN", "BIOMETRIE")), "organisations",
				List.of()))).hasStatus(400);

		// L'administrateur passe les administrateurs au mot de passe : il reste connecté.
		configurer(admin, List.of(role("ADMIN", "MOT_DE_PASSE")), List.of());
		assertThat(get(admin, "/v1/admin/authentification")).hasStatusOk();
		var entree = lire(get(admin, "/v1/admin/audit?operation=CONFIGURATION_AUTHENTIFICATION&auteurId=" + admin.id()))
			.get("contenu").get(0);
		assertThat(entree.get("details").get("roles").toString()).contains("MOT_DE_PASSE");
		// À sa prochaine connexion, il définit son mot de passe par code SMS.
		assertThat(type(verifierCode(admin.telephone(), demanderCode(admin.telephone()), UUID.randomUUID(), null)))
			.isEqualTo("403 mot-de-passe-a-definir");
		assertThat(lire(get(admin, "/v1/admin/authentification")).get("roles").toString())
			.contains("{\"role\":\"ADMIN\",\"methode\":\"MOT_DE_PASSE\"}");
		var session = enroler(admin.telephone(), MDP);
		assertThat(session.get("utilisateur").get("roles").get(0).asString()).isEqualTo("ADMIN");
	}

	@Test
	void reinitialisationParLAdminEffaceLeMotDePasseFermeLesSessionsEtEstAuditee() {
		var admin = admin();
		configurer(admin, List.of(role("GROSSISTE", "MOT_DE_PASSE")), List.of());
		var grossiste = grossiste();
		var session = enroler(grossiste.telephone(), MDP);

		var r = post(admin, "/v1/admin/utilisateurs/" + grossiste.id() + "/reinitialisation-mot-de-passe", null);
		assertThat(r).hasStatusOk();
		assertThat(lire(r).get("motDePasseDefini").asBoolean()).isFalse();
		assertThat(lire(r).get("motDePasseModifieLe").isNull()).isFalse();
		assertThat(moi(jeton(session))).hasStatus(401);
		assertThat(type(connexion(grossiste.telephone(), MDP, UUID.randomUUID()))).isEqualTo("401 identifiants-invalides");
		var entree = lire(get(admin, "/v1/admin/audit?operation=MOT_DE_PASSE_REINITIALISE&auteurId=" + admin.id())).get("contenu")
			.get(0);
		assertThat(entree.get("details").get("utilisateurId").asString()).isEqualTo(grossiste.id().toString());
		assertThat(entree.toString()).doesNotContain(grossiste.telephone());
		assertThat(post(admin, "/v1/admin/utilisateurs/" + UUID.randomUUID() + "/reinitialisation-mot-de-passe", null))
			.hasStatus(404);
		assertThat(post(grossiste, "/v1/admin/utilisateurs/" + grossiste.id() + "/reinitialisation-mot-de-passe", null))
			.hasStatus(403);
		// Il redéfinit un mot de passe par code SMS.
		assertThat(type(verifierCode(grossiste.telephone(), demanderCode(grossiste.telephone()), UUID.randomUUID(), null)))
			.isEqualTo("403 mot-de-passe-a-definir");
		enroler(grossiste.telephone(), AUTRE_MDP);
		assertThat(connexion(grossiste.telephone(), AUTRE_MDP, UUID.randomUUID())).hasStatusOk();
	}

}

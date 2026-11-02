package ma.daba.comptes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;

import ma.daba.TestIntegration;
import ma.daba.canaux.Canaux;

class ComptesTest extends TestIntegration {

	@Autowired
	Comptes comptes;

	private String codeEnvoye(String telephone) {
		var texte = ArgumentCaptor.forClass(String.class);
		verify(sms, org.mockito.Mockito.atLeastOnce())
			.envoyer(argThat((Canaux.Destinataire d) -> telephone.equals(d.telephone())), texte.capture());
		return texte.getValue().replaceAll(".*code est ([0-9]{6}).*", "$1");
	}

	@Test
	void inscriptionParOtpCreeUnAmbulantAvecPrefixeDeNumerotation() {
		var tel = telephone();
		assertThat(post(null, "/v1/auth/otp", m("telephone", tel))).hasStatus(202).bodyJson()
			.extractingPath("$.expireDans").isEqualTo(300);
		var appareil = UUID.randomUUID();
		var session = lire(post(null, "/v1/auth/otp/verify", m("telephone", tel, "code", codeEnvoye(tel), "appareilId", appareil)));
		assertThat(session.get("utilisateur").get("roles").get(0).asString()).isEqualTo("AMBULANT");
		assertThat(session.get("utilisateur").get("nouveau").asBoolean()).isTrue();
		assertThat(session.get("prefixeNumerotation").asString()).matches("^[A-Z0-9]{3,6}$");

		// Le préfixe est stable pour le même appareil.
		post(null, "/v1/auth/otp", m("telephone", tel));
		var seconde = lire(post(null, "/v1/auth/otp/verify", m("telephone", tel, "code", codeEnvoye(tel), "appareilId", appareil)));
		assertThat(seconde.get("prefixeNumerotation").asString()).isEqualTo(session.get("prefixeNumerotation").asString());
		assertThat(seconde.get("utilisateur").get("nouveau").asBoolean()).isFalse();

		// Le refreshToken rouvre une session.
		assertThat(post(null, "/v1/auth/refresh", m("refreshToken", seconde.get("refreshToken").asString()))).hasStatusOk();
	}

	@Test
	void codeFauxRefuseEtEssaisLimites() {
		var tel = telephone();
		post(null, "/v1/auth/otp", m("telephone", tel));
		var verif = m("telephone", tel, "code", "000000", "appareilId", UUID.randomUUID());
		for (int i = 0; i < Authentification.ESSAIS_MAX; i++) {
			assertThat(post(null, "/v1/auth/otp/verify", verif)).hasStatus(401);
		}
		assertThat(post(null, "/v1/auth/otp/verify", verif)).hasStatus(429);
	}

	@Test
	void premiereConnexionDuTelephoneDUnHanoutDonneLeRoleHanout() {
		var hanoutId = hanout(33.58, -7.60);
		var tel = jdbc.sql("SELECT telephone FROM hanout WHERE id = :id").param("id", hanoutId).query(String.class).single();
		post(null, "/v1/auth/otp", m("telephone", tel));
		var session = lire(post(null, "/v1/auth/otp/verify", m("telephone", tel, "code", codeEnvoye(tel), "appareilId", UUID.randomUUID())));
		assertThat(session.get("utilisateur").get("roles").get(0).asString()).isEqualTo("HANOUT");
		assertThat(session.get("utilisateur").get("hanoutId").asString()).isEqualTo(hanoutId.toString());
		assertThat(session.has("prefixeNumerotation")).isFalse();
	}

	@Test
	void sansJetonReponse401EtMauvaisRole403() {
		assertThat(get(null, "/v1/hanouts")).hasStatus(401);
		var hanout = compteHanout(hanout(33.5, -7.6));
		assertThat(get(hanout, "/v1/hanouts")).hasStatus(403).bodyJson().extractingPath("$.type")
			.asString().endsWith("role-non-autorise");
	}

	@Test
	void consentementCndpExigeAvantToutAppel() {
		var ambulant = ambulant();
		jdbc.sql("INSERT INTO consentement (utilisateur_id, version_texte, traitement_donnees) VALUES (:u, 'v2', false)")
			.param("u", ambulant.id()).update();
		assertThat(get(ambulant, "/v1/produits")).hasStatus(403).bodyJson().extractingPath("$.type")
			.asString().endsWith("consentement-requis");
		// Le droit d'accès et le consentement restent ouverts.
		assertThat(get(ambulant, "/v1/moi/donnees")).hasStatusOk();
		assertThat(put(ambulant, "/v1/moi/consentement", m("versionTexte", "v2", "traitementDonnees", true, "geolocalisation", false)))
			.hasStatusOk().bodyJson().extractingPath("$.geolocalisation").isEqualTo(false);
		assertThat(get(ambulant, "/v1/produits")).hasStatusOk();
		assertThat(comptes.geolocalisationConsentie(ambulant.id())).isFalse();
	}

	@Test
	void appareilRevoqueRefuse() {
		var ambulant = ambulant();
		jdbc.sql("UPDATE appareil SET revoque = true WHERE id = :id").param("id", ambulant.appareilId()).update();
		assertThat(get(ambulant, "/v1/produits")).hasStatus(403).bodyJson().extractingPath("$.type")
			.asString().endsWith("appareil-revoque");
	}

	@Test
	void exportDesDonneesPersonnelles() {
		var ambulant = ambulant();
		var export = lire(get(ambulant, "/v1/moi/donnees"));
		assertThat(export.get("utilisateur").get("telephone").asString()).isEqualTo(ambulant.telephone());
		assertThat(export.get("consentements").size()).isEqualTo(1);
		assertThat(export.get("appareils").get(0).get("id").asString()).isEqualTo(ambulant.appareilId().toString());
	}

	@Test
	void lienSigneNeRevelePasLeNumeroEtConnecteParOtp() {
		var hanoutId = hanout(33.5, -7.6);
		var tel = jdbc.sql("SELECT telephone FROM hanout WHERE id = :id").param("id", hanoutId).query(String.class).single();
		var docId = UUID.randomUUID();
		var lien = comptes.lien(Comptes.CibleLien.DOCUMENT, docId, tel, Duration.ofDays(7));
		var jeton = lien.substring(lien.lastIndexOf('/') + 1);

		var cible = lire(get(null, "/v1/liens/" + jeton));
		assertThat(cible.get("cible").asString()).isEqualTo("DOCUMENT");
		assertThat(cible.get("cibleId").asString()).isEqualTo(docId.toString());
		assertThat(cible.get("telephoneMasque").asString()).doesNotContain(tel.substring(5, 10)).endsWith(tel.substring(10));

		assertThat(get(null, "/v1/liens/" + jeton.substring(0, jeton.length() - 2) + "xx")).hasStatus(404);
		post(null, "/v1/auth/otp", m("lienJeton", jeton));
		assertThat(post(null, "/v1/auth/otp/verify", m("lienJeton", jeton, "code", codeEnvoye(tel), "appareilId", UUID.randomUUID())))
			.hasStatusOk();
	}

	@Test
	void lienExpireRepond410() {
		var lien = comptes.lien(Comptes.CibleLien.RELEVE, UUID.randomUUID(), telephone(), Duration.ofSeconds(-5));
		assertThat(get(null, "/v1/liens/" + lien.substring(lien.lastIndexOf('/') + 1))).hasStatus(410);
	}

	@Test
	void jetonPushSeulementPourSonAppareil() {
		var ambulant = ambulant();
		assertThat(put(ambulant, "/v1/appareils/" + ambulant.appareilId() + "/push", m("jeton", "fcm-1", "plateforme", "ANDROID", "langue", "ary-Latn")))
			.hasStatus(204);
		assertThat(comptes.destinataire(ambulant.id()).jetonsPush()).containsExactly("fcm-1");
		assertThat(comptes.destinataire(ambulant.id()).langue()).isEqualTo("ary-Latn");
		assertThat(put(ambulant, "/v1/appareils/" + UUID.randomUUID() + "/push", m("jeton", "x", "plateforme", "ANDROID")))
			.hasStatus(404);
	}

	@Test
	void ladministrateurInscritUnGrossisteVisibleDesGrandsClients() {
		var admin = admin();
		var tel = telephone();
		var cree = lire(post(admin, "/v1/admin/organisations", m("type", "GROSSISTE", "nom", "Grossiste Derb Omar",
				"utilisateurs", java.util.List.of(m("telephone", tel)), "position", m("lat", 33.59, "lon", -7.61),
				"ville", "Casablanca")));
		assertThat(post(admin, "/v1/admin/organisations", m("type", "GROSSISTE", "nom", "Doublon",
				"utilisateurs", java.util.List.of(m("telephone", tel)), "position", m("lat", 33.59, "lon", -7.61))))
			.hasStatus(409);
		var page = lire(get(grandClient(), "/v1/grossistes?q=Derb&lat=33.59&lon=-7.61&rayonM=1000"));
		assertThat(page.get("contenu").get(0).get("id").asString()).isEqualTo(cree.get("id").asString());
		assertThat(page.get("contenu").get(0).get("distanceM").asInt()).isLessThan(10);
		verify(sms, org.mockito.Mockito.never()).envoyer(argThat(d -> tel.equals(d.telephone())), any());
	}

}

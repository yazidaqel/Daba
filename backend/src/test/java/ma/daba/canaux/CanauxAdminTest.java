package ma.daba.canaux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionTemplate;

import ma.daba.TestIntegration;
import ma.daba.canaux.Canaux.Canal;
import ma.daba.canaux.Canaux.TypeMessage;
import ma.daba.comptes.Comptes;
import tools.jackson.databind.JsonNode;

/** Paramétrage des canaux par l'administrateur (§9, « sans développement ») : GET/PUT /v1/admin/canaux. */
class CanauxAdminTest extends TestIntegration {

	@Autowired
	Canaux canaux;

	@Autowired
	Comptes comptes;

	@Autowired
	ApplicationEventPublisher evenements;

	@Autowired
	TransactionTemplate transactions;

	@AfterEach
	void valeursParDefaut() {
		jdbc.sql("DELETE FROM configuration_canaux").update();
	}

	private static JsonNode canal(JsonNode config, String canal) {
		return StreamSupport.stream(config.get("canaux").spliterator(), false)
			.filter(c -> canal.equals(c.get("canal").asString())).findFirst().orElseThrow();
	}

	@Test
	void reserveALAdministrateur() {
		assertThat(get(ambulant(), "/v1/admin/canaux")).hasStatus(403);
		assertThat(put(grossiste(), "/v1/admin/canaux", m("canaux", List.of(), "types", List.of(), "organisations", List.of())))
			.hasStatus(403);
		assertThat(get(null, "/v1/admin/canaux")).hasStatus(401);
	}

	@Test
	void configurationParDefautDuPiloteAvecFournisseursDisponiblesEtSansSecret() {
		var config = lire(get(admin(), "/v1/admin/canaux"));
		assertThat(config.get("canaux")).hasSize(3);
		assertThat(config.get("types")).hasSize(TypeMessage.values().length);
		var sms = canal(config, "SMS");
		assertThat(sms.get("actif").asBoolean()).isTrue();
		assertThat(sms.get("fournisseur").asString()).isEqualTo("JOURNAL");
		assertThat(sms.get("fournisseursDisponibles").toString())
			.isEqualTo("[{\"code\":\"JOURNAL\",\"configure\":true},{\"code\":\"INFOBIP\",\"configure\":false}]");
		assertThat(canal(config, "PUSH").get("fournisseursDisponibles").toString()).contains("{\"code\":\"FCM\",\"configure\":false}");
		assertThat(canal(config, "WHATSAPP").get("actif").asBoolean()).isFalse();
		// Tableau §9 : promo confirmée → push, SMS en repli.
		var promo = StreamSupport.stream(config.get("types").spliterator(), false)
			.filter(t -> "PROMO_CONFIRMEE".equals(t.get("type").asString())).findFirst().orElseThrow();
		assertThat(promo.get("principal").asString()).isEqualTo("PUSH");
		assertThat(promo.get("repli").asString()).isEqualTo("SMS");
		// Aucun champ en dehors du contrat (jamais de clé d'API ni de compte de service).
		StreamSupport.stream(config.get("canaux").spliterator(), false)
			.forEach(c -> assertThat(c.properties().stream().map(java.util.Map.Entry::getKey).toList()).containsOnly("canal", "actif", "fournisseur", "fournisseursDisponibles"));
	}

	@Test
	void fournisseurNonConfigureOuEtrangerAuCanalNePeutPasEtreChoisi() {
		var admin = admin();
		var infobip = put(admin, "/v1/admin/canaux", m("canaux", List.of(m("canal", "SMS", "actif", true, "fournisseur", "INFOBIP")),
				"types", List.of(), "organisations", List.of()));
		assertThat(infobip).hasStatus(422);
		assertThat(lire(infobip).get("type").asString()).endsWith("/fournisseur-non-configure");
		assertThat(put(admin, "/v1/admin/canaux", m("canaux", List.of(m("canal", "PUSH", "actif", true, "fournisseur", "INFOBIP")),
				"types", List.of(), "organisations", List.of()))).hasStatus(400);
		assertThat(put(admin, "/v1/admin/canaux", m("canaux", List.of(), "types",
				List.of(m("type", "OTP", "principal", "SMS", "repli", "SMS")), "organisations", List.of()))).hasStatus(400);
		assertThat(jdbc.sql("SELECT count(*) FROM configuration_canaux").query(Long.class).single()).isZero();
	}

	@Test
	void otpResteSurSmsEtLeSmsNePeutPasEtreCoupe() {
		var admin = admin();
		for (var otp : List.of(m("type", "OTP", "principal", "PUSH", "repli", "SMS"), m("type", "OTP", "principal", "WHATSAPP"),
				m("type", "OTP", "principal", "SMS", "repli", "PUSH"), m("type", "OTP", "principal", "SMS", "inactifs", List.of("SMS")))) {
			var r = put(admin, "/v1/admin/canaux", m("canaux", List.of(), "types", List.of(otp), "organisations", List.of()));
			assertThat(r).hasStatus(400);
			assertThat(lire(r).get("detail").asString()).contains("OTP");
		}
		var smsCoupe = put(admin, "/v1/admin/canaux", m("canaux", List.of(m("canal", "SMS", "actif", false, "fournisseur", "JOURNAL")),
				"types", List.of(), "organisations", List.of()));
		assertThat(smsCoupe).hasStatus(400);
		assertThat(lire(smsCoupe).get("detail").asString()).contains("OTP");
		assertThat(put(admin, "/v1/admin/canaux", m("canaux", List.of(), "types",
				List.of(m("type", "OTP", "principal", "SMS")), "organisations", List.of()))).hasStatusOk();
	}

	@Test
	void organisationInconnueRefusee() {
		var inconnue = UUID.randomUUID();
		var r = put(admin(), "/v1/admin/canaux", m("canaux", List.of(), "types", List.of(),
				"organisations", List.of(m("organisationId", inconnue, "inactifs", List.of("SMS")))));
		assertThat(r).hasStatus(400);
		assertThat(lire(r).get("detail").asString()).contains(inconnue.toString());
	}

	@Test
	void configurationEnregistreeAppliqueeDesLEnvoiSuivantEtJournaliseeEnAudit() {
		var admin = admin();
		var ambulant = ambulant();
		var saisie = m("canaux", List.of(m("canal", "SMS", "actif", true, "fournisseur", "JOURNAL", "apiKey", "ne-doit-pas-rester")),
				"types", List.of(m("type", "DEMANDE_LIVREE", "principal", "SMS", "inactifs", List.of())),
				"organisations", List.of(m("organisationId", ambulant.organisationId(), "inactifs", List.of("SMS"))));
		var reponse = put(admin, "/v1/admin/canaux", saisie);
		assertThat(reponse).hasStatusOk();
		var config = lire(reponse);
		assertThat(config.get("organisations").get(0).get("inactifs").get(0).asString()).isEqualTo("SMS");
		assertThat(config.toString()).doesNotContain("ne-doit-pas-rester");
		assertThat(lire(get(admin, "/v1/admin/canaux")).get("organisations")).hasSize(1);

		// SMS coupé pour l'organisation de cet ambulant : rien ne part, les autres reçoivent.
		assertThat(canaux.envoyer(TypeMessage.DEMANDE_LIVREE, null, comptes.destinataire(ambulant.id()), "Thé")).isEmpty();
		verify(sms, never()).envoyer(argThat(msg -> ambulant.telephone().equals(msg.destinataire().telephone())));
		var autre = ambulant();
		assertThat(canaux.envoyer(TypeMessage.DEMANDE_LIVREE, null, comptes.destinataire(autre.id()), "Thé")).contains(Canal.SMS);

		var audit = jdbc.sql("SELECT utilisateur_id, details::text FROM journal_audit WHERE operation = 'CONFIGURATION_CANAUX' ORDER BY id DESC LIMIT 1")
			.query((rs, i) -> List.of(rs.getObject(1, UUID.class).toString(), rs.getString(2))).single();
		assertThat(audit.get(0)).isEqualTo(admin.id().toString());
		assertThat(audit.get(1)).contains(ambulant.organisationId().toString()).doesNotContain("ne-doit-pas-rester");
		assertThat(jdbc.sql("SELECT configuration::text FROM configuration_canaux").query(String.class).single())
			.doesNotContain("ne-doit-pas-rester").doesNotContain("fournisseursDisponibles");

		// Journal d'audit en ajout seul (V12) : la base refuse modification, suppression et troncature.
		for (var sql : List.of("UPDATE journal_audit SET operation = 'X'", "DELETE FROM journal_audit", "TRUNCATE journal_audit")) {
			org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.sql(sql).update()).hasMessageContaining("ajout seul");
		}

		// Une requête vide rend les valeurs par défaut.
		assertThat(lire(put(admin, "/v1/admin/canaux", m("canaux", List.of(), "types", List.of(), "organisations", List.of())))
			.get("organisations")).isEmpty();
	}

	@Test
	void jetonPushRefuseParFcmEffaceDeLAppareil() throws InterruptedException {
		var ambulant = ambulant();
		jdbc.sql("UPDATE appareil SET jeton_push = 'jeton-mort' WHERE id = :a").param("a", ambulant.appareilId()).update();
		assertThat(comptes.destinataire(ambulant.id()).jetonsPush()).containsExactly("jeton-mort");
		transactions.executeWithoutResult(t -> evenements.publishEvent(new Canaux.JetonPushInvalide("jeton-mort")));
		for (int i = 0; i < 50 && !comptes.destinataire(ambulant.id()).jetonsPush().isEmpty(); i++) {
			Thread.sleep(100);
		}
		assertThat(comptes.destinataire(ambulant.id()).jetonsPush()).isEmpty();
		assertThat(Set.of(comptes.destinataire(ambulant.id()).organisationId())).containsExactly(ambulant.organisationId());
	}

}

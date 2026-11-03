package ma.daba.ventes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import ma.daba.TestIntegration;
import ma.daba.canaux.Canaux;

/** Réception d'une livraison et contestation par le hanout (§6), close par l'ambulant hors ligne. */
class AvisHanoutTest extends TestIntegration {

	record Livraison(Compte ambulant, Compte hanout, UUID documentId) {
	}

	/** BL de deux lignes (5 thés, 2 sucres) poussé par un ambulant, adressé à un hanout qui a un compte. */
	Livraison livraison(Object confirmationHanout) {
		var ambulant = ambulant();
		var the = produit();
		var sucre = produit();
		var hanoutId = hanout(33.57, -7.59);
		var documentId = UUID.randomUUID();
		var donnees = m("documentId", documentId, "type", "BON_LIVRAISON", "numero", ambulant.prefixe() + "-BL-000001",
				"sequence", 1, "hanoutId", hanoutId, "lignes", List.of(
						m("nature", "ARTICLE", "produitId", the, "quantite", 5, "prixUnitaire", 100),
						m("nature", "ARTICLE", "produitId", sucre, "quantite", 2, "prixUnitaire", 300)));
		if (confirmationHanout != null) {
			donnees.put("confirmationHanout", confirmationHanout);
		}
		assertThat(post(ambulant, "/v1/sync/operations", m("appareilId", ambulant.appareilId(), "operations", List.of(
				operation("EMISSION_DOCUMENT", donnees))))).hasStatusOk();
		return new Livraison(ambulant, compteHanout(hanoutId), documentId);
	}

	static Object operation(String type, Object donnees) {
		return m("id", UUID.randomUUID(), "type", type, "creeLe", Instant.now().toString(), "donnees", donnees);
	}

	@Test
	void receptionAvecEcartParLigneSansModifierLeDocument() {
		var l = livraison(null);
		var uri = "/v1/documents/" + l.documentId() + "/reception";
		var id = UUID.randomUUID();
		var saisie = m("id", id, "conforme", false, "ecarts", List.of(m("ligne", 0, "quantiteRecue", 4)), "commentaire", "Un thé manquant");

		assertThat(post(l.hanout(), uri, m("id", UUID.randomUUID(), "conforme", false))).hasStatus(400);
		assertThat(post(l.hanout(), uri, m("id", UUID.randomUUID(), "conforme", false,
				"ecarts", List.of(m("ligne", 5, "quantiteRecue", 1))))).hasStatus(400);
		assertThat(post(l.hanout(), uri, m("id", UUID.randomUUID(), "conforme", false,
				"ecarts", List.of(m("ligne", 1, "quantiteRecue", 2))))).hasStatus(400);

		var r = post(l.hanout(), uri, saisie);
		assertThat(r).hasStatus(201);
		var reception = lire(r).get("receptionHanout");
		assertThat(reception.get("conforme").asBoolean()).isFalse();
		assertThat(reception.get("ecarts").get(0).get("quantiteRecue").asInt()).isEqualTo(4);
		// Le document et le solde ne changent pas (RG-C4) : l'ambulant régularise par un avoir.
		assertThat(lire(r).get("total").asLong()).isEqualTo(1100);

		assertThat(post(l.hanout(), uri, saisie)).hasStatusOk();
		assertThat(post(l.hanout(), uri, m("id", UUID.randomUUID(), "conforme", true))).hasStatus(409);
		assertThat(post(compteHanout(hanout(33.5, -7.5)), uri, m("id", UUID.randomUUID(), "conforme", true))).hasStatus(404);
		assertThat(lire(get(l.ambulant(), "/v1/documents/" + l.documentId())).get("receptionHanout").get("le").isString()).isTrue();
	}

	@Test
	void receptionDejaConfirmeeALaLivraisonRepond409() {
		var l = livraison(m("mode", "CONFIRMATION_ECRAN", "le", Instant.now().toString()));
		assertThat(post(l.hanout(), "/v1/documents/" + l.documentId() + "/reception", m("id", UUID.randomUUID(), "conforme", true)))
			.hasStatus(409);
	}

	@Test
	void contestationNotifieeALAmbulantEtCloseParSynchronisation() {
		var l = livraison(null);
		var id = UUID.randomUUID();
		var saisie = m("id", id, "cible", "DOCUMENT", "cibleId", l.documentId(), "motif", "QUANTITE", "commentaire", "Il manque un carton");
		var cree = post(l.hanout(), "/v1/contestations", saisie);
		assertThat(cree).hasStatus(201);
		assertThat(lire(cree).get("statut").asString()).isEqualTo("OUVERTE");
		assertThat(lire(cree).get("ambulant").get("id").asString()).isEqualTo(l.ambulant().id().toString());
		assertThat(post(l.hanout(), "/v1/contestations", saisie)).hasStatusOk();
		assertThat(post(l.hanout(), "/v1/contestations", m("id", UUID.randomUUID(), "cible", "DOCUMENT", "cibleId", l.documentId(),
				"motif", "MONTANT"))).hasStatus(409);
		assertThat(post(compteHanout(hanout(33.5, -7.5)), "/v1/contestations", m("id", UUID.randomUUID(), "cible", "DOCUMENT",
				"cibleId", l.documentId(), "motif", "MONTANT"))).hasStatus(404);

		// Sans jeton push, la notification à l'ambulant part par SMS (canal de repli).
		verify(sms, timeout(5000)).envoyer(argThat((Canaux.Message m) -> l.ambulant().telephone().equals(m.destinataire().telephone())
				&& m.texte().contains(l.ambulant().prefixe() + "-BL-000001")));

		var vue = lire(get(l.hanout(), "/v1/contestations?cibleId=" + l.documentId()));
		assertThat(vue.get("total").asInt()).isEqualTo(1);
		assertThat(lire(get(l.ambulant(), "/v1/contestations?statut=OUVERTE")).get("total").asInt()).isEqualTo(1);
		var tirage = lire(get(l.ambulant(), "/v1/sync/changements")).get("changements").findValues("entite").stream()
			.map(e -> e.asString()).toList();
		assertThat(tirage).contains("CONTESTATION");

		var resolution = operation("RESOLUTION_CONTESTATION", m("contestationId", id, "commentaire", "Avoir émis"));
		var r = lire(post(l.ambulant(), "/v1/sync/operations", m("appareilId", l.ambulant().appareilId(), "operations",
				List.of(resolution, operation("RESOLUTION_CONTESTATION", m("contestationId", UUID.randomUUID()))))));
		assertThat(r.get("resultats").get(0).get("statut").asString()).isEqualTo("APPLIQUEE");
		assertThat(r.get("resultats").get(1).get("statut").asString()).isEqualTo("REJETEE");
		var close = lire(get(l.hanout(), "/v1/contestations?cibleId=" + l.documentId())).get("contenu").get(0);
		assertThat(close.get("statut").asString()).isEqualTo("RESOLUE");
		assertThat(close.get("commentaireResolution").asString()).isEqualTo("Avoir émis");
		// Une fois close, une nouvelle contestation est possible ; le solde se conteste aussi.
		assertThat(post(l.hanout(), "/v1/contestations", m("id", UUID.randomUUID(), "cible", "DOCUMENT", "cibleId", l.documentId(),
				"motif", "MONTANT"))).hasStatus(201);
		var compte = lire(get(l.hanout(), "/v1/karni/comptes")).get("contenu").get(0).get("id").asString();
		assertThat(post(l.hanout(), "/v1/contestations", m("id", UUID.randomUUID(), "cible", "COMPTE_CREDIT", "cibleId", compte,
				"motif", "DEJA_PAYE"))).hasStatus(201);
	}

}

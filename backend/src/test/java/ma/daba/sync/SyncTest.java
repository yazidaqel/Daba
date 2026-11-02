package ma.daba.sync;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import ma.daba.TestIntegration;
import tools.jackson.databind.JsonNode;

/** Synchronisation, stock et ventes : chaque règle de gestion a son test, nommé par la règle. */
class SyncTest extends TestIntegration {

	static Map<String, Object> op(String type, Object donnees) {
		return op(type, donnees, Instant.now());
	}

	static Map<String, Object> op(String type, Object donnees, Instant creeLe) {
		return m("id", UUID.randomUUID(), "type", type, "creeLe", creeLe.toString(), "donnees", donnees);
	}

	JsonNode pousser(Compte ambulant, Object... operations) {
		var r = post(ambulant, "/v1/sync/operations", m("appareilId", ambulant.appareilId(), "operations", List.of(operations)));
		assertThat(r).hasStatusOk();
		return lire(r).get("resultats");
	}

	static Map<String, Object> chargement(UUID produit, int quantite) {
		return op("CHARGEMENT", m("lignes", List.of(m("produitId", produit, "quantite", quantite))));
	}

	/** BL d'un article, avec réglement comptant facultatif. */
	static Map<String, Object> vente(Compte ambulant, int sequence, UUID hanout, UUID produit, int quantite, long prix, long regle) {
		return op("EMISSION_DOCUMENT", m("documentId", UUID.randomUUID(), "type", "BON_LIVRAISON",
				"numero", "%s-BL-%06d".formatted(ambulant.prefixe(), sequence), "sequence", sequence, "hanoutId", hanout,
				"lignes", List.of(m("nature", "ARTICLE", "produitId", produit, "quantite", quantite, "prixUnitaire", prix)),
				"reglements", regle == 0 ? List.of() : List.of(m("mode", "ESPECES", "montant", regle))));
	}

	static List<String> types(JsonNode resultat) {
		var types = new ArrayList<String>();
		resultat.path("anomalies").forEach(a -> types.add(a.get("type").asString()));
		return types;
	}

	long solde(Compte ambulant) {
		return lire(get(ambulant, "/v1/karni/comptes")).get("contenu").get(0).get("solde").asLong();
	}

	@Test
	void rejouerUneOperationNaAucunNouvelEffet() {
		var ambulant = ambulant();
		var produit = produit();
		var charge = chargement(produit, 10);
		assertThat(pousser(ambulant, charge).get(0).get("statut").asString()).isEqualTo("APPLIQUEE");
		assertThat(pousser(ambulant, charge).get(0).get("statut").asString()).isEqualTo("DEJA_APPLIQUEE");
		assertThat(lire(get(ambulant, "/v1/stock")).get("lignes").get(0).get("quantite").asInt()).isEqualTo(10);
	}

	@Test
	void lotRefusePourUnAutreAppareil() {
		var ambulant = ambulant();
		assertThat(post(ambulant, "/v1/sync/operations", m("appareilId", UUID.randomUUID(), "operations",
				List.of(chargement(produit(), 1))))).hasStatus(403);
	}

	@Test
	void rgS1_venteHorsLigneQuiRendLeStockNegatifEstEnregistreeEtSignalee() {
		var ambulant = ambulant();
		var produit = produit();
		var hanout = hanout(33.57, -7.59);
		var r = pousser(ambulant, chargement(produit, 2), vente(ambulant, 1, hanout, produit, 5, 1000, 5000));
		assertThat(r.get(1).get("statut").asString()).isEqualTo("APPLIQUEE");
		assertThat(types(r.get(1))).containsExactly("STOCK_NEGATIF");
		assertThat(r.get(1).get("anomalies").get(0).get("regle").asString()).isEqualTo("RG-S1");
		assertThat(lire(get(ambulant, "/v1/stock")).get("lignes").get(0).get("quantite").asInt()).isEqualTo(-3);
	}

	@Test
	void rgS2_chaqueMouvementPorteTypeAuteurHorodatageEtAppareil() {
		var ambulant = ambulant();
		var produit = produit();
		var geste = Instant.now().minus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
		pousser(ambulant, op("CHARGEMENT", m("lignes", List.of(m("produitId", produit, "quantite", 4))), geste));
		var mvt = lire(get(ambulant, "/v1/stock/mouvements?type=CHARGEMENT")).get("contenu").get(0);
		assertThat(mvt.get("type").asString()).isEqualTo("CHARGEMENT");
		assertThat(mvt.get("auteurId").asString()).isEqualTo(ambulant.id().toString());
		assertThat(mvt.get("appareilId").asString()).isEqualTo(ambulant.appareilId().toString());
		assertThat(Instant.parse(mvt.get("horodatage").asString())).isEqualTo(geste);
		assertThat(mvt.has("recuLe")).isTrue();
	}

	@Test
	void rgS4_ecartDInventaireSansMotifAuDelaDuSeuil() {
		var ambulant = ambulant();
		var p1 = produit();
		var p2 = produit();
		var r = pousser(ambulant, chargement(p1, 100), chargement(p2, 100), op("INVENTAIRE", m("lignes", List.of(
				m("produitId", p1, "quantiteComptee", 98),
				m("produitId", p2, "quantiteComptee", 80, "motifEcart", "Casse")))));
		// 2 % d'écart sous le seuil de 5 % ; 20 % justifié par un motif.
		assertThat(types(r.get(2))).isEmpty();
		var r2 = pousser(ambulant, op("INVENTAIRE", m("lignes", List.of(m("produitId", p1, "quantiteComptee", 50)))));
		assertThat(types(r2.get(0))).containsExactly("ECART_SANS_MOTIF");
		assertThat(r2.get(0).get("anomalies").get(0).get("regle").asString()).isEqualTo("RG-S4");
		var stock = lire(get(ambulant, "/v1/stock")).get("lignes");
		assertThat(stock.findValues("quantite").stream().map(JsonNode::asInt)).containsExactlyInAnyOrder(50, 80);
	}

	@Test
	void chargementParQrDUnBonDeSortieUneSeuleFois() {
		var grossiste = grossiste();
		var ambulant = ambulant();
		var produit = produit();
		var bon = m("id", UUID.randomUUID(), "ambulantTelephone", ambulant.telephone(),
				"lignes", List.of(m("produitId", produit, "quantite", 24)));
		var cree = lire(post(grossiste, "/v1/bons-sortie", bon));
		assertThat(post(grossiste, "/v1/bons-sortie", bon)).hasStatus(200);
		assertThat(post(grossiste, "/v1/bons-sortie", m("id", UUID.randomUUID(), "ambulantTelephone", telephone(),
				"lignes", List.of(m("produitId", produit, "quantite", 1))))).hasStatus(422);

		// L'ambulant reçoit le bon par synchronisation et lit la charge du QR hors ligne.
		var qr = cree.get("qrJeton").asString();
		var charge = json.readTree(java.util.Base64.getUrlDecoder().decode(qr.split("\\.")[1]));
		assertThat(charge.get("ambulantId").asString()).isEqualTo(ambulant.id().toString());
		assertThat(lire(get(ambulant, "/v1/sync/changements")).get("changements").findValuesAsString("entite")).contains("BON_SORTIE");

		var r = pousser(ambulant, op("CHARGEMENT", m("qrJeton", qr)), op("CHARGEMENT", m("qrJeton", qr)));
		assertThat(r.get(0).get("statut").asString()).isEqualTo("APPLIQUEE");
		assertThat(types(r.get(1))).containsExactly("CHARGEMENT_EN_DOUBLE");
		assertThat(lire(get(ambulant, "/v1/stock")).get("lignes").get(0).get("quantite").asInt()).isEqualTo(24);
		assertThat(lire(get(grossiste, "/v1/bons-sortie/" + cree.get("id").asString())).has("chargeLe")).isTrue();

		// Un autre ambulant ne peut pas charger ce bon, ni le grossiste d'à côté le lire.
		assertThat(pousser(ambulant(), op("CHARGEMENT", m("qrJeton", qr))).get(0).get("statut").asString()).isEqualTo("REJETEE");
		assertThat(get(grossiste(), "/v1/bons-sortie/" + cree.get("id").asString())).hasStatus(404);
	}

	@Test
	void rgF1_trouEtDoublonDeNumerotationSignalesSansRefus() {
		var ambulant = ambulant();
		var produit = produit();
		var hanout = hanout(33.57, -7.59);
		var r = pousser(ambulant, chargement(produit, 100), vente(ambulant, 1, hanout, produit, 1, 100, 100),
				vente(ambulant, 3, hanout, produit, 1, 100, 100), vente(ambulant, 3, hanout, produit, 1, 100, 100));
		assertThat(types(r.get(1))).isEmpty();
		assertThat(types(r.get(2))).containsExactly("NUMEROTATION_TROU");
		assertThat(types(r.get(3))).containsExactly("NUMERO_EN_DOUBLE");
		assertThat(r.get(3).get("anomalies").get(0).get("regle").asString()).isEqualTo("RG-F1");
		assertThat(r.get(3).get("statut").asString()).isEqualTo("APPLIQUEE");
	}

	@Test
	void rgF2_factureSansMentionsLegalesRejetee() {
		var ambulant = ambulant();
		var produit = produit();
		var facture = m("documentId", UUID.randomUUID(), "type", "FACTURE", "numero", ambulant.prefixe() + "-FA-000001",
				"sequence", 1, "hanoutId", hanout(33.5, -7.6),
				"lignes", List.of(m("nature", "ARTICLE", "produitId", produit, "quantite", 1, "prixUnitaire", 100)));
		var r = pousser(ambulant, op("EMISSION_DOCUMENT", facture));
		assertThat(r.get(0).get("statut").asString()).isEqualTo("REJETEE");
		assertThat(r.get(0).get("erreur").get("regle").asString()).isEqualTo("RG-F2");

		facture.put("documentId", UUID.randomUUID());
		facture.put("mentionsLegales", m("ice", "001234567000089", "identifiantFiscal", "IF1", "registreCommerce", "RC1",
				"assujettiTva", false));
		assertThat(pousser(ambulant, op("EMISSION_DOCUMENT", facture)).get(0).get("statut").asString()).isEqualTo("APPLIQUEE");
	}

	@Test
	void rgF3_remisePromoSansCampagneRejetee() {
		var ambulant = ambulant();
		var produit = produit();
		var r = pousser(ambulant, op("EMISSION_DOCUMENT", m("documentId", UUID.randomUUID(), "type", "BON_LIVRAISON",
				"numero", ambulant.prefixe() + "-BL-000001", "sequence", 1, "hanoutId", hanout(33.5, -7.6),
				"lignes", List.of(m("nature", "ARTICLE", "produitId", produit, "quantite", 1, "prixUnitaire", 1000),
						m("nature", "REMISE_PROMO", "produitId", produit, "montant", -100)))));
		assertThat(r.get(0).get("erreur").get("regle").asString()).isEqualTo("RG-F3");
	}

	@Test
	void rgC1_soldeEgalCreditMoinsVersementsMoinsAvoirs() {
		var ambulant = ambulant();
		var produit = produit();
		var hanout = hanout(33.57, -7.59);
		var v1 = vente(ambulant, 1, hanout, produit, 10, 1000, 2000); // 10 000 dont 8 000 à crédit
		pousser(ambulant, chargement(produit, 50), v1);
		assertThat(solde(ambulant)).isEqualTo(8000);
		pousser(ambulant, op("VERSEMENT", m("versementId", UUID.randomUUID(), "hanoutId", hanout, "mode", "ESPECES", "montant", 3000)));
		assertThat(solde(ambulant)).isEqualTo(5000);
		var docId = ((Map<?, ?>) v1.get("donnees")).get("documentId");
		pousser(ambulant, op("AVOIR", m("documentId", UUID.randomUUID(), "documentOrigineId", docId,
				"numero", ambulant.prefixe() + "-AV-000001", "sequence", 1, "motif", "RETOUR_PRODUIT",
				"lignes", List.of(m("nature", "ARTICLE", "produitId", produit, "quantite", 2, "prixUnitaire", 1000)))));
		assertThat(solde(ambulant)).isEqualTo(3000);

		var compte = lire(get(ambulant, "/v1/karni/comptes")).get("contenu").get(0).get("id").asString();
		var releve = lire(get(ambulant, "/v1/karni/comptes/" + compte + "/releve")).get("ecritures");
		assertThat(releve.findValuesAsString("nature")).containsExactly("ACHAT_CREDIT", "VERSEMENT", "AVOIR");
		assertThat(releve.get(2).get("soldeApres").asLong()).isEqualTo(3000);
	}

	@Test
	void rgC2_versementImputeEnFifoSurLaPieceLaPlusAncienne() {
		var ambulant = ambulant();
		var produit = produit();
		var hanout = hanout(33.57, -7.59);
		var ancienne = vente(ambulant, 1, hanout, produit, 1, 4000, 0);
		ancienne.put("creeLe", Instant.now().minus(3, ChronoUnit.DAYS).toString());
		var recente = vente(ambulant, 2, hanout, produit, 1, 6000, 0);
		pousser(ambulant, chargement(produit, 10), ancienne, recente);
		var versement = UUID.randomUUID();
		pousser(ambulant, op("VERSEMENT", m("versementId", versement, "hanoutId", hanout, "mode", "ESPECES", "montant", 5000)));
		var imputations = jdbc.sql("SELECT d.sequence, i.montant FROM imputation i JOIN document d ON d.id = i.document_id WHERE i.versement_id = :v ORDER BY d.sequence")
			.param("v", versement).query((rs, i) -> rs.getLong(1) + ":" + rs.getLong(2)).list();
		assertThat(imputations).containsExactly("1:4000", "2:1000");

		// Répartition manuelle respectée.
		var choisi = UUID.randomUUID();
		var recenteId = ((Map<?, ?>) recente.get("donnees")).get("documentId");
		pousser(ambulant, op("VERSEMENT", m("versementId", choisi, "hanoutId", hanout, "mode", "CHEQUE", "montant", 500,
				"imputations", List.of(m("documentId", recenteId, "montant", 500)))));
		assertThat(jdbc.sql("SELECT montant FROM imputation WHERE versement_id = :v").param("v", choisi).query(Long.class).single())
			.isEqualTo(500);
	}

	@Test
	void rgC3_venteAuDelaDuPlafondAppliqueeAvecAnomalie() {
		var ambulant = ambulant();
		var produit = produit();
		var hanout = hanout(33.57, -7.59);
		pousser(ambulant, chargement(produit, 10),
				op("PARAMETRES_AMBULANT", m("politiqueDepassementCredit", "BLOQUER")),
				op("CREDIT_HANOUT", m("hanoutId", hanout, "plafond", 5000, "delaiMaxJours", 15)));
		var r = pousser(ambulant, vente(ambulant, 1, hanout, produit, 1, 8000, 0));
		assertThat(r.get(0).get("statut").asString()).isEqualTo("APPLIQUEE");
		assertThat(types(r.get(0))).containsExactly("PLAFOND_CREDIT_DEPASSE");
		assertThat(r.get(0).get("anomalies").get(0).get("regle").asString()).isEqualTo("RG-C3");
		assertThat(lire(get(ambulant, "/v1/karni/comptes")).get("contenu").get(0).get("depassement").asString())
			.isEqualTo("PLAFOND");
	}

	@Test
	void rgC3_delaiDeCreditDepasse() {
		var ambulant = ambulant();
		var produit = produit();
		var hanout = hanout(33.57, -7.59);
		var vieille = vente(ambulant, 1, hanout, produit, 1, 1000, 0);
		vieille.put("creeLe", Instant.now().minus(20, ChronoUnit.DAYS).toString());
		pousser(ambulant, chargement(produit, 10), op("CREDIT_HANOUT", m("hanoutId", hanout, "delaiMaxJours", 15)), vieille);
		var r = pousser(ambulant, vente(ambulant, 2, hanout, produit, 1, 1000, 0));
		assertThat(types(r.get(0))).containsExactly("DELAI_CREDIT_DEPASSE");
	}

	@Test
	void rgC4_documentJamaisSupprimeSeulUnAvoirLAnnule() {
		var ambulant = ambulant();
		var produit = produit();
		var hanout = hanout(33.57, -7.59);
		var v = vente(ambulant, 1, hanout, produit, 2, 1500, 0);
		pousser(ambulant, chargement(produit, 10), v);
		var docId = ((Map<?, ?>) v.get("donnees")).get("documentId").toString();
		assertThat(appel(HttpMethod.DELETE, ambulant, "/v1/documents/" + docId, null)).hasStatus(405);
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.sql("DELETE FROM document WHERE id = :id")
			.param("id", UUID.fromString(docId)).update()).hasMessageContaining("RG-C4");

		var avoir = UUID.randomUUID();
		pousser(ambulant, op("AVOIR", m("documentId", avoir, "documentOrigineId", docId,
				"numero", ambulant.prefixe() + "-AV-000001", "sequence", 1, "motif", "ERREUR_SAISIE")));
		var doc = lire(get(ambulant, "/v1/documents/" + docId));
		assertThat(doc.get("statut").asString()).isEqualTo("ANNULE");
		assertThat(doc.get("avoirIds").get(0).asString()).isEqualTo(avoir.toString());
		// Un second avoir au-delà du restant est refusé.
		var r = pousser(ambulant, op("AVOIR", m("documentId", UUID.randomUUID(), "documentOrigineId", docId,
				"numero", ambulant.prefixe() + "-AV-000002", "sequence", 2, "motif", "AUTRE")));
		assertThat(r.get(0).get("erreur").get("regle").asString()).isEqualTo("RG-C4");
		assertThat(solde(ambulant)).isZero();
	}

	@Test
	void conflitSurTarifPlusRecentAilleursConserveLaVersionServeur() {
		var ambulant = ambulant();
		var produit = produit();
		pousser(ambulant, op("TARIF", m("produitId", produit, "prixUnitaire", 1200)));
		var ancien = pousser(ambulant, op("TARIF", m("produitId", produit, "prixUnitaire", 900), Instant.now().minus(1, ChronoUnit.HOURS)));
		assertThat(ancien.get(0).get("statut").asString()).isEqualTo("EN_CONFLIT");
		assertThat(ancien.get(0).get("conflit").get("resolution").asString()).isEqualTo("VERSION_SERVEUR_CONSERVEE");
		assertThat(ancien.get(0).get("conflit").get("versionServeur").get("prixUnitaire").asLong()).isEqualTo(1200);
	}

	@Test
	void operationMalFormeeOuReferenceInconnueRejeteeEtRejouable() {
		var ambulant = ambulant();
		var produit = produit();
		var hanoutTerrain = UUID.randomUUID();
		var v = vente(ambulant, 1, hanoutTerrain, produit, 1, 100, 100);
		var r = pousser(ambulant, v, op("CHARGEMENT", m("lignes", List.of(m("produitId", produit, "quantite", 0)))));
		assertThat(r.get(0).get("statut").asString()).isEqualTo("REJETEE");
		assertThat(r.get(1).get("erreur").get("erreurs").get(0).get("champ").asString()).contains("quantite");

		// Le hanout créé sur le terrain arrive, la vente rejouée passe.
		var r2 = pousser(ambulant, op("HANOUT", m("hanoutId", hanoutTerrain, "nom", "Hanout du coin")), v);
		assertThat(r2.get(1).get("statut").asString()).isEqualTo("APPLIQUEE");
	}

	@Test
	void tirageDifferentielParCurseurEt410SiTropAncien() {
		var ambulant = ambulant();
		var produit = produit();
		pousser(ambulant, chargement(produit, 3));
		var complet = lire(get(ambulant, "/v1/sync/changements?limite=1000"));
		var curseur = complet.get("curseur").asString();
		while (complet.get("plusDeChangements").asBoolean()) {
			complet = lire(get(ambulant, "/v1/sync/changements?limite=1000&curseur=" + curseur));
			curseur = complet.get("curseur").asString();
		}
		pousser(ambulant, chargement(produit, 2));
		var suite = lire(get(ambulant, "/v1/sync/changements?curseur=" + curseur)).get("changements");
		assertThat(suite.findValuesAsString("entite")).containsExactlyInAnyOrder("MOUVEMENT", "STOCK");
		var stock = suite.findParents("entite").stream().filter(c -> c.get("entite").asString().equals("STOCK")).findFirst().orElseThrow();
		assertThat(stock.get("donnees").get("quantite").asInt()).isEqualTo(5);

		// Un autre ambulant ne voit pas ces changements.
		assertThat(lire(get(ambulant(), "/v1/sync/changements?curseur=" + curseur)).get("changements").findValuesAsString("entite"))
			.doesNotContain("MOUVEMENT");

		jdbc.sql("INSERT INTO changement_purge (seq) VALUES (nextval('changement_seq'))").update();
		assertThat(get(ambulant, "/v1/sync/changements?curseur=" + curseur)).hasStatus(410);
	}

	@Test
	void cloisonnement_documentDUnAutreAmbulantRepond404EtLeHanoutVoitLesSiens() {
		var ambulant = ambulant();
		var produit = produit();
		var hanout = hanout(33.57, -7.59);
		var v = vente(ambulant, 1, hanout, produit, 1, 100, 0);
		pousser(ambulant, chargement(produit, 1), v);
		var docId = ((Map<?, ?>) v.get("donnees")).get("documentId").toString();
		assertThat(get(ambulant(), "/v1/documents/" + docId)).hasStatus(404);
		assertThat(get(compteHanout(hanout(33.5, -7.5)), "/v1/documents/" + docId)).hasStatus(404);
		var leHanout = compteHanout(hanout);
		assertThat(get(leHanout, "/v1/documents/" + docId)).hasStatusOk();
		assertThat(lire(get(leHanout, "/v1/karni/comptes")).get("contenu").get(0).get("solde").asLong()).isEqualTo(100);
		assertThat(lire(get(leHanout, "/v1/produits?habituels=true")).get("total").asInt()).isEqualTo(1);
	}

	@Test
	void pdfDuDocumentEnArabeEtEnFrancais() throws Exception {
		var ambulant = ambulant();
		var produit = produit();
		var hanout = hanout(33.57, -7.59);
		var v = vente(ambulant, 1, hanout, produit, 2, 1250, 0);
		pousser(ambulant, chargement(produit, 5), v);
		var docId = ((Map<?, ?>) v.get("donnees")).get("documentId");
		for (var langue : List.of("ar", "fr", "ary-Latn")) {
			var r = get(ambulant, "/v1/documents/" + docId + "/pdf?langue=" + langue);
			assertThat(r).hasStatusOk().hasContentType("application/pdf");
			try (var pdf = org.apache.pdfbox.Loader.loadPDF(r.getResponse().getContentAsByteArray())) {
				var texte = new org.apache.pdfbox.text.PDFTextStripper().getText(pdf);
				assertThat(texte).contains(ambulant.prefixe() + "-BL-000001").contains("25,00 DH");
			}
		}
		assertThat(get(ambulant(), "/v1/documents/" + docId + "/pdf")).hasStatus(404);
	}

	@Test
	void syntheseDesCreancesParAnciennete() {
		var ambulant = ambulant();
		var produit = produit();
		var hanout = hanout(33.57, -7.59);
		var vieille = vente(ambulant, 1, hanout, produit, 1, 1000, 0);
		vieille.put("creeLe", Instant.now().minus(40, ChronoUnit.DAYS).toString());
		pousser(ambulant, chargement(produit, 5), vieille, vente(ambulant, 2, hanout, produit, 1, 500, 0));
		var synthese = lire(get(ambulant, "/v1/karni/synthese"));
		assertThat(synthese.get("total").asLong()).isEqualTo(1500);
		assertThat(synthese.get("anciennete").get("plusDe30Jours").asLong()).isEqualTo(1000);
		assertThat(synthese.get("anciennete").get("de0a15Jours").asLong()).isEqualTo(500);
		assertThat(synthese.get("topDebiteurs").get(0).get("hanout").get("id").asString()).isEqualTo(hanout.toString());
	}

}

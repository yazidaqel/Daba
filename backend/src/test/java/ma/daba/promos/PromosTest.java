package ma.daba.promos;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import ma.daba.TestIntegration;
import tools.jackson.databind.JsonNode;

class PromosTest extends TestIntegration {

	@Autowired
	Promos promos;

	record Scenario(Compte marque, Compte grossiste, UUID produit, String campagneId, String depotId) {
	}

	Map<String, Object> campagne(UUID produit, int total, int max) {
		return m("nom", m("fr", "Promo été", "ar", "عرض الصيف"), "debut", Instant.now().minus(1, ChronoUnit.HOURS).toString(),
				"fin", Instant.now().plus(10, ChronoUnit.DAYS).toString(),
				"lignes", List.of(m("produitId", produit, "remise", m("type", "POURCENTAGE", "pourcentage", 10),
						"quantiteTotale", total, "quantiteMaxParAmbulant", max, "prixReventeConseille", 900)));
	}

	JsonNode recevoir(Compte grossiste, String depotId, UUID produit, int quantite) {
		var reception = new MockMultipartFile("reception", "", "application/json",
				json.writeValueAsBytes(m("lignes", List.of(m("produitId", produit, "quantiteRecue", quantite)))));
		var photo = new MockMultipartFile("photoBonLivraison", "bl.jpg", "image/jpeg", new byte[] { (byte) 0xFF, (byte) 0xD8, 1 });
		var r = mvc.post().uri("/v1/depots/" + depotId + "/reception").multipart().file(reception).file(photo)
			.header("Authorization", "Bearer " + grossiste.jeton()).exchange();
		assertThat(r).hasStatusOk();
		return lire(r);
	}

	/** Campagne proposée, acceptée et reçue chez un grossiste : 20 unités, 5 maximum par ambulant. */
	Scenario promoActive() {
		var marque = grandClient();
		var grossiste = grossiste();
		var produit = produit(marque.organisationId());
		var campagneId = lire(post(marque, "/v1/campagnes", campagne(produit, 100, 5))).get("id").asString();
		var depotId = lire(post(marque, "/v1/campagnes/" + campagneId + "/depots", m("grossisteId", grossiste.organisationId(),
				"lignes", List.of(m("produitId", produit, "quantite", 20))))).get("id").asString();
		post(grossiste, "/v1/depots/" + depotId + "/reponse", m("decision", "ACCEPTER"));
		recevoir(grossiste, depotId, produit, 20);
		return new Scenario(marque, grossiste, produit, campagneId, depotId);
	}

	String offre(Compte ambulant, Scenario s) {
		var offres = lire(get(ambulant, "/v1/promos?taille=200")).get("contenu");
		for (var o : offres) {
			if (o.get("depotId").asString().equals(s.depotId())) {
				return o.get("id").asString();
			}
		}
		return null;
	}

	@Test
	void rgP1_promoInvisibleDesAmbulantsAvantLaConfirmationDeReception() {
		var marque = grandClient();
		var grossiste = grossiste();
		var produit = produit(marque.organisationId());
		var campagne = lire(post(marque, "/v1/campagnes", campagne(produit, 50, 5)));
		assertThat(campagne.get("statut").asString()).isEqualTo("BROUILLON");
		var campagneId = campagne.get("id").asString();
		var depot = lire(post(marque, "/v1/campagnes/" + campagneId + "/depots", m("grossisteId", grossiste.organisationId(),
				"lignes", List.of(m("produitId", produit, "quantite", 30)))));
		assertThat(lire(get(marque, "/v1/campagnes/" + campagneId)).get("statut").asString()).isEqualTo("PROPOSEE");
		// La campagne proposée ne se modifie plus.
		assertThat(put(marque, "/v1/campagnes/" + campagneId, campagne(produit, 50, 5))).hasStatus(409);
		var depotId = depot.get("id").asString();
		var ambulant = ambulant();

		post(grossiste, "/v1/depots/" + depotId + "/reponse", m("decision", "ACCEPTER"));
		assertThat(lire(get(ambulant, "/v1/promos")).findValuesAsString("depotId")).doesNotContain(depotId);

		var recu = recevoir(grossiste, depotId, produit, 28);
		assertThat(recu.get("statut").asString()).isEqualTo("RECU");
		assertThat(recu.get("lignes").get(0).get("quantiteRestante").asInt()).isEqualTo(28);
		assertThat(recu.get("photoBonLivraisonUrl").asString()).contains("/v1/photos/");
		assertThat(lire(get(marque, "/v1/campagnes/" + campagneId)).get("statut").asString()).isEqualTo("ACTIVE");
		assertThat(lire(get(ambulant, "/v1/promos")).findValuesAsString("depotId")).contains(depotId);
		assertThat(lire(get(ambulant, "/v1/sync/changements?limite=1000")).get("changements").findValuesAsString("entite")).contains("PROMO");
		// Recevoir deux fois est refusé.
		var reception = new MockMultipartFile("reception", "", "application/json",
				json.writeValueAsBytes(m("lignes", List.of(m("produitId", produit, "quantiteRecue", 1)))));
		assertThat(mvc.post().uri("/v1/depots/" + depotId + "/reception").multipart().file(reception)
			.file(new MockMultipartFile("photoBonLivraison", "bl.jpg", "image/jpeg", new byte[] { 1 }))
			.header("Authorization", "Bearer " + grossiste.jeton())).hasStatus(409);
	}

	@Test
	void rgP3_plafondParAmbulantControleALaReservation() {
		var s = promoActive();
		var ambulant = ambulant();
		var offre = offre(ambulant, s);
		var id = UUID.randomUUID();
		assertThat(post(ambulant, "/v1/reservations", m("id", id, "offreId", offre, "quantite", 3))).hasStatus(201);
		assertThat(post(ambulant, "/v1/reservations", m("id", id, "offreId", offre, "quantite", 3))).hasStatus(200);
		var refus = post(ambulant, "/v1/reservations", m("id", UUID.randomUUID(), "offreId", offre, "quantite", 3));
		assertThat(refus).hasStatus(422).bodyJson().extractingPath("$.regle").isEqualTo("RG-P3");
		assertThat(lire(get(ambulant, "/v1/promos")).get("contenu").findValues("quantiteDejaReservee").stream().map(JsonNode::asInt))
			.contains(3);
	}

	@Test
	void quantiteRestanteInsuffisanteRepond409() {
		var s = promoActive();
		for (int i = 0; i < 4; i++) {
			var a = ambulant();
			assertThat(post(a, "/v1/reservations", m("id", UUID.randomUUID(), "offreId", offre(a, s), "quantite", 5))).hasStatus(201);
		}
		var dernier = ambulant();
		assertThat(post(dernier, "/v1/reservations", m("id", UUID.randomUUID(), "offreId", offre(dernier, s), "quantite", 1)))
			.hasStatus(409);
	}

	@Test
	void rgP2_reservationNonRetireeExpireEtLaQuantiteRevientAuDisponible() {
		var s = promoActive();
		var ambulant = ambulant();
		var offre = offre(ambulant, s);
		var id = UUID.randomUUID();
		var resa = lire(post(ambulant, "/v1/reservations", m("id", id, "offreId", offre, "quantite", 4)));
		assertThat(Instant.parse(resa.get("expireLe").asString())).isBetween(Instant.now().plus(23, ChronoUnit.HOURS),
				Instant.now().plus(25, ChronoUnit.HOURS));
		assertThat(lire(get(s.grossiste(), "/v1/depots/" + s.depotId())).get("lignes").get(0).get("quantiteRestante").asInt()).isEqualTo(16);

		jdbc.sql("UPDATE reservation SET expire_le = now() - interval '1 minute' WHERE id = :id").param("id", id).update();
		assertThat(lire(get(s.grossiste(), "/v1/depots/" + s.depotId())).get("lignes").get(0).get("quantiteRestante").asInt()).isEqualTo(20);
		promos.echeances();
		assertThat(lire(get(ambulant, "/v1/reservations/" + id)).get("statut").asString()).isEqualTo("EXPIREE");
		assertThat(lire(get(ambulant, "/v1/reservations/" + id)).has("qrJeton")).isFalse();
	}

	@Test
	void rgP4_retraitSeulementParScanChezLeGrossisteConcerne_etRgS3_stockPromoTraceJusquALaVente() {
		var s = promoActive();
		var ambulant = ambulant();
		var resaId = UUID.randomUUID();
		var resa = lire(post(ambulant, "/v1/reservations", m("id", resaId, "offreId", offre(ambulant, s), "quantite", 5)));
		var qr = resa.get("qrJeton").asString();

		var autre = grossiste();
		assertThat(post(autre, "/v1/retraits", m("qrJeton", qr, "appareilId", autre.appareilId(), "quantiteRemise", 5)))
			.hasStatus(422).bodyJson().extractingPath("$.regle").isEqualTo("RG-P4");
		assertThat(post(s.grossiste(), "/v1/retraits", m("qrJeton", "faux.qr.jeton", "appareilId", s.grossiste().appareilId(), "quantiteRemise", 1)))
			.hasStatus(422);
		assertThat(lire(post(s.grossiste(), "/v1/retraits/apercu", m("qrJeton", qr))).get("quantiteReservee").asInt()).isEqualTo(5);

		assertThat(post(s.grossiste(), "/v1/retraits", m("qrJeton", qr, "appareilId", UUID.randomUUID(), "quantiteRemise", 4)))
			.hasStatus(403);
		var retrait = lire(post(s.grossiste(), "/v1/retraits", m("qrJeton", qr, "appareilId", s.grossiste().appareilId(), "quantiteRemise", 4)));
		assertThat(retrait.get("statut").asString()).isEqualTo("RETIREE");
		assertThat(post(s.grossiste(), "/v1/retraits", m("qrJeton", qr, "appareilId", s.grossiste().appareilId(), "quantiteRemise", 1)))
			.hasStatus(409);
		// La quantité non remise revient au disponible.
		assertThat(lire(get(s.grossiste(), "/v1/depots/" + s.depotId())).get("lignes").get(0).get("quantiteRestante").asInt()).isEqualTo(16);

		// Stock promo isolé, rattaché à la campagne et à la réservation (RG-S3), mouvement PROMO de l'appareil du grossiste.
		var stock = lire(get(ambulant, "/v1/stock")).get("lignes").get(0);
		assertThat(stock.get("quantite").asInt()).isZero();
		assertThat(stock.get("promo").get(0).get("reservationId").asString()).isEqualTo(resaId.toString());
		assertThat(stock.get("promo").get(0).get("quantite").asInt()).isEqualTo(4);
		var mvt = lire(get(ambulant, "/v1/stock/mouvements?type=PROMO")).get("contenu").get(0);
		assertThat(mvt.get("appareilId").asString()).isEqualTo(s.grossiste().appareilId().toString());
		assertThat(mvt.get("campagneId").asString()).isEqualTo(s.campagneId());
		// Sans opération de l'appareil ambulant, l'operationId du mouvement PROMO est l'id de la réservation.
		assertThat(mvt.get("operationId").asString()).isEqualTo(resaId.toString());

		// Revente au hanout avec la référence de campagne sur la ligne (RG-F3) ; au-delà du retiré, anomalie RG-S3.
		var hanout = hanout(33.57, -7.59);
		var vente = m("id", UUID.randomUUID(), "type", "EMISSION_DOCUMENT", "creeLe", Instant.now().toString(), "donnees",
				m("documentId", UUID.randomUUID(), "type", "BON_LIVRAISON", "numero", ambulant.prefixe() + "-BL-000001", "sequence", 1,
						"hanoutId", hanout, "lignes", List.of(
								m("nature", "ARTICLE", "produitId", s.produit(), "quantite", 3, "prixUnitaire", 1000,
										"campagneId", s.campagneId(), "reservationId", resaId),
								m("nature", "REMISE_PROMO", "produitId", s.produit(), "montant", -300, "campagneId", s.campagneId()))));
		var r = lire(post(ambulant, "/v1/sync/operations", m("appareilId", ambulant.appareilId(), "operations", List.of(vente))));
		assertThat(r.get("resultats").get(0).get("statut").asString()).isEqualTo("APPLIQUEE");
		var doc = lire(get(ambulant, "/v1/documents?type=BON_LIVRAISON")).get("contenu").get(0);
		assertThat(doc.get("lignes").get(1).get("campagneNom").get("fr").asString()).isEqualTo("Promo été");
		assertThat(doc.get("total").asLong()).isEqualTo(2700);
		assertThat(lire(get(ambulant, "/v1/reservations/" + resaId)).get("statut").asString()).isEqualTo("REVENDUE");

		var trop = m("id", UUID.randomUUID(), "type", "EMISSION_DOCUMENT", "creeLe", Instant.now().toString(), "donnees",
				m("documentId", UUID.randomUUID(), "type", "BON_LIVRAISON", "numero", ambulant.prefixe() + "-BL-000002", "sequence", 2,
						"hanoutId", hanout, "lignes", List.of(m("nature", "ARTICLE", "produitId", s.produit(), "quantite", 2,
								"prixUnitaire", 1000, "campagneId", s.campagneId(), "reservationId", resaId))));
		var r2 = lire(post(ambulant, "/v1/sync/operations", m("appareilId", ambulant.appareilId(), "operations", List.of(trop))));
		assertThat(r2.get("resultats").get(0).get("anomalies").get(0).get("type").asString()).isEqualTo("PROMO_SANS_RETRAIT");
		assertThat(r2.get("resultats").get(0).get("anomalies").get(0).get("regle").asString()).isEqualTo("RG-S3");

		// Entonnoir de la marque : agrégé, sans identifiant d'ambulant ni de hanout (§5.4).
		var entonnoir = get(s.marque(), "/v1/campagnes/" + s.campagneId() + "/entonnoir");
		var total = lire(entonnoir).get("total");
		assertThat(List.of(total.get("depose").asInt(), total.get("reserve").asInt(), total.get("retire").asInt(), total.get("revendu").asInt()))
			.containsExactly(20, 4, 4, 5);
		var texte = new String(entonnoir.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
		assertThat(texte).doesNotContain(ambulant.id().toString()).doesNotContain(hanout.toString());
	}

	@Test
	void rgP3_plafondEtStatutDeCampagneVisiblesDuGrossiste_etRgP4_qrDeReservationEnJwsSigne() {
		var s = promoActive();
		var depot = lire(get(s.grossiste(), "/v1/depots/" + s.depotId()));
		assertThat(depot.get("statutCampagne").asString()).isEqualTo("ACTIVE");
		assertThat(depot.get("lignes").get(0).get("quantiteMaxParAmbulant").asInt()).isEqualTo(5);

		var ambulant = ambulant();
		var resaId = UUID.randomUUID();
		var qr = lire(post(ambulant, "/v1/reservations", m("id", resaId, "offreId", offre(ambulant, s), "quantite", 1)))
			.get("qrJeton").asString();
		assertThat(qr).matches("^[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+$");
		var charge = json.readTree(java.util.Base64.getUrlDecoder().decode(qr.split("\\.")[1]));
		assertThat(charge.get("reservationId").asString()).isEqualTo(resaId.toString());
		assertThat(charge.get("depotId").asString()).isEqualTo(s.depotId());
		// Une charge modifiée sans la clé ne passe pas (RG-P4).
		var falsifie = qr.split("\\.")[0] + "." + java.util.Base64.getUrlEncoder().withoutPadding()
			.encodeToString(json.writeValueAsBytes(m("reservationId", UUID.randomUUID(), "depotId", s.depotId(), "typ", "resa")))
				+ "." + qr.split("\\.")[2];
		assertThat(post(s.grossiste(), "/v1/retraits/apercu", m("qrJeton", falsifie))).hasStatus(422).bodyJson()
			.extractingPath("$.regle").isEqualTo("RG-P4");
	}

	@Test
	void rgP3_plafondRecontroleAuRetrait() {
		var s = promoActive();
		var ambulant = ambulant();
		var r1 = lire(post(ambulant, "/v1/reservations", m("id", UUID.randomUUID(), "offreId", offre(ambulant, s), "quantite", 3)));
		var r2 = lire(post(ambulant, "/v1/reservations", m("id", UUID.randomUUID(), "offreId", offre(ambulant, s), "quantite", 2)));
		post(s.grossiste(), "/v1/retraits", m("qrJeton", r1.get("qrJeton").asString(), "appareilId", s.grossiste().appareilId(), "quantiteRemise", 3));
		// Le plafond de la ligne est abaissé entre-temps par la marque (cas limite) : le retrait est recontrôlé.
		jdbc.sql("UPDATE campagne_ligne SET quantite_max_par_ambulant = 4 WHERE campagne_id = :c").param("c", UUID.fromString(s.campagneId())).update();
		assertThat(post(s.grossiste(), "/v1/retraits", m("qrJeton", r2.get("qrJeton").asString(), "appareilId", s.grossiste().appareilId(), "quantiteRemise", 2)))
			.hasStatus(422).bodyJson().extractingPath("$.regle").isEqualTo("RG-P3");
	}

	@Test
	void contrePropositionEtRepartitionDesQuantites() {
		var marque = grandClient();
		var produit = produit(marque.organisationId());
		var campagneId = lire(post(marque, "/v1/campagnes", campagne(produit, 50, 5))).get("id").asString();
		var g1 = grossiste();
		var g2 = grossiste();
		var d1 = lire(post(marque, "/v1/campagnes/" + campagneId + "/depots", m("grossisteId", g1.organisationId(),
				"lignes", List.of(m("produitId", produit, "quantite", 30))))).get("id").asString();
		assertThat(post(marque, "/v1/campagnes/" + campagneId + "/depots", m("grossisteId", g2.organisationId(),
				"lignes", List.of(m("produitId", produit, "quantite", 30))))).hasStatus(422);
		var contre = lire(post(g1, "/v1/depots/" + d1 + "/reponse", m("decision", "CONTRE_PROPOSER",
				"lignes", List.of(m("produitId", produit, "quantite", 10)))));
		assertThat(contre.get("statut").asString()).isEqualTo("CONTRE_PROPOSE");
		assertThat(post(g1, "/v1/depots/" + d1 + "/reponse", m("decision", "ACCEPTER"))).hasStatus(409);
		var accepte = lire(post(marque, "/v1/depots/" + d1 + "/reponse-marque", m("decision", "ACCEPTER")));
		assertThat(accepte.get("statut").asString()).isEqualTo("ACCEPTE");
		assertThat(accepte.get("lignes").get(0).get("quantiteProposee").asInt()).isEqualTo(10);
		assertThat(post(marque, "/v1/campagnes/" + campagneId + "/depots", m("grossisteId", g2.organisationId(),
				"lignes", List.of(m("produitId", produit, "quantite", 40))))).hasStatus(201);
		// Cloisonnement : une autre marque ou un autre grossiste ne voit pas le dépôt.
		assertThat(get(grandClient(), "/v1/depots/" + d1)).hasStatus(404);
		assertThat(get(g2, "/v1/depots/" + d1)).hasStatus(404);
		assertThat(get(grandClient(), "/v1/campagnes/" + campagneId)).hasStatus(404);
	}

	/** Offre visible d'un ambulant pour une campagne au ciblage donné, reçue chez un grossiste de Casablanca. */
	boolean visibleAvecCiblage(Map<String, Object> ciblage) {
		var marque = grandClient();
		var grossiste = grossiste();
		var produit = produit(marque.organisationId());
		var saisie = campagne(produit, 50, 5);
		saisie.put("ciblage", ciblage);
		var campagneId = lire(post(marque, "/v1/campagnes", saisie)).get("id").asString();
		var depotId = lire(post(marque, "/v1/campagnes/" + campagneId + "/depots", m("grossisteId", grossiste.organisationId(),
				"lignes", List.of(m("produitId", produit, "quantite", 10))))).get("id").asString();
		post(grossiste, "/v1/depots/" + depotId + "/reponse", m("decision", "ACCEPTER"));
		recevoir(grossiste, depotId, produit, 10);
		return offre(ambulant(), new Scenario(marque, grossiste, produit, campagneId, depotId)) != null;
	}

	@Test
	void ciblageParRegionEtParVilleAvecLesCodesDuReferentiel() {
		assertThat(visibleAvecCiblage(m("regions", List.of("CASABLANCA_SETTAT")))).isTrue();
		assertThat(visibleAvecCiblage(m("regions", List.of("MARRAKECH_SAFI")))).isFalse();
		assertThat(visibleAvecCiblage(m("villes", List.of("CASABLANCA")))).isTrue();
		assertThat(visibleAvecCiblage(m("villes", List.of("FES")))).isFalse();
	}

	@Test
	void rgP1_campagneFutureAnnonceeSeulementASonDebut() {
		var marque = grandClient();
		var grossiste = grossiste();
		var produit = produit(marque.organisationId());
		var saisie = campagne(produit, 50, 5);
		saisie.put("debut", Instant.now().plus(2, ChronoUnit.HOURS).toString());
		var campagneId = lire(post(marque, "/v1/campagnes", saisie)).get("id").asString();
		var depotId = lire(post(marque, "/v1/campagnes/" + campagneId + "/depots", m("grossisteId", grossiste.organisationId(),
				"lignes", List.of(m("produitId", produit, "quantite", 10))))).get("id").asString();
		post(grossiste, "/v1/depots/" + depotId + "/reponse", m("decision", "ACCEPTER"));
		recevoir(grossiste, depotId, produit, 10);
		var ambulant = ambulant();
		assertThat(lire(get(ambulant, "/v1/promos")).findValuesAsString("depotId")).doesNotContain(depotId);
		assertThat(jdbc.sql("SELECT annonce_le FROM depot WHERE id = :d").param("d", UUID.fromString(depotId))
			.query(java.sql.Timestamp.class).optional()).isEmpty();

		jdbc.sql("UPDATE campagne SET debut = now() - interval '1 minute' WHERE id = :c").param("c", UUID.fromString(campagneId)).update();
		promos.echeances();
		assertThat(lire(get(ambulant, "/v1/promos")).findValuesAsString("depotId")).contains(depotId);
		var offres = lire(get(ambulant, "/v1/sync/changements?limite=1000")).get("changements").findParents("entite").stream()
			.filter(c -> c.get("entite").asString().equals("PROMO") && c.get("action").asString().equals("UPSERT")
					&& c.get("donnees").get("depotId").asString().equals(depotId))
			.count();
		assertThat(offres).isEqualTo(1);
	}

	@Test
	void clotureApresExpirationPuisCampagneCloturee() {
		var s = promoActive();
		assertThat(post(s.grossiste(), "/v1/depots/" + s.depotId() + "/cloture", m("issueReliquat", "RETOURNE"))).hasStatus(409);
		jdbc.sql("UPDATE campagne SET fin = now() - interval '1 minute', debut = now() - interval '2 days' WHERE id = :c")
			.param("c", UUID.fromString(s.campagneId())).update();
		promos.echeances();
		assertThat(lire(get(s.marque(), "/v1/campagnes/" + s.campagneId())).get("statut").asString()).isEqualTo("EXPIREE");
		assertThat(lire(get(ambulant(), "/v1/promos")).findValuesAsString("depotId")).doesNotContain(s.depotId());
		var clos = lire(post(s.grossiste(), "/v1/depots/" + s.depotId() + "/cloture", m("issueReliquat", "RACHETE")));
		assertThat(clos.get("statut").asString()).isEqualTo("CLOTURE");
		assertThat(lire(get(s.marque(), "/v1/campagnes/" + s.campagneId())).get("statut").asString()).isEqualTo("CLOTUREE");
	}

	@Test
	void receptionNotifieLesAmbulantsParPush() throws InterruptedException {
		var ambulant = ambulant();
		put(ambulant, "/v1/appareils/" + ambulant.appareilId() + "/push", m("jeton", "fcm-promo", "plateforme", "ANDROID"));
		promoActive();
		long envois = 0;
		for (int i = 0; i < 50 && envois == 0; i++) {
			Thread.sleep(200);
			envois = jdbc.sql("SELECT count(*) FROM notification WHERE utilisateur_id = :u AND type = 'PROMO_CONFIRMEE' AND canal = 'PUSH'")
				.param("u", ambulant.id()).query(Long.class).single();
		}
		assertThat(envois).isPositive();
	}

	// ---------------------------------------------------------------- ciblage par catégorie (§9)

	/** Produit d'une marque propre au test (historique d'achat), dans une catégorie donnée. */
	UUID produitDeMarque(String marque, String categorie, UUID grandClient) {
		jdbc.sql("INSERT INTO categorie (code, libelle) VALUES (:c, jsonb_build_object('fr', CAST(:c AS text))) ON CONFLICT DO NOTHING")
			.param("c", categorie).update();
		var id = UUID.randomUUID();
		jdbc.sql("""
				INSERT INTO produit (id, ean, libelle, marque, categorie, conditionnement, unites_par_conditionnement, grand_client_id)
				VALUES (:id, :ean, '{"fr":"Produit"}', :m, :c, 'UNITE', 1, :gc)
				""").param("id", id).param("ean", "%013d".formatted(java.util.concurrent.ThreadLocalRandom.current().nextLong(1_000_000_000_000L)))
			.param("m", marque).param("c", categorie).param("gc", grandClient).update();
		return id;
	}

	JsonNode pousser(Compte ambulant, String type, Object donnees) {
		var r = post(ambulant, "/v1/sync/operations", m("appareilId", ambulant.appareilId(), "operations",
				List.of(m("id", UUID.randomUUID(), "type", type, "creeLe", Instant.now().toString(), "donnees", donnees))));
		assertThat(r).hasStatusOk();
		var resultat = lire(r).get("resultats").get(0);
		assertThat(resultat.get("statut").asString()).isEqualTo("APPLIQUEE");
		return resultat;
	}

	Compte ambulantQuiVend(String... categories) {
		for (var c : categories) {
			jdbc.sql("INSERT INTO categorie (code, libelle) VALUES (:c, jsonb_build_object('fr', CAST(:c AS text))) ON CONFLICT DO NOTHING")
				.param("c", c).update();
		}
		var a = ambulant();
		pousser(a, "PROFIL_AMBULANT", m("nom", "Ambulant " + String.join(" ", categories), "categories", List.of(categories)));
		return a;
	}

	/** Offres PROMO présentes sur l'appareil après une synchronisation complète. */
	java.util.Set<String> promosSurLAppareil(Compte ambulant) {
		var ids = new java.util.HashSet<String>();
		String curseur = null;
		for (;;) {
			var lot = lire(get(ambulant, "/v1/sync/changements?limite=1000" + (curseur == null ? "" : "&curseur=" + curseur)));
			for (var c : lot.get("changements")) {
				if (c.get("entite").asString().equals("PROMO")) {
					if (c.get("action").asString().equals("UPSERT")) {
						ids.add(c.get("id").asString());
					}
					else {
						ids.remove(c.get("id").asString());
					}
				}
			}
			curseur = lot.get("curseur").asString();
			if (!lot.get("plusDeChangements").asBoolean()) {
				return ids;
			}
		}
	}

	/** Campagne ciblée sur BOISSONS, sur un produit BOISSONS d'une marque propre au test, reçue chez un grossiste. */
	Scenario promoCiblee(String marqueProduit) {
		var marque = grandClient();
		var grossiste = grossiste();
		var produit = produitDeMarque(marqueProduit, "BOISSONS", marque.organisationId());
		var saisie = campagne(produit, 100, 5);
		saisie.put("ciblage", m("villes", List.of("CASABLANCA"), "categories", List.of("BOISSONS")));
		var campagneId = lire(post(marque, "/v1/campagnes", saisie)).get("id").asString();
		var depotId = lire(post(marque, "/v1/campagnes/" + campagneId + "/depots", m("grossisteId", grossiste.organisationId(),
				"lignes", List.of(m("produitId", produit, "quantite", 20))))).get("id").asString();
		post(grossiste, "/v1/depots/" + depotId + "/reponse", m("decision", "ACCEPTER"));
		recevoir(grossiste, depotId, produit, 20);
		return new Scenario(marque, grossiste, produit, campagneId, depotId);
	}

	@Test
	void ciblageParCategorie_visibleSiLAmbulantVendLaCategorie_ouNEnDeclareAucune() {
		produitDeMarque("Autre", "EPICERIE", null);
		var s = promoCiblee("Marque-" + UUID.randomUUID());
		var vendeur = ambulantQuiVend("EPICERIE", "BOISSONS");
		var autre = ambulantQuiVend("EPICERIE");
		var neuf = ambulant();
		var offre = offre(vendeur, s);
		assertThat(offre).isNotNull();
		assertThat(offre(neuf, s)).isEqualTo(offre);
		assertThat(offre(autre, s)).isNull();
		assertThat(post(autre, "/v1/reservations", m("id", UUID.randomUUID(), "offreId", offre, "quantite", 1))).hasStatus(404);

		// Hors ligne : l'offre n'arrive que chez les ambulants visés.
		assertThat(promosSurLAppareil(vendeur)).contains(offre);
		assertThat(promosSurLAppareil(neuf)).contains(offre);
		assertThat(promosSurLAppareil(autre)).doesNotContain(offre);

		// Le vendeur ne déclare plus BOISSONS : l'offre quitte son fil et son appareil ; l'autre l'ajoute et la reçoit.
		pousser(vendeur, "PROFIL_AMBULANT", m("nom", "Vendeur", "categories", List.of("EPICERIE")));
		pousser(autre, "PROFIL_AMBULANT", m("nom", "Autre", "categories", List.of("BOISSONS")));
		assertThat(offre(vendeur, s)).isNull();
		assertThat(promosSurLAppareil(vendeur)).doesNotContain(offre);
		assertThat(promosSurLAppareil(autre)).contains(offre);
		// Une réservation met à jour l'offre chez les seuls ambulants visés.
		assertThat(post(autre, "/v1/reservations", m("id", UUID.randomUUID(), "offreId", offre, "quantite", 2))).hasStatus(201);
		assertThat(promosSurLAppareil(vendeur)).doesNotContain(offre);
	}

	@Test
	void ciblageParCategorie_visibleSiLAmbulantADejaAcheteLaMarque() {
		var marqueProduit = "Marque-" + UUID.randomUUID();
		var s = promoCiblee(marqueProduit);
		var ambulant = ambulantQuiVend("EPICERIE");
		assertThat(offre(ambulant, s)).isNull();
		// Chargement d'un autre produit de la même marque (hors catégorie ciblée) : il a acheté la marque.
		var memeMarque = produitDeMarque(marqueProduit, "EPICERIE", null);
		pousser(ambulant, "CHARGEMENT", m("lignes", List.of(m("produitId", memeMarque, "quantite", 3))));
		var offre = offre(ambulant, s);
		assertThat(offre).isNotNull();
		assertThat(promosSurLAppareil(ambulant)).contains(offre);
		// Un chargement d'une autre marque ne suffit pas.
		var autre = ambulantQuiVend("EPICERIE");
		pousser(autre, "CHARGEMENT", m("lignes", List.of(m("produitId", produitDeMarque("Autre-" + UUID.randomUUID(), "EPICERIE", null),
				"quantite", 3))));
		assertThat(offre(autre, s)).isNull();
	}

	@Test
	void ciblageParCategorie_notificationPromoConfirmeeAuxSeulsAmbulantsVises() throws InterruptedException {
		var vise = ambulantQuiVend("BOISSONS");
		var nonVise = ambulantQuiVend("EPICERIE");
		var s = promoCiblee("Marque-" + UUID.randomUUID());
		for (int i = 0; i < 50 && jdbc.sql("""
				SELECT count(*) FROM event_publication WHERE completion_date IS NULL AND serialized_event LIKE :d
				""").param("d", "%" + s.depotId() + "%").query(Long.class).single() > 0; i++) {
			Thread.sleep(200);
		}
		var compte = (java.util.function.Function<Compte, Long>) a -> jdbc.sql(
				"SELECT count(*) FROM notification WHERE utilisateur_id = :u AND type = 'PROMO_CONFIRMEE'")
			.param("u", a.id()).query(Long.class).single();
		assertThat(compte.apply(vise)).isPositive();
		assertThat(compte.apply(nonVise)).isZero();
	}

	// ---------------------------------------------------------------- ciblage par zone de tournée et rayon (§9)

	/** Campagne au ciblage donné, reçue chez un nouveau grossiste de Casablanca (lon -7.6, lat 33.58). */
	Scenario promoAvecCiblage(Map<String, Object> ciblage) {
		var marque = grandClient();
		var grossiste = grossiste();
		var produit = produit(marque.organisationId());
		var saisie = campagne(produit, 50, 5);
		saisie.put("ciblage", ciblage);
		var campagneId = lire(post(marque, "/v1/campagnes", saisie)).get("id").asString();
		var depotId = lire(post(marque, "/v1/campagnes/" + campagneId + "/depots", m("grossisteId", grossiste.organisationId(),
				"lignes", List.of(m("produitId", produit, "quantite", 10))))).get("id").asString();
		post(grossiste, "/v1/depots/" + depotId + "/reponse", m("decision", "ACCEPTER"));
		recevoir(grossiste, depotId, produit, 10);
		return new Scenario(marque, grossiste, produit, campagneId, depotId);
	}

	void tournee(Compte ambulant, UUID... hanouts) {
		pousser(ambulant, "TOURNEE", m("id", UUID.randomUUID(), "jours", List.of(1, 3), "hanoutIds", List.of(hanouts)));
	}

	void attendreLesNotifications(String depotId) throws InterruptedException {
		for (int i = 0; i < 50 && jdbc.sql("""
				SELECT count(*) FROM event_publication WHERE completion_date IS NULL AND serialized_event LIKE :d
				""").param("d", "%" + depotId + "%").query(Long.class).single() > 0; i++) {
			Thread.sleep(200);
		}
	}

	long promosConfirmees(Compte ambulant) {
		return jdbc.sql("SELECT count(*) FROM notification WHERE utilisateur_id = :u AND type = 'PROMO_CONFIRMEE'")
			.param("u", ambulant.id()).query(Long.class).single();
	}

	@Test
	void ciblageParRayon_zoneDeTourneeDesTournees_sinonDesVentesDes30Jours_sinonAucunFiltre() throws InterruptedException {
		var proche = hanout(33.585, -7.6); // ~550 m du dépositaire
		var loin = hanout(34.02, -6.84); // Rabat, ~85 km
		var neuf = ambulant();
		var enTourneeLoin = ambulant();
		tournee(enTourneeLoin, loin);
		var venduLoin = ambulant();
		pousser(venduLoin, "EMISSION_DOCUMENT", m("documentId", UUID.randomUUID(), "type", "BON_LIVRAISON",
				"numero", venduLoin.prefixe() + "-BL-000001", "sequence", 1, "hanoutId", loin,
				"lignes", List.of(m("nature", "ARTICLE", "produitId", produit(), "quantite", 1, "prixUnitaire", 500))));
		var s = promoAvecCiblage(m("rayonM", 5000));
		var offre = offre(neuf, s);

		// Compte neuf (ni tournée ni vente) : pas de filtre, son fil n'est pas vide.
		assertThat(offre).isNotNull();
		assertThat(offre(enTourneeLoin, s)).isNull();
		assertThat(offre(venduLoin, s)).isNull();
		assertThat(promosSurLAppareil(neuf)).contains(offre);
		assertThat(promosSurLAppareil(enTourneeLoin)).doesNotContain(offre);
		// Même règle pour la notification « promo confirmée ».
		attendreLesNotifications(s.depotId());
		assertThat(promosConfirmees(neuf)).isPositive();
		assertThat(promosConfirmees(enTourneeLoin)).isZero();
		assertThat(promosConfirmees(venduLoin)).isZero();

		// Le hanout de la zone le plus proche du dépositaire compte ; l'offre arrive sur l'appareil.
		tournee(enTourneeLoin, loin, proche);
		assertThat(offre(enTourneeLoin, s)).isEqualTo(offre);
		assertThat(promosSurLAppareil(enTourneeLoin)).contains(offre);
		// Une tournée prime sur les ventes des 30 derniers jours.
		tournee(venduLoin, proche);
		assertThat(offre(venduLoin, s)).isEqualTo(offre);
	}

	@Test
	void ciblageParZones_auMoinsUnHanoutDeLaTourneeDansUnPolygone() throws InterruptedException {
		var dedans = hanout(33.581, -7.601);
		var dehors = hanout(34.02, -6.84);
		var carre = m("type", "Polygon", "coordinates", List.of(List.of(List.of(-7.61, 33.57), List.of(-7.59, 33.57),
				List.of(-7.59, 33.59), List.of(-7.61, 33.59), List.of(-7.61, 33.57))));
		var dansLaZone = ambulant();
		tournee(dansLaZone, dehors, dedans);
		var horsZone = ambulant();
		tournee(horsZone, dehors);
		var s = promoAvecCiblage(m("zones", List.of(carre)));
		assertThat(offre(dansLaZone, s)).isNotNull();
		assertThat(offre(horsZone, s)).isNull();
		attendreLesNotifications(s.depotId());
		assertThat(promosConfirmees(dansLaZone)).isPositive();
		assertThat(promosConfirmees(horsZone)).isZero();

		// Polygone illisible ou rayon hors bornes : campagne refusée.
		var marque = grandClient();
		var saisie = campagne(produit(marque.organisationId()), 10, 5);
		saisie.put("ciblage", m("zones", List.of(m("type", "Polygon", "coordinates", List.of(List.of(List.of(0, 0), List.of(1, 1)))))));
		assertThat(post(marque, "/v1/campagnes", saisie)).hasStatus(400);
		saisie.put("ciblage", m("rayonM", 100));
		assertThat(post(marque, "/v1/campagnes", saisie)).hasStatus(400);
	}

	@Test
	void prioriteAuxAmbulantsRattaches_offresDuDepositaireRattacheDAbord_etNotifiesEnPremier() {
		var autre = promoActive();
		var fournisseur = promoActive();
		var ambulant = ambulant();
		pousser(ambulant, "PROFIL_AMBULANT", m("nom", "Rattaché", "grossisteIds", List.of(fournisseur.grossiste().organisationId())));
		var contenu = lire(get(ambulant, "/v1/promos?taille=200")).get("contenu");
		int iFournisseur = -1;
		int iAutre = -1;
		for (int i = 0; i < contenu.size(); i++) {
			var depot = contenu.get(i).get("depotId").asString();
			iFournisseur = depot.equals(fournisseur.depotId()) ? i : iFournisseur;
			iAutre = depot.equals(autre.depotId()) ? i : iAutre;
		}
		assertThat(iFournisseur).isNotNegative().isLessThan(iAutre);
		assertThat(contenu.get(iFournisseur).get("rattache").asBoolean()).isTrue();
		assertThat(contenu.get(iAutre).get("rattache").asBoolean()).isFalse();
		assertThat(contenu.get(0).get("rattache").asBoolean()).isTrue();

		// « Promo confirmée » : les rattachés au dépositaire en tête du même envoi.
		assertThat(promos.ambulantsVises(UUID.fromString(fournisseur.depotId())).getFirst()).isEqualTo(ambulant.id());
	}

	@Test
	void reservationMontreLeNomDeLAmbulantAuGrossiste() {
		var s = promoActive();
		var ambulant = ambulant();
		pousser(ambulant, "PROFIL_AMBULANT", m("nom", "Hamid Bennis"));
		var id = UUID.randomUUID();
		post(ambulant, "/v1/reservations", m("id", id, "offreId", offre(ambulant, s), "quantite", 1));
		assertThat(lire(get(s.grossiste(), "/v1/reservations/" + id)).get("ambulant").get("nom").asString()).isEqualTo("Hamid Bennis");
		assertThat(lire(get(s.grossiste(), "/v1/reservations?depotId=" + s.depotId())).get("contenu").get(0).get("ambulant")
			.get("nom").asString()).isEqualTo("Hamid Bennis");
	}

}

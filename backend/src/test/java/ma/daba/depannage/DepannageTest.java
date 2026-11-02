package ma.daba.depannage;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import ma.daba.TestIntegration;
import tools.jackson.databind.JsonNode;

class DepannageTest extends TestIntegration {

	static final double LAT = 33.57;
	static final double LON = -7.59;

	@Autowired
	Depannage depannage;

	void disponible(Compte ambulant, double lat, double lon) {
		assertThat(put(ambulant, "/v1/ambulant/disponibilite", m("disponible", true, "position", m("lat", lat, "lon", lon))))
			.hasStatusOk();
	}

	void charger(Compte ambulant, UUID produit, int quantite) {
		post(ambulant, "/v1/sync/operations", m("appareilId", ambulant.appareilId(), "operations", List.of(m("id", UUID.randomUUID(),
				"type", "CHARGEMENT", "creeLe", Instant.now().toString(),
				"donnees", m("lignes", List.of(m("produitId", produit, "quantite", quantite)))))));
	}

	/** Ambulant disponible, à {@code dLat} degrés au nord du hanout, avec ou sans le produit. */
	Compte ambulant(double dLat, UUID produitEnStock) {
		var a = ambulant();
		if (produitEnStock != null) {
			charger(a, produitEnStock, 10);
		}
		disponible(a, LAT + dLat, LON);
		return a;
	}

	String signaler(Compte hanout, UUID produit) {
		var r = post(hanout, "/v1/demandes-depannage", m("id", UUID.randomUUID(), "produitId", produit, "quantite", 6));
		assertThat(r).hasStatus(201);
		return lire(r).get("id").asString();
	}

	List<String> proposees(Compte ambulant) {
		return lire(get(ambulant, "/v1/demandes-depannage")).get("contenu").findValuesAsString("id").stream()
			.filter(s -> s.length() == 36).toList();
	}

	@Test
	void rgD1_demandeEnvoyeeAuxSeulsAmbulantsDisponiblesDuRayonAyantLeProduit() {
		var produit = produit();
		var hanout = compteHanout(hanout(LAT, LON));
		var proche = ambulant(0.01, produit);       // ≈ 1,1 km, produit en stock
		var sansStock = ambulant(0.01, null);       // ≈ 1,1 km, sans le produit
		var loin = ambulant(0.09, produit);         // ≈ 10 km
		var indisponible = ambulant();
		charger(indisponible, produit, 10);
		var demande = signaler(hanout, produit);

		assertThat(proposees(proche)).contains(demande);
		assertThat(proposees(sansStock)).doesNotContain(demande);
		assertThat(proposees(loin)).doesNotContain(demande);
		assertThat(proposees(indisponible)).doesNotContain(demande);
		var vue = lire(get(proche, "/v1/demandes-depannage/" + demande)).get("hanout");
		assertThat(vue.get("distanceM").asInt()).isBetween(1000, 1250);
		assertThat(vue.get("solde").asLong()).isZero();
		assertThat(get(sansStock, "/v1/demandes-depannage/" + demande)).hasStatus(404);
	}

	@Test
	void rgD2_lePremierQuiAccepteLaPrendLesAutresRecoivent409() {
		var produit = produit();
		var hanout = compteHanout(hanout(LAT, LON));
		var premier = ambulant(0.005, produit);
		var second = ambulant(0.006, produit);
		var demande = signaler(hanout, produit);

		assertThat(post(premier, "/v1/demandes-depannage/" + demande + "/acceptation", null)).hasStatusOk()
			.bodyJson().extractingPath("$.statut").isEqualTo("ACCEPTEE");
		assertThat(post(second, "/v1/demandes-depannage/" + demande + "/acceptation", null)).hasStatus(409)
			.bodyJson().extractingPath("$.regle").isEqualTo("RG-D2");
		assertThat(post(premier, "/v1/demandes-depannage/" + demande + "/acceptation", null)).hasStatusOk();
		assertThat(proposees(second)).doesNotContain(demande);
		assertThat(post(second, "/v1/demandes-depannage/" + demande + "/en-route", null)).hasStatus(409);

		// Le hanout suit sa demande et voit l'ambulant qui l'a prise.
		var suivi = lire(get(hanout, "/v1/demandes-depannage/" + demande));
		assertThat(suivi.get("ambulant").get("telephone").asString()).isEqualTo(premier.telephone());
		assertThat(suivi.has("hanout")).isFalse();
	}

	@Test
	void rgD2_refusFaitDisparaitreLaDemande() {
		var produit = produit();
		var hanout = compteHanout(hanout(LAT, LON));
		var ambulant = ambulant(0.004, produit);
		var demande = signaler(hanout, produit);
		assertThat(post(ambulant, "/v1/demandes-depannage/" + demande + "/refus", null)).hasStatus(204);
		assertThat(proposees(ambulant)).doesNotContain(demande);
	}

	@Test
	void rgD3_sansAcceptationLeRayonSElargitUneFoisPuisLeHanoutEstPrevenu() {
		var produit = produit();
		var hanout = compteHanout(hanout(LAT, LON));
		var a5km = ambulant(0.045, produit); // ≈ 5 km : hors du rayon initial (3 km), dans le rayon élargi (6 km)
		var demande = signaler(hanout, produit);
		assertThat(proposees(a5km)).doesNotContain(demande);

		expirer(demande);
		depannage.delais();
		assertThat(lire(get(hanout, "/v1/demandes-depannage/" + demande)).get("rayonElargi").asBoolean()).isTrue();
		assertThat(proposees(a5km)).contains(demande);

		expirer(demande);
		depannage.delais();
		var finale = lire(get(hanout, "/v1/demandes-depannage/" + demande));
		assertThat(finale.get("statut").asString()).isEqualTo("SANS_REPONSE");
		assertThat(finale.get("historique").findValuesAsString("statut")).containsExactly("EN_ATTENTE", "SANS_REPONSE");
	}

	private void expirer(String demande) {
		jdbc.sql("UPDATE demande SET expire_le = now() - interval '1 second' WHERE id = :id").param("id", UUID.fromString(demande)).update();
	}

	@Test
	void rgD4_laLivraisonCreeUneVenteLieeALaDemande() {
		var produit = produit();
		var hanoutId = hanout(LAT, LON);
		var hanout = compteHanout(hanoutId);
		var ambulant = ambulant(0.005, produit);
		var demande = signaler(hanout, produit);
		post(ambulant, "/v1/demandes-depannage/" + demande + "/acceptation", null);
		assertThat(post(ambulant, "/v1/demandes-depannage/" + demande + "/en-route", null)).hasStatusOk();

		var documentId = UUID.randomUUID();
		var r = lire(post(ambulant, "/v1/sync/operations", m("appareilId", ambulant.appareilId(), "operations", List.of(
				livraison(ambulant, 1, documentId, hanoutId, produit, demande)))));
		assertThat(r.get("resultats").get(0).get("statut").asString()).isEqualTo("APPLIQUEE");
		var livree = lire(get(hanout, "/v1/demandes-depannage/" + demande));
		assertThat(livree.get("statut").asString()).isEqualTo("LIVREE");
		assertThat(livree.get("documentId").asString()).isEqualTo(documentId.toString());
		assertThat(lire(get(hanout, "/v1/documents/" + documentId)).get("demandeDepannageId").asString()).isEqualTo(demande);

		// Un autre ambulant qui « livre » cette demande : vente gardée, anomalie levée.
		var intrus = ambulant();
		charger(intrus, produit, 6);
		var r2 = lire(post(intrus, "/v1/sync/operations", m("appareilId", intrus.appareilId(), "operations", List.of(
				livraison(intrus, 1, UUID.randomUUID(), hanoutId, produit, demande)))));
		assertThat(r2.get("resultats").get(0).get("statut").asString()).isEqualTo("APPLIQUEE");
		assertThat(r2.get("resultats").get(0).get("anomalies").get(0).get("type").asString()).isEqualTo("DEMANDE_NON_ATTRIBUEE");
		assertThat(r2.get("resultats").get(0).get("anomalies").get(0).get("regle").asString()).isEqualTo("RG-D4");
	}

	static java.util.Map<String, Object> livraison(Compte ambulant, int sequence, UUID documentId, UUID hanout, UUID produit, String demande) {
		return m("id", UUID.randomUUID(), "type", "EMISSION_DOCUMENT", "creeLe", Instant.now().toString(), "donnees",
				m("documentId", documentId, "type", "BON_LIVRAISON", "numero", "%s-BL-%06d".formatted(ambulant.prefixe(), sequence),
						"sequence", sequence, "hanoutId", hanout, "demandeDepannageId", demande,
						"lignes", List.of(m("nature", "ARTICLE", "produitId", produit, "quantite", 6, "prixUnitaire", 500))));
	}

	@Test
	void cndp_positionSeulementAvecConsentementEtEffaceeQuandIndisponible() throws InterruptedException {
		var ambulant = ambulant();
		assertThat(put(ambulant, "/v1/ambulant/disponibilite", m("disponible", false, "position", m("lat", LAT, "lon", LON))))
			.hasStatus(400);
		disponible(ambulant, LAT, LON);
		assertThat(lire(get(ambulant, "/v1/moi/donnees")).get("disponibilite").get(0).get("lat").asDouble()).isEqualTo(LAT);

		put(ambulant, "/v1/moi/consentement", m("versionTexte", "v2", "traitementDonnees", true, "geolocalisation", false));
		JsonNode export = null;
		for (int i = 0; i < 50; i++) {
			export = lire(get(ambulant, "/v1/moi/donnees")).get("disponibilite").get(0);
			if (!export.get("disponible").asBoolean()) {
				break;
			}
			Thread.sleep(100);
		}
		assertThat(export.get("disponible").asBoolean()).isFalse();
		assertThat(export.hasNonNull("lat")).isFalse();
		assertThat(put(ambulant, "/v1/ambulant/disponibilite", m("disponible", true, "position", m("lat", LAT, "lon", LON))))
			.hasStatus(422);
	}

	@Test
	void signalementEnPhotoSansProduit() {
		var hanout = compteHanout(hanout(LAT, LON));
		var rupture = new MockMultipartFile("rupture", "", "application/json",
				json.writeValueAsBytes(m("id", UUID.randomUUID(), "quantite", 2)));
		var photo = new MockMultipartFile("photo", "p.jpg", "image/jpeg", new byte[] { (byte) 0xFF, (byte) 0xD8 });
		var r = mvc.post().uri("/v1/demandes-depannage").multipart().file(rupture).file(photo)
			.header("Authorization", "Bearer " + hanout.jeton()).exchange();
		assertThat(r).hasStatus(201);
		var photoUrl = lire(r).get("photoUrl").asString();
		assertThat(get(hanout, photoUrl.substring(photoUrl.indexOf("/v1/")))).hasStatusOk();
		assertThat(post(hanout, "/v1/demandes-depannage", m("id", UUID.randomUUID(), "quantite", 2))).hasStatus(400);
	}

	@Test
	void carteDesRupturesAgregeeEtAnonymisee() {
		var marque = grandClient();
		var produit = produit(marque.organisationId());
		// Trois hanouts distincts dans la même cellule, deux dans une autre (sous le seuil d'anonymat).
		for (int i = 0; i < 3; i++) {
			signaler(compteHanout(hanout(33.5900 + i * 0.0005, -7.6100)), produit);
		}
		var isole = hanout(33.6500, -7.5000);
		signaler(compteHanout(isole), produit);
		signaler(compteHanout(hanout(33.6501, -7.5001)), produit);

		var carte = get(marque, "/v1/analytique/ruptures?produitId=" + produit + "&precision=5");
		var cellules = lire(carte).get("cellules");
		assertThat(cellules.size()).isEqualTo(1);
		assertThat(cellules.get(0).get("signalements").asInt()).isEqualTo(3);
		assertThat(cellules.get(0).get("enCours").asInt()).isEqualTo(3);
		assertThat(cellules.get(0).get("geohash").asString()).hasSize(5);
		assertThat(new String(carte.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8)).doesNotContain(isole.toString());
		assertThat(get(grandClient(), "/v1/analytique/ruptures?produitId=" + produit)).hasStatus(404);
	}

}

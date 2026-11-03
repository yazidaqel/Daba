package ma.daba;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import ma.daba.commun.Acces.Roles;
import ma.daba.commun.Appelant.Role;

/**
 * CNDP (§5.4) : aucune réponse à un grand client ne contient de donnée nominative d'un ambulant ou
 * d'un hanout, même quand ils ont vendu, retiré et signalé des ruptures sur ses produits.
 */
class CndpGrandClientTest extends TestIntegration {

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	RequestMappingHandlerMapping routes;

	void pousser(Compte ambulant, String type, Object donnees) {
		var r = post(ambulant, "/v1/sync/operations", m("appareilId", ambulant.appareilId(), "operations",
				List.of(m("id", UUID.randomUUID(), "type", type, "creeLe", Instant.now().toString(), "donnees", donnees))));
		assertThat(lire(r).get("resultats").get(0).get("statut").asString()).isEqualTo("APPLIQUEE");
	}

	@Test
	void cndp_aucuneDonneeNominativeDAmbulantNiDeHanoutVersLeGrandClient() {
		var marque = grandClient();
		var grossiste = grossiste();
		var produit = produit(marque.organisationId());

		// Promo reçue, réservée, retirée et revendue par un ambulant nommé ; ruptures signalées par 3 hanouts proches.
		var campagne = lire(post(marque, "/v1/campagnes", m("nom", m("fr", "Promo CNDP"),
				"debut", Instant.now().minus(1, ChronoUnit.HOURS).toString(), "fin", Instant.now().plus(5, ChronoUnit.DAYS).toString(),
				"lignes", List.of(m("produitId", produit, "remise", m("type", "POURCENTAGE", "pourcentage", 10),
						"quantiteTotale", 50, "quantiteMaxParAmbulant", 10))))).get("id").asString();
		var depot = lire(post(marque, "/v1/campagnes/" + campagne + "/depots", m("grossisteId", grossiste.organisationId(),
				"lignes", List.of(m("produitId", produit, "quantite", 30))))).get("id").asString();
		post(grossiste, "/v1/depots/" + depot + "/reponse", m("decision", "ACCEPTER"));
		var reception = new MockMultipartFile("reception", "", "application/json",
				json.writeValueAsBytes(m("lignes", List.of(m("produitId", produit, "quantiteRecue", 30)))));
		var photo = new MockMultipartFile("photoBonLivraison", "bl.jpg", "image/jpeg", new byte[] { (byte) 0xFF, (byte) 0xD8, 1 });
		assertThat(mvc.post().uri("/v1/depots/" + depot + "/reception").multipart().file(reception).file(photo)
			.header("Authorization", "Bearer " + grossiste.jeton())).hasStatusOk();

		var ambulant = ambulant();
		var nomAmbulant = "Rachid Cndp" + System.nanoTime();
		pousser(ambulant, "PROFIL_AMBULANT", m("nom", nomAmbulant, "cin", "ZT998877", "categories", List.of("BOISSONS")));
		var offre = lire(get(ambulant, "/v1/promos?taille=200")).get("contenu").findParents("depotId").stream()
			.filter(o -> o.get("depotId").asString().equals(depot)).findFirst().orElseThrow().get("id").asString();
		var resa = lire(post(ambulant, "/v1/reservations", m("id", UUID.randomUUID(), "offreId", offre, "quantite", 6)));
		post(grossiste, "/v1/retraits", m("qrJeton", resa.get("qrJeton").asString(), "appareilId", grossiste.appareilId(),
				"quantiteRemise", 6));

		var hanouts = new ArrayList<Map<String, Object>>();
		for (int i = 0; i < 3; i++) {
			var id = hanout(33.5900 + i * 0.0005, -7.6100);
			var nom = "Hanout Cndp " + System.nanoTime();
			var gerant = "Gerant Cndp " + System.nanoTime();
			jdbc.sql("UPDATE hanout SET nom = :n, gerant = :g WHERE id = :id").param("n", nom).param("g", gerant).param("id", id).update();
			var tel = jdbc.sql("SELECT telephone FROM hanout WHERE id = :id").param("id", id).query(String.class).single();
			hanouts.add(m("id", id, "nom", nom, "gerant", gerant, "telephone", tel));
			post(compteHanout(id), "/v1/demandes-depannage", m("id", UUID.randomUUID(), "produitId", produit, "quantite", 2));
		}
		var premier = (UUID) hanouts.get(0).get("id");
		pousser(ambulant, "EMISSION_DOCUMENT", m("documentId", UUID.randomUUID(), "type", "BON_LIVRAISON",
				"numero", ambulant.prefixe() + "-BL-000001", "sequence", 1, "hanoutId", premier,
				"lignes", List.of(m("nature", "ARTICLE", "produitId", produit, "quantite", 6, "prixUnitaire", 700,
						"campagneId", campagne, "reservationId", resa.get("id").asString()))));

		var interdits = new ArrayList<String>(List.of(nomAmbulant, "ZT998877", ambulant.telephone(), ambulant.id().toString(),
				"+2126•"));
		for (var h : hanouts) {
			h.values().forEach(v -> interdits.add(v.toString()));
		}

		var chemins = List.of("/v1/campagnes", "/v1/campagnes/" + campagne, "/v1/campagnes/" + campagne + "/depots",
				"/v1/campagnes/" + campagne + "/entonnoir", "/v1/depots/" + depot, "/v1/analytique/ruptures?precision=5",
				"/v1/produits?mesProduits=true", "/v1/produits/" + produit, "/v1/categories", "/v1/regions", "/v1/grossistes",
				"/v1/moi/consentement", "/v1/moi/donnees");
		var entonnoir = lire(get(marque, "/v1/campagnes/" + campagne + "/entonnoir")).get("total");
		assertThat(entonnoir.get("revendu").asInt()).isEqualTo(6);
		assertThat(lire(get(marque, "/v1/analytique/ruptures?precision=5")).get("cellules").size()).isPositive();
		for (var chemin : chemins) {
			var r = get(marque, chemin);
			assertThat(r).as(chemin).hasStatusOk();
			var texte = new String(r.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
			for (var interdit : interdits) {
				assertThat(texte).as(chemin).doesNotContain(interdit);
			}
		}

		// Toute lecture ouverte au grand client est couverte ci-dessus : une nouvelle doit être ajoutée au test.
		var couverts = chemins.stream().map(c -> c.replaceAll("\\?.*", "").replace(campagne, "{campagneId}")
			.replace(depot, "{depotId}").replace(produit.toString(), "{produitId}")).toList();
		var lectures = routes.getHandlerMethods().entrySet().stream()
			.filter(e -> e.getKey().getMethodsCondition().getMethods().contains(RequestMethod.GET))
			.filter(e -> e.getValue().hasMethodAnnotation(Roles.class)
					&& Arrays.asList(e.getValue().getMethodAnnotation(Roles.class).value()).contains(Role.GRAND_CLIENT))
			.flatMap(e -> e.getKey().getPatternValues().stream())
			.filter(chemin -> !chemin.equals("/v1/photos/{photoId}")) // identifiant non devinable, photo d'un bon ou d'un produit
			.toList();
		assertThat(lectures).hasSizeGreaterThanOrEqualTo(12);
		assertThat(couverts).containsAll(lectures);
	}

}

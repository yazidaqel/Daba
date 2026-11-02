package ma.daba.referentiel;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import ma.daba.TestIntegration;
import tools.jackson.databind.JsonNode;

class ReferentielTest extends TestIntegration {

	private tools.jackson.databind.JsonNode importer(Compte admin, String chemin, String csv) {
		var fichier = new MockMultipartFile("fichier", "base.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
		return lire(mvc.post().uri(chemin).multipart().file(fichier).header("Authorization", "Bearer " + admin.jeton()).exchange());
	}

	@Test
	void importDeLaBasePosRedPuisRechercheParProximite() {
		var admin = admin();
		var code = "POS" + System.nanoTime();
		var csv = """
				code_pos;nom;gerant;telephone;lat;lon;adresse;ville;ice
				%1$s-1;Hanout Al Amal;Said;0612345678;33,5731;-7,5898;Rue 1;Casablanca;
				%1$s-2;"Epicerie ""Nour""\";Fatima;;33.5800;-7.6000;Rue 2;Casablanca;001234567000089
				%1$s-3;Sans position;;;;;;Casablanca;
				%1$s-4;Mauvais tel;;12345;33.6;-7.6;;Casablanca;
				""".formatted(code);
		var bilan = importer(admin, "/v1/admin/hanouts/import", csv);
		assertThat(bilan.get("crees").asInt()).isEqualTo(2);
		assertThat(bilan.get("rejets").size()).isEqualTo(2);
		assertThat(bilan.get("rejets").get(0).get("ligne").asInt()).isEqualTo(4);

		// Réimport : mise à jour, pas de doublon.
		var maj = importer(admin, "/v1/admin/hanouts/import", csv);
		assertThat(maj.get("misAJour").asInt()).isEqualTo(2);

		var ambulant = ambulant();
		var page = lire(get(ambulant, "/v1/hanouts?lat=33.5731&lon=-7.5898&rayonM=5000&q=Amal"));
		assertThat(page.get("contenu").get(0).get("telephone").asString()).isEqualTo("+212612345678");
		assertThat(page.get("contenu").get(0).get("distanceM").asInt()).isLessThan(5);
		var nour = lire(get(ambulant, "/v1/hanouts?q=Nour"));
		assertThat(nour.get("contenu").get(0).get("nom").asString()).isEqualTo("Epicerie \"Nour\"");
		assertThat(get(ambulant, "/v1/hanouts?lat=33.5")).hasStatus(400);
	}

	@Test
	void importDesProduitsAvecLibellesMultilinguesEtRecherche() {
		var admin = admin();
		var ean = "%013d".formatted(java.util.concurrent.ThreadLocalRandom.current().nextLong(1_000_000_000_000L));
		var csv = """
				ean,libelle_fr,libelle_ar,libelle_ary_arab,libelle_ary_latn,marque,categorie,conditionnement,unites
				%s,Thé vert 200g,شاي أخضر,أتاي,Atay,Sultan,EPICERIE,PACK,10
				123,Mauvais EAN,,,,X,EPICERIE,UNITE,1
				""".formatted(ean);
		var bilan = importer(admin, "/v1/admin/produits/import", csv);
		assertThat(bilan.get("crees").asInt()).isEqualTo(1);
		assertThat(bilan.get("rejets").size()).isEqualTo(1);

		var trouve = lire(get(ambulant(), "/v1/produits?q=Atay"));
		assertThat(trouve.get("contenu").get(0).get("libelle").get("ar").asString()).isEqualTo("شاي أخضر");
		assertThat(trouve.get("contenu").get(0).get("unitesParConditionnement").asInt()).isEqualTo(10);
		assertThat(lire(get(ambulant(), "/v1/produits?ean=" + ean)).get("total").asInt()).isEqualTo(1);
	}

	@Test
	void produitParIdFiltresIdsEtMesProduitsCategoriesEtRegions() {
		var marque = grandClient();
		var aMoi = produit(marque.organisationId());
		var autre = produit();
		var grossiste = grossiste();
		assertThat(lire(get(grossiste, "/v1/produits/" + aMoi)).get("id").asString()).isEqualTo(aMoi.toString());
		assertThat(get(grossiste, "/v1/produits/" + UUID.randomUUID())).hasStatus(404);

		var parIds = lire(get(grossiste, "/v1/produits?ids=" + aMoi + "," + autre));
		assertThat(parIds.get("total").asInt()).isEqualTo(2);
		var miens = lire(get(marque, "/v1/produits?mesProduits=true&taille=200"));
		assertThat(miens.get("total").asInt()).isEqualTo(1);
		assertThat(miens.get("contenu").get(0).get("id").asString()).isEqualTo(aMoi.toString());

		assertThat(lire(get(marque, "/v1/categories")).findValues("code").stream().map(c -> c.asString()))
			.contains("BOISSONS");
		var regions = lire(get(marque, "/v1/regions"));
		assertThat(regions.size()).isEqualTo(12);
		JsonNode casa = null;
		for (var r : regions) {
			if (r.get("code").asString().equals("CASABLANCA_SETTAT")) {
				casa = r;
			}
		}
		assertThat(casa.get("nom").get("ar").asString()).isEqualTo("الدار البيضاء سطات");
		assertThat(casa.get("nom").get("ary-Arab").asString()).isEqualTo("الدار البيضاء سطات");
		assertThat(casa.get("nom").get("ary-Latn").asString()).isEqualTo("Casablanca-Settat");
		assertThat(casa.get("villes").findValues("code").stream().map(c -> c.asString())).contains("CASABLANCA", "MOHAMMEDIA");
	}

	@Test
	void importReserveALAdministrateur() {
		var fichier = new MockMultipartFile("fichier", "b.csv", "text/csv", "code_pos;nom\n".getBytes());
		assertThat(mvc.post().uri("/v1/admin/hanouts/import").multipart().file(fichier)
			.header("Authorization", "Bearer " + ambulant().jeton())).hasStatus(403);
	}

	@Test
	void decoupageCsvAvecGuillemets() {
		assertThat(ImportReferentiel.champs("a;\"b;c\";\"d\"\"e\"", ';')).containsExactly("a", "b;c", "d\"e");
		assertThat(ImportReferentiel.telephone("06 12-34.56.78")).isEqualTo("+212612345678");
		assertThat(ImportReferentiel.telephone("00212712345678")).isEqualTo("+212712345678");
	}

}

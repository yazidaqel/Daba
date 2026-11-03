package ma.daba.comptes;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import ma.daba.TestIntegration;
import tools.jackson.databind.JsonNode;

/** Profil de l'ambulant (§3.1), saisi hors ligne, et son nom montré aux grossistes et aux hanouts. */
class ProfilAmbulantTest extends TestIntegration {

	JsonNode pousser(Compte ambulant, String type, Object donnees, Instant creeLe) {
		var r = post(ambulant, "/v1/sync/operations", m("appareilId", ambulant.appareilId(), "operations",
				List.of(m("id", UUID.randomUUID(), "type", type, "creeLe", creeLe.toString(), "donnees", donnees))));
		assertThat(r).hasStatusOk();
		return lire(r).get("resultats").get(0);
	}

	JsonNode profil(Compte ambulant, String nom) {
		return pousser(ambulant, "PROFIL_AMBULANT", m("nom", nom, "cin", "BK123456", "vehicule", "Triporteur",
				"villes", List.of("CASABLANCA", "MOHAMMEDIA"), "categories", List.of("BOISSONS")), Instant.now());
	}

	JsonNode changement(Compte ambulant, String entite) {
		JsonNode trouve = null;
		for (var c : lire(get(ambulant, "/v1/sync/changements?limite=1000")).get("changements")) {
			if (c.get("entite").asString().equals(entite)) {
				trouve = c;
			}
		}
		return trouve;
	}

	@Test
	void profilSaisiHorsLigneEtLivreParSynchronisation() {
		produit(); // catégorie BOISSONS
		var ambulant = ambulant();
		assertThat(profil(ambulant, "Youssef El Amrani").get("statut").asString()).isEqualTo("APPLIQUEE");

		var p = changement(ambulant, "PROFIL");
		assertThat(p.get("id").asString()).isEqualTo(ambulant.id().toString());
		assertThat(p.get("donnees").get("nom").asString()).isEqualTo("Youssef El Amrani");
		assertThat(p.get("donnees").get("cin").asString()).isEqualTo("BK123456");
		assertThat(p.get("donnees").get("villes").get(0).asString()).isEqualTo("CASABLANCA");
		assertThat(p.get("donnees").get("villes").get(1).asString()).isEqualTo("MOHAMMEDIA");
		assertThat(p.get("donnees").get("categories").get(0).asString()).isEqualTo("BOISSONS");
		assertThat(lire(get(ambulant, "/v1/moi/donnees")).get("profil").get("cin").asString()).isEqualTo("BK123456");

		// Le profil est remplacé en entier : un champ facultatif absent est effacé.
		pousser(ambulant, "PROFIL_AMBULANT", m("nom", "Youssef"), Instant.now());
		var remplace = changement(ambulant, "PROFIL").get("donnees");
		assertThat(remplace.get("cin")).isNull();
		assertThat(remplace.get("categories").size()).isZero();
	}

	@Test
	void profilSansNomOuAvecUnCodeInconnuRejete_etProfilPlusRecentAilleursConserve() {
		var ambulant = ambulant();
		var sansNom = pousser(ambulant, "PROFIL_AMBULANT", m("villes", List.of("CASABLANCA")), Instant.now());
		assertThat(sansNom.get("statut").asString()).isEqualTo("REJETEE");
		assertThat(sansNom.get("erreur").get("erreurs").findValuesAsString("champ")).contains("nom");
		var villeInconnue = pousser(ambulant, "PROFIL_AMBULANT", m("nom", "Ali", "villes", List.of("ATLANTIS")), Instant.now());
		assertThat(villeInconnue.get("statut").asString()).isEqualTo("REJETEE");
		assertThat(pousser(ambulant, "PROFIL_AMBULANT", m("nom", "Ali", "categories", List.of("INCONNUE")), Instant.now())
			.get("statut").asString()).isEqualTo("REJETEE");

		pousser(ambulant, "PROFIL_AMBULANT", m("nom", "Ali Récent"), Instant.now());
		var ancien = pousser(ambulant, "PROFIL_AMBULANT", m("nom", "Ali Ancien"), Instant.now().minus(1, ChronoUnit.HOURS));
		assertThat(ancien.get("statut").asString()).isEqualTo("EN_CONFLIT");
		assertThat(ancien.get("conflit").get("resolution").asString()).isEqualTo("VERSION_SERVEUR_CONSERVEE");
		assertThat(changement(ambulant, "PROFIL").get("donnees").get("nom").asString()).isEqualTo("Ali Récent");
	}

	@Test
	void rgF2_mentionsLegalesDeclareesDansLesParametresDeLAmbulant() {
		var ambulant = ambulant();
		var mentions = m("ice", "001234567000089", "identifiantFiscal", "12345678", "registreCommerce", "RC 4567",
				"assujettiTva", false);
		pousser(ambulant, "PARAMETRES_AMBULANT", m("mentionsLegales", mentions), Instant.now());
		pousser(ambulant, "PARAMETRES_AMBULANT", m("politiqueDepassementCredit", "BLOQUER"), Instant.now().plusSeconds(1));
		var parametres = changement(ambulant, "PARAMETRES").get("donnees");
		assertThat(parametres.get("politiqueDepassementCredit").asString()).isEqualTo("BLOQUER");
		assertThat(parametres.get("mentionsLegales").get("ice").asString()).isEqualTo("001234567000089");
		var iceInvalide = pousser(ambulant, "PARAMETRES_AMBULANT",
				m("mentionsLegales", m("ice", "12", "identifiantFiscal", "1", "registreCommerce", "1", "assujettiTva", true)),
				Instant.now().plusSeconds(2));
		assertThat(iceInvalide.get("statut").asString()).isEqualTo("REJETEE");
	}

	@Test
	void nomDeLAmbulantMontreAuGrossisteEtAuHanout_telephoneMasqueSansProfil() {
		var produit = produit();
		var ambulant = ambulant();
		jdbc.sql("UPDATE utilisateur SET nom = NULL WHERE id = :u").param("u", ambulant.id()).update();
		var masque = Comptes.masquer(ambulant.telephone());
		var grossiste = grossiste();
		var bon = lire(post(grossiste, "/v1/bons-sortie", m("id", UUID.randomUUID(), "ambulantTelephone", ambulant.telephone(),
				"lignes", List.of(m("produitId", produit, "quantite", 10)))));
		assertThat(bon.get("ambulant").get("nom").asString()).isEqualTo(masque);

		profil(ambulant, "Youssef El Amrani");
		var nom = "Youssef El Amrani";
		// Grossiste : bons de sortie et ambulants rattachés.
		assertThat(lire(get(grossiste, "/v1/bons-sortie/" + bon.get("id").asString())).get("ambulant").get("nom").asString())
			.isEqualTo(nom);
		assertThat(lire(get(grossiste, "/v1/ambulants")).get("contenu").get(0).get("nom").asString()).isEqualTo(nom);

		// Hanout : documents, compte de karni, demande de dépannage.
		var hanoutId = hanout(33.57, -7.59);
		var hanout = compteHanout(hanoutId);
		pousser(ambulant, "CHARGEMENT", m("qrJeton", bon.get("qrJeton").asString()), Instant.now());
		pousser(ambulant, "EMISSION_DOCUMENT", m("documentId", UUID.randomUUID(), "type", "BON_LIVRAISON",
				"numero", "%s-BL-%06d".formatted(ambulant.prefixe(), 1), "sequence", 1, "hanoutId", hanoutId,
				"lignes", List.of(m("nature", "ARTICLE", "produitId", produit, "quantite", 2, "prixUnitaire", 700))), Instant.now());
		assertThat(lire(get(hanout, "/v1/documents")).get("contenu").get(0).get("ambulant").get("nom").asString()).isEqualTo(nom);
		assertThat(lire(get(hanout, "/v1/karni/comptes")).get("contenu").get(0).get("ambulant").get("nom").asString()).isEqualTo(nom);

		assertThat(put(ambulant, "/v1/ambulant/disponibilite", m("disponible", true, "position", m("lat", 33.571, "lon", -7.59))))
			.hasStatusOk();
		var demande = lire(post(hanout, "/v1/demandes-depannage", m("id", UUID.randomUUID(), "produitId", produit, "quantite", 2)))
			.get("id").asString();
		assertThat(post(ambulant, "/v1/demandes-depannage/" + demande + "/acceptation", null)).hasStatusOk();
		assertThat(lire(get(hanout, "/v1/demandes-depannage/" + demande)).get("ambulant").get("nom").asString()).isEqualTo(nom);

		// Retrait du consentement : les listes repassent au téléphone masqué, la pièce émise garde le nom (RG-C4).
		retirerConsentement(ambulant);
		assertThat(lire(get(grossiste, "/v1/bons-sortie/" + bon.get("id").asString())).get("ambulant").get("nom").asString())
			.isEqualTo(masque);
		assertThat(lire(get(grossiste, "/v1/bons-sortie")).get("contenu").get(0).get("ambulant").get("nom").asString())
			.isEqualTo(masque);
		assertThat(lire(get(grossiste, "/v1/ambulants")).get("contenu").get(0).get("nom").asString()).isEqualTo(masque);
		assertThat(lire(get(hanout, "/v1/karni/comptes")).get("contenu").get(0).get("ambulant").get("nom").asString())
			.isEqualTo(masque);
		assertThat(lire(get(hanout, "/v1/demandes-depannage/" + demande)).get("ambulant").get("nom").asString()).isEqualTo(masque);
		assertThat(lire(get(hanout, "/v1/documents")).get("contenu").get(0).get("ambulant").get("nom").asString()).isEqualTo(nom);
	}

	void retirerConsentement(Compte ambulant) {
		assertThat(put(ambulant, "/v1/moi/consentement", m("versionTexte", "v1", "traitementDonnees", false))).hasStatusOk();
	}

	@Test
	void cndp_sansConsentementSeulesLesPiecesDeVenteEtLeStockRemontent() {
		var produit = produit();
		var ambulant = ambulant();
		var nomAvant = jdbc.sql("SELECT nom FROM utilisateur WHERE id = :u").param("u", ambulant.id()).query(String.class).single();
		var hanoutId = hanout(33.57, -7.59);
		retirerConsentement(ambulant);

		var profil = m("id", UUID.randomUUID(), "type", "PROFIL_AMBULANT", "creeLe", Instant.now().toString(),
				"donnees", m("nom", "Nom Sans Consentement", "cin", "AB123456"));
		var refusees = List.of(profil,
				op("PARAMETRES_AMBULANT", m("politiqueDepassementCredit", "BLOQUER")),
				op("TARIF", m("produitId", produit, "prixUnitaire", 900)),
				op("CREDIT_HANOUT", m("hanoutId", hanoutId, "plafond", 10000)),
				op("RESOLUTION_CONTESTATION", m("contestationId", UUID.randomUUID())));
		for (var r : lot(ambulant, refusees)) {
			assertThat(r.get("statut").asString()).as(r.toString()).isEqualTo("REJETEE");
			assertThat(r.get("erreur").get("type").asString()).endsWith("/consentement-requis");
		}
		assertThat(jdbc.sql("SELECT nom FROM utilisateur WHERE id = :u").param("u", ambulant.id()).query(String.class).single())
			.isEqualTo(nomAvant);
		assertThat(jdbc.sql("SELECT count(*) FROM profil_ambulant WHERE utilisateur_id = :u").param("u", ambulant.id())
			.query(Long.class).single()).isZero();

		var acceptees = List.of(op("CHARGEMENT", m("lignes", List.of(m("produitId", produit, "quantite", 5)))),
				op("EMISSION_DOCUMENT", m("documentId", UUID.randomUUID(), "type", "BON_LIVRAISON",
						"numero", "%s-BL-%06d".formatted(ambulant.prefixe(), 1), "sequence", 1, "hanoutId", hanoutId,
						"lignes", List.of(m("nature", "ARTICLE", "produitId", produit, "quantite", 2, "prixUnitaire", 700)))),
				op("VERSEMENT", m("versementId", UUID.randomUUID(), "hanoutId", hanoutId, "mode", "ESPECES", "montant", 500)));
		for (var r : lot(ambulant, acceptees)) {
			assertThat(r.get("statut").asString()).as(r.toString()).isEqualTo("APPLIQUEE");
		}

		// Consentement redonné : l'opération rejetée se renvoie telle quelle.
		assertThat(put(ambulant, "/v1/moi/consentement", m("versionTexte", "v1", "traitementDonnees", true))).hasStatusOk();
		assertThat(lot(ambulant, List.of(profil)).get(0).get("statut").asString()).isEqualTo("APPLIQUEE");
		assertThat(changement(ambulant, "PROFIL").get("donnees").get("nom").asString()).isEqualTo("Nom Sans Consentement");
	}

	static java.util.Map<String, Object> op(String type, Object donnees) {
		return m("id", UUID.randomUUID(), "type", type, "creeLe", Instant.now().toString(), "donnees", donnees);
	}

	JsonNode lot(Compte ambulant, List<?> operations) {
		var r = post(ambulant, "/v1/sync/operations", m("appareilId", ambulant.appareilId(), "operations", operations));
		assertThat(r).hasStatusOk();
		return lire(r).get("resultats");
	}

}

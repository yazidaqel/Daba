package ma.daba.sync;

import java.net.URI;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import ma.daba.commun.Anomalie;
import ma.daba.commun.Appelant;
import ma.daba.commun.Geste;
import ma.daba.commun.Geste.Conflit;
import ma.daba.commun.Geste.Effet;
import ma.daba.commun.Journal;
import ma.daba.commun.Json;
import ma.daba.commun.Position;
import ma.daba.commun.Probleme;
import ma.daba.comptes.Comptes;
import ma.daba.depannage.Depannage;
import ma.daba.promos.Promos;
import ma.daba.referentiel.Referentiel;
import ma.daba.stock.Stock;
import ma.daba.stock.Stock.InventaireLigne;
import ma.daba.stock.Stock.LigneQuantite;
import ma.daba.ventes.AvisHanout;
import ma.daba.ventes.Ventes;
import tools.jackson.databind.JsonNode;

/**
 * Applique les opérations hors ligne dans l'ordre reçu, chacune dans sa transaction. Rejouer une
 * opération déjà appliquée ne fait rien (DEJA_APPLIQUEE) ; une opération rejetée peut être renvoyée.
 */
@Service
class Synchronisation {

	private static final Logger log = LoggerFactory.getLogger(Synchronisation.class);

	record Chargement(String qrJeton, UUID grossisteId, List<@Valid LigneQuantite> lignes) {
	}

	record Retour(@NotNull @Pattern(regexp = "^(HANOUT_VERS_AMBULANT|AMBULANT_VERS_GROSSISTE)$") String sens, UUID hanoutId,
			UUID grossisteId, @NotNull @Pattern(regexp = "^(ABIME|PERIME|INVENDU|AUTRE)$") String motif, Boolean remisEnVente,
			@NotEmpty List<@Valid LigneQuantite> lignes) {
	}

	record Inventaire(@NotEmpty List<@Valid InventaireLigne> lignes) {
	}

	record NouveauHanout(@NotNull UUID hanoutId, @NotNull @jakarta.validation.constraints.NotBlank String nom, String gerant,
			@Pattern(regexp = "^\\+212[5-7][0-9]{8}$") String telephone, @Valid Position position, String adresse,
			String ville, @Pattern(regexp = "^[0-9]{15}$") String ice) {
	}

	record AnomalieVue(UUID id, String type, String regle, UUID operationId, UUID produitId, String detail, String statut,
			Instant detecteeLe) {
	}

	record Resultat(UUID operationId, String statut, List<AnomalieVue> anomalies, Conflit conflit, Map<String, Object> erreur) {
	}

	/**
	 * Opérations acceptées sans consentement CNDP actif : les pièces de vente et encaissements déjà
	 * faits (conservation légale, RG-C4), le stock qu'ils mouvementent (RG-S1, RG-S2) et le hanout
	 * terrain qu'ils référencent. Le reste (profil, paramètres, tarifs, crédit, contestations) est
	 * REJETEE (consentement-requis) et peut être renvoyé après le consentement.
	 */
	static final Set<String> SANS_CONSENTEMENT = Set.of("EMISSION_DOCUMENT", "AVOIR", "VERSEMENT", "CHARGEMENT", "RETOUR",
			"INVENTAIRE", "HANOUT");

	private final JdbcClient jdbc;
	private final Json json;
	private final Journal journal;
	private final Validator validateur;
	private final TransactionTemplate transactions;
	private final Stock stock;
	private final Ventes ventes;
	private final Referentiel referentiel;
	private final Promos promos;
	private final Depannage depannage;
	private final AvisHanout avisHanout;
	private final Comptes comptes;

	Synchronisation(JdbcClient jdbc, Json json, Journal journal, Validator validateur, TransactionTemplate transactions,
			Stock stock, Ventes ventes, Referentiel referentiel, Promos promos, Depannage depannage, AvisHanout avisHanout,
			Comptes comptes) {
		this.avisHanout = avisHanout;
		this.comptes = comptes;
		this.jdbc = jdbc;
		this.json = json;
		this.journal = journal;
		this.validateur = validateur;
		this.transactions = transactions;
		this.stock = stock;
		this.ventes = ventes;
		this.referentiel = referentiel;
		this.promos = promos;
		this.depannage = depannage;
	}

	List<Resultat> appliquer(Appelant appelant, List<JsonNode> operations) {
		var resultats = new ArrayList<Resultat>();
		for (var operation : operations) {
			resultats.add(appliquer(appelant, operation));
		}
		return resultats;
	}

	private Resultat appliquer(Appelant appelant, JsonNode operation) {
		var id = uuid(operation.get("id"));
		var type = operation.path("type").asString(null);
		var creeLe = instant(operation.get("creeLe"));
		var deja = jdbc.sql("SELECT ambulant_id, statut FROM operation_sync WHERE id = :id").param("id", id)
			.query((rs, i) -> Map.entry(rs.getObject("ambulant_id", UUID.class), rs.getString("statut"))).optional();
		if (deja.isPresent() && !deja.get().getKey().equals(appelant.utilisateurId())) {
			return rejet(appelant, id, type, creeLe, operation, Probleme.requete("Identifiant d'opération déjà utilisé"));
		}
		if (deja.isPresent() && !"REJETEE".equals(deja.get().getValue())) {
			return new Resultat(id, "DEJA_APPLIQUEE", null, null, null);
		}
		if (type == null || creeLe == null) {
			return rejet(appelant, id, type, creeLe, operation, Probleme.requete("type et creeLe requis"));
		}
		if (!SANS_CONSENTEMENT.contains(type) && !comptes.traitementConsenti(appelant.utilisateurId())) {
			return rejet(appelant, id, type, creeLe, operation,
					Probleme.interdit("consentement-requis", "Consentement CNDP requis (PUT /v1/moi/consentement)"));
		}
		var geste = new Geste(appelant.utilisateurId(), appelant.organisationId(), appelant.appareilId(), id, creeLe);
		try {
			return transactions.execute(s -> {
				var effet = executer(geste, type, operation.get("donnees"));
				// §9 : un achat ou des catégories déclarées changent les offres ciblées que voit l'ambulant.
				if (effet.conflit() == null && Set.of("CHARGEMENT", "EMISSION_DOCUMENT", "PROFIL_AMBULANT").contains(type)) {
					promos.reevaluerCiblage(geste.ambulantId());
				}
				var anomalies = effet.anomalies().stream().map(a -> enregistrer(geste, a)).toList();
				var statut = effet.conflit() == null ? "APPLIQUEE" : "EN_CONFLIT";
				var resultat = new Resultat(id, statut, anomalies.isEmpty() ? null : anomalies, effet.conflit(), null);
				journaliser(appelant, id, type, creeLe, operation, statut, resultat);
				return resultat;
			});
		}
		catch (Probleme p) {
			return rejet(appelant, id, type, creeLe, operation, p);
		}
		catch (RuntimeException e) {
			log.error("Opération {} ({}) en erreur", id, type, e);
			return rejet(appelant, id, type, creeLe, operation,
					new Probleme(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "erreur-serveur", null,
							"Erreur serveur : renvoyer l'opération plus tard"));
		}
	}

	private Effet executer(Geste geste, String type, JsonNode donnees) {
		return switch (type) {
			case "CHARGEMENT" -> {
				var c = lire(donnees, Chargement.class);
				if ((c.qrJeton() == null) == (c.lignes() == null || c.lignes().isEmpty())) {
					throw Probleme.requete("Chargement : qrJeton ou lignes, l'un des deux");
				}
				yield c.qrJeton() != null ? stock.chargerBon(geste, c.qrJeton()) : stock.chargerManuel(geste, c.lignes());
			}
			case "RETOUR" -> {
				var r = lire(donnees, Retour.class);
				var versGrossiste = "AMBULANT_VERS_GROSSISTE".equals(r.sens());
				if (versGrossiste ? r.grossisteId() == null : r.hanoutId() == null) {
					throw Probleme.requete(versGrossiste ? "grossisteId requis" : "hanoutId requis");
				}
				if (versGrossiste) {
					referentiel.grossiste(r.grossisteId()).orElseThrow(() -> Probleme.regle(null, "Grossiste inconnu"));
				}
				else {
					referentiel.hanout(r.hanoutId()).orElseThrow(() -> Probleme.regle(null, "Hanout inconnu"));
				}
				yield stock.retour(geste, versGrossiste, Boolean.TRUE.equals(r.remisEnVente()), r.motif(), r.lignes());
			}
			case "INVENTAIRE" -> stock.inventaire(geste, lire(donnees, Inventaire.class).lignes(),
					ventes.parametres(geste.ambulantId()).seuilEcartInventairePourcent());
			case "EMISSION_DOCUMENT" -> {
				var e = lire(donnees, Ventes.EmissionDocument.class);
				var effet = ventes.emettre(geste, e, noms(e.lignes()));
				if (e.demandeDepannageId() == null) {
					yield effet;
				}
				var anomalies = new ArrayList<>(effet.anomalies());
				anomalies.addAll(depannage.livraison(geste, e.demandeDepannageId(), e.documentId()));
				yield Effet.de(anomalies);
			}
			case "AVOIR" -> {
				var a = lire(donnees, Ventes.AvoirSaisie.class);
				yield ventes.avoir(geste, a, noms(a.lignes()));
			}
			case "VERSEMENT" -> ventes.verser(geste, lire(donnees, Ventes.VersementSaisie.class));
			case "TARIF" -> ventes.tarif(geste, lire(donnees, Ventes.Tarif.class));
			case "CREDIT_HANOUT" -> ventes.credit(geste, lire(donnees, Ventes.CreditHanout.class));
			case "PARAMETRES_AMBULANT" -> ventes.parametres(geste, lire(donnees, Ventes.ParametresSaisie.class));
			case "PROFIL_AMBULANT" -> comptes.profil(geste, lire(donnees, Comptes.ProfilSaisie.class));
			case "RESOLUTION_CONTESTATION" -> avisHanout.resoudre(geste, lire(donnees, AvisHanout.Resolution.class));
			case "HANOUT" -> {
				var h = lire(donnees, NouveauHanout.class);
				referentiel.creerHanoutTerrain(h.hanoutId(), h.nom(), h.gerant(), h.telephone(), h.position(), h.adresse(),
						h.ville(), h.ice(), geste.ambulantId());
				yield Effet.ok();
			}
			default -> throw Probleme.requete("Type d'opération inconnu : " + type);
		};
	}

	private Map<UUID, Map<String, String>> noms(List<Ventes.LigneSaisie> lignes) {
		var ids = lignes == null ? Set.<UUID>of()
				: lignes.stream().map(Ventes.LigneSaisie::campagneId).filter(Objects::nonNull).collect(Collectors.toSet());
		return promos.nomsCampagnes(ids);
	}

	/** Lit et valide les données d'une opération ; mal formées = REJETEE avec le détail par champ. */
	private <T> T lire(JsonNode donnees, Class<T> type) {
		if (donnees == null || !donnees.isObject()) {
			throw Probleme.requete("donnees manquantes");
		}
		T valeur;
		try {
			valeur = json.mapper().treeToValue(donnees, type);
		}
		catch (RuntimeException e) {
			throw Probleme.requete("donnees illisibles : " + e.getMessage());
		}
		var violations = validateur.validate(valeur);
		if (!violations.isEmpty()) {
			throw new Invalide(violations.stream()
				.map(v -> Map.of("champ", v.getPropertyPath().toString(), "message", v.getMessage()))
				.toList());
		}
		return valeur;
	}

	static class Invalide extends Probleme {

		final List<Map<String, String>> erreurs;

		Invalide(List<Map<String, String>> erreurs) {
			super(org.springframework.http.HttpStatus.BAD_REQUEST, "requete-invalide", null, "Opération mal formée");
			this.erreurs = erreurs;
		}

	}

	private AnomalieVue enregistrer(Geste geste, Anomalie a) {
		var vue = new AnomalieVue(UUID.randomUUID(), a.type(), a.regle(), geste.operationId(), a.produitId(), a.detail(),
				"OUVERTE", Instant.now());
		jdbc.sql("""
				INSERT INTO anomalie (id, ambulant_id, operation_id, type, regle, produit_id, detail, detectee_le)
				VALUES (:id, :a, :op, :type, :regle, :p, :detail, :le)
				""").param("id", vue.id()).param("a", geste.ambulantId()).param("op", geste.operationId())
			.param("type", a.type()).param("regle", a.regle()).param("p", a.produitId()).param("detail", a.detail())
			.param("le", Timestamp.from(vue.detecteeLe())).update();
		journal.publier(geste.ambulantId(), "ANOMALIE", vue.id(), vue);
		return vue;
	}

	private Resultat rejet(Appelant appelant, UUID id, String type, Instant creeLe, JsonNode operation, Probleme p) {
		var erreur = new LinkedHashMap<String, Object>();
		erreur.put("type", URI.create("https://daba.ma/problemes/" + p.code()).toString());
		erreur.put("title", p.statut().getReasonPhrase());
		erreur.put("status", p.statut().value());
		erreur.put("detail", p.getMessage());
		if (p.regle() != null) {
			erreur.put("regle", p.regle());
		}
		if (p instanceof Invalide invalide) {
			erreur.put("erreurs", invalide.erreurs);
		}
		var resultat = new Resultat(id, "REJETEE", null, null, erreur);
		transactions.executeWithoutResult(s -> journaliser(appelant, id, type, creeLe, operation, "REJETEE", resultat));
		return resultat;
	}

	private void journaliser(Appelant appelant, UUID id, String type, Instant creeLe, JsonNode operation, String statut,
			Resultat resultat) {
		jdbc.sql("""
				INSERT INTO operation_sync (id, ambulant_id, appareil_id, type, cree_le, statut, contenu, resultat)
				VALUES (:id, :a, :appareil, :type, :cree, :statut, :contenu::jsonb, :resultat::jsonb)
				ON CONFLICT (id) DO UPDATE SET statut = excluded.statut, resultat = excluded.resultat, recu_le = now()
				  WHERE operation_sync.ambulant_id = excluded.ambulant_id
				""").param("id", id).param("a", appelant.utilisateurId()).param("appareil", appelant.appareilId())
			.param("type", type == null ? "?" : type).param("cree", creeLe == null ? null : Timestamp.from(creeLe))
			.param("statut", statut).param("contenu", operation.toString()).param("resultat", json.ecrire(resultat))
			.update();
	}

	private static UUID uuid(JsonNode valeur) {
		try {
			return valeur == null ? null : UUID.fromString(valeur.asString());
		}
		catch (IllegalArgumentException e) {
			return null;
		}
	}

	private static Instant instant(JsonNode valeur) {
		try {
			return valeur == null ? null : java.time.OffsetDateTime.parse(valeur.asString()).toInstant();
		}
		catch (RuntimeException e) {
			return null;
		}
	}

}

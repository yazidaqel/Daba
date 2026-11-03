package ma.daba.promos;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import ma.daba.commun.Jetons;
import ma.daba.commun.Journal;
import ma.daba.commun.Json;
import ma.daba.commun.Page;
import ma.daba.commun.Photos;
import ma.daba.commun.Probleme;
import ma.daba.commun.Reference;
import ma.daba.comptes.Comptes;
import ma.daba.referentiel.Referentiel;
import ma.daba.referentiel.Referentiel.Grossiste;
import ma.daba.referentiel.Referentiel.Produit;
import ma.daba.stock.Stock;
import ma.daba.ventes.Ventes;
import tools.jackson.core.type.TypeReference;

/**
 * Cycle de vie des promotions (§7.2) : campagne de la marque, dépôts chez les grossistes, réception
 * confirmée (pivot RG-P1), réservation en ligne plafonnée (RG-P3) et expirant (RG-P2), retrait par
 * QR chez le grossiste (RG-P4) qui alimente le stock promo de l'ambulant (RG-S3).
 */
@Service
public class Promos {

	// ---------------------------------------------------------------- saisies

	record LigneCampagne(@NotNull UUID produitId, @NotNull Map<String, Object> remise, @NotNull @Min(1) Integer quantiteTotale,
			@NotNull @Min(1) Integer quantiteMaxParAmbulant, @Min(0) Long prixReventeConseille) {
	}

	record CampagneSaisie(@NotNull Map<String, String> nom, Map<String, String> description, @NotNull Instant debut,
			@NotNull Instant fin, @NotEmpty List<@Valid LigneCampagne> lignes, Map<String, Object> ciblage,
			@Min(1) @Max(168) Integer delaiRetraitHeures) {
	}

	record LigneQuantite(@NotNull UUID produitId, @NotNull @Min(1) Integer quantite) {
	}

	record PropositionDepot(@NotNull UUID grossisteId, @NotEmpty List<@Valid LigneQuantite> lignes) {
	}

	record ReponseGrossiste(@NotNull @Pattern(regexp = "^(ACCEPTER|REFUSER|CONTRE_PROPOSER)$") String decision,
			List<@Valid LigneQuantite> lignes, @Size(max = 500) String commentaire) {
	}

	record ReponseMarque(@NotNull @Pattern(regexp = "^(ACCEPTER|REFUSER)$") String decision) {
	}

	record LigneRecue(@NotNull UUID produitId, @NotNull @Min(0) Integer quantiteRecue) {
	}

	record ReceptionDepot(@NotEmpty List<@Valid LigneRecue> lignes, @Size(max = 500) String commentaire) {
	}

	record ClotureDepot(@NotNull @Pattern(regexp = "^(RETOURNE|RACHETE)$") String issueReliquat,
			@Size(max = 500) String commentaire) {
	}

	record ReservationSaisie(@NotNull UUID id, @NotNull UUID offreId, @NotNull @Min(1) Integer quantite) {
	}

	record RetraitSaisie(@NotNull String qrJeton, @NotNull UUID appareilId, @NotNull @Min(1) Integer quantiteRemise) {
	}

	// ---------------------------------------------------------------- vues

	record Etapes(long depose, long reserve, long retire, long revendu) {

		Etapes plus(Etapes o) {
			return new Etapes(depose + o.depose, reserve + o.reserve, retire + o.retire, revendu + o.revendu);
		}

		static final Etapes ZERO = new Etapes(0, 0, 0, 0);

	}

	record Campagne(UUID id, Map<String, String> nom, Map<String, String> description, Instant debut, Instant fin,
			List<LigneCampagne> lignes, Map<String, Object> ciblage, int delaiRetraitHeures, String statut, Instant creeLe,
			Etapes entonnoir) {
	}

	record LigneDepot(UUID produitId, int quantiteProposee, Integer quantiteContreProposee, int quantiteDeposee,
			int quantiteReservee, int quantiteRetiree, int quantiteRestante, Integer quantiteMaxParAmbulant) {

		LigneDepot avecPlafond(Integer plafond) {
			return new LigneDepot(produitId, quantiteProposee, quantiteContreProposee, quantiteDeposee, quantiteReservee,
					quantiteRetiree, quantiteRestante, plafond);
		}

	}

	record Depot(UUID id, Reference campagne, String statutCampagne, Reference marque, Grossiste grossiste, String statut,
			List<LigneDepot> lignes, Instant finCampagne, Instant recuLe, String photoBonLivraisonUrl, Map<String, Object> cloture) {
	}

	record OffrePromo(UUID id, UUID campagneId, Map<String, String> campagneNom, UUID depotId, Produit produit,
			Map<String, Object> remise, Long prixReventeConseille, Grossiste grossiste, int quantiteDisponible,
			int quantiteMaxParAmbulant, int quantiteDejaReservee, Instant fin, int delaiRetraitHeures) {
	}

	record Reservation(UUID id, String statut, UUID campagneId, UUID depotId, UUID produitId, Grossiste grossiste,
			Reference ambulant, int quantiteReservee, Integer quantiteRetiree, Integer quantiteRevendue, Instant reserveeLe,
			Instant expireLe, Instant retireeLe, String qrJeton) {
	}

	record EntonnoirGrossiste(Reference grossiste, long depose, long reserve, long retire, long revendu) {
	}

	record EntonnoirProduit(UUID produitId, long depose, long reserve, long retire, long revendu) {
	}

	record Entonnoir(UUID campagneId, Etapes total, List<EntonnoirGrossiste> parGrossiste, List<EntonnoirProduit> parProduit) {
	}

	/** Événements publiés dans l'outbox, notifiés par {@link NotificationsPromos}. */
	record DepotPropose(UUID depotId) {
	}

	record ReceptionConfirmee(UUID depotId) {
	}

	record RetraitEffectue(UUID reservationId) {
	}

	static final TypeReference<Map<String, String>> LIBELLE = new TypeReference<>() {
	};

	static final TypeReference<Map<String, Object>> OBJET = new TypeReference<>() {
	};

	/** Quantités d'une ligne de dépôt : une réservation expirée revient au disponible (RG-P2). */
	static final String LIGNES_DEPOT = """
			SELECT dl.*, d.campagne_id, d.grossiste_id,
			  coalesce(sum(r.quantite_reservee) FILTER (WHERE r.statut = 'RESERVEE' AND r.expire_le > now()), 0) AS reservee,
			  coalesce(sum(r.quantite_retiree) FILTER (WHERE r.statut = 'RETIREE'), 0) AS retiree
			FROM depot_ligne dl JOIN depot d ON d.id = dl.depot_id LEFT JOIN reservation r ON r.offre_id = dl.id
			""";

	private final JdbcClient jdbc;
	private final Json json;
	private final Journal journal;
	private final Jetons jetons;
	private final Photos photos;
	private final Referentiel referentiel;
	private final Comptes comptes;
	private final Stock stock;
	private final Ventes ventes;
	private final ApplicationEventPublisher evenements;

	Promos(JdbcClient jdbc, Json json, Journal journal, Jetons jetons, Photos photos, Referentiel referentiel,
			Comptes comptes, Stock stock, Ventes ventes, ApplicationEventPublisher evenements) {
		this.jdbc = jdbc;
		this.json = json;
		this.journal = journal;
		this.jetons = jetons;
		this.photos = photos;
		this.referentiel = referentiel;
		this.comptes = comptes;
		this.stock = stock;
		this.ventes = ventes;
		this.evenements = evenements;
	}

	// ---------------------------------------------------------------- campagnes (grand client)

	@Transactional
	Campagne creerCampagne(UUID grandClient, CampagneSaisie saisie) {
		verifier(saisie);
		var id = UUID.randomUUID();
		jdbc.sql("""
				INSERT INTO campagne (id, grand_client_id, nom, description, debut, fin, ciblage, delai_retrait_heures)
				VALUES (:id, :gc, :nom::jsonb, :description::jsonb, :debut, :fin, :ciblage::jsonb, :delai)
				""").param("id", id).param("gc", grandClient).param("nom", json.ecrire(saisie.nom()))
			.param("description", json.ecrire(saisie.description())).param("debut", Timestamp.from(saisie.debut()))
			.param("fin", Timestamp.from(saisie.fin())).param("ciblage", json.ecrire(saisie.ciblage()))
			.param("delai", saisie.delaiRetraitHeures() == null ? 24 : saisie.delaiRetraitHeures()).update();
		enregistrerLignes(id, saisie.lignes());
		return campagne(grandClient, id);
	}

	@Transactional
	Campagne modifierCampagne(UUID grandClient, UUID id, CampagneSaisie saisie) {
		exigerBrouillon(grandClient, id);
		verifier(saisie);
		jdbc.sql("""
				UPDATE campagne SET nom = :nom::jsonb, description = :description::jsonb, debut = :debut, fin = :fin,
				  ciblage = :ciblage::jsonb, delai_retrait_heures = :delai WHERE id = :id
				""").param("id", id).param("nom", json.ecrire(saisie.nom())).param("description", json.ecrire(saisie.description()))
			.param("debut", Timestamp.from(saisie.debut())).param("fin", Timestamp.from(saisie.fin()))
			.param("ciblage", json.ecrire(saisie.ciblage()))
			.param("delai", saisie.delaiRetraitHeures() == null ? 24 : saisie.delaiRetraitHeures()).update();
		jdbc.sql("DELETE FROM campagne_ligne WHERE campagne_id = :id").param("id", id).update();
		enregistrerLignes(id, saisie.lignes());
		return campagne(grandClient, id);
	}

	@Transactional
	void supprimerCampagne(UUID grandClient, UUID id) {
		exigerBrouillon(grandClient, id);
		jdbc.sql("DELETE FROM campagne WHERE id = :id").param("id", id).update();
	}

	private void exigerBrouillon(UUID grandClient, UUID id) {
		if (!"BROUILLON".equals(statutCampagne(grandClient, id))) {
			throw Probleme.conflit("Campagne modifiable seulement à l'état BROUILLON");
		}
	}

	private String statutCampagne(UUID grandClient, UUID id) {
		return jdbc.sql("SELECT statut FROM campagne WHERE id = :id AND grand_client_id = :gc FOR UPDATE")
			.param("id", id).param("gc", grandClient).query(String.class).optional().orElseThrow(Probleme::introuvable);
	}

	private void verifier(CampagneSaisie saisie) {
		if (saisie.nom().get("fr") == null || saisie.nom().get("fr").isBlank()) {
			throw Probleme.requete("nom.fr requis");
		}
		if (!saisie.fin().isAfter(saisie.debut())) {
			throw Probleme.regle(null, "La fin doit suivre le début");
		}
		var produits = saisie.lignes().stream().map(LigneCampagne::produitId).collect(Collectors.toSet());
		if (produits.size() != saisie.lignes().size()) {
			throw Probleme.regle(null, "Un produit apparaît deux fois");
		}
		if (referentiel.produits(produits).size() != produits.size()) {
			throw Probleme.regle(null, "Produit inconnu");
		}
		for (var ligne : saisie.lignes()) {
			if (ligne.quantiteMaxParAmbulant() > ligne.quantiteTotale()) {
				throw Probleme.regle("RG-P3", "Plafond par ambulant supérieur à la quantité totale");
			}
			verifierRemise(ligne.remise());
		}
	}

	static void verifierRemise(Map<String, Object> remise) {
		var ok = switch (String.valueOf(remise.get("type"))) {
			case "POURCENTAGE" -> remise.get("pourcentage") instanceof Number p && p.doubleValue() > 0 && p.doubleValue() <= 100;
			case "PRIX_NET" -> remise.get("prixNet") instanceof Number p && p.longValue() >= 0;
			case "PRODUIT_OFFERT" -> remise.get("quantiteAchetee") instanceof Number a && a.intValue() >= 1
					&& remise.get("produitOffertId") != null && remise.get("quantiteOfferte") instanceof Number o && o.intValue() >= 1;
			case "PALIER" -> remise.get("paliers") instanceof List<?> paliers && !paliers.isEmpty();
			default -> false;
		};
		if (!ok) {
			throw Probleme.requete("Remise invalide : " + remise);
		}
	}

	private void enregistrerLignes(UUID campagneId, List<LigneCampagne> lignes) {
		for (var l : lignes) {
			jdbc.sql("""
					INSERT INTO campagne_ligne (campagne_id, produit_id, remise, quantite_totale, quantite_max_par_ambulant,
					  prix_revente_conseille) VALUES (:c, :p, :remise::jsonb, :total, :max, :prix)
					""").param("c", campagneId).param("p", l.produitId()).param("remise", json.ecrire(l.remise()))
				.param("total", l.quantiteTotale()).param("max", l.quantiteMaxParAmbulant())
				.param("prix", l.prixReventeConseille()).update();
		}
	}

	Campagne campagne(UUID grandClient, UUID id) {
		return jdbc.sql("SELECT * FROM campagne WHERE id = :id AND grand_client_id = :gc").param("id", id).param("gc", grandClient)
			.query(this::ligneCampagne).optional().orElseThrow(Probleme::introuvable);
	}

	Page<Campagne> campagnes(UUID grandClient, String statut, int page, int taille) {
		var total = jdbc.sql("SELECT count(*) FROM campagne WHERE grand_client_id = :gc AND (CAST(:s AS text) IS NULL OR statut = :s)")
			.param("gc", grandClient).param("s", statut).query(Long.class).single();
		var contenu = jdbc.sql("""
				SELECT * FROM campagne WHERE grand_client_id = :gc AND (CAST(:s AS text) IS NULL OR statut = :s)
				ORDER BY cree_le DESC LIMIT :taille OFFSET :offset
				""").param("gc", grandClient).param("s", statut).param("taille", taille).param("offset", page * taille)
			.query(this::ligneCampagne).list();
		return Page.de(page, taille, total, contenu);
	}

	private Campagne ligneCampagne(ResultSet rs, int i) throws SQLException {
		var id = rs.getObject("id", UUID.class);
		var lignes = jdbc.sql("SELECT * FROM campagne_ligne WHERE campagne_id = :c ORDER BY produit_id").param("c", id)
			.query((r, n) -> new LigneCampagne(r.getObject("produit_id", UUID.class), json.lire(r.getString("remise"), OBJET),
					r.getInt("quantite_totale"), r.getInt("quantite_max_par_ambulant"), (Long) r.getObject("prix_revente_conseille")))
			.list();
		return new Campagne(id, json.lire(rs.getString("nom"), LIBELLE), json.lire(rs.getString("description"), LIBELLE),
				rs.getTimestamp("debut").toInstant(), rs.getTimestamp("fin").toInstant(), lignes,
				json.lire(rs.getString("ciblage"), OBJET), rs.getInt("delai_retrait_heures"), rs.getString("statut"),
				rs.getTimestamp("cree_le").toInstant(), entonnoir(id).total());
	}

	/** Noms des campagnes, imprimés sur les lignes promo des pièces de vente (RG-F3). */
	public Map<UUID, Map<String, String>> nomsCampagnes(Set<UUID> ids) {
		if (ids.isEmpty()) {
			return Map.of();
		}
		var noms = new HashMap<UUID, Map<String, String>>();
		jdbc.sql("SELECT id, nom FROM campagne WHERE id IN (:ids)").param("ids", ids)
			.query(rs -> {
				noms.put(rs.getObject("id", UUID.class), json.lire(rs.getString("nom"), LIBELLE));
			});
		return noms;
	}

	// ---------------------------------------------------------------- dépôts

	/** Propose un dépôt (§5.2) ; la campagne passe de BROUILLON à PROPOSEE au premier. */
	@Transactional
	Depot proposerDepot(UUID grandClient, UUID campagneId, PropositionDepot proposition) {
		var statut = statutCampagne(grandClient, campagneId);
		if (List.of("EXPIREE", "CLOTUREE").contains(statut)) {
			throw Probleme.conflit("Campagne " + statut);
		}
		referentiel.grossiste(proposition.grossisteId()).orElseThrow(() -> Probleme.regle(null, "Grossiste inconnu"));
		if (jdbc.sql("SELECT count(*) FROM depot WHERE campagne_id = :c AND grossiste_id = :g")
			.param("c", campagneId).param("g", proposition.grossisteId()).query(Long.class).single() > 0) {
			throw Probleme.conflit("Un dépôt est déjà proposé à ce grossiste");
		}
		verifierRepartition(campagneId, null, proposition.lignes());
		var id = UUID.randomUUID();
		jdbc.sql("INSERT INTO depot (id, campagne_id, grossiste_id) VALUES (:id, :c, :g)")
			.param("id", id).param("c", campagneId).param("g", proposition.grossisteId()).update();
		for (var l : proposition.lignes()) {
			jdbc.sql("INSERT INTO depot_ligne (id, depot_id, produit_id, quantite_proposee) VALUES (:id, :d, :p, :q)")
				.param("id", UUID.randomUUID()).param("d", id).param("p", l.produitId()).param("q", l.quantite()).update();
		}
		if ("BROUILLON".equals(statut)) {
			jdbc.sql("UPDATE campagne SET statut = 'PROPOSEE' WHERE id = :c").param("c", campagneId).update();
		}
		evenements.publishEvent(new DepotPropose(id));
		return depot(id);
	}

	/** La somme des quantités proposées (hors dépôts refusés) ne dépasse pas la quantité totale de chaque ligne (§5.2). */
	private void verifierRepartition(UUID campagneId, UUID depotExclu, List<LigneQuantite> lignes) {
		var totaux = jdbc.sql("SELECT produit_id, quantite_totale FROM campagne_ligne WHERE campagne_id = :c").param("c", campagneId)
			.query((rs, i) -> Map.entry(rs.getObject(1, UUID.class), rs.getInt(2))).list().stream()
			.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
		for (var l : lignes) {
			if (!totaux.containsKey(l.produitId())) {
				throw Probleme.regle(null, "Produit hors de la campagne : " + l.produitId());
			}
			int deja = jdbc.sql("""
					SELECT coalesce(sum(dl.quantite_proposee), 0) FROM depot_ligne dl JOIN depot d ON d.id = dl.depot_id
					WHERE d.campagne_id = :c AND d.statut <> 'REFUSE' AND dl.produit_id = :p
					  AND d.id IS DISTINCT FROM CAST(:exclu AS uuid)
					""").param("c", campagneId).param("p", l.produitId()).param("exclu", depotExclu).query(Integer.class).single();
			if (deja + l.quantite() > totaux.get(l.produitId())) {
				throw Probleme.regle(null, "Répartition au-delà de la quantité totale du produit " + l.produitId());
			}
		}
	}

	List<Depot> depotsCampagne(UUID grandClient, UUID campagneId) {
		statutCampagne(grandClient, campagneId);
		return jdbc.sql("SELECT id FROM depot WHERE campagne_id = :c ORDER BY cree_le").param("c", campagneId)
			.query(UUID.class).list().stream().map(this::depot).toList();
	}

	Page<Depot> depots(UUID grossiste, String statut, int page, int taille) {
		var total = jdbc.sql("SELECT count(*) FROM depot WHERE grossiste_id = :g AND (CAST(:s AS text) IS NULL OR statut = :s)")
			.param("g", grossiste).param("s", statut).query(Long.class).single();
		var ids = jdbc.sql("""
				SELECT id FROM depot WHERE grossiste_id = :g AND (CAST(:s AS text) IS NULL OR statut = :s)
				ORDER BY cree_le DESC LIMIT :taille OFFSET :offset
				""").param("g", grossiste).param("s", statut).param("taille", taille).param("offset", page * taille)
			.query(UUID.class).list();
		return Page.de(page, taille, total, ids.stream().map(this::depot).toList());
	}

	/** Dépôt visible par son grossiste ou par la marque de la campagne ; 404 pour tout autre. */
	Depot depotVisible(UUID organisation, UUID depotId) {
		var visible = jdbc.sql("""
				SELECT count(*) FROM depot d JOIN campagne c ON c.id = d.campagne_id
				WHERE d.id = :d AND (d.grossiste_id = :o OR c.grand_client_id = :o)
				""").param("d", depotId).param("o", organisation).query(Long.class).single() > 0;
		if (!visible) {
			throw Probleme.introuvable();
		}
		return depot(depotId);
	}

	Depot depot(UUID id) {
		return jdbc.sql("""
				SELECT d.*, c.nom AS campagne_nom, c.statut AS statut_campagne, c.grand_client_id, c.fin
				FROM depot d JOIN campagne c ON c.id = d.campagne_id
				WHERE d.id = :id
				""").param("id", id).query((rs, i) -> {
				var campagneId = rs.getObject("campagne_id", UUID.class);
				// Plafond par ambulant (RG-P3) visible du grossiste, qui borne la quantité remise au retrait.
				var plafonds = jdbc.sql("SELECT produit_id, quantite_max_par_ambulant FROM campagne_ligne WHERE campagne_id = :c")
					.param("c", campagneId).query((r, n) -> Map.entry(r.getObject(1, UUID.class), r.getInt(2))).list().stream()
					.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
				var lignes = jdbc.sql(LIGNES_DEPOT + " WHERE dl.depot_id = :d GROUP BY dl.id, d.id ORDER BY dl.produit_id")
					.param("d", id).query(this::ligneDepot).list().stream()
					.map(l -> l.avecPlafond(plafonds.get(l.produitId()))).toList();
				var gc = rs.getObject("grand_client_id", UUID.class);
				var recu = rs.getTimestamp("recu_le");
				return new Depot(id, new Reference(campagneId, json.lire(rs.getString("campagne_nom"), LIBELLE).get("fr")),
						rs.getString("statut_campagne"), new Reference(gc, comptes.nomOrganisation(gc)), referentiel.grossiste(rs.getObject("grossiste_id", UUID.class)).orElse(null),
						rs.getString("statut"), lignes, rs.getTimestamp("fin").toInstant(), recu == null ? null : recu.toInstant(),
						photos.url(rs.getObject("photo_id", UUID.class)), json.lire(rs.getString("cloture"), OBJET));
			}).single();
	}

	private LigneDepot ligneDepot(ResultSet rs, int i) throws SQLException {
		int deposee = rs.getInt("quantite_deposee");
		int reservee = rs.getInt("reservee");
		int retiree = rs.getInt("retiree");
		return new LigneDepot(rs.getObject("produit_id", UUID.class), rs.getInt("quantite_proposee"),
				(Integer) rs.getObject("quantite_contre_proposee"), deposee, reservee, retiree, deposee - reservee - retiree, null);
	}

	private String statutDepot(UUID grossiste, UUID depotId) {
		return jdbc.sql("SELECT statut FROM depot WHERE id = :id AND grossiste_id = :g FOR UPDATE")
			.param("id", depotId).param("g", grossiste).query(String.class).optional().orElseThrow(Probleme::introuvable);
	}

	@Transactional
	Depot repondre(UUID grossiste, UUID depotId, ReponseGrossiste reponse) {
		if (!"PROPOSE".equals(statutDepot(grossiste, depotId))) {
			throw Probleme.conflit("Réponse possible seulement à l'état PROPOSE");
		}
		var statut = switch (reponse.decision()) {
			case "ACCEPTER" -> "ACCEPTE";
			case "REFUSER" -> "REFUSE";
			default -> {
				if (reponse.lignes() == null || reponse.lignes().isEmpty()) {
					throw Probleme.requete("lignes requises pour une contre-proposition");
				}
				for (var l : reponse.lignes()) {
					if (jdbc.sql("UPDATE depot_ligne SET quantite_contre_proposee = :q WHERE depot_id = :d AND produit_id = :p")
						.param("q", l.quantite()).param("d", depotId).param("p", l.produitId()).update() == 0) {
						throw Probleme.requete("Produit hors du dépôt : " + l.produitId());
					}
				}
				yield "CONTRE_PROPOSE";
			}
		};
		jdbc.sql("UPDATE depot SET statut = :s, commentaire = :c WHERE id = :id")
			.param("s", statut).param("c", reponse.commentaire()).param("id", depotId).update();
		return depot(depotId);
	}

	@Transactional
	Depot repondreMarque(UUID grandClient, UUID depotId, ReponseMarque reponse) {
		var depot = depotVisible(grandClient, depotId);
		if (!depot.marque().id().equals(grandClient)) {
			throw Probleme.introuvable();
		}
		if (!"CONTRE_PROPOSE".equals(depot.statut())) {
			throw Probleme.conflit("Réponse possible seulement à l'état CONTRE_PROPOSE");
		}
		if ("ACCEPTER".equals(reponse.decision())) {
			var lignes = depot.lignes().stream()
				.map(l -> new LigneQuantite(l.produitId(), l.quantiteContreProposee() != null ? l.quantiteContreProposee() : l.quantiteProposee()))
				.toList();
			verifierRepartition(depot.campagne().id(), depotId, lignes);
			jdbc.sql("UPDATE depot_ligne SET quantite_proposee = coalesce(quantite_contre_proposee, quantite_proposee) WHERE depot_id = :d")
				.param("d", depotId).update();
		}
		jdbc.sql("UPDATE depot SET statut = :s WHERE id = :id")
			.param("s", "ACCEPTER".equals(reponse.decision()) ? "ACCEPTE" : "REFUSE").param("id", depotId).update();
		return depot(depotId);
	}

	/**
	 * Pivot du flux promo (RG-P1) : la réception physique confirmée rend le dépôt visible des
	 * ambulants, active la campagne au premier dépôt reçu et déclenche leur notification.
	 */
	@Transactional
	Depot recevoir(UUID grossiste, UUID depotId, ReceptionDepot reception, MultipartFile photo) {
		if (!"ACCEPTE".equals(statutDepot(grossiste, depotId))) {
			throw Probleme.conflit("Réception possible seulement à l'état ACCEPTE");
		}
		if (reception.lignes().stream().noneMatch(l -> l.quantiteRecue() > 0)) {
			throw Probleme.regle("RG-P1", "Aucune quantité reçue : pas de confirmation");
		}
		for (var l : reception.lignes()) {
			if (jdbc.sql("UPDATE depot_ligne SET quantite_deposee = :q WHERE depot_id = :d AND produit_id = :p")
				.param("q", l.quantiteRecue()).param("d", depotId).param("p", l.produitId()).update() == 0) {
				throw Probleme.requete("Produit hors du dépôt : " + l.produitId());
			}
		}
		var photoId = photos.enregistrer(photo);
		jdbc.sql("UPDATE depot SET statut = 'RECU', recu_le = now(), photo_id = :photo, commentaire = coalesce(:c, commentaire) WHERE id = :id")
			.param("photo", photoId).param("c", reception.commentaire()).param("id", depotId).update();
		jdbc.sql("UPDATE campagne SET statut = 'ACTIVE' WHERE id = (SELECT campagne_id FROM depot WHERE id = :id) AND statut IN ('PROPOSEE', 'EPUISEE')")
			.param("id", depotId).update();
		annoncer("d.id = :id", depotId);
		return depot(depotId);
	}

	/** Clôture d'un dépôt reçu une fois la campagne épuisée ou expirée ; la campagne est clôturée avec son dernier dépôt. */
	@Transactional
	Depot cloturer(UUID grossiste, UUID depotId, ClotureDepot cloture) {
		var statut = statutDepot(grossiste, depotId);
		var campagne = jdbc.sql("SELECT c.id, c.statut FROM campagne c JOIN depot d ON d.campagne_id = c.id WHERE d.id = :d")
			.param("d", depotId).query((rs, i) -> Map.entry(rs.getObject(1, UUID.class), rs.getString(2))).single();
		if (!"RECU".equals(statut) || !List.of("EPUISEE", "EXPIREE").contains(campagne.getValue())) {
			throw Probleme.conflit("Clôture possible pour un dépôt reçu d'une campagne épuisée ou expirée");
		}
		jdbc.sql("UPDATE depot SET statut = 'CLOTURE', cloture = :c::jsonb WHERE id = :id")
			.param("c", json.ecrire(cloture)).param("id", depotId).update();
		jdbc.sql("""
				UPDATE campagne SET statut = 'CLOTUREE' WHERE id = :c
				  AND NOT EXISTS (SELECT 1 FROM depot WHERE campagne_id = :c AND statut = 'RECU')
				""").param("c", campagne.getKey()).update();
		publierOffres(depotId);
		return depot(depotId);
	}

	// ---------------------------------------------------------------- offres et réservations (ambulant)

	/**
	 * Offres visibles : produit d'une campagne ACTIVE en cours, dans un dépôt RECU (RG-P1), filtrées
	 * par ville ciblée et par catégorie (§9, {@link #vise}). ponytail: filtre et pagination en
	 * mémoire ; zones de tournée et rayon (Ciblage.zones, rayonM) pas encore appliqués.
	 */
	Page<OffrePromo> offres(UUID ambulantId, String categorie, int page, int taille) {
		var toutes = offresVisibles(null).stream()
			.filter(o -> categorie == null || o.produit().categorie().code().equals(categorie))
			.filter(o -> vise(o, ambulantId))
			.map(o -> avecDejaReserve(o, ambulantId))
			.sorted(Comparator.comparing(OffrePromo::fin))
			.toList();
		var debut = Math.min(page * taille, toutes.size());
		return Page.de(page, taille, toutes.size(), toutes.subList(debut, Math.min(debut + taille, toutes.size())));
	}

	private List<OffrePromo> offresVisibles(UUID offreId) {
		return jdbc.sql(LIGNES_DEPOT + """
				JOIN campagne c ON c.id = d.campagne_id
				WHERE d.statut = 'RECU' AND c.statut = 'ACTIVE' AND c.debut <= now() AND c.fin > now()
				  AND (CAST(:offre AS uuid) IS NULL OR dl.id = :offre)
				GROUP BY dl.id, d.id
				""").param("offre", offreId).query(this::ligneOffre).list().stream()
			.filter(o -> o != null)
			.toList();
	}

	private OffrePromo ligneOffre(ResultSet rs, int i) throws SQLException {
		var campagneId = rs.getObject("campagne_id", UUID.class);
		var produitId = rs.getObject("produit_id", UUID.class);
		var campagne = jdbc.sql("SELECT * FROM campagne WHERE id = :c").param("c", campagneId).query((r, n) -> r.getString("nom") + "\u0000"
				+ r.getInt("delai_retrait_heures") + "\u0000" + r.getTimestamp("fin").toInstant() + "\u0000" + r.getString("ciblage"))
			.single().split("\u0000");
		var grossiste = referentiel.grossiste(rs.getObject("grossiste_id", UUID.class)).orElse(null);
		var ciblage = json.lire("null".equals(campagne[3]) ? null : campagne[3], OBJET);
		// Ciblage (§5.1) : ville ou région du grossiste dépositaire, avec les codes de GET /v1/regions.
		var villeGrossiste = grossiste == null ? "" : Referentiel.codeVille(grossiste.ville());
		if (ciblage != null && ciblage.get("villes") instanceof List<?> villes && !villes.isEmpty()
				&& villes.stream().noneMatch(v -> Referentiel.codeVille(String.valueOf(v)).equals(villeGrossiste))) {
			return null;
		}
		if (ciblage != null && ciblage.get("regions") instanceof List<?> regions && !regions.isEmpty()
				&& referentiel.region(villeGrossiste).filter(r -> regions.stream().anyMatch(c -> r.equals(String.valueOf(c)))).isEmpty()) {
			return null;
		}
		var ligne = jdbc.sql("SELECT remise, quantite_max_par_ambulant, prix_revente_conseille FROM campagne_ligne WHERE campagne_id = :c AND produit_id = :p")
			.param("c", campagneId).param("p", produitId)
			.query((r, n) -> new Object[] { json.lire(r.getString(1), OBJET), r.getInt(2), r.getObject(3) }).single();
		int disponible = rs.getInt("quantite_deposee") - rs.getInt("reservee") - rs.getInt("retiree");
		@SuppressWarnings("unchecked")
		var remise = (Map<String, Object>) ligne[0];
		return new OffrePromo(rs.getObject("id", UUID.class), campagneId, json.lire(campagne[0], LIBELLE),
				rs.getObject("depot_id", UUID.class), referentiel.produit(produitId).orElseThrow(), remise, (Long) ligne[2],
				grossiste, disponible, (int) ligne[1], 0, Instant.parse(campagne[2]), Integer.parseInt(campagne[1]));
	}

	private OffrePromo avecDejaReserve(OffrePromo o, UUID ambulantId) {
		return new OffrePromo(o.id(), o.campagneId(), o.campagneNom(), o.depotId(), o.produit(), o.remise(),
				o.prixReventeConseille(), o.grossiste(), o.quantiteDisponible(), o.quantiteMaxParAmbulant(),
				dejaReserve(ambulantId, o.campagneId(), o.produit().id(), null), o.fin(), o.delaiRetraitHeures());
	}

	/** Déjà réservé (en cours) ou retiré par l'ambulant sur ce produit de la campagne (RG-P3). */
	private int dejaReserve(UUID ambulantId, UUID campagneId, UUID produitId, UUID sauf) {
		return jdbc.sql("""
				SELECT coalesce(sum(CASE WHEN statut = 'RETIREE' THEN quantite_retiree ELSE quantite_reservee END), 0)
				FROM reservation WHERE ambulant_id = :a AND campagne_id = :c AND produit_id = :p
				  AND (statut = 'RETIREE' OR (statut = 'RESERVEE' AND expire_le > now()))
				  AND id IS DISTINCT FROM CAST(:sauf AS uuid)
				""").param("a", ambulantId).param("c", campagneId).param("p", produitId).param("sauf", sauf)
			.query(Integer.class).single();
	}

	/**
	 * Réservation en ligne : quantité restante du dépôt (409), plafond par ambulant (422, RG-P3),
	 * expiration après le délai de la campagne (RG-P2). Rejouer le même id rend la réservation existante.
	 */
	@Transactional
	Map.Entry<Reservation, Boolean> reserver(UUID ambulantId, ReservationSaisie saisie) {
		var existante = jdbc.sql("SELECT ambulant_id FROM reservation WHERE id = :id").param("id", saisie.id()).query(UUID.class).optional();
		if (existante.isPresent()) {
			if (!existante.get().equals(ambulantId)) {
				throw Probleme.conflit("Identifiant de réservation déjà utilisé");
			}
			return Map.entry(reservation(saisie.id(), true), false);
		}
		// Verrou sur l'offre : deux ambulants ne peuvent pas réserver les mêmes dernières unités.
		jdbc.sql("SELECT id FROM depot_ligne WHERE id = :o FOR UPDATE").param("o", saisie.offreId()).query(UUID.class).optional()
			.orElseThrow(Probleme::introuvable);
		var offre = offresVisibles(saisie.offreId()).stream().filter(o -> vise(o, ambulantId)).findFirst()
			.orElseThrow(Probleme::introuvable);
		if (offre.quantiteDisponible() < saisie.quantite()) {
			throw Probleme.conflit("Quantité disponible insuffisante : " + offre.quantiteDisponible());
		}
		int deja = dejaReserve(ambulantId, offre.campagneId(), offre.produit().id(), null);
		if (deja + saisie.quantite() > offre.quantiteMaxParAmbulant()) {
			throw Probleme.regle("RG-P3", "Plafond par ambulant de " + offre.quantiteMaxParAmbulant() + " (déjà " + deja + ")");
		}
		jdbc.sql("""
				INSERT INTO reservation (id, offre_id, depot_id, campagne_id, produit_id, ambulant_id, quantite_reservee, expire_le)
				VALUES (:id, :o, :d, :c, :p, :a, :q, now() + make_interval(hours => :delai))
				""").param("id", saisie.id()).param("o", offre.id()).param("d", offre.depotId()).param("c", offre.campagneId())
			.param("p", offre.produit().id()).param("a", ambulantId).param("q", saisie.quantite())
			.param("delai", offre.delaiRetraitHeures()).update();
		publierReservation(saisie.id());
		publierOffre(offre.id());
		return Map.entry(reservation(saisie.id(), true), true);
	}

	Reservation reservation(UUID id, boolean avecQr) {
		return jdbc.sql("SELECT * FROM reservation WHERE id = :id").param("id", id).query((rs, i) -> ligneReservation(rs, avecQr)).single();
	}

	private Reservation ligneReservation(ResultSet rs, boolean avecQr) throws SQLException {
		var id = rs.getObject("id", UUID.class);
		var a = rs.getObject("ambulant_id", UUID.class);
		var expire = rs.getTimestamp("expire_le").toInstant();
		var statut = rs.getString("statut");
		if ("RESERVEE".equals(statut) && expire.isBefore(Instant.now())) {
			statut = "EXPIREE";
		}
		Integer revendue = null;
		if ("RETIREE".equals(statut)) {
			revendue = (int) Math.max(0, ventes.revendu(rs.getObject("campagne_id", UUID.class)).stream()
				.filter(r -> id.equals(r.reservationId())).mapToLong(Ventes.Revendu::quantite).sum());
			if (revendue > 0) {
				statut = "REVENDUE";
			}
		}
		var retiree = rs.getTimestamp("retiree_le");
		var qr = avecQr && "RESERVEE".equals(statut)
				? jetons.signer("resa", Map.of("reservationId", id.toString(), "depotId", rs.getObject("depot_id", UUID.class).toString()), null)
				: null;
		return new Reservation(id, statut, rs.getObject("campagne_id", UUID.class), rs.getObject("depot_id", UUID.class),
				rs.getObject("produit_id", UUID.class), referentiel.grossiste(grossisteDuDepot(rs.getObject("depot_id", UUID.class))).orElse(null),
				new Reference(a, comptes.nom(a)), rs.getInt("quantite_reservee"), (Integer) rs.getObject("quantite_retiree"),
				revendue, rs.getTimestamp("reservee_le").toInstant(), expire, retiree == null ? null : retiree.toInstant(), qr);
	}

	private UUID grossisteDuDepot(UUID depotId) {
		return jdbc.sql("SELECT grossiste_id FROM depot WHERE id = :d").param("d", depotId).query(UUID.class).single();
	}

	/** L'ambulant voit ses réservations (avec QR), le grossiste celles faites sur ses dépôts. */
	Page<Reservation> reservations(UUID ambulantId, UUID grossisteId, String statut, UUID depotId, int page, int taille) {
		var filtre = ambulantId != null ? "r.ambulant_id = :moi" : "d.grossiste_id = :moi";
		var etat = statut == null ? "" : switch (statut) {
			case "RESERVEE" -> " AND r.statut = 'RESERVEE' AND r.expire_le > now()";
			case "EXPIREE" -> " AND (r.statut = 'EXPIREE' OR (r.statut = 'RESERVEE' AND r.expire_le <= now()))";
			default -> " AND r.statut = 'RETIREE'";
		};
		var where = " FROM reservation r JOIN depot d ON d.id = r.depot_id WHERE " + filtre + etat
				+ " AND (CAST(:depot AS uuid) IS NULL OR r.depot_id = :depot)";
		var moi = ambulantId != null ? ambulantId : grossisteId;
		var total = jdbc.sql("SELECT count(*)" + where).param("moi", moi).param("depot", depotId).query(Long.class).single();
		var contenu = jdbc.sql("SELECT r.*" + where + " ORDER BY r.reservee_le DESC LIMIT :taille OFFSET :offset")
			.param("moi", moi).param("depot", depotId).param("taille", taille).param("offset", page * taille)
			.query((rs, i) -> ligneReservation(rs, ambulantId != null)).list().stream()
			.filter(r -> statut == null || !List.of("RETIREE", "REVENDUE").contains(statut) || r.statut().equals(statut))
			.toList();
		return Page.de(page, taille, total, contenu);
	}

	Reservation reservationVisible(UUID ambulantId, UUID grossisteId, UUID id) {
		var filtre = ambulantId != null ? "r.ambulant_id = :moi" : "d.grossiste_id = :moi";
		return jdbc.sql("SELECT r.* FROM reservation r JOIN depot d ON d.id = r.depot_id WHERE r.id = :id AND " + filtre)
			.param("id", id).param("moi", ambulantId != null ? ambulantId : grossisteId)
			.query((rs, i) -> ligneReservation(rs, ambulantId != null)).optional().orElseThrow(Probleme::introuvable);
	}

	// ---------------------------------------------------------------- retraits (grossiste)

	/**
	 * RG-P4 : le QR doit désigner une réservation sur un dépôt du grossiste connecté (422) ;
	 * expirée ou déjà retirée → 409.
	 */
	private Reservation reservationDuQr(UUID grossiste, String qrJeton) {
		UUID id;
		try {
			id = UUID.fromString(jetons.verifier(qrJeton, "resa").get("reservationId").toString());
		}
		catch (Jetons.Invalide | IllegalArgumentException e) {
			throw Probleme.regle("RG-P4", "QR de réservation invalide");
		}
		var depot = jdbc.sql("SELECT r.depot_id FROM reservation r WHERE r.id = :id FOR UPDATE").param("id", id)
			.query(UUID.class).optional().orElseThrow(() -> Probleme.regle("RG-P4", "Réservation inconnue"));
		if (!grossisteDuDepot(depot).equals(grossiste)) {
			throw Probleme.regle("RG-P4", "Réservation faite chez un autre grossiste");
		}
		var reservation = reservation(id, false);
		if (!"RESERVEE".equals(reservation.statut())) {
			throw Probleme.conflit("RG-P2", "Réservation " + reservation.statut());
		}
		return reservation;
	}

	@Transactional
	Reservation apercuRetrait(UUID grossiste, String qrJeton) {
		return reservationDuQr(grossiste, qrJeton);
	}

	/**
	 * Valide le retrait (RG-P4), recontrôle le plafond par ambulant (RG-P3), rend la quantité non
	 * remise au disponible et crée le mouvement PROMO du stock embarqué (RG-S3).
	 */
	@Transactional
	Reservation retirer(UUID grossisteOrganisation, UUID grossisteUtilisateur, UUID appareilSession, RetraitSaisie saisie) {
		if (!saisie.appareilId().equals(appareilSession)) {
			throw Probleme.interdit("appareil-different", "appareilId différent de l'appareil de la session (RG-S2)");
		}
		var r = reservationDuQr(grossisteOrganisation, saisie.qrJeton());
		if (saisie.quantiteRemise() > r.quantiteReservee()) {
			throw Probleme.regle("RG-P4", "Quantité remise supérieure à la quantité réservée");
		}
		int max = jdbc.sql("SELECT quantite_max_par_ambulant FROM campagne_ligne WHERE campagne_id = :c AND produit_id = :p")
			.param("c", r.campagneId()).param("p", r.produitId()).query(Integer.class).single();
		int deja = dejaReserve(r.ambulant().id(), r.campagneId(), r.produitId(), r.id());
		if (deja + saisie.quantiteRemise() > max) {
			throw Probleme.regle("RG-P3", "Plafond par ambulant de " + max + " (déjà " + deja + ")");
		}
		jdbc.sql("UPDATE reservation SET statut = 'RETIREE', quantite_retiree = :q, retiree_le = now() WHERE id = :id")
			.param("q", saisie.quantiteRemise()).param("id", r.id()).update();
		var organisationAmbulant = comptes.organisation(r.ambulant().id()).orElseThrow();
		stock.entreePromo(r.ambulant().id(), organisationAmbulant, grossisteUtilisateur, saisie.appareilId(), r.produitId(),
				saisie.quantiteRemise(), r.campagneId(), r.id());
		var offre = jdbc.sql("SELECT offre_id FROM reservation WHERE id = :id").param("id", r.id()).query(UUID.class).single();
		publierReservation(r.id());
		publierOffre(offre);
		reevaluerCiblage(r.ambulant().id());
		// Campagne épuisée quand plus rien n'est disponible ni réservé dans ses dépôts reçus.
		jdbc.sql("""
				UPDATE campagne SET statut = 'EPUISEE' WHERE id = :c AND statut = 'ACTIVE' AND NOT EXISTS (
				  SELECT 1 FROM (""" + LIGNES_DEPOT + """
				    WHERE d.campagne_id = :c AND d.statut = 'RECU' GROUP BY dl.id, d.id) x
				  WHERE x.quantite_deposee - x.reservee - x.retiree > 0 OR x.reservee > 0)
				""").param("c", r.campagneId()).update();
		evenements.publishEvent(new RetraitEffectue(r.id()));
		return reservation(r.id(), false);
	}

	// ---------------------------------------------------------------- entonnoir

	/** Entonnoir déposé → réservé → retiré → revendu, agrégé (aucune donnée nominative, §5.4). */
	Entonnoir entonnoir(UUID campagneId) {
		var revendu = ventes.revendu(campagneId);
		var reservationsDepot = jdbc.sql("SELECT id, depot_id FROM reservation WHERE campagne_id = :c").param("c", campagneId)
			.query((rs, i) -> Map.entry(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class))).list().stream()
			.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
		var parDepotProduit = new HashMap<List<UUID>, Long>();
		long revenduSansReservation = 0;
		var revenduParProduit = new HashMap<UUID, Long>();
		for (var r : revendu) {
			revenduParProduit.merge(r.produitId(), r.quantite(), Long::sum);
			var depot = r.reservationId() == null ? null : reservationsDepot.get(r.reservationId());
			if (depot == null) {
				revenduSansReservation += r.quantite();
			}
			else {
				parDepotProduit.merge(List.of(depot, r.produitId()), r.quantite(), Long::sum);
			}
		}
		var lignes = jdbc.sql("""
				SELECT d.id AS depot, d.grossiste_id, dl.produit_id,
				  CASE WHEN d.statut IN ('RECU', 'CLOTURE') THEN dl.quantite_deposee ELSE 0 END AS depose,
				  coalesce(sum(CASE WHEN r.statut = 'RETIREE' THEN r.quantite_retiree ELSE r.quantite_reservee END)
				    FILTER (WHERE r.statut = 'RETIREE' OR (r.statut = 'RESERVEE' AND r.expire_le > now())), 0) AS reserve,
				  coalesce(sum(r.quantite_retiree) FILTER (WHERE r.statut = 'RETIREE'), 0) AS retire
				FROM depot d JOIN depot_ligne dl ON dl.depot_id = d.id LEFT JOIN reservation r ON r.offre_id = dl.id
				WHERE d.campagne_id = :c GROUP BY d.id, dl.id
				""").param("c", campagneId)
			.query((rs, i) -> Map.entry(List.of(rs.getObject("depot", UUID.class), rs.getObject("grossiste_id", UUID.class),
					rs.getObject("produit_id", UUID.class)), new Etapes(rs.getLong("depose"), rs.getLong("reserve"), rs.getLong("retire"),
							0)))
			.list();
		var parGrossiste = new HashMap<UUID, Etapes>();
		var parProduit = new HashMap<UUID, Etapes>();
		var total = Etapes.ZERO;
		for (var l : lignes) {
			var depot = l.getKey().get(0);
			var grossiste = l.getKey().get(1);
			var produit = l.getKey().get(2);
			var e = l.getValue();
			var avecRevendu = new Etapes(e.depose(), e.reserve(), e.retire(),
					Math.max(0, parDepotProduit.getOrDefault(List.of(depot, produit), 0L)));
			parGrossiste.merge(grossiste, avecRevendu, Etapes::plus);
			parProduit.merge(produit, new Etapes(e.depose(), e.reserve(), e.retire(), 0), Etapes::plus);
			total = total.plus(new Etapes(e.depose(), e.reserve(), e.retire(), 0));
		}
		total = new Etapes(total.depose(), total.reserve(), total.retire(),
				Math.max(0, revenduParProduit.values().stream().mapToLong(Long::longValue).sum()));
		return new Entonnoir(campagneId, total,
				parGrossiste.entrySet().stream().map(g -> new EntonnoirGrossiste(new Reference(g.getKey(),
						referentiel.grossiste(g.getKey()).map(Grossiste::nom).orElse("—")), g.getValue().depose(), g.getValue().reserve(),
						g.getValue().retire(), g.getValue().revendu())).toList(),
				parProduit.entrySet().stream().map(p -> new EntonnoirProduit(p.getKey(), p.getValue().depose(), p.getValue().reserve(),
						p.getValue().retire(), Math.max(0, revenduParProduit.getOrDefault(p.getKey(), 0L)))).toList());
	}

	// ---------------------------------------------------------------- journal et échéances

	/**
	 * Dépôts reçus d'une campagne en cours : offres publiées aux ambulants et notification, une seule
	 * fois. Une campagne qui commence plus tard est annoncée par {@link #echeances()} à son début.
	 */
	private void annoncer(String filtre, UUID depotId) {
		jdbc.sql("""
				UPDATE depot d SET annonce_le = now() FROM campagne c
				WHERE c.id = d.campagne_id AND d.statut = 'RECU' AND d.annonce_le IS NULL
				  AND c.statut = 'ACTIVE' AND c.debut <= now() AND c.fin > now()""" + " AND " + filtre + " RETURNING d.id")
			.param("id", depotId).query(UUID.class).list()
			.forEach(id -> {
				publierOffres(id);
				evenements.publishEvent(new ReceptionConfirmee(id));
			});
	}

	private void publierOffres(UUID depotId) {
		jdbc.sql("SELECT id FROM depot_ligne WHERE depot_id = :d").param("d", depotId).query(UUID.class).list()
			.forEach(this::publierOffre);
	}

	/**
	 * Offre livrée hors ligne (entité PROMO) à tous les ambulants (destinataire null), ou à chaque
	 * ambulant visé si la campagne cible des catégories (§9) ; retirée des appareils qui ne la voient plus.
	 */
	private void publierOffre(UUID offreId) {
		var offre = offresVisibles(offreId).stream().findFirst().orElse(null);
		var pour = new HashSet<UUID>();
		if (offre != null) {
			var categories = categoriesCiblees(offre.campagneId());
			if (categories.isEmpty()) {
				pour.add(null);
			}
			else {
				pour.addAll(ambulantsVises(categories, offre.produit().marque(), comptes.ambulants()));
			}
		}
		pour.forEach(d -> journal.publier(d, "PROMO", offreId, offre));
		journal.destinataires("PROMO", offreId).stream().filter(d -> !pour.contains(d))
			.forEach(d -> journal.supprimer(d, "PROMO", offreId));
	}

	// ---------------------------------------------------------------- ciblage par catégorie (§9)

	private List<String> categoriesCiblees(UUID campagneId) {
		var ciblage = json.lire(jdbc.sql("SELECT ciblage::text FROM campagne WHERE id = :c").param("c", campagneId)
			.query(String.class).optional().orElse(null), OBJET);
		return ciblage != null && ciblage.get("categories") instanceof List<?> l ? l.stream().map(String::valueOf).toList()
				: List.of();
	}

	/**
	 * §9 : sans catégorie ciblée, tous ; sinon l'ambulant qui vend une catégorie ciblée, qui a déjà
	 * acheté la marque (chargement, retrait promo ou vente), ou qui n'a déclaré aucune catégorie.
	 */
	private Set<UUID> ambulantsVises(List<String> categories, String marque, Collection<UUID> ambulants) {
		if (categories.isEmpty()) {
			return Set.copyOf(ambulants);
		}
		var vendues = comptes.categoriesVendues(ambulants);
		var acheteurs = stock.ambulantsAyantAchete(referentiel.produitsDeMarque(marque), ambulants);
		return ambulants.stream().filter(a -> {
			var c = vendues.getOrDefault(a, List.of());
			return c.isEmpty() || c.stream().anyMatch(categories::contains) || acheteurs.contains(a);
		}).collect(Collectors.toSet());
	}

	private boolean vise(OffrePromo offre, UUID ambulantId) {
		return ambulantsVises(categoriesCiblees(offre.campagneId()), offre.produit().marque(), List.of(ambulantId))
			.contains(ambulantId);
	}

	/** Ambulants visés par au moins une ligne du dépôt : destinataires de « promo confirmée » (§9). */
	Set<UUID> ambulantsVises(UUID depotId) {
		var depot = depot(depotId);
		var categories = categoriesCiblees(depot.campagne().id());
		var ambulants = comptes.ambulants();
		var vises = new HashSet<UUID>();
		referentiel.produits(depot.lignes().stream().map(LigneDepot::produitId).toList()).values().stream()
			.map(Produit::marque).distinct().forEach(m -> vises.addAll(ambulantsVises(categories, m, ambulants)));
		return vises;
	}

	/**
	 * Profil ou historique d'achat de l'ambulant changé : ses offres ciblées par catégorie arrivent
	 * sur son appareil ou en repartent. ponytail: relit toutes les offres en cours à chaque
	 * chargement, vente ou profil ; à indexer par marque et catégorie si le nombre d'offres grandit.
	 */
	public void reevaluerCiblage(UUID ambulantId) {
		for (var offre : offresVisibles(null)) {
			if (categoriesCiblees(offre.campagneId()).isEmpty()) {
				continue;
			}
			var voit = vise(offre, ambulantId);
			var a = journal.destinataires("PROMO", offre.id()).contains(ambulantId);
			if (voit && !a) {
				journal.publier(ambulantId, "PROMO", offre.id(), offre);
			}
			else if (!voit && a) {
				journal.supprimer(ambulantId, "PROMO", offre.id());
			}
		}
	}

	private void publierReservation(UUID id) {
		var r = reservation(id, true);
		journal.publier(r.ambulant().id(), "RESERVATION", id, r);
	}

	/**
	 * Toutes les minutes : réservations non retirées à temps → EXPIREE, la quantité revient au
	 * disponible (RG-P2) ; campagnes arrivées à leur fin → EXPIREE ; rappel une heure avant l'expiration.
	 */
	@Scheduled(fixedDelay = 60_000)
	@Transactional
	public void echeances() {
		annoncer("d.annonce_le IS NULL", null);
		jdbc.sql("UPDATE reservation SET statut = 'EXPIREE' WHERE statut = 'RESERVEE' AND expire_le <= now() RETURNING id, offre_id")
			.query((rs, i) -> Map.entry(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class))).list()
			.forEach(r -> {
				publierReservation(r.getKey());
				publierOffre(r.getValue());
			});
		jdbc.sql("""
				UPDATE campagne SET statut = 'EXPIREE' WHERE statut IN ('PROPOSEE', 'ACTIVE', 'EPUISEE') AND fin <= now() RETURNING id
				""").query(UUID.class).list()
			.forEach(c -> jdbc.sql("SELECT dl.id FROM depot_ligne dl JOIN depot d ON d.id = dl.depot_id WHERE d.campagne_id = :c")
				.param("c", c).query(UUID.class).list().forEach(this::publierOffre));
		jdbc.sql("""
				UPDATE reservation SET rappel_envoye = true
				WHERE statut = 'RESERVEE' AND NOT rappel_envoye AND expire_le <= now() + interval '1 hour' RETURNING id
				""").query(UUID.class).list()
			.forEach(id -> evenements.publishEvent(new NotificationsPromos.RappelReservation(id)));
	}

}

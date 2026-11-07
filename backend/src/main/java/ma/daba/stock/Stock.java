package ma.daba.stock;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import ma.daba.commun.Anomalie;
import ma.daba.commun.Geste;
import ma.daba.commun.Geste.Effet;
import ma.daba.commun.Jetons;
import ma.daba.commun.Journal;
import ma.daba.commun.Json;
import ma.daba.commun.Probleme;
import ma.daba.commun.Reference;
import ma.daba.comptes.Comptes;
import ma.daba.referentiel.Referentiel;
import tools.jackson.core.type.TypeReference;

/**
 * Stock embarqué de l'ambulant : la somme de ses mouvements (RG-S2). Le stock promo est isolé par
 * campagne et réservation (RG-S3). Un fait terrain qui rend le stock négatif est enregistré puis
 * signalé en anomalie (RG-S1).
 */
@Service
public class Stock {

	public record LigneQuantite(@NotNull UUID produitId, @NotNull @Min(1) Integer quantite) {
	}

	/** Ligne chargée (chargement manuel, bon de sortie) : péremption (§3.2) et prix d'achat TTC (§3.8), facultatifs. */
	public record LigneChargement(@NotNull UUID produitId, @NotNull @Min(1) Integer quantite, LocalDate datePeremption,
			@Min(0) Long prixUnitaire) {
	}

	public record Mouvement(UUID id, String type, UUID produitId, int quantite, UUID auteurId, UUID appareilId,
			Instant horodatage, Instant recuLe, UUID operationId, UUID documentId, UUID bonSortieId, UUID campagneId,
			UUID reservationId, String motif, LocalDate datePeremption, Long prixUnitaire) {
	}

	public record StockPromo(UUID campagneId, UUID reservationId, int quantite) {
	}

	public record LigneStock(UUID produitId, int quantite, List<StockPromo> promo, LocalDate peremptionProche) {
	}

	/** Mouvement à enregistrer ; quantité positive en entrée, négative en sortie. */
	public record NouveauMouvement(String type, UUID produitId, int quantite, UUID documentId, UUID bonSortieId,
			UUID campagneId, UUID reservationId, String motif) {

		public static NouveauMouvement de(String type, UUID produitId, int quantite) {
			return new NouveauMouvement(type, produitId, quantite, null, null, null, null, null);
		}

	}

	public record InventaireLigne(@NotNull UUID produitId, @NotNull @Min(0) Integer quantiteComptee, String motifEcart) {
	}

	static final TypeReference<List<LigneChargement>> LIGNES = new TypeReference<>() {
	};

	private final JdbcClient jdbc;
	private final Journal journal;
	private final Jetons jetons;
	private final Json json;
	private final Referentiel referentiel;
	private final Comptes comptes;

	Stock(JdbcClient jdbc, Journal journal, Jetons jetons, Json json, Referentiel referentiel, Comptes comptes) {
		this.jdbc = jdbc;
		this.comptes = comptes;
		this.journal = journal;
		this.jetons = jetons;
		this.json = json;
		this.referentiel = referentiel;
	}

	/**
	 * Enregistre un mouvement (type, auteur, horodatage, appareil : RG-S2) et signale STOCK_NEGATIF
	 * (RG-S1) ou PROMO_SANS_RETRAIT (RG-S3) si une sortie dépasse le stock.
	 */
	@Transactional
	public List<Anomalie> enregistrer(Geste geste, UUID auteurId, NouveauMouvement m) {
		return enregistrer(geste, auteurId, m, null, null);
	}

	private List<Anomalie> enregistrer(Geste geste, UUID auteurId, NouveauMouvement m, LocalDate peremption, Long prix) {
		var reservation = m.reservationId();
		if (m.campagneId() != null && reservation == null && m.quantite() < 0) {
			// Vente promo sans réservation précisée : on puise dans la plus ancienne réservation retirée qui en a encore.
			reservation = jdbc.sql("""
					SELECT reservation_id FROM mouvement
					WHERE ambulant_id = :a AND produit_id = :p AND campagne_id = :c AND reservation_id IS NOT NULL
					GROUP BY reservation_id HAVING sum(quantite) > 0 ORDER BY min(horodatage) LIMIT 1
					""").param("a", geste.ambulantId()).param("p", m.produitId()).param("c", m.campagneId())
				.query(UUID.class).optional().orElse(null);
		}
		var id = UUID.randomUUID();
		jdbc.sql("""
				INSERT INTO mouvement (id, ambulant_id, organisation_id, type, produit_id, quantite, auteur_id, appareil_id,
				  horodatage, operation_id, document_id, bon_sortie_id, campagne_id, reservation_id, motif, date_peremption,
				  prix_unitaire)
				VALUES (:id, :a, :org, :type, :p, :q, :auteur, :appareil, :h, :op, :doc, :bon, :c, :r, :motif, :peremption, :prix)
				""")
			.param("id", id).param("a", geste.ambulantId()).param("org", geste.organisationId()).param("type", m.type())
			.param("p", m.produitId()).param("q", m.quantite()).param("auteur", auteurId)
			.param("appareil", geste.appareilId()).param("h", Timestamp.from(geste.horodatage()))
			.param("op", geste.operationId()).param("doc", m.documentId()).param("bon", m.bonSortieId())
			.param("c", m.campagneId()).param("r", reservation).param("motif", m.motif())
			.param("peremption", peremption).param("prix", prix)
			.update();
		journal.publier(geste.ambulantId(), "MOUVEMENT", id, mouvement(id));
		publierLigne(geste.ambulantId(), m.produitId());
		if (m.quantite() >= 0) {
			return List.of();
		}
		var anomalies = new ArrayList<Anomalie>();
		if (m.campagneId() == null) {
			var reste = jdbc.sql("""
					SELECT coalesce(sum(quantite), 0) FROM mouvement
					WHERE ambulant_id = :a AND produit_id = :p AND campagne_id IS NULL
					""").param("a", geste.ambulantId()).param("p", m.produitId()).query(Integer.class).single();
			if (reste < 0) {
				anomalies.add(new Anomalie("STOCK_NEGATIF", "RG-S1", m.produitId(),
						"Stock négatif après " + m.type() + " : " + reste));
			}
		}
		else {
			var reste = jdbc.sql("""
					SELECT coalesce(sum(quantite), 0) FROM mouvement
					WHERE ambulant_id = :a AND produit_id = :p AND campagne_id = :c AND reservation_id IS NOT DISTINCT FROM :r
					""").param("a", geste.ambulantId()).param("p", m.produitId()).param("c", m.campagneId())
				.param("r", reservation).query(Integer.class).single();
			if (reste < 0) {
				anomalies.add(new Anomalie("PROMO_SANS_RETRAIT", "RG-S3", m.produitId(),
						"Vente promo au-delà de la quantité retirée : " + reste));
			}
		}
		return anomalies;
	}

	/** Chargement par scan du QR d'un bon de sortie (lisible hors ligne, signé par le serveur). */
	@Transactional
	public Effet chargerBon(Geste geste, String qrJeton) {
		Map<String, Object> charge;
		try {
			charge = jetons.verifier(qrJeton, "bon");
		}
		catch (Jetons.Invalide e) {
			throw Probleme.requete("QR de bon de sortie invalide");
		}
		var bonId = UUID.fromString(charge.get("bonSortieId").toString());
		var bon = jdbc.sql("SELECT ambulant_id, lignes::text FROM bon_sortie WHERE id = :id").param("id", bonId)
			.query((rs, i) -> Map.entry(rs.getObject("ambulant_id", UUID.class), rs.getString("lignes")))
			.optional().orElseThrow(Probleme::introuvable);
		if (!bon.getKey().equals(geste.ambulantId())) {
			throw Probleme.requete("Bon de sortie adressé à un autre ambulant");
		}
		if (jdbc.sql("UPDATE bon_sortie SET charge_le = now() WHERE id = :id AND charge_le IS NULL").param("id", bonId).update() == 0) {
			return Effet.de(List.of(Anomalie.de("CHARGEMENT_EN_DOUBLE", "RG-S2", "Bon de sortie déjà chargé : rien n'est ajouté")));
		}
		for (var ligne : json.lire(bon.getValue(), LIGNES)) {
			enregistrer(geste, geste.ambulantId(), new NouveauMouvement("CHARGEMENT", ligne.produitId(), ligne.quantite(),
					null, bonId, null, null, null), ligne.datePeremption(), ligne.prixUnitaire());
		}
		journal.publier(geste.ambulantId(), "BON_SORTIE", bonId, bonSortie(bonId));
		return Effet.ok();
	}

	@Transactional
	public Effet chargerManuel(Geste geste, List<LigneChargement> lignes) {
		verifierProduits(lignes.stream().map(LigneChargement::produitId).toList());
		for (var ligne : lignes) {
			enregistrer(geste, geste.ambulantId(), NouveauMouvement.de("CHARGEMENT", ligne.produitId(), ligne.quantite()),
					ligne.datePeremption(), ligne.prixUnitaire());
		}
		return Effet.ok();
	}

	/**
	 * Retour d'un hanout (entrée seulement si le produit est remis en vente) ou au grossiste (sortie).
	 */
	@Transactional
	public Effet retour(Geste geste, boolean versGrossiste, boolean remisEnVente, String motif, List<LigneQuantite> lignes) {
		verifierProduits(lignes.stream().map(LigneQuantite::produitId).toList());
		var anomalies = new ArrayList<Anomalie>();
		for (var ligne : lignes) {
			if (versGrossiste) {
				anomalies.addAll(enregistrer(geste, geste.ambulantId(),
						new NouveauMouvement("RETOUR", ligne.produitId(), -ligne.quantite(), null, null, null, null, motif)));
			}
			else if (remisEnVente) {
				enregistrer(geste, geste.ambulantId(),
						new NouveauMouvement("RETOUR", ligne.produitId(), ligne.quantite(), null, null, null, null, motif));
			}
		}
		return Effet.de(anomalies);
	}

	/**
	 * Inventaire de fin de journée : un AJUSTEMENT par écart ; un écart au-delà du seuil sans motif
	 * est signalé ECART_SANS_MOTIF (RG-S4).
	 */
	@Transactional
	public Effet inventaire(Geste geste, List<InventaireLigne> lignes, BigDecimal seuilPourcent) {
		verifierProduits(lignes.stream().map(InventaireLigne::produitId).toList());
		var anomalies = new ArrayList<Anomalie>();
		for (var ligne : lignes) {
			int theorique = jdbc.sql("""
					SELECT coalesce(sum(quantite), 0) FROM mouvement
					WHERE ambulant_id = :a AND produit_id = :p AND campagne_id IS NULL
					""").param("a", geste.ambulantId()).param("p", ligne.produitId()).query(Integer.class).single();
			int ecart = ligne.quantiteComptee() - theorique;
			if (ecart == 0) {
				continue;
			}
			var motif = ligne.motifEcart() == null || ligne.motifEcart().isBlank() ? null : ligne.motifEcart();
			enregistrer(geste, geste.ambulantId(), new NouveauMouvement("AJUSTEMENT", ligne.produitId(), ecart, null, null,
					null, null, motif));
			if (motif == null && depasseSeuil(ecart, theorique, seuilPourcent)) {
				anomalies.add(new Anomalie("ECART_SANS_MOTIF", "RG-S4", ligne.produitId(),
						"Écart d'inventaire de " + ecart + " (théorique " + theorique + ") sans motif"));
			}
		}
		return Effet.de(anomalies);
	}

	static boolean depasseSeuil(int ecart, int theorique, BigDecimal seuilPourcent) {
		if (theorique <= 0) {
			return true;
		}
		return BigDecimal.valueOf(Math.abs(ecart) * 100L).compareTo(seuilPourcent.multiply(BigDecimal.valueOf(theorique))) > 0;
	}

	/**
	 * Entrée de stock promo au retrait chez le grossiste (RG-S3), avec l'appareil du grossiste qui a
	 * scanné ; sans opération de l'appareil ambulant, l'operationId est l'id de la réservation.
	 */
	@Transactional
	public void entreePromo(UUID ambulantId, UUID organisationId, UUID grossisteUtilisateurId, UUID appareilGrossiste,
			UUID produitId, int quantite, UUID campagneId, UUID reservationId) {
		var geste = new Geste(ambulantId, organisationId, appareilGrossiste, reservationId, Instant.now());
		enregistrer(geste, grossisteUtilisateurId,
				new NouveauMouvement("PROMO", produitId, quantite, null, null, campagneId, reservationId, null));
	}

	/** Parmi {@code ambulants}, ceux qui ont le produit en stock déclaré (RG-D1). */
	public Set<UUID> ambulantsAvecStock(UUID produitId, Collection<UUID> ambulants) {
		if (ambulants.isEmpty()) {
			return Set.of();
		}
		return Set.copyOf(jdbc.sql("""
				SELECT ambulant_id FROM mouvement WHERE produit_id = :p AND ambulant_id IN (:a)
				GROUP BY ambulant_id HAVING sum(quantite) > 0
				""").param("p", produitId).param("a", ambulants).query(UUID.class).list());
	}

	/** Parmi {@code ambulants}, ceux qui ont déjà chargé, retiré en promo ou vendu un de ces produits (§9). */
	public Set<UUID> ambulantsAyantAchete(Collection<UUID> produits, Collection<UUID> ambulants) {
		if (produits.isEmpty() || ambulants.isEmpty()) {
			return Set.of();
		}
		return Set.copyOf(jdbc.sql("""
				SELECT DISTINCT ambulant_id FROM mouvement
				WHERE produit_id IN (:p) AND ambulant_id IN (:a) AND type IN ('CHARGEMENT', 'PROMO', 'VENTE')
				""").param("p", produits).param("a", ambulants).query(UUID.class).list());
	}

	/** Ambulants rattachés à un grossiste : déclaré à leur profil (§3.1) ou au moins un bon de sortie reçu. */
	public Set<UUID> ambulantsRattaches(UUID grossisteId) {
		var rattaches = new HashSet<>(comptes.ambulantsDeclares(grossisteId));
		rattaches.addAll(jdbc.sql("SELECT DISTINCT ambulant_id FROM bon_sortie WHERE grossiste_id = :g").param("g", grossisteId)
			.query(UUID.class).list());
		return rattaches;
	}

	/** Grossistes auxquels l'ambulant est rattaché (même règle que {@link #ambulantsRattaches}). */
	public Set<UUID> grossistesRattaches(UUID ambulantId) {
		var rattaches = new HashSet<>(comptes.grossistesDeclares(ambulantId));
		rattaches.addAll(jdbc.sql("SELECT DISTINCT grossiste_id FROM bon_sortie WHERE ambulant_id = :a").param("a", ambulantId)
			.query(UUID.class).list());
		return rattaches;
	}

	/**
	 * Plus proche péremption des entrées encore en stock normal, par produit (§3.2).
	 * ponytail: suppose une consommation premier entré, premier sorti (les plus anciennes entrées
	 * sortent d'abord) ; un suivi par lot si l'ambulant vend en réalité dans un autre ordre.
	 */
	private Map<UUID, LocalDate> peremptions(UUID ambulantId) {
		var peremptions = new HashMap<UUID, LocalDate>();
		jdbc.sql("""
				SELECT e.produit_id, min(e.date_peremption) AS peremption FROM (
				  SELECT produit_id, date_peremption,
				    sum(quantite) OVER (PARTITION BY produit_id ORDER BY horodatage DESC, recu_le DESC, id) - quantite AS plus_recentes
				  FROM mouvement WHERE ambulant_id = :a AND campagne_id IS NULL AND quantite > 0) e
				JOIN (SELECT produit_id, sum(quantite) AS q FROM mouvement WHERE ambulant_id = :a AND campagne_id IS NULL
				      GROUP BY produit_id) s ON s.produit_id = e.produit_id
				WHERE e.date_peremption IS NOT NULL AND e.plus_recentes < s.q
				GROUP BY e.produit_id
				""").param("a", ambulantId)
			.query(rs -> {
				peremptions.put(rs.getObject(1, UUID.class), rs.getObject(2, LocalDate.class));
			});
		return peremptions;
	}

	public List<LigneStock> stock(UUID ambulantId) {
		var peremptions = peremptions(ambulantId);
		var lignes = new LinkedHashMap<UUID, LigneStock>();
		jdbc.sql("""
				SELECT produit_id, campagne_id, reservation_id, sum(quantite) AS q FROM mouvement WHERE ambulant_id = :a
				GROUP BY produit_id, campagne_id, reservation_id ORDER BY produit_id
				""").param("a", ambulantId).query(rs -> {
				var p = rs.getObject("produit_id", UUID.class);
				var ligne = lignes.computeIfAbsent(p, k -> new LigneStock(k, 0, new ArrayList<>(), null));
				var c = rs.getObject("campagne_id", UUID.class);
				if (c == null) {
					lignes.put(p, new LigneStock(p, rs.getInt("q"), ligne.promo(), null));
				}
				else if (rs.getInt("q") != 0) {
					ligne.promo().add(new StockPromo(c, rs.getObject("reservation_id", UUID.class), rs.getInt("q")));
				}
			});
		return lignes.values().stream()
			.filter(l -> l.quantite() != 0 || !l.promo().isEmpty())
			.map(l -> new LigneStock(l.produitId(), l.quantite(), l.promo().isEmpty() ? null : l.promo(),
					peremptions.get(l.produitId())))
			.toList();
	}

	private void publierLigne(UUID ambulantId, UUID produitId) {
		var ligne = stock(ambulantId).stream().filter(l -> l.produitId().equals(produitId)).findFirst()
			.orElse(new LigneStock(produitId, 0, null, null));
		journal.publier(ambulantId, "STOCK", produitId, ligne);
	}

	Mouvement mouvement(UUID id) {
		return jdbc.sql("SELECT * FROM mouvement WHERE id = :id").param("id", id).query(Stock::ligneMouvement).single();
	}

	static Mouvement ligneMouvement(ResultSet rs, int i) throws SQLException {
		return new Mouvement(rs.getObject("id", UUID.class), rs.getString("type"), rs.getObject("produit_id", UUID.class),
				rs.getInt("quantite"), rs.getObject("auteur_id", UUID.class), rs.getObject("appareil_id", UUID.class),
				rs.getTimestamp("horodatage").toInstant(), rs.getTimestamp("recu_le").toInstant(),
				rs.getObject("operation_id", UUID.class), rs.getObject("document_id", UUID.class),
				rs.getObject("bon_sortie_id", UUID.class), rs.getObject("campagne_id", UUID.class),
				rs.getObject("reservation_id", UUID.class), rs.getString("motif"), rs.getObject("date_peremption", LocalDate.class),
				(Long) rs.getObject("prix_unitaire"));
	}

	void verifierProduits(Collection<UUID> produits) {
		var connus = referentiel.produits(Set.copyOf(produits)).keySet();
		for (var p : produits) {
			if (!connus.contains(p)) {
				throw Probleme.regle(null, "Produit inconnu : " + p);
			}
		}
	}

	// ---------------------------------------------------------------- bons de sortie (§4.3)

	public record BonSortie(UUID id, String numero, UUID grossisteId, Reference ambulant, List<LigneChargement> lignes,
			Instant emisLe, String qrJeton, Instant chargeLe) {
	}

	record BonSortieSaisie(@NotNull UUID id, @NotNull @jakarta.validation.constraints.Pattern(regexp = "^\\+212[5-7][0-9]{8}$") String ambulantTelephone,
			@NotNull @jakarta.validation.constraints.Size(min = 1, max = 30) List<@jakarta.validation.Valid LigneChargement> lignes) {
	}

	/** Émet un bon de sortie pour un ambulant inscrit ; rejouer le même id rend le bon existant (cree = faux). */
	@Transactional
	Map.Entry<BonSortie, Boolean> creerBonSortie(UUID grossisteId, BonSortieSaisie saisie) {
		var existant = jdbc.sql("SELECT grossiste_id FROM bon_sortie WHERE id = :id").param("id", saisie.id())
			.query(UUID.class).optional();
		if (existant.isPresent()) {
			if (!existant.get().equals(grossisteId)) {
				throw Probleme.conflit("Identifiant de bon déjà utilisé");
			}
			return Map.entry(bonSortie(saisie.id()), false);
		}
		var ambulant = comptes.ambulantParTelephone(saisie.ambulantTelephone())
			.orElseThrow(() -> Probleme.regle(null, "Ce numéro n'est pas un ambulant inscrit"));
		verifierProduits(saisie.lignes().stream().map(LigneChargement::produitId).toList());
		var numero = "BS-%06d".formatted(jdbc.sql("SELECT nextval('bon_sortie_numero')").query(Long.class).single());
		jdbc.sql("""
				INSERT INTO bon_sortie (id, numero, grossiste_id, ambulant_id, lignes) VALUES (:id, :numero, :g, :a, :lignes::jsonb)
				""")
			.param("id", saisie.id()).param("numero", numero).param("g", grossisteId).param("a", ambulant)
			.param("lignes", json.ecrire(saisie.lignes()))
			.update();
		var bon = bonSortie(saisie.id());
		journal.publier(ambulant, "BON_SORTIE", bon.id(), bon);
		return Map.entry(bon, true);
	}

	BonSortie bonSortie(UUID id) {
		return jdbc.sql("SELECT * FROM bon_sortie WHERE id = :id").param("id", id).query(this::ligneBon).single();
	}

	BonSortie ligneBon(ResultSet rs, int i) throws SQLException {
		var id = rs.getObject("id", UUID.class);
		var grossiste = rs.getObject("grossiste_id", UUID.class);
		var ambulant = rs.getObject("ambulant_id", UUID.class);
		var lignes = json.lire(rs.getString("lignes"), LIGNES);
		var emis = rs.getTimestamp("emis_le").toInstant();
		var charge = rs.getTimestamp("charge_le");
		// Charge lisible hors ligne par l'app (BonSortieQr), signée par le serveur.
		var qr = jetons.signer("bon", Map.of("bonSortieId", id.toString(), "grossisteId", grossiste.toString(),
				"ambulantId", ambulant.toString(), "lignes", lignes, "emisLe", emis.toString()), null);
		return new BonSortie(id, rs.getString("numero"), grossiste, new Reference(ambulant, comptes.nom(ambulant)), lignes,
				emis, qr, charge == null ? null : charge.toInstant());
	}

}

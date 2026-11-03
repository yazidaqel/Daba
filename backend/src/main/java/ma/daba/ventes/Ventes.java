package ma.daba.ventes;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import ma.daba.commun.Anomalie;
import ma.daba.commun.Geste;
import ma.daba.commun.Geste.Conflit;
import ma.daba.commun.Geste.Effet;
import ma.daba.commun.Journal;
import ma.daba.commun.Json;
import ma.daba.commun.Probleme;
import ma.daba.commun.ProduitsHabituels;
import ma.daba.commun.Reference;
import ma.daba.comptes.Comptes;
import ma.daba.referentiel.Referentiel;
import ma.daba.stock.Stock;
import tools.jackson.core.type.TypeReference;

/**
 * Ventes de l'ambulant au hanout : bons de livraison et factures numérotés par appareil (RG-F1, RG-F2),
 * avoirs seuls pour annuler (RG-C4), versements imputés en FIFO (RG-C2), solde du karni (RG-C1)
 * et contrôle du plafond et du délai de crédit (RG-C3).
 */
@Service
public class Ventes implements ProduitsHabituels {

	// ---------------------------------------------------------------- saisies (opérations de synchronisation)

	public record LigneSaisie(@NotNull @jakarta.validation.constraints.Pattern(regexp = "^(ARTICLE|REMISE_PONCTUELLE|REMISE_PROMO)$") String nature,
			@NotNull UUID produitId, Integer quantite, Long prixUnitaire,
			@jakarta.validation.constraints.Pattern(regexp = "^(STANDARD|NEGOCIE)$") String tarif, Long montant,
			UUID campagneId, UUID reservationId, Integer tauxTva) {
	}

	public record Reglement(@NotNull @jakarta.validation.constraints.Pattern(regexp = "^(ESPECES|CHEQUE)$") String mode,
			@NotNull @Min(0) Long montant, String chequeNumero, String chequeBanque) {
	}

	public record MentionsLegales(@NotNull @jakarta.validation.constraints.Pattern(regexp = "^[0-9]{15}$") String ice,
			@NotBlank String identifiantFiscal, @NotBlank String registreCommerce, @NotNull Boolean assujettiTva) {
	}

	public record ConfirmationHanout(@NotNull @jakarta.validation.constraints.Pattern(regexp = "^(SIGNATURE|CONFIRMATION_ECRAN)$") String mode,
			String signaturePng, @NotNull Instant le) {
	}

	public record EmissionDocument(@NotNull UUID documentId,
			@NotNull @jakarta.validation.constraints.Pattern(regexp = "^(BON_LIVRAISON|FACTURE)$") String type,
			@NotNull String numero, @NotNull @Min(1) Long sequence, @NotNull UUID hanoutId,
			@NotEmpty List<@Valid LigneSaisie> lignes, List<@Valid Reglement> reglements, UUID demandeDepannageId,
			@Valid ConfirmationHanout confirmationHanout, Boolean avertissementCreditAccepte,
			@Valid MentionsLegales mentionsLegales) {
	}

	public record AvoirSaisie(@NotNull UUID documentId, @NotNull UUID documentOrigineId, @NotNull String numero,
			@NotNull @Min(1) Long sequence,
			@NotNull @jakarta.validation.constraints.Pattern(regexp = "^(ERREUR_SAISIE|RETOUR_PRODUIT|GESTE_COMMERCIAL|AUTRE)$") String motif,
			@jakarta.validation.constraints.Size(max = 500) String commentaire, List<@Valid LigneSaisie> lignes) {
	}

	public record Imputation(@NotNull UUID documentId, @NotNull @Min(1) Long montant) {
	}

	public record VersementSaisie(@NotNull UUID versementId, @NotNull UUID hanoutId,
			@NotNull @jakarta.validation.constraints.Pattern(regexp = "^(ESPECES|CHEQUE)$") String mode,
			@NotNull @Min(1) Long montant, String chequeNumero, String chequeBanque, List<@Valid Imputation> imputations) {
	}

	public record Tarif(@NotNull UUID produitId, UUID hanoutId, @NotNull @Min(0) Long prixUnitaire) {
	}

	public record CreditHanout(@NotNull UUID hanoutId, @Min(0) Long plafond, @Min(1) Integer delaiMaxJours) {
	}

	public record ParametresSaisie(@jakarta.validation.constraints.Pattern(regexp = "^(BLOQUER|AVERTIR)$") String politiqueDepassementCredit,
			@DecimalMin("0") @DecimalMax("100") BigDecimal plafondRemisePonctuellePourcent,
			@Valid MentionsLegales mentionsLegales) {
	}

	// ---------------------------------------------------------------- vues

	public record LigneDocument(String nature, UUID produitId, Integer quantite, Long prixUnitaire, String tarif,
			long montant, UUID campagneId, UUID reservationId, Integer tauxTva, Map<String, String> libelle,
			Map<String, String> campagneNom) {
	}

	public record EcartReception(@NotNull @Min(0) Integer ligne, @NotNull @Min(0) Integer quantiteRecue) {
	}

	public record ReceptionHanout(boolean conforme, List<EcartReception> ecarts, String commentaire, Instant le) {
	}

	public record Document(UUID id, String type, String numero, long sequence, UUID appareilId, Reference ambulant,
			Reference hanout, Instant emisLe, List<LigneDocument> lignes, long total, Long tva, long montantRegle,
			long montantCredit, String statut, UUID documentOrigineId, List<UUID> avoirIds, String motifAvoir,
			UUID demandeDepannageId, MentionsLegales mentionsLegales, ConfirmationHanout confirmationHanout,
			ReceptionHanout receptionHanout) {
	}

	static final Set<Integer> TAUX_TVA = Set.of(0, 7, 10, 14, 20);

	public record Versement(UUID versementId, UUID hanoutId, String mode, long montant, String chequeNumero,
			String chequeBanque, List<Imputation> imputations, UUID ambulantId, Instant recuLe) {
	}

	public record CompteCredit(UUID id, Reference ambulant, Reference hanout, long solde, Long plafond,
			Integer delaiMaxJours, Instant plusAncienImpayeLe, String depassement) {
	}

	public record Parametres(String politiqueDepassementCredit, BigDecimal plafondRemisePonctuellePourcent,
			BigDecimal seuilEcartInventairePourcent, MentionsLegales mentionsLegales) {
	}

	/** Pièce émise : l'outbox la transmet au listener qui prévient le hanout par SMS (§9). */
	public record DocumentEmis(UUID documentId) {
	}

	static final Pattern NUMERO = Pattern.compile("^([A-Z0-9]{3,6})-(BL|FA|AV)-([0-9]{6,})$");
	static final Map<String, String> SERIES = Map.of("BON_LIVRAISON", "BL", "FACTURE", "FA", "AVOIR", "AV");
	static final TypeReference<List<LigneDocument>> LIGNES = new TypeReference<>() {
	};

	/** Reste dû de chaque pièce à crédit : crédit − imputations − avoirs (RG-C2). */
	static final String RESTES = """
			SELECT d.id, d.emis_le, greatest(0, d.montant_credit
			  - coalesce((SELECT sum(i.montant) FROM imputation i WHERE i.document_id = d.id), 0)
			  - coalesce((SELECT sum(a.total) FROM document a WHERE a.document_origine_id = d.id), 0)) AS reste
			FROM document d WHERE d.ambulant_id = :a AND d.hanout_id = :h AND d.type <> 'AVOIR' AND d.montant_credit > 0
			""";

	private final JdbcClient jdbc;
	private final Json json;
	private final Journal journal;
	private final Stock stock;
	private final Referentiel referentiel;
	private final Comptes comptes;
	private final ApplicationEventPublisher evenements;

	Ventes(JdbcClient jdbc, Json json, Journal journal, Stock stock, Referentiel referentiel, Comptes comptes,
			ApplicationEventPublisher evenements) {
		this.jdbc = jdbc;
		this.json = json;
		this.journal = journal;
		this.stock = stock;
		this.referentiel = referentiel;
		this.comptes = comptes;
		this.evenements = evenements;
	}

	// ---------------------------------------------------------------- émission

	/**
	 * Enregistre une vente faite sur le terrain. Seule une pièce mal formée est rejetée (dont une
	 * FACTURE sans mentions légales, RG-F2) ; le reste (numérotation, crédit, stock) est appliqué et
	 * signalé en anomalie. {@code nomsCampagnes} : référence imprimée sur les lignes promo (RG-F3).
	 */
	@Transactional
	public Effet emettre(Geste geste, EmissionDocument e, Map<UUID, Map<String, String>> nomsCampagnes) {
		if ("FACTURE".equals(e.type()) && e.mentionsLegales() == null) {
			throw Probleme.regle("RG-F2", "Facture sans mentions légales (ICE, IF, RC, TVA)");
		}
		verifierNouveau(e.documentId());
		var hanout = referentiel.hanout(e.hanoutId()).orElseThrow(() -> Probleme.regle(null, "Hanout inconnu"));
		var lignes = lignes(e.lignes(), nomsCampagnes);
		var anomalies = new ArrayList<>(numerotation(geste, e.type(), e.numero(), e.sequence()));

		long total = lignes.stream().mapToLong(LigneDocument::montant).sum();
		long regle = e.reglements() == null ? 0 : e.reglements().stream().mapToLong(Reglement::montant).sum();
		long credit = Math.max(0, total - regle);

		var compte = compteId(geste.ambulantId(), geste.organisationId(), hanout.id());
		if (credit > 0) {
			anomalies.addAll(controleCredit(geste.ambulantId(), hanout.id(), credit));
		}
		anomalies.addAll(controleRemise(geste.ambulantId(), lignes));
		if ("FACTURE".equals(e.type()) && e.mentionsLegales().assujettiTva() && tva(lignes) == null) {
			// La facture existe sur le terrain : enregistrée, mais sans TVA calculable elle est signalée (RG-F2).
			anomalies.add(Anomalie.de("TVA_INCONNUE", "RG-F2", "Facture assujettie dont un produit n'a pas de taux de TVA"));
		}

		jdbc.sql("""
				INSERT INTO document (id, type, numero, serie, sequence, appareil_id, ambulant_id, organisation_id, hanout_id,
				  emis_le, lignes, total, montant_regle, montant_credit, reglements, demande_depannage_id, mentions_legales,
				  confirmation_hanout, operation_id, ambulant_nom)
				VALUES (:id, :type, :numero, :serie, :seq, :appareil, :a, :org, :h, :emis, :lignes::jsonb, :total, :regle, :credit,
				  :reglements::jsonb, :demande, :mentions::jsonb, :confirmation::jsonb, :op, :nomAmbulant)
				""")
			.param("id", e.documentId()).param("type", e.type()).param("numero", e.numero())
			.param("serie", serie(e.numero())).param("seq", e.sequence()).param("appareil", geste.appareilId())
			.param("a", geste.ambulantId()).param("org", geste.organisationId()).param("h", hanout.id())
			.param("emis", Timestamp.from(geste.horodatage())).param("lignes", json.ecrire(lignes))
			.param("total", total).param("regle", Math.min(regle, total)).param("credit", credit)
			.param("reglements", json.ecrire(e.reglements())).param("demande", e.demandeDepannageId())
			.param("mentions", json.ecrire(e.mentionsLegales())).param("confirmation", json.ecrire(e.confirmationHanout()))
			.param("op", geste.operationId()).param("nomAmbulant", comptes.nomPourDocument(geste.ambulantId()))
			.update();

		for (var ligne : lignes) {
			if ("ARTICLE".equals(ligne.nature())) {
				anomalies.addAll(stock.enregistrer(geste, geste.ambulantId(), new Stock.NouveauMouvement("VENTE",
						ligne.produitId(), -ligne.quantite(), e.documentId(), null, ligne.campagneId(), ligne.reservationId(), null)));
			}
		}
		publier(e.documentId());
		publierCompte(compte);
		evenements.publishEvent(new DocumentEmis(e.documentId()));
		return Effet.de(anomalies);
	}

	/** Avoir : seul moyen d'annuler tout ou partie d'une pièce (RG-C4), numéroté dans la série AV (RG-F1). */
	@Transactional
	public Effet avoir(Geste geste, AvoirSaisie a, Map<UUID, Map<String, String>> nomsCampagnes) {
		verifierNouveau(a.documentId());
		var origine = jdbc.sql("""
				SELECT d.*, d.total - coalesce((SELECT sum(x.total) FROM document x WHERE x.document_origine_id = d.id), 0) AS restant
				FROM document d WHERE d.id = :id AND d.organisation_id = :org AND d.type <> 'AVOIR'
				""").param("id", a.documentOrigineId()).param("org", geste.organisationId())
			.query((rs, i) -> Map.entry(rs.getObject("hanout_id", UUID.class),
					Map.entry(rs.getLong("restant"), rs.getString("lignes"))))
			.optional().orElseThrow(() -> Probleme.regle(null, "Document d'origine inconnu"));
		var hanoutId = origine.getKey();
		long restant = origine.getValue().getKey();
		List<LigneDocument> lignes;
		long total;
		if (a.lignes() == null || a.lignes().isEmpty()) {
			// Annulation complète : l'avoir reprend les lignes d'origine pour la traçabilité promo (RG-S3).
			lignes = json.lire(origine.getValue().getValue(), LIGNES);
			total = restant;
		}
		else {
			lignes = lignes(a.lignes(), nomsCampagnes);
			total = lignes.stream().mapToLong(LigneDocument::montant).sum();
		}
		if (total <= 0 || total > restant) {
			throw Probleme.regle("RG-C4", "Avoir de " + total + " pour un restant à annuler de " + restant);
		}
		var anomalies = numerotation(geste, "AVOIR", a.numero(), a.sequence());
		var compte = compteId(geste.ambulantId(), geste.organisationId(), hanoutId);
		jdbc.sql("""
				INSERT INTO document (id, type, numero, serie, sequence, appareil_id, ambulant_id, organisation_id, hanout_id,
				  emis_le, lignes, total, montant_regle, montant_credit, document_origine_id, motif_avoir, commentaire, operation_id,
				  ambulant_nom)
				VALUES (:id, 'AVOIR', :numero, :serie, :seq, :appareil, :a, :org, :h, :emis, :lignes::jsonb, :total, 0, 0,
				  :origine, :motif, :commentaire, :op, :nomAmbulant)
				""")
			.param("id", a.documentId()).param("numero", a.numero()).param("serie", serie(a.numero()))
			.param("seq", a.sequence()).param("appareil", geste.appareilId()).param("a", geste.ambulantId())
			.param("org", geste.organisationId()).param("h", hanoutId).param("emis", Timestamp.from(geste.horodatage()))
			.param("lignes", json.ecrire(lignes)).param("total", total).param("origine", a.documentOrigineId())
			.param("motif", a.motif()).param("commentaire", a.commentaire()).param("op", geste.operationId())
			.param("nomAmbulant", comptes.nomPourDocument(geste.ambulantId()))
			.update();
		publier(a.documentId());
		publier(a.documentOrigineId());
		publierCompte(compte);
		return Effet.de(anomalies);
	}

	private void verifierNouveau(UUID documentId) {
		if (jdbc.sql("SELECT count(*) FROM document WHERE id = :id").param("id", documentId).query(Long.class).single() > 0) {
			throw Probleme.regle(null, "Document déjà enregistré par une autre opération : " + documentId);
		}
	}

	/** Lignes enrichies du libellé produit et de la référence de campagne (RG-F3), montants calculés. */
	private List<LigneDocument> lignes(List<LigneSaisie> saisies, Map<UUID, Map<String, String>> nomsCampagnes) {
		var produits = referentiel.produits(saisies.stream().map(LigneSaisie::produitId).collect(java.util.stream.Collectors.toSet()));
		return saisies.stream().map(l -> {
			var produit = produits.get(l.produitId());
			if (produit == null) {
				throw Probleme.regle(null, "Produit inconnu : " + l.produitId());
			}
			long montant;
			if ("ARTICLE".equals(l.nature())) {
				if (l.quantite() == null || l.quantite() < 1 || l.prixUnitaire() == null || l.prixUnitaire() < 0) {
					throw Probleme.requete("Ligne ARTICLE : quantite ≥ 1 et prixUnitaire ≥ 0 requis");
				}
				montant = l.quantite() * l.prixUnitaire();
			}
			else {
				if (l.montant() == null || l.montant() >= 0) {
					throw Probleme.requete("Ligne de remise : montant négatif requis (RG-F3)");
				}
				if ("REMISE_PROMO".equals(l.nature()) && l.campagneId() == null) {
					throw Probleme.regle("RG-F3", "REMISE_PROMO sans référence de campagne");
				}
				montant = l.montant();
			}
			var campagne = l.campagneId() == null ? null : nomsCampagnes.get(l.campagneId());
			if (l.campagneId() != null && campagne == null) {
				throw Probleme.regle(null, "Campagne inconnue : " + l.campagneId());
			}
			if (l.tauxTva() != null && !TAUX_TVA.contains(l.tauxTva())) {
				throw Probleme.requete("Taux de TVA inconnu : " + l.tauxTva());
			}
			// Le taux connu de l'appareil à l'émission est figé dans la pièce (RG-F2), sinon le taux actuel du produit.
			return new LigneDocument(l.nature(), l.produitId(), "ARTICLE".equals(l.nature()) ? l.quantite() : null,
					"ARTICLE".equals(l.nature()) ? l.prixUnitaire() : null, l.tarif(), montant, l.campagneId(),
					l.reservationId(), l.tauxTva() != null ? l.tauxTva() : produit.tauxTva(), produit.libelle(), campagne);
		}).toList();
	}

	/**
	 * RG-F1 : numéro {@code PREFIXE-SERIE-SEQUENCE} cohérent avec le type et le préfixe attribué à
	 * l'appareil ; un doublon ou un trou dans la série de l'émetteur est signalé, jamais refusé (la
	 * pièce existe sur le terrain).
	 */
	List<Anomalie> numerotation(Geste geste, String type, String numero, long sequence) {
		var m = NUMERO.matcher(numero);
		if (!m.matches() || !m.group(2).equals(SERIES.get(type)) || Long.parseLong(m.group(3)) != sequence) {
			throw Probleme.requete("Numéro " + numero + " incohérent avec le type " + type + " et la séquence " + sequence);
		}
		if (!comptes.prefixe(geste.ambulantId(), geste.appareilId()).map(m.group(1)::equals).orElse(false)) {
			throw Probleme.regle("RG-F1", "Le préfixe " + m.group(1) + " n'est pas celui de cet ambulant sur cet appareil");
		}
		var anomalies = new ArrayList<Anomalie>();
		var serie = serie(numero);
		if (jdbc.sql("SELECT count(*) FROM document WHERE serie = :s AND sequence = :seq").param("s", serie).param("seq", sequence)
			.query(Long.class).single() > 0) {
			anomalies.add(Anomalie.de("NUMERO_EN_DOUBLE", "RG-F1", "Numéro " + numero + " déjà émis"));
		}
		long max = jdbc.sql("SELECT coalesce(max(sequence), 0) FROM document WHERE serie = :s").param("s", serie)
			.query(Long.class).single();
		if (sequence > max + 1) {
			anomalies.add(Anomalie.de("NUMEROTATION_TROU", "RG-F1",
					"Séquences " + (max + 1) + " à " + (sequence - 1) + " manquantes dans la série " + serie));
		}
		return anomalies;
	}

	static String serie(String numero) {
		return numero.substring(0, numero.lastIndexOf('-'));
	}

	/** RG-C3 : plafond et délai du hanout, contrôlés sur la vente à crédit. */
	private List<Anomalie> controleCredit(UUID ambulantId, UUID hanoutId, long credit) {
		var anomalies = new ArrayList<Anomalie>();
		var compte = jdbc.sql("SELECT plafond, delai_max_jours FROM compte_credit WHERE ambulant_id = :a AND hanout_id = :h")
			.param("a", ambulantId).param("h", hanoutId)
			.query((rs, i) -> Map.entry(Optional.ofNullable((Long) rs.getObject("plafond")),
					Optional.ofNullable((Integer) rs.getObject("delai_max_jours"))))
			.single();
		var politique = parametres(ambulantId).politiqueDepassementCredit();
		long solde = solde(ambulantId, hanoutId);
		if (compte.getKey().isPresent() && solde + credit > compte.getKey().get()) {
			anomalies.add(Anomalie.de("PLAFOND_CREDIT_DEPASSE", "RG-C3", "Solde " + (solde + credit) + " au-delà du plafond "
					+ compte.getKey().get() + " (politique " + politique + ")"));
		}
		var plusAncien = plusAncienImpaye(ambulantId, hanoutId);
		if (compte.getValue().isPresent() && plusAncien != null
				&& plusAncien.isBefore(Instant.now().minus(Duration.ofDays(compte.getValue().get())))) {
			anomalies.add(Anomalie.de("DELAI_CREDIT_DEPASSE", "RG-C3", "Impayé depuis le " + plusAncien + ", délai "
					+ compte.getValue().get() + " jours (politique " + politique + ")"));
		}
		return anomalies;
	}

	/** §3.4 : remise ponctuelle plafonnée en pourcentage du montant des articles. */
	private List<Anomalie> controleRemise(UUID ambulantId, List<LigneDocument> lignes) {
		var plafond = parametres(ambulantId).plafondRemisePonctuellePourcent();
		long articles = lignes.stream().filter(l -> "ARTICLE".equals(l.nature())).mapToLong(LigneDocument::montant).sum();
		long remise = -lignes.stream().filter(l -> "REMISE_PONCTUELLE".equals(l.nature())).mapToLong(LigneDocument::montant).sum();
		if (plafond != null && remise > 0 && BigDecimal.valueOf(remise * 100).compareTo(plafond.multiply(BigDecimal.valueOf(articles))) > 0) {
			return List.of(Anomalie.de("REMISE_PLAFOND_DEPASSE", "§3.4", "Remise de " + remise + " au-delà de " + plafond + " %"));
		}
		return List.of();
	}

	// ---------------------------------------------------------------- versements et paramètres

	/** Versement d'un hanout, imputé en FIFO sur les pièces à crédit les plus anciennes, sauf répartition choisie (RG-C2). */
	@Transactional
	public Effet verser(Geste geste, VersementSaisie v) {
		referentiel.hanout(v.hanoutId()).orElseThrow(() -> Probleme.regle(null, "Hanout inconnu"));
		if (jdbc.sql("SELECT count(*) FROM versement WHERE id = :id").param("id", v.versementId()).query(Long.class).single() > 0) {
			throw Probleme.regle(null, "Versement déjà enregistré par une autre opération");
		}
		var compte = compteId(geste.ambulantId(), geste.organisationId(), v.hanoutId());
		var restes = jdbc.sql(RESTES + " ORDER BY d.emis_le, d.sequence").param("a", geste.ambulantId()).param("h", v.hanoutId())
			.query((rs, i) -> Map.entry(rs.getObject("id", UUID.class), rs.getLong("reste")))
			.list();
		var imputations = new ArrayList<Imputation>();
		if (v.imputations() != null && !v.imputations().isEmpty()) {
			var pieces = restes.stream().map(Map.Entry::getKey).collect(java.util.stream.Collectors.toSet());
			long somme = 0;
			for (var imp : v.imputations()) {
				if (!pieces.contains(imp.documentId())) {
					throw Probleme.regle("RG-C2", "Imputation sur une pièce inconnue ou sans crédit : " + imp.documentId());
				}
				somme += imp.montant();
				imputations.add(imp);
			}
			if (somme > v.montant()) {
				throw Probleme.regle("RG-C2", "Imputations supérieures au versement");
			}
		}
		else {
			long reste = v.montant();
			for (var piece : restes) {
				if (reste == 0) {
					break;
				}
				long part = Math.min(reste, piece.getValue());
				if (part > 0) {
					imputations.add(new Imputation(piece.getKey(), part));
					reste -= part;
				}
			}
		}
		jdbc.sql("""
				INSERT INTO versement (id, ambulant_id, organisation_id, hanout_id, mode, montant, cheque_numero, cheque_banque,
				  verse_le, operation_id)
				VALUES (:id, :a, :org, :h, :mode, :montant, :num, :banque, :le, :op)
				""")
			.param("id", v.versementId()).param("a", geste.ambulantId()).param("org", geste.organisationId())
			.param("h", v.hanoutId()).param("mode", v.mode()).param("montant", v.montant())
			.param("num", v.chequeNumero()).param("banque", v.chequeBanque())
			.param("le", Timestamp.from(geste.horodatage())).param("op", geste.operationId())
			.update();
		for (var imp : imputations) {
			jdbc.sql("INSERT INTO imputation (versement_id, document_id, montant) VALUES (:v, :d, :m)")
				.param("v", v.versementId()).param("d", imp.documentId()).param("m", imp.montant()).update();
		}
		journal.publier(geste.ambulantId(), "VERSEMENT", v.versementId(), versement(v.versementId()));
		publierCompte(compte);
		return Effet.ok();
	}

	/** Tarif standard ou négocié ; une modification plus récente faite ailleurs est conservée. */
	@Transactional
	public Effet tarif(Geste geste, Tarif t) {
		referentiel.produit(t.produitId()).orElseThrow(() -> Probleme.regle(null, "Produit inconnu"));
		if (t.hanoutId() != null) {
			referentiel.hanout(t.hanoutId()).orElseThrow(() -> Probleme.regle(null, "Hanout inconnu"));
		}
		var serveur = jdbc.sql("""
				SELECT id, prix_unitaire, modifie_le_appareil FROM tarif
				WHERE ambulant_id = :a AND produit_id = :p AND hanout_id IS NOT DISTINCT FROM :h
				""").param("a", geste.ambulantId()).param("p", t.produitId()).param("h", t.hanoutId())
			.query((rs, i) -> Map.entry(rs.getObject("id", UUID.class),
					Map.entry(rs.getLong("prix_unitaire"), rs.getTimestamp("modifie_le_appareil").toInstant())))
			.optional();
		if (serveur.isPresent() && serveur.get().getValue().getValue().isAfter(geste.horodatage())) {
			return new Effet(List.of(), Conflit.versionServeur("Tarif modifié plus récemment sur un autre appareil",
					new Tarif(t.produitId(), t.hanoutId(), serveur.get().getValue().getKey())));
		}
		var id = serveur.map(Map.Entry::getKey).orElse(UUID.randomUUID());
		jdbc.sql("""
				INSERT INTO tarif (id, ambulant_id, produit_id, hanout_id, prix_unitaire, modifie_le_appareil)
				VALUES (:id, :a, :p, :h, :prix, :le)
				ON CONFLICT ON CONSTRAINT tarif_unique DO UPDATE SET prix_unitaire = excluded.prix_unitaire,
				  modifie_le_appareil = excluded.modifie_le_appareil
				""").param("id", id).param("a", geste.ambulantId()).param("p", t.produitId()).param("h", t.hanoutId())
			.param("prix", t.prixUnitaire()).param("le", Timestamp.from(geste.horodatage())).update();
		journal.publier(geste.ambulantId(), "TARIF", id, t);
		return Effet.ok();
	}

	/** Plafond et délai de crédit d'un hanout (§3.5). */
	@Transactional
	public Effet credit(Geste geste, CreditHanout c) {
		referentiel.hanout(c.hanoutId()).orElseThrow(() -> Probleme.regle(null, "Hanout inconnu"));
		var compte = compteId(geste.ambulantId(), geste.organisationId(), c.hanoutId());
		var modifie = jdbc.sql("SELECT modifie_le_appareil FROM compte_credit WHERE id = :id").param("id", compte)
			.query(Timestamp.class).optional();
		if (modifie.isPresent() && modifie.get() != null && modifie.get().toInstant().isAfter(geste.horodatage())) {
			return new Effet(List.of(), Conflit.versionServeur("Plafond modifié plus récemment sur un autre appareil",
					compte(compte)));
		}
		jdbc.sql("UPDATE compte_credit SET plafond = :p, delai_max_jours = :d, modifie_le_appareil = :le WHERE id = :id")
			.param("p", c.plafond()).param("d", c.delaiMaxJours()).param("le", Timestamp.from(geste.horodatage()))
			.param("id", compte).update();
		publierCompte(compte);
		return Effet.ok();
	}

	@Transactional
	public Effet parametres(Geste geste, ParametresSaisie p) {
		var modifie = jdbc.sql("SELECT modifie_le_appareil FROM parametres_ambulant WHERE ambulant_id = :a")
			.param("a", geste.ambulantId()).query(Timestamp.class).optional();
		if (modifie.isPresent() && modifie.get() != null && modifie.get().toInstant().isAfter(geste.horodatage())) {
			return new Effet(List.of(), Conflit.versionServeur("Paramètres modifiés plus récemment sur un autre appareil",
					parametres(geste.ambulantId())));
		}
		jdbc.sql("""
				INSERT INTO parametres_ambulant (ambulant_id, politique_depassement, plafond_remise_pourcent, mentions_legales,
				  modifie_le_appareil)
				VALUES (:a, coalesce(:pol, 'AVERTIR'), :remise, CAST(:mentions AS jsonb), :le)
				ON CONFLICT (ambulant_id) DO UPDATE SET
				  politique_depassement = coalesce(:pol, parametres_ambulant.politique_depassement),
				  plafond_remise_pourcent = coalesce(:remise, parametres_ambulant.plafond_remise_pourcent),
				  mentions_legales = coalesce(CAST(:mentions AS jsonb), parametres_ambulant.mentions_legales),
				  modifie_le_appareil = :le
				""").param("a", geste.ambulantId()).param("pol", p.politiqueDepassementCredit())
			.param("mentions", json.ecrire(p.mentionsLegales()))
			.param("remise", p.plafondRemisePonctuellePourcent()).param("le", Timestamp.from(geste.horodatage())).update();
		journal.publier(geste.ambulantId(), "PARAMETRES", geste.ambulantId(), parametres(geste.ambulantId()));
		return Effet.ok();
	}

	public Parametres parametres(UUID ambulantId) {
		return jdbc.sql("SELECT * FROM parametres_ambulant WHERE ambulant_id = :a").param("a", ambulantId)
			.query((rs, i) -> new Parametres(rs.getString("politique_depassement"), rs.getBigDecimal("plafond_remise_pourcent"),
					rs.getBigDecimal("seuil_ecart_pourcent"), json.lire(rs.getString("mentions_legales"), MentionsLegales.class)))
			.optional().orElse(new Parametres("AVERTIR", null, new BigDecimal("5"), null));
	}

	// ---------------------------------------------------------------- karni

	/** Compte de crédit du couple ambulant ↔ hanout, créé à la première pièce. */
	private UUID compteId(UUID ambulantId, UUID organisationId, UUID hanoutId) {
		return jdbc.sql("""
				INSERT INTO compte_credit (id, ambulant_id, organisation_id, hanout_id) VALUES (:id, :a, :org, :h)
				ON CONFLICT (ambulant_id, hanout_id) DO UPDATE SET hanout_id = excluded.hanout_id
				RETURNING id
				""").param("id", UUID.randomUUID()).param("a", ambulantId).param("org", organisationId).param("h", hanoutId)
			.query(UUID.class).single();
	}

	/** RG-C1 : solde = pièces à crédit − versements − avoirs. */
	public long solde(UUID ambulantId, UUID hanoutId) {
		return jdbc.sql("""
				SELECT coalesce((SELECT sum(montant_credit) FROM document WHERE ambulant_id = :a AND hanout_id = :h AND type <> 'AVOIR'), 0)
				     - coalesce((SELECT sum(montant) FROM versement WHERE ambulant_id = :a AND hanout_id = :h), 0)
				     - coalesce((SELECT sum(total) FROM document WHERE ambulant_id = :a AND hanout_id = :h AND type = 'AVOIR'), 0)
				""").param("a", ambulantId).param("h", hanoutId).query(Long.class).single();
	}

	private Instant plusAncienImpaye(UUID ambulantId, UUID hanoutId) {
		return jdbc.sql("SELECT min(emis_le) FROM (" + RESTES + ") r WHERE reste > 0").param("a", ambulantId).param("h", hanoutId)
			.query((rs, i) -> rs.getTimestamp(1) == null ? null : rs.getTimestamp(1).toInstant()).list().getFirst();
	}

	public Optional<Instant> derniereVente(UUID ambulantId, UUID hanoutId) {
		return Optional.ofNullable(jdbc.sql("SELECT max(emis_le) FROM document WHERE ambulant_id = :a AND hanout_id = :h AND type <> 'AVOIR'")
			.param("a", ambulantId).param("h", hanoutId)
			.query((rs, i) -> rs.getTimestamp(1) == null ? null : rs.getTimestamp(1).toInstant()).list().getFirst());
	}

	CompteCredit compte(UUID compteId) {
		return jdbc.sql("SELECT * FROM compte_credit WHERE id = :id").param("id", compteId).query(this::ligneCompte).single();
	}

	CompteCredit ligneCompte(ResultSet rs, int i) throws SQLException {
		var a = rs.getObject("ambulant_id", UUID.class);
		var h = rs.getObject("hanout_id", UUID.class);
		var plafond = (Long) rs.getObject("plafond");
		var delai = (Integer) rs.getObject("delai_max_jours");
		long solde = solde(a, h);
		var plusAncien = plusAncienImpaye(a, h);
		boolean horsPlafond = plafond != null && solde > plafond;
		boolean horsDelai = delai != null && plusAncien != null && plusAncien.isBefore(Instant.now().minus(Duration.ofDays(delai)));
		var depassement = horsPlafond && horsDelai ? "PLAFOND_ET_DELAI" : horsPlafond ? "PLAFOND" : horsDelai ? "DELAI" : "AUCUN";
		return new CompteCredit(rs.getObject("id", UUID.class), new Reference(a, comptes.nom(a)),
				new Reference(h, referentiel.hanout(h).map(Referentiel.Hanout::nom).orElse("—")), solde, plafond, delai,
				plusAncien, depassement);
	}

	private void publierCompte(UUID compteId) {
		var compte = compte(compteId);
		journal.publier(compte.ambulant().id(), "COMPTE_CREDIT", compteId, compte);
	}

	// ---------------------------------------------------------------- lectures

	public Document document(UUID id) {
		return jdbc.sql("SELECT * FROM document WHERE id = :id").param("id", id).query(this::ligneDocument).single();
	}

	Document ligneDocument(ResultSet rs, int i) throws SQLException {
		var id = rs.getObject("id", UUID.class);
		var type = rs.getString("type");
		var a = rs.getObject("ambulant_id", UUID.class);
		var h = rs.getObject("hanout_id", UUID.class);
		long total = rs.getLong("total");
		var avoirs = jdbc.sql("SELECT id, total FROM document WHERE document_origine_id = :id ORDER BY emis_le")
			.param("id", id).query((r, n) -> Map.entry(r.getObject("id", UUID.class), r.getLong("total"))).list();
		long annule = avoirs.stream().mapToLong(Map.Entry::getValue).sum();
		var statut = "AVOIR".equals(type) || annule == 0 ? "EMIS" : annule >= total ? "ANNULE" : "PARTIELLEMENT_ANNULE";
		var lignes = json.lire(rs.getString("lignes"), LIGNES);
		var mentions = json.lire(rs.getString("mentions_legales"), MentionsLegales.class);
		// Un avoir contre-passe la TVA de la facture assujettie qu'il annule (RG-C4, RG-F2), si ses lignes font son total.
		var facturee = "AVOIR".equals(type)
				? jdbc.sql("SELECT mentions_legales::text FROM document WHERE id = :o AND type = 'FACTURE'")
					.param("o", rs.getObject("document_origine_id", UUID.class)).query(String.class).optional()
					.map(m -> json.lire(m, MentionsLegales.class)).map(MentionsLegales::assujettiTva).orElse(false)
						&& lignes.stream().mapToLong(LigneDocument::montant).sum() == total
				: "FACTURE".equals(type) && mentions != null && mentions.assujettiTva();
		var reception = jdbc.sql("SELECT conforme, ecarts::text, commentaire, le FROM reception_hanout WHERE document_id = :id")
			.param("id", id)
			.query((r, n) -> new ReceptionHanout(r.getBoolean(1), json.lire(r.getString(2), ECARTS), r.getString(3),
					r.getTimestamp(4).toInstant()))
			.optional().orElse(null);
		return new Document(id, type, rs.getString("numero"), rs.getLong("sequence"), rs.getObject("appareil_id", UUID.class),
				// Nom figé à l'émission (RG-C4), même si l'ambulant retire ensuite son consentement.
				new Reference(a, rs.getString("ambulant_nom") != null ? rs.getString("ambulant_nom") : comptes.nom(a)),
				new Reference(h, referentiel.hanout(h).map(Referentiel.Hanout::nom).orElse("—")),
				rs.getTimestamp("emis_le").toInstant(), lignes, total,
				facturee ? tva(lignes) : null,
				rs.getLong("montant_regle"), rs.getLong("montant_credit"), statut, rs.getObject("document_origine_id", UUID.class),
				avoirs.isEmpty() ? null : avoirs.stream().map(Map.Entry::getKey).toList(), rs.getString("motif_avoir"),
				rs.getObject("demande_depannage_id", UUID.class), mentions,
				json.lire(rs.getString("confirmation_hanout"), ConfirmationHanout.class), reception);
	}

	static final TypeReference<List<EcartReception>> ECARTS = new TypeReference<>() {
	};

	/**
	 * RG-F2 : TVA comprise dans des prix TTC, M × t / (100 + t) arrondi au centime par ligne (demi
	 * au-dessus en valeur absolue) ; null si un taux est inconnu.
	 */
	static Long tva(List<LigneDocument> lignes) {
		long tva = 0;
		for (var l : lignes) {
			if (l.tauxTva() == null) {
				return null;
			}
			tva += BigDecimal.valueOf(l.montant()).multiply(BigDecimal.valueOf(l.tauxTva()))
				.divide(BigDecimal.valueOf(100L + l.tauxTva()), 0, java.math.RoundingMode.HALF_UP).longValueExact();
		}
		return tva;
	}

	void publier(UUID documentId) {
		var document = document(documentId);
		journal.publier(document.ambulant().id(), "DOCUMENT", documentId, document);
	}

	Versement versement(UUID id) {
		var imputations = jdbc.sql("SELECT document_id, montant FROM imputation WHERE versement_id = :id").param("id", id)
			.query((rs, i) -> new Imputation(rs.getObject("document_id", UUID.class), rs.getLong("montant"))).list();
		return jdbc.sql("SELECT * FROM versement WHERE id = :id").param("id", id)
			.query((rs, i) -> new Versement(id, rs.getObject("hanout_id", UUID.class), rs.getString("mode"), rs.getLong("montant"),
					rs.getString("cheque_numero"), rs.getString("cheque_banque"), imputations,
					rs.getObject("ambulant_id", UUID.class), rs.getTimestamp("recu_le").toInstant()))
			.single();
	}

	/** Unités promo revendues par réservation (lignes de la campagne, avoirs déduits), pour l'entonnoir. */
	public record Revendu(UUID reservationId, UUID produitId, long quantite) {
	}

	public List<Revendu> revendu(UUID campagneId) {
		return jdbc.sql("""
				SELECT (l->>'reservationId')::uuid AS r, (l->>'produitId')::uuid AS p,
				       sum(CASE WHEN d.type = 'AVOIR' THEN -1 ELSE 1 END * (l->>'quantite')::int) AS q
				FROM document d, jsonb_array_elements(d.lignes) l
				WHERE l->>'nature' = 'ARTICLE' AND l->>'campagneId' = :c
				GROUP BY 1, 2
				""").param("c", campagneId.toString())
			.query((rs, i) -> new Revendu(rs.getObject("r", UUID.class), rs.getObject("p", UUID.class), rs.getLong("q")))
			.list();
	}

	@Override
	public Set<UUID> pour(UUID hanoutId) {
		return new HashSet<>(jdbc.sql("""
				SELECT DISTINCT (l->>'produitId')::uuid FROM document d, jsonb_array_elements(d.lignes) l
				WHERE d.hanout_id = :h AND d.type <> 'AVOIR' AND l->>'nature' = 'ARTICLE'
				""").param("h", hanoutId).query(UUID.class).list());
	}

}

package ma.daba.stock;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import ma.daba.commun.Acces.Roles;
import ma.daba.commun.Appelant;
import ma.daba.commun.Appelant.Role;
import ma.daba.commun.Page;
import ma.daba.commun.Probleme;
import ma.daba.comptes.Comptes;

@RestController
@Validated
class StockController {

	record StockEmbarque(List<Stock.LigneStock> lignes, Instant calculeLe) {
	}

	private final Stock stock;
	private final JdbcClient jdbc;
	private final Comptes comptes;

	StockController(Stock stock, JdbcClient jdbc, Comptes comptes) {
		this.stock = stock;
		this.jdbc = jdbc;
		this.comptes = comptes;
	}

	@GetMapping("/v1/stock")
	@Roles(Role.AMBULANT)
	StockEmbarque lireStock(Appelant appelant) {
		return new StockEmbarque(stock.stock(appelant.utilisateurId()), Instant.now());
	}

	@GetMapping("/v1/stock/mouvements")
	@Roles(Role.AMBULANT)
	Page<Stock.Mouvement> mouvements(Appelant appelant,
			@RequestParam(required = false) @Pattern(regexp = "^(CHARGEMENT|VENTE|RETOUR|AJUSTEMENT|PROMO)$") String type,
			@RequestParam(required = false) UUID produitId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant depuis,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant jusqua,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "50") @Min(1) @Max(200) int taille) {
		var where = """
				 FROM mouvement WHERE ambulant_id = :a AND (CAST(:type AS text) IS NULL OR type = :type)
				   AND (CAST(:p AS uuid) IS NULL OR produit_id = :p)
				   AND (CAST(:depuis AS timestamptz) IS NULL OR horodatage >= :depuis)
				   AND (CAST(:jusqua AS timestamptz) IS NULL OR horodatage < :jusqua)
				""";
		var compte = jdbc.sql("SELECT count(*)" + where);
		var liste = jdbc.sql("SELECT *" + where + " ORDER BY horodatage DESC, recu_le DESC LIMIT :taille OFFSET :offset")
			.param("taille", taille).param("offset", page * taille);
		for (var s : new JdbcClient.StatementSpec[] { compte, liste }) {
			s.param("a", appelant.utilisateurId()).param("type", type).param("p", produitId)
				.param("depuis", ts(depuis)).param("jusqua", ts(jusqua));
		}
		return Page.de(page, taille, compte.query(Long.class).single(), liste.query(Stock::ligneMouvement).list());
	}

	@PostMapping("/v1/bons-sortie")
	@Roles(Role.GROSSISTE)
	ResponseEntity<Stock.BonSortie> creerBonSortie(Appelant appelant, @Valid @RequestBody Stock.BonSortieSaisie saisie) {
		var resultat = stock.creerBonSortie(appelant.organisationId(), saisie);
		return ResponseEntity.status(resultat.getValue() ? HttpStatus.CREATED : HttpStatus.OK).body(resultat.getKey());
	}

	@GetMapping("/v1/bons-sortie")
	@Roles(Role.GROSSISTE)
	Page<Stock.BonSortie> bonsSortie(Appelant appelant,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant depuis,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant jusqua,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "50") @Min(1) @Max(200) int taille) {
		var where = """
				 FROM bon_sortie WHERE grossiste_id = :g
				   AND (CAST(:depuis AS timestamptz) IS NULL OR emis_le >= :depuis)
				   AND (CAST(:jusqua AS timestamptz) IS NULL OR emis_le < :jusqua)
				""";
		var compte = jdbc.sql("SELECT count(*)" + where);
		var liste = jdbc.sql("SELECT *" + where + " ORDER BY emis_le DESC LIMIT :taille OFFSET :offset")
			.param("taille", taille).param("offset", page * taille);
		for (var s : new JdbcClient.StatementSpec[] { compte, liste }) {
			s.param("g", appelant.organisationId()).param("depuis", ts(depuis)).param("jusqua", ts(jusqua));
		}
		return Page.de(page, taille, compte.query(Long.class).single(), liste.query(stock::ligneBon).list());
	}

	record AmbulantRattache(UUID id, String nom, String telephone, int nombreBons, long quantiteTotale, int bons30Jours,
			Instant premierBonLe, Instant dernierBonLe) {
	}

	/** §4.3 : ambulants rattachés (au moins un bon de sortie reçu), avec volume et régularité. */
	@GetMapping("/v1/ambulants")
	@Roles(Role.GROSSISTE)
	Page<AmbulantRattache> ambulants(Appelant appelant, @RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "50") @Min(1) @Max(200) int taille) {
		var total = jdbc.sql("SELECT count(DISTINCT ambulant_id) FROM bon_sortie WHERE grossiste_id = :g")
			.param("g", appelant.organisationId()).query(Long.class).single();
		var contenu = jdbc.sql("""
				SELECT b.ambulant_id, count(*) AS bons, sum(x.q) AS quantite,
				  count(*) FILTER (WHERE b.emis_le > now() - interval '30 days') AS bons30, min(b.emis_le) AS premier, max(b.emis_le) AS dernier
				FROM bon_sortie b, LATERAL (SELECT sum((l->>'quantite')::int) AS q FROM jsonb_array_elements(b.lignes) l) x
				WHERE b.grossiste_id = :g
				GROUP BY b.ambulant_id ORDER BY max(b.emis_le) DESC LIMIT :taille OFFSET :offset
				""").param("g", appelant.organisationId()).param("taille", taille).param("offset", page * taille)
			.query((rs, i) -> {
				var a = rs.getObject("ambulant_id", UUID.class);
				return new AmbulantRattache(a, comptes.nom(a), comptes.telephone(a).orElse(null), rs.getInt("bons"),
						rs.getLong("quantite"), rs.getInt("bons30"), rs.getTimestamp("premier").toInstant(),
						rs.getTimestamp("dernier").toInstant());
			}).list();
		return Page.de(page, taille, total, contenu);
	}

	@GetMapping("/v1/bons-sortie/{bonSortieId}")
	@Roles(Role.GROSSISTE)
	Stock.BonSortie bonSortie(Appelant appelant, @PathVariable UUID bonSortieId) {
		return jdbc.sql("SELECT * FROM bon_sortie WHERE id = :id AND grossiste_id = :g")
			.param("id", bonSortieId).param("g", appelant.organisationId())
			.query(stock::ligneBon).optional().orElseThrow(Probleme::introuvable);
	}

	static java.sql.Timestamp ts(Instant instant) {
		return instant == null ? null : java.sql.Timestamp.from(instant);
	}

}

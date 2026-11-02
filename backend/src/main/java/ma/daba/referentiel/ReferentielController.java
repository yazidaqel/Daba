package ma.daba.referentiel;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import ma.daba.commun.Acces.Roles;
import ma.daba.commun.Appelant;
import ma.daba.commun.Appelant.Role;
import ma.daba.commun.Json;
import ma.daba.commun.Page;
import ma.daba.commun.Probleme;
import ma.daba.commun.ProduitsHabituels;
import ma.daba.referentiel.Referentiel.Grossiste;
import ma.daba.referentiel.Referentiel.Hanout;
import ma.daba.referentiel.Referentiel.Produit;

@RestController
@Validated
class ReferentielController {

	private final JdbcClient jdbc;
	private final Json json;
	private final Referentiel referentiel;
	private final ImportReferentiel imports;
	private final ObjectProvider<ProduitsHabituels> habituels;

	ReferentielController(JdbcClient jdbc, Json json, Referentiel referentiel, ImportReferentiel imports,
			ObjectProvider<ProduitsHabituels> habituels) {
		this.jdbc = jdbc;
		this.json = json;
		this.referentiel = referentiel;
		this.imports = imports;
		this.habituels = habituels;
	}

	/** CNDP : lat/lon est le centre de carte choisi par l'utilisateur ; ni stocké ni journalisé. */
	@GetMapping("/v1/hanouts")
	@Roles({ Role.AMBULANT, Role.ADMIN })
	Page<Hanout> hanouts(@RequestParam(required = false) @Size(min = 2) String q,
			@RequestParam(required = false) @DecimalMin("-90") @DecimalMax("90") Double lat,
			@RequestParam(required = false) @DecimalMin("-180") @DecimalMax("180") Double lon,
			@RequestParam(defaultValue = "2000") @Min(1) @Max(50000) int rayonM,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "50") @Min(1) @Max(200) int taille) {
		var proche = proche(lat, lon);
		var where = " FROM hanout h WHERE (CAST(:q AS text) IS NULL OR h.nom ILIKE :motif OR h.gerant ILIKE :motif OR h.telephone LIKE :motif)"
				+ (proche ? " AND ST_DWithin(h.position, " + ma.daba.commun.Position.sql("c") + ", :rayon)" : "");
		var total = parametres(jdbc.sql("SELECT count(*)" + where), q, lat, lon, rayonM).query(Long.class).single();
		var contenu = parametres(jdbc.sql(Referentiel.SELECT_HANOUT + ", "
				+ (proche ? "ST_Distance(h.position, " + ma.daba.commun.Position.sql("c") + ")" : "null") + " AS distance"
				+ where + (proche ? " ORDER BY distance" : " ORDER BY h.nom") + " LIMIT :taille OFFSET :offset"), q, lat, lon, rayonM)
			.param("taille", taille).param("offset", page * taille)
			.query(referentiel::ligneHanout).list();
		return Page.de(page, taille, total, contenu);
	}

	@GetMapping("/v1/produits")
	@Roles({ Role.AMBULANT, Role.HANOUT, Role.GROSSISTE, Role.GRAND_CLIENT, Role.ADMIN })
	Page<Produit> produits(Appelant appelant, @RequestParam(required = false) @Size(min = 2) String q,
			@RequestParam(required = false) @Pattern(regexp = "^[0-9]{8}$|^[0-9]{13}$") String ean,
			@RequestParam(required = false) String categorie,
			@RequestParam(defaultValue = "false") boolean habituels,
			@RequestParam(defaultValue = "false") boolean mesProduits,
			@RequestParam(required = false) @Size(max = 200) List<UUID> ids,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "50") @Min(1) @Max(200) int taille) {
		Set<UUID> restriction = ids == null || ids.isEmpty() ? null : Set.copyOf(ids);
		if (habituels && appelant.a(Role.HANOUT)) {
			var fournisseur = this.habituels.getIfAvailable();
			var achetes = fournisseur == null ? Set.<UUID>of() : fournisseur.pour(appelant.hanoutId());
			restriction = restriction == null ? achetes
					: restriction.stream().filter(achetes::contains).collect(java.util.stream.Collectors.toSet());
		}
		if (restriction != null && restriction.isEmpty()) {
			return Page.de(page, taille, 0, List.of());
		}
		var where = """
				 WHERE (CAST(:q AS text) IS NULL OR p.marque ILIKE :motif
				        OR EXISTS (SELECT 1 FROM jsonb_each_text(p.libelle) l WHERE l.value ILIKE :motif))
				   AND (CAST(:ean AS text) IS NULL OR p.ean = :ean)
				   AND (CAST(:categorie AS text) IS NULL OR p.categorie = :categorie)
				   AND (CAST(:gc AS uuid) IS NULL OR p.grand_client_id = :gc)
				""" + (restriction == null ? "" : " AND p.id IN (:ids)");
		var compte = jdbc.sql("SELECT count(*) FROM produit p" + where);
		var liste = jdbc.sql(Referentiel.SELECT_PRODUIT + where + " ORDER BY p.marque, p.ean LIMIT :taille OFFSET :offset")
			.param("taille", taille).param("offset", page * taille);
		for (var s : new JdbcClient.StatementSpec[] { compte, liste }) {
			s.param("q", q).param("motif", "%" + q + "%").param("ean", ean).param("categorie", categorie)
				.param("gc", mesProduits && appelant.a(Role.GRAND_CLIENT) ? appelant.organisationId() : null);
			if (restriction != null) {
				s.param("ids", restriction);
			}
		}
		return Page.de(page, taille, compte.query(Long.class).single(), liste.query(referentiel::ligneProduit).list());
	}

	@GetMapping("/v1/produits/{produitId}")
	@Roles({ Role.AMBULANT, Role.HANOUT, Role.GROSSISTE, Role.GRAND_CLIENT, Role.ADMIN })
	Produit produit(@PathVariable UUID produitId) {
		return referentiel.produit(produitId).orElseThrow(Probleme::introuvable);
	}

	@GetMapping("/v1/categories")
	@Roles({ Role.AMBULANT, Role.HANOUT, Role.GROSSISTE, Role.GRAND_CLIENT, Role.ADMIN })
	List<Referentiel.Categorie> categories() {
		return jdbc.sql("SELECT code, libelle FROM categorie ORDER BY code")
			.query((rs, i) -> new Referentiel.Categorie(rs.getString(1), json.lire(rs.getString(2), Referentiel.LIBELLE))).list();
	}

	record Ville(String code, Map<String, String> nom) {
	}

	record Region(String code, Map<String, String> nom, List<Ville> villes) {
	}

	/** Les 12 régions et leurs principales villes (ciblage d'une campagne, §5.1). */
	@GetMapping("/v1/regions")
	@Roles({ Role.AMBULANT, Role.HANOUT, Role.GROSSISTE, Role.GRAND_CLIENT, Role.ADMIN })
	List<Region> regions() {
		var villes = jdbc.sql("SELECT region_code, code, nom FROM ville ORDER BY code")
			.query((rs, i) -> Map.entry(rs.getString(1), new Ville(rs.getString(2), json.lire(rs.getString(3), Referentiel.LIBELLE))))
			.list().stream()
			.collect(java.util.stream.Collectors.groupingBy(Map.Entry::getKey,
					java.util.stream.Collectors.mapping(Map.Entry::getValue, java.util.stream.Collectors.toList())));
		return jdbc.sql("SELECT code, nom FROM region ORDER BY code")
			.query((rs, i) -> new Region(rs.getString(1), json.lire(rs.getString(2), Referentiel.LIBELLE),
					villes.getOrDefault(rs.getString(1), List.of())))
			.list();
	}

	@GetMapping("/v1/grossistes")
	@Roles({ Role.GRAND_CLIENT, Role.ADMIN })
	Page<Grossiste> grossistes(@RequestParam(required = false) @Size(min = 2) String q,
			@RequestParam(required = false) String ville,
			@RequestParam(required = false) @DecimalMin("-90") @DecimalMax("90") Double lat,
			@RequestParam(required = false) @DecimalMin("-180") @DecimalMax("180") Double lon,
			@RequestParam(defaultValue = "2000") @Min(1) @Max(50000) int rayonM,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "50") @Min(1) @Max(200) int taille) {
		var proche = proche(lat, lon);
		var where = " FROM grossiste g WHERE (CAST(:q AS text) IS NULL OR g.nom ILIKE :motif)"
				+ " AND (CAST(:ville AS text) IS NULL OR g.ville ILIKE :ville)"
				+ (proche ? " AND ST_DWithin(g.position, " + ma.daba.commun.Position.sql("c") + ", :rayon)" : "");
		var total = parametres(jdbc.sql("SELECT count(*)" + where), q, lat, lon, rayonM).param("ville", ville)
			.query(Long.class).single();
		var contenu = parametres(jdbc.sql(Referentiel.SELECT_GROSSISTE + ", "
				+ (proche ? "ST_Distance(g.position, " + ma.daba.commun.Position.sql("c") + ")" : "null") + " AS distance"
				+ where + (proche ? " ORDER BY distance" : " ORDER BY g.nom") + " LIMIT :taille OFFSET :offset"), q, lat, lon, rayonM)
			.param("ville", ville).param("taille", taille).param("offset", page * taille)
			.query(referentiel::ligneGrossiste).list();
		return Page.de(page, taille, total, contenu);
	}

	@PostMapping(path = "/v1/admin/hanouts/import", consumes = "multipart/form-data")
	@Roles(Role.ADMIN)
	ImportReferentiel.Bilan importerHanouts(@RequestPart MultipartFile fichier) {
		return imports.hanouts(fichier);
	}

	@PostMapping(path = "/v1/admin/produits/import", consumes = "multipart/form-data")
	@Roles(Role.ADMIN)
	ImportReferentiel.Bilan importerProduits(@RequestPart MultipartFile fichier) {
		return imports.produits(fichier);
	}

	private static boolean proche(Double lat, Double lon) {
		if ((lat == null) != (lon == null)) {
			throw Probleme.requete("lat et lon vont ensemble");
		}
		return lat != null;
	}

	private static JdbcClient.StatementSpec parametres(JdbcClient.StatementSpec s, String q, Double lat, Double lon, int rayon) {
		return s.param("q", q).param("motif", "%" + q + "%").param("cLat", lat).param("cLon", lon).param("rayon", rayon);
	}

}

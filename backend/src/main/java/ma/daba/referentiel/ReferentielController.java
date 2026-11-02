package ma.daba.referentiel;

import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
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
	private final Referentiel referentiel;
	private final ImportReferentiel imports;
	private final ObjectProvider<ProduitsHabituels> habituels;

	ReferentielController(JdbcClient jdbc, Referentiel referentiel, ImportReferentiel imports,
			ObjectProvider<ProduitsHabituels> habituels) {
		this.jdbc = jdbc;
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
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "50") @Min(1) @Max(200) int taille) {
		Set<UUID> restriction = null;
		if (habituels && appelant.a(Role.HANOUT)) {
			var fournisseur = this.habituels.getIfAvailable();
			restriction = fournisseur == null ? Set.of() : fournisseur.pour(appelant.hanoutId());
			if (restriction.isEmpty()) {
				return Page.de(page, taille, 0, java.util.List.of());
			}
		}
		var where = """
				 WHERE (CAST(:q AS text) IS NULL OR p.marque ILIKE :motif
				        OR EXISTS (SELECT 1 FROM jsonb_each_text(p.libelle) l WHERE l.value ILIKE :motif))
				   AND (CAST(:ean AS text) IS NULL OR p.ean = :ean)
				   AND (CAST(:categorie AS text) IS NULL OR p.categorie = :categorie)
				""" + (restriction == null ? "" : " AND p.id IN (:ids)");
		var compte = jdbc.sql("SELECT count(*) FROM produit p" + where);
		var liste = jdbc.sql(Referentiel.SELECT_PRODUIT + where + " ORDER BY p.marque, p.ean LIMIT :taille OFFSET :offset")
			.param("taille", taille).param("offset", page * taille);
		for (var s : new JdbcClient.StatementSpec[] { compte, liste }) {
			s.param("q", q).param("motif", "%" + q + "%").param("ean", ean).param("categorie", categorie);
			if (restriction != null) {
				s.param("ids", restriction);
			}
		}
		return Page.de(page, taille, compte.query(Long.class).single(), liste.query(referentiel::ligneProduit).list());
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

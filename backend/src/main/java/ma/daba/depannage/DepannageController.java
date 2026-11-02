package ma.daba.depannage;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import ma.daba.commun.Acces.Roles;
import ma.daba.commun.Appelant;
import ma.daba.commun.Appelant.Role;
import ma.daba.commun.Page;
import ma.daba.commun.Position;
import ma.daba.commun.Probleme;
import ma.daba.referentiel.Referentiel;

@RestController
@Validated
class DepannageController {

	record Cellule(String geohash, Position centre, UUID produitId, int signalements, int resolues, int enCours,
			Integer delaiMoyenResolutionMinutes) {
	}

	record CarteRuptures(int seuilAnonymat, Instant genereLe, List<Cellule> cellules) {
	}

	private final Depannage depannage;
	private final JdbcClient jdbc;
	private final Referentiel referentiel;
	private final int seuilAnonymat;

	DepannageController(Depannage depannage, JdbcClient jdbc, Referentiel referentiel,
			@Value("${daba.analytique.seuil-anonymat}") int seuilAnonymat) {
		this.depannage = depannage;
		this.jdbc = jdbc;
		this.referentiel = referentiel;
		this.seuilAnonymat = seuilAnonymat;
	}

	@GetMapping("/v1/ambulant/disponibilite")
	@Roles(Role.AMBULANT)
	Depannage.Disponibilite disponibilite(Appelant appelant) {
		return depannage.disponibilite(appelant.utilisateurId());
	}

	@PutMapping("/v1/ambulant/disponibilite")
	@Roles(Role.AMBULANT)
	Depannage.Disponibilite changerDisponibilite(Appelant appelant, @Valid @RequestBody Depannage.DisponibiliteSaisie saisie) {
		return depannage.changerDisponibilite(appelant.utilisateurId(), saisie);
	}

	@PostMapping(path = "/v1/demandes-depannage", consumes = MediaType.APPLICATION_JSON_VALUE)
	@Roles(Role.HANOUT)
	ResponseEntity<Depannage.DemandeDepannage> signaler(Appelant appelant, @Valid @RequestBody Depannage.RuptureSaisie saisie) {
		if (saisie.produitId() == null) {
			throw Probleme.requete("produitId requis (ou photo en multipart)");
		}
		return reponse(depannage.signaler(appelant, saisie, null));
	}

	@PostMapping(path = "/v1/demandes-depannage", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@Roles(Role.HANOUT)
	ResponseEntity<Depannage.DemandeDepannage> signalerEnPhoto(Appelant appelant,
			@Valid @RequestPart("rupture") Depannage.RuptureSaisie saisie, @RequestPart("photo") MultipartFile photo) {
		return reponse(depannage.signaler(appelant, saisie, photo));
	}

	private static ResponseEntity<Depannage.DemandeDepannage> reponse(java.util.Map.Entry<Depannage.DemandeDepannage, Boolean> r) {
		return ResponseEntity.status(r.getValue() ? HttpStatus.CREATED : HttpStatus.OK).body(r.getKey());
	}

	@GetMapping("/v1/demandes-depannage")
	@Roles({ Role.HANOUT, Role.AMBULANT })
	Page<Depannage.DemandeDepannage> demandes(Appelant appelant,
			@RequestParam(required = false) @Pattern(regexp = "^(EN_ATTENTE|ACCEPTEE|EN_ROUTE|LIVREE|SANS_REPONSE)$") String statut,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "50") @Min(1) @Max(200) int taille) {
		return depannage.demandes(appelant, statut, page, taille);
	}

	@GetMapping("/v1/demandes-depannage/{demandeId}")
	@Roles({ Role.HANOUT, Role.AMBULANT })
	Depannage.DemandeDepannage demande(Appelant appelant, @PathVariable UUID demandeId) {
		return depannage.demande(demandeId, appelant);
	}

	@PostMapping("/v1/demandes-depannage/{demandeId}/acceptation")
	@Roles(Role.AMBULANT)
	Depannage.DemandeDepannage accepter(Appelant appelant, @PathVariable UUID demandeId) {
		return depannage.accepter(appelant, demandeId);
	}

	@PostMapping("/v1/demandes-depannage/{demandeId}/refus")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Roles(Role.AMBULANT)
	void refuser(Appelant appelant, @PathVariable UUID demandeId) {
		depannage.refuser(appelant, demandeId);
	}

	@PostMapping("/v1/demandes-depannage/{demandeId}/en-route")
	@Roles(Role.AMBULANT)
	Depannage.DemandeDepannage enRoute(Appelant appelant, @PathVariable UUID demandeId) {
		return depannage.enRoute(appelant, demandeId);
	}

	/**
	 * Carte des ruptures (§5.3, §5.4) : agrégée par cellule geohash sur les produits du grand client,
	 * sans identifiant de hanout ni d'ambulant ; une cellule de moins de {@code seuilAnonymat} hanouts
	 * distincts n'est pas renvoyée.
	 */
	@GetMapping("/v1/analytique/ruptures")
	@Roles(Role.GRAND_CLIENT)
	CarteRuptures ruptures(Appelant appelant, @RequestParam(required = false) UUID produitId,
			@RequestParam(required = false) String ville, @RequestParam(defaultValue = "6") @Min(5) @Max(6) int precision,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant depuis,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant jusqua) {
		var produits = produitId != null ? List.of(produitId) : referentiel.produitsDuGrandClient(appelant.organisationId());
		if (produitId != null && !referentiel.produitDuGrandClient(produitId, appelant.organisationId())) {
			throw Probleme.introuvable();
		}
		if (produits.isEmpty()) {
			return new CarteRuptures(seuilAnonymat, Instant.now(), List.of());
		}
		var cellules = jdbc.sql("""
				SELECT ST_GeoHash(position::geometry, :precision) AS geohash, produit_id,
				  count(*) AS signalements,
				  count(*) FILTER (WHERE statut = 'LIVREE') AS resolues,
				  count(*) FILTER (WHERE statut IN ('EN_ATTENTE', 'ACCEPTEE', 'EN_ROUTE')) AS en_cours,
				  round(avg(extract(epoch FROM livree_le - cree_le) / 60) FILTER (WHERE statut = 'LIVREE')) AS delai
				FROM demande
				WHERE produit_id IN (:produits)
				  AND (CAST(:ville AS text) IS NULL OR ville ILIKE :ville)
				  AND (CAST(:depuis AS timestamptz) IS NULL OR cree_le >= :depuis)
				  AND (CAST(:jusqua AS timestamptz) IS NULL OR cree_le < :jusqua)
				GROUP BY 1, 2
				HAVING count(DISTINCT hanout_id) >= :seuil
				ORDER BY 1
				""").param("precision", precision).param("produits", produits).param("ville", ville)
			.param("depuis", depuis == null ? null : java.sql.Timestamp.from(depuis))
			.param("jusqua", jusqua == null ? null : java.sql.Timestamp.from(jusqua)).param("seuil", seuilAnonymat)
			.query((rs, i) -> {
				var geohash = rs.getString("geohash");
				var delai = rs.getObject("delai");
				return new Cellule(geohash, centre(geohash), rs.getObject("produit_id", UUID.class), rs.getInt("signalements"),
						rs.getInt("resolues"), rs.getInt("en_cours"), delai == null ? null : ((Number) delai).intValue());
			}).list();
		return new CarteRuptures(seuilAnonymat, Instant.now(), cellules);
	}

	private Position centre(String geohash) {
		return jdbc.sql("SELECT ST_Y(p) AS lat, ST_X(p) AS lon FROM ST_PointFromGeoHash(:g) p").param("g", geohash)
			.query((rs, i) -> new Position(rs.getDouble("lat"), rs.getDouble("lon"))).single();
	}

}

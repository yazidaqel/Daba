package ma.daba.comptes;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import ma.daba.commun.Acces.Roles;
import ma.daba.commun.Appelant;
import ma.daba.commun.Appelant.Role;
import ma.daba.commun.Audit;
import ma.daba.commun.Page;
import ma.daba.commun.Probleme;
import ma.daba.commun.Reference;
import ma.daba.referentiel.Referentiel;

/**
 * Back-office de l'administrateur plateforme (§2 : comptes, modération, support ; §10 : déconnexion
 * à distance, journal d'audit). ADMIN seulement : ces réponses portent des données personnelles
 * (CNDP), jamais de code OTP, de jeton ni de session.
 */
@RestController
class AdministrationController {

	record OrganisationRef(UUID id, String type, String nom) {
	}

	/** Autre compte qui s'est connecté sur le même appareil (téléphone partagé). */
	record CompteAppareil(UUID id, String telephone, String nom) {
	}

	record AppareilAdmin(UUID appareilId, String plateforme, String langue, String prefixeNumerotation, Instant creeLe,
			Instant derniereConnexion, Instant vuLe, boolean compteActuel, boolean sessionOuverte, boolean pushActif,
			boolean revoque, Instant revoqueLe, List<CompteAppareil> autresComptes) {
	}

	record UtilisateurAdmin(UUID id, String telephone, String nom, List<String> roles, OrganisationRef organisation,
			UUID hanoutId, boolean actif, Instant desactiveLe, Instant creeLe, ComptesController.Consentement consentement,
			List<AppareilAdmin> appareils) {
	}

	record OrganisationAdmin(UUID id, String type, String nom, Instant creeLe, long nombreUtilisateurs,
			Referentiel.Grossiste grossiste) {
	}

	record OrganisationDetail(UUID id, String type, String nom, Instant creeLe, long nombreUtilisateurs,
			Referentiel.Grossiste grossiste, List<UtilisateurAdmin> utilisateurs) {
	}

	record EntreeAudit(long id, Instant le, Reference auteur, UUID appareilId, String operation, Object details) {
	}

	private static final String SELECT_UTILISATEUR = """
			SELECT u.id, u.telephone, u.nom, u.role, u.hanout_id, u.desactive_le, u.cree_le,
			       o.id AS org_id, o.type AS org_type, o.nom AS org_nom,
			       c.version_texte, c.traitement_donnees, c.geolocalisation, c.langue, c.enregistre_le
			""";

	private static final String FROM_UTILISATEUR = """
			 FROM utilisateur u JOIN organisation o ON o.id = u.organisation_id
			LEFT JOIN LATERAL (SELECT * FROM consentement WHERE utilisateur_id = u.id ORDER BY id DESC LIMIT 1) c ON true
			""";

	private static final String SELECT_ORGANISATION = """
			SELECT o.id, o.type, o.nom, o.cree_le, (SELECT count(*) FROM utilisateur u WHERE u.organisation_id = o.id) AS n
			FROM organisation o
			""";

	private final JdbcClient jdbc;
	private final Comptes comptes;
	private final Referentiel referentiel;
	private final Audit audit;
	private final ApplicationEventPublisher evenements;

	AdministrationController(JdbcClient jdbc, Comptes comptes, Referentiel referentiel, Audit audit,
			ApplicationEventPublisher evenements) {
		this.jdbc = jdbc;
		this.comptes = comptes;
		this.referentiel = referentiel;
		this.audit = audit;
		this.evenements = evenements;
	}

	// ---------------------------------------------------------------- organisations

	@GetMapping("/v1/admin/organisations")
	@Roles(Role.ADMIN)
	Page<OrganisationAdmin> organisations(@RequestParam(required = false) @Pattern(regexp = "^(GROSSISTE|GRAND_CLIENT)$") String type,
			@RequestParam(required = false) @Size(min = 2) String q,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "50") @Min(1) @Max(200) int taille) {
		var where = """
				 WHERE o.type IN ('GROSSISTE', 'GRAND_CLIENT') AND (CAST(:type AS text) IS NULL OR o.type = :type)
				   AND (CAST(:q AS text) IS NULL OR o.nom ILIKE :motif)
				""";
		var compte = jdbc.sql("SELECT count(*) FROM organisation o" + where);
		var liste = jdbc.sql(SELECT_ORGANISATION + where + " ORDER BY o.nom, o.id LIMIT :taille OFFSET :offset")
			.param("taille", taille).param("offset", page * taille);
		for (var s : new JdbcClient.StatementSpec[] { compte, liste }) {
			s.param("type", type).param("q", q).param("motif", "%" + q + "%");
		}
		return Page.de(page, taille, compte.query(Long.class).single(), liste.query(this::ligneOrganisation).list());
	}

	@GetMapping("/v1/admin/organisations/{organisationId}")
	@Roles(Role.ADMIN)
	OrganisationDetail organisation(@PathVariable UUID organisationId) {
		var o = jdbc.sql(SELECT_ORGANISATION + " WHERE o.id = :id").param("id", organisationId).query(this::ligneOrganisation)
			.optional().orElseThrow(Probleme::introuvable);
		var utilisateurs = utilisateurs(jdbc.sql(SELECT_UTILISATEUR + FROM_UTILISATEUR
				+ " WHERE u.organisation_id = :o ORDER BY u.cree_le, u.id").param("o", organisationId));
		return new OrganisationDetail(o.id(), o.type(), o.nom(), o.creeLe(), o.nombreUtilisateurs(), o.grossiste(), utilisateurs);
	}

	@PostMapping("/v1/admin/organisations/{organisationId}/utilisateurs")
	@ResponseStatus(HttpStatus.CREATED)
	@Roles(Role.ADMIN)
	@Transactional
	UtilisateurAdmin ajouterUtilisateur(Appelant admin, @PathVariable UUID organisationId,
			@Valid @RequestBody ComptesController.Membre membre) {
		var type = jdbc.sql("SELECT type FROM organisation WHERE id = :o").param("o", organisationId).query(String.class)
			.optional().orElseThrow(Probleme::introuvable);
		if (!"GROSSISTE".equals(type) && !"GRAND_CLIENT".equals(type)) {
			throw Probleme.conflit("Seuls un grossiste ou un grand client reçoivent de nouveaux utilisateurs");
		}
		var id = comptes.inscrireMembre(organisationId, type, membre.telephone(), membre.nom());
		audit.consigner(admin, "UTILISATEUR_AJOUTE", Map.of("utilisateurId", id, "organisationId", organisationId, "role", type));
		return utilisateur(id);
	}

	// ---------------------------------------------------------------- utilisateurs et appareils

	@GetMapping("/v1/admin/utilisateurs")
	@Roles(Role.ADMIN)
	Page<UtilisateurAdmin> utilisateurs(@RequestParam(required = false) @Size(min = 2) String q,
			@RequestParam(required = false) Role role,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "50") @Min(1) @Max(200) int taille) {
		var chiffres = q == null ? "" : q.replaceAll("\\D", "").replaceFirst("^(00212|212|0)", "");
		var where = """
				 WHERE (CAST(:role AS text) IS NULL OR u.role = :role)
				   AND (CAST(:q AS text) IS NULL OR u.nom ILIKE :motif OR o.nom ILIKE :motif
				        OR (CAST(:chiffres AS text) IS NOT NULL AND u.telephone LIKE :motifTel))
				""";
		var compte = jdbc.sql("SELECT count(*)" + FROM_UTILISATEUR + where);
		var liste = jdbc.sql(SELECT_UTILISATEUR + FROM_UTILISATEUR + where
				+ " ORDER BY u.cree_le DESC, u.id LIMIT :taille OFFSET :offset").param("taille", taille).param("offset", page * taille);
		for (var s : new JdbcClient.StatementSpec[] { compte, liste }) {
			s.param("role", role == null ? null : role.name()).param("q", q).param("motif", "%" + q + "%")
				.param("chiffres", chiffres.length() < 2 ? null : chiffres).param("motifTel", "%" + chiffres + "%");
		}
		return Page.de(page, taille, compte.query(Long.class).single(), utilisateurs(liste));
	}

	/** Modération : le compte ne se connecte plus, ses sessions se ferment ; rien n'est effacé. */
	@PostMapping("/v1/admin/utilisateurs/{utilisateurId}/desactivation")
	@Roles(Role.ADMIN)
	@Transactional
	UtilisateurAdmin desactiver(Appelant admin, @PathVariable UUID utilisateurId) {
		var role = role(utilisateurId);
		if (utilisateurId.equals(admin.utilisateurId())) {
			throw Probleme.conflit("Un administrateur ne peut pas désactiver son propre compte");
		}
		if (jdbc.sql("UPDATE utilisateur SET desactive_le = now() WHERE id = :u AND desactive_le IS NULL")
			.param("u", utilisateurId).update() > 0) {
			jdbc.sql("UPDATE appareil SET session = NULL, jeton_push = NULL WHERE utilisateur_id = :u")
				.param("u", utilisateurId).update();
			audit.consigner(admin, "UTILISATEUR_DESACTIVE", Map.of("utilisateurId", utilisateurId, "role", role));
			retirerAcces(utilisateurId, role);
		}
		return utilisateur(utilisateurId);
	}

	@PostMapping("/v1/admin/utilisateurs/{utilisateurId}/reactivation")
	@Roles(Role.ADMIN)
	@Transactional
	UtilisateurAdmin reactiver(Appelant admin, @PathVariable UUID utilisateurId) {
		var role = role(utilisateurId);
		if (jdbc.sql("UPDATE utilisateur SET desactive_le = NULL WHERE id = :u AND desactive_le IS NOT NULL")
			.param("u", utilisateurId).update() > 0) {
			audit.consigner(admin, "UTILISATEUR_REACTIVE", Map.of("utilisateurId", utilisateurId, "role", role));
		}
		return utilisateur(utilisateurId);
	}

	/** §10 : déconnexion à distance d'un appareil perdu ; définitive pour cet appareilId. */
	@PostMapping("/v1/admin/appareils/{appareilId}/revocation")
	@Roles(Role.ADMIN)
	@Transactional
	UtilisateurAdmin revoquer(Appelant admin, @PathVariable UUID appareilId) {
		var utilisateurId = jdbc.sql("SELECT utilisateur_id FROM appareil WHERE id = :a FOR UPDATE").param("a", appareilId)
			.query(UUID.class).optional().orElseThrow(Probleme::introuvable);
		if (jdbc.sql("""
				UPDATE appareil SET revoque = true, revoque_le = now(), session = NULL, jeton_push = NULL
				WHERE id = :a AND NOT revoque
				""").param("a", appareilId).update() > 0) {
			// Un téléphone perdu s'efface pour tous les comptes qui l'ont utilisé ; seul le compte courant y avait une session.
			var comptesAppareil = jdbc.sql("SELECT utilisateur_id FROM appareil_utilisateur WHERE appareil_id = :a ORDER BY premiere_connexion")
				.param("a", appareilId).query(UUID.class).list();
			audit.consigner(admin, "APPAREIL_REVOQUE", Map.of("appareilId", appareilId, "utilisateurId", utilisateurId,
					"utilisateurIds", comptesAppareil));
			retirerAcces(utilisateurId, role(utilisateurId));
		}
		return utilisateur(utilisateurId);
	}

	// ---------------------------------------------------------------- journal d'audit

	@GetMapping("/v1/admin/audit")
	@Roles(Role.ADMIN)
	Page<EntreeAudit> audit(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant depuis,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant jusqua,
			@RequestParam(required = false) UUID auteurId,
			@RequestParam(required = false) @Pattern(regexp = "^[A-Z_]{1,64}$") String operation,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "50") @Min(1) @Max(200) int taille) {
		var entrees = audit.lister(depuis, jusqua, auteurId, operation, page, taille);
		var auteurs = entrees.contenu().stream().map(Audit.Entree::utilisateurId).filter(java.util.Objects::nonNull)
			.collect(Collectors.toSet());
		var noms = new HashMap<UUID, String>();
		if (!auteurs.isEmpty()) {
			jdbc.sql("SELECT id, coalesce(nom, telephone) FROM utilisateur WHERE id IN (:ids)").param("ids", auteurs)
				.query(rs -> {
					noms.put(rs.getObject(1, UUID.class), rs.getString(2));
				});
		}
		return Page.de(entrees.page(), entrees.taille(), entrees.total(), entrees.contenu().stream()
			.map(e -> new EntreeAudit(e.id(), e.le(),
					e.utilisateurId() == null ? null : new Reference(e.utilisateurId(), noms.getOrDefault(e.utilisateurId(), "—")),
					e.appareilId(), e.operation(), e.details()))
			.toList());
	}

	// ---------------------------------------------------------------- lectures

	private String role(UUID utilisateurId) {
		return jdbc.sql("SELECT role FROM utilisateur WHERE id = :u").param("u", utilisateurId).query(String.class)
			.optional().orElseThrow(Probleme::introuvable);
	}

	private void retirerAcces(UUID utilisateurId, String role) {
		if ("AMBULANT".equals(role)) {
			evenements.publishEvent(new Comptes.AccesRetire(utilisateurId));
		}
	}

	private UtilisateurAdmin utilisateur(UUID id) {
		return utilisateurs(jdbc.sql(SELECT_UTILISATEUR + FROM_UTILISATEUR + " WHERE u.id = :id").param("id", id)).getFirst();
	}

	/** Utilisateurs de la requête, avec leurs appareils (une seule requête de plus pour toute la page). */
	private List<UtilisateurAdmin> utilisateurs(JdbcClient.StatementSpec requete) {
		var utilisateurs = requete.query((rs, i) -> utilisateurSansAppareils(rs)).list();
		if (utilisateurs.isEmpty()) {
			return utilisateurs;
		}
		var ids = utilisateurs.stream().map(UtilisateurAdmin::id).toList();
		// Tous les comptes de chaque appareil de la page, pour dire qui d'autre l'a utilisé.
		var comptesParAppareil = new HashMap<UUID, List<CompteAppareil>>();
		jdbc.sql("""
				SELECT au.appareil_id, u.id, u.telephone, u.nom
				FROM appareil_utilisateur au JOIN utilisateur u ON u.id = au.utilisateur_id
				WHERE au.appareil_id IN (SELECT appareil_id FROM appareil_utilisateur WHERE utilisateur_id IN (:u))
				ORDER BY au.premiere_connexion, u.id
				""").param("u", ids)
			.query(rs -> {
				comptesParAppareil.computeIfAbsent(rs.getObject("appareil_id", UUID.class), k -> new java.util.ArrayList<>())
					.add(new CompteAppareil(rs.getObject("id", UUID.class), rs.getString("telephone"), rs.getString("nom")));
			});
		var appareils = new HashMap<UUID, List<AppareilAdmin>>();
		jdbc.sql("""
				SELECT au.utilisateur_id, a.id, a.plateforme, a.langue, p.prefixe, au.premiere_connexion, au.derniere_connexion,
				       au.vu_le, a.utilisateur_id = au.utilisateur_id AS actuel,
				       a.utilisateur_id = au.utilisateur_id AND a.session IS NOT NULL AS ouverte,
				       a.utilisateur_id = au.utilisateur_id AND a.session IS NOT NULL AND a.jeton_push IS NOT NULL AS push,
				       a.revoque, a.revoque_le
				FROM appareil_utilisateur au JOIN appareil a ON a.id = au.appareil_id
				LEFT JOIN prefixe_numerotation p ON p.appareil_id = au.appareil_id AND p.utilisateur_id = au.utilisateur_id
				WHERE au.utilisateur_id IN (:u)
				ORDER BY coalesce(au.vu_le, au.derniere_connexion, au.premiere_connexion) DESC, a.id
				""").param("u", ids)
			.query(rs -> {
				var utilisateurId = rs.getObject("utilisateur_id", UUID.class);
				var appareilId = rs.getObject("id", UUID.class);
				var autres = comptesParAppareil.getOrDefault(appareilId, List.of()).stream()
					.filter(c -> !c.id().equals(utilisateurId)).toList();
				appareils.computeIfAbsent(utilisateurId, k -> new java.util.ArrayList<>())
					.add(new AppareilAdmin(appareilId, rs.getString("plateforme"), rs.getString("langue"), rs.getString("prefixe"),
							instant(rs, "premiere_connexion"), instant(rs, "derniere_connexion"), instant(rs, "vu_le"),
							rs.getBoolean("actuel"), rs.getBoolean("ouverte"), rs.getBoolean("push"), rs.getBoolean("revoque"),
							instant(rs, "revoque_le"), autres));
			});
		return utilisateurs.stream()
			.map(u -> new UtilisateurAdmin(u.id(), u.telephone(), u.nom(), u.roles(), u.organisation(), u.hanoutId(), u.actif(),
					u.desactiveLe(), u.creeLe(), u.consentement(), appareils.getOrDefault(u.id(), List.of())))
			.toList();
	}

	private static UtilisateurAdmin utilisateurSansAppareils(ResultSet rs) throws SQLException {
		var consentement = rs.getTimestamp("enregistre_le") == null ? null
				: new ComptesController.Consentement(rs.getString("version_texte"), rs.getBoolean("traitement_donnees"),
						(Boolean) rs.getObject("geolocalisation"), rs.getString("langue"), instant(rs, "enregistre_le"));
		return new UtilisateurAdmin(rs.getObject("id", UUID.class), rs.getString("telephone"), rs.getString("nom"),
				List.of(rs.getString("role")),
				new OrganisationRef(rs.getObject("org_id", UUID.class), rs.getString("org_type"), rs.getString("org_nom")),
				rs.getObject("hanout_id", UUID.class), rs.getTimestamp("desactive_le") == null, instant(rs, "desactive_le"),
				instant(rs, "cree_le"), consentement, List.of());
	}

	private OrganisationAdmin ligneOrganisation(ResultSet rs, int i) throws SQLException {
		var id = rs.getObject("id", UUID.class);
		var type = rs.getString("type");
		// ponytail: une lecture de fiche par grossiste de la page (200 au plus) ; lecture groupée si le back-office rame.
		return new OrganisationAdmin(id, type, rs.getString("nom"), instant(rs, "cree_le"), rs.getLong("n"),
				"GROSSISTE".equals(type) ? referentiel.grossiste(id).orElse(null) : null);
	}

	private static Instant instant(ResultSet rs, String colonne) throws SQLException {
		Timestamp t = rs.getTimestamp(colonne);
		return t == null ? null : t.toInstant();
	}

}

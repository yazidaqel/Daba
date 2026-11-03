package ma.daba.comptes;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import ma.daba.commun.Acces;
import ma.daba.commun.Acces.Public;
import ma.daba.commun.Acces.Roles;
import ma.daba.commun.Acces.SansConsentement;
import ma.daba.commun.Appelant;
import ma.daba.commun.Audit;
import ma.daba.commun.Appelant.Role;
import ma.daba.commun.Jetons;
import ma.daba.commun.Position;
import ma.daba.commun.Probleme;
import ma.daba.referentiel.Referentiel;

@RestController
class ComptesController {

	static final String TELEPHONE = "^\\+212[5-7][0-9]{8}$";
	static final String LANGUE = "^(fr|ar|ary-Arab|ary-Latn)$";

	record OtpRequest(@Pattern(regexp = TELEPHONE) String telephone, String lienJeton,
			@Pattern(regexp = LANGUE) String langue) {
	}

	record OtpSent(long expireDans) {
	}

	record OtpVerify(@Pattern(regexp = TELEPHONE) String telephone, String lienJeton,
			@NotNull @Pattern(regexp = "^[0-9]{6}$") String code, @NotNull UUID appareilId) {
	}

	record Rafraichissement(@NotBlank String refreshToken) {
	}

	record LienCible(String cible, UUID cibleId, String telephoneMasque, Instant expireLe) {
	}

	record JetonPush(@NotBlank String jeton, @NotNull @Pattern(regexp = "^(ANDROID|IOS)$") String plateforme,
			@Pattern(regexp = LANGUE) String langue) {
	}

	record ConsentementSaisie(@NotBlank String versionTexte, @NotNull Boolean traitementDonnees, Boolean geolocalisation,
			@Pattern(regexp = LANGUE) String langue) {
	}

	record Consentement(String versionTexte, boolean traitementDonnees, Boolean geolocalisation, String langue,
			Instant enregistreLe) {
	}

	record Membre(@NotNull @Pattern(regexp = TELEPHONE) String telephone, String nom) {
	}

	record OrganisationSaisie(@NotNull @Pattern(regexp = "^(GROSSISTE|GRAND_CLIENT)$") String type, @NotBlank String nom,
			@NotEmpty List<@Valid Membre> utilisateurs, @Valid Position position, String adresse, String ville) {
	}

	record OrganisationCreee(UUID id, String type, String nom, List<Map<String, Object>> utilisateurs) {
	}

	private final JdbcClient jdbc;
	private final Comptes comptes;
	private final Authentification authentification;
	private final Jetons jetons;
	private final Referentiel referentiel;
	private final ApplicationEventPublisher evenements;
	private final List<Acces.ContributeurExport> contributeurs;
	private final Audit audit;

	ComptesController(JdbcClient jdbc, Comptes comptes, Authentification authentification, Jetons jetons,
			Referentiel referentiel, ApplicationEventPublisher evenements, List<Acces.ContributeurExport> contributeurs,
			Audit audit) {
		this.audit = audit;
		this.jdbc = jdbc;
		this.comptes = comptes;
		this.authentification = authentification;
		this.jetons = jetons;
		this.referentiel = referentiel;
		this.evenements = evenements;
		this.contributeurs = contributeurs;
	}

	@PostMapping("/v1/auth/otp")
	@ResponseStatus(HttpStatus.ACCEPTED)
	@Public
	OtpSent demanderOtp(@Valid @RequestBody OtpRequest requete) {
		authentification.demanderOtp(telephone(requete.telephone(), requete.lienJeton()), requete.langue());
		return new OtpSent(Authentification.VALIDITE_CODE.toSeconds());
	}

	@PostMapping("/v1/auth/otp/verify")
	@Public
	Authentification.Session verifierOtp(@Valid @RequestBody OtpVerify requete) {
		return authentification.verifier(telephone(requete.telephone(), requete.lienJeton()), requete.code(),
				requete.appareilId());
	}

	/** Renouvelle la session avec le refreshToken (ajouté au contrat en v0.3.0). */
	@PostMapping("/v1/auth/refresh")
	@Public
	Authentification.Session rafraichir(@Valid @RequestBody Rafraichissement requete) {
		return authentification.rafraichir(requete.refreshToken());
	}

	/** Ferme la session de l'appareil : ses jetons ne valent plus, il ne reçoit plus de push (pas une révocation). */
	@PostMapping("/v1/auth/deconnexion")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Roles({ Role.AMBULANT, Role.HANOUT, Role.GROSSISTE, Role.GRAND_CLIENT, Role.ADMIN })
	@SansConsentement
	void seDeconnecter(Appelant appelant) {
		jdbc.sql("UPDATE appareil SET session = NULL, jeton_push = NULL WHERE id = :a AND utilisateur_id = :u")
			.param("a", appelant.appareilId()).param("u", appelant.utilisateurId()).update();
	}

	private String telephone(String telephone, String lienJeton) {
		if (lienJeton != null) {
			return authentification.telephoneDuLien(lienJeton);
		}
		if (telephone == null) {
			throw Probleme.requete("telephone ou lienJeton requis");
		}
		return telephone;
	}

	/** Lien hanout signé : ne révèle que la cible et le téléphone masqué. */
	@GetMapping("/v1/liens/{jeton}")
	@Public
	LienCible ouvrirLien(@PathVariable String jeton) {
		try {
			var c = jetons.verifier(jeton, "lien");
			return new LienCible(c.get("cible").toString(), UUID.fromString(c.get("cibleId").toString()),
					Comptes.masquer(c.get("tel").toString()), Instant.ofEpochSecond(((Number) c.get("exp")).longValue()));
		}
		catch (Jetons.Invalide e) {
			if (e.expire()) {
				throw new Probleme(HttpStatus.GONE, "lien-expire", null, "Lien expiré");
			}
			throw Probleme.introuvable();
		}
	}

	@PutMapping("/v1/appareils/{appareilId}/push")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Roles({ Role.AMBULANT, Role.HANOUT, Role.GROSSISTE })
	void enregistrerJetonPush(Appelant appelant, @PathVariable UUID appareilId, @Valid @RequestBody JetonPush jeton) {
		if (!appareilId.equals(appelant.appareilId())) {
			throw Probleme.introuvable();
		}
		jdbc.sql("UPDATE appareil SET jeton_push = :j, plateforme = :p, langue = coalesce(:l, langue) WHERE id = :id")
			.param("j", jeton.jeton()).param("p", jeton.plateforme()).param("l", jeton.langue()).param("id", appareilId)
			.update();
	}

	@GetMapping("/v1/moi/consentement")
	@Roles({ Role.AMBULANT, Role.HANOUT, Role.GROSSISTE, Role.GRAND_CLIENT })
	@SansConsentement
	Consentement lireConsentement(Appelant appelant) {
		return consentements(appelant.utilisateurId()).stream().findFirst().orElseThrow(Probleme::introuvable);
	}

	@PutMapping("/v1/moi/consentement")
	@Roles({ Role.AMBULANT, Role.HANOUT, Role.GROSSISTE, Role.GRAND_CLIENT })
	@SansConsentement
	@Transactional
	Consentement enregistrerConsentement(Appelant appelant, @Valid @RequestBody ConsentementSaisie saisie) {
		var geolocalisation = appelant.a(Role.AMBULANT) ? saisie.geolocalisation() : null;
		jdbc.sql("""
				INSERT INTO consentement (utilisateur_id, version_texte, traitement_donnees, geolocalisation, langue)
				VALUES (:u, :v, :t, :g, :l)
				""")
			.param("u", appelant.utilisateurId()).param("v", saisie.versionTexte()).param("t", saisie.traitementDonnees())
			.param("g", geolocalisation).param("l", saisie.langue())
			.update();
		evenements.publishEvent(new Comptes.ConsentementEnregistre(appelant.utilisateurId(), saisie.traitementDonnees(),
				Boolean.TRUE.equals(geolocalisation)));
		return lireConsentement(appelant);
	}

	/** Droit d'accès (loi 09-08) : tout ce que Daba conserve sur l'utilisateur connecté. */
	@GetMapping("/v1/moi/donnees")
	@Roles({ Role.AMBULANT, Role.HANOUT, Role.GROSSISTE, Role.GRAND_CLIENT })
	@SansConsentement
	Map<String, Object> exporterMesDonnees(Appelant appelant) {
		var export = new LinkedHashMap<String, Object>();
		export.put("utilisateur", jdbc.sql("""
				SELECT u.id, u.telephone, u.nom, u.role, u.hanout_id, u.cree_le, o.id AS organisation_id,
				       o.type AS organisation_type, o.nom AS organisation_nom
				FROM utilisateur u JOIN organisation o ON o.id = u.organisation_id WHERE u.id = :u
				""").param("u", appelant.utilisateurId()).query().singleRow());
		export.put("profil", comptes.profil(appelant.utilisateurId()).orElse(null));
		export.put("consentements", consentements(appelant.utilisateurId()));
		export.put("appareils", jdbc.sql("""
				SELECT a.id, p.prefixe, a.plateforme, a.langue,
				  a.utilisateur_id = au.utilisateur_id AND a.session IS NOT NULL AND a.jeton_push IS NOT NULL AS push_actif,
				  a.revoque, au.premiere_connexion AS cree_le, au.derniere_connexion, au.vu_le
				FROM appareil_utilisateur au JOIN appareil a ON a.id = au.appareil_id
				LEFT JOIN prefixe_numerotation p ON p.appareil_id = au.appareil_id AND p.utilisateur_id = au.utilisateur_id
				WHERE au.utilisateur_id = :u
				""").param("u", appelant.utilisateurId()).query().listOfRows());
		for (var contributeur : contributeurs) {
			export.put(contributeur.cle(), contributeur.exporter(appelant.utilisateurId()));
		}
		return export;
	}

	private List<Consentement> consentements(UUID utilisateurId) {
		return jdbc.sql("SELECT * FROM consentement WHERE utilisateur_id = :u ORDER BY id DESC")
			.param("u", utilisateurId)
			.query((rs, i) -> new Consentement(rs.getString("version_texte"), rs.getBoolean("traitement_donnees"),
					(Boolean) rs.getObject("geolocalisation"), rs.getString("langue"),
					rs.getTimestamp("enregistre_le").toInstant()))
			.list();
	}

	/** Inscription d'un grossiste ou d'un grand client et de ses utilisateurs par l'administrateur. */
	@PostMapping("/v1/admin/organisations")
	@ResponseStatus(HttpStatus.CREATED)
	@Roles(Role.ADMIN)
	@Transactional
	OrganisationCreee creerOrganisation(Appelant appelant, @Valid @RequestBody OrganisationSaisie saisie) {
		if ("GROSSISTE".equals(saisie.type()) && saisie.position() == null) {
			throw Probleme.requete("position requise pour un grossiste");
		}
		var id = UUID.randomUUID();
		jdbc.sql("INSERT INTO organisation (id, type, nom) VALUES (:id, :type, :nom)")
			.param("id", id).param("type", saisie.type()).param("nom", saisie.nom()).update();
		var utilisateurs = saisie.utilisateurs().stream()
			.map(m -> Map.<String, Object>of("id", comptes.inscrireMembre(id, saisie.type(), m.telephone(), m.nom()),
					"telephone", m.telephone()))
			.toList();
		if ("GROSSISTE".equals(saisie.type())) {
			referentiel.enregistrerGrossiste(id, saisie.nom(), saisie.position(), saisie.adresse(), saisie.ville());
		}
		audit.consigner(appelant, "ORGANISATION_CREEE", Map.of("organisationId", id, "type", saisie.type(),
				"utilisateurIds", utilisateurs.stream().map(u -> u.get("id")).toList()));
		return new OrganisationCreee(id, saisie.type(), saisie.nom(), utilisateurs);
	}

}

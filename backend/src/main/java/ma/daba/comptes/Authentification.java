package ma.daba.comptes;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import ma.daba.canaux.Canaux;
import ma.daba.commun.Appelant;
import ma.daba.commun.Audit;
import ma.daba.commun.Jetons;
import ma.daba.commun.Probleme;
import ma.daba.referentiel.Referentiel;

/**
 * Connexion par OTP SMS et, pour les portails web, par mot de passe (§10) ; sessions par appareil,
 * préfixe de numérotation des pièces (RG-F1).
 */
@Service
class Authentification {

	static final Duration VALIDITE_CODE = Duration.ofMinutes(5);
	static final Duration VALIDITE_ACCES = Duration.ofHours(24);
	static final Duration VALIDITE_REFRESH = Duration.ofDays(90);
	static final int ESSAIS_MAX = 5;
	static final int ENVOIS_MAX_PAR_HEURE = 5;
	private static final String ALPHABET_PREFIXE = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

	record Utilisateur(UUID id, String telephone, String nom, List<String> roles, UUID organisationId, UUID hanoutId,
			boolean nouveau) {
	}

	record Session(String accessToken, String refreshToken, long expireDans, Utilisateur utilisateur,
			String prefixeNumerotation) {
	}

	/** Méthode MOT_DE_PASSE : la session ; MOT_DE_PASSE_ET_OTP : le défi à joindre au code SMS. */
	record ConnexionMotDePasse(Session session, String defi, Long expireDans) {
	}

	private final SecureRandom aleatoire = new SecureRandom();
	private final JdbcClient jdbc;
	private final Jetons jetons;
	private final Canaux canaux;
	private final Comptes comptes;
	private final Referentiel referentiel;
	private final Set<String> administrateurs;
	private final MotsDePasse motsDePasse;
	private final Audit audit;
	private final TransactionTemplate transactions;

	Authentification(JdbcClient jdbc, Jetons jetons, Canaux canaux, Comptes comptes, Referentiel referentiel,
			@Value("${daba.admin.telephones:}") Set<String> administrateurs, MotsDePasse motsDePasse, Audit audit,
			TransactionTemplate transactions) {
		this.transactions = transactions;
		this.motsDePasse = motsDePasse;
		this.audit = audit;
		this.jdbc = jdbc;
		this.jetons = jetons;
		this.canaux = canaux;
		this.comptes = comptes;
		this.referentiel = referentiel;
		this.administrateurs = administrateurs;
	}

	/** Envoie un code ; même réponse que le numéro soit inscrit ou non. */
	@Transactional
	void demanderOtp(String telephone, String langue) {
		envoyerCode(telephone, langue, null);
	}

	/**
	 * Remplace le code du numéro (essais à zéro) ; {@code defi} est le nonce du défi MOT_DE_PASSE_ET_OTP
	 * qui l'a demandé (null sinon) : tout défi précédent ne vaut plus. Au-delà de ENVOIS_MAX_PAR_HEURE,
	 * rien n'est écrit : le code en cours, son défi et ses essais restent tels quels.
	 */
	private void envoyerCode(String telephone, String langue, UUID defi) {
		var code = "%06d".formatted(aleatoire.nextInt(1_000_000));
		var accepte = jdbc.sql("""
				INSERT INTO otp AS o (telephone, code_hash, expire_le, defi) VALUES (:t, :hash, :expire, CAST(:defi AS uuid))
				ON CONFLICT (telephone) DO UPDATE SET code_hash = excluded.code_hash, expire_le = excluded.expire_le, essais = 0,
				  defi = excluded.defi,
				  envois = CASE WHEN o.fenetre_debut < now() - interval '1 hour' THEN 1 ELSE o.envois + 1 END,
				  fenetre_debut = CASE WHEN o.fenetre_debut < now() - interval '1 hour' THEN now() ELSE o.fenetre_debut END
				WHERE o.fenetre_debut < now() - interval '1 hour' OR o.envois < :max
				RETURNING envois
				""")
			.param("t", telephone).param("hash", empreinte(telephone, code)).param("defi", defi).param("max", ENVOIS_MAX_PAR_HEURE)
			.param("expire", java.sql.Timestamp.from(Instant.now().plus(VALIDITE_CODE)))
			.query(Integer.class).optional();
		if (accepte.isEmpty()) {
			throw trop("Trop de codes demandés, réessayez dans une heure");
		}
		canaux.envoyer(Canaux.TypeMessage.OTP, null, comptes.destinataireTelephone(telephone, langue), code);
	}

	/**
	 * Vérifie le code, crée le compte à la première connexion et ouvre une session sur l'appareil. Un
	 * compte de portail dont la méthode exige un mot de passe n'a pas de session ici sans le défi du
	 * mot de passe (MOT_DE_PASSE_ET_OTP) lié à ce code ; son code reste alors valable pour définir un
	 * mot de passe. Le succès consomme le code et le défi.
	 */
	@Transactional(noRollbackFor = Probleme.class)
	Session verifier(String telephone, String code, UUID appareilId, String defi) {
		var nonce = verifierCode(telephone, code);
		var existant = comptes.utilisateurParTelephone(telephone);
		var utilisateurId = existant.orElseGet(() -> inscrire(telephone));
		controler(utilisateurId, appareilId);
		var methode = motsDePasse.methode(utilisateurId);
		if (methode != MotsDePasse.Methode.OTP && !(methode == MotsDePasse.Methode.MOT_DE_PASSE_ET_OTP
				&& defiValable(defi, utilisateurId, appareilId, nonce))) {
			throw motsDePasse.defini(utilisateurId)
					? Probleme.interdit("mot-de-passe-requis", "Connexion par mot de passe exigée pour ce compte")
					: Probleme.interdit("mot-de-passe-a-definir", "Définissez un mot de passe avec ce code");
		}
		supprimerCode(telephone);
		return ouvrir(utilisateurId, appareilId, existant.isEmpty());
	}

	/**
	 * Connexion d'un portail par mot de passe (§10) ; même réponse pour tout numéro avant un mot de passe
	 * juste. Pas de transaction autour : le calcul Argon2 ne retient aucune connexion à la base.
	 */
	ConnexionMotDePasse connecterParMotDePasse(String telephone, String motDePasse, UUID appareilId, String langue) {
		var utilisateurId = motsDePasse.verifier(telephone, motDePasse, appareilId)
			.orElseThrow(() -> new Probleme(HttpStatus.UNAUTHORIZED, "identifiants-invalides", null,
					"Numéro ou mot de passe incorrect"));
		return transaction(() -> {
			controler(utilisateurId, appareilId);
			return switch (motsDePasse.methode(utilisateurId)) {
				case OTP -> throw Probleme.interdit("connexion-par-code", "Ce compte se connecte par code SMS");
				case MOT_DE_PASSE -> new ConnexionMotDePasse(ouvrir(utilisateurId, appareilId, false), null, null);
				case MOT_DE_PASSE_ET_OTP -> {
					var nonce = UUID.randomUUID();
					envoyerCode(telephone, langue, nonce);
					var defi = jetons.signer("defi-mdp", Map.of("sub", utilisateurId.toString(), "app", appareilId.toString(),
							"ver", motsDePasse.version(utilisateurId), "n", nonce.toString()), VALIDITE_CODE);
					yield new ConnexionMotDePasse(null, defi, VALIDITE_CODE.toSeconds());
				}
			};
		});
	}

	/**
	 * Première connexion ou mot de passe oublié : le code SMS prouve le numéro, le mot de passe remplace
	 * l'ancien, toutes les sessions du compte se ferment et une nouvelle s'ouvre sur cet appareil. Code
	 * juste et compte de portail d'abord (aucun calcul Argon2 sans eux), empreinte calculée hors
	 * transaction, puis code consommé.
	 */
	Session definirMotDePasse(String telephone, String code, String motDePasse, UUID appareilId) {
		motsDePasse.valider(motDePasse, telephone);
		transaction(() -> {
			verifierCode(telephone, code);
			var role = comptes.utilisateurParTelephone(telephone).map(this::role)
				.orElse(administrateurs.contains(telephone) ? "ADMIN" : null);
			if (role == null || !MotsDePasse.ROLES_PORTAIL.contains(role)) {
				supprimerCode(telephone);
				throw Probleme.interdit("mot-de-passe-non-applicable", "Pas de mot de passe pour ce compte : connexion par code SMS");
			}
			return null;
		});
		var empreinte = motsDePasse.hacher(motDePasse);
		return transaction(() -> {
			verifierCode(telephone, code);
			var existant = comptes.utilisateurParTelephone(telephone);
			var utilisateurId = existant.orElseGet(() -> inscrire(telephone));
			controler(utilisateurId, appareilId);
			motsDePasse.enregistrerEmpreinte(utilisateurId, empreinte);
			fermerSessions(utilisateurId, null);
			supprimerCode(telephone);
			audit.consigner(new Appelant(utilisateurId, null, Set.of(), appareilId, null, null), "MOT_DE_PASSE_DEFINI",
					Map.of("utilisateurId", utilisateurId));
			return ouvrir(utilisateurId, appareilId, existant.isEmpty());
		});
	}

	/** Changement par l'utilisateur connecté : mot de passe actuel exigé, ses autres sessions se ferment. */
	void changerMotDePasse(Appelant appelant, String actuel, String nouveau) {
		var telephone = comptes.telephone(appelant.utilisateurId()).orElseThrow(Probleme::nonAuthentifie);
		motsDePasse.valider(nouveau, telephone);
		if (!motsDePasse.defini(appelant.utilisateurId())) {
			throw new Probleme(HttpStatus.CONFLICT, "mot-de-passe-absent", null,
					"Aucun mot de passe défini : passez par le code SMS");
		}
		if (motsDePasse.verifier(telephone, actuel, appelant.appareilId()).filter(appelant.utilisateurId()::equals).isEmpty()) {
			throw Probleme.interdit("identifiants-invalides", "Mot de passe actuel incorrect");
		}
		var empreinte = motsDePasse.hacher(nouveau);
		transaction(() -> {
			motsDePasse.enregistrerEmpreinte(appelant.utilisateurId(), empreinte);
			fermerSessions(appelant.utilisateurId(), appelant.appareilId());
			audit.consigner(appelant, "MOT_DE_PASSE_MODIFIE", Map.of("utilisateurId", appelant.utilisateurId()));
			return null;
		});
	}

	/** Toutes les sessions du compte (sauf celle de l'appareil {@code sauf}) se ferment ; rien n'est révoqué. */
	void fermerSessions(UUID utilisateurId, UUID sauf) {
		jdbc.sql("""
				UPDATE appareil SET session = NULL, jeton_push = NULL
				WHERE utilisateur_id = :u AND (CAST(:sauf AS uuid) IS NULL OR id <> :sauf)
				""").param("u", utilisateurId).param("sauf", sauf).update();
	}

	/**
	 * Comme {@code @Transactional(noRollbackFor = Probleme.class)}, pour une étape d'un parcours dont le
	 * calcul Argon2 se fait hors transaction : un refus métier garde ce qui a été écrit (essais comptés).
	 */
	private <T> T transaction(Supplier<T> etape) {
		var refus = new Probleme[1];
		var resultat = transactions.execute(statut -> {
			try {
				return etape.get();
			}
			catch (Probleme p) {
				refus[0] = p;
				return null;
			}
		});
		if (refus[0] != null) {
			throw refus[0];
		}
		return resultat;
	}

	/** Défi signé pour ce compte et cet appareil, mot de passe inchangé, et lié au code en cours (nonce). */
	private boolean defiValable(String defi, UUID utilisateurId, UUID appareilId, UUID nonce) {
		if (defi == null || nonce == null) {
			return false;
		}
		try {
			var c = jetons.verifier(defi, "defi-mdp");
			return utilisateurId.toString().equals(c.get("sub")) && appareilId.toString().equals(c.get("app"))
					&& nonce.toString().equals(c.get("n"))
					&& c.get("ver") instanceof Number v && v.longValue() == motsDePasse.version(utilisateurId);
		}
		catch (Jetons.Invalide e) {
			return false;
		}
	}

	private String role(UUID utilisateurId) {
		return jdbc.sql("SELECT role FROM utilisateur WHERE id = :u").param("u", utilisateurId).query(String.class).single();
	}

	/**
	 * Code OTP juste et valable ; renvoie le nonce du défi lié (ou null). Il n'est pas consommé ici
	 * ({@link #supprimerCode}). À appeler dans une transaction qui garde les essais comptés.
	 */
	private UUID verifierCode(String telephone, String code) {
		var otp = jdbc.sql("SELECT code_hash, expire_le, essais, defi FROM otp WHERE telephone = :t FOR UPDATE")
			.param("t", telephone)
			.query((rs, i) -> new Object[] { rs.getString("code_hash"), rs.getTimestamp("expire_le").toInstant(), rs.getInt("essais"),
					rs.getObject("defi", UUID.class) })
			.optional()
			.orElseThrow(Authentification::codeInvalide);
		if ((int) otp[2] >= ESSAIS_MAX) {
			throw trop("Trop d'essais : demandez un nouveau code");
		}
		if (((Instant) otp[1]).isBefore(Instant.now())) {
			throw codeInvalide();
		}
		if (!MessageDigest.isEqual(((String) otp[0]).getBytes(), empreinte(telephone, code).getBytes())) {
			jdbc.sql("UPDATE otp SET essais = essais + 1 WHERE telephone = :t").param("t", telephone).update();
			throw codeInvalide();
		}
		return (UUID) otp[3];
	}

	private void supprimerCode(String telephone) {
		jdbc.sql("DELETE FROM otp WHERE telephone = :t").param("t", telephone).update();
	}

	/** Appareil révoqué d'abord, puis compte désactivé. */
	private void controler(UUID utilisateurId, UUID appareilId) {
		var compte = jdbc.sql("SELECT role, desactive_le IS NOT NULL AS desactive FROM utilisateur WHERE id = :u FOR UPDATE")
			.param("u", utilisateurId)
			.query((rs, i) -> Map.entry(rs.getString("role"), rs.getBoolean("desactive"))).single();
		// Appareil révoqué d'abord : c'est la seule réponse qui fait effacer les données du téléphone.
		if (jdbc.sql("SELECT revoque FROM appareil WHERE id = :a").param("a", appareilId).query(Boolean.class).optional()
			.orElse(false)) {
			throw revoque();
		}
		if (compte.getValue()) {
			throw Comptes.desactive();
		}
	}

	/** Ouvre une nouvelle session sur l'appareil (contrôles déjà faits). */
	private Session ouvrir(UUID utilisateurId, UUID appareilId, boolean nouveau) {
		// Chaque connexion ouvre une nouvelle session sur l'appareil ; les jetons de la précédente ne valent plus.
		// Un appareil révoqué ne se rouvre jamais : la ligne reste telle quelle (aucune session, aucun jeton push).
		var ouverte = jdbc.sql("""
				INSERT INTO appareil (id, utilisateur_id, derniere_connexion, session) VALUES (:id, :u, now(), gen_random_uuid())
				ON CONFLICT (id) DO UPDATE SET derniere_connexion = now(), session = excluded.session,
				  jeton_push = CASE WHEN appareil.utilisateur_id = excluded.utilisateur_id THEN appareil.jeton_push END,
				  utilisateur_id = excluded.utilisateur_id
				WHERE NOT appareil.revoque
				RETURNING id
				""").param("id", appareilId).param("u", utilisateurId).query(UUID.class).optional();
		if (ouverte.isEmpty()) {
			throw revoque();
		}
		// Historique par compte : un téléphone partagé garde la trace de chaque compte qui s'y connecte.
		jdbc.sql("""
				INSERT INTO appareil_utilisateur (utilisateur_id, appareil_id, premiere_connexion, derniere_connexion, vu_le)
				VALUES (:u, :a, now(), now(), now())
				ON CONFLICT (utilisateur_id, appareil_id) DO UPDATE SET derniere_connexion = now(), vu_le = now()
				""").param("u", utilisateurId).param("a", appareilId).update();
		if ("AMBULANT".equals(role(utilisateurId))) {
			attribuerPrefixe(utilisateurId, appareilId);
		}
		return session(utilisateurId, appareilId, nouveau);
	}

	@Transactional
	Session rafraichir(String refreshToken) {
		Map<String, Object> claims;
		try {
			claims = jetons.verifier(refreshToken, "refresh");
		}
		catch (Jetons.Invalide e) {
			throw Probleme.nonAuthentifie();
		}
		var utilisateurId = UUID.fromString(claims.get("sub").toString());
		var appareilId = UUID.fromString(claims.get("app").toString());
		var etat = jdbc.sql("""
				SELECT revoque, session IS NOT NULL AND session::text = :ses AS ouverte
				FROM appareil WHERE id = :a AND utilisateur_id = :u
				""").param("a", appareilId).param("u", utilisateurId).param("ses", String.valueOf(claims.get("ses")))
			.query((rs, i) -> Map.entry(rs.getBoolean("revoque"), rs.getBoolean("ouverte"))).optional();
		// Révoqué : 403 pour que l'app d'un téléphone perdu efface ses données même quand son accessToken a expiré (§10).
		if (etat.isPresent() && etat.get().getKey()) {
			throw Probleme.interdit("appareil-revoque", "Appareil déconnecté : reconnectez-vous");
		}
		if (etat.isEmpty() || !etat.get().getValue()) {
			throw Probleme.nonAuthentifie();
		}
		return session(utilisateurId, appareilId, false);
	}

	/** Téléphone porté par un lien hanout signé (410 si expiré). */
	String telephoneDuLien(String lienJeton) {
		try {
			return jetons.verifier(lienJeton, "lien").get("tel").toString();
		}
		catch (Jetons.Invalide e) {
			if (e.expire()) {
				throw new Probleme(HttpStatus.GONE, "lien-expire", null, "Lien expiré");
			}
			throw Probleme.requete("Lien invalide");
		}
	}

	/**
	 * Première connexion : administrateur si le numéro est déclaré, hanout si le numéro est celui
	 * d'une fiche POS, ambulant sinon (inscription libre, §3.1). Grossistes et grands clients sont
	 * créés par l'administrateur.
	 */
	private UUID inscrire(String telephone) {
		var hanout = referentiel.hanoutParTelephone(telephone);
		String role;
		String type;
		String nom = null;
		if (administrateurs.contains(telephone)) {
			role = "ADMIN";
			type = "PLATEFORME";
		}
		else if (hanout.isPresent()) {
			role = "HANOUT";
			type = "HANOUT";
			nom = hanout.get().nom();
		}
		else {
			role = "AMBULANT";
			type = "AMBULANT";
		}
		var organisationId = UUID.randomUUID();
		jdbc.sql("INSERT INTO organisation (id, type, nom) VALUES (:id, :type, :nom)")
			.param("id", organisationId).param("type", type).param("nom", nom != null ? nom : Comptes.masquer(telephone))
			.update();
		var utilisateurId = UUID.randomUUID();
		jdbc.sql("""
				INSERT INTO utilisateur (id, organisation_id, telephone, nom, role, hanout_id)
				VALUES (:id, :org, :tel, :nom, :role, :hanout)
				""")
			.param("id", utilisateurId).param("org", organisationId).param("tel", telephone).param("nom", nom)
			.param("role", role).param("hanout", hanout.map(Referentiel.Hanout::id).orElse(null))
			.update();
		return utilisateurId;
	}

	/**
	 * Préfixe court, unique et stable pour le couple (ambulant, appareil) (RG-F1) : un second ambulant
	 * sur le même téléphone a sa propre série, le premier retrouve la sienne.
	 */
	private void attribuerPrefixe(UUID utilisateurId, UUID appareilId) {
		int attribue = 0;
		while (attribue == 0) {
			var prefixe = new StringBuilder();
			for (int i = 0; i < 4; i++) {
				prefixe.append(ALPHABET_PREFIXE.charAt(aleatoire.nextInt(ALPHABET_PREFIXE.length())));
			}
			attribue = jdbc.sql("""
					INSERT INTO prefixe_numerotation (utilisateur_id, appareil_id, prefixe) VALUES (:u, :a, :p)
					ON CONFLICT DO NOTHING
					""").param("u", utilisateurId).param("a", appareilId).param("p", prefixe.toString()).update();
			if (attribue == 0 && comptes.prefixe(utilisateurId, appareilId).isPresent()) {
				return;
			}
		}
	}

	Session session(UUID utilisateurId, UUID appareilId, boolean nouveau) {
		var utilisateur = jdbc.sql("SELECT * FROM utilisateur WHERE id = :u").param("u", utilisateurId)
			.query((rs, i) -> new Utilisateur(utilisateurId, rs.getString("telephone"), rs.getString("nom"),
					List.of(rs.getString("role")), rs.getObject("organisation_id", UUID.class),
					rs.getObject("hanout_id", UUID.class), nouveau))
			.single();
		var prefixe = comptes.prefixe(utilisateurId, appareilId).orElse(null);
		var session = jdbc.sql("SELECT session FROM appareil WHERE id = :a").param("a", appareilId).query(UUID.class).single();
		var claims = new HashMap<String, Object>();
		claims.put("sub", utilisateurId.toString());
		claims.put("org", utilisateur.organisationId().toString());
		claims.put("roles", utilisateur.roles());
		claims.put("app", appareilId.toString());
		claims.put("ses", session.toString());
		if (utilisateur.hanoutId() != null) {
			claims.put("hanout", utilisateur.hanoutId().toString());
		}
		var refresh = jetons.signer("refresh",
				Map.of("sub", utilisateurId.toString(), "app", appareilId.toString(), "ses", session.toString()), VALIDITE_REFRESH);
		return new Session(jetons.signer("acces", claims, VALIDITE_ACCES), refresh, VALIDITE_ACCES.toSeconds(),
				utilisateur, prefixe);
	}

	private String empreinte(String telephone, String code) {
		return jetons.empreinte("otp:" + telephone + ":" + code);
	}

	private static Probleme revoque() {
		return new Probleme(HttpStatus.UNAUTHORIZED, "appareil-revoque", null, "Appareil déconnecté à distance");
	}

	private static Probleme codeInvalide() {
		return new Probleme(HttpStatus.UNAUTHORIZED, "code-invalide", null, "Code invalide ou expiré");
	}

	private static Probleme trop(String detail) {
		return new Probleme(HttpStatus.TOO_MANY_REQUESTS, "trop-de-demandes", null, detail);
	}

}

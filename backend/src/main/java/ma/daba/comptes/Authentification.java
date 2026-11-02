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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ma.daba.canaux.Canaux;
import ma.daba.commun.Jetons;
import ma.daba.commun.Probleme;
import ma.daba.referentiel.Referentiel;

/** Connexion par OTP SMS, sessions par appareil, préfixe de numérotation des pièces (RG-F1). */
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

	private final SecureRandom aleatoire = new SecureRandom();
	private final JdbcClient jdbc;
	private final Jetons jetons;
	private final Canaux canaux;
	private final Comptes comptes;
	private final Referentiel referentiel;
	private final Set<String> administrateurs;

	Authentification(JdbcClient jdbc, Jetons jetons, Canaux canaux, Comptes comptes, Referentiel referentiel,
			@Value("${daba.admin.telephones:}") Set<String> administrateurs) {
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
		var code = "%06d".formatted(aleatoire.nextInt(1_000_000));
		var envois = jdbc.sql("""
				INSERT INTO otp (telephone, code_hash, expire_le) VALUES (:t, :hash, :expire)
				ON CONFLICT (telephone) DO UPDATE SET code_hash = excluded.code_hash, expire_le = excluded.expire_le, essais = 0,
				  envois = CASE WHEN otp.fenetre_debut < now() - interval '1 hour' THEN 1 ELSE otp.envois + 1 END,
				  fenetre_debut = CASE WHEN otp.fenetre_debut < now() - interval '1 hour' THEN now() ELSE otp.fenetre_debut END
				RETURNING envois
				""")
			.param("t", telephone).param("hash", empreinte(telephone, code))
			.param("expire", java.sql.Timestamp.from(Instant.now().plus(VALIDITE_CODE)))
			.query(Integer.class).single();
		if (envois > ENVOIS_MAX_PAR_HEURE) {
			throw trop("Trop de codes demandés, réessayez dans une heure");
		}
		canaux.envoyer(Canaux.TypeMessage.OTP, comptes.destinataireTelephone(telephone, langue), code);
	}

	/** Vérifie le code, crée le compte à la première connexion et ouvre une session sur l'appareil. */
	@Transactional(noRollbackFor = Probleme.class)
	Session verifier(String telephone, String code, UUID appareilId) {
		var otp = jdbc.sql("SELECT code_hash, expire_le, essais FROM otp WHERE telephone = :t FOR UPDATE")
			.param("t", telephone)
			.query((rs, i) -> new Object[] { rs.getString("code_hash"), rs.getTimestamp("expire_le").toInstant(), rs.getInt("essais") })
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
		jdbc.sql("DELETE FROM otp WHERE telephone = :t").param("t", telephone).update();

		var existant = comptes.utilisateurParTelephone(telephone);
		var utilisateurId = existant.orElseGet(() -> inscrire(telephone));
		var role = jdbc.sql("SELECT role FROM utilisateur WHERE id = :u").param("u", utilisateurId).query(String.class).single();
		var revoque = jdbc.sql("""
				INSERT INTO appareil (id, utilisateur_id, derniere_connexion) VALUES (:id, :u, now())
				ON CONFLICT (id) DO UPDATE SET derniere_connexion = now(),
				  prefixe = CASE WHEN appareil.utilisateur_id = excluded.utilisateur_id THEN appareil.prefixe END,
				  jeton_push = CASE WHEN appareil.utilisateur_id = excluded.utilisateur_id THEN appareil.jeton_push END,
				  utilisateur_id = excluded.utilisateur_id
				RETURNING revoque
				""").param("id", appareilId).param("u", utilisateurId).query(Boolean.class).single();
		if (revoque) {
			throw new Probleme(HttpStatus.UNAUTHORIZED, "appareil-revoque", null, "Appareil déconnecté à distance");
		}
		if ("AMBULANT".equals(role)) {
			attribuerPrefixe(appareilId);
		}
		return session(utilisateurId, appareilId, existant.isEmpty());
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
		var actif = jdbc.sql("SELECT count(*) FROM appareil WHERE id = :a AND utilisateur_id = :u AND NOT revoque")
			.param("a", appareilId).param("u", utilisateurId).query(Long.class).single() > 0;
		if (!actif) {
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

	/** Préfixe court, unique et stable pour l'appareil d'un ambulant (RG-F1). */
	private void attribuerPrefixe(UUID appareilId) {
		while (jdbc.sql("SELECT prefixe IS NULL FROM appareil WHERE id = :id").param("id", appareilId).query(Boolean.class).single()) {
			var prefixe = new StringBuilder();
			for (int i = 0; i < 4; i++) {
				prefixe.append(ALPHABET_PREFIXE.charAt(aleatoire.nextInt(ALPHABET_PREFIXE.length())));
			}
			jdbc.sql("""
					UPDATE appareil SET prefixe = :p
					WHERE id = :id AND prefixe IS NULL AND NOT EXISTS (SELECT 1 FROM appareil WHERE prefixe = :p)
					""").param("p", prefixe.toString()).param("id", appareilId).update();
		}
	}

	Session session(UUID utilisateurId, UUID appareilId, boolean nouveau) {
		var utilisateur = jdbc.sql("SELECT * FROM utilisateur WHERE id = :u").param("u", utilisateurId)
			.query((rs, i) -> new Utilisateur(utilisateurId, rs.getString("telephone"), rs.getString("nom"),
					List.of(rs.getString("role")), rs.getObject("organisation_id", UUID.class),
					rs.getObject("hanout_id", UUID.class), nouveau))
			.single();
		var prefixe = jdbc.sql("SELECT prefixe FROM appareil WHERE id = :a").param("a", appareilId).query(String.class)
			.optional().orElse(null);
		var claims = new HashMap<String, Object>();
		claims.put("sub", utilisateurId.toString());
		claims.put("org", utilisateur.organisationId().toString());
		claims.put("roles", utilisateur.roles());
		claims.put("app", appareilId.toString());
		if (utilisateur.hanoutId() != null) {
			claims.put("hanout", utilisateur.hanoutId().toString());
		}
		var refresh = jetons.signer("refresh", Map.of("sub", utilisateurId.toString(), "app", appareilId.toString()),
				VALIDITE_REFRESH);
		return new Session(jetons.signer("acces", claims, VALIDITE_ACCES), refresh, VALIDITE_ACCES.toSeconds(),
				utilisateur, prefixe);
	}

	private String empreinte(String telephone, String code) {
		return jetons.empreinte("otp:" + telephone + ":" + code);
	}

	private static Probleme codeInvalide() {
		return new Probleme(HttpStatus.UNAUTHORIZED, "code-invalide", null, "Code invalide ou expiré");
	}

	private static Probleme trop(String detail) {
		return new Probleme(HttpStatus.TOO_MANY_REQUESTS, "trop-de-demandes", null, detail);
	}

}

package ma.daba.commun;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

/**
 * JWS compacts signés en HS256 : jetons d'accès, liens hanout, QR des bons de sortie et des
 * réservations. La charge reste lisible sans la clé (l'app ambulant lit le QR hors ligne).
 */
@Component
public class Jetons {

	private static final String ENTETE = b64("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));

	private final String secret;
	private final JsonMapper json;
	private final JdbcClient jdbc;
	private volatile SecretKeySpec cle;

	/**
	 * {@code daba.jwt.secret} (DABA_JWT_SECRET) d'au moins 32 caractères ; absent, une clé aléatoire
	 * est générée au premier usage et conservée en base, jamais une clé connue.
	 */
	public Jetons(@Value("${daba.jwt.secret:}") String secret, JsonMapper json, JdbcClient jdbc) {
		if (!secret.isBlank() && secret.length() < 32) {
			throw new IllegalStateException("DABA_JWT_SECRET doit faire au moins 32 caractères");
		}
		this.secret = secret;
		this.json = json;
		this.jdbc = jdbc;
	}

	private SecretKeySpec cle() {
		if (cle == null) {
			synchronized (this) {
				if (cle == null) {
					var valeur = secret;
					if (valeur.isBlank()) {
						var aleatoire = new byte[48];
						new SecureRandom().nextBytes(aleatoire);
						jdbc.sql("INSERT INTO cle_signature (id, cle) VALUES (1, :cle) ON CONFLICT (id) DO NOTHING")
							.param("cle", b64(aleatoire)).update();
						valeur = jdbc.sql("SELECT cle FROM cle_signature WHERE id = 1").query(String.class).single();
						LoggerFactory.getLogger(Jetons.class).info("DABA_JWT_SECRET absent : clé de signature générée et conservée en base");
					}
					cle = new SecretKeySpec(valeur.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
				}
			}
		}
		return cle;
	}

	/** Signe {@code charge} ; {@code typ} distingue les usages, {@code duree} null = sans expiration. */
	public String signer(String typ, Map<String, ?> charge, Duration duree) {
		var claims = new HashMap<String, Object>(charge);
		claims.put("typ", typ);
		claims.put("iat", Instant.now().getEpochSecond());
		if (duree != null) {
			claims.put("exp", Instant.now().plus(duree).getEpochSecond());
		}
		var corps = ENTETE + "." + b64(json.writeValueAsBytes(claims));
		return corps + "." + b64(hmac(corps));
	}

	/** Vérifie signature, type et expiration ; lève {@link Invalide}. */
	@SuppressWarnings("unchecked")
	public Map<String, Object> verifier(String jeton, String typ) {
		var parties = jeton == null ? new String[0] : jeton.split("\\.");
		if (parties.length != 3) {
			throw new Invalide(false);
		}
		byte[] signature;
		Map<String, Object> claims;
		try {
			signature = Base64.getUrlDecoder().decode(parties[2]);
			claims = json.readValue(Base64.getUrlDecoder().decode(parties[1]), Map.class);
		}
		catch (RuntimeException e) {
			throw new Invalide(false);
		}
		if (!MessageDigest.isEqual(signature, hmac(parties[0] + "." + parties[1])) || !typ.equals(claims.get("typ"))) {
			throw new Invalide(false);
		}
		if (claims.get("exp") instanceof Number exp && exp.longValue() < Instant.now().getEpochSecond()) {
			throw new Invalide(true);
		}
		return claims;
	}

	private byte[] hmac(String donnees) {
		try {
			var mac = Mac.getInstance("HmacSHA256");
			mac.init(cle());
			return mac.doFinal(donnees.getBytes(StandardCharsets.UTF_8));
		}
		catch (GeneralSecurityException e) {
			throw new IllegalStateException(e);
		}
	}

	/** Empreinte HMAC d'une valeur sensible (code OTP) : jamais stockée en clair. */
	public String empreinte(String valeur) {
		return b64(hmac(valeur));
	}

	private static String b64(byte[] octets) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(octets);
	}

	public static class Invalide extends RuntimeException {

		private final boolean expire;

		Invalide(boolean expire) {
			super(expire ? "Jeton expiré" : "Jeton invalide");
			this.expire = expire;
		}

		public boolean expire() {
			return expire;
		}

	}

}

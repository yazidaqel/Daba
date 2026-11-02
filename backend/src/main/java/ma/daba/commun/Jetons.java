package ma.daba.commun;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

/**
 * JWS compacts signés en HS256 : jetons d'accès, liens hanout, QR des bons de sortie et des
 * réservations. La charge reste lisible sans la clé (l'app ambulant lit le QR hors ligne).
 */
@Component
public class Jetons {

	static final String SECRET_DEV = "dev-uniquement-a-remplacer-par-DABA_JWT_SECRET";

	private static final String ENTETE = b64("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));

	private final SecretKeySpec cle;
	private final JsonMapper json;

	public Jetons(@Value("${daba.jwt.secret}") String secret, JsonMapper json) {
		if (SECRET_DEV.equals(secret)) {
			LoggerFactory.getLogger(Jetons.class).warn("Clé de signature de développement : définir DABA_JWT_SECRET hors du poste de dev");
		}
		this.cle = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
		this.json = json;
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
			mac.init(cle);
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

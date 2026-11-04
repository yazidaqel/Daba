package ma.daba.commun;

import java.time.Duration;

import org.springframework.http.HttpStatus;

/**
 * Erreur métier rendue au format RFC 9457. {@code code} devient la fin de {@code type},
 * {@code regle} nomme la règle du cahier des charges en cause.
 */
public class Probleme extends RuntimeException {

	private final HttpStatus statut;
	private final String code;
	private final String regle;
	private final Duration reessayerDans;

	public Probleme(HttpStatus statut, String code, String regle, String detail) {
		this(statut, code, regle, detail, null);
	}

	private Probleme(HttpStatus statut, String code, String regle, String detail, Duration reessayerDans) {
		super(detail);
		this.statut = statut;
		this.code = code;
		this.regle = regle;
		this.reessayerDans = reessayerDans;
	}

	/** 429 avec l'en-tête Retry-After (secondes). */
	public static Probleme tropDeDemandes(String detail, Duration reessayerDans) {
		return new Probleme(HttpStatus.TOO_MANY_REQUESTS, "trop-de-demandes", null, detail, reessayerDans);
	}

	public static Probleme introuvable() {
		return new Probleme(HttpStatus.NOT_FOUND, "introuvable", null, "Ressource inexistante");
	}

	public static Probleme requete(String detail) {
		return new Probleme(HttpStatus.BAD_REQUEST, "requete-invalide", null, detail);
	}

	public static Probleme conflit(String detail) {
		return new Probleme(HttpStatus.CONFLICT, "conflit", null, detail);
	}

	public static Probleme conflit(String regle, String detail) {
		return new Probleme(HttpStatus.CONFLICT, "conflit", regle, detail);
	}

	/** Règle de gestion violée (422). */
	public static Probleme regle(String regle, String detail) {
		return new Probleme(HttpStatus.UNPROCESSABLE_CONTENT, "regle-de-gestion", regle, detail);
	}

	public static Probleme interdit(String code, String detail) {
		return new Probleme(HttpStatus.FORBIDDEN, code, null, detail);
	}

	public static Probleme nonAuthentifie() {
		return new Probleme(HttpStatus.UNAUTHORIZED, "non-authentifie", null, "Jeton absent, invalide ou expiré");
	}

	public HttpStatus statut() {
		return statut;
	}

	public String code() {
		return code;
	}

	public String regle() {
		return regle;
	}

	/** Délai de l'en-tête Retry-After, ou null. */
	public Duration reessayerDans() {
		return reessayerDans;
	}

}

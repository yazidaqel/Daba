package ma.daba.commun;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Annotations de contrôle d'accès des opérations REST, reprises de {@code x-roles} du contrat. */
public final class Acces {

	private Acces() {
	}

	/** Rôles autorisés ; une opération /v1 sans cette annotation ni {@link Public} est refusée. */
	@Target(ElementType.METHOD)
	@Retention(RetentionPolicy.RUNTIME)
	public @interface Roles {

		Appelant.Role[] value();

	}

	/** Opération sans authentification (OTP, liens signés). */
	@Target(ElementType.METHOD)
	@Retention(RetentionPolicy.RUNTIME)
	public @interface Public {

	}

	/** Opération permise avant le consentement CNDP (consentement, droit d'accès, remontée des pièces émises). */
	@Target(ElementType.METHOD)
	@Retention(RetentionPolicy.RUNTIME)
	public @interface SansConsentement {

	}

	/** Contrôles propres au compte (appareil révoqué, consentement), fournis par le module comptes. */
	public interface VerificationCompte {

		void verifier(Appelant appelant, boolean consentementExige);

	}

	/** Contribution d'un module à l'export des données personnelles (droit d'accès, loi 09-08). */
	public interface ContributeurExport {

		String cle();

		Object exporter(java.util.UUID utilisateurId);

	}

}

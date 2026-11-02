package ma.daba.commun;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Geste d'un ambulant remonté par synchronisation : qui, sur quel appareil, quand (RG-S2),
 * et par quelle opération. {@code ambulantId} est l'utilisateur, {@code organisationId} son organisation.
 */
public record Geste(UUID ambulantId, UUID organisationId, UUID appareilId, UUID operationId, Instant horodatage) {

	/** Effet d'une opération appliquée : anomalies détectées, ou conflit résolu côté serveur. */
	public record Effet(List<Anomalie> anomalies, Conflit conflit) {

		public static Effet ok() {
			return new Effet(List.of(), null);
		}

		public static Effet de(List<Anomalie> anomalies) {
			return new Effet(List.copyOf(anomalies), null);
		}

	}

	/** Conflit avec l'état serveur, résolu et journalisé (pas d'écrasement silencieux). */
	public record Conflit(String code, String resolution, String detail, Object versionServeur) {

		/** Une modification plus récente faite ailleurs est conservée. */
		public static Conflit versionServeur(String detail, Object versionServeur) {
			return new Conflit("MODIFIE_AILLEURS", "VERSION_SERVEUR_CONSERVEE", detail, versionServeur);
		}

	}

}

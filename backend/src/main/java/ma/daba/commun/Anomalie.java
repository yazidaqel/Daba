package ma.daba.commun;

import java.util.UUID;

/**
 * Anomalie détectée en appliquant un fait terrain : l'opération est appliquée quand même,
 * l'anomalie est journalisée et renvoyée à l'appareil (RG-S1, RG-C3, RG-F1…).
 */
public record Anomalie(String type, String regle, UUID produitId, String detail) {

	public static Anomalie de(String type, String regle, String detail) {
		return new Anomalie(type, regle, null, detail);
	}

}

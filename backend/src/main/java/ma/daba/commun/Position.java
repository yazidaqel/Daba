package ma.daba.commun;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/** Position WGS 84. */
public record Position(@NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
		@NotNull @DecimalMin("-180") @DecimalMax("180") Double lon) {

	/** Expression SQL PostGIS d'un point géographique, avec les paramètres nommés {@code :<nom>Lon} et {@code :<nom>Lat}. */
	public static String sql(String nom) {
		return "ST_SetSRID(ST_MakePoint(:" + nom + "Lon, :" + nom + "Lat), 4326)::geography";
	}

}

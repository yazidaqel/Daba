package ma.daba.commun;

import java.util.Set;
import java.util.UUID;

/** Produits déjà achetés par un hanout à ses ambulants (fourni par le module ventes). */
public interface ProduitsHabituels {

	Set<UUID> pour(UUID hanoutId);

}

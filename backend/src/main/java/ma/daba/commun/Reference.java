package ma.daba.commun;

import java.util.UUID;

/** Référence courte vers un acteur, pour l'affichage. */
public record Reference(UUID id, String nom) {
}

package ma.daba.commun;

import java.util.Set;
import java.util.UUID;

/**
 * Utilisateur authentifié, lu dans le jeton d'accès. L'organisation sert au cloisonnement
 * multi-tenant : jamais lue dans le chemin ni dans le corps.
 */
public record Appelant(UUID utilisateurId, UUID organisationId, Set<Role> roles, UUID appareilId, UUID hanoutId) {

	public enum Role {
		AMBULANT, HANOUT, GROSSISTE, GRAND_CLIENT, ADMIN
	}

	public boolean a(Role role) {
		return roles.contains(role);
	}

}

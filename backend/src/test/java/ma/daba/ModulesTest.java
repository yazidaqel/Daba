package ma.daba;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModulesTest {

	// Échoue si un module utilise les classes internes d'un autre au lieu de son API publique.
	@Test
	void modulesRespectTheirBoundaries() {
		ApplicationModules.of(DabaApplication.class).verify();
	}

}

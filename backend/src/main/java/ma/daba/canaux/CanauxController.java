package ma.daba.canaux;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import ma.daba.canaux.ParametresCanaux.Configuration;
import ma.daba.commun.Acces.Roles;
import ma.daba.commun.Appelant;
import ma.daba.commun.Appelant.Role;

/** Paramétrage des canaux par l'administrateur (§9) ; aucun secret n'entre ni ne sort par ici. */
@RestController
class CanauxController {

	private final ParametresCanaux parametres;

	CanauxController(ParametresCanaux parametres) {
		this.parametres = parametres;
	}

	@GetMapping("/v1/admin/canaux")
	@Roles(Role.ADMIN)
	Configuration lireConfigurationCanaux() {
		return parametres.effective();
	}

	@PutMapping("/v1/admin/canaux")
	@Roles(Role.ADMIN)
	Configuration modifierConfigurationCanaux(Appelant appelant, @Valid @RequestBody Configuration saisie) {
		parametres.enregistrer(appelant, saisie);
		return parametres.effective();
	}

}

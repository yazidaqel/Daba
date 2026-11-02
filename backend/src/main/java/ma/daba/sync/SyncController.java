package ma.daba.sync;

import java.util.List;
import java.util.UUID;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ma.daba.commun.Acces.Roles;
import ma.daba.commun.Acces.SansConsentement;
import ma.daba.commun.Appelant;
import ma.daba.commun.Appelant.Role;
import ma.daba.commun.Journal;
import ma.daba.commun.Probleme;
import tools.jackson.databind.JsonNode;

@RestController
@Validated
class SyncController {

	record LotOperations(@NotNull UUID appareilId, @NotEmpty @Size(max = 500) List<JsonNode> operations) {
	}

	record ResultatLot(List<Synchronisation.Resultat> resultats) {
	}

	private final Synchronisation synchronisation;
	private final Journal journal;

	SyncController(Synchronisation synchronisation, Journal journal) {
		this.synchronisation = synchronisation;
		this.journal = journal;
	}

	/** Ouvert sans consentement : les pièces déjà émises doivent remonter (obligation de conservation, RG-C4). */
	@PostMapping("/v1/sync/operations")
	@Roles(Role.AMBULANT)
	@SansConsentement
	ResultatLot pousser(Appelant appelant, @Valid @RequestBody LotOperations lot) {
		if (!lot.appareilId().equals(appelant.appareilId())) {
			throw Probleme.interdit("appareil-different", "appareilId différent de l'appareil de la session");
		}
		for (var operation : lot.operations()) {
			if (operation == null || !operation.isObject() || !operation.hasNonNull("id")) {
				throw Probleme.requete("Chaque opération porte un id");
			}
		}
		return new ResultatLot(synchronisation.appliquer(appelant, lot.operations()));
	}

	@GetMapping("/v1/sync/changements")
	@Roles(Role.AMBULANT)
	Journal.Lot tirer(Appelant appelant, @RequestParam(required = false) String curseur,
			@RequestParam(defaultValue = "500") @Min(1) @Max(1000) int limite) {
		return journal.lire(appelant.utilisateurId(), curseur, limite);
	}

}

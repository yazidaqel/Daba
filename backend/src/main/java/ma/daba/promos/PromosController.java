package ma.daba.promos;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import ma.daba.commun.Acces.Roles;
import ma.daba.commun.Appelant;
import ma.daba.commun.Appelant.Role;
import ma.daba.commun.Page;

@RestController
@Validated
class PromosController {

	private final Promos promos;

	PromosController(Promos promos) {
		this.promos = promos;
	}

	// ---------------------------------------------------------------- grand client

	@PostMapping("/v1/campagnes")
	@ResponseStatus(HttpStatus.CREATED)
	@Roles(Role.GRAND_CLIENT)
	Promos.Campagne creerCampagne(Appelant appelant, @Valid @RequestBody Promos.CampagneSaisie saisie) {
		return promos.creerCampagne(appelant.organisationId(), saisie);
	}

	@GetMapping("/v1/campagnes")
	@Roles(Role.GRAND_CLIENT)
	Page<Promos.Campagne> campagnes(Appelant appelant,
			@RequestParam(required = false) @Pattern(regexp = "^(BROUILLON|PROPOSEE|ACTIVE|EPUISEE|EXPIREE|CLOTUREE)$") String statut,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "50") @Min(1) @Max(200) int taille) {
		return promos.campagnes(appelant.organisationId(), statut, page, taille);
	}

	@GetMapping("/v1/campagnes/{campagneId}")
	@Roles(Role.GRAND_CLIENT)
	Promos.Campagne campagne(Appelant appelant, @PathVariable UUID campagneId) {
		return promos.campagne(appelant.organisationId(), campagneId);
	}

	@PutMapping("/v1/campagnes/{campagneId}")
	@Roles(Role.GRAND_CLIENT)
	Promos.Campagne modifierCampagne(Appelant appelant, @PathVariable UUID campagneId,
			@Valid @RequestBody Promos.CampagneSaisie saisie) {
		return promos.modifierCampagne(appelant.organisationId(), campagneId, saisie);
	}

	@DeleteMapping("/v1/campagnes/{campagneId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Roles(Role.GRAND_CLIENT)
	void supprimerCampagne(Appelant appelant, @PathVariable UUID campagneId) {
		promos.supprimerCampagne(appelant.organisationId(), campagneId);
	}

	@PostMapping("/v1/campagnes/{campagneId}/depots")
	@ResponseStatus(HttpStatus.CREATED)
	@Roles(Role.GRAND_CLIENT)
	Promos.Depot proposerDepot(Appelant appelant, @PathVariable UUID campagneId,
			@Valid @RequestBody Promos.PropositionDepot proposition) {
		return promos.proposerDepot(appelant.organisationId(), campagneId, proposition);
	}

	@GetMapping("/v1/campagnes/{campagneId}/depots")
	@Roles(Role.GRAND_CLIENT)
	List<Promos.Depot> depotsCampagne(Appelant appelant, @PathVariable UUID campagneId) {
		return promos.depotsCampagne(appelant.organisationId(), campagneId);
	}

	@GetMapping("/v1/campagnes/{campagneId}/entonnoir")
	@Roles(Role.GRAND_CLIENT)
	Promos.Entonnoir entonnoir(Appelant appelant, @PathVariable UUID campagneId) {
		promos.campagne(appelant.organisationId(), campagneId);
		return promos.entonnoir(campagneId);
	}

	@PostMapping("/v1/depots/{depotId}/reponse-marque")
	@Roles(Role.GRAND_CLIENT)
	Promos.Depot repondreContreProposition(Appelant appelant, @PathVariable UUID depotId,
			@Valid @RequestBody Promos.ReponseMarque reponse) {
		return promos.repondreMarque(appelant.organisationId(), depotId, reponse);
	}

	// ---------------------------------------------------------------- grossiste

	@GetMapping("/v1/depots")
	@Roles(Role.GROSSISTE)
	Page<Promos.Depot> depots(Appelant appelant,
			@RequestParam(required = false) @Pattern(regexp = "^(PROPOSE|CONTRE_PROPOSE|ACCEPTE|REFUSE|RECU|CLOTURE)$") String statut,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "50") @Min(1) @Max(200) int taille) {
		return promos.depots(appelant.organisationId(), statut, page, taille);
	}

	@GetMapping("/v1/depots/{depotId}")
	@Roles({ Role.GROSSISTE, Role.GRAND_CLIENT })
	Promos.Depot depot(Appelant appelant, @PathVariable UUID depotId) {
		return promos.depotVisible(appelant.organisationId(), depotId);
	}

	@PostMapping("/v1/depots/{depotId}/reponse")
	@Roles(Role.GROSSISTE)
	Promos.Depot repondre(Appelant appelant, @PathVariable UUID depotId, @Valid @RequestBody Promos.ReponseGrossiste reponse) {
		return promos.repondre(appelant.organisationId(), depotId, reponse);
	}

	@PostMapping(path = "/v1/depots/{depotId}/reception", consumes = "multipart/form-data")
	@Roles(Role.GROSSISTE)
	Promos.Depot confirmerReception(Appelant appelant, @PathVariable UUID depotId,
			@Valid @RequestPart("reception") Promos.ReceptionDepot reception,
			@RequestPart("photoBonLivraison") MultipartFile photo) {
		return promos.recevoir(appelant.organisationId(), depotId, reception, photo);
	}

	@PostMapping("/v1/depots/{depotId}/cloture")
	@Roles(Role.GROSSISTE)
	Promos.Depot cloturer(Appelant appelant, @PathVariable UUID depotId, @Valid @RequestBody Promos.ClotureDepot cloture) {
		return promos.cloturer(appelant.organisationId(), depotId, cloture);
	}

	record ScanQr(@jakarta.validation.constraints.NotBlank String qrJeton) {
	}

	@PostMapping("/v1/retraits/apercu")
	@Roles(Role.GROSSISTE)
	Promos.Reservation apercuRetrait(Appelant appelant, @Valid @RequestBody ScanQr scan) {
		return promos.apercuRetrait(appelant.organisationId(), scan.qrJeton());
	}

	@PostMapping("/v1/retraits")
	@ResponseStatus(HttpStatus.CREATED)
	@Roles(Role.GROSSISTE)
	Promos.Reservation validerRetrait(Appelant appelant, @Valid @RequestBody Promos.RetraitSaisie saisie) {
		return promos.retirer(appelant.organisationId(), appelant.utilisateurId(), saisie);
	}

	// ---------------------------------------------------------------- ambulant

	@GetMapping("/v1/promos")
	@Roles(Role.AMBULANT)
	Page<Promos.OffrePromo> promos(Appelant appelant, @RequestParam(required = false) String categorie,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "50") @Min(1) @Max(200) int taille) {
		return promos.offres(appelant.utilisateurId(), categorie, page, taille);
	}

	@PostMapping("/v1/reservations")
	@Roles(Role.AMBULANT)
	ResponseEntity<Promos.Reservation> reserver(Appelant appelant, @Valid @RequestBody Promos.ReservationSaisie saisie) {
		var resultat = promos.reserver(appelant.utilisateurId(), saisie);
		return ResponseEntity.status(resultat.getValue() ? HttpStatus.CREATED : HttpStatus.OK).body(resultat.getKey());
	}

	@GetMapping("/v1/reservations")
	@Roles({ Role.AMBULANT, Role.GROSSISTE })
	Page<Promos.Reservation> reservations(Appelant appelant,
			@RequestParam(required = false) @Pattern(regexp = "^(RESERVEE|RETIREE|EXPIREE|REVENDUE)$") String statut,
			@RequestParam(required = false) UUID depotId,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "50") @Min(1) @Max(200) int taille) {
		return appelant.a(Role.AMBULANT) ? promos.reservations(appelant.utilisateurId(), null, statut, depotId, page, taille)
				: promos.reservations(null, appelant.organisationId(), statut, depotId, page, taille);
	}

	@GetMapping("/v1/reservations/{reservationId}")
	@Roles({ Role.AMBULANT, Role.GROSSISTE })
	Promos.Reservation reservation(Appelant appelant, @PathVariable UUID reservationId) {
		return appelant.a(Role.AMBULANT) ? promos.reservationVisible(appelant.utilisateurId(), null, reservationId)
				: promos.reservationVisible(null, appelant.organisationId(), reservationId);
	}

}

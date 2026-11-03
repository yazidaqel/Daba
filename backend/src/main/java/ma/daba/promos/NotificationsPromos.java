package ma.daba.promos;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.UUID;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import ma.daba.canaux.Canaux;
import ma.daba.canaux.Canaux.TypeMessage;
import ma.daba.comptes.Comptes;
import ma.daba.referentiel.Referentiel;

/** Notifications du flux promo (§9), envoyées depuis l'outbox après le commit. */
@Component
class NotificationsPromos {

	static final ZoneId CASABLANCA = ZoneId.of("Africa/Casablanca");

	/** Réservation qui expire dans l'heure (§9, priorité haute). */
	record RappelReservation(UUID reservationId) {
	}

	private final Promos promos;
	private final Comptes comptes;
	private final Canaux canaux;
	private final Referentiel referentiel;

	NotificationsPromos(Promos promos, Comptes comptes, Canaux canaux, Referentiel referentiel) {
		this.promos = promos;
		this.comptes = comptes;
		this.canaux = canaux;
		this.referentiel = referentiel;
	}

	@ApplicationModuleListener
	void depotPropose(Promos.DepotPropose evenement) {
		var depot = promos.depot(evenement.depotId());
		for (var membre : comptes.membres(depot.grossiste().id())) {
			canaux.envoyer(TypeMessage.PROPOSITION_DEPOT, depot.id(), comptes.destinataire(membre), depot.marque().nom(), depot.campagne().nom());
		}
	}

	/**
	 * « Promo confirmée chez un grossiste de sa zone » : moins de 2 minutes après la confirmation (§10),
	 * aux ambulants visés par catégorie (§9, même règle que le fil). ponytail: pas encore de filtre par
	 * zone de tournée (pilote sur une seule ville).
	 */
	@ApplicationModuleListener
	void receptionConfirmee(Promos.ReceptionConfirmee evenement) {
		var depot = promos.depot(evenement.depotId());
		var nom = promos.nomsCampagnes(Set.of(depot.campagne().id())).get(depot.campagne().id());
		var fin = DateTimeFormatter.ofPattern("dd/MM").withZone(CASABLANCA).format(depot.finCampagne());
		for (var ambulant : promos.ambulantsVises(depot.id())) {
			canaux.envoyer(TypeMessage.PROMO_CONFIRMEE, depot.id(), comptes.destinataire(ambulant), nom, depot.grossiste().nom(), fin);
		}
	}

	@ApplicationModuleListener
	void retraitEffectue(Promos.RetraitEffectue evenement) {
		var reservation = promos.reservation(evenement.reservationId(), false);
		var depot = promos.depot(reservation.depotId());
		var nom = promos.nomsCampagnes(Set.of(reservation.campagneId())).get(reservation.campagneId());
		var destinataires = new java.util.ArrayList<>(comptes.membres(depot.grossiste().id()));
		destinataires.addAll(comptes.membres(depot.marque().id()));
		for (var membre : destinataires) {
			canaux.envoyer(TypeMessage.RETRAIT_EFFECTUE, reservation.id(), comptes.destinataire(membre), reservation.quantiteRetiree(), nom);
		}
	}

	@ApplicationModuleListener
	void rappelReservation(RappelReservation evenement) {
		var reservation = promos.reservation(evenement.reservationId(), false);
		var produit = referentiel.produit(reservation.produitId()).map(Referentiel.Produit::libelle).orElse(null);
		var heure = DateTimeFormatter.ofPattern("HH:mm").withZone(CASABLANCA).format(reservation.expireLe());
		canaux.envoyer(TypeMessage.RESERVATION_EXPIRE_BIENTOT, reservation.id(), comptes.destinataire(reservation.ambulant().id()), produit,
				reservation.grossiste().nom(), heure);
	}

}

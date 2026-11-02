package ma.daba.depannage;

import java.util.Map;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import ma.daba.canaux.Canaux;
import ma.daba.canaux.Canaux.TypeMessage;
import ma.daba.comptes.Comptes;
import ma.daba.referentiel.Referentiel;

/** Notifications du dépannage (§9), envoyées depuis l'outbox après le commit. */
@Component
class NotificationsDepannage {

	private final Depannage depannage;
	private final Comptes comptes;
	private final Canaux canaux;
	private final Referentiel referentiel;

	NotificationsDepannage(Depannage depannage, Comptes comptes, Canaux canaux, Referentiel referentiel) {
		this.depannage = depannage;
		this.comptes = comptes;
		this.canaux = canaux;
		this.referentiel = referentiel;
	}

	/** Rupture signalée par un hanout proche, produit en stock → ambulant disponible (push + SMS, priorité haute). */
	@ApplicationModuleListener
	void demandeEnvoyee(Depannage.DemandeEnvoyee evenement) {
		var demande = depannage.resume(evenement.demandeId());
		var distances = depannage.destinataires(evenement.demandeId()).stream()
			.collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
		for (var ambulant : evenement.ambulants()) {
			canaux.envoyer(TypeMessage.RUPTURE_SIGNALEE, comptes.destinataire(ambulant), distances.getOrDefault(ambulant, 0),
					produit(demande), demande.quantite());
		}
	}

	@ApplicationModuleListener
	void demandeChangee(Depannage.DemandeChangee evenement) {
		var demande = depannage.resume(evenement.demandeId());
		var hanout = comptes.destinataire(demande.hanoutUtilisateurId());
		var produit = produit(demande);
		switch (evenement.statut()) {
			case "ACCEPTEE" -> {
				canaux.envoyer(TypeMessage.DEMANDE_ACCEPTEE, hanout, produit, comptes.nom(demande.ambulantId()),
						comptes.telephone(demande.ambulantId()).orElse(""));
				// Les autres ambulants sont prévenus que la demande est prise (§3.7).
				for (var autre : depannage.destinataires(evenement.demandeId())) {
					if (!autre.getKey().equals(demande.ambulantId())) {
						canaux.envoyer(TypeMessage.DEMANDE_PRISE, comptes.destinataire(autre.getKey()), produit);
					}
				}
			}
			case "EN_ROUTE" -> canaux.envoyer(TypeMessage.DEMANDE_EN_ROUTE, hanout, comptes.nom(demande.ambulantId()), produit);
			case "LIVREE" -> canaux.envoyer(TypeMessage.DEMANDE_LIVREE, hanout, produit);
			case "SANS_REPONSE" -> canaux.envoyer(TypeMessage.DEMANDE_SANS_REPONSE, hanout, produit);
			default -> {
			}
		}
	}

	private Object produit(Depannage.Resume demande) {
		return demande.produitId() == null ? "—"
				: referentiel.produit(demande.produitId()).map(Referentiel.Produit::libelle).map(Object.class::cast).orElse("—");
	}

}

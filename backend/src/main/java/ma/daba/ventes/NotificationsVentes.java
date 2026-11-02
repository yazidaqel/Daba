package ma.daba.ventes;

import java.time.Duration;
import java.util.Map;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import ma.daba.canaux.Canaux;
import ma.daba.comptes.Comptes;
import ma.daba.referentiel.Referentiel;

/**
 * « Nouvelle facture émise → hanout, SMS avec lien » (§9), et avis du hanout (écart, contestation)
 * vers l'ambulant (§6), traités depuis l'outbox après le commit.
 */
@Component
class NotificationsVentes {

	static final Duration VALIDITE_LIEN = Duration.ofDays(30);

	private final Ventes ventes;
	private final Referentiel referentiel;
	private final Comptes comptes;
	private final Canaux canaux;
	private final AvisHanout avis;

	NotificationsVentes(Ventes ventes, Referentiel referentiel, Comptes comptes, Canaux canaux, AvisHanout avis) {
		this.ventes = ventes;
		this.referentiel = referentiel;
		this.comptes = comptes;
		this.canaux = canaux;
		this.avis = avis;
	}

	@ApplicationModuleListener
	void documentEmis(Ventes.DocumentEmis evenement) {
		var document = ventes.document(evenement.documentId());
		var telephone = referentiel.hanout(document.hanout().id()).map(Referentiel.Hanout::telephone).orElse(null);
		if (telephone == null) {
			return;
		}
		var lien = comptes.lien(Comptes.CibleLien.DOCUMENT, document.id(), telephone, VALIDITE_LIEN);
		canaux.envoyer(Canaux.TypeMessage.DOCUMENT_EMIS, comptes.destinataireTelephone(telephone, null),
				document.ambulant().nom(), document.numero(), dirhams(document.total()), lien);
	}

	/** Écart signalé par le hanout à la réception (§6) → ambulant, push. */
	@ApplicationModuleListener
	void ecartSignale(AvisHanout.EcartSignale evenement) {
		var document = ventes.document(evenement.documentId());
		canaux.envoyer(Canaux.TypeMessage.RECEPTION_ECART, comptes.destinataire(document.ambulant().id()),
				document.hanout().nom(), document.numero());
	}

	/** Contestation d'un document ou d'un solde (§6) → ambulant, push puis SMS en repli. */
	@ApplicationModuleListener
	void contestationOuverte(AvisHanout.ContestationOuverte evenement) {
		var c = avis.contestation(evenement.contestationId());
		Object objet = "DOCUMENT".equals(c.cible()) ? ventes.document(c.cibleId()).numero() : SOLDE;
		canaux.envoyer(Canaux.TypeMessage.CONTESTATION, comptes.destinataire(c.ambulant().id()), c.hanout().nom(), objet);
	}

	private static final Map<String, String> SOLDE = Map.of("fr", "le solde", "ar", "الرصيد", "ary-Arab", "الحساب",
			"ary-Latn", "l7sab");

	static String dirhams(long centimes) {
		return "%d,%02d".formatted(centimes / 100, Math.abs(centimes % 100));
	}

}

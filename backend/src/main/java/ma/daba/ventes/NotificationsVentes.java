package ma.daba.ventes;

import java.time.Duration;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import ma.daba.canaux.Canaux;
import ma.daba.comptes.Comptes;
import ma.daba.referentiel.Referentiel;

/** « Nouvelle facture émise → hanout, SMS avec lien » (§9), traité depuis l'outbox après le commit. */
@Component
class NotificationsVentes {

	static final Duration VALIDITE_LIEN = Duration.ofDays(30);

	private final Ventes ventes;
	private final Referentiel referentiel;
	private final Comptes comptes;
	private final Canaux canaux;

	NotificationsVentes(Ventes ventes, Referentiel referentiel, Comptes comptes, Canaux canaux) {
		this.ventes = ventes;
		this.referentiel = referentiel;
		this.comptes = comptes;
		this.canaux = canaux;
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

	static String dirhams(long centimes) {
		return "%d,%02d".formatted(centimes / 100, Math.abs(centimes % 100));
	}

}

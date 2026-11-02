package ma.daba.canaux;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import ma.daba.canaux.Canaux.Canal;
import ma.daba.canaux.Canaux.Destinataire;
import ma.daba.canaux.Canaux.Fournisseur;

/**
 * Fournisseurs simulés : le message est seulement écrit dans le journal applicatif (codes OTP
 * compris). ponytail: aucun opérateur branché ; remplacer chaque bean par le vrai fournisseur
 * (SMS, FCM, WhatsApp Business API) avant le pilote terrain.
 */
@Configuration(proxyBeanMethods = false)
class FournisseursJournal {

	@Bean
	Fournisseur push() {
		return new Journalise(Canal.PUSH);
	}

	@Bean
	Fournisseur sms() {
		return new Journalise(Canal.SMS);
	}

	@Bean
	Fournisseur whatsapp() {
		return new Journalise(Canal.WHATSAPP);
	}

	record Journalise(Canal canal) implements Fournisseur {

		private static final Logger log = LoggerFactory.getLogger(FournisseursJournal.class);

		@Override
		public boolean envoyer(Destinataire destinataire, String texte) {
			log.info("[{}] {} : {}", canal, canal == Canal.PUSH ? destinataire.utilisateurId() : destinataire.telephone(), texte);
			return true;
		}

	}

}

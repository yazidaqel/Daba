package ma.daba.canaux;

import java.util.function.BooleanSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import ma.daba.canaux.Canaux.Canal;
import ma.daba.canaux.Canaux.Fournisseur;
import ma.daba.canaux.Canaux.Message;

/**
 * Fournisseurs JOURNAL : le message est seulement écrit dans le journal applicatif. Le texte complet
 * (codes OTP, liens signés) n'y est écrit que tant qu'aucun vrai opérateur n'est configuré sur le
 * serveur (poste de dev, démo) ; ensuite, seulement le type et le numéro masqué. Seul fournisseur
 * WhatsApp pour l'instant.
 */
@Configuration(proxyBeanMethods = false)
class FournisseursJournal {

	private final BooleanSupplier dev;

	FournisseursJournal(ObjectProvider<Fournisseur> fournisseurs) {
		this.dev = () -> fournisseurs.stream().noneMatch(f -> f.code() != Fournisseur.Code.JOURNAL && f.configure());
	}

	@Bean
	Fournisseur push() {
		return new Journalise(Canal.PUSH, dev);
	}

	@Bean
	Fournisseur sms() {
		return new Journalise(Canal.SMS, dev);
	}

	@Bean
	Fournisseur whatsapp() {
		return new Journalise(Canal.WHATSAPP, dev);
	}

	record Journalise(Canal canal, BooleanSupplier dev) implements Fournisseur {

		private static final Logger log = LoggerFactory.getLogger(FournisseursJournal.class);

		@Override
		public Code code() {
			return Code.JOURNAL;
		}

		@Override
		public boolean envoyer(Message message) {
			var d = message.destinataire();
			if (dev.getAsBoolean()) {
				log.info("[{}] {} : {}", canal, canal == Canal.PUSH ? d.utilisateurId() : d.telephone(), message.texte());
			}
			else {
				log.info("[{}] {} : {} (texte non journalisé : un vrai fournisseur est configuré)", canal,
						canal == Canal.PUSH ? d.utilisateurId() : masquer(d.telephone()), message.type());
			}
			return true;
		}

		static String masquer(String telephone) {
			return telephone == null || telephone.length() < 8 ? "•••"
					: telephone.substring(0, 5) + "•".repeat(telephone.length() - 8) + telephone.substring(telephone.length() - 3);
		}

	}

}

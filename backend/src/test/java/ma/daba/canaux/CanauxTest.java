package ma.daba.canaux;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.env.MockEnvironment;

import ma.daba.TestIntegration;
import ma.daba.canaux.Canaux.Canal;
import ma.daba.canaux.Canaux.Destinataire;
import ma.daba.canaux.Canaux.TypeMessage;

class CanauxTest extends TestIntegration {

	@Autowired
	org.springframework.jdbc.core.simple.JdbcClient client;

	/** Fournisseur de test qui note les envois et peut échouer. */
	record Faux(Canal canal, boolean accepte, List<String> envois) implements Canaux.Fournisseur {

		@Override
		public boolean envoyer(Destinataire destinataire, String texte) {
			envois.add(canal + ":" + texte);
			return accepte;
		}

	}

	@Test
	void canalPrincipalPuisRepliEnCasDEchecOuDeDestinataireInjoignable() {
		var envois = new ArrayList<String>();
		var canaux = new Canaux(List.of(new Faux(Canal.PUSH, false, envois), new Faux(Canal.SMS, true, envois)),
				Set.of(Canal.PUSH, Canal.SMS), new MockEnvironment(), client);
		var avecPush = new Destinataire(null, "+212612345678", List.of("fcm"), "fr");
		assertThat(canaux.envoyer(TypeMessage.PROMO_CONFIRMEE, avecPush, "Promo", "Grossiste", "10/11")).contains(Canal.SMS);
		assertThat(envois).hasSize(2).first().asString().startsWith("PUSH:");

		envois.clear();
		var sansPush = new Destinataire(null, "+212612345678", List.of(), "fr");
		assertThat(canaux.envoyer(TypeMessage.PROMO_CONFIRMEE, sansPush, "Promo", "Grossiste", "10/11")).contains(Canal.SMS);
		assertThat(envois).hasSize(1);
	}

	@Test
	void routageEtCanauxActifsParametrablesSansDeveloppement() {
		var envois = new ArrayList<String>();
		var env = new MockEnvironment().withProperty("daba.canaux.routage.DOCUMENT_EMIS", "WHATSAPP,SMS");
		var fournisseurs = List.<Canaux.Fournisseur>of(new Faux(Canal.WHATSAPP, true, envois), new Faux(Canal.SMS, true, envois));
		var destinataire = new Destinataire(null, "+212612345678", List.of(), "ary-Latn");

		var whatsappCoupe = new Canaux(fournisseurs, Set.of(Canal.SMS), env, client);
		assertThat(whatsappCoupe.envoyer(TypeMessage.DOCUMENT_EMIS, destinataire, "Said", "K7Q2-BL-000001", "12,50", "lien")).contains(Canal.SMS);

		var whatsappActif = new Canaux(fournisseurs, Set.of(Canal.SMS, Canal.WHATSAPP), env, client);
		assertThat(whatsappActif.envoyer(TypeMessage.DOCUMENT_EMIS, destinataire, "Said", "K7Q2-BL-000001", "12,50", "lien")).contains(Canal.WHATSAPP);
		assertThat(envois.getLast()).startsWith("WHATSAPP:Daba: Said 3tak");
	}

	@Test
	void texteDansLaLangueDuDestinataireAvecLibelleMultilingue() {
		var canaux = new Canaux(List.of(), Set.of(), new MockEnvironment(), client);
		var produit = java.util.Map.of("fr", "Thé", "ar", "شاي");
		assertThat(canaux.texte(TypeMessage.DEMANDE_LIVREE, "ar", produit)).contains("شاي").startsWith("دابا");
		assertThat(canaux.texte(TypeMessage.DEMANDE_LIVREE, "ary-Latn", produit)).contains("Thé");
		assertThat(canaux.texte(TypeMessage.DEMANDE_LIVREE, "xx", produit)).startsWith("Daba : votre commande de Thé");
	}

}

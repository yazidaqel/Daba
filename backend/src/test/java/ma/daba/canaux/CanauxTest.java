package ma.daba.canaux;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.env.MockEnvironment;

import ma.daba.TestIntegration;
import ma.daba.canaux.Canaux.Canal;
import ma.daba.canaux.Canaux.Destinataire;
import ma.daba.canaux.Canaux.Fournisseur;
import ma.daba.canaux.Canaux.Fournisseur.Code;
import ma.daba.canaux.Canaux.Message;
import ma.daba.canaux.Canaux.TypeMessage;
import ma.daba.canaux.ParametresCanaux.CanauxOrganisation;
import ma.daba.canaux.ParametresCanaux.Configuration;
import ma.daba.canaux.ParametresCanaux.ParametreCanal;
import ma.daba.canaux.ParametresCanaux.RoutageMessage;
import ma.daba.commun.Audit;
import ma.daba.commun.Json;

/** Couche canaux (§9) : repli, routage, canaux coupés aux trois niveaux, choix du fournisseur. */
class CanauxTest extends TestIntegration {

	@Autowired
	Json jsonDaba;

	@Autowired
	Audit audit;

	@Autowired
	ma.daba.commun.Acces.Organisations organisations;

	/** Fournisseur de test qui note les envois et peut échouer. */
	record Faux(Canal canal, Code code, boolean configure, boolean accepte, List<String> envois) implements Fournisseur {

		Faux(Canal canal, boolean accepte, List<String> envois) {
			this(canal, Code.JOURNAL, true, accepte, envois);
		}

		@Override
		public boolean envoyer(Message message) {
			envois.add(canal + "/" + code + ":" + message.texte());
			return accepte;
		}

	}

	/** Canaux sur une configuration enregistrée donnée (null = valeurs par défaut), sans toucher à la base partagée. */
	private Canaux canaux(List<Fournisseur> fournisseurs, Set<Canal> actifs, MockEnvironment env, Configuration enregistree) {
		var parametres = new ParametresCanaux(fournisseurs, actifs, env, jdbc, jsonDaba, audit, organisations) {
			@Override
			Configuration enregistree() {
				return enregistree;
			}
		};
		return new Canaux(fournisseurs, parametres, jdbc);
	}

	private static Destinataire destinataire(UUID organisation, List<String> jetons) {
		return new Destinataire(null, organisation, "+212612345678", jetons, "fr");
	}

	@Test
	void canalPrincipalPuisRepliEnCasDEchecOuDeDestinataireInjoignable() {
		var envois = new ArrayList<String>();
		var canaux = canaux(List.of(new Faux(Canal.PUSH, false, envois), new Faux(Canal.SMS, true, envois)),
				Set.of(Canal.PUSH, Canal.SMS), new MockEnvironment(), null);
		assertThat(canaux.envoyer(TypeMessage.PROMO_CONFIRMEE, null, destinataire(null, List.of("fcm")), "Promo", "Grossiste", "10/11"))
			.contains(Canal.SMS);
		assertThat(envois).hasSize(2).first().asString().startsWith("PUSH");

		envois.clear();
		assertThat(canaux.envoyer(TypeMessage.PROMO_CONFIRMEE, null, destinataire(null, List.of()), "Promo", "Grossiste", "10/11"))
			.contains(Canal.SMS);
		assertThat(envois).hasSize(1);
	}

	@Test
	void routageParDefautParametrableParPropriete() {
		var envois = new ArrayList<String>();
		var env = new MockEnvironment().withProperty("daba.canaux.routage.DOCUMENT_EMIS", "WHATSAPP,SMS");
		var fournisseurs = List.<Fournisseur>of(new Faux(Canal.WHATSAPP, true, envois), new Faux(Canal.SMS, true, envois));
		var d = new Destinataire(null, null, "+212612345678", List.of(), "ary-Latn");

		var whatsappCoupe = canaux(fournisseurs, Set.of(Canal.SMS), env, null);
		assertThat(whatsappCoupe.envoyer(TypeMessage.DOCUMENT_EMIS, null, d, "Said", "K7Q2-BL-000001", "12,50", "lien")).contains(Canal.SMS);

		var whatsappActif = canaux(fournisseurs, Set.of(Canal.SMS, Canal.WHATSAPP), env, null);
		assertThat(whatsappActif.envoyer(TypeMessage.DOCUMENT_EMIS, null, d, "Said", "K7Q2-BL-000001", "12,50", "lien")).contains(Canal.WHATSAPP);
		assertThat(envois.getLast()).startsWith("WHATSAPP/JOURNAL:Daba: Said 3tak");
	}

	@Test
	void canalCoupeAuNiveauSystemeTypeEtOrganisationPasseAuRepli() {
		var envois = new ArrayList<String>();
		var fournisseurs = List.<Fournisseur>of(new Faux(Canal.PUSH, true, envois), new Faux(Canal.SMS, true, envois));
		var organisation = UUID.randomUUID();
		var d = destinataire(organisation, List.of("jeton"));
		var tous = Set.of(Canal.PUSH, Canal.SMS);

		// Rien de coupé : PUSH, canal principal de CONTESTATION.
		assertThat(canaux(fournisseurs, tous, new MockEnvironment(), null).envoyer(TypeMessage.CONTESTATION, null, d, "H", "doc"))
			.contains(Canal.PUSH);

		// Niveau système.
		var systeme = config(List.of(new ParametreCanal(Canal.PUSH, false, Code.JOURNAL, null)), List.of(), List.of());
		assertThat(canaux(fournisseurs, tous, new MockEnvironment(), systeme).envoyer(TypeMessage.CONTESTATION, null, d, "H", "doc"))
			.contains(Canal.SMS);

		// Niveau type de message : coupé pour CONTESTATION seulement.
		var type = config(List.of(), List.of(new RoutageMessage(TypeMessage.CONTESTATION, Canal.PUSH, Canal.SMS, Set.of(Canal.PUSH))),
				List.of());
		var parType = canaux(fournisseurs, tous, new MockEnvironment(), type);
		assertThat(parType.envoyer(TypeMessage.CONTESTATION, null, d, "H", "doc")).contains(Canal.SMS);
		assertThat(parType.envoyer(TypeMessage.RECEPTION_ECART, null, d, "H", "doc")).contains(Canal.PUSH);

		// Niveau organisation : coupé pour les destinataires de cette organisation seulement.
		var org = config(List.of(), List.of(), List.of(new CanauxOrganisation(organisation, Set.of(Canal.PUSH))));
		var parOrg = canaux(fournisseurs, tous, new MockEnvironment(), org);
		assertThat(parOrg.envoyer(TypeMessage.CONTESTATION, null, d, "H", "doc")).contains(Canal.SMS);
		assertThat(parOrg.envoyer(TypeMessage.CONTESTATION, null, destinataire(UUID.randomUUID(), List.of("jeton")), "H", "doc"))
			.contains(Canal.PUSH);

		// Principal et repli coupés : rien ne part.
		var deux = config(List.of(), List.of(), List.of(new CanauxOrganisation(organisation, Set.of(Canal.PUSH, Canal.SMS))));
		assertThat(canaux(fournisseurs, tous, new MockEnvironment(), deux).envoyer(TypeMessage.CONTESTATION, null, d, "H", "doc"))
			.isEmpty();
	}

	@Test
	void routageEtFournisseurChoisisParLAdministrateur() {
		var envois = new ArrayList<String>();
		var fournisseurs = List.<Fournisseur>of(new Faux(Canal.SMS, Code.JOURNAL, true, true, envois),
				new Faux(Canal.SMS, Code.INFOBIP, true, true, envois), new Faux(Canal.WHATSAPP, true, envois));
		var tous = Set.of(Canal.PUSH, Canal.SMS, Canal.WHATSAPP);
		var d = destinataire(null, List.of());

		// Par défaut : le vrai opérateur configuré plutôt que JOURNAL.
		canaux(fournisseurs, tous, new MockEnvironment(), null).envoyer(TypeMessage.DEMANDE_LIVREE, null, d, "Thé");
		assertThat(envois.getLast()).startsWith("SMS/INFOBIP:");

		var choix = config(List.of(new ParametreCanal(Canal.SMS, true, Code.INFOBIP, null)),
				List.of(new RoutageMessage(TypeMessage.DEMANDE_LIVREE, Canal.WHATSAPP, Canal.SMS, null)), List.of());
		var canaux = canaux(fournisseurs, tous, new MockEnvironment(), choix);
		assertThat(canaux.envoyer(TypeMessage.DEMANDE_LIVREE, null, d, "Thé")).contains(Canal.WHATSAPP);
		assertThat(canaux.envoyer(TypeMessage.DEMANDE_EN_ROUTE, null, d, "Said", "Thé")).contains(Canal.SMS);
		assertThat(envois.getLast()).startsWith("SMS/INFOBIP:");
	}

	@Test
	void journalInterditDesQuUnVraiFournisseurEstConfigurePourLeCanal() {
		var envois = new ArrayList<String>();
		var fournisseurs = List.<Fournisseur>of(new Faux(Canal.SMS, Code.JOURNAL, true, true, envois),
				new Faux(Canal.SMS, Code.INFOBIP, true, true, envois), new Faux(Canal.WHATSAPP, true, envois));
		var parametres = new ParametresCanaux(fournisseurs, Set.of(Canal.SMS), new MockEnvironment(), jdbc, jsonDaba, audit,
				organisations);
		var admin = new ma.daba.commun.Appelant(UUID.randomUUID(), UUID.randomUUID(), Set.of(ma.daba.commun.Appelant.Role.ADMIN),
				null, null, null);
		var journal = config(List.of(new ParametreCanal(Canal.SMS, true, Code.JOURNAL, null)), List.of(), List.of());
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> parametres.enregistrer(admin, journal))
			.isInstanceOfSatisfying(ma.daba.commun.Probleme.class, p -> assertThat(p.code()).isEqualTo("journal-interdit"));
		// WhatsApp n'a pas de vrai fournisseur : JOURNAL reste permis (refus seulement sur l'organisation inconnue).
		var whatsapp = config(List.of(new ParametreCanal(Canal.WHATSAPP, true, Code.JOURNAL, null)), List.of(),
				List.of(new CanauxOrganisation(UUID.randomUUID(), Set.of(Canal.WHATSAPP))));
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> parametres.enregistrer(admin, whatsapp))
			.isInstanceOfSatisfying(ma.daba.commun.Probleme.class, p -> assertThat(p.getMessage()).startsWith("Organisations inconnues"));
	}

	@Test
	void otpIgnoreLaCoupureParOrganisationPourQueTousPuissentSeConnecter() {
		var envois = new ArrayList<String>();
		var organisation = UUID.randomUUID();
		var coupe = config(List.of(), List.of(), List.of(new CanauxOrganisation(organisation, Set.of(Canal.SMS))));
		var canaux = canaux(List.of(new Faux(Canal.SMS, true, envois)), Set.of(Canal.SMS), new MockEnvironment(), coupe);
		assertThat(canaux.envoyer(TypeMessage.OTP, null, destinataire(organisation, List.of()), "123456")).contains(Canal.SMS);
		assertThat(canaux.envoyer(TypeMessage.DEMANDE_LIVREE, null, destinataire(organisation, List.of()), "Thé")).isEmpty();
	}

	@Test
	void fournisseurChoisiPuisDeconfigureEstUnEchecQuiPasseAuRepli() {
		var envois = new ArrayList<String>();
		var fournisseurs = List.<Fournisseur>of(new Faux(Canal.PUSH, Code.FCM, false, true, envois), new Faux(Canal.SMS, true, envois));
		var choix = config(List.of(new ParametreCanal(Canal.PUSH, true, Code.FCM, null)), List.of(), List.of());
		var canaux = canaux(fournisseurs, Set.of(Canal.PUSH, Canal.SMS), new MockEnvironment(), choix);
		assertThat(canaux.envoyer(TypeMessage.PROMO_CONFIRMEE, null, destinataire(null, List.of("jeton")), "P", "G", "10/11"))
			.contains(Canal.SMS);
		assertThat(envois).hasSize(1).first().asString().startsWith("SMS");
	}

	@Test
	void texteDansLaLangueDuDestinataireAvecLibelleMultilingue() {
		var canaux = canaux(List.of(), Set.of(), new MockEnvironment(), null);
		var produit = java.util.Map.of("fr", "Thé", "ar", "شاي");
		assertThat(canaux.texte(TypeMessage.DEMANDE_LIVREE, "ar", produit)).contains("شاي").startsWith("دابا");
		assertThat(canaux.texte(TypeMessage.DEMANDE_LIVREE, "ary-Latn", produit)).contains("Thé");
		assertThat(canaux.texte(TypeMessage.DEMANDE_LIVREE, "xx", produit)).startsWith("Daba : votre commande de Thé");
		assertThat(Canaux.corps("Daba : votre commande de Thé est livrée.", "Daba")).isEqualTo("votre commande de Thé est livrée.");
		assertThat(Canaux.corps("دابا: تم تسليم طلبك", "دابا")).isEqualTo("تم تسليم طلبك");
	}

	@Test
	void pushSansNomNiTelephoneNiLienSurLEcranVerrouilleSmsInchange() {
		var canaux = canaux(List.of(), Set.of(), new MockEnvironment(), null);
		var produit = java.util.Map.of("fr", "Thé", "ar", "شاي");
		for (var langue : List.of("fr", "ar", "ary-Arab", "ary-Latn")) {
			assertThat(canaux.texte(TypeMessage.CONTESTATION, Canal.PUSH, langue, "Hanout Zerktouni", "K7Q2-BL-000001"))
				.contains("K7Q2-BL-000001").doesNotContain("Zerktouni");
			assertThat(canaux.texte(TypeMessage.RECEPTION_ECART, Canal.PUSH, langue, "Hanout Zerktouni", "K7Q2-BL-000001"))
				.doesNotContain("Zerktouni");
			assertThat(canaux.texte(TypeMessage.DEMANDE_ACCEPTEE, Canal.PUSH, langue, produit, "Said", "+212612345678"))
				.doesNotContain("Said").doesNotContain("+212");
			assertThat(canaux.texte(TypeMessage.DEMANDE_EN_ROUTE, Canal.PUSH, langue, "Said", produit)).doesNotContain("Said");
			assertThat(canaux.texte(TypeMessage.DOCUMENT_EMIS, Canal.PUSH, langue, "Said", "K7Q2-BL-000001", "12,50", "https://lien"))
				.doesNotContain("Said").doesNotContain("https://");
		}
		assertThat(canaux.texte(TypeMessage.CONTESTATION, Canal.SMS, "fr", "Hanout Zerktouni", "K7Q2-BL-000001"))
			.isEqualTo("Daba : Hanout Zerktouni conteste K7Q2-BL-000001. Ouvrez l’app pour répondre.");
		// Sans variante push, le texte commun (produit et quantité seulement).
		assertThat(canaux.texte(TypeMessage.RUPTURE_SIGNALEE, Canal.PUSH, "fr", 300, produit, 12))
			.isEqualTo(canaux.texte(TypeMessage.RUPTURE_SIGNALEE, Canal.SMS, "fr", 300, produit, 12));

		// Bout en bout : le fournisseur push reçoit la variante sans nom.
		var envois = new ArrayList<String>();
		var avecPush = canaux(List.of(new Faux(Canal.PUSH, true, envois)), Set.of(Canal.PUSH), new MockEnvironment(), null);
		avecPush.envoyer(TypeMessage.CONTESTATION, null, destinataire(null, List.of("jeton")), "Hanout Zerktouni", "K7Q2-BL-000001");
		assertThat(envois).singleElement().asString().startsWith("PUSH/JOURNAL:Daba : un hanout conteste K7Q2-BL-000001");
	}

	private static Configuration config(List<ParametreCanal> canaux, List<RoutageMessage> types, List<CanauxOrganisation> orgs) {
		return new Configuration(canaux, types, orgs);
	}

}

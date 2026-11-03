package ma.daba.canaux;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Couche canaux unique (§9) : chaque type de message a un canal principal et un canal de repli ;
 * chaque canal s'active ou se coupe au niveau du système, du type de message et de l'organisation du
 * destinataire, et utilise le fournisseur choisi par l'administrateur, sans développement
 * ({@code /v1/admin/canaux}, {@link ParametresCanaux}).
 */
@Service
public class Canaux {

	private static final Logger log = LoggerFactory.getLogger(Canaux.class);

	public enum Canal {
		PUSH, SMS, WHATSAPP
	}

	/**
	 * Priorité d'un message (colonne « Priorité » du tableau §9) : canal de notification Android
	 * ({@code channel_id} créé par l'app) et priorité FCM. Aucun type BASSE encore (alertes de stock embarqué).
	 */
	public enum Priorite {

		HAUTE("haute", "HIGH"), MOYENNE("moyenne", "NORMAL"), BASSE("basse", "NORMAL");

		final String canalAndroid;
		final String prioriteFcm;

		Priorite(String canalAndroid, String prioriteFcm) {
			this.canalAndroid = canalAndroid;
			this.prioriteFcm = prioriteFcm;
		}

	}

	/**
	 * Types de message, avec la priorité et le routage par défaut du pilote (tableau §9). Hors tableau :
	 * OTP (code valable 5 min) en HAUTE ; DEMANDE_PRISE, RECEPTION_ECART, CONTESTATION en MOYENNE.
	 */
	public enum TypeMessage {

		OTP(Priorite.HAUTE, Canal.SMS),
		RUPTURE_SIGNALEE(Priorite.HAUTE, Canal.PUSH, Canal.SMS),
		DEMANDE_ACCEPTEE(Priorite.HAUTE, Canal.SMS),
		DEMANDE_PRISE(Priorite.MOYENNE, Canal.PUSH),
		DEMANDE_EN_ROUTE(Priorite.HAUTE, Canal.SMS),
		DEMANDE_LIVREE(Priorite.HAUTE, Canal.SMS),
		DEMANDE_SANS_REPONSE(Priorite.MOYENNE, Canal.SMS),
		PROMO_CONFIRMEE(Priorite.HAUTE, Canal.PUSH, Canal.SMS),
		RESERVATION_EXPIRE_BIENTOT(Priorite.HAUTE, Canal.PUSH),
		PROPOSITION_DEPOT(Priorite.HAUTE, Canal.PUSH, Canal.SMS),
		RETRAIT_EFFECTUE(Priorite.MOYENNE, Canal.PUSH),
		DOCUMENT_EMIS(Priorite.MOYENNE, Canal.SMS),
		RECEPTION_ECART(Priorite.MOYENNE, Canal.PUSH),
		CONTESTATION(Priorite.MOYENNE, Canal.PUSH, Canal.SMS);

		final Priorite priorite;
		final List<Canal> route;

		TypeMessage(Priorite priorite, Canal... route) {
			this.priorite = priorite;
			this.route = List.of(route);
		}

	}

	/** Coordonnées d'un utilisateur, résolues par le module comptes ; organisation absente = numéro sans compte. */
	public record Destinataire(UUID utilisateurId, UUID organisationId, String telephone, List<String> jetonsPush,
			String langue) {
	}

	/** Message prêt à partir, dans la langue du destinataire : texte complet (SMS), titre (push) ; {@code cibleId} = objet à ouvrir. */
	public record Message(TypeMessage type, UUID cibleId, Destinataire destinataire, String titre, String texte) {
	}

	/** Jeton push refusé définitivement par le fournisseur : le module comptes l'efface de l'appareil. */
	public record JetonPushInvalide(String jeton) {
	}

	/** Fournisseur d'un canal (opérateur SMS, FCM, WhatsApp Business API, ou journal applicatif). */
	public interface Fournisseur {

		enum Code {
			JOURNAL, INFOBIP, FCM
		}

		Canal canal();

		Code code();

		/** Vrai si les identifiants du fournisseur sont présents sur ce serveur. */
		default boolean configure() {
			return true;
		}

		/** Vrai si le message est accepté par le fournisseur. */
		boolean envoyer(Message message);

	}

	private final List<Fournisseur> fournisseurs;
	private final ParametresCanaux parametres;
	private final JdbcClient jdbc;
	private final Map<String, Properties> textes;

	Canaux(List<Fournisseur> fournisseurs, ParametresCanaux parametres, JdbcClient jdbc) {
		this.fournisseurs = fournisseurs;
		this.parametres = parametres;
		this.jdbc = jdbc;
		this.textes = Arrays.stream(new String[] { "fr", "ar", "ary-Arab", "ary-Latn" })
			.collect(Collectors.toMap(Function.identity(), Canaux::charger));
	}

	/**
	 * Envoie par le canal principal, puis par le canal de repli si le principal échoue, est coupé ou
	 * si le destinataire n'y est pas joignable. {@code cibleId} : objet que l'app ouvre (push). Renvoie
	 * le canal qui a réussi.
	 */
	public Optional<Canal> envoyer(TypeMessage type, UUID cibleId, Destinataire destinataire, Object... arguments) {
		var config = parametres.effective();
		var routage = config.routage(type);
		var langue = langue(destinataire.langue());
		var titre = textes.get(langue).getProperty("TITRE", "Daba");
		for (var canal : routage.canaux()) {
			if (!config.actif(canal, type, destinataire.organisationId()) || !joignable(canal, destinataire)) {
				continue;
			}
			var message = new Message(type, cibleId, destinataire, titre, texte(type, canal, langue, arguments));
			var code = config.fournisseur(canal);
			var fournisseur = fournisseurs.stream().filter(f -> f.canal() == canal && f.code() == code).findFirst();
			boolean ok;
			if (fournisseur.isEmpty() || !fournisseur.get().configure()) {
				log.warn("Fournisseur {} du canal {} non configuré : {} non envoyé par ce canal", code, canal, type);
				ok = false;
			}
			else {
				try {
					ok = fournisseur.get().envoyer(message);
				}
				catch (RuntimeException e) {
					log.warn("Échec d'envoi {} par {} ({}) : {}", type, canal, code, e.getClass().getSimpleName());
					ok = false;
				}
			}
			jdbc.sql("INSERT INTO notification (type, utilisateur_id, canal, envoyee) VALUES (:type, :u, :canal, :ok)")
				.param("type", type.name())
				.param("u", destinataire.utilisateurId())
				.param("canal", canal.name())
				.param("ok", ok)
				.update();
			if (ok) {
				return Optional.of(canal);
			}
		}
		return Optional.empty();
	}

	private static boolean joignable(Canal canal, Destinataire d) {
		return canal == Canal.PUSH ? d.jetonsPush() != null && !d.jetonsPush().isEmpty() : d.telephone() != null;
	}

	private String langue(String langue) {
		return langue == null || !textes.containsKey(langue) ? "fr" : langue;
	}

	String texte(TypeMessage type, String langue, Object... arguments) {
		return texte(type, null, langue, arguments);
	}

	/**
	 * Texte dans la langue du destinataire (fr par défaut) ; un argument libellé multilingue (Map) est lu
	 * dans cette langue. PUSH : variante {@code <TYPE>.PUSH} si elle existe, sans nom ni téléphone ni lien
	 * (écran verrouillé, CNDP), préférée en français plutôt que le texte nominatif dans la langue.
	 */
	String texte(TypeMessage type, Canal canal, String langue, Object... arguments) {
		var cle = langue(langue);
		var fr = textes.get("fr");
		var modele = textes.get(cle).getProperty(type.name(), fr.getProperty(type.name(), type.name()));
		if (canal == Canal.PUSH) {
			modele = textes.get(cle).getProperty(type.name() + ".PUSH", fr.getProperty(type.name() + ".PUSH", modele));
		}
		var traduits = Arrays.stream(arguments)
			.map(a -> a instanceof Map<?, ?> libelle ? String.valueOf(libelle.get(libelle.containsKey(cle) ? cle : "fr")) : a)
			.toArray();
		return new MessageFormat(modele, Locale.ROOT).format(traduits);
	}

	/** Texte sans le préfixe « Daba : », déjà porté par le titre d'un push. */
	static String corps(String texte, String titre) {
		return texte.startsWith(titre) ? texte.substring(titre.length()).replaceFirst("^\\s*:\\s*", "") : texte;
	}

	private static Properties charger(String langue) {
		var proprietes = new Properties();
		var ressource = new ClassPathResource("canaux/messages_" + langue + ".properties");
		if (ressource.exists()) {
			try (var lecteur = new InputStreamReader(ressource.getInputStream(), StandardCharsets.UTF_8)) {
				proprietes.load(lecteur);
			}
			catch (IOException e) {
				throw new IllegalStateException(e);
			}
		}
		return proprietes;
	}

}

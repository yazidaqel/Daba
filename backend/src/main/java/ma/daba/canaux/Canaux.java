package ma.daba.canaux;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Couche canaux unique (§9) : chaque type de message a un canal principal et un canal de repli,
 * modifiables par configuration ({@code daba.canaux.routage.<TYPE>=PRINCIPAL,REPLI}) ; chaque canal
 * s'active ou se coupe par {@code daba.canaux.actifs}, sans développement.
 */
@Service
public class Canaux {

	private static final Logger log = LoggerFactory.getLogger(Canaux.class);

	public enum Canal {
		PUSH, SMS, WHATSAPP
	}

	/** Types de message et routage par défaut du pilote (tableau §9). */
	public enum TypeMessage {

		OTP(Canal.SMS),
		RUPTURE_SIGNALEE(Canal.PUSH, Canal.SMS),
		DEMANDE_ACCEPTEE(Canal.SMS),
		DEMANDE_PRISE(Canal.PUSH),
		DEMANDE_EN_ROUTE(Canal.SMS),
		DEMANDE_LIVREE(Canal.SMS),
		DEMANDE_SANS_REPONSE(Canal.SMS),
		PROMO_CONFIRMEE(Canal.PUSH, Canal.SMS),
		RESERVATION_EXPIRE_BIENTOT(Canal.PUSH),
		PROPOSITION_DEPOT(Canal.PUSH, Canal.SMS),
		RETRAIT_EFFECTUE(Canal.PUSH),
		DOCUMENT_EMIS(Canal.SMS);

		private final List<Canal> route;

		TypeMessage(Canal... route) {
			this.route = List.of(route);
		}

	}

	/** Coordonnées d'un utilisateur, résolues par le module comptes. */
	public record Destinataire(UUID utilisateurId, String telephone, List<String> jetonsPush, String langue) {
	}

	/** Fournisseur d'un canal (opérateur SMS, FCM, WhatsApp Business API). */
	public interface Fournisseur {

		Canal canal();

		/** Vrai si le message est accepté par le fournisseur. */
		boolean envoyer(Destinataire destinataire, String texte);

	}

	private final Map<Canal, Fournisseur> fournisseurs;
	private final Set<Canal> actifs;
	private final Environment env;
	private final JdbcClient jdbc;
	private final Map<String, Properties> textes;

	Canaux(List<Fournisseur> fournisseurs, @Value("${daba.canaux.actifs}") Set<Canal> actifs, Environment env,
			JdbcClient jdbc) {
		this.fournisseurs = new EnumMap<>(fournisseurs.stream().collect(Collectors.toMap(Fournisseur::canal, Function.identity())));
		this.actifs = actifs;
		this.env = env;
		this.jdbc = jdbc;
		this.textes = Arrays.stream(new String[] { "fr", "ar", "ary-Arab", "ary-Latn" })
			.collect(Collectors.toMap(Function.identity(), Canaux::charger));
	}

	/**
	 * Envoie par le canal principal, puis par le canal de repli en cas d'échec ou si le destinataire
	 * n'y est pas joignable. Renvoie le canal qui a réussi.
	 */
	public Optional<Canal> envoyer(TypeMessage type, Destinataire destinataire, Object... arguments) {
		var texte = texte(type, destinataire.langue(), arguments);
		for (var canal : route(type)) {
			if (!actifs.contains(canal) || !joignable(canal, destinataire)) {
				continue;
			}
			boolean ok;
			try {
				ok = fournisseurs.get(canal).envoyer(destinataire, texte);
			}
			catch (RuntimeException e) {
				log.warn("Échec d'envoi {} par {}", type, canal, e);
				ok = false;
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

	List<Canal> route(TypeMessage type) {
		var configure = env.getProperty("daba.canaux.routage." + type.name());
		return configure == null ? type.route
				: Arrays.stream(configure.split(",")).map(String::trim).map(Canal::valueOf).toList();
	}

	private boolean joignable(Canal canal, Destinataire d) {
		return fournisseurs.containsKey(canal)
				&& (canal == Canal.PUSH ? d.jetonsPush() != null && !d.jetonsPush().isEmpty() : d.telephone() != null);
	}

	/** Texte dans la langue du destinataire (fr par défaut) ; un argument libellé multilingue (Map) est lu dans cette langue. */
	String texte(TypeMessage type, String langue, Object... arguments) {
		var cle = langue == null || !textes.containsKey(langue) ? "fr" : langue;
		var modele = textes.get(cle).getProperty(type.name(), textes.get("fr").getProperty(type.name(), type.name()));
		var traduits = Arrays.stream(arguments)
			.map(a -> a instanceof Map<?, ?> libelle ? String.valueOf(libelle.get(libelle.containsKey(cle) ? cle : "fr")) : a)
			.toArray();
		return new MessageFormat(modele, Locale.ROOT).format(traduits);
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

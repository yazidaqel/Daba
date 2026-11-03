package ma.daba.canaux;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import ma.daba.canaux.Canaux.Canal;
import ma.daba.canaux.Canaux.Fournisseur;
import ma.daba.canaux.Canaux.TypeMessage;
import ma.daba.commun.Acces;
import ma.daba.commun.Appelant;
import ma.daba.commun.Audit;
import ma.daba.commun.Json;
import ma.daba.commun.Probleme;

/**
 * Configuration des canaux choisie par l'administrateur (§9, « sans développement »), en base, sur
 * les valeurs par défaut du serveur : {@code daba.canaux.actifs}, {@code daba.canaux.routage.<TYPE>}
 * (sinon le tableau §9) et, par canal, le vrai fournisseur s'il est configuré, sinon JOURNAL.
 * Aucun secret ici : les identifiants des fournisseurs restent dans l'environnement du serveur.
 */
@Component
class ParametresCanaux {

	record Configuration(@NotNull List<@Valid @NotNull ParametreCanal> canaux,
			@NotNull List<@Valid @NotNull RoutageMessage> types,
			@NotNull List<@Valid @NotNull CanauxOrganisation> organisations) {

		RoutageMessage routage(TypeMessage type) {
			return types.stream().filter(t -> t.type() == type).findFirst().orElseThrow();
		}

		Fournisseur.Code fournisseur(Canal canal) {
			return parametre(canal).fournisseur();
		}

		/**
		 * Canal ouvert au niveau du système, du type de message et de l'organisation du destinataire.
		 * L'OTP ignore le niveau organisation : sinon les membres de l'organisation ne pourraient plus se connecter.
		 */
		boolean actif(Canal canal, TypeMessage type, UUID organisationId) {
			return parametre(canal).actif() && !routage(type).coupes().contains(canal)
					&& (type == TypeMessage.OTP || organisations.stream()
						.noneMatch(o -> o.organisationId().equals(organisationId) && o.inactifs().contains(canal)));
		}

		private ParametreCanal parametre(Canal canal) {
			return canaux.stream().filter(c -> c.canal() == canal).findFirst().orElseThrow();
		}

	}

	record ParametreCanal(@NotNull Canal canal, @NotNull Boolean actif, @NotNull Fournisseur.Code fournisseur,
			List<Disponible> fournisseursDisponibles) {
	}

	record Disponible(Fournisseur.Code code, boolean configure) {
	}

	record RoutageMessage(@NotNull TypeMessage type, @NotNull Canal principal, Canal repli, Set<@NotNull Canal> inactifs) {

		List<Canal> canaux() {
			return repli == null ? List.of(principal) : List.of(principal, repli);
		}

		Set<Canal> coupes() {
			return inactifs == null ? Set.of() : inactifs;
		}

	}

	record CanauxOrganisation(@NotNull UUID organisationId, @NotNull Set<@NotNull Canal> inactifs) {
	}

	private final List<Fournisseur> fournisseurs;
	private final Set<Canal> actifsParDefaut;
	private final Environment env;
	private final JdbcClient jdbc;
	private final Json json;
	private final Audit audit;
	private final Acces.Organisations organisationsExistantes;

	ParametresCanaux(List<Fournisseur> fournisseurs, @Value("${daba.canaux.actifs}") Set<Canal> actifsParDefaut,
			Environment env, JdbcClient jdbc, Json json, Audit audit, Acces.Organisations organisationsExistantes) {
		this.fournisseurs = fournisseurs;
		this.actifsParDefaut = actifsParDefaut;
		this.env = env;
		this.jdbc = jdbc;
		this.json = json;
		this.audit = audit;
		this.organisationsExistantes = organisationsExistantes;
	}

	/** Configuration enregistrée par l'administrateur, ou null. ponytail: relue à chaque envoi (une ligne), cache si le volume l'exige. */
	Configuration enregistree() {
		return jdbc.sql("SELECT configuration::text FROM configuration_canaux").query(String.class).optional()
			.map(c -> json.lire(c, Configuration.class)).orElse(null);
	}

	/** Configuration en vigueur : l'enregistrée, complétée par les valeurs par défaut pour tout le reste. */
	Configuration effective() {
		var enregistree = enregistree();
		var canaux = Arrays.stream(Canal.values()).map(canal -> {
			var p = enregistree == null ? null
					: enregistree.canaux().stream().filter(c -> c.canal() == canal).findFirst().orElse(null);
			return new ParametreCanal(canal, p != null ? p.actif() : actifsParDefaut.contains(canal),
					p != null ? p.fournisseur() : fournisseurParDefaut(canal), disponibles(canal));
		}).toList();
		var types = Arrays.stream(TypeMessage.values())
			.map(type -> (enregistree == null ? Stream.<RoutageMessage>empty() : enregistree.types().stream())
				.filter(t -> t.type() == type).findFirst().orElseGet(() -> routageParDefaut(type)))
			.toList();
		return new Configuration(canaux, types, enregistree == null ? List.of() : enregistree.organisations());
	}

	/** Remplace la configuration enregistrée (opération sensible, journal d'audit §10). */
	@Transactional
	void enregistrer(Appelant appelant, Configuration saisie) {
		uniques(saisie.canaux(), ParametreCanal::canal, "canal");
		uniques(saisie.types(), RoutageMessage::type, "type de message");
		uniques(saisie.organisations(), CanauxOrganisation::organisationId, "organisation");
		for (var c : saisie.canaux()) {
			var fournisseur = fournisseurs.stream().filter(f -> f.canal() == c.canal() && f.code() == c.fournisseur())
				.findFirst()
				.orElseThrow(() -> Probleme.requete("Fournisseur " + c.fournisseur() + " indisponible pour le canal " + c.canal()));
			if (!fournisseur.configure()) {
				throw new Probleme(HttpStatus.UNPROCESSABLE_CONTENT, "fournisseur-non-configure", null,
						"Identifiants de " + c.fournisseur() + " absents de la configuration du serveur");
			}
			// JOURNAL écrit les messages dans les logs : interdit dès qu'un vrai opérateur existe pour ce canal.
			if (c.fournisseur() == Fournisseur.Code.JOURNAL && reelConfigure(c.canal())) {
				throw new Probleme(HttpStatus.UNPROCESSABLE_CONTENT, "journal-interdit", null,
						"JOURNAL ne peut pas être choisi pour " + c.canal() + " : un vrai fournisseur est configuré");
			}
			if (c.canal() == Canal.SMS && !c.actif()) {
				throw Probleme.requete("Le SMS ne peut pas être coupé : il porte les codes OTP de connexion");
			}
		}
		for (var t : saisie.types()) {
			if (t.principal() == t.repli()) {
				throw Probleme.requete("Canal de repli identique au canal principal pour " + t.type());
			}
			if (t.type() == TypeMessage.OTP && (t.principal() != Canal.SMS || t.repli() != null || t.coupes().contains(Canal.SMS))) {
				throw Probleme.requete("L'OTP part seulement par SMS, sans repli et sans coupure (code sur l'écran verrouillé sinon)");
			}
		}
		var inconnues = organisationsExistantes
			.inconnues(saisie.organisations().stream().map(CanauxOrganisation::organisationId).toList());
		if (!inconnues.isEmpty()) {
			throw Probleme.requete("Organisations inconnues : " + inconnues);
		}
		var configuration = new Configuration(
				saisie.canaux().stream().map(c -> new ParametreCanal(c.canal(), c.actif(), c.fournisseur(), null)).toList(),
				saisie.types(), saisie.organisations());
		var texte = json.ecrire(configuration);
		jdbc.sql("""
				INSERT INTO configuration_canaux (configuration, modifiee_par) VALUES (:c::jsonb, :u)
				ON CONFLICT (id) DO UPDATE SET configuration = excluded.configuration, modifiee_par = excluded.modifiee_par,
				  modifiee_le = now()
				""").param("c", texte).param("u", appelant.utilisateurId()).update();
		audit.consigner(appelant, "CONFIGURATION_CANAUX", configuration);
	}

	private boolean reelConfigure(Canal canal) {
		return fournisseurs.stream().anyMatch(f -> f.canal() == canal && f.code() != Fournisseur.Code.JOURNAL && f.configure());
	}

	private Fournisseur.Code fournisseurParDefaut(Canal canal) {
		return fournisseurs.stream().filter(f -> f.canal() == canal && f.code() != Fournisseur.Code.JOURNAL && f.configure())
			.map(Fournisseur::code).findFirst().orElse(Fournisseur.Code.JOURNAL);
	}

	private List<Disponible> disponibles(Canal canal) {
		return fournisseurs.stream().filter(f -> f.canal() == canal).map(f -> new Disponible(f.code(), f.configure()))
			.sorted(java.util.Comparator.comparing(Disponible::code)).toList();
	}

	private RoutageMessage routageParDefaut(TypeMessage type) {
		var configure = env.getProperty("daba.canaux.routage." + type.name());
		var route = configure == null ? type.route
				: Arrays.stream(configure.split(",")).map(String::trim).map(Canal::valueOf).toList();
		return new RoutageMessage(type, route.getFirst(), route.size() > 1 ? route.get(1) : null, Set.of());
	}

	private static <T, K> void uniques(List<T> elements, Function<T, K> cle, String nom) {
		var vus = new HashSet<K>();
		var doublons = elements.stream().map(cle).filter(k -> !vus.add(k)).collect(Collectors.toSet());
		if (!doublons.isEmpty()) {
			throw Probleme.requete("Plusieurs entrées pour le même " + nom + " : " + doublons);
		}
	}

}

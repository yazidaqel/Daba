package ma.daba.comptes;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import ma.daba.commun.Appelant;
import ma.daba.commun.Audit;
import ma.daba.commun.Json;
import ma.daba.commun.Probleme;

/**
 * Mots de passe des portails web (GROSSISTE, GRAND_CLIENT, ADMIN ; §10) : politique, empreinte
 * Argon2id, ralentissement après échecs, méthode de connexion choisie par l'administrateur. Jamais de
 * mot de passe en clair hors de la mémoire de la requête : ni en base, ni en journal, ni en réponse.
 */
@Service
class MotsDePasse {

	enum Methode {
		OTP(Set.of("SMS")), MOT_DE_PASSE(Set.of("MDP")), MOT_DE_PASSE_ET_OTP(Set.of("MDP", "SMS"));

		final Set<String> facteurs;

		Methode(Set<String> facteurs) {
			this.facteurs = facteurs;
		}

		/** Plus stricte : exige un facteur que {@code avant} n'exigeait pas (une session ouverte sans lui ne le prouve pas). */
		boolean plusStricteQue(Methode avant) {
			return !avant.facteurs.containsAll(facteurs);
		}
	}

	enum RolePortail {
		GROSSISTE, GRAND_CLIENT, ADMIN
	}

	record MethodeRole(@NotNull RolePortail role, @NotNull Methode methode) {
	}

	record MethodeOrganisation(@NotNull UUID organisationId, @NotNull Methode methode) {
	}

	/** {@code fermerSessions} : en écriture seulement, jamais enregistré. */
	record Configuration(@NotNull List<@Valid @NotNull MethodeRole> roles,
			@NotNull List<@Valid @NotNull MethodeOrganisation> organisations, Boolean fermerSessions) {

		/** Exception de l'organisation, sinon défaut du rôle ; toujours OTP hors des rôles de portail. */
		Methode methode(String role, UUID organisationId) {
			if (!ROLES_PORTAIL.contains(role)) {
				return Methode.OTP;
			}
			return organisations.stream().filter(o -> o.organisationId().equals(organisationId)).map(MethodeOrganisation::methode)
				.findFirst()
				.orElseGet(() -> roles.stream().filter(r -> r.role().name().equals(role)).map(MethodeRole::methode).findFirst()
					.orElse(Methode.OTP));
		}

	}

	/** NIST SP 800-63B rév. 4, mot de passe seul facteur ; même seuil pour MOT_DE_PASSE_ET_OTP (la méthode peut changer). */
	static final int LONGUEUR_MIN = 15;
	static final int LONGUEUR_MAX = 128;
	/** Échecs consécutifs sans attente ; ensuite chaque échec double l'attente, de ATTENTE_BASE à ATTENTE_MAX. */
	static final int ECHECS_LIBRES = 5;
	static final Duration ATTENTE_BASE = Duration.ofSeconds(30);
	static final Duration ATTENTE_MAX = Duration.ofMinutes(15);
	/** Compteur des appareils inconnus du compte (ou d'un numéro sans compte de portail). */
	static final UUID APPAREIL_INCONNU = new UUID(0, 0);
	static final Set<String> ROLES_PORTAIL = Arrays.stream(RolePortail.values()).map(Enum::name).collect(Collectors.toSet());
	/** Attente d'un calcul Argon2 libre avant de répondre 429. */
	private static final Duration ATTENTE_CALCUL = Duration.ofMillis(500);

	/**
	 * Argon2id au minimum OWASP (m = 19 MiB, t = 2, p = 1), sel de 16 octets, empreinte de 32 octets.
	 * Les paramètres sont dans chaque empreinte : les relever plus tard n'invalide pas les anciennes.
	 */
	private final Argon2PasswordEncoder encodeur = new Argon2PasswordEncoder(16, 32, 1, 19 * 1024, 2);
	/** Empreinte comparée quand le numéro n'a pas de mot de passe : même coût, pas d'énumération par le temps de réponse. */
	private final String leurre = encodeur.encode("leurre-" + UUID.randomUUID());
	/** Calculs Argon2 simultanés (19 MiB chacun) : au-delà, 429 pour tous, sans rien compter. */
	private final Semaphore calculs;
	private final Set<String> courants;
	private final JdbcClient jdbc;
	private final Json json;
	private final Audit audit;

	MotsDePasse(JdbcClient jdbc, Json json, Audit audit,
			@Value("${daba.mots-de-passe.calculs-simultanes:4}") int calculsSimultanes) {
		this.jdbc = jdbc;
		this.json = json;
		this.audit = audit;
		this.calculs = new Semaphore(calculsSimultanes, true);
		try (var flux = new ClassPathResource("mots-de-passe-courants.txt").getInputStream()) {
			courants = new String(flux.readAllBytes(), StandardCharsets.UTF_8).lines().map(String::strip)
				.filter(l -> !l.isEmpty() && !l.startsWith("#")).map(l -> l.toLowerCase(Locale.ROOT))
				.collect(Collectors.toUnmodifiableSet());
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	// ---------------------------------------------------------------- politique (NIST SP 800-63B)

	/** NFC avant comptage et hachage (NIST SP 800-63B) : un même mot de passe saisi au clavier arabe ou latin reste le même. */
	static String normaliser(String motDePasse) {
		return Normalizer.normalize(motDePasse, Normalizer.Form.NFC);
	}

	/** Longueur, numéro de téléphone, mots de passe courants ou triviaux ; aucune règle de composition. 422 sinon. */
	void valider(String motDePasse, String telephone) {
		var mdp = normaliser(motDePasse);
		var longueur = mdp.codePointCount(0, mdp.length());
		if (longueur < LONGUEUR_MIN) {
			throw refuse("trop-court", "Mot de passe trop court : " + LONGUEUR_MIN + " caractères au moins");
		}
		if (longueur > LONGUEUR_MAX) {
			throw refuse("trop-long", "Mot de passe trop long : " + LONGUEUR_MAX + " caractères au plus");
		}
		var chiffres = telephone.substring(4); // +212 6XXXXXXXX → 6XXXXXXXX
		var sansSeparateurs = mdp.replaceAll("[\\s+.\\-()]", "");
		if (Set.of(chiffres, "0" + chiffres, "212" + chiffres, "00212" + chiffres).contains(sansSeparateurs)) {
			throw refuse("telephone", "Le mot de passe ne peut pas être le numéro de téléphone");
		}
		if (courants.contains(mdp.toLowerCase(Locale.ROOT)) || trivial(mdp)) {
			throw refuse("courant", "Mot de passe trop courant : choisissez-en un autre");
		}
	}

	/** Un motif de 1 à 4 caractères répété (aaaa…, abab…), ou une suite de caractères consécutifs (abcd…, 9876…). */
	static boolean trivial(String mdp) {
		if (mdp.toLowerCase(Locale.ROOT).matches("(?s)(.{1,4})\\1+")) {
			return true;
		}
		var points = mdp.toLowerCase(Locale.ROOT).codePoints().toArray();
		var pas = points[1] - points[0];
		if (Math.abs(pas) != 1) {
			return false;
		}
		for (int i = 2; i < points.length; i++) {
			if (points[i] - points[i - 1] != pas) {
				return false;
			}
		}
		return true;
	}

	private static Probleme refuse(String raison, String detail) {
		return new Probleme(HttpStatus.UNPROCESSABLE_CONTENT, "mot-de-passe-" + raison, null, detail);
	}

	// ---------------------------------------------------------------- calcul Argon2, hors transaction

	/**
	 * Calcul Argon2 sous plafond global, jamais dans une transaction : il ne retient ni connexion à la
	 * base ni verrou pendant ses ~19 MiB et ses dizaines de millisecondes.
	 */
	private <T> T calculer(Supplier<T> calcul) {
		if (TransactionSynchronizationManager.isActualTransactionActive()) {
			throw new IllegalStateException("Calcul Argon2 dans une transaction : il retiendrait une connexion à la base");
		}
		boolean obtenu;
		try {
			obtenu = calculs.tryAcquire(ATTENTE_CALCUL.toMillis(), TimeUnit.MILLISECONDS);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			obtenu = false;
		}
		if (!obtenu) {
			throw Probleme.tropDeDemandes("Service occupé : réessayez dans un instant", Duration.ofSeconds(1));
		}
		try {
			return calcul.get();
		}
		finally {
			calculs.release();
		}
	}

	/** Plafond des calculs (les tests le saturent). */
	Semaphore calculs() {
		return calculs;
	}

	String hacher(String motDePasse) {
		return calculer(() -> encodeur.encode(normaliser(motDePasse)));
	}

	// ---------------------------------------------------------------- vérification et ralentissement

	/**
	 * Compte de portail dont c'est le mot de passe, ou vide (numéro inconnu, ambulant, hanout, sans mot
	 * de passe, mauvais mot de passe : même chemin, même calcul). L'essai est compté avant le calcul
	 * (des essais simultanés ne passent pas le seuil) puis effacé s'il réussit. Après ECHECS_LIBRES
	 * échecs, chaque essai attend (429 avant) une durée qui double à chaque échec, jusqu'à ATTENTE_MAX ;
	 * un mot de passe juste après l'attente passe toujours. Un appareil où le compte s'est déjà connecté
	 * a son propre compteur : un attaquant qui ralentit le numéro ne bloque pas le navigateur habituel.
	 */
	Optional<UUID> verifier(String telephone, String motDePasse, UUID appareilId) {
		return calculer(() -> {
			var appareil = connu(telephone, appareilId) ? appareilId : APPAREIL_INCONNU;
			var compte = jdbc.sql("""
					INSERT INTO tentative_mot_de_passe AS t (telephone, appareil, echecs, modifie_le) VALUES (:t, :a, 1, now())
					ON CONFLICT (telephone, appareil) DO UPDATE SET echecs = t.echecs + 1, modifie_le = now(),
					  prochain_essai = CASE WHEN t.echecs + 1 >= :libres THEN now() + least(:max * interval '1 second',
					    :base * interval '1 second' * power(2, least(t.echecs + 1 - :libres, 20))) END
					WHERE t.prochain_essai IS NULL OR t.prochain_essai <= now()
					RETURNING echecs
					""").param("t", telephone).param("a", appareil).param("libres", ECHECS_LIBRES)
				.param("max", ATTENTE_MAX.toSeconds()).param("base", ATTENTE_BASE.toSeconds()).query(Integer.class).optional();
			if (compte.isEmpty()) {
				var attente = jdbc.sql("""
						SELECT greatest(1, ceil(extract(epoch FROM prochain_essai - now())))::bigint FROM tentative_mot_de_passe
						WHERE telephone = :t AND appareil = :a
						""").param("t", telephone).param("a", appareil).query(Long.class).optional().orElse(1L);
				throw Probleme.tropDeDemandes("Trop d'essais : réessayez plus tard, ou réinitialisez par code SMS",
						Duration.ofSeconds(attente));
			}
			var titulaire = jdbc.sql("""
					SELECT id, mot_de_passe_hash FROM utilisateur
					WHERE telephone = :t AND role IN ('GROSSISTE', 'GRAND_CLIENT', 'ADMIN') AND mot_de_passe_hash IS NOT NULL
					""").param("t", telephone).query((rs, i) -> Map.entry(rs.getObject(1, UUID.class), rs.getString(2))).optional();
			var juste = encodeur.matches(normaliser(motDePasse), titulaire.map(Map.Entry::getValue).orElse(leurre))
					&& titulaire.isPresent();
			if (!juste) {
				return Optional.<UUID>empty();
			}
			jdbc.sql("DELETE FROM tentative_mot_de_passe WHERE telephone = :t AND appareil = :a").param("t", telephone)
				.param("a", appareil).update();
			return titulaire.map(Map.Entry::getKey);
		});
	}

	/** Appareil non révoqué où un compte de portail de ce numéro s'est déjà connecté (UUID tiré par le navigateur). */
	private boolean connu(String telephone, UUID appareilId) {
		return appareilId != null && jdbc.sql("""
				SELECT EXISTS (SELECT 1 FROM appareil_utilisateur au JOIN utilisateur u ON u.id = au.utilisateur_id
				  JOIN appareil a ON a.id = au.appareil_id
				  WHERE u.telephone = :t AND au.appareil_id = :a AND NOT a.revoque
				    AND u.role IN ('GROSSISTE', 'GRAND_CLIENT', 'ADMIN'))
				""").param("t", telephone).param("a", appareilId).query(Boolean.class).single();
	}

	/** Compteurs sans essai depuis 24 h (CNDP : un numéro tapé ne reste pas en base) ; l'attente maximale est de 15 min. */
	@Scheduled(cron = "0 17 * * * *")
	void purger() {
		jdbc.sql("DELETE FROM tentative_mot_de_passe WHERE modifie_le < now() - interval '24 hours'").update();
	}

	boolean defini(UUID utilisateurId) {
		return jdbc.sql("SELECT mot_de_passe_hash IS NOT NULL FROM utilisateur WHERE id = :u").param("u", utilisateurId)
			.query(Boolean.class).single();
	}

	/** Version du mot de passe, portée par le défi de MOT_DE_PASSE_ET_OTP : un changement le rend caduc. */
	long version(UUID utilisateurId) {
		return jdbc.sql("SELECT mot_de_passe_modifie_le FROM utilisateur WHERE id = :u").param("u", utilisateurId)
			.query((rs, i) -> rs.getTimestamp(1) == null ? 0L : rs.getTimestamp(1).getTime()).single();
	}

	/** Enregistre l'empreinte déjà calculée ({@link #hacher}) et remet à zéro les compteurs du numéro. */
	void enregistrerEmpreinte(UUID utilisateurId, String empreinte) {
		var telephone = jdbc.sql("""
				UPDATE utilisateur SET mot_de_passe_hash = :h, mot_de_passe_modifie_le = clock_timestamp() WHERE id = :u
				RETURNING telephone
				""").param("h", empreinte).param("u", utilisateurId).query(String.class).single();
		jdbc.sql("DELETE FROM tentative_mot_de_passe WHERE telephone = :t").param("t", telephone).update();
	}

	void effacer(UUID utilisateurId) {
		var telephone = jdbc.sql("""
				UPDATE utilisateur SET mot_de_passe_hash = NULL, mot_de_passe_modifie_le = clock_timestamp() WHERE id = :u
				RETURNING telephone
				""").param("u", utilisateurId).query(String.class).single();
		jdbc.sql("DELETE FROM tentative_mot_de_passe WHERE telephone = :t").param("t", telephone).update();
	}

	// ---------------------------------------------------------------- méthode de connexion

	Methode methode(UUID utilisateurId) {
		var compte = jdbc.sql("SELECT role, organisation_id FROM utilisateur WHERE id = :u").param("u", utilisateurId)
			.query((rs, i) -> Map.entry(rs.getString(1), rs.getObject(2, UUID.class))).single();
		return effective().methode(compte.getKey(), compte.getValue());
	}

	/** Les trois rôles toujours listés (OTP par défaut), puis les exceptions par organisation. */
	Configuration effective() {
		var enregistree = jdbc.sql("SELECT configuration::text FROM configuration_authentification").query(String.class).optional()
			.map(c -> json.lire(c, Configuration.class)).orElse(new Configuration(List.of(), List.of(), null));
		var roles = Arrays.stream(RolePortail.values())
			.map(r -> new MethodeRole(r, enregistree.methode(r.name(), null)))
			.toList();
		return new Configuration(roles, enregistree.organisations(), null);
	}

	/**
	 * Remplace la configuration. Avec {@code fermerSessions}, les sessions ouvertes des comptes dont la
	 * méthode devient plus stricte se ferment, sauf celle de l'administrateur qui enregistre.
	 */
	@Transactional
	void enregistrer(Appelant admin, Configuration saisie) {
		var roles = new HashSet<RolePortail>();
		if (!saisie.roles().stream().allMatch(r -> roles.add(r.role()))) {
			throw Probleme.requete("Plusieurs entrées pour le même rôle");
		}
		var ids = saisie.organisations().stream().map(MethodeOrganisation::organisationId).toList();
		if (new HashSet<>(ids).size() != ids.size()) {
			throw Probleme.requete("Plusieurs entrées pour la même organisation");
		}
		if (!ids.isEmpty()) {
			var types = jdbc.sql("SELECT id, type FROM organisation WHERE id IN (:ids)").param("ids", ids)
				.query((rs, i) -> Map.entry(rs.getObject(1, UUID.class), rs.getString(2))).list().stream()
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
			var inconnues = ids.stream().filter(id -> !types.containsKey(id)).toList();
			if (!inconnues.isEmpty()) {
				throw Probleme.requete("Organisations inconnues : " + inconnues);
			}
			var horsPortail = ids.stream().filter(id -> !Set.of("GROSSISTE", "GRAND_CLIENT").contains(types.get(id))).toList();
			if (!horsPortail.isEmpty()) {
				throw Probleme.requete("Exception possible seulement pour un grossiste ou un grand client : " + horsPortail);
			}
		}
		var avant = effective();
		var configuration = new Configuration(saisie.roles(), saisie.organisations(), null);
		jdbc.sql("""
				INSERT INTO configuration_authentification (configuration, modifiee_par) VALUES (:c::jsonb, :u)
				ON CONFLICT (id) DO UPDATE SET configuration = excluded.configuration, modifiee_par = excluded.modifiee_par,
				  modifiee_le = now()
				""").param("c", json.ecrire(configuration)).param("u", admin.utilisateurId()).update();
		List<UUID> fermes = List.of();
		if (Boolean.TRUE.equals(saisie.fermerSessions())) {
			// ponytail: une ligne par compte de portail (quelques milliers) ; requête SQL dédiée si le volume l'exige.
			fermes = jdbc.sql("SELECT id, role, organisation_id FROM utilisateur WHERE role IN ('GROSSISTE', 'GRAND_CLIENT', 'ADMIN')")
				.query((rs, i) -> {
					var role = rs.getString("role");
					var org = rs.getObject("organisation_id", UUID.class);
					return configuration.methode(role, org).plusStricteQue(avant.methode(role, org)) ? rs.getObject("id", UUID.class)
							: null;
				})
				.list().stream().filter(java.util.Objects::nonNull).toList();
			if (!fermes.isEmpty()) {
				jdbc.sql("""
						UPDATE appareil SET session = NULL, jeton_push = NULL
						WHERE utilisateur_id IN (:ids) AND session IS NOT NULL AND NOT (utilisateur_id = :admin AND id = :appareil)
						""").param("ids", fermes).param("admin", admin.utilisateurId()).param("appareil", admin.appareilId()).update();
			}
		}
		// Sans fermerSessions, aucune session n'est fermée : la méthode vaut à la connexion suivante.
		audit.consigner(admin, "CONFIGURATION_AUTHENTIFICATION", Map.of("roles", configuration.roles(),
				"organisations", configuration.organisations(), "fermerSessions", Boolean.TRUE.equals(saisie.fermerSessions()),
				"sessionsFermees", fermes));
	}

}

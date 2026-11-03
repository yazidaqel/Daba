package ma.daba.comptes;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import ma.daba.canaux.Canaux.Destinataire;
import ma.daba.commun.Acces;
import ma.daba.commun.Appelant;
import ma.daba.commun.Geste;
import ma.daba.commun.Geste.Conflit;
import ma.daba.commun.Geste.Effet;
import ma.daba.commun.Jetons;
import ma.daba.commun.Journal;
import ma.daba.commun.Json;
import ma.daba.commun.Probleme;
import ma.daba.referentiel.Referentiel;
import tools.jackson.core.type.TypeReference;

/** API publique des comptes : coordonnées des utilisateurs, liens signés, contrôles d'accès du compte. */
@Service
public class Comptes implements Acces.VerificationCompte {

	/** Consentement CNDP enregistré ; publié dans l'outbox pour couper la disponibilité si la géolocalisation est retirée. */
	public record ConsentementEnregistre(UUID utilisateurId, boolean traitementDonnees, boolean geolocalisation) {
	}

	public enum CibleLien {
		DOCUMENT, RELEVE, DEMANDE_DEPANNAGE
	}

	/** Profil saisi hors ligne (opération PROFIL_AMBULANT, §3.1) : remplace tout le profil. */
	public record ProfilSaisie(@NotBlank @Size(max = 100) String nom, @Pattern(regexp = "^[A-Z]{1,2}[0-9]{1,6}$") String cin,
			@Size(max = 100) String vehicule, @Size(max = 50) List<@NotBlank String> villes,
			@Size(max = 50) List<@NotBlank String> categories) {
	}

	public record Profil(String nom, String cin, String vehicule, List<String> villes, List<String> categories,
			Instant modifieLe) {
	}

	private static final TypeReference<List<String>> CODES = new TypeReference<>() {
	};

	private final JdbcClient jdbc;
	private final Jetons jetons;
	private final Json json;
	private final Journal journal;
	private final Referentiel referentiel;
	private final String baseLiens;

	Comptes(JdbcClient jdbc, Jetons jetons, Json json, Journal journal, Referentiel referentiel,
			@Value("${daba.liens.base-url}") String baseLiens) {
		this.jdbc = jdbc;
		this.jetons = jetons;
		this.json = json;
		this.journal = journal;
		this.referentiel = referentiel;
		this.baseLiens = baseLiens;
	}

	@Override
	public void verifier(Appelant appelant, boolean consentementExige) {
		record Etat(boolean revoque, boolean sessionOuverte, boolean consenti) {
		}
		var etat = jdbc.sql("""
				SELECT a.revoque, a.session IS NOT NULL AND a.session = :session AS ouverte,
				  (SELECT c.traitement_donnees FROM consentement c
				   WHERE c.utilisateur_id = a.utilisateur_id ORDER BY c.id DESC LIMIT 1) AS consenti
				FROM appareil a WHERE a.id = :appareil AND a.utilisateur_id = :u
				""")
			.param("appareil", appelant.appareilId()).param("u", appelant.utilisateurId()).param("session", appelant.sessionId())
			.query((rs, i) -> new Etat(rs.getBoolean("revoque"), rs.getBoolean("ouverte"),
					Boolean.TRUE.equals(rs.getObject("consenti"))))
			.optional();
		if (etat.isEmpty() || etat.get().revoque()) {
			throw Probleme.interdit("appareil-revoque", "Appareil déconnecté : reconnectez-vous");
		}
		if (!etat.get().sessionOuverte()) {
			throw Probleme.nonAuthentifie();
		}
		if (consentementExige && !etat.get().consenti() && !appelant.a(Appelant.Role.ADMIN)) {
			throw Probleme.interdit("consentement-requis", "Consentement CNDP requis (PUT /v1/moi/consentement)");
		}
	}

	/** Téléphone, jetons push des appareils actifs et langue du dernier appareil. */
	public Destinataire destinataire(UUID utilisateurId) {
		var telephone = jdbc.sql("SELECT telephone FROM utilisateur WHERE id = :u").param("u", utilisateurId)
			.query(String.class).optional().orElse(null);
		var appareils = jdbc.sql("""
				SELECT jeton_push, langue FROM appareil WHERE utilisateur_id = :u AND NOT revoque
				ORDER BY derniere_connexion DESC NULLS LAST
				""").param("u", utilisateurId)
			.query((rs, i) -> Map.entry(String.valueOf(rs.getString("jeton_push")), String.valueOf(rs.getString("langue"))))
			.list();
		var jetonsPush = appareils.stream().map(Map.Entry::getKey).filter(j -> !"null".equals(j)).toList();
		var langue = appareils.stream().map(Map.Entry::getValue).filter(l -> !"null".equals(l)).findFirst().orElse("fr");
		return new Destinataire(utilisateurId, telephone, jetonsPush, langue);
	}

	/** Destinataire d'un numéro qui n'a pas forcément de compte (OTP, SMS au hanout). */
	public Destinataire destinataireTelephone(String telephone, String langue) {
		return utilisateurParTelephone(telephone).map(this::destinataire)
			.orElse(new Destinataire(null, telephone, List.of(), langue == null ? "fr" : langue));
	}

	/**
	 * Nom affichable dans les listes (grossiste, hanout) : le nom saisi tant que le consentement CNDP
	 * est actif, sinon le téléphone masqué.
	 */
	public String nom(UUID utilisateurId) {
		return nom(utilisateurId, true);
	}

	/** Nom imprimé sur une pièce de vente à son émission (RG-C4) : le nom saisi, consentement ou non. */
	public String nomPourDocument(UUID utilisateurId) {
		return nom(utilisateurId, false);
	}

	private String nom(UUID utilisateurId, boolean selonConsentement) {
		return jdbc.sql("""
				SELECT u.nom, u.telephone, (SELECT c.traitement_donnees FROM consentement c
				  WHERE c.utilisateur_id = u.id ORDER BY c.id DESC LIMIT 1) AS consenti
				FROM utilisateur u WHERE u.id = :u
				""").param("u", utilisateurId)
			.query((rs, i) -> rs.getString("nom") != null && (!selonConsentement || rs.getBoolean("consenti"))
					? rs.getString("nom") : masquer(rs.getString("telephone")))
			.optional().orElse("—");
	}

	/** Vrai si le dernier consentement autorise le traitement des données (CNDP). */
	public boolean traitementConsenti(UUID utilisateurId) {
		return jdbc.sql("""
				SELECT coalesce((SELECT traitement_donnees FROM consentement
				                 WHERE utilisateur_id = :u ORDER BY id DESC LIMIT 1), false)
				""").param("u", utilisateurId).query(Boolean.class).single();
	}

	/**
	 * Profil de l'ambulant (§3.1) : son nom devient celui que voient ses grossistes et ses hanouts
	 * ({@link #nom}). Codes de ville ou de catégorie inconnus : REJETEE ; profil plus récent sur un
	 * autre appareil : conservé (pas d'écrasement silencieux).
	 */
	@Transactional
	public Effet profil(Geste geste, ProfilSaisie p) {
		var villes = p.villes() == null ? List.<String>of() : p.villes().stream().distinct().toList();
		var categories = p.categories() == null ? List.<String>of() : p.categories().stream().distinct().toList();
		var inconnus = new java.util.ArrayList<>(referentiel.villesInconnues(villes));
		inconnus.addAll(referentiel.categoriesInconnues(categories));
		if (!inconnus.isEmpty()) {
			throw Probleme.regle(null, "Codes inconnus du référentiel : " + inconnus);
		}
		var actuel = profil(geste.ambulantId());
		if (actuel.isPresent() && actuel.get().modifieLe().isAfter(geste.horodatage())) {
			return new Effet(List.of(), Conflit.versionServeur("Profil modifié plus récemment sur un autre appareil", actuel.get()));
		}
		jdbc.sql("UPDATE utilisateur SET nom = :nom WHERE id = :u").param("nom", p.nom().trim()).param("u", geste.ambulantId())
			.update();
		jdbc.sql("""
				INSERT INTO profil_ambulant (utilisateur_id, cin, vehicule, villes, categories, modifie_le_appareil)
				VALUES (:u, :cin, :vehicule, :villes::jsonb, :categories::jsonb, :le)
				ON CONFLICT (utilisateur_id) DO UPDATE SET cin = excluded.cin, vehicule = excluded.vehicule,
				  villes = excluded.villes, categories = excluded.categories, modifie_le_appareil = excluded.modifie_le_appareil
				""").param("u", geste.ambulantId()).param("cin", p.cin()).param("vehicule", p.vehicule())
			.param("villes", json.ecrire(villes)).param("categories", json.ecrire(categories))
			.param("le", Timestamp.from(geste.horodatage())).update();
		journal.publier(geste.ambulantId(), "PROFIL", geste.ambulantId(), profil(geste.ambulantId()).orElseThrow());
		return Effet.ok();
	}

	public Optional<Profil> profil(UUID ambulantId) {
		return jdbc.sql("""
				SELECT u.nom, p.cin, p.vehicule, p.villes::text AS villes, p.categories::text AS categories, p.modifie_le_appareil
				FROM profil_ambulant p JOIN utilisateur u ON u.id = p.utilisateur_id WHERE p.utilisateur_id = :u
				""").param("u", ambulantId)
			.query((rs, i) -> new Profil(rs.getString("nom"), rs.getString("cin"), rs.getString("vehicule"),
					json.lire(rs.getString("villes"), CODES), json.lire(rs.getString("categories"), CODES),
					rs.getTimestamp("modifie_le_appareil").toInstant()))
			.optional();
	}

	/** Catégories vendues déclarées au profil (ciblage d'une promo, §9) ; absent = aucune déclarée. */
	public Map<UUID, List<String>> categoriesVendues(Collection<UUID> ambulants) {
		var categories = new HashMap<UUID, List<String>>();
		if (!ambulants.isEmpty()) {
			jdbc.sql("SELECT utilisateur_id, categories::text FROM profil_ambulant WHERE utilisateur_id IN (:a)")
				.param("a", ambulants)
				.query(rs -> {
					categories.put(rs.getObject(1, UUID.class), json.lire(rs.getString(2), CODES));
				});
		}
		return categories;
	}

	/** Préfixe de numérotation attribué au couple (ambulant, appareil) (RG-F1). */
	public Optional<String> prefixe(UUID ambulantId, UUID appareilId) {
		return jdbc.sql("SELECT prefixe FROM prefixe_numerotation WHERE utilisateur_id = :u AND appareil_id = :a")
			.param("u", ambulantId).param("a", appareilId).query(String.class).optional();
	}

	public String nomOrganisation(UUID organisationId) {
		return jdbc.sql("SELECT nom FROM organisation WHERE id = :o").param("o", organisationId).query(String.class)
			.optional().orElse("—");
	}

	public Optional<String> telephone(UUID utilisateurId) {
		return jdbc.sql("SELECT telephone FROM utilisateur WHERE id = :u").param("u", utilisateurId).query(String.class).optional();
	}

	public Optional<UUID> utilisateurParTelephone(String telephone) {
		return jdbc.sql("SELECT id FROM utilisateur WHERE telephone = :t").param("t", telephone).query(UUID.class).optional();
	}

	/** Ambulant inscrit à ce numéro. */
	public Optional<UUID> ambulantParTelephone(String telephone) {
		return jdbc.sql("SELECT id FROM utilisateur WHERE telephone = :t AND role = 'AMBULANT'").param("t", telephone)
			.query(UUID.class).optional();
	}

	/** Organisation d'un utilisateur (cloisonnement des pièces d'un ambulant). */
	public Optional<UUID> organisation(UUID utilisateurId) {
		return jdbc.sql("SELECT organisation_id FROM utilisateur WHERE id = :u").param("u", utilisateurId)
			.query(UUID.class).optional();
	}

	public List<UUID> ambulants() {
		return jdbc.sql("SELECT id FROM utilisateur WHERE role = 'AMBULANT'").query(UUID.class).list();
	}

	/** Membres d'une organisation (grossiste, grand client) à notifier. */
	public List<UUID> membres(UUID organisationId) {
		return jdbc.sql("SELECT id FROM utilisateur WHERE organisation_id = :o").param("o", organisationId)
			.query(UUID.class).list();
	}

	/** Utilisateur hanout lié à une fiche hanout, s'il s'est déjà connecté. */
	public Optional<UUID> utilisateurHanout(UUID hanoutId) {
		return jdbc.sql("SELECT id FROM utilisateur WHERE hanout_id = :h LIMIT 1").param("h", hanoutId).query(UUID.class).optional();
	}

	/** Vrai si le dernier consentement autorise la géolocalisation (CNDP). */
	public boolean geolocalisationConsentie(UUID utilisateurId) {
		return jdbc.sql("""
				SELECT coalesce((SELECT traitement_donnees AND coalesce(geolocalisation, false) FROM consentement
				                 WHERE utilisateur_id = :u ORDER BY id DESC LIMIT 1), false)
				""").param("u", utilisateurId).query(Boolean.class).single();
	}

	/** Lien hanout signé et à durée limitée (§10), à envoyer par SMS. */
	public String lien(CibleLien cible, UUID cibleId, String telephone, Duration duree) {
		return baseLiens + jetons.signer("lien", Map.of("cible", cible.name(), "cibleId", cibleId.toString(), "tel", telephone), duree);
	}

	static String masquer(String telephone) {
		return telephone == null || telephone.length() < 8 ? "•••"
				: telephone.substring(0, 5) + "•".repeat(telephone.length() - 8) + telephone.substring(telephone.length() - 3);
	}

}

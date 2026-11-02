package ma.daba.comptes;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import ma.daba.canaux.Canaux.Destinataire;
import ma.daba.commun.Acces;
import ma.daba.commun.Appelant;
import ma.daba.commun.Jetons;
import ma.daba.commun.Probleme;

/** API publique des comptes : coordonnées des utilisateurs, liens signés, contrôles d'accès du compte. */
@Service
public class Comptes implements Acces.VerificationCompte {

	/** Consentement CNDP enregistré ; publié dans l'outbox pour couper la disponibilité si la géolocalisation est retirée. */
	public record ConsentementEnregistre(UUID utilisateurId, boolean traitementDonnees, boolean geolocalisation) {
	}

	public enum CibleLien {
		DOCUMENT, RELEVE, DEMANDE_DEPANNAGE
	}

	private final JdbcClient jdbc;
	private final Jetons jetons;
	private final String baseLiens;

	Comptes(JdbcClient jdbc, Jetons jetons, @Value("${daba.liens.base-url}") String baseLiens) {
		this.jdbc = jdbc;
		this.jetons = jetons;
		this.baseLiens = baseLiens;
	}

	@Override
	public void verifier(Appelant appelant, boolean consentementExige) {
		var etat = jdbc.sql("""
				SELECT a.revoque, (SELECT c.traitement_donnees FROM consentement c
				                   WHERE c.utilisateur_id = a.utilisateur_id ORDER BY c.id DESC LIMIT 1) AS consenti
				FROM appareil a WHERE a.id = :appareil AND a.utilisateur_id = :u
				""")
			.param("appareil", appelant.appareilId()).param("u", appelant.utilisateurId())
			.query((rs, i) -> Map.entry(rs.getBoolean("revoque"), Boolean.TRUE.equals(rs.getObject("consenti"))))
			.optional();
		if (etat.isEmpty() || etat.get().getKey()) {
			throw Probleme.interdit("appareil-revoque", "Appareil déconnecté : reconnectez-vous");
		}
		if (consentementExige && !etat.get().getValue() && !appelant.a(Appelant.Role.ADMIN)) {
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

	/** Nom affichable : le nom saisi, sinon le téléphone masqué. */
	public String nom(UUID utilisateurId) {
		return jdbc.sql("SELECT nom, telephone FROM utilisateur WHERE id = :u").param("u", utilisateurId)
			.query((rs, i) -> rs.getString("nom") != null ? rs.getString("nom") : masquer(rs.getString("telephone")))
			.optional().orElse("—");
	}

	/** Préfixe de numérotation attribué à l'appareil d'un ambulant (RG-F1). */
	public Optional<String> prefixe(UUID appareilId) {
		return jdbc.sql("SELECT prefixe FROM appareil WHERE id = :a").param("a", appareilId).query(String.class).optional();
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

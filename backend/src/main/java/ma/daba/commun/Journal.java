package ma.daba.commun;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.JsonNode;

/**
 * Journal des changements tirés par l'app ambulant ({@code GET /v1/sync/changements}).
 * Une ligne par objet et destinataire (null = tous les ambulants), dont le numéro de séquence
 * avance à chaque modification : la synchronisation différentielle lit les séquences après le curseur.
 */
@Component
public class Journal {

	private static final Object CLE_TRANSACTION = new Object();

	private final JdbcClient jdbc;
	private final Json json;
	private final TransactionTemplate transactions;

	Journal(JdbcClient jdbc, Json json, TransactionTemplate transactions) {
		this.jdbc = jdbc;
		this.json = json;
		this.transactions = transactions;
	}

	public record Changement(String entite, UUID id, String action, JsonNode donnees) {
	}

	public record Lot(String curseur, boolean plusDeChangements, List<Changement> changements) {
	}

	private record Entree(UUID destinataire, String entite, UUID id, String action, String donnees) {
	}

	/** Publie l'état courant d'un objet ({@code UPSERT}) ; {@code destinataire} null = tous les ambulants. */
	public void publier(UUID destinataire, String entite, UUID id, Object donnees) {
		enregistrer(new Entree(destinataire, entite, id, "UPSERT", json.ecrire(donnees)));
	}

	/** Retire un objet des appareils (promo terminée…), jamais pour un document de vente. */
	public void supprimer(UUID destinataire, String entite, UUID id) {
		enregistrer(new Entree(destinataire, entite, id, "SUPPRESSION", null));
	}

	/** Destinataires qui ont l'objet sur leur appareil (dernier état UPSERT, déjà écrit) ; null = tous les ambulants. */
	public List<UUID> destinataires(String entite, UUID id) {
		return jdbc.sql("SELECT destinataire FROM changement WHERE entite = :e AND entite_id = :id AND action = 'UPSERT'")
			.param("e", entite).param("id", id).query(UUID.class).list();
	}

	/**
	 * Les entrées d'une transaction sont écrites juste avant son commit, sous un verrou global :
	 * l'ordre des séquences est alors celui des commits, et aucun curseur ne saute une ligne.
	 * ponytail: verrou global, à remplacer par un filigrane xid si le débit d'écriture l'exige.
	 */
	@SuppressWarnings("unchecked")
	private void enregistrer(Entree entree) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			transactions.executeWithoutResult(s -> ecrire(List.of(entree)));
			return;
		}
		var entrees = (Map<String, Entree>) TransactionSynchronizationManager.getResource(CLE_TRANSACTION);
		if (entrees == null) {
			var nouvelles = new LinkedHashMap<String, Entree>();
			TransactionSynchronizationManager.bindResource(CLE_TRANSACTION, nouvelles);
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void beforeCommit(boolean readOnly) {
					ecrire(List.copyOf(nouvelles.values()));
				}

				@Override
				public void afterCompletion(int status) {
					TransactionSynchronizationManager.unbindResourceIfPossible(CLE_TRANSACTION);
				}
			});
			entrees = nouvelles;
		}
		entrees.remove(entree.entite + entree.id + entree.destinataire);
		entrees.put(entree.entite + entree.id + entree.destinataire, entree);
	}

	private void ecrire(List<Entree> entrees) {
		if (entrees.isEmpty()) {
			return;
		}
		jdbc.sql("SELECT pg_advisory_xact_lock(7300)").query().listOfRows();
		for (var e : entrees) {
			jdbc.sql("""
					INSERT INTO changement (seq, destinataire, entite, entite_id, action, donnees, modifie_le)
					VALUES (nextval('changement_seq'), :dest, :entite, :id, :action, :donnees::jsonb, now())
					ON CONFLICT ON CONSTRAINT changement_objet
					DO UPDATE SET seq = excluded.seq, action = excluded.action, donnees = excluded.donnees, modifie_le = now()
					""")
				.param("dest", e.destinataire)
				.param("entite", e.entite)
				.param("id", e.id)
				.param("action", e.action)
				.param("donnees", e.donnees)
				.update();
		}
	}

	/** Changements visibles par {@code destinataire} après le curseur ; sans curseur, l'état complet. */
	public Lot lire(UUID destinataire, String curseur, int limite) {
		long depuis = 0;
		if (curseur != null) {
			try {
				depuis = Long.parseLong(new String(Base64.getUrlDecoder().decode(curseur), StandardCharsets.UTF_8));
			}
			catch (IllegalArgumentException e) {
				throw Probleme.requete("Curseur illisible");
			}
			long purge = jdbc.sql("SELECT coalesce(max(seq), 0) FROM changement_purge").query(Long.class).single();
			if (depuis < purge) {
				throw new Probleme(HttpStatus.GONE, "curseur-expire", null, "Curseur trop ancien : resynchronisation complète");
			}
		}
		var lignes = jdbc.sql("""
				SELECT seq, entite, entite_id, action, donnees::text AS donnees FROM changement
				WHERE (destinataire = :dest OR destinataire IS NULL) AND seq > :depuis
				  AND (:complet = false OR action = 'UPSERT')
				ORDER BY seq LIMIT :limite
				""")
			.param("dest", destinataire)
			.param("depuis", depuis)
			.param("complet", curseur == null)
			.param("limite", limite + 1)
			.query((rs, i) -> Map.entry(rs.getLong("seq"), new Changement(rs.getString("entite"),
					rs.getObject("entite_id", UUID.class), rs.getString("action"),
					rs.getString("donnees") == null ? null : json.mapper().readTree(rs.getString("donnees")))))
			.list();
		var plus = lignes.size() > limite;
		var page = plus ? lignes.subList(0, limite) : lignes;
		long dernier = page.isEmpty() ? depuis : page.getLast().getKey();
		var suivant = Base64.getUrlEncoder().withoutPadding()
			.encodeToString(Long.toString(dernier).getBytes(StandardCharsets.UTF_8));
		return new Lot(suivant, plus, page.stream().map(Map.Entry::getValue).toList());
	}

	/** Les suppressions de plus de 30 jours sont purgées ; un curseur antérieur reçoit 410. */
	@Scheduled(cron = "0 30 3 * * *")
	void purger() {
		jdbc.sql("""
				WITH supprimees AS (DELETE FROM changement
				  WHERE action = 'SUPPRESSION' AND modifie_le < now() - interval '30 days' RETURNING seq)
				INSERT INTO changement_purge (seq) SELECT max(seq) FROM supprimees HAVING max(seq) IS NOT NULL
				""").update();
	}

}

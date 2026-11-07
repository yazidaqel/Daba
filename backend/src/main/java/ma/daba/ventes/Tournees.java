package ma.daba.ventes;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import ma.daba.commun.Geste;
import ma.daba.commun.Geste.Conflit;
import ma.daba.commun.Geste.Effet;
import ma.daba.commun.Journal;
import ma.daba.commun.Json;
import ma.daba.commun.Probleme;
import ma.daba.referentiel.Referentiel;
import tools.jackson.core.type.TypeReference;

/**
 * Tournées planifiées et visites sans vente de l'ambulant (§3.3), et sa zone de tournée pour le
 * ciblage géographique des promos (§9).
 */
@Service
public class Tournees {

	/** Opération TOURNEE : remplace toute la tournée ; {@code supprimee} la retire des appareils. */
	public record TourneeSaisie(@NotNull UUID id, @Size(max = 100) String nom,
			@Size(max = 7) List<@NotNull @Min(1) @Max(7) Integer> jours, @Size(max = 200) List<@NotNull UUID> hanoutIds,
			Boolean supprimee) {
	}

	/** {@code supprimee} n'est présent (vrai) que dans la version serveur d'un conflit. */
	public record Tournee(UUID id, String nom, List<Integer> jours, List<UUID> hanoutIds, Boolean supprimee,
			Instant modifieLe) {
	}

	public record VisiteSaisie(@NotNull UUID hanoutId, @NotNull Instant horodatage,
			@NotNull @Pattern(regexp = "^(FERME|PAS_BESOIN|GERANT_ABSENT|AUTRE)$") String motif) {
	}

	public record Visite(UUID id, UUID hanoutId, Instant horodatage, String motif) {
	}

	private static final TypeReference<List<Integer>> JOURS = new TypeReference<>() {
	};

	private static final TypeReference<List<UUID>> IDS = new TypeReference<>() {
	};

	private final JdbcClient jdbc;
	private final Json json;
	private final Journal journal;
	private final Referentiel referentiel;

	Tournees(JdbcClient jdbc, Json json, Journal journal, Referentiel referentiel) {
		this.jdbc = jdbc;
		this.json = json;
		this.journal = journal;
		this.referentiel = referentiel;
	}

	/**
	 * Tournée remplacée en entier ; une version plus récente faite sur un autre appareil (même
	 * supprimée) est conservée (pas d'écrasement silencieux). Hanout inconnu : REJETEE.
	 */
	@Transactional
	public Effet tournee(Geste geste, TourneeSaisie t) {
		var supprimee = Boolean.TRUE.equals(t.supprimee());
		var jours = t.jours() == null ? List.<Integer>of() : t.jours();
		var hanouts = t.hanoutIds() == null ? List.<UUID>of() : t.hanoutIds();
		if (!supprimee && jours.isEmpty()) {
			throw Probleme.requete("jours : au moins un jour, sauf pour une tournée supprimée");
		}
		if (Set.copyOf(jours).size() != jours.size() || Set.copyOf(hanouts).size() != hanouts.size()) {
			throw Probleme.requete("jours et hanoutIds sans doublon");
		}
		var inconnus = referentiel.hanoutsInconnus(hanouts);
		if (!inconnus.isEmpty()) {
			throw Probleme.regle(null, "Hanouts inconnus : " + inconnus);
		}
		var actuelle = tournee(t.id());
		if (actuelle.isPresent()) {
			if (!actuelle.get().getKey().equals(geste.ambulantId())) {
				throw Probleme.requete("Identifiant de tournée déjà utilisé");
			}
			if (actuelle.get().getValue().modifieLe().isAfter(geste.horodatage())) {
				return new Effet(List.of(), Conflit.versionServeur("Tournée modifiée plus récemment sur un autre appareil",
						actuelle.get().getValue()));
			}
		}
		jdbc.sql("""
				INSERT INTO tournee (id, ambulant_id, nom, jours, hanouts, supprimee, modifie_le_appareil)
				VALUES (:id, :a, :nom, CAST(:jours AS jsonb), CAST(:hanouts AS jsonb), :supprimee, :le)
				ON CONFLICT (id) DO UPDATE SET nom = excluded.nom, jours = excluded.jours, hanouts = excluded.hanouts,
				  supprimee = excluded.supprimee, modifie_le_appareil = excluded.modifie_le_appareil
				""").param("id", t.id()).param("a", geste.ambulantId()).param("nom", t.nom()).param("jours", json.ecrire(jours))
			.param("hanouts", json.ecrire(hanouts)).param("supprimee", supprimee)
			.param("le", Timestamp.from(geste.horodatage())).update();
		if (supprimee) {
			journal.supprimer(geste.ambulantId(), "TOURNEE", t.id());
		}
		else {
			journal.publier(geste.ambulantId(), "TOURNEE", t.id(), tournee(t.id()).orElseThrow().getValue());
		}
		return Effet.ok();
	}

	/** Tournée et son ambulant. */
	private Optional<Map.Entry<UUID, Tournee>> tournee(UUID id) {
		return jdbc.sql("SELECT * FROM tournee WHERE id = :id").param("id", id)
			.query((rs, i) -> Map.entry(rs.getObject("ambulant_id", UUID.class),
					new Tournee(id, rs.getString("nom"), json.lire(rs.getString("jours"), JOURS),
							json.lire(rs.getString("hanouts"), IDS), rs.getBoolean("supprimee") ? true : null,
							rs.getTimestamp("modifie_le_appareil").toInstant())))
			.optional();
	}

	/** Visite sans vente (§3.3), identifiée par son opération ; tirée avec l'historique du hanout. */
	@Transactional
	public Effet visite(Geste geste, VisiteSaisie v) {
		referentiel.hanout(v.hanoutId()).orElseThrow(() -> Probleme.regle(null, "Hanout inconnu"));
		jdbc.sql("""
				INSERT INTO visite (id, ambulant_id, appareil_id, hanout_id, horodatage, motif)
				VALUES (:id, :a, :appareil, :h, :le, :motif) ON CONFLICT (id) DO NOTHING
				""").param("id", geste.operationId()).param("a", geste.ambulantId()).param("appareil", geste.appareilId())
			.param("h", v.hanoutId()).param("le", Timestamp.from(v.horodatage())).param("motif", v.motif()).update();
		journal.publier(geste.ambulantId(), "VISITE", geste.operationId(),
				new Visite(geste.operationId(), v.hanoutId(), v.horodatage(), v.motif()));
		return Effet.ok();
	}

	/**
	 * Zone de tournée de chaque ambulant (§9) : les hanouts de ses tournées, sinon ceux à qui il a
	 * vendu dans les 30 derniers jours ; absent de la carte s'il n'a ni l'un ni l'autre.
	 * ponytail: la fenêtre de 30 jours n'est réévaluée qu'au prochain geste de l'ambulant (vente,
	 * tournée, profil) ; un job quotidien de reevaluerCiblage si les offres hors ligne doivent la suivre.
	 */
	public Map<UUID, Set<UUID>> zonesDeTournee(Collection<UUID> ambulants) {
		var zones = new HashMap<UUID, Set<UUID>>();
		if (ambulants.isEmpty()) {
			return zones;
		}
		jdbc.sql("""
				WITH a AS (SELECT DISTINCT CAST(value AS uuid) AS id FROM jsonb_array_elements_text(CAST(:a AS jsonb))),
				t AS (SELECT t.ambulant_id, CAST(h.value AS uuid) AS hanout_id
				      FROM tournee t JOIN a ON a.id = t.ambulant_id, jsonb_array_elements_text(t.hanouts) h
				      WHERE NOT t.supprimee)
				SELECT ambulant_id, hanout_id FROM t
				UNION
				SELECT d.ambulant_id, d.hanout_id FROM document d JOIN a ON a.id = d.ambulant_id
				WHERE d.type <> 'AVOIR' AND d.emis_le > now() - interval '30 days'
				  AND NOT EXISTS (SELECT 1 FROM t WHERE t.ambulant_id = d.ambulant_id)
				""").param("a", json.ecrire(ambulants))
			.query(rs -> {
				zones.computeIfAbsent(rs.getObject(1, UUID.class), k -> new HashSet<>()).add(rs.getObject(2, UUID.class));
			});
		return zones;
	}

}

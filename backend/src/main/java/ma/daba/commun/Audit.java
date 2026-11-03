package ma.daba.commun;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Journal d'audit des opérations sensibles (§10) : qui, quand, depuis quel appareil, quoi ; jamais de secret. */
@Component
public class Audit {

	/** Entrée du journal ; {@code details} est le JSON enregistré, tel quel. */
	public record Entree(long id, Instant le, UUID utilisateurId, UUID appareilId, String operation, Object details) {
	}

	private final JdbcClient jdbc;
	private final Json json;

	Audit(JdbcClient jdbc, Json json) {
		this.jdbc = jdbc;
		this.json = json;
	}

	public void consigner(Appelant appelant, String operation, Object details) {
		jdbc.sql("""
				INSERT INTO journal_audit (utilisateur_id, appareil_id, operation, details)
				VALUES (:u, :a, :operation, :details::jsonb)
				""").param("u", appelant.utilisateurId()).param("a", appelant.appareilId()).param("operation", operation)
			.param("details", json.ecrire(details)).update();
	}

	/** Lecture seule, du plus récent au plus ancien ; {@code jusqua} exclu. */
	public Page<Entree> lister(Instant depuis, Instant jusqua, UUID auteur, String operation, int page, int taille) {
		var where = """
				 FROM journal_audit WHERE (CAST(:depuis AS timestamptz) IS NULL OR le >= :depuis)
				   AND (CAST(:jusqua AS timestamptz) IS NULL OR le < :jusqua)
				   AND (CAST(:auteur AS uuid) IS NULL OR utilisateur_id = :auteur)
				   AND (CAST(:operation AS text) IS NULL OR operation = :operation)
				""";
		var compte = jdbc.sql("SELECT count(*)" + where);
		var liste = jdbc.sql("SELECT id, le, utilisateur_id, appareil_id, operation, details::text AS details" + where
				+ " ORDER BY le DESC, id DESC LIMIT :taille OFFSET :offset")
			.param("taille", taille).param("offset", page * taille);
		for (var s : new JdbcClient.StatementSpec[] { compte, liste }) {
			s.param("depuis", depuis == null ? null : Timestamp.from(depuis))
				.param("jusqua", jusqua == null ? null : Timestamp.from(jusqua))
				.param("auteur", auteur).param("operation", operation);
		}
		return Page.de(page, taille, compte.query(Long.class).single(),
				liste.query((rs, i) -> new Entree(rs.getLong("id"), rs.getTimestamp("le").toInstant(),
						rs.getObject("utilisateur_id", UUID.class), rs.getObject("appareil_id", UUID.class),
						rs.getString("operation"), json.lire(rs.getString("details"), Object.class)))
					.list());
	}

}

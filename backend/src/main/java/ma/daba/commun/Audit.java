package ma.daba.commun;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Journal d'audit des opérations sensibles (§10) : qui, quand, depuis quel appareil, quoi ; jamais de secret. */
@Component
public class Audit {

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

}

package ma.daba.ventes;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import ma.daba.commun.Acces;
import ma.daba.commun.Appelant;
import ma.daba.commun.Geste;
import ma.daba.commun.Geste.Effet;
import ma.daba.commun.Journal;
import ma.daba.commun.Json;
import ma.daba.commun.Page;
import ma.daba.commun.Probleme;
import ma.daba.commun.Reference;
import ma.daba.comptes.Comptes;
import ma.daba.referentiel.Referentiel;

/**
 * Ce que le hanout dit de ses pièces (§6) : réception d'une livraison (conforme ou avec écarts), et
 * contestation d'un document ou d'un solde, close hors ligne par l'ambulant. Rien ne modifie un
 * document émis (RG-C4) : l'ambulant régularise par un avoir.
 */
@Service
public class AvisHanout implements Acces.ContributeurExport {

	record ReceptionSaisie(@NotNull UUID id, @NotNull Boolean conforme,
			@Size(max = 100) List<Ventes.@Valid EcartReception> ecarts, @Size(max = 500) String commentaire) {
	}

	record ContestationSaisie(@NotNull UUID id, @NotNull @Pattern(regexp = "^(DOCUMENT|COMPTE_CREDIT)$") String cible,
			@NotNull UUID cibleId, @NotNull @Pattern(regexp = "^(MONTANT|QUANTITE|NON_RECU|DEJA_PAYE|AUTRE)$") String motif,
			@Size(max = 500) String commentaire) {
	}

	record Contestation(UUID id, String cible, UUID cibleId, String motif, String commentaire, String statut, Instant creeLe,
			Reference ambulant, Reference hanout, Instant resolueLe, String commentaireResolution) {
	}

	/** Opération de synchronisation RESOLUTION_CONTESTATION. */
	public record Resolution(@NotNull UUID contestationId, @Size(max = 500) String commentaire) {
	}

	/** Événements de l'outbox, notifiés à l'ambulant par {@link NotificationsVentes}. */
	record EcartSignale(UUID documentId) {
	}

	record ContestationOuverte(UUID contestationId) {
	}

	private final JdbcClient jdbc;
	private final Json json;
	private final Journal journal;
	private final Ventes ventes;
	private final Comptes comptes;
	private final Referentiel referentiel;
	private final ApplicationEventPublisher evenements;

	AvisHanout(JdbcClient jdbc, Json json, Journal journal, Ventes ventes, Comptes comptes, Referentiel referentiel,
			ApplicationEventPublisher evenements) {
		this.jdbc = jdbc;
		this.json = json;
		this.journal = journal;
		this.ventes = ventes;
		this.comptes = comptes;
		this.referentiel = referentiel;
		this.evenements = evenements;
	}

	// ---------------------------------------------------------------- réception d'une livraison

	/**
	 * Une réception par document, à part de la pièce immuable ; refusée si la réception a déjà été
	 * confirmée sur l'écran de l'ambulant à la livraison ({@code confirmationHanout}). Rejouer le même
	 * id rend le document (cree = faux).
	 */
	@Transactional
	Map.Entry<Ventes.Document, Boolean> receptionner(Appelant hanout, UUID documentId, ReceptionSaisie saisie) {
		var document = jdbc.sql("SELECT * FROM document WHERE id = :id AND hanout_id = :h").param("id", documentId)
			.param("h", hanout.hanoutId()).query(ventes::ligneDocument).optional().orElseThrow(Probleme::introuvable);
		var existante = jdbc.sql("SELECT document_id FROM reception_hanout WHERE id = :id").param("id", saisie.id())
			.query(UUID.class).optional();
		if (existante.isPresent()) {
			if (!existante.get().equals(documentId)) {
				throw Probleme.conflit("Identifiant de réception déjà utilisé");
			}
			return Map.entry(document, false);
		}
		if (document.receptionHanout() != null) {
			throw Probleme.conflit("Réception déjà enregistrée pour ce document");
		}
		if (document.confirmationHanout() != null) {
			throw Probleme.conflit("Réception confirmée à la livraison ; un désaccord passe par une contestation");
		}
		if ("AVOIR".equals(document.type()) || "ANNULE".equals(document.statut())) {
			throw Probleme.conflit("Pas de réception pour un avoir ou un document annulé");
		}
		var ecarts = saisie.ecarts() == null ? List.<Ventes.EcartReception>of() : saisie.ecarts();
		if (saisie.conforme() != ecarts.isEmpty()) {
			throw Probleme.requete(saisie.conforme() ? "Réception conforme sans écart" : "Au moins un écart quand la réception n'est pas conforme");
		}
		var vues = new HashSet<Integer>();
		for (var e : ecarts) {
			var ligne = e.ligne() < document.lignes().size() ? document.lignes().get(e.ligne()) : null;
			if (ligne == null || !"ARTICLE".equals(ligne.nature()) || !vues.add(e.ligne())) {
				throw Probleme.requete("Écart sur une ligne inexistante, en double ou qui n'est pas un article : " + e.ligne());
			}
			if (e.quantiteRecue().equals(ligne.quantite())) {
				throw Probleme.requete("Quantité reçue égale à la quantité livrée sur la ligne " + e.ligne());
			}
		}
		jdbc.sql("""
				INSERT INTO reception_hanout (document_id, id, utilisateur_id, conforme, ecarts, commentaire)
				VALUES (:d, :id, :u, :conforme, :ecarts::jsonb, :commentaire)
				""").param("d", documentId).param("id", saisie.id()).param("u", hanout.utilisateurId())
			.param("conforme", saisie.conforme()).param("ecarts", ecarts.isEmpty() ? null : json.ecrire(ecarts))
			.param("commentaire", saisie.commentaire()).update();
		ventes.publier(documentId);
		if (!saisie.conforme()) {
			evenements.publishEvent(new EcartSignale(documentId));
		}
		return Map.entry(ventes.document(documentId), true);
	}

	// ---------------------------------------------------------------- contestations

	/** Une seule contestation ouverte par cible ; rejouer le même id rend la contestation (cree = faux). */
	@Transactional
	Map.Entry<Contestation, Boolean> contester(Appelant hanout, ContestationSaisie saisie) {
		var existante = jdbc.sql("SELECT hanout_id FROM contestation WHERE id = :id").param("id", saisie.id())
			.query(UUID.class).optional();
		if (existante.isPresent()) {
			if (!existante.get().equals(hanout.hanoutId())) {
				throw Probleme.conflit("Identifiant de contestation déjà utilisé");
			}
			return Map.entry(contestation(saisie.id()), false);
		}
		var table = "DOCUMENT".equals(saisie.cible()) ? "document" : "compte_credit";
		var ambulant = jdbc.sql("SELECT ambulant_id FROM " + table + " WHERE id = :id AND hanout_id = :h")
			.param("id", saisie.cibleId()).param("h", hanout.hanoutId()).query(UUID.class).optional()
			.orElseThrow(Probleme::introuvable);
		try {
			jdbc.sql("""
					INSERT INTO contestation (id, hanout_id, utilisateur_id, ambulant_id, cible, cible_id, motif, commentaire)
					VALUES (:id, :h, :u, :a, :cible, :cibleId, :motif, :commentaire)
					""").param("id", saisie.id()).param("h", hanout.hanoutId()).param("u", hanout.utilisateurId())
				.param("a", ambulant).param("cible", saisie.cible()).param("cibleId", saisie.cibleId())
				.param("motif", saisie.motif()).param("commentaire", saisie.commentaire()).update();
		}
		catch (DuplicateKeyException e) {
			throw Probleme.conflit("Une contestation est déjà en cours sur cette cible");
		}
		publier(saisie.id());
		evenements.publishEvent(new ContestationOuverte(saisie.id()));
		return Map.entry(contestation(saisie.id()), true);
	}

	/** RESOLUTION_CONTESTATION : l'ambulant clôt une contestation qui lui est adressée ; rejouer ne change rien. */
	@Transactional
	public Effet resoudre(Geste geste, Resolution r) {
		var statut = jdbc.sql("SELECT statut FROM contestation WHERE id = :id AND ambulant_id = :a FOR UPDATE")
			.param("id", r.contestationId()).param("a", geste.ambulantId()).query(String.class).optional()
			.orElseThrow(() -> Probleme.regle(null, "Contestation inconnue"));
		if ("OUVERTE".equals(statut)) {
			jdbc.sql("UPDATE contestation SET statut = 'RESOLUE', resolue_le = :le, commentaire_resolution = :c WHERE id = :id")
				.param("le", Timestamp.from(geste.horodatage())).param("c", r.commentaire()).param("id", r.contestationId())
				.update();
			publier(r.contestationId());
		}
		return Effet.ok();
	}

	/** Le hanout voit ses contestations, l'ambulant celles qui lui sont adressées. */
	Page<Contestation> contestations(Appelant appelant, UUID cibleId, String statut, int page, int taille) {
		var hanout = appelant.a(Appelant.Role.HANOUT);
		var where = " FROM contestation WHERE " + (hanout ? "hanout_id" : "ambulant_id") + " = :moi"
				+ " AND (CAST(:cible AS uuid) IS NULL OR cible_id = :cible) AND (CAST(:s AS text) IS NULL OR statut = :s)";
		var moi = hanout ? appelant.hanoutId() : appelant.utilisateurId();
		var total = jdbc.sql("SELECT count(*)" + where).param("moi", moi).param("cible", cibleId).param("s", statut)
			.query(Long.class).single();
		var contenu = jdbc.sql("SELECT *" + where + " ORDER BY cree_le DESC LIMIT :taille OFFSET :offset")
			.param("moi", moi).param("cible", cibleId).param("s", statut).param("taille", taille).param("offset", page * taille)
			.query(this::ligne).list();
		return Page.de(page, taille, total, contenu);
	}

	Contestation contestation(UUID id) {
		return jdbc.sql("SELECT * FROM contestation WHERE id = :id").param("id", id).query(this::ligne).single();
	}

	private Contestation ligne(ResultSet rs, int i) throws SQLException {
		var a = rs.getObject("ambulant_id", UUID.class);
		var h = rs.getObject("hanout_id", UUID.class);
		var resolue = rs.getTimestamp("resolue_le");
		return new Contestation(rs.getObject("id", UUID.class), rs.getString("cible"), rs.getObject("cible_id", UUID.class),
				rs.getString("motif"), rs.getString("commentaire"), rs.getString("statut"), rs.getTimestamp("cree_le").toInstant(),
				new Reference(a, comptes.nom(a)), new Reference(h, referentiel.hanout(h).map(Referentiel.Hanout::nom).orElse("—")),
				resolue == null ? null : resolue.toInstant(), rs.getString("commentaire_resolution"));
	}

	@Override
	public String cle() {
		return "avisHanout";
	}

	/** Droit d'accès (loi 09-08) : réceptions saisies, contestations ouvertes ou reçues, avec leurs commentaires. */
	@Override
	public Object exporter(UUID utilisateurId) {
		return Map.of(
				"receptions", jdbc.sql("SELECT document_id, conforme, ecarts::text AS ecarts, commentaire, le FROM reception_hanout WHERE utilisateur_id = :u")
					.param("u", utilisateurId).query().listOfRows(),
				"contestations", jdbc.sql("SELECT * FROM contestation WHERE utilisateur_id = :u OR ambulant_id = :u")
					.param("u", utilisateurId).query().listOfRows());
	}

	/** Livrée hors ligne à l'ambulant concerné (entité CONTESTATION). */
	private void publier(UUID id) {
		var c = contestation(id);
		journal.publier(c.ambulant().id(), "CONTESTATION", id, c);
	}

}

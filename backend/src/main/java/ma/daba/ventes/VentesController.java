package ma.daba.ventes;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import ma.daba.commun.Acces.Roles;
import ma.daba.commun.Appelant;
import ma.daba.commun.Appelant.Role;
import ma.daba.commun.Page;
import ma.daba.commun.Probleme;

@RestController
@Validated
class VentesController {

	record Anciennete(long de0a15Jours, long de15a30Jours, long plusDe30Jours) {
	}

	record SyntheseCreances(long total, Anciennete anciennete, List<Ventes.CompteCredit> topDebiteurs) {
	}

	record Ecriture(Instant le, String nature, long montant, long soldeApres, UUID documentId, String numero, UUID versementId) {
	}

	record Releve(Ventes.CompteCredit compte, long soldeInitial, List<Ecriture> ecritures) {
	}

	private final JdbcClient jdbc;
	private final Ventes ventes;
	private final PdfDocuments pdfs;

	VentesController(JdbcClient jdbc, Ventes ventes, PdfDocuments pdfs) {
		this.jdbc = jdbc;
		this.ventes = ventes;
		this.pdfs = pdfs;
	}

	/** Cloisonnement : l'ambulant voit les pièces de son organisation, le hanout celles qui lui sont adressées. */
	private static String perimetre(Appelant appelant) {
		return appelant.a(Role.AMBULANT) ? "organisation_id = :moi" : "hanout_id = :moi";
	}

	private static UUID moi(Appelant appelant) {
		return appelant.a(Role.AMBULANT) ? appelant.organisationId() : appelant.hanoutId();
	}

	@GetMapping("/v1/documents")
	@Roles({ Role.AMBULANT, Role.HANOUT })
	Page<Ventes.Document> documents(Appelant appelant,
			@RequestParam(required = false) @Pattern(regexp = "^(BON_LIVRAISON|FACTURE|AVOIR)$") String type,
			@RequestParam(required = false) UUID hanoutId, @RequestParam(required = false) UUID ambulantId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant depuis,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant jusqua,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "50") @Min(1) @Max(200) int taille) {
		var where = " FROM document WHERE " + perimetre(appelant) + """
				   AND (CAST(:type AS text) IS NULL OR type = :type)
				   AND (CAST(:h AS uuid) IS NULL OR hanout_id = :h)
				   AND (CAST(:amb AS uuid) IS NULL OR ambulant_id = :amb)
				   AND (CAST(:depuis AS timestamptz) IS NULL OR emis_le >= :depuis)
				   AND (CAST(:jusqua AS timestamptz) IS NULL OR emis_le < :jusqua)
				""";
		var compte = jdbc.sql("SELECT count(*)" + where);
		var liste = jdbc.sql("SELECT *" + where + " ORDER BY emis_le DESC, sequence DESC LIMIT :taille OFFSET :offset")
			.param("taille", taille).param("offset", page * taille);
		for (var s : new JdbcClient.StatementSpec[] { compte, liste }) {
			s.param("moi", moi(appelant)).param("type", type)
				.param("h", appelant.a(Role.AMBULANT) ? hanoutId : null)
				.param("amb", appelant.a(Role.HANOUT) ? ambulantId : null)
				.param("depuis", ts(depuis)).param("jusqua", ts(jusqua));
		}
		return Page.de(page, taille, compte.query(Long.class).single(), liste.query(ventes::ligneDocument).list());
	}

	@GetMapping("/v1/documents/{documentId}")
	@Roles({ Role.AMBULANT, Role.HANOUT })
	Ventes.Document document(Appelant appelant, @PathVariable UUID documentId) {
		return jdbc.sql("SELECT * FROM document WHERE id = :id AND " + perimetre(appelant))
			.param("id", documentId).param("moi", moi(appelant))
			.query(ventes::ligneDocument).optional().orElseThrow(Probleme::introuvable);
	}

	@GetMapping("/v1/documents/{documentId}/pdf")
	@Roles({ Role.AMBULANT, Role.HANOUT })
	ResponseEntity<byte[]> pdf(Appelant appelant, @PathVariable UUID documentId,
			@RequestParam(required = false) @Pattern(regexp = "^(fr|ar|ary-Arab|ary-Latn)$") String langue,
			@RequestHeader(name = "Accept-Language", required = false) String acceptLanguage) {
		var document = document(appelant, documentId);
		var choisie = langue != null ? langue
				: acceptLanguage == null ? "fr"
						: java.util.stream.Stream.of("ary-Arab", "ary-Latn", "ar", "fr").filter(acceptLanguage::startsWith).findFirst().orElse("fr");
		return ResponseEntity.ok()
			.contentType(MediaType.APPLICATION_PDF)
			.header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + document.numero() + ".pdf\"")
			.body(pdfs.generer(document, choisie));
	}

	@GetMapping("/v1/karni/comptes")
	@Roles({ Role.AMBULANT, Role.HANOUT })
	Page<Ventes.CompteCredit> comptes(Appelant appelant, @RequestParam(defaultValue = "false") boolean debiteurSeulement,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "50") @Min(1) @Max(200) int taille) {
		var tous = comptesDe(appelant).stream()
			.filter(c -> !debiteurSeulement || c.solde() > 0)
			.sorted((a, b) -> Long.compare(b.solde(), a.solde()))
			.toList();
		var debut = Math.min(page * taille, tous.size());
		return Page.de(page, taille, tous.size(), tous.subList(debut, Math.min(debut + taille, tous.size())));
	}

	/** ponytail: soldes calculés compte par compte ; une vue matérialisée si un ambulant dépasse quelques centaines de hanouts. */
	private List<Ventes.CompteCredit> comptesDe(Appelant appelant) {
		var colonne = appelant.a(Role.AMBULANT) ? "ambulant_id = :moi AND organisation_id = :org" : "hanout_id = :moi";
		return jdbc.sql("SELECT * FROM compte_credit WHERE " + colonne)
			.param("moi", appelant.a(Role.AMBULANT) ? appelant.utilisateurId() : appelant.hanoutId())
			.param("org", appelant.organisationId())
			.query(ventes::ligneCompte).list();
	}

	@GetMapping("/v1/karni/synthese")
	@Roles(Role.AMBULANT)
	SyntheseCreances synthese(Appelant appelant, @RequestParam(defaultValue = "10") @Min(1) @Max(50) int top) {
		long[] tranches = new long[3];
		jdbc.sql("""
				SELECT r.emis_le, r.reste FROM compte_credit c,
				  LATERAL (""" + Ventes.RESTES.replace(":a", "c.ambulant_id").replace(":h", "c.hanout_id") + """
				  ) r
				WHERE c.ambulant_id = :moi AND r.reste > 0
				""").param("moi", appelant.utilisateurId())
			.query(rs -> {
				var age = Duration.between(rs.getTimestamp("emis_le").toInstant(), Instant.now()).toDays();
				tranches[age < 15 ? 0 : age < 30 ? 1 : 2] += rs.getLong("reste");
			});
		var debiteurs = comptesDe(appelant).stream().filter(c -> c.solde() > 0)
			.sorted((a, b) -> Long.compare(b.solde(), a.solde())).limit(top).toList();
		return new SyntheseCreances(tranches[0] + tranches[1] + tranches[2],
				new Anciennete(tranches[0], tranches[1], tranches[2]), debiteurs);
	}

	/** Relevé chronologique : achats à crédit, versements, avoirs, avec le solde après chaque écriture (RG-C1). */
	@GetMapping("/v1/karni/comptes/{compteId}/releve")
	@Roles({ Role.AMBULANT, Role.HANOUT })
	Releve releve(Appelant appelant, @PathVariable UUID compteId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant depuis,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant jusqua) {
		var compte = comptesDe(appelant).stream().filter(c -> c.id().equals(compteId)).findFirst()
			.orElseThrow(Probleme::introuvable);
		var ecritures = jdbc.sql("""
				SELECT emis_le AS le, 'ACHAT_CREDIT' AS nature, montant_credit AS montant, id AS document_id, numero, NULL::uuid AS versement_id
				  FROM document WHERE ambulant_id = :a AND hanout_id = :h AND type <> 'AVOIR' AND montant_credit > 0
				UNION ALL
				SELECT verse_le, 'VERSEMENT', -montant, NULL, NULL, id FROM versement WHERE ambulant_id = :a AND hanout_id = :h
				UNION ALL
				SELECT emis_le, 'AVOIR', -total, id, numero, NULL FROM document WHERE ambulant_id = :a AND hanout_id = :h AND type = 'AVOIR'
				ORDER BY 1
				""").param("a", compte.ambulant().id()).param("h", compte.hanout().id())
			.query((rs, i) -> Map.entry(rs.getTimestamp("le").toInstant(), new Object[] { rs.getString("nature"),
					rs.getLong("montant"), rs.getObject("document_id", UUID.class), rs.getString("numero"),
					rs.getObject("versement_id", UUID.class) }))
			.list();
		long solde = 0;
		long initial = 0;
		var resultat = new ArrayList<Ecriture>();
		for (var e : ecritures) {
			var v = e.getValue();
			solde += (long) v[1];
			if (depuis != null && e.getKey().isBefore(depuis)) {
				initial = solde;
				continue;
			}
			if (jusqua != null && !e.getKey().isBefore(jusqua)) {
				break;
			}
			resultat.add(new Ecriture(e.getKey(), (String) v[0], Math.abs((long) v[1]), solde, (UUID) v[2], (String) v[3], (UUID) v[4]));
		}
		return new Releve(compte, initial, resultat);
	}

	static java.sql.Timestamp ts(Instant instant) {
		return instant == null ? null : java.sql.Timestamp.from(instant);
	}

}

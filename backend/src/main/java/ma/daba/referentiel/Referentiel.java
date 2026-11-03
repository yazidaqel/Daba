package ma.daba.referentiel;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ma.daba.commun.Journal;
import ma.daba.commun.Json;
import ma.daba.commun.Position;
import tools.jackson.core.type.TypeReference;

/** API publique du référentiel : produits, hanouts (base POS RED + terrain), grossistes. */
@Service
public class Referentiel {

	static final TypeReference<Map<String, String>> LIBELLE = new TypeReference<>() {
	};

	public record Categorie(String code, Map<String, String> libelle) {
	}

	/** {@code tauxTva} en pour cent, null si inconnu (RG-F2). */
	public record Produit(UUID id, String ean, Map<String, String> libelle, String marque, Categorie categorie,
			String conditionnement, int unitesParConditionnement, Integer tauxTva, String photoUrl) {
	}

	public record Hanout(UUID id, String codePos, String nom, String gerant, String telephone, Position position,
			String adresse, String ville, String photoFacadeUrl, String ice, Integer distanceM) {
	}

	public record Grossiste(UUID id, String nom, Position position, String adresse, String ville, Integer distanceM) {
	}

	static final String SELECT_PRODUIT = """
			SELECT p.*, c.libelle AS categorie_libelle FROM produit p JOIN categorie c ON c.code = p.categorie
			""";

	static final String SELECT_HANOUT = """
			SELECT h.*, ST_Y(h.position::geometry) AS lat, ST_X(h.position::geometry) AS lon
			""";

	static final String SELECT_GROSSISTE = """
			SELECT g.*, ST_Y(g.position::geometry) AS lat, ST_X(g.position::geometry) AS lon
			""";

	private final JdbcClient jdbc;
	private final Json json;
	private final Journal journal;

	Referentiel(JdbcClient jdbc, Json json, Journal journal) {
		this.jdbc = jdbc;
		this.json = json;
		this.journal = journal;
	}

	public Optional<Produit> produit(UUID id) {
		return jdbc.sql(SELECT_PRODUIT + " WHERE p.id = :id").param("id", id).query(this::ligneProduit).optional();
	}

	public Map<UUID, Produit> produits(Collection<UUID> ids) {
		if (ids.isEmpty()) {
			return Map.of();
		}
		return jdbc.sql(SELECT_PRODUIT + " WHERE p.id IN (:ids)").param("ids", ids).query(this::ligneProduit).stream()
			.collect(Collectors.toMap(Produit::id, Function.identity()));
	}

	/** Vrai si le produit appartient au grand client (carte des ruptures, §5.4). */
	public boolean produitDuGrandClient(UUID produitId, UUID grandClientId) {
		return jdbc.sql("SELECT count(*) FROM produit WHERE id = :id AND grand_client_id = :gc")
			.param("id", produitId).param("gc", grandClientId).query(Long.class).single() > 0;
	}

	public List<UUID> produitsDuGrandClient(UUID grandClientId) {
		return jdbc.sql("SELECT id FROM produit WHERE grand_client_id = :gc").param("gc", grandClientId)
			.query(UUID.class).list();
	}

	/** Produits d'une marque (ciblage d'une promo par l'historique d'achat, §9). */
	public List<UUID> produitsDeMarque(String marque) {
		return jdbc.sql("SELECT id FROM produit WHERE marque = :m").param("m", marque).query(UUID.class).list();
	}

	/** Codes de {@code codes} absents des villes de {@code GET /v1/regions}. */
	public List<String> villesInconnues(Collection<String> codes) {
		return inconnus("ville", codes);
	}

	/** Codes de {@code codes} absents de {@code GET /v1/categories}. */
	public List<String> categoriesInconnues(Collection<String> codes) {
		return inconnus("categorie", codes);
	}

	private List<String> inconnus(String table, Collection<String> codes) {
		if (codes.isEmpty()) {
			return List.of();
		}
		var connus = jdbc.sql("SELECT code FROM " + table + " WHERE code IN (:c)").param("c", codes).query(String.class).list();
		return codes.stream().filter(c -> !connus.contains(c)).toList();
	}

	public Optional<Hanout> hanout(UUID id) {
		return jdbc.sql(SELECT_HANOUT + ", null AS distance FROM hanout h WHERE h.id = :id").param("id", id)
			.query(this::ligneHanout).optional();
	}

	public Optional<Hanout> hanoutParTelephone(String telephone) {
		return jdbc.sql(SELECT_HANOUT + ", null AS distance FROM hanout h WHERE h.telephone = :tel ORDER BY h.cree_le LIMIT 1")
			.param("tel", telephone).query(this::ligneHanout).optional();
	}

	/**
	 * Hanout créé sur le terrain par un ambulant (absent de la base POS), avec l'UUID de l'appareil :
	 * une vente hors ligne n'est jamais perdue. Rejouer la création ne change rien.
	 */
	@Transactional
	public void creerHanoutTerrain(UUID id, String nom, String gerant, String telephone, Position position,
			String adresse, String ville, String ice, UUID ambulantId) {
		var cree = jdbc.sql("""
				INSERT INTO hanout (id, nom, gerant, telephone, position, adresse, ville, ice, cree_par)
				VALUES (:id, :nom, :gerant, :tel, CASE WHEN :posLat::float8 IS NULL THEN NULL ELSE %s END,
				        :adresse, :ville, :ice, :ambulant)
				ON CONFLICT (id) DO NOTHING
				""".formatted(Position.sql("pos")))
			.param("id", id).param("nom", nom).param("gerant", gerant).param("tel", telephone)
			.param("posLat", position == null ? null : position.lat())
			.param("posLon", position == null ? null : position.lon())
			.param("adresse", adresse).param("ville", ville).param("ice", ice).param("ambulant", ambulantId)
			.update();
		if (cree > 0) {
			publierHanout(id);
		}
	}

	void publierHanout(UUID id) {
		hanout(id).ifPresent(h -> journal.publier(null, "HANOUT", id, h));
	}

	void publierProduit(UUID id) {
		produit(id).ifPresent(p -> journal.publier(null, "PRODUIT", id, p));
	}

	/** Libellés d'une catégorie changés : ses produits, qui l'embarquent, repartent vers les appareils. */
	void publierCategorie(String code) {
		jdbc.sql("SELECT id FROM produit WHERE categorie = :c").param("c", code).query(UUID.class).list()
			.forEach(this::publierProduit);
	}

	/** Code de ville de {@code GET /v1/regions} : « Fès », « fes » et « FES » désignent la même ville. */
	public static String codeVille(String ville) {
		return ville == null ? ""
				: java.text.Normalizer.normalize(ville, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "")
					.toUpperCase(java.util.Locale.ROOT).trim().replaceAll("[^A-Z0-9]+", "_");
	}

	/** Région d'une ville du référentiel (ciblage, §5.1), à partir de son nom ou de son code. */
	public Optional<String> region(String ville) {
		return jdbc.sql("SELECT region_code FROM ville WHERE code = :c").param("c", codeVille(ville)).query(String.class).optional();
	}

	public Optional<Grossiste> grossiste(UUID id) {
		return jdbc.sql(SELECT_GROSSISTE + ", null AS distance FROM grossiste g WHERE g.id = :id").param("id", id)
			.query(this::ligneGrossiste).optional();
	}

	/** Fiche d'un grossiste à son inscription (id = son organisation). */
	@Transactional
	public void enregistrerGrossiste(UUID id, String nom, Position position, String adresse, String ville) {
		jdbc.sql("""
				INSERT INTO grossiste (id, nom, position, adresse, ville) VALUES (:id, :nom, %s, :adresse, :ville)
				ON CONFLICT (id) DO UPDATE SET nom = excluded.nom, position = excluded.position,
				  adresse = excluded.adresse, ville = excluded.ville
				""".formatted(Position.sql("pos")))
			.param("id", id).param("nom", nom).param("posLat", position.lat()).param("posLon", position.lon())
			.param("adresse", adresse).param("ville", ville)
			.update();
	}

	Produit ligneProduit(ResultSet rs, int i) throws SQLException {
		return new Produit(rs.getObject("id", UUID.class), rs.getString("ean"), json.lire(rs.getString("libelle"), LIBELLE),
				rs.getString("marque"),
				new Categorie(rs.getString("categorie"), json.lire(rs.getString("categorie_libelle"), LIBELLE)),
				rs.getString("conditionnement"), rs.getInt("unites_par_conditionnement"),
				rs.getBigDecimal("taux_tva") == null ? null : rs.getBigDecimal("taux_tva").intValueExact(),
				rs.getString("photo_url"));
	}

	Hanout ligneHanout(ResultSet rs, int i) throws SQLException {
		return new Hanout(rs.getObject("id", UUID.class), rs.getString("code_pos"), rs.getString("nom"),
				rs.getString("gerant"), rs.getString("telephone"), position(rs), rs.getString("adresse"),
				rs.getString("ville"), rs.getString("photo_facade_url"), rs.getString("ice"), distance(rs));
	}

	Grossiste ligneGrossiste(ResultSet rs, int i) throws SQLException {
		return new Grossiste(rs.getObject("id", UUID.class), rs.getString("nom"), position(rs), rs.getString("adresse"),
				rs.getString("ville"), distance(rs));
	}

	private static Position position(ResultSet rs) throws SQLException {
		var lat = rs.getObject("lat", Double.class);
		return lat == null ? null : new Position(lat, rs.getDouble("lon"));
	}

	private static Integer distance(ResultSet rs) throws SQLException {
		return rs.getObject("distance") instanceof Number d ? (int) Math.round(d.doubleValue()) : null;
	}

}

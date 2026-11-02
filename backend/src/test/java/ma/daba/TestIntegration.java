package ma.daba;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import ma.daba.canaux.Canaux;
import ma.daba.commun.Jetons;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Base des tests d'intégration : un seul contexte Spring et un seul conteneur PostGIS pour toute la suite. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
public abstract class TestIntegration {

	@Autowired
	protected MockMvcTester mvc;

	@Autowired
	protected JdbcClient jdbc;

	@Autowired
	protected Jetons jetons;

	@Autowired
	protected JsonMapper json;

	/** Fournisseur SMS espionné : permet de lire les codes OTP envoyés. */
	@MockitoSpyBean(name = "sms")
	protected Canaux.Fournisseur sms;

	private static final java.util.concurrent.atomic.AtomicLong PREFIXES = new java.util.concurrent.atomic.AtomicLong();

	public record Compte(UUID id, UUID organisationId, UUID appareilId, String telephone, String jeton, String prefixe,
			UUID hanoutId) {
	}

	protected static String telephone() {
		return "+2126" + "%08d".formatted(ThreadLocalRandom.current().nextInt(100_000_000));
	}

	protected Compte ambulant() {
		return compte("AMBULANT", "AMBULANT", null, null);
	}

	protected Compte grossiste() {
		var c = compte("GROSSISTE", "GROSSISTE", null, null);
		jdbc.sql("INSERT INTO grossiste (id, nom, position, ville) VALUES (:id, 'Grossiste test', ST_SetSRID(ST_MakePoint(-7.6, 33.58), 4326)::geography, 'Casablanca')")
			.param("id", c.organisationId()).update();
		return c;
	}

	protected Compte grandClient() {
		return compte("GRAND_CLIENT", "GRAND_CLIENT", null, null);
	}

	protected Compte admin() {
		return compte("ADMIN", "PLATEFORME", null, null);
	}

	protected Compte compteHanout(UUID hanoutId) {
		return compte("HANOUT", "HANOUT", hanoutId, null);
	}

	/** Compte avec appareil et consentement CNDP (géolocalisation acceptée), et son jeton d'accès. */
	protected Compte compte(String role, String type, UUID hanoutId, String telephone) {
		var org = UUID.randomUUID();
		var id = UUID.randomUUID();
		var appareil = UUID.randomUUID();
		var tel = telephone != null ? telephone : telephone();
		jdbc.sql("INSERT INTO organisation (id, type, nom) VALUES (:id, :type, :nom)")
			.param("id", org).param("type", type).param("nom", role + " " + tel).update();
		jdbc.sql("INSERT INTO utilisateur (id, organisation_id, telephone, nom, role, hanout_id) VALUES (:id, :org, :tel, :nom, :role, :h)")
			.param("id", id).param("org", org).param("tel", tel).param("nom", role.toLowerCase() + " " + tel.substring(8))
			.param("role", role).param("h", hanoutId).update();
		var prefixe = "AMBULANT".equals(role) ? "T" + "%5s".formatted(Long.toString(PREFIXES.incrementAndGet(), 36).toUpperCase()).replace(' ', '0') : null;
		jdbc.sql("INSERT INTO appareil (id, utilisateur_id, prefixe) VALUES (:id, :u, :p)")
			.param("id", appareil).param("u", id).param("p", prefixe).update();
		jdbc.sql("INSERT INTO consentement (utilisateur_id, version_texte, traitement_donnees, geolocalisation) VALUES (:u, 'v1', true, true)")
			.param("u", id).update();
		var claims = new HashMap<String, Object>();
		claims.put("sub", id.toString());
		claims.put("org", org.toString());
		claims.put("roles", List.of(role));
		claims.put("app", appareil.toString());
		if (hanoutId != null) {
			claims.put("hanout", hanoutId.toString());
		}
		return new Compte(id, org, appareil, tel, jetons.signer("acces", claims, java.time.Duration.ofHours(1)), prefixe, hanoutId);
	}

	protected UUID produit() {
		return produit(null);
	}

	protected UUID produit(UUID grandClient) {
		var id = UUID.randomUUID();
		jdbc.sql("INSERT INTO categorie (code, libelle) VALUES ('BOISSONS', '{\"fr\":\"Boissons\"}') ON CONFLICT DO NOTHING").update();
		jdbc.sql("""
				INSERT INTO produit (id, ean, libelle, marque, categorie, conditionnement, unites_par_conditionnement, grand_client_id)
				VALUES (:id, :ean, '{"fr":"Soda 1L","ar":"مشروب 1 لتر"}', 'Marque', 'BOISSONS', 'UNITE', 1, :gc)
				""")
			.param("id", id).param("ean", "%013d".formatted(ThreadLocalRandom.current().nextLong(1_000_000_000_000L)))
			.param("gc", grandClient).update();
		return id;
	}

	protected UUID hanout(double lat, double lon) {
		var id = UUID.randomUUID();
		jdbc.sql("INSERT INTO hanout (id, nom, telephone, position, ville) VALUES (:id, 'Hanout test', :tel, ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography, 'Casablanca')")
			.param("id", id).param("tel", telephone()).param("lat", lat).param("lon", lon).update();
		return id;
	}

	protected MvcTestResult appel(HttpMethod methode, Compte compte, String uri, Object corps) {
		var requete = mvc.method(methode).uri(uri);
		if (compte != null) {
			requete.header("Authorization", "Bearer " + compte.jeton());
		}
		if (corps != null) {
			requete.contentType(MediaType.APPLICATION_JSON).content(corps instanceof String s ? s : json.writeValueAsString(corps));
		}
		return requete.exchange();
	}

	protected MvcTestResult get(Compte compte, String uri) {
		return appel(HttpMethod.GET, compte, uri, null);
	}

	protected MvcTestResult post(Compte compte, String uri, Object corps) {
		return appel(HttpMethod.POST, compte, uri, corps);
	}

	protected MvcTestResult put(Compte compte, String uri, Object corps) {
		return appel(HttpMethod.PUT, compte, uri, corps);
	}

	protected JsonNode lire(MvcTestResult resultat) {
		try {
			return json.readTree(resultat.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
		}
		catch (java.io.UnsupportedEncodingException e) {
			throw new IllegalStateException(e);
		}
	}

	protected static Map<String, Object> m(Object... clesValeurs) {
		var map = new java.util.LinkedHashMap<String, Object>();
		for (int i = 0; i < clesValeurs.length; i += 2) {
			map.put((String) clesValeurs[i], clesValeurs[i + 1]);
		}
		return map;
	}

}

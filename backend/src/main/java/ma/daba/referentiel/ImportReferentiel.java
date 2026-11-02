package ma.daba.referentiel;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import ma.daba.commun.Json;
import ma.daba.commun.Position;
import ma.daba.commun.Probleme;

/**
 * Import CSV (UTF-8, séparateur « ; » ou « , », première ligne = en-têtes) de la base POS fournie
 * par RED et du référentiel produits. Une ligne invalide est rejetée avec son numéro, les autres
 * sont importées ; réimporter met à jour (clé : code_pos, ean).
 */
@Service
class ImportReferentiel {

	record Rejet(int ligne, String erreur) {
	}

	record Bilan(int crees, int misAJour, List<Rejet> rejets) {
	}

	private final JdbcClient jdbc;
	private final Json json;
	private final Referentiel referentiel;

	ImportReferentiel(JdbcClient jdbc, Json json, Referentiel referentiel) {
		this.jdbc = jdbc;
		this.json = json;
		this.referentiel = referentiel;
	}

	/** Colonnes : code_pos, nom, gerant, telephone, lat, lon, adresse, ville, ice. */
	@Transactional
	Bilan hanouts(MultipartFile fichier) {
		return importer(fichier, l -> {
			var tel = telephone(l.get("telephone"));
			var ice = vide(l.get("ice"));
			if (ice != null && !ice.matches("[0-9]{15}")) {
				throw new IllegalArgumentException("ICE invalide");
			}
			var r = jdbc.sql("""
					INSERT INTO hanout (id, code_pos, nom, gerant, telephone, position, adresse, ville, ice)
					VALUES (:id, :code, :nom, :gerant, :tel, %s, :adresse, :ville, :ice)
					ON CONFLICT (code_pos) DO UPDATE SET nom = excluded.nom, gerant = excluded.gerant,
					  telephone = excluded.telephone, position = excluded.position, adresse = excluded.adresse,
					  ville = excluded.ville, ice = excluded.ice
					RETURNING id, (xmax = 0) AS cree
					""".formatted(Position.sql("pos")))
				.param("id", UUID.randomUUID()).param("code", requis(l, "code_pos")).param("nom", requis(l, "nom"))
				.param("gerant", vide(l.get("gerant"))).param("tel", tel)
				.param("posLat", coordonnee(l, "lat", 90)).param("posLon", coordonnee(l, "lon", 180))
				.param("adresse", vide(l.get("adresse"))).param("ville", vide(l.get("ville"))).param("ice", ice)
				.query((rs, i) -> Map.entry(rs.getObject("id", UUID.class), rs.getBoolean("cree"))).single();
			referentiel.publierHanout(r.getKey());
			return r.getValue();
		});
	}

	/**
	 * Colonnes : ean, libelle_fr, libelle_ar, libelle_ary_arab, libelle_ary_latn, marque, categorie,
	 * conditionnement (UNITE, PACK, CARTON), unites, photo_url, grand_client_id, taux_tva (vide = inconnu).
	 */
	@Transactional
	Bilan produits(MultipartFile fichier) {
		return importer(fichier, l -> {
			var ean = requis(l, "ean");
			if (!ean.matches("[0-9]{8}|[0-9]{13}")) {
				throw new IllegalArgumentException("EAN invalide");
			}
			var libelle = new LinkedHashMap<String, String>();
			libelle.put("fr", requis(l, "libelle_fr"));
			for (var langue : List.of("ar", "ary-Arab", "ary-Latn")) {
				var valeur = vide(l.get("libelle_" + langue.toLowerCase().replace('-', '_')));
				if (valeur != null) {
					libelle.put(langue, valeur);
				}
			}
			var categorie = requis(l, "categorie");
			jdbc.sql("INSERT INTO categorie (code, libelle) VALUES (:code, :libelle::jsonb) ON CONFLICT DO NOTHING")
				.param("code", categorie).param("libelle", json.ecrire(Map.of("fr", categorie))).update();
			var conditionnement = requis(l, "conditionnement").toUpperCase();
			if (!List.of("UNITE", "PACK", "CARTON").contains(conditionnement)) {
				throw new IllegalArgumentException("Conditionnement inconnu : " + conditionnement);
			}
			var grandClient = vide(l.get("grand_client_id"));
			var tva = vide(l.get("taux_tva"));
			if (tva != null && !List.of("0", "7", "10", "14", "20").contains(tva)) {
				throw new IllegalArgumentException("Taux de TVA inconnu : " + tva);
			}
			var r = jdbc.sql("""
					INSERT INTO produit (id, ean, libelle, marque, categorie, conditionnement, unites_par_conditionnement,
					  photo_url, grand_client_id, taux_tva)
					VALUES (:id, :ean, :libelle::jsonb, :marque, :categorie, :cond, :unites, :photo, :gc, :tva)
					ON CONFLICT (ean) DO UPDATE SET libelle = excluded.libelle, marque = excluded.marque,
					  categorie = excluded.categorie, conditionnement = excluded.conditionnement,
					  unites_par_conditionnement = excluded.unites_par_conditionnement, photo_url = excluded.photo_url,
					  grand_client_id = excluded.grand_client_id, taux_tva = excluded.taux_tva
					RETURNING id, (xmax = 0) AS cree
					""")
				.param("id", UUID.randomUUID()).param("ean", ean).param("libelle", json.ecrire(libelle))
				.param("marque", requis(l, "marque")).param("categorie", categorie).param("cond", conditionnement)
				.param("unites", Integer.parseInt(l.getOrDefault("unites", "1").isBlank() ? "1" : l.get("unites")))
				.param("photo", vide(l.get("photo_url")))
				.param("gc", grandClient == null ? null : UUID.fromString(grandClient))
				.param("tva", tva == null ? null : Integer.valueOf(tva))
				.query((rs, i) -> Map.entry(rs.getObject("id", UUID.class), rs.getBoolean("cree"))).single();
			referentiel.publierProduit(r.getKey());
			return r.getValue();
		});
	}

	private interface Ligne {

		/** Vrai si créée, faux si mise à jour. */
		boolean importer(Map<String, String> ligne);

	}

	private Bilan importer(MultipartFile fichier, Ligne traitement) {
		int crees = 0;
		int maj = 0;
		var rejets = new ArrayList<Rejet>();
		try (var lecteur = new BufferedReader(new InputStreamReader(fichier.getInputStream(), StandardCharsets.UTF_8))) {
			var entete = lecteur.readLine();
			if (entete == null) {
				throw Probleme.requete("Fichier vide");
			}
			entete = entete.replace("﻿", "");
			var separateur = entete.chars().filter(c -> c == ';').count() >= entete.chars().filter(c -> c == ',').count() ? ';' : ',';
			var colonnes = champs(entete, separateur).stream().map(String::toLowerCase).toList();
			String texte;
			for (int numero = 2; (texte = lecteur.readLine()) != null; numero++) {
				if (texte.isBlank()) {
					continue;
				}
				var valeurs = champs(texte, separateur);
				var ligne = new HashMap<String, String>();
				for (int i = 0; i < colonnes.size() && i < valeurs.size(); i++) {
					ligne.put(colonnes.get(i), valeurs.get(i));
				}
				try {
					if (traitement.importer(ligne)) {
						crees++;
					}
					else {
						maj++;
					}
				}
				catch (IllegalArgumentException e) {
					rejets.add(new Rejet(numero, e.getMessage()));
				}
			}
		}
		catch (IOException e) {
			throw Probleme.requete("Fichier illisible");
		}
		return new Bilan(crees, maj, rejets);
	}

	/** Découpe une ligne CSV (guillemets doublés acceptés). ponytail: pas de champ sur plusieurs lignes. */
	static List<String> champs(String ligne, char separateur) {
		var champs = new ArrayList<String>();
		var courant = new StringBuilder();
		boolean entreGuillemets = false;
		for (int i = 0; i < ligne.length(); i++) {
			char c = ligne.charAt(i);
			if (entreGuillemets) {
				if (c == '"' && i + 1 < ligne.length() && ligne.charAt(i + 1) == '"') {
					courant.append('"');
					i++;
				}
				else if (c == '"') {
					entreGuillemets = false;
				}
				else {
					courant.append(c);
				}
			}
			else if (c == '"') {
				entreGuillemets = true;
			}
			else if (c == separateur) {
				champs.add(courant.toString().trim());
				courant.setLength(0);
			}
			else {
				courant.append(c);
			}
		}
		champs.add(courant.toString().trim());
		return champs;
	}

	/** Numéro marocain au format E.164 (06…, 2126…, 002126… acceptés) ; null si absent. */
	static String telephone(String brut) {
		if (vide(brut) == null) {
			return null;
		}
		var chiffres = brut.replaceAll("[^0-9+]", "");
		if (chiffres.startsWith("00")) {
			chiffres = "+" + chiffres.substring(2);
		}
		else if (chiffres.startsWith("0") && chiffres.length() == 10) {
			chiffres = "+212" + chiffres.substring(1);
		}
		else if (chiffres.startsWith("212")) {
			chiffres = "+" + chiffres;
		}
		if (!chiffres.matches("^\\+212[5-7][0-9]{8}$")) {
			throw new IllegalArgumentException("Téléphone invalide : " + brut);
		}
		return chiffres;
	}

	private static String requis(Map<String, String> ligne, String colonne) {
		var valeur = vide(ligne.get(colonne));
		if (valeur == null) {
			throw new IllegalArgumentException("Colonne " + colonne + " manquante");
		}
		return valeur;
	}

	private static double coordonnee(Map<String, String> ligne, String colonne, double borne) {
		try {
			var valeur = Double.parseDouble(requis(ligne, colonne).replace(',', '.'));
			if (Math.abs(valeur) > borne) {
				throw new IllegalArgumentException(colonne + " hors bornes");
			}
			return valeur;
		}
		catch (NumberFormatException e) {
			throw new IllegalArgumentException(colonne + " invalide");
		}
	}

	private static String vide(String valeur) {
		return valeur == null || valeur.isBlank() ? null : valeur.trim();
	}

}

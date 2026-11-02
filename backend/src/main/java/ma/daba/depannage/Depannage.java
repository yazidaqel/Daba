package ma.daba.depannage;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import ma.daba.commun.Acces;
import ma.daba.commun.Anomalie;
import ma.daba.commun.Appelant;
import ma.daba.commun.Geste;
import ma.daba.commun.Json;
import ma.daba.commun.Page;
import ma.daba.commun.Photos;
import ma.daba.commun.Position;
import ma.daba.commun.Probleme;
import ma.daba.comptes.Comptes;
import ma.daba.referentiel.Referentiel;
import ma.daba.stock.Stock;
import ma.daba.ventes.Ventes;
import tools.jackson.core.type.TypeReference;

/**
 * Dépannage sur rupture (§7.1) : la demande d'un hanout part aux ambulants disponibles du rayon qui
 * ont le produit en stock déclaré (RG-D1) ; le premier qui accepte la prend (RG-D2) ; sans réponse,
 * le rayon s'élargit une fois puis le hanout est prévenu (RG-D3) ; la livraison est une vente liée (RG-D4).
 */
@Service
public class Depannage implements Acces.ContributeurExport {

	record DisponibiliteSaisie(@NotNull Boolean disponible, @jakarta.validation.Valid Position position) {
	}

	record Disponibilite(boolean disponible, Instant depuis, Instant positionMiseAJourLe) {
	}

	record RuptureSaisie(@NotNull UUID id, UUID produitId, @NotNull @Min(1) Integer quantite) {
	}

	record Etape(String statut, Instant le) {
	}

	record VueHanout(UUID id, String nom, Position position, Integer distanceM, Long solde, Instant derniereVenteLe) {
	}

	record VueAmbulant(String nom, String telephone) {
	}

	record DemandeDepannage(UUID id, String statut, Referentiel.Produit produit, String photoUrl, int quantite, Instant creeLe,
			boolean rayonElargi, Instant expireLe, VueHanout hanout, VueAmbulant ambulant, UUID documentId, List<Etape> historique) {
	}

	/** Événements de l'outbox, notifiés par {@link NotificationsDepannage}. */
	record DemandeEnvoyee(UUID demandeId, List<UUID> ambulants) {
	}

	record DemandeChangee(UUID demandeId, String statut) {
	}

	static final TypeReference<List<Etape>> HISTORIQUE = new TypeReference<>() {
	};

	private final JdbcClient jdbc;
	private final Json json;
	private final Photos photos;
	private final Referentiel referentiel;
	private final Comptes comptes;
	private final Stock stock;
	private final Ventes ventes;
	private final ApplicationEventPublisher evenements;
	private final int rayonM;
	private final int rayonElargiM;
	private final Duration delai;

	Depannage(JdbcClient jdbc, Json json, Photos photos, Referentiel referentiel, Comptes comptes, Stock stock, Ventes ventes,
			ApplicationEventPublisher evenements, @Value("${daba.depannage.rayon-m}") int rayonM,
			@Value("${daba.depannage.rayon-elargi-m}") int rayonElargiM,
			@Value("${daba.depannage.delai-acceptation}") Duration delai) {
		this.jdbc = jdbc;
		this.json = json;
		this.photos = photos;
		this.referentiel = referentiel;
		this.comptes = comptes;
		this.stock = stock;
		this.ventes = ventes;
		this.evenements = evenements;
		this.rayonM = rayonM;
		this.rayonElargiM = rayonElargiM;
		this.delai = delai;
	}

	// ---------------------------------------------------------------- disponibilité (CNDP)

	Disponibilite disponibilite(UUID ambulantId) {
		return jdbc.sql("SELECT * FROM disponibilite WHERE ambulant_id = :a").param("a", ambulantId)
			.query((rs, i) -> new Disponibilite(rs.getBoolean("disponible"), instant(rs, "depuis"), instant(rs, "position_le")))
			.optional().orElse(new Disponibilite(false, null, null));
	}

	/**
	 * La position n'est acceptée qu'avec la disponibilité active et le consentement géolocalisation
	 * (loi 09-08) ; couper la disponibilité efface la dernière position.
	 */
	@Transactional
	Disponibilite changerDisponibilite(UUID ambulantId, DisponibiliteSaisie saisie) {
		if (saisie.disponible() != (saisie.position() != null)) {
			throw Probleme.requete(saisie.disponible() ? "position requise quand disponible" : "position refusée quand indisponible");
		}
		if (saisie.disponible() && !comptes.geolocalisationConsentie(ambulantId)) {
			throw Probleme.regle("CNDP", "Consentement à la géolocalisation requis (PUT /v1/moi/consentement)");
		}
		if (!saisie.disponible()) {
			couper(ambulantId);
			return disponibilite(ambulantId);
		}
		jdbc.sql("""
				INSERT INTO disponibilite (ambulant_id, disponible, depuis, position, position_le)
				VALUES (:a, true, now(), %s, now())
				ON CONFLICT (ambulant_id) DO UPDATE SET disponible = true, position = excluded.position, position_le = now(),
				  depuis = CASE WHEN disponibilite.disponible THEN disponibilite.depuis ELSE now() END
				""".formatted(Position.sql("pos")))
			.param("a", ambulantId).param("posLat", saisie.position().lat()).param("posLon", saisie.position().lon()).update();
		return disponibilite(ambulantId);
	}

	private void couper(UUID ambulantId) {
		jdbc.sql("""
				INSERT INTO disponibilite (ambulant_id, disponible) VALUES (:a, false)
				ON CONFLICT (ambulant_id) DO UPDATE SET disponible = false, depuis = NULL, position = NULL, position_le = NULL
				""").param("a", ambulantId).update();
	}

	/** Retirer la géolocalisation (ou tout consentement) coupe la disponibilité et efface la position. */
	@ApplicationModuleListener
	void consentement(Comptes.ConsentementEnregistre evenement) {
		if (!evenement.traitementDonnees() || !evenement.geolocalisation()) {
			couper(evenement.utilisateurId());
		}
	}

	@Override
	public String cle() {
		return "disponibilite";
	}

	@Override
	public Object exporter(UUID utilisateurId) {
		return jdbc.sql("""
				SELECT disponible, depuis, ST_Y(position::geometry) AS lat, ST_X(position::geometry) AS lon, position_le
				FROM disponibilite WHERE ambulant_id = :a
				""").param("a", utilisateurId).query().listOfRows();
	}

	// ---------------------------------------------------------------- signalement et dispatch

	/** Signalement par le hanout ; rejouer le même id rend la demande existante (cree = faux). */
	@Transactional
	Map.Entry<DemandeDepannage, Boolean> signaler(Appelant hanout, RuptureSaisie saisie, MultipartFile photo) {
		var existante = jdbc.sql("SELECT hanout_id FROM demande WHERE id = :id").param("id", saisie.id()).query(UUID.class).optional();
		if (existante.isPresent()) {
			if (!existante.get().equals(hanout.hanoutId())) {
				throw Probleme.conflit("Identifiant de demande déjà utilisé");
			}
			return Map.entry(demande(saisie.id(), hanout), false);
		}
		if (saisie.produitId() == null && photo == null) {
			throw Probleme.requete("produitId ou photo requis");
		}
		if (saisie.produitId() != null && referentiel.produit(saisie.produitId()).isEmpty()) {
			throw Probleme.requete("Produit inconnu");
		}
		var fiche = referentiel.hanout(hanout.hanoutId()).orElseThrow(Probleme::introuvable);
		if (fiche.position() == null) {
			throw Probleme.regle("RG-D1", "Position du hanout inconnue : dépannage impossible");
		}
		var photoId = photo == null ? null : photos.enregistrer(photo);
		jdbc.sql("""
				INSERT INTO demande (id, hanout_id, hanout_utilisateur_id, produit_id, photo_id, quantite, position, ville, rayon_m,
				  expire_le, historique)
				VALUES (:id, :h, :u, :p, :photo, :q, %s, :ville, :rayon, :expire, :historique::jsonb)
				""".formatted(Position.sql("pos")))
			.param("id", saisie.id()).param("h", hanout.hanoutId()).param("u", hanout.utilisateurId())
			.param("p", saisie.produitId()).param("photo", photoId).param("q", saisie.quantite())
			.param("posLat", fiche.position().lat()).param("posLon", fiche.position().lon()).param("ville", fiche.ville())
			.param("rayon", rayonM).param("expire", Timestamp.from(Instant.now().plus(delai)))
			.param("historique", json.ecrire(List.of(new Etape("EN_ATTENTE", Instant.now()))))
			.update();
		dispatcher(saisie.id());
		return Map.entry(demande(saisie.id(), hanout), true);
	}

	/**
	 * RG-D1 : ambulants disponibles dans le rayon, ayant consenti à la géolocalisation et le produit
	 * en stock déclaré (sans produit précis, signalement en photo : tous les disponibles du rayon).
	 */
	private void dispatcher(UUID demandeId) {
		var d = jdbc.sql("SELECT produit_id, rayon_m FROM demande WHERE id = :id").param("id", demandeId)
			.query((rs, i) -> Map.entry(java.util.Optional.ofNullable(rs.getObject("produit_id", UUID.class)), rs.getInt("rayon_m")))
			.single();
		var proches = jdbc.sql("""
				SELECT a.ambulant_id, ST_Distance(a.position, d.position) AS distance
				FROM disponibilite a, demande d
				WHERE d.id = :id AND a.disponible AND ST_DWithin(a.position, d.position, :rayon)
				  AND NOT EXISTS (SELECT 1 FROM demande_destinataire x WHERE x.demande_id = d.id AND x.ambulant_id = a.ambulant_id)
				""").param("id", demandeId).param("rayon", d.getValue())
			.query((rs, i) -> Map.entry(rs.getObject(1, UUID.class), (int) Math.round(rs.getDouble(2)))).list().stream()
			.filter(a -> comptes.geolocalisationConsentie(a.getKey()))
			.toList();
		var avecStock = d.getKey().map(p -> stock.ambulantsAvecStock(p, proches.stream().map(Map.Entry::getKey).toList())).orElse(null);
		var retenus = proches.stream().filter(a -> avecStock == null || avecStock.contains(a.getKey())).toList();
		for (var a : retenus) {
			jdbc.sql("INSERT INTO demande_destinataire (demande_id, ambulant_id, distance_m) VALUES (:d, :a, :m)")
				.param("d", demandeId).param("a", a.getKey()).param("m", a.getValue()).update();
		}
		if (!retenus.isEmpty()) {
			evenements.publishEvent(new DemandeEnvoyee(demandeId, retenus.stream().map(Map.Entry::getKey).toList()));
		}
	}

	// ---------------------------------------------------------------- actions de l'ambulant (en ligne)

	/** RG-D2 : le premier qui accepte prend la demande ; les suivants reçoivent 409. */
	@Transactional
	DemandeDepannage accepter(Appelant ambulant, UUID demandeId) {
		exigerProposee(ambulant.utilisateurId(), demandeId);
		var prise = jdbc.sql("""
				UPDATE demande SET statut = 'ACCEPTEE', ambulant_id = :a,
				  historique = historique || jsonb_build_array(jsonb_build_object('statut', 'ACCEPTEE', 'le', now()))
				WHERE id = :id AND statut = 'EN_ATTENTE'
				""").param("a", ambulant.utilisateurId()).param("id", demandeId).update() > 0;
		if (!prise) {
			var preneur = jdbc.sql("SELECT ambulant_id FROM demande WHERE id = :id").param("id", demandeId).query(UUID.class).optional();
			if (preneur.isEmpty() || !ambulant.utilisateurId().equals(preneur.get())) {
				throw Probleme.conflit("RG-D2", "Demande déjà prise ou close");
			}
			return demande(demandeId, ambulant);
		}
		evenements.publishEvent(new DemandeChangee(demandeId, "ACCEPTEE"));
		return demande(demandeId, ambulant);
	}

	@Transactional
	void refuser(Appelant ambulant, UUID demandeId) {
		exigerProposee(ambulant.utilisateurId(), demandeId);
		var statut = jdbc.sql("SELECT statut FROM demande WHERE id = :id").param("id", demandeId).query(String.class).single();
		if (!"EN_ATTENTE".equals(statut)) {
			throw Probleme.conflit("RG-D2", "Demande déjà prise ou close");
		}
		jdbc.sql("UPDATE demande_destinataire SET refusee = true WHERE demande_id = :d AND ambulant_id = :a")
			.param("d", demandeId).param("a", ambulant.utilisateurId()).update();
	}

	@Transactional
	DemandeDepannage enRoute(Appelant ambulant, UUID demandeId) {
		exigerProposee(ambulant.utilisateurId(), demandeId);
		var ok = jdbc.sql("""
				UPDATE demande SET statut = 'EN_ROUTE',
				  historique = historique || jsonb_build_array(jsonb_build_object('statut', 'EN_ROUTE', 'le', now()))
				WHERE id = :id AND ambulant_id = :a AND statut = 'ACCEPTEE'
				""").param("id", demandeId).param("a", ambulant.utilisateurId()).update() > 0;
		if (!ok) {
			throw Probleme.conflit("Seul l'ambulant qui a pris la demande la passe en route, après l'acceptation");
		}
		evenements.publishEvent(new DemandeChangee(demandeId, "EN_ROUTE"));
		return demande(demandeId, ambulant);
	}

	/** Une demande proposée à cet ambulant (ou qu'il a prise) ; sinon 404, comme une demande inexistante. */
	private void exigerProposee(UUID ambulantId, UUID demandeId) {
		if (jdbc.sql("SELECT count(*) FROM demande_destinataire WHERE demande_id = :d AND ambulant_id = :a")
			.param("d", demandeId).param("a", ambulantId).query(Long.class).single() == 0) {
			throw Probleme.introuvable();
		}
	}

	/**
	 * RG-D4 : la vente portant {@code demandeDepannageId} livre la demande. Livrée par un autre
	 * ambulant que celui qui l'a prise : la vente reste, l'anomalie DEMANDE_NON_ATTRIBUEE est levée.
	 */
	@Transactional
	public List<Anomalie> livraison(Geste geste, UUID demandeId, UUID documentId) {
		var preneur = jdbc.sql("SELECT ambulant_id, statut FROM demande WHERE id = :id FOR UPDATE").param("id", demandeId)
			.query((rs, i) -> Map.entry(java.util.Optional.ofNullable(rs.getObject(1, UUID.class)), rs.getString(2)))
			.optional().orElseThrow(() -> Probleme.regle("RG-D4", "Demande de dépannage inconnue"));
		if (!preneur.getKey().map(geste.ambulantId()::equals).orElse(false)
				|| !List.of("ACCEPTEE", "EN_ROUTE").contains(preneur.getValue())) {
			return List.of(Anomalie.de("DEMANDE_NON_ATTRIBUEE", "RG-D4",
					"Livraison d'une demande " + preneur.getValue() + " non attribuée à cet ambulant"));
		}
		jdbc.sql("""
				UPDATE demande SET statut = 'LIVREE', document_id = :doc, livree_le = :le,
				  historique = historique || jsonb_build_array(jsonb_build_object('statut', 'LIVREE', 'le', CAST(:le AS timestamptz)))
				WHERE id = :id
				""").param("doc", documentId).param("le", Timestamp.from(geste.horodatage())).param("id", demandeId).update();
		evenements.publishEvent(new DemandeChangee(demandeId, "LIVREE"));
		return List.of();
	}

	/** RG-D3 : délai écoulé sans acceptation → rayon élargi une fois, puis SANS_REPONSE et hanout prévenu. */
	@Scheduled(fixedDelay = 30_000)
	@Transactional
	public void delais() {
		var expirees = jdbc.sql("SELECT id, rayon_elargi FROM demande WHERE statut = 'EN_ATTENTE' AND expire_le <= now() FOR UPDATE SKIP LOCKED")
			.query((rs, i) -> Map.entry(rs.getObject(1, UUID.class), rs.getBoolean(2))).list();
		for (var d : expirees) {
			if (!d.getValue()) {
				jdbc.sql("UPDATE demande SET rayon_m = :r, rayon_elargi = true, expire_le = :e WHERE id = :id")
					.param("r", rayonElargiM).param("e", Timestamp.from(Instant.now().plus(delai))).param("id", d.getKey()).update();
				dispatcher(d.getKey());
			}
			else {
				jdbc.sql("""
						UPDATE demande SET statut = 'SANS_REPONSE',
						  historique = historique || jsonb_build_array(jsonb_build_object('statut', 'SANS_REPONSE', 'le', now()))
						WHERE id = :id
						""").param("id", d.getKey()).update();
				evenements.publishEvent(new DemandeChangee(d.getKey(), "SANS_REPONSE"));
			}
		}
	}

	// ---------------------------------------------------------------- lectures

	/** Le hanout voit ses demandes ; l'ambulant celles qui lui sont proposées (non refusées, non prises par un autre) et les siennes. */
	private static String perimetre(Appelant appelant) {
		return appelant.a(Appelant.Role.HANOUT) ? "d.hanout_id = :moi" : """
				(d.ambulant_id = :moi OR (d.statut = 'EN_ATTENTE' AND EXISTS (SELECT 1 FROM demande_destinataire x
				  WHERE x.demande_id = d.id AND x.ambulant_id = :moi AND NOT x.refusee)))
				""";
	}

	private static UUID moi(Appelant appelant) {
		return appelant.a(Appelant.Role.HANOUT) ? appelant.hanoutId() : appelant.utilisateurId();
	}

	DemandeDepannage demande(UUID id, Appelant appelant) {
		return jdbc.sql("SELECT d.*, ST_Y(d.position::geometry) AS lat, ST_X(d.position::geometry) AS lon FROM demande d WHERE d.id = :id AND "
				+ perimetre(appelant))
			.param("id", id).param("moi", moi(appelant))
			.query((rs, i) -> ligne(rs, appelant)).optional().orElseThrow(Probleme::introuvable);
	}

	Page<DemandeDepannage> demandes(Appelant appelant, String statut, int page, int taille) {
		var where = " FROM demande d WHERE " + perimetre(appelant) + " AND (CAST(:s AS text) IS NULL OR d.statut = :s)";
		var total = jdbc.sql("SELECT count(*)" + where).param("moi", moi(appelant)).param("s", statut).query(Long.class).single();
		var contenu = jdbc.sql("SELECT d.*, ST_Y(d.position::geometry) AS lat, ST_X(d.position::geometry) AS lon" + where
				+ " ORDER BY d.cree_le DESC LIMIT :taille OFFSET :offset")
			.param("moi", moi(appelant)).param("s", statut).param("taille", taille).param("offset", page * taille)
			.query((rs, i) -> ligne(rs, appelant)).list();
		return Page.de(page, taille, total, contenu);
	}

	DemandeDepannage ligne(ResultSet rs, Appelant appelant) throws SQLException {
		var id = rs.getObject("id", UUID.class);
		var hanoutId = rs.getObject("hanout_id", UUID.class);
		var preneur = rs.getObject("ambulant_id", UUID.class);
		var produit = rs.getObject("produit_id", UUID.class);
		VueHanout hanout = null;
		VueAmbulant ambulant = null;
		if (appelant.a(Appelant.Role.AMBULANT)) {
			var distance = jdbc.sql("SELECT distance_m FROM demande_destinataire WHERE demande_id = :d AND ambulant_id = :a")
				.param("d", id).param("a", appelant.utilisateurId()).query(Integer.class).optional().orElse(null);
			hanout = new VueHanout(hanoutId, referentiel.hanout(hanoutId).map(Referentiel.Hanout::nom).orElse("—"),
					new Position(rs.getDouble("lat"), rs.getDouble("lon")), distance, ventes.solde(appelant.utilisateurId(), hanoutId),
					ventes.derniereVente(appelant.utilisateurId(), hanoutId).orElse(null));
		}
		else if (preneur != null) {
			ambulant = new VueAmbulant(comptes.nom(preneur), comptes.telephone(preneur).orElse(null));
		}
		return new DemandeDepannage(id, rs.getString("statut"), produit == null ? null : referentiel.produit(produit).orElse(null),
				photos.url(rs.getObject("photo_id", UUID.class)), rs.getInt("quantite"), rs.getTimestamp("cree_le").toInstant(),
				rs.getBoolean("rayon_elargi"), rs.getTimestamp("expire_le").toInstant(), hanout, ambulant,
				rs.getObject("document_id", UUID.class), json.lire(rs.getString("historique"), HISTORIQUE));
	}

	/** Contexte d'une demande pour les notifications. */
	record Resume(UUID hanoutUtilisateurId, UUID produitId, int quantite, UUID ambulantId) {
	}

	Resume resume(UUID demandeId) {
		return jdbc.sql("SELECT hanout_utilisateur_id, produit_id, quantite, ambulant_id FROM demande WHERE id = :id")
			.param("id", demandeId)
			.query((rs, i) -> new Resume(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getInt(3), rs.getObject(4, UUID.class)))
			.single();
	}

	List<Map.Entry<UUID, Integer>> destinataires(UUID demandeId) {
		return jdbc.sql("SELECT ambulant_id, distance_m FROM demande_destinataire WHERE demande_id = :d AND NOT refusee")
			.param("d", demandeId).query((rs, i) -> Map.entry(rs.getObject(1, UUID.class), rs.getInt(2))).list();
	}

	private static Instant instant(ResultSet rs, String colonne) throws SQLException {
		var t = rs.getTimestamp(colonne);
		return t == null ? null : t.toInstant();
	}

}

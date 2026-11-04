package ma.daba.comptes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import ma.daba.TestIntegration;
import ma.daba.canaux.Canaux;
import ma.daba.commun.Acces;
import ma.daba.commun.Appelant.Role;
import tools.jackson.databind.JsonNode;

/** Back-office de l'administrateur plateforme (§2) : organisations, support, déconnexion à distance et audit (§10). */
class AdministrationTest extends TestIntegration {

	@Autowired
	Comptes comptes;

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	RequestMappingHandlerMapping routes;

	private String codeEnvoye(String telephone) {
		var message = ArgumentCaptor.forClass(Canaux.Message.class);
		verify(sms, org.mockito.Mockito.atLeastOnce()).envoyer(message.capture());
		return message.getAllValues().stream().filter(m -> telephone.equals(m.destinataire().telephone())).toList().getLast()
			.texte().replaceAll(".*code est ([0-9]{6}).*", "$1");
	}

	private org.springframework.test.web.servlet.assertj.MvcTestResult connexion(String telephone, UUID appareil) {
		post(null, "/v1/auth/otp", m("telephone", telephone));
		return post(null, "/v1/auth/otp/verify", m("telephone", telephone, "code", codeEnvoye(telephone), "appareilId", appareil));
	}

	private Compte compteDe(JsonNode session, UUID appareil) {
		var u = session.get("utilisateur");
		return new Compte(UUID.fromString(u.get("id").asString()), UUID.fromString(u.get("organisationId").asString()), appareil,
				u.get("telephone").asString(), session.get("accessToken").asString(), null, null);
	}

	private List<String> operationsAudit(UUID auteur) {
		return StreamSupport.stream(lire(get(admin(), "/v1/admin/audit?auteurId=" + auteur)).get("contenu").spliterator(), false)
			.map(e -> e.get("operation").asString()).toList();
	}

	private static String type(org.springframework.test.web.servlet.assertj.MvcTestResult r) throws Exception {
		return r.getResponse().getContentAsString();
	}

	// ---------------------------------------------------------------- CNDP : ADMIN seulement

	@Test
	void cndp_touteOperationAdminEstReserveeAuSeulRoleAdmin() {
		var admins = routes.getHandlerMethods().entrySet().stream()
			.filter(e -> e.getKey().getPatternValues().stream().anyMatch(p -> p.startsWith("/v1/admin/")))
			.toList();
		assertThat(admins).hasSizeGreaterThanOrEqualTo(12);
		for (var route : admins) {
			var roles = route.getValue().getMethodAnnotation(Acces.Roles.class);
			assertThat(roles).as(route.getKey().toString()).isNotNull();
			assertThat(roles.value()).as(route.getKey().toString()).containsExactly(Role.ADMIN);
			assertThat(route.getValue().hasMethodAnnotation(Acces.Public.class)).isFalse();
		}

		var cible = ambulant();
		var appels = List.of(
				new Object[] { HttpMethod.GET, "/v1/admin/organisations", null },
				new Object[] { HttpMethod.GET, "/v1/admin/organisations/" + cible.organisationId(), null },
				new Object[] { HttpMethod.POST, "/v1/admin/organisations/" + cible.organisationId() + "/utilisateurs", m("telephone", telephone()) },
				new Object[] { HttpMethod.GET, "/v1/admin/utilisateurs?q=" + cible.telephone().substring(4), null },
				new Object[] { HttpMethod.POST, "/v1/admin/utilisateurs/" + cible.id() + "/desactivation", null },
				new Object[] { HttpMethod.POST, "/v1/admin/utilisateurs/" + cible.id() + "/reactivation", null },
				new Object[] { HttpMethod.POST, "/v1/admin/appareils/" + cible.appareilId() + "/revocation", null },
				new Object[] { HttpMethod.GET, "/v1/admin/audit", null },
				new Object[] { HttpMethod.GET, "/v1/admin/authentification", null },
				new Object[] { HttpMethod.PUT, "/v1/admin/authentification", m("roles", List.of(), "organisations", List.of()) },
				new Object[] { HttpMethod.POST, "/v1/admin/utilisateurs/" + cible.id() + "/reinitialisation-mot-de-passe", null });
		for (var autre : List.of(ambulant(), compteHanout(hanout(33.5, -7.6)), grossiste(), grandClient())) {
			for (var a : appels) {
				assertThat(appel((HttpMethod) a[0], autre, (String) a[1], a[2])).as(a[1].toString()).hasStatus(403)
					.bodyJson().extractingPath("$.type").asString().endsWith("role-non-autorise");
			}
		}
		for (var a : appels) {
			assertThat(appel((HttpMethod) a[0], null, (String) a[1], a[2])).hasStatus(401);
		}
		// Aucun effet des appels refusés.
		assertThat(jdbc.sql("SELECT revoque FROM appareil WHERE id = :a").param("a", cible.appareilId()).query(Boolean.class).single())
			.isFalse();
		assertThat(get(cible, "/v1/produits")).hasStatusOk();
	}

	// ---------------------------------------------------------------- organisations

	@Test
	void organisationsListeesDetailleesEtUtilisateurAjoute() {
		var admin = admin();
		var nom = "Grossiste Admin " + UUID.randomUUID();
		var creee = lire(post(admin, "/v1/admin/organisations", m("type", "GROSSISTE", "nom", nom,
				"utilisateurs", List.of(m("telephone", telephone(), "nom", "Gérant")), "position", m("lat", 33.59, "lon", -7.61),
				"ville", "Casablanca")));
		var id = creee.get("id").asString();
		post(admin, "/v1/admin/organisations", m("type", "GRAND_CLIENT", "nom", "Marque " + nom,
				"utilisateurs", List.of(m("telephone", telephone()))));

		var page = lire(get(admin, "/v1/admin/organisations?type=GROSSISTE&q=" + nom.substring(10)));
		assertThat(page.get("total").asLong()).isEqualTo(1);
		var item = page.get("contenu").get(0);
		assertThat(item.get("id").asString()).isEqualTo(id);
		assertThat(item.get("nombreUtilisateurs").asInt()).isEqualTo(1);
		assertThat(item.get("grossiste").get("ville").asString()).isEqualTo("Casablanca");
		assertThat(lire(get(admin, "/v1/admin/organisations?q=" + nom.substring(10))).get("total").asLong()).isEqualTo(2);
		// Les organisations personnelles (ambulant, hanout, admin) ne sont pas listées.
		var ambulant = ambulant();
		assertThat(lire(get(admin, "/v1/admin/organisations?taille=200")).toString()).doesNotContain(ambulant.organisationId().toString());
		assertThat(get(admin, "/v1/admin/organisations?type=AMBULANT")).hasStatus(400);

		var tel = telephone();
		var ajoute = post(admin, "/v1/admin/organisations/" + id + "/utilisateurs", m("telephone", tel, "nom", "Caissier"));
		assertThat(ajoute).hasStatus(201);
		var utilisateur = lire(ajoute);
		assertThat(utilisateur.get("roles").get(0).asString()).isEqualTo("GROSSISTE");
		assertThat(utilisateur.get("organisation").get("id").asString()).isEqualTo(id);
		assertThat(utilisateur.get("actif").asBoolean()).isTrue();
		assertThat(utilisateur.get("appareils")).isEmpty();
		verify(sms, org.mockito.Mockito.never()).envoyer(argThat(msg -> tel.equals(msg.destinataire().telephone())));

		// 409 : numéro déjà inscrit (tous rôles), organisation personnelle ; 404 : organisation inconnue.
		assertThat(post(admin, "/v1/admin/organisations/" + id + "/utilisateurs", m("telephone", tel))).hasStatus(409);
		assertThat(post(admin, "/v1/admin/organisations/" + id + "/utilisateurs", m("telephone", ambulant.telephone()))).hasStatus(409);
		assertThat(post(admin, "/v1/admin/organisations/" + ambulant.organisationId() + "/utilisateurs", m("telephone", telephone())))
			.hasStatus(409);
		assertThat(post(admin, "/v1/admin/organisations/" + admin.organisationId() + "/utilisateurs", m("telephone", telephone())))
			.hasStatus(409);
		assertThat(post(admin, "/v1/admin/organisations/" + UUID.randomUUID() + "/utilisateurs", m("telephone", telephone())))
			.hasStatus(404);
		assertThat(post(admin, "/v1/admin/organisations/" + id + "/utilisateurs", m("telephone", "0612345678"))).hasStatus(400);

		var detail = lire(get(admin, "/v1/admin/organisations/" + id));
		assertThat(detail.get("nombreUtilisateurs").asInt()).isEqualTo(2);
		assertThat(detail.get("utilisateurs")).hasSize(2);
		assertThat(detail.get("utilisateurs").get(1).get("telephone").asString()).isEqualTo(tel);
		assertThat(lire(get(admin, "/v1/admin/organisations/" + ambulant.organisationId())).get("type").asString()).isEqualTo("AMBULANT");
		assertThat(get(admin, "/v1/admin/organisations/" + UUID.randomUUID())).hasStatus(404);

		// L'utilisateur ajouté se connecte par OTP avec le rôle de l'organisation.
		var session = lire(connexion(tel, UUID.randomUUID()));
		assertThat(session.get("utilisateur").get("roles").get(0).asString()).isEqualTo("GROSSISTE");
		assertThat(session.get("utilisateur").get("organisationId").asString()).isEqualTo(id);

		assertThat(operationsAudit(admin.id())).containsExactly("UTILISATEUR_AJOUTE", "ORGANISATION_CREEE", "ORGANISATION_CREEE");
	}

	// ---------------------------------------------------------------- support : utilisateurs et appareils

	@Test
	void cndp_rechercheParTelephoneOuNomAvecConsentementEtAppareilsSansAucunSecret() throws Exception {
		var admin = admin();
		var tel = telephone();
		var appareil = UUID.randomUUID();
		var ambulant = compteDe(lire(connexion(tel, appareil)), appareil);
		jdbc.sql("INSERT INTO consentement (utilisateur_id, version_texte, traitement_donnees, geolocalisation, langue) VALUES (:u, 'v3', true, false, 'ar')")
			.param("u", ambulant.id()).update();
		assertThat(put(ambulant, "/v1/appareils/" + appareil + "/push", m("jeton", "secret-fcm-" + appareil, "plateforme", "ANDROID")))
			.hasStatus(204);

		// Toutes les formes du numéro : 06…, 2126…, +2126…, ou une partie.
		var local = "0" + tel.substring(4);
		for (var q : List.of(local, tel.substring(1), tel, tel.substring(6))) {
			var page = lire(get(admin, "/v1/admin/utilisateurs?q=" + q));
			assertThat(page.get("contenu").toString()).as(q).contains(ambulant.id().toString());
		}
		var r = get(admin, "/v1/admin/utilisateurs?role=AMBULANT&q=" + local);
		var u = lire(r).get("contenu").get(0);
		assertThat(u.get("telephone").asString()).isEqualTo(tel);
		assertThat(u.get("roles").get(0).asString()).isEqualTo("AMBULANT");
		assertThat(u.get("organisation").get("type").asString()).isEqualTo("AMBULANT");
		assertThat(u.get("consentement").get("versionTexte").asString()).isEqualTo("v3");
		assertThat(u.get("consentement").get("traitementDonnees").asBoolean()).isTrue();
		assertThat(u.get("consentement").get("geolocalisation").asBoolean()).isFalse();
		assertThat(u.get("consentement").has("enregistreLe")).isTrue();
		var a = u.get("appareils").get(0);
		assertThat(a.get("appareilId").asString()).isEqualTo(appareil.toString());
		assertThat(a.get("plateforme").asString()).isEqualTo("ANDROID");
		assertThat(a.get("sessionOuverte").asBoolean()).isTrue();
		assertThat(a.get("pushActif").asBoolean()).isTrue();
		assertThat(a.get("revoque").asBoolean()).isFalse();
		assertThat(a.get("prefixeNumerotation").asString()).matches("^[A-Z0-9]{3,6}$");
		assertThat(a.has("vuLe")).isTrue();
		assertThat(a.has("derniereConnexion")).isTrue();
		// Jamais de jeton push, de session, de jeton d'accès ni de code OTP.
		var corps = type(r);
		assertThat(corps).doesNotContain("secret-fcm").doesNotContain(ambulant.jeton());
		var session = jdbc.sql("SELECT session::text FROM appareil WHERE id = :a").param("a", appareil).query(String.class).single();
		assertThat(corps).doesNotContain(session);
		assertThat(a.properties().stream().map(java.util.Map.Entry::getKey).toList())
			.isSubsetOf("appareilId", "plateforme", "langue", "prefixeNumerotation", "creeLe", "derniereConnexion", "vuLe",
					"compteActuel", "sessionOuverte", "pushActif", "revoque", "revoqueLe", "autresComptes");

		// Recherche par nom, filtre par rôle ; un hanout et son absence de consentement.
		var hanout = compteHanout(hanout(33.5, -7.6));
		jdbc.sql("DELETE FROM consentement WHERE utilisateur_id = :u").param("u", hanout.id()).update();
		var parNom = lire(get(admin, "/v1/admin/utilisateurs?role=HANOUT&q=" + hanout.telephone().substring(8)));
		var h = StreamSupport.stream(parNom.get("contenu").spliterator(), false)
			.filter(x -> x.get("id").asString().equals(hanout.id().toString())).findFirst().orElseThrow();
		assertThat(h.has("consentement")).isFalse();
		assertThat(h.get("hanoutId").asString()).isEqualTo(hanout.hanoutId().toString());
		assertThat(lire(get(admin, "/v1/admin/utilisateurs?role=GROSSISTE&q=" + local)).get("total").asLong()).isZero();
		assertThat(get(admin, "/v1/admin/utilisateurs?q=x")).hasStatus(400);
	}

	@Test
	void desactivationFermeLesSessionsEmpecheLaConnexionPuisReactivation() throws Exception {
		var admin = admin();
		var tel = telephone();
		var appareil = UUID.randomUUID();
		var session = lire(connexion(tel, appareil));
		var ambulant = compteDe(session, appareil);
		jdbc.sql("INSERT INTO consentement (utilisateur_id, version_texte, traitement_donnees, geolocalisation) VALUES (:u, 'v3', true, true)")
			.param("u", ambulant.id()).update();
		assertThat(put(ambulant, "/v1/appareils/" + appareil + "/push", m("jeton", "fcm-" + appareil, "plateforme", "ANDROID"))).hasStatus(204);
		assertThat(put(ambulant, "/v1/ambulant/disponibilite", m("disponible", true, "position", m("lat", 33.58, "lon", -7.6))))
			.hasStatusOk();
		assertThat(comptes.ambulants()).contains(ambulant.id());

		var r = post(admin, "/v1/admin/utilisateurs/" + ambulant.id() + "/desactivation", null);
		assertThat(r).hasStatusOk();
		assertThat(lire(r).get("actif").asBoolean()).isFalse();
		assertThat(lire(r).has("desactiveLe")).isTrue();
		assertThat(lire(r).get("appareils").get(0).get("sessionOuverte").asBoolean()).isFalse();
		assertThat(lire(r).get("appareils").get(0).get("pushActif").asBoolean()).isFalse();
		// Idempotent : pas de seconde entrée d'audit.
		assertThat(post(admin, "/v1/admin/utilisateurs/" + ambulant.id() + "/desactivation", null)).hasStatusOk();

		// Le jeton en cours est refusé (403, sans effacement), le refresh aussi, et la connexion OTP.
		assertThat(get(ambulant, "/v1/produits")).hasStatus(403).bodyJson().extractingPath("$.type").asString()
			.endsWith("utilisateur-desactive");
		assertThat(post(null, "/v1/auth/refresh", m("refreshToken", session.get("refreshToken").asString()))).hasStatus(401);
		assertThat(connexion(tel, appareil)).hasStatus(403).bodyJson().extractingPath("$.type").asString()
			.endsWith("utilisateur-desactive");
		assertThat(connexion(tel, UUID.randomUUID())).hasStatus(403);
		assertThat(comptes.destinataire(ambulant.id()).jetonsPush()).isEmpty();
		assertThat(comptes.ambulants()).doesNotContain(ambulant.id());
		Boolean disponible = true;
		for (int i = 0; i < 50 && Boolean.TRUE.equals(disponible); i++) {
			Thread.sleep(100);
			disponible = jdbc.sql("SELECT disponible FROM disponibilite WHERE ambulant_id = :a").param("a", ambulant.id())
				.query(Boolean.class).single();
		}
		assertThat(disponible).isFalse();

		// Un administrateur ne se désactive pas lui-même ; utilisateur inconnu : 404.
		assertThat(post(admin, "/v1/admin/utilisateurs/" + admin.id() + "/desactivation", null)).hasStatus(409);
		assertThat(post(admin, "/v1/admin/utilisateurs/" + UUID.randomUUID() + "/desactivation", null)).hasStatus(404);
		assertThat(post(admin, "/v1/admin/utilisateurs/" + UUID.randomUUID() + "/reactivation", null)).hasStatus(404);

		var reactive = post(admin, "/v1/admin/utilisateurs/" + ambulant.id() + "/reactivation", null);
		assertThat(reactive).hasStatusOk();
		assertThat(lire(reactive).get("actif").asBoolean()).isTrue();
		assertThat(lire(reactive).has("desactiveLe")).isFalse();
		var nouvelle = connexion(tel, appareil);
		assertThat(nouvelle).hasStatusOk();
		assertThat(get(compteDe(lire(nouvelle), appareil), "/v1/produits")).hasStatusOk();

		assertThat(operationsAudit(admin.id())).containsExactly("UTILISATEUR_REACTIVE", "UTILISATEUR_DESACTIVE");
	}

	/** §10 : téléphone perdu, révoqué par l'administrateur ; l'app reçoit appareil-revoque et efface ses données. */
	@Test
	void revocationDUnAppareilPerduDeLaSynchroAuRefreshPuisNouvelAppareil() throws Exception {
		var admin = admin();
		var tel = telephone();
		var perdu = UUID.randomUUID();
		var session = lire(connexion(tel, perdu));
		var ambulant = compteDe(session, perdu);
		var prefixePerdu = session.get("prefixeNumerotation").asString();
		jdbc.sql("INSERT INTO consentement (utilisateur_id, version_texte, traitement_donnees, geolocalisation) VALUES (:u, 'v3', true, true)")
			.param("u", ambulant.id()).update();
		assertThat(put(ambulant, "/v1/appareils/" + perdu + "/push", m("jeton", "fcm-" + perdu, "plateforme", "ANDROID"))).hasStatus(204);
		assertThat(put(ambulant, "/v1/ambulant/disponibilite", m("disponible", true, "position", m("lat", 33.58, "lon", -7.6))))
			.hasStatusOk();

		var r = post(admin, "/v1/admin/appareils/" + perdu + "/revocation", null);
		assertThat(r).hasStatusOk();
		var a = lire(r).get("appareils").get(0);
		assertThat(lire(r).get("id").asString()).isEqualTo(ambulant.id().toString());
		assertThat(a.get("revoque").asBoolean()).isTrue();
		assertThat(a.has("revoqueLe")).isTrue();
		assertThat(a.get("sessionOuverte").asBoolean()).isFalse();
		assertThat(a.get("pushActif").asBoolean()).isFalse();
		assertThat(jdbc.sql("SELECT jeton_push IS NULL AND session IS NULL FROM appareil WHERE id = :a").param("a", perdu)
			.query(Boolean.class).single()).isTrue();
		assertThat(comptes.destinataire(ambulant.id()).jetonsPush()).isEmpty();
		// Sans effet la seconde fois ; appareil inconnu : 404.
		assertThat(post(admin, "/v1/admin/appareils/" + perdu + "/revocation", null)).hasStatusOk();
		assertThat(post(admin, "/v1/admin/appareils/" + UUID.randomUUID() + "/revocation", null)).hasStatus(404);

		// Prochain appel de l'app : la synchro et le refresh répondent 403 appareil-revoque (effacement local).
		assertThat(post(ambulant, "/v1/sync/operations", m("appareilId", perdu, "operations", List.of(m("type", "CHARGEMENT")))))
			.hasStatus(403).bodyJson().extractingPath("$.type").asString().endsWith("appareil-revoque");
		assertThat(get(ambulant, "/v1/sync/changements")).hasStatus(403);
		assertThat(post(null, "/v1/auth/refresh", m("refreshToken", session.get("refreshToken").asString())))
			.hasStatus(403).bodyJson().extractingPath("$.type").asString().endsWith("appareil-revoque");
		// Irréversible pour cet appareilId : une nouvelle connexion OTP n'y rouvre aucune session.
		assertThat(connexion(tel, perdu)).hasStatus(401).bodyJson().extractingPath("$.type").asString().endsWith("appareil-revoque");
		assertThat(jdbc.sql("SELECT session IS NULL AND revoque FROM appareil WHERE id = :a").param("a", perdu)
			.query(Boolean.class).single()).isTrue();

		Boolean disponible = true;
		for (int i = 0; i < 50 && Boolean.TRUE.equals(disponible); i++) {
			Thread.sleep(100);
			disponible = jdbc.sql("SELECT disponible FROM disponibilite WHERE ambulant_id = :a").param("a", ambulant.id())
				.query(Boolean.class).single();
		}
		assertThat(disponible).isFalse();

		// Après effacement, l'app tire un nouvel appareilId : la connexion refonctionne, avec un nouveau préfixe (RG-F1).
		var neuf = UUID.randomUUID();
		var nouvelle = connexion(tel, neuf);
		assertThat(nouvelle).hasStatusOk();
		assertThat(lire(nouvelle).get("prefixeNumerotation").asString()).isNotEqualTo(prefixePerdu);
		var remplacant = compteDe(lire(nouvelle), neuf);
		assertThat(get(remplacant, "/v1/produits")).hasStatusOk();
		var appareils = lire(get(admin, "/v1/admin/utilisateurs?q=" + tel.substring(1))).get("contenu").get(0).get("appareils");
		assertThat(appareils).hasSize(2);
		assertThat(appareils.toString()).contains(neuf.toString()).contains(perdu.toString());

		var entree = lire(get(admin, "/v1/admin/audit?operation=APPAREIL_REVOQUE&auteurId=" + admin.id())).get("contenu");
		assertThat(entree).hasSize(1);
		assertThat(entree.get(0).get("details").get("appareilId").asString()).isEqualTo(perdu.toString());
		assertThat(entree.get(0).get("details").get("utilisateurId").asString()).isEqualTo(ambulant.id().toString());
		assertThat(entree.get(0).get("auteur").get("id").asString()).isEqualTo(admin.id().toString());
		assertThat(entree.get(0).get("appareilId").asString()).isEqualTo(admin.appareilId().toString());
	}

	private JsonNode appareilDe(Compte admin, String telephone, UUID appareil) {
		var u = lire(get(admin, "/v1/admin/utilisateurs?q=" + telephone.substring(1))).get("contenu").get(0);
		return StreamSupport.stream(u.get("appareils").spliterator(), false)
			.filter(a -> a.get("appareilId").asString().equals(appareil.toString())).findFirst().orElseThrow();
	}

	/** Deux ambulants sur un même téléphone (RG-F1) : chacun garde l'appareil dans sa fiche ; la révocation (§10) les touche tous. */
	@Test
	void rgF1_telephonePartageVisibleDansChaqueCompteEtRevocationPourTousSesComptes() {
		var admin = admin();
		var partage = UUID.randomUUID();
		var telA = telephone();
		var telB = telephone();
		var sessionA = lire(connexion(telA, partage));
		// vuLe est posé dès la connexion, avant tout autre appel.
		var a = appareilDe(admin, telA, partage);
		assertThat(a.has("vuLe")).isTrue();
		assertThat(a.get("compteActuel").asBoolean()).isTrue();
		assertThat(a.get("sessionOuverte").asBoolean()).isTrue();
		assertThat(a.get("autresComptes")).isEmpty();

		var sessionB = lire(connexion(telB, partage));
		var b = compteDe(sessionB, partage);
		a = appareilDe(admin, telA, partage);
		assertThat(a.get("compteActuel").asBoolean()).isFalse();
		assertThat(a.get("sessionOuverte").asBoolean()).isFalse();
		assertThat(a.get("pushActif").asBoolean()).isFalse();
		assertThat(a.get("prefixeNumerotation").asString()).isEqualTo(sessionA.get("prefixeNumerotation").asString());
		assertThat(a.get("autresComptes").get(0).get("telephone").asString()).isEqualTo(telB);
		var vueB = appareilDe(admin, telB, partage);
		assertThat(vueB.get("compteActuel").asBoolean()).isTrue();
		assertThat(vueB.get("prefixeNumerotation").asString()).isEqualTo(sessionB.get("prefixeNumerotation").asString());
		assertThat(vueB.get("autresComptes").get(0).get("telephone").asString()).isEqualTo(telA);

		var r = lire(post(admin, "/v1/admin/appareils/" + partage + "/revocation", null));
		assertThat(r.get("id").asString()).isEqualTo(b.id().toString());
		var revoque = StreamSupport.stream(r.get("appareils").spliterator(), false)
			.filter(x -> x.get("appareilId").asString().equals(partage.toString())).findFirst().orElseThrow();
		assertThat(revoque.get("revoque").asBoolean()).isTrue();
		assertThat(revoque.get("autresComptes").get(0).get("id").asString()).isEqualTo(sessionA.get("utilisateur").get("id").asString());
		assertThat(appareilDe(admin, telA, partage).get("revoque").asBoolean()).isTrue();
		var details = lire(get(admin, "/v1/admin/audit?operation=APPAREIL_REVOQUE&auteurId=" + admin.id())).get("contenu").get(0)
			.get("details");
		assertThat(details.get("utilisateurIds").toString()).contains(b.id().toString())
			.contains(sessionA.get("utilisateur").get("id").asString());

		// Effacé pour tous : ni B ni A ne s'y reconnectent ; A refonctionne sur un nouvel appareil.
		assertThat(get(b, "/v1/produits")).hasStatus(403);
		assertThat(connexion(telA, partage)).hasStatus(401).bodyJson().extractingPath("$.type").asString().endsWith("appareil-revoque");
		assertThat(connexion(telA, UUID.randomUUID())).hasStatusOk();
	}

	// ---------------------------------------------------------------- audit

	@Test
	void journalAuditFiltreParDateAuteurEtOperationSansTelephone() {
		var admin = admin();
		var autre = admin();
		var debut = Instant.now().minusSeconds(1);
		var ambulant = ambulant();
		post(admin, "/v1/admin/utilisateurs/" + ambulant.id() + "/desactivation", null);
		post(autre, "/v1/admin/utilisateurs/" + ambulant.id() + "/reactivation", null);
		post(admin, "/v1/admin/organisations", m("type", "GRAND_CLIENT", "nom", "Marque audit", "utilisateurs", List.of(m("telephone", telephone()))));

		var tout = lire(get(admin, "/v1/admin/audit?auteurId=" + admin.id()));
		assertThat(tout.get("total").asLong()).isEqualTo(2);
		assertThat(tout.get("contenu").get(0).get("operation").asString()).isEqualTo("ORGANISATION_CREEE");
		assertThat(tout.get("contenu").get(1).get("details").get("utilisateurId").asString()).isEqualTo(ambulant.id().toString());
		assertThat(tout.get("contenu").get(1).get("auteur").get("nom").asString()).isNotBlank();
		assertThat(tout.toString()).doesNotContain(ambulant.telephone());

		assertThat(lire(get(admin, "/v1/admin/audit?operation=UTILISATEUR_REACTIVE&auteurId=" + autre.id())).get("total").asLong()).isEqualTo(1);
		assertThat(lire(get(admin, "/v1/admin/audit?operation=UTILISATEUR_REACTIVE&auteurId=" + admin.id())).get("total").asLong()).isZero();
		assertThat(lire(get(admin, "/v1/admin/audit?auteurId=" + admin.id() + "&depuis=" + debut)).get("total").asLong()).isEqualTo(2);
		assertThat(lire(get(admin, "/v1/admin/audit?auteurId=" + admin.id() + "&jusqua=" + debut)).get("total").asLong()).isZero();
		assertThat(lire(get(admin, "/v1/admin/audit?auteurId=" + admin.id() + "&taille=1&page=1")).get("contenu")).hasSize(1);
		assertThat(get(admin, "/v1/admin/audit?operation=pas-valide")).hasStatus(400);
		// Lecture seule : aucune écriture exposée.
		assertThat(post(admin, "/v1/admin/audit", m())).hasStatus(405);
	}

	@Test
	void importsDuReferentielJournalisesEtReferentielLisibleParLAdmin() {
		var admin = admin();
		var csv = "ean;libelle_fr;marque;categorie;conditionnement;unites\n" + "%013d".formatted(System.nanoTime() % 1_000_000_000_000L)
				+ ";Thé 200g;Marque import;THE;UNITE;1\n";
		var fichier = new org.springframework.mock.web.MockMultipartFile("fichier", "produits.csv", "text/csv", csv.getBytes());
		assertThat(mvc.post().uri("/v1/admin/produits/import").multipart().file(fichier)
			.header("Authorization", "Bearer " + admin.jeton()).exchange()).hasStatusOk();
		var entree = lire(get(admin, "/v1/admin/audit?operation=IMPORT_PRODUITS&auteurId=" + admin.id())).get("contenu").get(0);
		assertThat(entree.get("details").get("crees").asInt()).isEqualTo(1);

		// GET /v1/produits et /v1/hanouts : tout le référentiel, toutes organisations, pour l'administrateur.
		var a = produit(grandClient().organisationId());
		var b = produit(grandClient().organisationId());
		var produits = lire(get(admin, "/v1/produits?ids=" + a + "," + b));
		assertThat(produits.get("total").asLong()).isEqualTo(2);
		var h = hanout(33.5, -7.6);
		var tel = jdbc.sql("SELECT telephone FROM hanout WHERE id = :id").param("id", h).query(String.class).single();
		assertThat(lire(get(admin, "/v1/hanouts?q=" + tel.substring(4)))
			.get("contenu").get(0).get("id").asString()).isEqualTo(h.toString());
	}

}

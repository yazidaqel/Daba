# Back-end Daba

Référence : cahier des charges §8 (règles de gestion), §9 (notifications), §10 (non fonctionnel), §11 (architecture).

- Java 25 LTS, Spring Boot 4.1, monolithe modulaire : un package par module (`comptes`, `referentiel`, `stock`, `ventes` (karni inclus), `promos`, `depannage`, `canaux`). Un module n'accède pas aux tables d'un autre : il passe par son API Java publique.
- PostgreSQL 18 + PostGIS ; schéma versionné par Flyway, jamais de modification d'une migration déjà commitée.
- API REST documentée en OpenAPI : le contrat de référence est `../api/openapi.yaml`, tenu à jour à chaque changement d'endpoint.
- Événements : outbox PostgreSQL (tranché), via `spring-modulith-starter-jdbc` (registre de publication d'événements). Les modules communiquent par événements applicatifs Spring ; pas de Kafka au MVP.
- Multi-tenant : chaque requête est cloisonnée par organisation.
- Synchronisation mobile : les UUID viennent de l'appareil, les opérations sont idempotentes, conflits résolus côté serveur avec journal (pas d'écrasement silencieux, RG-S1).
- Facture / bon de livraison : jamais supprimé, seul un avoir l'annule ; numérotation continue par émetteur avec préfixe par appareil (RG-F1, RG-C4).
- Tout tourne en conteneur Docker ; aucun service propre à Azure.

## Commandes

Depuis `backend/`, avec un JDK 25 dans `JAVA_HOME` (le wrapper télécharge Maven).

- Build + tests : `./mvnw verify` (Testcontainers démarre `postgis/postgis:18-3.6` : Docker doit tourner)
- Un test : `./mvnw test -Dtest=ModulesTest`
- Lancer contre la base du compose : `docker compose up -d db` (à la racine), puis `./mvnw spring-boot:run`
- Image : `docker compose build backend` (à la racine)
- Configuration : `DB_URL`, `DB_USER`, `DB_PASSWORD` (défaut : `jdbc:postgresql://localhost:5433/daba`, `daba`/`daba`) ; `DABA_JWT_SECRET` (obligatoire hors poste de dev), `DABA_ADMIN_TELEPHONES` (numéros administrateurs), `DABA_LIENS_BASE_URL`, `DABA_CANAUX_ACTIFS`. Le reste est dans `application.properties`.
- Persistance : `JdbcClient` et SQL (pas de JPA). Les modules publient leurs événements dans l'outbox avec `@ApplicationModuleListener`.
- Tout ce que l'app ambulant doit recevoir hors ligne passe par `Journal.publier` (commun) ; les tests d'intégration étendent `TestIntegration`.
- En dev, les SMS (codes OTP compris) sont seulement écrits dans les logs (`FournisseursJournal`), tant qu'aucun vrai fournisseur n'est configuré ; ensuite JOURNAL n'écrit plus que le type et le numéro masqué, et ne peut plus être choisi pour un canal qui a un vrai fournisseur.
- Canaux (§9) : l'administrateur active ou coupe chaque canal (système, type de message, organisation du destinataire), choisit le routage principal + repli et le fournisseur par canal (`GET`/`PUT /v1/admin/canaux`, table `configuration_canaux`, journal d'audit `journal_audit`). `DABA_CANAUX_ACTIFS` et `daba.canaux.routage.<TYPE>` ne sont que les valeurs par défaut. Fournisseur par défaut d'un canal : le vrai opérateur s'il est configuré, sinon JOURNAL.
- Back-office (§2, §10) : `comptes/AdministrationController` (`/v1/admin/organisations`, `/v1/admin/utilisateurs`, désactivation, révocation d'appareil, `/v1/admin/audit`), ADMIN seulement (un test vérifie toute route `/v1/admin/**`). Compte désactivé : `utilisateur.desactive_le` ; appareil révoqué : définitif pour cet `appareilId`, 403 `appareil-revoque` jusque sur `/v1/auth/refresh`. Toute écriture admin passe par `Audit.consigner` (identifiants seulement, jamais de téléphone ni de secret).
- Identifiants des fournisseurs, uniquement par l'environnement (jamais en base, jamais renvoyés par l'API) :
  - SMS Infobip : `DABA_INFOBIP_BASE_URL` (base URL du compte, `xxxxx.api.infobip.com`), `DABA_INFOBIP_API_KEY` (secret, portée `sms:message:send`), `DABA_INFOBIP_SENDER` (expéditeur, optionnel).
  - Push FCM : `DABA_FCM_PROJECT_ID` (identifiant du projet Firebase), `DABA_FCM_CREDENTIALS` (chemin du fichier JSON du compte de service, monté en lecture seule ; le contenu ne passe jamais par une variable).
- Nouvelle migration : `src/main/resources/db/migration/V<n>__<description>.sql`.
- `ModulesTest` (Spring Modulith) échoue si un module utilise les classes internes d'un autre : l'API publique d'un module est son package racine, le reste va dans des sous-packages.

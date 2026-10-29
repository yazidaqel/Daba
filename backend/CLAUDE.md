# Back-end Daba

Référence : cahier des charges §8 (règles de gestion), §9 (notifications), §10 (non fonctionnel), §11 (architecture).

- Java 25 LTS, Spring Boot 4.1, monolithe modulaire : un package par module (`comptes`, `referentiel`, `stock`, `ventes` (karni inclus), `promos`, `depannage`, `canaux`). Un module n'accède pas aux tables d'un autre : il passe par son API Java publique.
- PostgreSQL 18 + PostGIS ; schéma versionné par Flyway, jamais de modification d'une migration déjà commitée.
- API REST documentée en OpenAPI : le contrat de référence est `../api/openapi.yaml`, tenu à jour à chaque changement d'endpoint.
- Événements : outbox PostgreSQL ou Kafka **à trancher** ; ne pas introduire Kafka sans validation.
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
- Configuration : `DB_URL`, `DB_USER`, `DB_PASSWORD` (défaut : `jdbc:postgresql://localhost:5433/daba`, `daba`/`daba`).
- Nouvelle migration : `src/main/resources/db/migration/V<n>__<description>.sql`.
- `ModulesTest` (Spring Modulith) échoue si un module utilise les classes internes d'un autre : l'API publique d'un module est son package racine, le reste va dans des sous-packages.

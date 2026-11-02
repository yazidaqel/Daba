# Daba — Plateforme des grossistes ambulants (RED)

Référence fonctionnelle : @docs/cahier-des-charges.md
Toute demande qui contredit le cahier des charges doit être signalée avant d'être codée.

## Contexte

Daba est développée en collaboration avec RED (red.ma). Elle réunit les solutions 4 (Kenach) et 6 (Daba) de la note REDLAB v3 d'octobre 2026.

Acteurs :
- Vendeur grossiste ambulant : app mobile hors ligne (stock embarqué, tournée, bons de livraison, crédit des hanouts, alertes de rupture et de promo).
- Hanout (épicier) : signale ses ruptures, consulte ses bons de livraison et son solde, tient le carnet de crédit de ses propres clients. App mobile, ou PWA sans installation.
- Grossiste : stock, confirmation des dépôts promo, retraits par QR code.
- Grand client (marques et autres distributeurs) : crée les promos, choisit les grossistes dépositaires, suit l'entonnoir, la carte des ruptures et le sell-out.

## Décisions prises

- MVP en 6 semaines : dépannage sur rupture, flux promo, portail grossiste.
- Pilote à Casablanca, ouvert à toutes les marques ; base POS fournie par RED.
- Bon de livraison par défaut (facture en option si l'ambulant a un ICE). L'ambulant garde la marge sur les promos.
- Canaux de notification paramétrables (push, SMS, WhatsApp Business API) : canal principal + canal de repli par type de message.
- Hébergement : Azure pendant le pilote, puis VPS au Maroc. Tout est conteneurisé (Docker), aucun service propre à Azure n'est indispensable.

## Stack (versions vérifiées en octobre 2026)

- Back-end : Java 25 LTS, Spring Boot 4.1, monolithe modulaire, API REST documentée (OpenAPI).
- Données : PostgreSQL 18 + PostGIS (géociblage, dispatch des ruptures).
- Mobile : Flutter 3.47 (apps ambulant et hanout), base locale SQLite, file d'opérations rejouée à la synchronisation, UUID générés côté appareil.
- Web : React 19 + shadcn/ui (Tailwind CSS 4) pour les portails grossiste, grand client et admin.
- Événements : outbox PostgreSQL (tranché le 2 novembre 2026), via le registre de publication d'événements JDBC de Spring Modulith. Pas de Kafka au MVP ; il pourra être branché plus tard derrière la même outbox.

## Règles de travail

- Utilise toujours les dernières versions stables et vérifie-les avant de les citer ou de les épingler.
- Toute interface prévoit le darija (caractères arabes et latins), l'arabe (RTL) et le français.
- Typographie commune à toutes les apps : IBM Plex Sans Arabic pour l'écriture arabe, IBM Plex Sans pour l'écriture latine, embarquées ou auto-hébergées (jamais chargées depuis Google Fonts), seulement les graisses utilisées. Icônes : Phosphor (une seule graisse) dans les apps Flutter, Lucide dans les apps web. Décidé le 2 novembre 2026.
- Couleurs : palette Zellige, jetons et règles dans `docs/charte-graphique.md` (source unique, aucune couleur codée en dur). Le safran est réservé aux promotions.
- L'app ambulant fonctionne hors ligne par défaut. Seules deux actions exigent le réseau : la réservation d'une promo, et le dépannage (disponibilité, accepter ou refuser, en route), car le premier qui accepte doit être arbitré par le serveur (RG-D2). Décidé le 2 novembre 2026.
- Conformité CNDP (loi 09-08) : consentement, géolocalisation de l'ambulant seulement quand il est disponible, aucune donnée nominative partagée aux marques.
- Une facture ne se supprime jamais : seul un avoir l'annule. Numérotation continue avec un préfixe par appareil.
- Ne mets jamais de Co-Authored-By dans les commits git.

## Organisation multi-agents

- Dossiers : `backend/`, `mobile/`, `web/`, `api/` (contrat OpenAPI). Chacun a son propre CLAUDE.md.
- Agents (`.claude/agents/`) : `backend` (écrit dans `backend/` et `api/`), `mobile`, `web`, et `cdc-reviewer` (relecture seule).
- Une session parallèle = un dossier, dans son propre worktree git. Un changement transverse commence par le contrat dans `api/`, puis chaque côté l'implémente.
- Ordre du lot 1 : d'abord une session « fondations » (squelette back-end, `docker-compose.yml` PostgreSQL + PostGIS, premier `api/openapi.yaml`), puis en parallèle : modules back-end · app ambulant · portail grossiste · portail grand client · PWA hanout.
- Avant de fusionner une branche : lancer l'agent `cdc-reviewer` sur le diff.

## Commandes

Prérequis : Docker, et JDK 25 dans `JAVA_HOME` pour builder hors conteneur.

- Tout lancer (PostgreSQL 18 + PostGIS sur l'hôte en 5433, back-end en 8080) : `docker compose up -d --build`
- Santé : `curl localhost:8080/actuator/health` ; Swagger UI : http://localhost:8080/swagger-ui.html
- Arrêter : `docker compose down` (ajouter `-v` pour effacer la base)
- Valider le contrat : `npx @redocly/cli lint api/openapi.yaml`
- Back-end : voir `backend/CLAUDE.md`.

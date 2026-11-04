# Portail grand client (marques et distributeurs)

Référence : cahier des charges §5 et §7.2. Lot 1 : connexion (code SMS, mot de passe, ou les deux, au choix de l'administrateur), consentement CNDP, campagnes (création, cycle de vie), affectation aux grossistes (carte ou liste, quantités), suivi des dépôts, entonnoir, carte des ruptures. Export Excel et Power BI : hors lot 1.

- Vite + React 19 + TypeScript, shadcn/ui (composants copiés dans `src/components/ui/`), Tailwind CSS 4, MapLibre GL avec les tuiles libres OpenFreeMap (sans clé).
- Client d'API typé : `src/api/schema.d.ts` est généré depuis `../../api/openapi.yaml` (`npm run api`), ne pas l'éditer à la main. Appels via `api` (openapi-fetch) et `ok()` dans `src/api/client.ts`.
- Couleurs : palette Zellige (`../../docs/charte-graphique.md`), jetons en hexadécimal dans `src/index.css` (variables shadcn + `promo`, `success`, `warning` et leurs variantes `-soft`, `-foreground`), mode sombre selon le système. Aucune couleur en dur : MapLibre relit les jetons (`src/lib/jetons.ts`). Safran pour les campagnes et l'entonnoir ; carte des ruptures en échelle de `danger` avec légende ; un état = icône + libellé ; un seul bouton plein par écran (la variante `destructive` est en contour).
- Typographie : IBM Plex Sans (latin) et IBM Plex Sans Arabic (arabe, darija en graphie arabe), auto-hébergées (`@fontsource`, OFL), woff2 seulement, graisses 400, 500 et 600 (`src/fonts.css`) : pas de `font-bold`. Les étiquettes WebGL de MapLibre gardent les glyphes du style OpenFreeMap. Icônes : Lucide.
- Textes : `src/messages.ts` (fr, ar, ary-Arab, ary-Latn), typés : une clé manquante dans une langue casse la compilation. Mise en page RTL par propriétés logiques (`ms-`, `pe-`, `text-start`, `inline-size`).
- Session : sur 401, `src/api/client.ts` renouvelle le jeton (`POST /v1/auth/refresh`) et rejoue l'appel (jamais pour `/v1/auth/otp*`, `refresh`, `mot-de-passe*`), sinon retour à la connexion ; `appareil-revoque` (403, ou 401 à la connexion) efface la session et l'identifiant d'appareil. Les décisions se prennent sur le code de `Problem.type`, jamais sur `detail`.
- Connexion (contrat v0.9.0, §10) : machine d'états `src/connexion.ts`, affichée par `pages/Connexion.tsx`, même comportement que le portail grossiste (code séparé). Le portail ne connaît pas la méthode d'un numéro : il propose « mot de passe » ou « code SMS » (dernier choix dans `daba.gc.connexion.methode`, jamais le numéro) et suit le serveur : `{session}` ou `{defi}` → code avec `defi` ; 403 `mot-de-passe-requis` → mot de passe ; 403 `mot-de-passe-a-definir` → création avec le même code ; 403 `connexion-par-code` → code envoyé ; 401 `identifiants-invalides` → un seul message ; 429 → attente (`Retry-After` si présent). « Mot de passe oublié » : code puis `/definition`. Contrôle local 15 à 128 points de code (NFC). « Mon mot de passe » : `#/mot-de-passe` (409 `mot-de-passe-absent` expliqué). Mots de passe jamais stockés ni journalisés.
- Ciblage : codes de `GET /v1/regions` et `GET /v1/categories`. Produits : `mesProduits=true` pour choisir, `ids=` pour les libellés.
- CNDP (§5.4) : seules des données agrégées s'affichent (entonnoir, cellules geohash). Aucun nom de hanout ni d'ambulant.
- TypeScript reste en 6.0 : typescript-eslint et openapi-typescript ne prennent pas encore en charge TypeScript 7.

## Commandes (depuis `web/grand-client/`)

- Installer : `npm install`
- Mock de l'API (Prism, port 4013) : `npm run mock`
- Développement contre le mock (port 5177, `/v1` relayé vers Prism) : `npm run dev:mock` — ce mode envoie `Prefer: example=grandClient` à la connexion, pour que Prism renvoie une session grand client
- Développement (rôle contrôlé) : `npm run dev`
- Contre le vrai back-end : `DABA_API=http://localhost:8080 npm run dev`
- Régénérer les types après un changement du contrat : `npm run api`
- Vérifier : `npm run lint`, `npm test`, `npm run build`

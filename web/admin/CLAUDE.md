# Back-office administrateur (RED / éditeur)

Référence : cahier des charges §2 (administrateur plateforme), §9 (canaux configurables), §10 (sécurité, CNDP) ; contrat
`../../api/openapi.yaml`, tags `administration` et `canaux`. Règles communes : `../CLAUDE.md`.

Même pile et mêmes conventions que `../grossiste` : Vite 8 + React 19.3 + TypeScript 6.0 + Tailwind CSS 4.3 + shadcn/ui
(style radix-nova, classes logiques pour le RTL), openapi-fetch + openapi-react-query, IBM Plex Sans et IBM Plex Sans Arabic
auto-hébergées depuis `@fontsource` 5.3.0 (woff2 400, 500, 600 seulement, `src/polices.css`), icônes Lucide, palette Zellige
(`src/index.css`, sombre par `prefers-color-scheme`). Aucune couleur ni aucun texte en dur ; un état = icône + libellé
(`components/communs.tsx`) ; un seul bouton plein par écran (les confirmations ont le leur, dans leur dialogue).
TypeScript 7 est sorti mais openapi-typescript en dépend encore : rester en 6.0.x.

## Commandes (depuis `web/admin/`)

- Installer : `npm install`
- Mock de l'API : `npm run mock` (Prism sur le port 4014)
- Dev : `npm run dev` → http://localhost:5179 ; `/v1` est relayé vers le mock. Contre le vrai back-end : `API_URL=http://localhost:8080 npm run dev`
  (entrée `admin` de `../../.claude/launch.json`). Compte de démo : `+212600000001`, code dans
  `docker compose logs backend --since 2m | grep "\[SMS\] +212600000001"` (5 codes par heure et par numéro).
- Build : `npm run build` · Lint : `npm run lint` (oxlint) · Tests : `npm test` (vitest)
- Régénérer les types après un changement du contrat : `npm run api:types` (écrit `src/api/schema.d.ts`, à committer)

## Organisation

- `src/api/` : client typé, erreurs RFC 9457 (`erreurs.ts`), corps du PUT des canaux (`canaux.ts`).
- `src/i18n/` : `fr.ts` est la référence ; `ar`, `ary-Arab`, `ary-Latn` sont typés sur ses clés.
- `src/pages/` : Organisations (liste, création), Organisation (détail, ajout d'un membre), Utilisateurs (support),
  Référentiel (imports CSV, produits, hanouts), Canaux, Audit.
- `src/components/FicheUtilisateur.tsx` : rôles, organisation, consentement, appareils ; désactivation, réactivation et
  révocation d'un appareil, chacune derrière un dialogue de confirmation (`Confirmer`).
- Session : comme le grossiste (refresh sur 401 puis rejeu unique ; 403 `appareil-revoque` et lui seul efface la session ;
  403 `utilisateur-desactive` affiche un message sans rien effacer). Décisions sur `Problem.type`, jamais sur `detail`.
  Seul le rôle ADMIN entre ; aucun écran de consentement (`/v1/moi/consentement` n'est pas ouvert à ADMIN).
- CNDP : ce portail montre des données personnelles aux administrateurs seulement (mention en pied de page et sur
  Utilisateurs). Les recherches restent dans l'état de l'écran, jamais dans l'adresse ; seuls des UUID y figurent.
  Aucun secret de fournisseur n'est demandé ni affiché : les clés se règlent dans l'environnement du serveur.

## Limites du mock Prism

- Le proxy de Vite ajoute `Prefer: example=admin` à `POST /v1/auth/otp/verify` : le mock renvoie la Session ADMIN du
  contrat. Jetons non vérifiés par Prism : rafraîchissement, 401 et 403 ne se testent qu'avec le vrai back-end (ou vitest).
- Les pages renvoyées par Prism ont des valeurs aléatoires (`taille: 0`, totaux négatifs) : la pagination ne s'y affiche pas.
- Prism ne valide pas les règles métier du PUT canaux (OTP, fournisseur non configuré) : les erreurs 400/422 ne se voient
  qu'avec le vrai back-end.

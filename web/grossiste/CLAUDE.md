# Portail grossiste (lot 1)

Référence : cahier des charges §4 et §7.2. Règles communes : `../CLAUDE.md`.

Vite 8 + React 19.3 + TypeScript 6.0 + Tailwind CSS 4.3 + shadcn/ui (style radix-nova, classes logiques pour le RTL).
Police : IBM Plex Sans (latin) et IBM Plex Sans Arabic (arabe), auto-hébergées depuis `@fontsource` 5.3.0 (OFL 1.1) ;
`src/polices.css` ne déclare que les woff2 des graisses 400, 500, 600 (sous-ensembles latin, latin-ext, arabic) ;
la face arabe passe en tête pour `lang="ar"` et `ary-Arab` (`src/index.css`).
TypeScript 7 est sorti mais openapi-typescript et le JS API en dépendent encore : rester en 6.0.x.

## Commandes (depuis `web/grossiste/`)

- Installer : `npm install`
- Mock de l'API : `npm run mock` (Prism sur le port 4012 ; 4010 est souvent pris par les autres portails)
- Dev : `npm run dev` → http://localhost:5175 ; `/v1` est relayé vers le mock. Contre le vrai back-end : `API_URL=http://localhost:8080 npm run dev`
- Build : `npm run build` · Lint : `npm run lint` (oxlint) · Tests : `npm test` (vitest)
- Régénérer les types après un changement du contrat : `npm run api:types` (écrit `src/api/schema.d.ts`, à committer)

## Organisation

- `src/api/` : client typé (openapi-fetch + openapi-react-query), erreurs RFC 9457, libellés produits.
- `src/i18n/` : `fr.ts` est la référence ; `ar`, `ary-Arab`, `ary-Latn` sont typés sur ses clés (une clé manquante ne compile pas).
- `src/pages/` : Dépôts (réponse, réception, réservations, clôture), Stock promo, Retrait (QR), Bons de sortie, Ambulants rattachés.
- Session : `POST /v1/auth/refresh` sur un 401 puis la requête est rejouée une fois ; `POST /v1/auth/deconnexion` à la déconnexion ;
  un 403 `appareil-revoque` (et lui seul) efface la session. Les erreurs se décident sur `Problem.type`, jamais sur `detail`.
- Composants shadcn copiés dans `src/components/ui/` (le CLI `shadcn` échoue ici sur `npm install --allow-scripts` : les fichiers ont été tirés du registre).

## Limites du mock Prism

- Le proxy de Vite ajoute `Prefer: example=grossiste` à `POST /v1/auth/otp/verify` : le mock renvoie une Session GROSSISTE.
  Les jetons d'exemple ne sont pas vérifiés par Prism, donc rafraîchissement et 401 ne se testent qu'avec le vrai back-end.
- Les dépôts reviennent toujours à l'état PROPOSE (et `statutCampagne` au hasard) : la réception et la clôture ne s'affichent qu'avec le vrai back-end
  (ou en réécrivant la réponse dans la console).
- Prism ne sait pas lire une partie JSON dans un multipart : `POST /v1/depots/{id}/reception` répond 400 sur le mock,
  alors que le corps suit le contrat (`reception` en `application/json`, photo en `image/jpeg`).

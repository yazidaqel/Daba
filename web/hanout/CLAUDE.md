# PWA hanout (lot 1)

Référence : cahier des charges §6, §7.1, §10. App autonome (Vite + React 19 + Tailwind 4 + shadcn/ui), sans code partagé avec les autres portails.

## Commandes

Depuis `web/hanout/` :

- Installer : `npm install`
- Mock de l'API (Prism, port 4010, données générées) : `npm run mock`
  (le mode statique sans `-d` renvoie des `Document` incomplets à cause du `if/then` du contrat)
- Développement : `npm run dev` (proxy `/v1` vers le mock ; vrai back-end : `API_URL=http://localhost:8080 npm run dev`)
- Types de l'API depuis `../../api/openapi.yaml` : `npm run api:types` (aussi lancé par `build`)
- Build : `npm run build`, puis `npm run preview` (port 4173, même proxy)
- Lint : `npm run lint` (oxlint, avertissements bloquants)
- Tests : `npm test` (Vitest + jsdom)
- Icônes PWA : générées au build depuis `public/logo.svg` (`pwa-assets.config.ts`)

Ouvrir un lien SMS en local : http://localhost:5173/l/abcdefghijklmnopqrst (jeton de 16 caractères ou plus), code au choix sur 6 chiffres.
Le mock tire les rôles au hasard : si la connexion répond « pas une boutique », recommencer. On peut aussi ouvrir une session à la main dans la console :
`localStorage.setItem('daba.session', JSON.stringify({ accessToken: 'x' }))`.

Vérifications faites le 2 novembre 2026 (machine chargée, indice CPU Lighthouse ≈ 450–570) :
- Lighthouse 13.6, profil mobile, `/connexion` : performance 80 à 84, accessibilité 100, bonnes pratiques 100, LCP ≈ 1,1 s, TBT ≈ 700 ms (évaluation de React).
- Avec 3G lente (400 ms, 400 kbit/s, CPU ×6) : performance 69, LCP 2,8 s.
- Lighthouse 13 n'a plus de catégorie PWA. Manifeste, icônes et service worker sont vérifiés dans Chrome sans interface : rechargement hors ligne OK.

## Repères

- Textes : `src/textes.ts`, une entrée par langue (fr, ar, ary-Arab, ary-Latn) ; le type impose chaque clé partout. Aucun texte en dur, propriétés CSS logiques (`ms-`, `me-`, `text-end`).
- Client API typé : `src/api/client.ts` (openapi-fetch). Les endpoints absents du contrat (réception / écart, contestation) sont typés à la main dans `src/api/a-venir.ts`, à remplacer dès qu'ils arrivent dans `api/openapi.yaml`.
- Session : jeton dans `localStorage`, pas d'expiration locale ; un 401 renvoie à la connexion OTP, un 403 `consentement-requis` à l'écran de consentement CNDP.
- Hors ligne : le service worker garde l'appli et les dernières réponses GET (`api`, vidé à la déconnexion). Le signalement de rupture exige le réseau (même id rejoué : idempotent).
- Suivi d'une demande : sondage toutes les 15 s (la PWA ne reçoit pas de push, §6).

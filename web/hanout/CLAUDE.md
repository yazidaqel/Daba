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
Avec le mock, le proxy de `dev` et `preview` ajoute `Prefer: example=hanout` à `POST /v1/auth/otp/verify` : la connexion rend toujours une session hanout. On peut aussi ouvrir une session à la main dans la console :
`localStorage.setItem('daba.session', JSON.stringify({ accessToken: 'x' }))`.

Vérifications faites le 2 novembre 2026 (machine chargée, indice CPU Lighthouse ≈ 450–570) :
- Lighthouse 13.6, profil mobile, `/connexion` : performance 80 à 84, accessibilité 100, bonnes pratiques 100, LCP ≈ 1,1 s, TBT ≈ 700 ms (évaluation de React).
- Avec 3G lente (400 ms, 400 kbit/s, CPU ×6) : performance 69, LCP 2,8 s.
- Lighthouse 13 n'a plus de catégorie PWA. Manifeste, icônes et service worker sont vérifiés dans Chrome sans interface : rechargement hors ligne OK.

## Repères

- Textes : `src/textes.ts`, une entrée par langue (fr, ar, ary-Arab, ary-Latn) ; le type impose chaque clé partout. Aucun texte en dur, propriétés CSS logiques (`ms-`, `me-`, `text-end`).
- Client API typé : `src/api/client.ts` (openapi-fetch, types générés depuis le contrat v0.4.0). Les décisions se prennent sur le code de `Problem.type`, jamais sur `detail`.
- Session : jetons dans `localStorage`, pas d'expiration locale. Sur 401, `POST /v1/auth/refresh` puis la requête est rejouée une fois ; si le renouvellement échoue, retour à l'OTP. Un 403 `appareil-revoque` efface les données locales, un 403 `consentement-requis` mène à l'écran CNDP. La déconnexion appelle `POST /v1/auth/deconnexion`.
- Réception : `Document.receptionHanout` (distincte de `confirmationHanout`, signée chez l'ambulant). Un 409 veut dire réception déjà acquise : la PWA propose la contestation. Une seule contestation ouverte par cible (`GET /v1/contestations?cibleId=&statut=OUVERTE`).
- Hors ligne : le service worker garde l'appli et les dernières réponses GET (`api`, vidé à la déconnexion). Le signalement de rupture exige le réseau (même id rejoué : idempotent).
- Polices : IBM Plex Sans (latin) et IBM Plex Sans Arabic (arabe, darija en lettres arabes), OFL 1.1, servies depuis `@fontsource` (`src/index.css`) : woff2, 400 et 600, sous-ensembles latin et arabe. Les pages en lettres latines n'ont pas la face arabe dans leur pile, pour ne jamais la télécharger. Le build précharge la 400 de la langue courante (plugin dans `vite.config.ts`) ; le service worker les garde (cache `polices`).
  Lighthouse mobile le 2 novembre 2026, médiane de 3 passages, sans puis avec polices : fr 99 → 99, LCP 1,01 → 0,98 s ; ary-Arab 99 → 98, LCP 0,99 → 0,97 s ; polices 46 Kio (fr) et 133 Kio (arabe).
- Suivi d'une demande : sondage toutes les 15 s (la PWA ne reçoit pas de push, §6).

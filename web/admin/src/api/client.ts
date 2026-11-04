import { QueryClient } from '@tanstack/react-query'
import createFetchClient from 'openapi-fetch'
import createClient from 'openapi-react-query'
import { langueInitiale } from '@/i18n'
import type { components, paths } from './schema'

export type S = components['schemas']
// `attente` (secondes) vient de l'en-tête Retry-After, ajouté par le client.
export type Problem = S['Problem'] & { attente?: number }

const SESSION = 'daba.session'
const APPAREIL = 'daba.appareilId'

export const lireSession = (): S['Session'] | null => JSON.parse(localStorage.getItem(SESSION) ?? 'null')

// Pourquoi la session a pris fin sans que l'utilisateur l'ait demandé ; affiché sur l'écran de connexion.
export type Motif = 'sessionExpiree' | 'appareilRevoque'
let motifFin: Motif | null = null
export const lireMotifFin = () => motifFin

export function ecrireSession(s: S['Session'] | null, motif: Motif | null = null) {
  const avant = lireSession()
  if (s) localStorage.setItem(SESSION, JSON.stringify(s))
  else localStorage.removeItem(SESSION)
  motifFin = motif
  // Changement de compte : rien du compte précédent ne doit rester en cache (cloisonnement, consentement).
  // Un simple rafraîchissement des jetons du même compte garde le cache.
  if (!s || s.utilisateur.id !== avant?.utilisateur.id) queryClient.clear()
  window.dispatchEvent(new Event('daba-session'))
}

// Ferme la session côté serveur (POST /v1/auth/deconnexion), puis localement même si le réseau échoue.
export async function seDeconnecter() {
  await fetchClient.POST('/v1/auth/deconnexion').catch(() => undefined)
  ecrireSession(null)
}

// Identifiant de ce navigateur (OtpVerify.appareilId) : l'appareil que le journal d'audit attribue à l'administrateur.
export function appareilId() {
  let id = localStorage.getItem(APPAREIL)
  if (!id) localStorage.setItem(APPAREIL, (id = crypto.randomUUID()))
  return id
}

export const codeProbleme = (e: unknown) => (e as Problem | undefined)?.type?.split('/').pop()

// Un seul rafraîchissement à la fois, partagé par les requêtes qui reçoivent 401 ensemble.
let rafraichissement: Promise<boolean> | null = null
function rafraichir(): Promise<boolean> {
  const refreshToken = lireSession()?.refreshToken
  if (!refreshToken) return Promise.resolve(false)
  rafraichissement ??= fetchClient
    .POST('/v1/auth/refresh', { body: { refreshToken } })
    .then(({ data }) => (data ? (ecrireSession(data), true) : false), () => false)
    .finally(() => (rafraichissement = null))
  return rafraichissement
}

// Copie de chaque requête avant envoi, pour la rejouer après un rafraîchissement (le corps est consommé).
const copies = new WeakMap<Request, Request>()

// Même origine : Vite relaie /v1 vers Prism ou le back-end (voir vite.config.ts).
export const fetchClient = createFetchClient<paths>({ baseUrl: location.origin })

fetchClient.use({
  onRequest({ request }) {
    const s = lireSession()
    if (s) request.headers.set('Authorization', `Bearer ${s.accessToken}`)
    request.headers.set('Accept-Language', langueInitiale())
    copies.set(request, request.clone())
    return request
  },
  async onResponse({ request, response }) {
    if (response.ok) return response
    // La déconnexion profite du rafraîchissement ; la connexion (code, mot de passe) et le rafraîchissement non.
    const auth = /^\/v1\/auth\/(otp|refresh|mot-de-passe)/.test(new URL(request.url).pathname)
    // Jeton expiré : on le renouvelle et on rejoue la requête une fois ; sinon nouvelle connexion OTP.
    // Partie avec un jeton déjà remplacé entre-temps : on rejoue sans rafraîchir de nouveau.
    if (response.status === 401 && !auth && lireSession()) {
      const copie = copies.get(request)
      const perime = request.headers.get('Authorization') !== `Bearer ${lireSession()!.accessToken}`
      if (copie && (perime || (await rafraichir()))) {
        copie.headers.set('Authorization', `Bearer ${lireSession()!.accessToken}`)
        response = await fetch(copie)
        if (response.ok) return response
      }
      if (response.status === 401) ecrireSession(null, 'sessionExpiree')
    }
    // openapi-react-query lève le corps d'erreur : on garantit un Problem portant le vrai statut HTTP.
    const corps: Problem = await response.json().catch(() => ({}))
    // Appareil déconnecté à distance (§10) : seul ce type efface la session locale. Un compte désactivé
    // (utilisateur-desactive) n'efface rien : le message s'affiche à la place du contenu (messageErreur).
    // La révocation vaut à jamais pour cet appareilId (403 sur un appel authentifié, 401 à la connexion) :
    // on en tire un nouveau, sinon ce navigateur ne pourrait plus jamais se reconnecter.
    if (codeProbleme(corps) === 'appareil-revoque') {
      localStorage.removeItem(APPAREIL)
      if (lireSession()) ecrireSession(null, 'appareilRevoque')
    }
    // Attente avant un nouvel essai (429, délai progressif) : Retry-After en secondes ou en date HTTP.
    const apres = response.headers.get('Retry-After')
    const secondes = apres === null ? NaN : /^\d+$/.test(apres) ? Number(apres) : Math.ceil((Date.parse(apres) - Date.now()) / 1000)
    const attente = Number.isFinite(secondes) && secondes > 0 ? { attente: secondes } : {}
    return new Response(JSON.stringify({ ...corps, status: response.status, ...attente }), {
      status: response.status,
      headers: { 'Content-Type': 'application/problem+json' },
    })
  },
})

export const $api = createClient(fetchClient)

export const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: 1, refetchOnWindowFocus: true } },
})

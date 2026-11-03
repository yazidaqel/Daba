import createClient from 'openapi-fetch'
import { langueActuelle } from '../i18n'
import { naviguer } from '../router'
import { fermerSession, lireSession, ouvrirSession } from '../session'
import type { components, paths } from './schema'

export type S = components['schemas']

/** Code fixe de `Problem.type` (`https://daba.ma/problemes/<code>`). */
export async function codeProbleme(response: Response) {
  const p = await response
    .clone()
    .json()
    .catch(() => null)
  return String(p?.type ?? '').split('/').pop()
}

export function entetes(h: Headers) {
  const s = lireSession()
  if (s) h.set('Authorization', `Bearer ${s.accessToken}`)
  h.set('Accept-Language', langueActuelle())
  return h
}

const ici = () => encodeURIComponent(location.pathname + location.search)

/** `ok` : nouvelle session ; `fin` : refreshToken refusé (400/401), retour à l'OTP ; `echec` : réseau ou serveur, on garde la session. */
type Renouvellement = 'ok' | 'fin' | 'echec'
let renouvellement: Promise<Renouvellement> | undefined

/** Renouvelle l'accessToken sans nouvel OTP ; un seul appel à la fois (le refreshToken peut tourner). */
function rafraichir(): Promise<Renouvellement> {
  const s = lireSession()
  if (!s?.refreshToken) return Promise.resolve('fin')
  renouvellement ??= fetch('/v1/auth/refresh', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken: s.refreshToken }),
  })
    .then(async (r): Promise<Renouvellement> => {
      if (r.status === 400 || r.status === 401) return 'fin'
      if (!r.ok) return 'echec'
      ouvrirSession(await r.json())
      return 'ok'
    })
    .catch((): Renouvellement => 'echec')
    .finally(() => (renouvellement = undefined))
  return renouvellement
}

const rejouer = (copie: Request) => fetch(new Request(copie, { headers: entetes(new Headers(copie.headers)) }))

/** Sur 401, renouvelle la session puis rejoue la requête une fois. */
async function envoyer(request: Request) {
  const copie = request.headers.has('Authorization') ? request.clone() : undefined
  const response = await fetch(request)
  if (response.status !== 401 || !copie) return response
  // Jeton déjà renouvelé par une requête parallèle : rejouer suffit (pas de second refresh).
  if (copie.headers.get('Authorization') !== `Bearer ${lireSession()?.accessToken}`) return rejouer(copie)
  const r = await rafraichir()
  if (r === 'ok') return rejouer(copie)
  // Panne passagère : une erreur ordinaire, sans perdre un refreshToken encore valable.
  return r === 'fin' ? response : new Response(null, { status: 503 })
}

/** Réaction commune aux réponses authentifiées : session perdue, appareil révoqué, consentement CNDP manquant. */
export async function surReponse(response: Response) {
  if (response.status === 401) {
    fermerSession()
    naviguer(`/connexion?suite=${ici()}`, true)
  } else if (response.status === 403) {
    const code = await codeProbleme(response)
    if (code === 'appareil-revoque') {
      // §10, appareil perdu : on efface les données locales.
      fermerSession()
      naviguer('/connexion?revoque=1', true)
    } else if (code === 'consentement-requis') naviguer(`/consentement?suite=${ici()}`, true)
  }
}

export const api = createClient<paths>({ baseUrl: location.origin, fetch: envoyer })

api.use({
  onRequest: ({ request }) => {
    // Pas de jeton vers la connexion OTP : un ancien jeton refusé y déclencherait un renouvellement inutile.
    if (new URL(request.url).pathname.startsWith('/v1/auth/otp')) request.headers.set('Accept-Language', langueActuelle())
    else entetes(request.headers)
    return request
  },
  onResponse: async ({ request, response }) => {
    if (request.headers.has('Authorization')) await surReponse(response)
  },
})

/** Ferme la session sur l'appareil tout de suite, puis côté serveur sans attendre (3G lente, hors ligne). */
export function deconnecter() {
  const s = lireSession()
  fermerSession()
  if (s)
    fetch('/v1/auth/deconnexion', {
      method: 'POST',
      headers: { Authorization: `Bearer ${s.accessToken}` },
      signal: AbortSignal.timeout(15_000),
    }).catch(() => undefined)
}

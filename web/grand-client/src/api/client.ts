import createClient from 'openapi-fetch'
import type { components, paths } from './schema'

export type S = components['schemas']
export type Problem = S['Problem']

const SESSION = 'daba.gc.session'

export function lireSession(): S['Session'] | null {
  const brut = sessionStorage.getItem(SESSION)
  return brut ? (JSON.parse(brut) as S['Session']) : null
}

export function ecrireSession(s: S['Session'] | null) {
  if (s) sessionStorage.setItem(SESSION, JSON.stringify(s))
  else sessionStorage.removeItem(SESSION)
}

/** UUID de cet appareil (navigateur), demandé par la vérification OTP. */
export function appareilId(): string {
  let id = localStorage.getItem('daba.gc.appareil')
  if (!id) {
    id = crypto.randomUUID()
    localStorage.setItem('daba.gc.appareil', id)
  }
  return id
}

let langue = 'fr'
export function choisirLangueApi(l: string) {
  langue = l
}

// Même origine : Vite relaie /v1 vers Prism ou le back-end (vite.config.ts).
export const api = createClient<paths>({ baseUrl: '' })

api.use({
  onRequest({ request }) {
    const s = lireSession()
    if (s) request.headers.set('Authorization', `Bearer ${s.accessToken}`)
    request.headers.set('Accept-Language', langue)
    return request
  },
  async onResponse({ response }) {
    // ponytail: pas de rafraîchissement du jeton ; on renvoie à la connexion. Ajouter refreshToken si les sessions courtes gênent.
    if (response.status === 401 && lireSession()) {
      ecrireSession(null)
      window.dispatchEvent(new Event('daba:deconnexion'))
    }
    // Consentement CNDP retiré ou exigé en cours de session : retour à l'écran de consentement.
    if (response.status === 403) {
      const p = (await response.clone().json().catch(() => ({}))) as Problem
      if (p.type?.endsWith('consentement-requis')) window.dispatchEvent(new Event('daba:consentement'))
    }
    return response
  },
})

export class ApiError extends Error {
  constructor(
    public status: number,
    public problem: Problem,
  ) {
    super(problem.title ?? `HTTP ${status}`)
  }
}

/** Résultat openapi-fetch → données, ou ApiError avec le Problem RFC 9457. */
export function ok<T>(r: { data?: T; error?: unknown; response: Response }): T {
  if (r.error !== undefined || !r.response.ok) throw new ApiError(r.response.status, (r.error ?? {}) as Problem)
  return r.data as T
}

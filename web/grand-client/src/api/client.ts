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
const APPAREIL = 'daba.gc.appareil'
export function appareilId(): string {
  let id = localStorage.getItem(APPAREIL)
  if (!id) {
    id = crypto.randomUUID()
    localStorage.setItem(APPAREIL, id)
  }
  return id
}

let langue = 'fr'
export function choisirLangueApi(l: string) {
  langue = l
}

// Même origine : Vite relaie /v1 vers Prism ou le back-end (vite.config.ts).
export const api = createClient<paths>({ baseUrl: location.origin })

/** Code fixe de `Problem.type` (`https://daba.ma/problemes/<code>`) : les décisions se prennent sur lui, jamais sur `detail`. */
export const codeProbleme = (p?: Problem) => p?.type?.split('/').pop()

// Mock Prism (npm run dev:mock) : demande l'exemple de session grand client.
export const enteteMock: Record<string, string> = import.meta.env.MODE === 'mock' ? { Prefer: 'example=grandClient' } : {}

const sortie = (motif?: string) => {
  ecrireSession(null)
  window.dispatchEvent(new CustomEvent('daba:deconnexion', { detail: motif }))
}

// Un seul renouvellement à la fois, partagé par les appels qui reçoivent 401 ensemble.
let renouvellement: Promise<S['Session'] | null> | undefined
function renouveler(refreshToken: string) {
  renouvellement ??= fetch('/v1/auth/refresh', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...enteteMock },
    body: JSON.stringify({ refreshToken }),
  })
    .then((r) => (r.ok ? (r.json() as Promise<S['Session']>) : null))
    .catch(() => null)
    .finally(() => (renouvellement = undefined))
  return renouvellement
}

const copies = new WeakMap<Request, Request>()

api.use({
  onRequest({ request }) {
    const s = lireSession()
    if (s) request.headers.set('Authorization', `Bearer ${s.accessToken}`)
    request.headers.set('Accept-Language', langue)
    copies.set(request, request.clone())
    return request
  },
  async onResponse({ request, response }) {
    let r = response
    const s = lireSession()
    // Jeton expiré ou session fermée : un renouvellement et un nouvel essai, sinon retour à la connexion.
    // La connexion (OTP, mot de passe) et le renouvellement lui-même ne sont jamais renouvelés.
    if (r.status === 401 && s && !/^\/v1\/auth\/(otp|refresh|mot-de-passe)/.test(new URL(request.url).pathname)) {
      const nouvelle = await renouveler(s.refreshToken)
      if (nouvelle) {
        ecrireSession(nouvelle)
        const copie = copies.get(request)!
        copie.headers.set('Authorization', `Bearer ${nouvelle.accessToken}`)
        r = await fetch(copie)
      }
      if (r.status === 401) sortie()
    }
    // Le nouvel essai passe aussi par ici.
    if (r.status === 401 || r.status === 403) {
      const code = codeProbleme((await r.clone().json().catch(() => ({}))) as Problem)
      // Appareil déconnecté à distance (403, ou 401 à la vérification OTP) : les données locales
      // s'effacent, identifiant d'appareil compris ; la prochaine connexion OTP est un nouvel appareil.
      if (code === 'appareil-revoque') {
        localStorage.removeItem(APPAREIL)
        sortie(code)
      }
      // Consentement CNDP retiré ou exigé en cours de session : retour à l'écran de consentement.
      if (code === 'consentement-requis') window.dispatchEvent(new Event('daba:consentement'))
    }
    return r
  },
})

/** Ferme la session côté serveur (jetons invalidés), puis localement. */
export async function seDeconnecter() {
  await api.POST('/v1/auth/deconnexion').catch(() => undefined)
  ecrireSession(null)
}

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

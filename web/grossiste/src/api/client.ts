import { QueryClient } from '@tanstack/react-query'
import createFetchClient from 'openapi-fetch'
import createClient from 'openapi-react-query'
import { langueInitiale } from '@/i18n'
import type { components, paths } from './schema'

export type S = components['schemas']
export type Problem = S['Problem']

const SESSION = 'daba.session'
const APPAREIL = 'daba.appareilId'

export const lireSession = (): S['Session'] | null => JSON.parse(localStorage.getItem(SESSION) ?? 'null')

export function ecrireSession(s: S['Session'] | null) {
  if (s) localStorage.setItem(SESSION, JSON.stringify(s))
  else localStorage.removeItem(SESSION)
  window.dispatchEvent(new Event('daba-session'))
}

// Identifiant de ce navigateur (OtpVerify.appareilId, RetraitSaisie.appareilId : RG-S2).
export function appareilId() {
  let id = localStorage.getItem(APPAREIL)
  if (!id) localStorage.setItem(APPAREIL, (id = crypto.randomUUID()))
  return id
}

// Même origine : Vite relaie /v1 vers Prism ou le back-end (voir vite.config.ts).
export const fetchClient = createFetchClient<paths>({ baseUrl: location.origin })

fetchClient.use({
  onRequest({ request }) {
    const s = lireSession()
    if (s) request.headers.set('Authorization', `Bearer ${s.accessToken}`)
    request.headers.set('Accept-Language', langueInitiale())
    return request
  },
  async onResponse({ response }) {
    if (response.ok) return response
    // Pas d'endpoint de rafraîchissement dans le contrat : jeton expiré = nouvelle connexion OTP.
    if (response.status === 401 && lireSession()) ecrireSession(null)
    // openapi-react-query lève le corps d'erreur : on garantit un Problem portant le vrai statut HTTP.
    const corps: Problem = await response.json().catch(() => ({}))
    return new Response(JSON.stringify({ ...corps, status: response.status }), {
      status: response.status,
      headers: { 'Content-Type': 'application/problem+json' },
    })
  },
})

export const $api = createClient(fetchClient)

export const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: 1, refetchOnWindowFocus: true } },
})

export const estConsentementRequis = (e: unknown) =>
  typeof (e as Problem | undefined)?.type === 'string' && (e as Problem).type!.endsWith('/consentement-requis')

import createClient from 'openapi-fetch'
import { langueActuelle } from '../i18n'
import { naviguer } from '../router'
import { fermerSession, lireSession } from '../session'
import type { components, paths } from './schema'

export type S = components['schemas']

export function entetes(h: Headers) {
  const s = lireSession()
  if (s) h.set('Authorization', `Bearer ${s.accessToken}`)
  h.set('Accept-Language', langueActuelle())
  return h
}

const ici = () => encodeURIComponent(location.pathname + location.search)

/** Réaction commune aux réponses authentifiées : session expirée, consentement CNDP manquant. */
export async function surReponse(response: Response) {
  if (response.status === 401) {
    // Pas de POST /v1/auth/refresh dans le contrat : retour à la connexion OTP.
    fermerSession()
    naviguer(`/connexion?suite=${ici()}`, true)
  } else if (response.status === 403) {
    const p = await response
      .clone()
      .json()
      .catch(() => null)
    if (String(p?.type).endsWith('consentement-requis')) naviguer(`/consentement?suite=${ici()}`, true)
  }
}

export const api = createClient<paths>({ baseUrl: location.origin, fetch: (r) => fetch(r) })

api.use({
  onRequest: ({ request }) => {
    entetes(request.headers)
    return request
  },
  onResponse: async ({ request, response }) => {
    if (request.headers.has('Authorization')) await surReponse(response)
  },
})

import { beforeEach, expect, test, vi } from 'vitest'

// Faux serveur : le jeton « vieux » est expiré, « neuf » est valide ; le refreshToken « r-ok » marche.
const serveur = vi.hoisted(() => {
  const s = { appels: [] as string[], revoque: false }
  globalThis.fetch = (async (req: Request) => {
    const chemin = new URL(req.url).pathname
    const corps = req.method === 'POST' ? await req.text() : ''
    s.appels.push(`${req.method} ${chemin} ${req.headers.get('Authorization') ?? ''} ${corps}`.trim())
    const probleme = (status: number, code: string) =>
      Response.json({ type: `https://daba.ma/problemes/${code}`, status }, { status })
    if (chemin === '/v1/auth/refresh')
      return JSON.parse(corps).refreshToken === 'r-ok' ? Response.json(session('neuf')) : probleme(401, 'non-authentifie')
    if (chemin === '/v1/auth/deconnexion') return new Response(null, { status: 204 })
    if (s.revoque) return probleme(403, 'appareil-revoque')
    if (chemin === '/v1/bons-sortie' && req.method === 'POST') return probleme(403, 'role-non-autorise')
    if (req.headers.get('Authorization') !== 'Bearer neuf') return probleme(401, 'non-authentifie')
    return Response.json({ id: 'b1' }, { status: 201 })
  }) as typeof fetch
  const session = (jeton: string, refreshToken = 'r-ok') => ({
    accessToken: jeton, refreshToken, expireDans: 86400,
    utilisateur: { id: 'u1', telephone: '+212522123456', roles: ['GROSSISTE'], organisationId: 'o1', nouveau: false },
  })
  return Object.assign(s, { session })
})

const { ecrireSession, fetchClient, lireMotifFin, lireSession, queryClient, seDeconnecter } = await import('@/api/client')
const corpsBon = { id: '00000000-0000-4000-8000-000000000009', ambulantTelephone: '+212612345678', lignes: [{ produitId: 'p', quantite: 1 }] }

beforeEach(() => {
  serveur.appels.length = 0
  serveur.revoque = false
})

test('jeton expiré : rafraîchi une fois, la requête est rejouée avec son corps, le cache est gardé', async () => {
  ecrireSession(serveur.session('vieux') as never)
  queryClient.setQueryData(['cle'], 1)
  const { data } = await fetchClient.POST('/v1/retraits/apercu', { body: { qrJeton: 'a.b.c' } })
  expect(data).toEqual({ id: 'b1' })
  expect(serveur.appels).toContain('POST /v1/retraits/apercu Bearer neuf {"qrJeton":"a.b.c"}')
  expect(lireSession()?.accessToken).toBe('neuf')
  expect(queryClient.getQueryData(['cle'])).toBe(1)
  expect(serveur.appels.filter((a) => a.startsWith('POST /v1/auth/refresh'))).toHaveLength(1)
})

test('refreshToken refusé : session effacée, motif « session expirée »', async () => {
  ecrireSession(serveur.session('vieux', 'r-mort') as never)
  await fetchClient.GET('/v1/depots/{depotId}', { params: { path: { depotId: 'd' } } })
  expect(lireSession()).toBeNull()
  expect(lireMotifFin()).toBe('sessionExpiree')
})

test('403 appareil-revoque efface la session ; un autre 403 non', async () => {
  ecrireSession(serveur.session('neuf') as never)
  const { error } = await fetchClient.POST('/v1/bons-sortie', { body: corpsBon })
  expect(error).toMatchObject({ status: 403 })
  expect(lireSession()).not.toBeNull()

  serveur.revoque = true
  await fetchClient.GET('/v1/depots/{depotId}', { params: { path: { depotId: 'd' } } })
  expect(lireSession()).toBeNull()
  expect(lireMotifFin()).toBe('appareilRevoque')
})

test('déconnexion : fermée côté serveur avec le jeton, puis localement', async () => {
  ecrireSession(serveur.session('neuf') as never)
  await seDeconnecter()
  expect(serveur.appels).toContain('POST /v1/auth/deconnexion Bearer neuf')
  expect(lireSession()).toBeNull()
  expect(lireMotifFin()).toBeNull()
})

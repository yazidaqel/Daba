import { beforeEach, expect, it, vi } from 'vitest'

const json = (status: number, corps: unknown) => new Response(JSON.stringify(corps), { status, headers: { 'Content-Type': 'application/json' } })

// Le client capture fetch à sa création : module neuf pour chaque test.
beforeEach(() => vi.resetModules())

it('renouvelle le jeton sur 401 puis rejoue la requête', async () => {
  const appels: string[] = []
  vi.stubGlobal(
    'fetch',
    vi.fn(async (entree: Request | string) => {
      const req = typeof entree === 'string' ? new Request(new URL(entree, location.href)) : entree
      const chemin = new URL(req.url).pathname
      appels.push(`${chemin} ${req.headers.get('Authorization') ?? ''}`)
      if (chemin === '/v1/auth/refresh') return json(200, { accessToken: 'neuf', refreshToken: 'r2', expireDans: 86400, utilisateur: {} })
      return req.headers.get('Authorization') === 'Bearer neuf' ? json(200, []) : json(401, { type: 'https://daba.ma/problemes/non-authentifie' })
    }),
  )
  const { api, ecrireSession, lireSession } = await import('./client')
  ecrireSession({ accessToken: 'vieux', refreshToken: 'r1', expireDans: 1, utilisateur: {} as never })

  const r = await api.GET('/v1/categories')

  expect(r.response.status).toBe(200)
  expect(lireSession()?.refreshToken).toBe('r2')
  expect(appels).toEqual(['/v1/categories Bearer vieux', '/v1/auth/refresh ', '/v1/categories Bearer neuf'])
  vi.unstubAllGlobals()
})

it('appareil révoqué : efface la session et l’identifiant d’appareil', async () => {
  vi.stubGlobal(
    'fetch',
    vi.fn(async () => json(403, { type: 'https://daba.ma/problemes/appareil-revoque' })),
  )
  const { api, appareilId, ecrireSession, lireSession } = await import('./client')
  const ancien = appareilId()
  ecrireSession({ accessToken: 'a', refreshToken: 'r', expireDans: 1, utilisateur: {} as never })
  let motif: unknown
  addEventListener('daba:deconnexion', (e) => (motif = (e as CustomEvent).detail))

  await api.GET('/v1/categories')

  expect(lireSession()).toBeNull()
  expect(motif).toBe('appareil-revoque')
  expect(appareilId()).not.toBe(ancien)
  vi.unstubAllGlobals()
})

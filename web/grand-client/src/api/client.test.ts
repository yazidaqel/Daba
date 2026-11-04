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

it('connexion : un 401 de /v1/auth/mot-de-passe* ou otp/verify ne rafraîchit rien ; appareil-revoque y change l’identifiant d’appareil', async () => {
  const appels: string[] = []
  let revoque = false
  vi.stubGlobal(
    'fetch',
    vi.fn(async (entree: Request | string) => {
      const req = typeof entree === 'string' ? new Request(new URL(entree, location.href)) : entree
      appels.push(new URL(req.url).pathname)
      return json(401, { type: `https://daba.ma/problemes/${revoque ? 'appareil-revoque' : 'identifiants-invalides'}` })
    }),
  )
  const { api, appareilId, ecrireSession } = await import('./client')
  ecrireSession({ accessToken: 'a', refreshToken: 'r', expireDans: 1, utilisateur: {} as never })
  const telephone = '+212600000003'
  const ancien = appareilId()
  await api.POST('/v1/auth/mot-de-passe', { body: { telephone, motDePasse: 'x', appareilId: ancien } })
  expect(appareilId()).toBe(ancien)

  revoque = true
  for (const appel of [
    () => api.POST('/v1/auth/mot-de-passe', { body: { telephone, motDePasse: 'x', appareilId: appareilId() } }),
    () => api.POST('/v1/auth/mot-de-passe/definition', { body: { telephone, code: '123456', motDePasse: 'une phrase assez longue', appareilId: appareilId() } }),
    () => api.POST('/v1/auth/otp/verify', { body: { telephone, code: '123456', appareilId: appareilId() } }),
  ]) {
    const avant = appareilId()
    await appel()
    expect(appareilId()).not.toBe(avant)
  }
  expect(appels).not.toContain('/v1/auth/refresh')
  vi.unstubAllGlobals()
})

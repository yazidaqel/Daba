import { beforeEach, expect, test, vi } from 'vitest'

// Faux serveur : le jeton « vieux » est expiré, « neuf » est valide ; le refreshToken « r-ok » marche.
const serveur = vi.hoisted(() => {
  const s = { appels: [] as string[], refus: null as string | null }
  globalThis.fetch = (async (req: Request) => {
    const chemin = new URL(req.url).pathname
    const corps = req.method === 'GET' ? '' : await req.text()
    s.appels.push(`${req.method} ${chemin} ${req.headers.get('Authorization') ?? ''} ${corps}`.trim())
    const probleme = (status: number, code: string) =>
      Response.json({ type: `https://daba.ma/problemes/${code}`, status, detail: 'texte libre du serveur' }, { status })
    if (chemin === '/v1/auth/refresh')
      return JSON.parse(corps).refreshToken === 'r-ok' ? Response.json(session('neuf')) : probleme(401, 'non-authentifie')
    if (chemin === '/v1/auth/deconnexion') return new Response(null, { status: 204 })
    if (s.refus) return probleme(403, s.refus)
    if (req.headers.get('Authorization') !== 'Bearer neuf') return probleme(401, 'non-authentifie')
    return Response.json({ id: 'u2', actif: true }, { status: 200 })
  }) as typeof fetch
  const session = (jeton: string, refreshToken = 'r-ok') => ({
    accessToken: jeton, refreshToken, expireDans: 86400,
    utilisateur: { id: 'u1', telephone: '+212600000001', roles: ['ADMIN'], organisationId: 'o1', nouveau: false },
  })
  return Object.assign(s, { session })
})

const { ecrireSession, fetchClient, lireMotifFin, lireSession, queryClient, seDeconnecter } = await import('@/api/client')
const { messageErreur } = await import('@/api/erreurs')
const { traduire } = await import('@/i18n')
const t = (c: Parameters<typeof traduire>[1]) => traduire('fr', c)
const desactiver = { params: { path: { utilisateurId: '00000000-0000-4000-8000-000000000002' } } }

beforeEach(() => {
  serveur.appels.length = 0
  serveur.refus = null
})

test('jeton expiré : rafraîchi une fois, la requête est rejouée avec son corps, le cache est gardé', async () => {
  ecrireSession(serveur.session('vieux') as never)
  queryClient.setQueryData(['cle'], 1)
  const corps = { type: 'GRAND_CLIENT' as const, nom: 'Marque', utilisateurs: [{ telephone: '+212661234567' }] }
  const { data } = await fetchClient.POST('/v1/admin/organisations', { body: corps })
  expect(data).toMatchObject({ id: 'u2' })
  expect(serveur.appels).toContain(`POST /v1/admin/organisations Bearer neuf ${JSON.stringify(corps)}`)
  expect(lireSession()?.accessToken).toBe('neuf')
  expect(queryClient.getQueryData(['cle'])).toBe(1)
  expect(serveur.appels.filter((a) => a.startsWith('POST /v1/auth/refresh'))).toHaveLength(1)
})

test('refreshToken refusé : session effacée, motif « session expirée »', async () => {
  ecrireSession(serveur.session('vieux', 'r-mort') as never)
  await fetchClient.GET('/v1/admin/canaux')
  expect(lireSession()).toBeNull()
  expect(lireMotifFin()).toBe('sessionExpiree')
})

test('403 appareil-revoque efface la session ; utilisateur-desactive ou un autre 403 non', async () => {
  ecrireSession(serveur.session('neuf') as never)
  serveur.refus = 'utilisateur-desactive'
  const { error } = await fetchClient.POST('/v1/admin/utilisateurs/{utilisateurId}/desactivation', desactiver)
  expect(error).toMatchObject({ status: 403 })
  expect(lireSession()).not.toBeNull()
  // Le message vient du type, jamais du detail renvoyé par le serveur.
  expect(messageErreur(error, t)).toBe(t('erreur.utilisateurDesactive'))

  serveur.refus = 'role-non-autorise'
  await fetchClient.GET('/v1/admin/canaux')
  expect(lireSession()).not.toBeNull()

  serveur.refus = 'appareil-revoque'
  await fetchClient.GET('/v1/admin/canaux')
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

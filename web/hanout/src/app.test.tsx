import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { App } from './App'
import { traduire } from './i18n'
import { telephoneE164 } from './outils'
import { correspond } from './router'
import { textes } from './textes'

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
  localStorage.clear()
})

describe('outils', () => {
  it('normalise un numéro marocain', () => {
    expect(telephoneE164('06 12 34 56 78')).toBe('+212612345678')
    expect(telephoneE164('+212 7-12-34-56-78')).toBe('+212712345678')
    expect(telephoneE164('00212512345678')).toBe('+212512345678')
    expect(telephoneE164('0812345678')).toBeNull()
    expect(telephoneE164('061234')).toBeNull()
  })

  it('associe les routes', () => {
    expect(correspond('/documents/:id', '/documents/42')).toEqual({ id: '42' })
    expect(correspond('/documents/:id', '/documents/')).toBeNull()
    expect(correspond('/documents', '/comptes')).toBeNull()
  })

  it('a un texte non vide pour chaque clé et chaque langue', () => {
    for (const dico of Object.values(textes)) for (const v of Object.values(dico)) expect(v.trim()).not.toBe('')
    expect(traduire('ary-Latn', 'quantite', { n: 3 })).toBe('3adad: ⁨3⁩')
  })
})

const json = (corps: unknown, status = 200) =>
  new Response(JSON.stringify(corps), { status, headers: { 'Content-Type': 'application/json' } })

describe('session', () => {
  it('renouvelle un jeton expiré sans nouvel OTP, puis rejoue la requête', async () => {
    localStorage.setItem('daba.session', JSON.stringify({ accessToken: 'vieux', refreshToken: 'r1' }))
    const compte = { id: 'c1', ambulant: { id: 'a1', nom: 'Hamid' }, solde: 5000 }
    vi.stubGlobal(
      'fetch',
      vi.fn(async (req: Request | string, init?: RequestInit) => {
        if (typeof req === 'string') {
          expect(JSON.parse(String(init?.body))).toEqual({ refreshToken: 'r1' })
          return json({ accessToken: 'neuf', refreshToken: 'r2' })
        }
        if (req.headers.get('Authorization') !== 'Bearer neuf') return json({ type: 'https://daba.ma/problemes/non-authentifie' }, 401)
        return json({ page: 0, taille: 200, total: 1, contenu: [compte] })
      }),
    )
    history.replaceState(null, '', '/comptes')
    render(<App />)

    expect(await screen.findByText('Hamid')).toBeTruthy()
    expect(location.pathname).toBe('/comptes')
    expect(JSON.parse(localStorage.getItem('daba.session')!).accessToken).toBe('neuf')
  })

  async function connecterDepuisAccueil(consenti: boolean) {
    const appels: string[] = []
    vi.stubGlobal(
      'fetch',
      vi.fn(async (req: Request) => {
        const { pathname } = new URL(req.url)
        appels.push(pathname)
        if (pathname === '/v1/auth/otp') return new Response(null, { status: 202 })
        if (pathname === '/v1/auth/otp/verify') return json({ accessToken: 'a', refreshToken: 'r', utilisateur: { roles: ['HANOUT'] } })
        if (pathname === '/v1/moi/consentement') {
          expect(req.headers.get('Authorization')).toBe('Bearer a')
          return json({ traitementDonnees: consenti })
        }
        return json({ page: 0, taille: 60, total: 0, contenu: [] })
      }),
    )
    history.replaceState(null, '', '/')
    render(<App />)

    await userEvent.type(screen.getByPlaceholderText('06 12 34 56 78'), '0600000011')
    await userEvent.click(screen.getByRole('button', { name: /Recevoir le code/ }))
    await userEvent.type(await screen.findByLabelText(/Code reçu/), '123456')
    await userEvent.click(screen.getByRole('button', { name: 'Entrer' }))
    return appels
  }

  it('quitte l’écran de connexion même quand l’URL ne change pas (connexion depuis « / »)', async () => {
    await connecterDepuisAccueil(true)
    expect(await screen.findByRole('link', { name: /Bons/ })).toBeTruthy()
    expect(location.pathname).toBe('/')
  })

  it('sans consentement, va à l’écran CNDP sans afficher ni charger l’accueil', async () => {
    const appels = await connecterDepuisAccueil(false)
    await vi.waitFor(() => expect(location.pathname).toBe('/consentement'))
    expect(appels).not.toContain('/v1/produits')
    expect(appels).not.toContain('/v1/demandes-depannage')
  })

  it('garde la session si le renouvellement tombe sur une panne du serveur', async () => {
    localStorage.setItem('daba.session', JSON.stringify({ accessToken: 'vieux', refreshToken: 'r1' }))
    vi.stubGlobal(
      'fetch',
      vi.fn(async (req: Request | string) => (typeof req === 'string' ? json({}, 503) : json({}, 401))),
    )
    history.replaceState(null, '', '/comptes')
    render(<App />)

    expect(await screen.findByRole('button', { name: 'Réessayer' })).toBeTruthy()
    expect(location.pathname).toBe('/comptes')
    expect(JSON.parse(localStorage.getItem('daba.session')!).refreshToken).toBe('r1')
  })

  it('efface la session sur un appareil révoqué, mais pas sur un autre 403', async () => {
    localStorage.setItem('daba.session', JSON.stringify({ accessToken: 'x' }))
    let type = 'role-non-autorise'
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => json({ type: `https://daba.ma/problemes/${type}`, status: 403 }, 403)),
    )
    history.replaceState(null, '', '/comptes')
    const { unmount } = render(<App />)
    expect(await screen.findByRole('button', { name: 'Réessayer' })).toBeTruthy()
    expect(localStorage.getItem('daba.session')).not.toBeNull()
    unmount()

    type = 'appareil-revoque'
    localStorage.setItem('daba.appareil', 'id-revoque')
    render(<App />)
    expect(await screen.findByText(/déconnecté à distance/)).toBeTruthy()
    expect(localStorage.getItem('daba.session')).toBeNull()
    // Le serveur refuse cet id pour toujours : la prochaine connexion en prend un nouveau.
    expect(localStorage.getItem('daba.appareil')).toBeNull()
  })
})

describe('rupture', () => {
  it('signale une rupture en deux gestes : produit puis quantité', async () => {
    localStorage.setItem('daba.session', JSON.stringify({ accessToken: 'jeton' }))
    const produit = {
      id: '11111111-1111-4111-8111-111111111111',
      ean: '61100000',
      libelle: { fr: 'Huile 1 L', 'ary-Latn': 'Zit 1 L' },
      marque: 'X',
      categorie: { code: 'EPI', libelle: { fr: 'Épicerie' } },
      conditionnement: 'UNITE',
      unitesParConditionnement: 1,
    }
    const envois: Request[] = []
    vi.stubGlobal(
      'fetch',
      vi.fn(async (req: Request) => {
        const { pathname } = new URL(req.url)
        if (req.method === 'POST') {
          envois.push(req.clone())
          return json({ id: 'd1', statut: 'EN_ATTENTE', quantite: 3, creeLe: new Date().toISOString(), historique: [] }, 201)
        }
        if (pathname === '/v1/produits') return json({ page: 0, taille: 60, total: 1, contenu: [produit] })
        if (pathname === '/v1/demandes-depannage') return json({ page: 0, taille: 5, total: 0, contenu: [] })
        return json({}, 404)
      }),
    )
    history.replaceState(null, '', '/')
    render(<App />)

    await userEvent.click(await screen.findByRole('button', { name: /Huile 1 L/ }))
    await userEvent.click(screen.getByRole('button', { name: '3' }))

    await vi.waitFor(() => expect(location.pathname).toBe('/demandes/d1'))
    expect(envois).toHaveLength(1)
    expect(envois[0].headers.get('Authorization')).toBe('Bearer jeton')
    expect(await envois[0].json()).toMatchObject({ produitId: produit.id, quantite: 3 })
  })
})

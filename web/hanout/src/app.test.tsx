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

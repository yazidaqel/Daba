import { QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, expect, test, vi } from 'vitest'

// openapi-fetch lit globalThis.fetch à la création du client : on le remplace avant les imports.
vi.hoisted(() => {
  const ligne = (produitId: string, d: number, r: number, t: number) => ({
    produitId, quantiteProposee: d, quantiteDeposee: d, quantiteReservee: r, quantiteRetiree: t, quantiteRestante: d - r - t, quantiteMaxParAmbulant: 10,
  })
  const depot = (id: string, nom: string, statut: string, lignes: unknown[]) => ({
    id, campagne: { id: `c-${id}`, nom }, statutCampagne: 'ACTIVE', grossiste: { id: 'g', nom: 'G', position: { lat: 0, lon: 0 } }, statut, lignes,
  })
  const page = (contenu: unknown[]) => Response.json({ contenu, page: 0, taille: 200, total: contenu.length })
  globalThis.fetch = (async (req: Request) => {
    const chemin = new URL(req.url).pathname
    if (chemin === '/v1/depots')
      return page([
        depot('d1', 'Ramadan huile', 'RECU', [ligne('p1', 100, 10, 30), ligne('p2', 100, 0, 20)]),
        depot('d2', 'Thé vert', 'PROPOSE', [ligne('p3', 0, 0, 0)]),
        depot('d3', 'Lait été', 'CLOTURE', [ligne('p4', 50, 0, 50)]),
      ])
    if (chemin === '/v1/ambulants')
      return page([
        { id: 'a1', nom: 'Hamid', nombreBons: 2, quantiteTotale: 40, bons30Jours: 0, premierBonLe: '2026-08-01T10:00:00Z', dernierBonLe: '2026-09-01T10:00:00Z' },
        { id: 'a2', nom: 'Said', nombreBons: 9, quantiteTotale: 900, bons30Jours: 4, premierBonLe: '2026-08-01T10:00:00Z', dernierBonLe: '2026-11-05T10:00:00Z' },
        // Rattaché par déclaration seule (contrat 0.10.0) : aucun bon, donc aucune date.
        { id: 'a3', nom: 'Karim', declare: true, nombreBons: 0, quantiteTotale: 0, bons30Jours: 0 },
      ])
    return Response.json({})
  }) as typeof fetch
})

const { queryClient } = await import('@/api/client')
const { I18nProvider } = await import('@/i18n')
const { default: TableauDeBord } = await import('@/pages/TableauDeBord')

afterEach(() => (cleanup(), queryClient.clear(), localStorage.clear()))

function afficher() {
  render(
    <MemoryRouter>
      <QueryClientProvider client={queryClient}>
        <I18nProvider>
          <TableauDeBord />
        </I18nProvider>
      </QueryClientProvider>
    </MemoryRouter>,
  )
}

test('volumes par campagne (dépôts reçus seulement) et par ambulant, du plus gros volume au plus petit', async () => {
  afficher()
  const campagnes = within((await screen.findByText('Ramadan huile')).closest('table')!)
  // Ramadan : 200 déposés, 10 réservés, 50 retirés, 140 restants, 25 % écoulé ; le dépôt proposé n'apparaît pas.
  expect(campagnes.getByText('Ramadan huile').closest('tr')).toHaveTextContent(/Reçu200105014025 %/)
  expect(campagnes.getByText('Lait été').closest('tr')).toHaveTextContent(/Clôturé500500100 %/)
  expect(screen.queryByText('Thé vert')).toBeNull()

  const lignes = within((await screen.findByText('Said')).closest('tbody')!).getAllByRole('row')
  expect(lignes.map((l) => l.firstChild?.textContent)).toEqual(['Said', 'Hamid', 'Karim'])
  // Un état = icône + libellé.
  expect(within(lignes[0]).getByText('Actif sur 30 jours')).toBeInTheDocument()
  expect(within(lignes[1]).getByText('Aucun bon depuis 30 jours')).toBeInTheDocument()
  expect(screen.getByText('Unités sorties (bons de sortie)').nextSibling).toHaveTextContent(/^940$/)
})

test('en arabe : page en RTL, libellés traduits', async () => {
  localStorage.setItem('daba.langue', 'ar')
  afficher()
  expect(await screen.findByRole('heading', { level: 1, name: 'لوحة القيادة' })).toBeInTheDocument()
  expect(document.documentElement).toHaveAttribute('dir', 'rtl')
  expect(document.documentElement).toHaveAttribute('lang', 'ar')
  expect(await screen.findByText('الكميات حسب البائع المتجول')).toBeInTheDocument()
  expect(await screen.findByText('نشط خلال 30 يومًا')).toBeInTheDocument()
})

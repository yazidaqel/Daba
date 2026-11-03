import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { expect, test, vi } from 'vitest'

// openapi-fetch lit globalThis.fetch à la création du client : on le remplace avant les imports.
const appels = vi.hoisted(() => {
  const appels: string[] = []
  globalThis.fetch = (async (req: Request) => {
    appels.push(`${req.method} ${new URL(req.url).pathname}`)
    return Response.json({}, { status: 200 })
  }) as typeof fetch
  return appels
})

const { queryClient } = await import('@/api/client')
const { I18nProvider } = await import('@/i18n')
const { default: FicheUtilisateur } = await import('@/components/FicheUtilisateur')

const APPAREIL = '00000000-0000-4000-8000-0000000000a1'
const utilisateur = {
  id: '00000000-0000-4000-8000-000000000005', telephone: '+212600000005', nom: 'Hamid', roles: ['AMBULANT'],
  organisation: { id: 'o5', type: 'AMBULANT', nom: 'Hamid' }, actif: true, creeLe: '2026-11-01T10:00:00Z',
  consentement: { versionTexte: 'ambulant-2026-11-v2-fr', traitementDonnees: true, geolocalisation: true, enregistreLe: '2026-11-01T10:05:00Z' },
  appareils: [{ appareilId: APPAREIL, plateforme: 'ANDROID', creeLe: '2026-11-01T10:00:00Z', vuLe: '2026-11-03T09:00:00Z', compteActuel: true, sessionOuverte: true, pushActif: true, revoque: false,
    autresComptes: [{ id: 'u7', telephone: '+212600000007', nom: 'Said' }, { id: 'u8', telephone: '+212600000008' }] }],
} as const

test('révocation : rien ne part sans confirmation explicite de l’action irréversible', async () => {
  vi.spyOn(queryClient, 'invalidateQueries').mockResolvedValue()
  const u = userEvent.setup()
  render(
    <QueryClientProvider client={queryClient}>
      <I18nProvider>
        <MemoryRouter>
          <FicheUtilisateur utilisateur={utilisateur as never} />
        </MemoryRouter>
      </I18nProvider>
    </QueryClientProvider>,
  )
  expect(screen.getByText('Session ouverte')).toBeInTheDocument()
  expect(screen.getByText('Géolocalisation acceptée')).toBeInTheDocument()
  expect(screen.getByText('Compte actuel sur cet appareil')).toBeInTheDocument()
  expect(screen.getByText('Autres comptes sur cet appareil :')).toHaveTextContent('Said, +212600000008')

  // Ouvrir puis annuler : aucun appel.
  await u.click(screen.getByRole('button', { name: 'Déconnecter à distance' }))
  const dialogue = await screen.findByRole('alertdialog')
  expect(dialogue).toHaveTextContent('Action définitive')
  expect(dialogue).toHaveTextContent('opérations hors ligne pas encore synchronisées')
  // L'appareil physique est effacé pour chacun de ses comptes : tous sont nommés (nom, sinon téléphone).
  expect(dialogue).toHaveTextContent('Le téléphone est effacé pour tous les comptes qui s’y sont connectés (3) :')
  expect(within(dialogue).getAllByRole('listitem').map((li) => li.textContent)).toEqual([
    'Hamid · +212600000005', 'Said · +212600000007', '+212600000008',
  ])
  await u.click(screen.getByRole('button', { name: 'Annuler' }))
  expect(screen.queryByRole('alertdialog')).toBeNull()
  expect(appels).toEqual([])

  // Confirmer : un seul POST, vers cet appareil.
  await u.click(screen.getByRole('button', { name: 'Déconnecter à distance' }))
  await u.click(await screen.findByRole('button', { name: 'Oui, déconnecter définitivement' }))
  await vi.waitFor(() => expect(appels).toEqual([`POST /v1/admin/appareils/${APPAREIL}/revocation`]))
})

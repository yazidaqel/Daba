import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { expect, test, vi } from 'vitest'

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

const ID = '00000000-0000-4000-8000-000000000002'
const grossiste = {
  id: ID, telephone: '+212600000002', nom: 'Karim Berrada', roles: ['GROSSISTE'],
  organisation: { id: 'o2', type: 'GROSSISTE', nom: 'Dépôt Derb Omar' }, actif: true, creeLe: '2026-11-01T10:00:00Z',
  motDePasseDefini: true, motDePasseModifieLe: '2026-11-03T09:00:00Z', methodeConnexion: 'MOT_DE_PASSE_ET_OTP', appareils: [],
}

const rendre = (u: unknown) =>
  render(
    <QueryClientProvider client={queryClient}>
      <I18nProvider>
        <MemoryRouter>
          <FicheUtilisateur utilisateur={u as never} />
        </MemoryRouter>
      </I18nProvider>
    </QueryClientProvider>,
  )

test('réinitialisation du mot de passe : méthode et état affichés, rien ne part sans confirmation', async () => {
  vi.spyOn(queryClient, 'invalidateQueries').mockResolvedValue()
  const u = userEvent.setup()
  const { unmount } = rendre(grossiste)
  expect(screen.getByText('Mot de passe et code par SMS')).toBeInTheDocument()
  expect(screen.getByText('Mot de passe défini')).toBeInTheDocument()
  expect(screen.getByText(/^modifié le /)).toBeInTheDocument()

  await u.click(screen.getByRole('button', { name: 'Réinitialiser le mot de passe' }))
  const dialogue = await screen.findByRole('alertdialog')
  expect(dialogue).toHaveTextContent('Réinitialiser le mot de passe de Karim Berrada ?')
  expect(dialogue).toHaveTextContent('toutes ses sessions se ferment')
  await u.click(screen.getByRole('button', { name: 'Annuler' }))
  expect(appels).toEqual([])

  await u.click(screen.getByRole('button', { name: 'Réinitialiser le mot de passe' }))
  const confirmer = (await screen.findAllByRole('button', { name: 'Réinitialiser le mot de passe' })).pop()!
  await u.click(confirmer)
  await vi.waitFor(() => expect(appels).toEqual([`POST /v1/admin/utilisateurs/${ID}/reinitialisation-mot-de-passe`]))
  unmount()

  // Ambulant : jamais de mot de passe, donc ni état ni action.
  rendre({ ...grossiste, roles: ['AMBULANT'], organisation: { id: 'o3', type: 'AMBULANT', nom: 'A' }, motDePasseDefini: false, methodeConnexion: 'OTP' })
  expect(screen.getByText('Code par SMS')).toBeInTheDocument()
  expect(screen.queryByText('Aucun mot de passe')).toBeNull()
  expect(screen.queryByRole('button', { name: 'Réinitialiser le mot de passe' })).toBeNull()
})

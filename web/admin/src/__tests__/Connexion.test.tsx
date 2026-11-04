import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, test, vi } from 'vitest'

const ORG = '00000000-0000-4000-8000-0000000000b1'

const serveur = vi.hoisted(() => {
  const s = { puts: [] as unknown[], reponses: [] as Response[] }
  globalThis.fetch = (async (req: Request) => {
    const chemin = new URL(req.url).pathname
    if (req.method === 'PUT') {
      s.puts.push(await req.json())
      return s.reponses.shift()!
    }
    if (chemin === '/v1/admin/organisations')
      return Response.json({ page: 0, taille: 200, total: 1, contenu: [{ id: '00000000-0000-4000-8000-0000000000b1', type: 'GROSSISTE', nom: 'Dépôt Derb Omar', creeLe: '2026-11-01T00:00:00Z', nombreUtilisateurs: 1 }] })
    return Response.json({ roles: [{ role: 'GROSSISTE', methode: 'OTP' }, { role: 'ADMIN', methode: 'OTP' }], organisations: [] })
  }) as typeof fetch
  return s
})

const { queryClient } = await import('@/api/client')
const { I18nProvider } = await import('@/i18n')
const { default: Connexion } = await import('@/pages/Connexion')

test('connexion : méthode par rôle et exception par organisation → corps du PUT ; avertissement ADMIN ; erreur 400', async () => {
  const u = userEvent.setup()
  render(
    <QueryClientProvider client={queryClient}>
      <I18nProvider>
        <Connexion />
      </I18nProvider>
    </QueryClientProvider>,
  )
  // Un rôle absent de la réponse s'affiche en OTP (défaut du serveur).
  expect(await screen.findByLabelText('Méthode : Grand client')).toHaveValue('OTP')
  expect(screen.queryByText(/chaque administrateur devra créer son mot de passe/)).toBeNull()

  await u.selectOptions(screen.getByLabelText('Méthode : Administrateur'), 'MOT_DE_PASSE_ET_OTP')
  expect(screen.getByText(/chaque administrateur devra créer son mot de passe/)).toBeInTheDocument()

  // Exception : elle part de la méthode du rôle de l'organisation, puis se règle.
  await u.selectOptions(screen.getByLabelText('Organisation'), ORG)
  await u.click(screen.getByRole('button', { name: 'Ajouter' }))
  const derb = screen.getAllByRole('row').find((r) => within(r).queryByText('Dépôt Derb Omar'))!
  expect(within(derb).getByRole('combobox')).toHaveValue('OTP')
  await u.selectOptions(within(derb).getByRole('combobox'), 'MOT_DE_PASSE')

  serveur.reponses.push(Response.json({ type: 'https://daba.ma/problemes/requete-invalide', status: 400, detail: 'Organisation inconnue', erreurs: [{ champ: 'organisations[0].organisationId', message: 'inconnue' }] }, { status: 400 }))
  await u.click(screen.getByRole('button', { name: 'Enregistrer la configuration' }))
  expect(await screen.findByText('Organisation inconnue')).toBeInTheDocument()
  expect(screen.getByText('organisations[0].organisationId')).toBeInTheDocument()
  expect(serveur.puts[0]).toEqual({
    roles: [
      { role: 'GROSSISTE', methode: 'OTP' },
      { role: 'GRAND_CLIENT', methode: 'OTP' },
      { role: 'ADMIN', methode: 'MOT_DE_PASSE_ET_OTP' },
    ],
    organisations: [{ organisationId: ORG, methode: 'MOT_DE_PASSE' }],
    fermerSessions: false,
  })

  // Case décochée par défaut ; cochée, elle part dans le corps (jamais enregistrée par le serveur).
  const fermer = screen.getByRole('checkbox', { name: 'Fermer les sessions ouvertes des comptes concernés' })
  expect(fermer).not.toBeChecked()
  await u.click(fermer)
  serveur.reponses.push(Response.json(serveur.puts[0]))
  await u.click(screen.getByRole('button', { name: 'Enregistrer la configuration' }))
  expect(await screen.findByText('Méthode enregistrée, appliquée à la prochaine connexion.')).toBeInTheDocument()
  expect(serveur.puts[1]).toMatchObject({ fermerSessions: true, roles: expect.arrayContaining([{ role: 'ADMIN', methode: 'MOT_DE_PASSE_ET_OTP' }]) })
  expect(fermer).not.toBeChecked()
})

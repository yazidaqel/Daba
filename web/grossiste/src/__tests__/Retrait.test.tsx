import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, test, vi } from 'vitest'

// openapi-fetch lit globalThis.fetch à la création du client : on le remplace avant les imports.
const appels = vi.hoisted(() => {
  const appels: { url: string; corps: Record<string, unknown> }[] = []
  globalThis.fetch = (async (req: Request) => {
    if (req.method === 'GET') return Response.json({ contenu: [], page: 0, taille: 200, total: 0 })
    const corps = await req.json()
    appels.push({ url: new URL(req.url).pathname, corps })
    const resa = {
      id: '00000000-0000-4000-8000-000000000001', statut: 'RESERVEE', campagneId: 'c', depotId: 'd', produitId: 'p',
      grossiste: { id: 'g', nom: 'G', position: { lat: 0, lon: 0 } }, ambulant: { id: 'a', nom: 'Hamid' },
      quantiteReservee: 5, reserveeLe: '2026-11-02T10:00:00Z', expireLe: '2026-11-03T10:00:00Z',
    }
    const retrait = req.url.endsWith('/v1/retraits')
    return new Response(JSON.stringify(retrait ? { ...resa, statut: 'RETIREE', quantiteRetiree: corps.quantiteRemise } : resa), {
      status: retrait ? 201 : 200,
      headers: { 'Content-Type': 'application/json' },
    })
  }) as typeof fetch
  return appels
})

const { queryClient } = await import('@/api/client')
const { I18nProvider } = await import('@/i18n')
const { default: Retrait } = await import('@/pages/Retrait')

test('retrait par douchette : aperçu, quantité bornée à la réservée, validation avec appareilId', async () => {
  vi.spyOn(queryClient, 'invalidateQueries').mockResolvedValue()
  const u = userEvent.setup()
  render(
    <QueryClientProvider client={queryClient}>
      <I18nProvider>
        <Retrait />
      </I18nProvider>
    </QueryClientProvider>,
  )
  await u.type(screen.getByLabelText('Lecture par douchette'), 'jws.qr.jeton{Enter}')
  const quantite = await screen.findByLabelText('Quantité remise')
  expect(quantite).toHaveValue(5)
  expect(screen.getByText('Hamid')).toBeInTheDocument()

  await u.clear(quantite)
  await u.type(quantite, '6')
  expect(screen.getByRole('button', { name: 'Valider le retrait' })).toBeDisabled()

  await u.clear(quantite)
  await u.type(quantite, '3')
  await u.click(screen.getByRole('button', { name: 'Valider le retrait' }))
  expect(await screen.findByText('Retrait validé : 3 remis.')).toBeInTheDocument()

  const validation = appels.find((a) => a.url === '/v1/retraits')!
  expect(validation.corps).toMatchObject({ qrJeton: 'jws.qr.jeton', quantiteRemise: 3 })
  expect(validation.corps.appareilId).toMatch(/^[0-9a-f-]{36}$/)
  expect(appels[0]).toEqual({ url: '/v1/retraits/apercu', corps: { qrJeton: 'jws.qr.jeton' } })
})

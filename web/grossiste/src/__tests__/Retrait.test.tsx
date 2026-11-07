import { QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, expect, test, vi } from 'vitest'

// openapi-fetch lit globalThis.fetch à la création du client : on le remplace avant les imports.
const appels = vi.hoisted(() => {
  const appels: { url: string; corps: Record<string, unknown> }[] = []
  globalThis.fetch = (async (req: Request) => {
    if (req.url.endsWith('/v1/depots/d')) return Response.json({ lignes: [{ produitId: 'p', quantiteMaxParAmbulant: 4 }] })
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

test('retrait par douchette : aperçu, quantité bornée à la réservée et au plafond, validation avec appareilId', async () => {
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
  // Réservée 5, plafond par ambulant 4 : on propose 4 et on refuse 5.
  await vi.waitFor(() => expect(quantite).toHaveValue(4))
  expect(screen.getByText('Hamid')).toBeInTheDocument()

  await u.clear(quantite)
  await u.type(quantite, '5')
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

function afficher() {
  render(
    <QueryClientProvider client={queryClient}>
      <I18nProvider>
        <Retrait />
      </I18nProvider>
    </QueryClientProvider>,
  )
}

// Décodeur simulé : l'API BarcodeDetector (native ou ponyfill) est remplacée, la photo n'est pas décodée pour de vrai.
function decodeur(valeurs: string[]) {
  const detect = vi.fn(async () => valeurs.map((rawValue) => ({ rawValue })))
  vi.stubGlobal('BarcodeDetector', class { detect = detect })
  vi.stubGlobal('createImageBitmap', vi.fn(async () => ({ close: vi.fn() })))
  return detect
}

afterEach(() => (cleanup(), vi.unstubAllGlobals()))

test('repli sans caméra : le QR lu dans une photo part à l’aperçu, comme un scan', async () => {
  const detect = decodeur(['jws.photo.jeton'])
  const u = userEvent.setup()
  afficher()
  const champ = screen.getByLabelText('Lire le QR depuis une photo')
  expect(champ).toHaveAttribute('capture', 'environment')
  expect(champ).toHaveAttribute('accept', 'image/*')
  await u.upload(champ, new File(['x'], 'qr.jpg', { type: 'image/jpeg' }))
  await screen.findByLabelText('Quantité remise')
  expect(detect).toHaveBeenCalledOnce()
  expect(appels.at(-1)).toEqual({ url: '/v1/retraits/apercu', corps: { qrJeton: 'jws.photo.jeton' } })
})

test('photo sans QR lisible : message, aucun appel à l’API, aucune saisie manuelle proposée', async () => {
  decodeur([])
  const avant = appels.length
  const u = userEvent.setup()
  afficher()
  await u.upload(screen.getByLabelText('Lire le QR depuis une photo'), new File(['x'], 'flou.jpg', { type: 'image/jpeg' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Aucun QR lisible sur cette photo')
  expect(appels.length).toBe(avant)
  expect(screen.queryByLabelText('Quantité remise')).toBeNull()
})

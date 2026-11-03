import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, test, vi } from 'vitest'

const ORG = '00000000-0000-4000-8000-0000000000b1'
const config = {
  canaux: [
    { canal: 'PUSH', actif: true, fournisseur: 'JOURNAL', fournisseursDisponibles: [{ code: 'JOURNAL', configure: true }, { code: 'FCM', configure: false }] },
    { canal: 'SMS', actif: true, fournisseur: 'INFOBIP', fournisseursDisponibles: [{ code: 'JOURNAL', configure: true }, { code: 'INFOBIP', configure: true }] },
    { canal: 'WHATSAPP', actif: true, fournisseur: 'JOURNAL', fournisseursDisponibles: [{ code: 'JOURNAL', configure: true }] },
  ],
  types: [
    { type: 'OTP', principal: 'SMS', inactifs: [] },
    { type: 'PROMO_CONFIRMEE', principal: 'PUSH', repli: 'SMS', inactifs: [] },
  ],
  organisations: [],
}

const serveur = vi.hoisted(() => {
  const s = { puts: [] as unknown[], reponses: [] as Response[], config: null as unknown }
  globalThis.fetch = (async (req: Request) => {
    const chemin = new URL(req.url).pathname
    if (req.method === 'PUT') {
      s.puts.push(await req.json())
      return s.reponses.shift()!
    }
    if (chemin === '/v1/admin/organisations')
      return Response.json({ page: 0, taille: 200, total: 1, contenu: [{ id: '00000000-0000-4000-8000-0000000000b1', type: 'GROSSISTE', nom: 'Dépôt Derb Omar', creeLe: '2026-11-01T00:00:00Z', nombreUtilisateurs: 1 }] })
    return Response.json(s.config)
  }) as typeof fetch
  return s
})
serveur.config = config

const { queryClient } = await import('@/api/client')
const { I18nProvider } = await import('@/i18n')
const { default: Canaux } = await import('@/pages/Canaux')
const probleme = (status: number, code: string, extra = {}) =>
  Response.json({ type: `https://daba.ma/problemes/${code}`, status, detail: 'détail serveur', ...extra }, { status, headers: { 'Content-Type': 'application/problem+json' } })

test('canaux : le formulaire envoie la configuration éditée sans champ en lecture seule, et affiche les refus du serveur', async () => {
  const u = userEvent.setup()
  render(
    <QueryClientProvider client={queryClient}>
      <I18nProvider>
        <Canaux />
      </I18nProvider>
    </QueryClientProvider>,
  )
  // Fournisseur non configuré : proposé mais pas sélectionnable.
  const push = await screen.findByLabelText('Fournisseur du canal Push')
  expect(within(push).getByRole('option', { name: 'Firebase Cloud Messaging (non configuré)' })).toBeDisabled()

  // WhatsApp coupé au niveau système ; promo sans repli et SMS coupé ; Derb Omar privé de WhatsApp.
  const whatsapp = screen.getAllByRole('row').find((r) => within(r).queryByText('WhatsApp'))!
  await u.click(within(whatsapp).getByRole('checkbox'))
  await u.selectOptions(screen.getByLabelText('Canal de repli : Promo confirmée chez un grossiste'), '')
  const promo = screen.getAllByRole('row').find((r) => within(r).queryByText('Promo confirmée chez un grossiste'))!
  await u.click(within(promo).getByLabelText('Couper SMS'))
  await u.selectOptions(screen.getByLabelText('Organisation'), ORG)
  await u.click(screen.getByRole('button', { name: 'Ajouter' }))
  const derb = screen.getAllByRole('row').find((r) => within(r).queryByText('Dépôt Derb Omar'))!
  await u.click(within(derb).getByLabelText('Couper WhatsApp'))

  serveur.reponses.push(probleme(422, 'fournisseur-non-configure'))
  await u.click(screen.getByRole('button', { name: 'Enregistrer la configuration' }))
  expect(await screen.findByText(/Ce fournisseur n’est pas configuré sur le serveur/)).toBeInTheDocument()
  expect(serveur.puts[0]).toEqual({
    canaux: [
      { canal: 'PUSH', actif: true, fournisseur: 'JOURNAL' },
      { canal: 'SMS', actif: true, fournisseur: 'INFOBIP' },
      { canal: 'WHATSAPP', actif: false, fournisseur: 'JOURNAL' },
    ],
    types: [
      { type: 'OTP', principal: 'SMS', inactifs: [] },
      { type: 'PROMO_CONFIRMEE', principal: 'PUSH', inactifs: ['SMS'] },
    ],
    organisations: [{ organisationId: ORG, inactifs: ['WHATSAPP'] }],
  })

  // 400 (règle de l'OTP) : texte du serveur et détail par champ.
  serveur.reponses.push(probleme(400, 'requete-invalide', { detail: 'L’OTP part seulement par SMS', erreurs: [{ champ: 'types[0].repli', message: 'interdit pour OTP' }] }))
  await u.click(screen.getByRole('button', { name: 'Enregistrer la configuration' }))
  expect(await screen.findByText('L’OTP part seulement par SMS')).toBeInTheDocument()
  expect(screen.getByText('types[0].repli')).toBeInTheDocument()
  expect(screen.getByText(/interdit pour OTP/)).toBeInTheDocument()
})

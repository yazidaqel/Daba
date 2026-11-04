import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'

// Faux serveur : le comportement dépend du numéro (le portail, lui, ne connaît pas la méthode à l'avance).
const serveur = vi.hoisted(() => {
  const s = { appels: [] as { chemin: string; corps: Record<string, string> }[] }
  const probleme = (status: number, code: string) =>
    Response.json({ type: `https://daba.ma/problemes/${code}`, status, detail: 'texte libre du serveur' }, { status })
  const session = { accessToken: 'a', refreshToken: 'r', expireDans: 86400, utilisateur: { id: 'u1', telephone: '+212600000001', roles: ['ADMIN'], organisationId: 'o1' } }
  globalThis.fetch = (async (req: Request) => {
    const chemin = new URL(req.url).pathname
    const corps = await req.json()
    s.appels.push({ chemin, corps })
    const tel: string = corps.telephone
    if (chemin === '/v1/auth/otp') return Response.json({ expireDans: 300 }, { status: 202 })
    if (chemin === '/v1/auth/mot-de-passe') {
      if (tel.endsWith('11')) return Response.json({ session })
      if (tel.endsWith('12')) return Response.json({ defi: 'defi-12', expireDans: 300 })
      if (tel.endsWith('16')) return probleme(429, 'trop-de-demandes')
      if (tel.endsWith('18'))
        return Response.json({ type: 'https://daba.ma/problemes/trop-de-demandes', status: 429 }, { status: 429, headers: { 'Retry-After': '90' } })
      if (tel.endsWith('17')) return probleme(403, 'connexion-par-code')
      return probleme(401, 'identifiants-invalides')
    }
    if (chemin === '/v1/auth/otp/verify') {
      if (tel.endsWith('13')) return probleme(403, 'mot-de-passe-a-definir')
      if (tel.endsWith('14')) return probleme(403, 'mot-de-passe-requis')
      return Response.json(session)
    }
    if (chemin === '/v1/auth/mot-de-passe/definition') return Response.json(session)
    return probleme(404, 'introuvable')
  }) as typeof fetch
  return s
})

const { lireSession } = await import('@/api/client')
const { I18nProvider } = await import('@/i18n')
const { default: Login } = await import('@/pages/Login')
const { ChampMotDePasse } = await import('@/components/ChampMotDePasse')

const SECRET = 'une phrase de passe assez longue'
const u = userEvent.setup()
const appels = (chemin: string) => serveur.appels.filter((a) => a.chemin === chemin).map((a) => a.corps)

// Les scénarios partent du choix « mot de passe » déjà retenu ; le premier test vérifie le défaut d'un navigateur neuf.
beforeEach(() => {
  localStorage.clear()
  localStorage.setItem('daba.connexion', 'motDePasse')
  serveur.appels.length = 0
  render(<I18nProvider><Login /></I18nProvider>)
})
afterEach(cleanup)

test('navigateur neuf : code SMS proposé par défaut, comme la méthode par défaut du serveur', () => {
  cleanup()
  localStorage.clear()
  render(<I18nProvider><Login /></I18nProvider>)
  expect(screen.getByRole('radio', { name: 'Code par SMS' })).toBeChecked()
  expect(screen.getByRole('radio', { name: 'Mot de passe' })).not.toBeChecked()
})

test('nouveau mot de passe : aucune limite de saisie qui couperait une phrase longue', () => {
  cleanup()
  render(
    <I18nProvider>
      <ChampMotDePasse id="n" label="Nouveau" valeur="" onChange={() => {}} autoComplete="new-password" />
      <ChampMotDePasse id="a" label="Actuel" valeur="" onChange={() => {}} autoComplete="current-password" />
    </I18nProvider>,
  )
  expect(champ('n')).not.toHaveAttribute('maxlength')
  expect(champ('a')).toHaveAttribute('maxlength', '1024')
  // Libellé fixe, l'état est donné par aria-pressed seul.
  expect(screen.getAllByRole('button', { name: 'Afficher le mot de passe' })[0]).toHaveAttribute('aria-pressed', 'false')
})

async function telephone(numero: string) {
  await u.type(screen.getByLabelText('Numéro de téléphone'), numero)
}
const champ = (id: string) => document.getElementById(id) as HTMLInputElement
// Aucun secret ne reste sur le navigateur : ni mot de passe, ni numéro (seul le choix de méthode).
const rienDeSecret = (numero: string) => {
  const tout = JSON.stringify({ ...localStorage })
  expect(tout).not.toContain(SECRET)
  expect(tout).not.toContain(numero.slice(1))
}

test('MOT_DE_PASSE : téléphone + mot de passe → session', async () => {
  await telephone('0600000011')
  await u.type(champ('mdp'), SECRET)
  await u.click(screen.getByRole('button', { name: 'Se connecter' }))
  await vi.waitFor(() => expect(lireSession()?.accessToken).toBe('a'))
  expect(appels('/v1/auth/mot-de-passe')[0]).toMatchObject({ telephone: '+212600000011', motDePasse: SECRET })
  expect(appels('/v1/auth/mot-de-passe')[0].appareilId).toMatch(/^[0-9a-f-]{36}$/)
  rienDeSecret('0600000011')
})

test('MOT_DE_PASSE_ET_OTP : le défi accompagne le code SMS', async () => {
  await telephone('0600000012')
  await u.type(champ('mdp'), SECRET)
  await u.click(screen.getByRole('button', { name: 'Se connecter' }))
  expect(await screen.findByText('Mot de passe accepté. Saisissez maintenant le code reçu par SMS.')).toBeInTheDocument()
  await u.type(screen.getByLabelText('Code reçu par SMS (6 chiffres)'), '123456')
  await u.click(screen.getByRole('button', { name: 'Se connecter' }))
  await vi.waitFor(() => expect(lireSession()).not.toBeNull())
  expect(appels('/v1/auth/otp/verify')[0]).toMatchObject({ telephone: '+212600000012', code: '123456', defi: 'defi-12' })
  rienDeSecret('0600000012')
})

test('mot-de-passe-a-definir : le même code sert à créer le mot de passe (15 caractères au moins, confirmé)', async () => {
  await u.click(screen.getByRole('radio', { name: 'Code par SMS' }))
  await telephone('0600000013')
  await u.click(screen.getByRole('button', { name: 'Recevoir le code par SMS' }))
  await u.type(await screen.findByLabelText('Code reçu par SMS (6 chiffres)'), '654321')
  await u.click(screen.getByRole('button', { name: 'Se connecter' }))
  expect(await screen.findByText('Créez votre mot de passe')).toBeInTheDocument()
  expect(screen.queryByLabelText('Code reçu par SMS (6 chiffres)')).toBeNull()
  expect(champ('nouveau')).toHaveAttribute('autocomplete', 'new-password')

  await u.type(champ('nouveau'), 'quatorze carac')
  await u.type(champ('confirmation'), 'quatorze carac')
  await u.click(screen.getByRole('button', { name: 'Enregistrer et se connecter' }))
  expect(await screen.findByText('Mot de passe trop court : 15 caractères au moins.')).toBeInTheDocument()

  await u.clear(champ('nouveau'))
  await u.clear(champ('confirmation'))
  await u.type(champ('nouveau'), SECRET)
  await u.type(champ('confirmation'), SECRET + '!')
  await u.click(screen.getByRole('button', { name: 'Enregistrer et se connecter' }))
  expect(await screen.findByText('Les deux mots de passe ne sont pas identiques.')).toBeInTheDocument()
  expect(appels('/v1/auth/mot-de-passe/definition')).toHaveLength(0)

  // Afficher / masquer
  await u.click(screen.getAllByRole('button', { name: 'Afficher le mot de passe' })[1])
  expect(champ('confirmation')).toHaveAttribute('type', 'text')
  await u.clear(champ('confirmation'))
  await u.type(champ('confirmation'), SECRET)
  await u.click(screen.getByRole('button', { name: 'Enregistrer et se connecter' }))
  await vi.waitFor(() => expect(lireSession()).not.toBeNull())
  expect(appels('/v1/auth/mot-de-passe/definition')[0]).toMatchObject({ telephone: '+212600000013', code: '654321', motDePasse: SECRET })
  rienDeSecret('0600000013')
})

test('mot-de-passe-requis : retour au mot de passe, choix retenu', async () => {
  await u.click(screen.getByRole('radio', { name: 'Code par SMS' }))
  expect(localStorage.getItem('daba.connexion')).toBe('code')
  await telephone('0600000014')
  await u.click(screen.getByRole('button', { name: 'Recevoir le code par SMS' }))
  await u.type(await screen.findByLabelText('Code reçu par SMS (6 chiffres)'), '111111')
  await u.click(screen.getByRole('button', { name: 'Se connecter' }))
  expect(await screen.findByText(/Ce compte se connecte avec un mot de passe/)).toBeInTheDocument()
  expect(screen.getByRole('radio', { name: 'Mot de passe' })).toBeChecked()
  expect(champ('mdp')).toBeInTheDocument()
  expect(localStorage.getItem('daba.connexion')).toBe('motDePasse')
  expect(lireSession()).toBeNull()
})

test('mot de passe oublié : code SMS puis nouveau mot de passe par définition', async () => {
  await telephone('0600000015')
  await u.click(screen.getByRole('button', { name: 'Mot de passe oublié ?' }))
  expect(await screen.findByText('Mot de passe oublié')).toBeInTheDocument()
  expect(appels('/v1/auth/otp')[0]).toMatchObject({ telephone: '+212600000015' })
  await u.type(screen.getByLabelText('Code reçu par SMS (6 chiffres)'), '222222')
  await u.type(champ('nouveau'), SECRET)
  await u.type(champ('confirmation'), SECRET)
  await u.click(screen.getByRole('button', { name: 'Enregistrer et se connecter' }))
  await vi.waitFor(() => expect(lireSession()).not.toBeNull())
  expect(appels('/v1/auth/mot-de-passe/definition')[0]).toMatchObject({ code: '222222', motDePasse: SECRET })
  rienDeSecret('0600000015')
})

test('429 trop-de-demandes (avec ou sans Retry-After), 401 identifiants-invalides générique, connexion-par-code bascule sur le code', async () => {
  await telephone('0600000016')
  await u.type(champ('mdp'), SECRET)
  await u.click(screen.getByRole('button', { name: 'Se connecter' }))
  expect(await screen.findByText('Trop d’essais. Patientez avant de recommencer.')).toBeInTheDocument()
  expect(champ('mdp')).toHaveValue('')

  // Délai progressif : l'attente annoncée par Retry-After s'affiche.
  await u.clear(screen.getByLabelText('Numéro de téléphone'))
  await telephone('0600000018')
  await u.type(champ('mdp'), SECRET)
  await u.click(screen.getByRole('button', { name: 'Se connecter' }))
  expect(await screen.findByText('Trop d’essais. Réessayez dans 2 min.')).toBeInTheDocument()

  await u.clear(screen.getByLabelText('Numéro de téléphone'))
  await telephone('0600000019')
  await u.type(champ('mdp'), SECRET)
  await u.click(screen.getByRole('button', { name: 'Se connecter' }))
  // Message identique quelle que soit la raison (numéro inconnu, mauvais mot de passe…), jamais le detail.
  expect(await screen.findByText('Numéro ou mot de passe incorrect.')).toBeInTheDocument()
  expect(screen.queryByText('texte libre du serveur')).toBeNull()

  await u.clear(screen.getByLabelText('Numéro de téléphone'))
  await telephone('0600000017')
  await u.type(champ('mdp'), SECRET)
  await u.click(screen.getByRole('button', { name: 'Se connecter' }))
  expect(await screen.findByText('Ce compte se connecte par code SMS : un code vient de vous être envoyé.')).toBeInTheDocument()
  expect(screen.getByLabelText('Code reçu par SMS (6 chiffres)')).toBeInTheDocument()
  expect(appels('/v1/auth/otp')).toEqual([{ telephone: '+212600000017', langue: 'fr' }])
  expect(localStorage.getItem('daba.connexion')).toBe('code')
  rienDeSecret('0600000017')
})

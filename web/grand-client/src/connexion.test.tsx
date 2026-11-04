import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import type { Cle } from '@/i18n'

// Faux serveur : chaque chemin répond selon `reponses` ; les corps envoyés sont gardés dans `appels`.
const serveur = vi.hoisted(() => {
  const s = {
    appels: [] as { chemin: string; corps: Record<string, string> }[],
    reponses: {} as Record<string, (corps: Record<string, string>) => Response>,
  }
  globalThis.fetch = (async (req: Request) => {
    const chemin = new URL(req.url).pathname
    const corps = req.method === 'GET' ? {} : await req.json().catch(() => ({}))
    s.appels.push({ chemin, corps })
    return s.reponses[chemin]?.(corps) ?? new Response(null, { status: 500 })
  }) as typeof fetch
  return s
})

const probleme = (status: number, code: string) => Response.json({ type: `https://daba.ma/problemes/${code}`, status }, { status })
const SESSION = { accessToken: 'a', refreshToken: 'r', expireDans: 86400, utilisateur: { id: 'u', telephone: '+212600000003', roles: ['GRAND_CLIENT'], organisationId: 'o', nouveau: false } }
const TEL = '+212600000003'
const MDP = 'une phrase assez longue'

const { changerMotDePasse, choisirMethode, etatInitial, methodeRetenue, oublie, soumettre, texteAvis } = await import('@/connexion')
const { I18nProvider } = await import('@/i18n')
const { MESSAGES } = await import('@/messages')
const traduire = (l: 'fr', cle: Cle, vars: Record<string, string | number> = {}) => MESSAGES[l][cle].replace(/\{(\w+)\}/g, (_, k: string) => String(vars[k]))
const { MotDePasse } = await import('@/pages/MotDePasse')

afterEach(cleanup)

const chemins = () => serveur.appels.map((a) => a.chemin)

beforeEach(() => {
  serveur.appels.length = 0
  serveur.reponses = { '/v1/auth/otp': () => Response.json({ expireDans: 300 }, { status: 202 }) }
  localStorage.clear()
})

test('le dernier choix de méthode est retenu sur le navigateur, jamais le numéro', () => {
  expect(methodeRetenue()).toBe('code')
  choisirMethode(etatInitial(), 'motDePasse')
  expect(methodeRetenue()).toBe('motDePasse')
  expect(JSON.stringify({ ...localStorage })).not.toContain('600000003')
})

test('code SMS seul (OTP) : inchangé', async () => {
  serveur.reponses['/v1/auth/otp/verify'] = () => Response.json(SESSION)
  const r1 = await soumettre(etatInitial('code'), { telephone: TEL }, 'fr')
  expect(r1.etat.etape).toEqual({ nom: 'code' })
  const r2 = await soumettre(r1.etat, { code: '123456' }, 'fr')
  expect(r2.session).toEqual(SESSION)
  expect(serveur.appels[1].corps).toMatchObject({ telephone: TEL, code: '123456' })
  expect(serveur.appels[1].corps.defi).toBeUndefined()
})

test('mot de passe seul : la Session arrive directement', async () => {
  serveur.reponses['/v1/auth/mot-de-passe'] = () => Response.json({ session: SESSION })
  const r = await soumettre(etatInitial('motDePasse'), { telephone: TEL, motDePasse: MDP }, 'fr')
  expect(r.session).toEqual(SESSION)
  expect(serveur.appels[0].corps).toMatchObject({ telephone: TEL, motDePasse: MDP, langue: 'fr' })
})

test('mot de passe puis code : le défi accompagne le code', async () => {
  serveur.reponses['/v1/auth/mot-de-passe'] = () => Response.json({ defi: 'd1', expireDans: 300 })
  serveur.reponses['/v1/auth/otp/verify'] = () => Response.json(SESSION)
  const r1 = await soumettre(etatInitial('motDePasse'), { telephone: TEL, motDePasse: MDP }, 'fr')
  expect(r1.etat.etape).toEqual({ nom: 'code', defi: 'd1' })
  const r2 = await soumettre(r1.etat, { code: '654321' }, 'fr')
  expect(r2.session).toEqual(SESSION)
  expect(serveur.appels[1].corps).toMatchObject({ code: '654321', defi: 'd1' })
  expect(chemins()).not.toContain('/v1/auth/otp')
})

test('identifiants invalides : un seul message générique', async () => {
  serveur.reponses['/v1/auth/mot-de-passe'] = () => probleme(401, 'identifiants-invalides')
  const r = await soumettre(etatInitial('motDePasse'), { telephone: TEL, motDePasse: 'faux' }, 'fr')
  expect(r.session).toBeUndefined()
  expect(r.etat).toMatchObject({ etape: { nom: 'identifiant' }, erreur: 'identifiantsInvalides' })
})

test('429 : message d’attente, avec la durée de Retry-After quand elle est donnée', async () => {
  const t = (cle: Cle, vars?: Record<string, string | number>) => traduire('fr', cle, vars)
  serveur.reponses['/v1/auth/mot-de-passe'] = () => {
    const r = probleme(429, 'trop-de-demandes')
    r.headers.set('Retry-After', '90')
    return r
  }
  serveur.reponses['/v1/auth/otp'] = () => probleme(429, 'trop-de-demandes')
  const avecDelai = (await soumettre(etatInitial('motDePasse'), { telephone: TEL, motDePasse: MDP }, 'fr')).etat
  expect(avecDelai).toMatchObject({ erreur: 'tropDeDemandes', attente: 90 })
  expect(texteAvis(t, avecDelai.erreur!, avecDelai.attente)).toBe('Trop d’essais. Réessayez dans 2 min.')
  expect(texteAvis(t, 'tropDeDemandes', 20)).toBe('Trop d’essais. Réessayez dans 20 s.')
  const sansDelai = (await soumettre(etatInitial('code'), { telephone: TEL }, 'fr')).etat
  expect(sansDelai).toMatchObject({ erreur: 'tropDeDemandes', attente: undefined })
  expect(texteAvis(t, sansDelai.erreur!, sansDelai.attente)).toBe('Trop d’essais. Patientez quelques minutes avant de recommencer.')
})

test('connexion-par-code : bascule sur le code, envoyé aussitôt', async () => {
  serveur.reponses['/v1/auth/mot-de-passe'] = () => probleme(403, 'connexion-par-code')
  const r = await soumettre(etatInitial('motDePasse'), { telephone: TEL, motDePasse: MDP }, 'fr')
  expect(r.etat).toMatchObject({ etape: { nom: 'code' }, methode: 'code', info: 'connexionParCode' })
  expect(chemins()).toEqual(['/v1/auth/mot-de-passe', '/v1/auth/otp'])
  expect(methodeRetenue()).toBe('code')
})

test('mot-de-passe-requis : bascule sur le mot de passe ; « oublié » reprend le même code sans nouveau SMS', async () => {
  serveur.reponses['/v1/auth/otp/verify'] = () => probleme(403, 'mot-de-passe-requis')
  serveur.reponses['/v1/auth/mot-de-passe/definition'] = () => Response.json(SESSION)
  const r1 = await soumettre(etatInitial('code'), { telephone: TEL }, 'fr')
  const r2 = await soumettre(r1.etat, { code: '111111' }, 'fr')
  expect(r2.etat).toMatchObject({ etape: { nom: 'identifiant' }, methode: 'motDePasse', info: 'motDePasseRequis' })
  expect(methodeRetenue()).toBe('motDePasse')

  const r3 = await oublie(r2.etat, TEL, 'fr')
  expect(r3.etat.etape).toEqual({ nom: 'definition', raison: 'oublie', code: '111111' })
  const r4 = await soumettre(r3.etat, { nouveau: MDP, confirmation: MDP }, 'fr')
  expect(r4.session).toEqual(SESSION)
  expect(chemins().filter((c) => c === '/v1/auth/otp')).toHaveLength(1)
  expect(serveur.appels.at(-1)!.corps).toMatchObject({ telephone: TEL, code: '111111', motDePasse: MDP })
})

test('mot-de-passe-a-definir : création avec le même code, contrôles locaux puis refus du serveur', async () => {
  serveur.reponses['/v1/auth/otp/verify'] = () => probleme(403, 'mot-de-passe-a-definir')
  serveur.reponses['/v1/auth/mot-de-passe/definition'] = (c) => (c.motDePasse === 'motdepasse123456' ? probleme(422, 'mot-de-passe-courant') : Response.json(SESSION))
  const r1 = await soumettre(etatInitial('code'), { telephone: TEL }, 'fr')
  const r2 = await soumettre(r1.etat, { code: '222222' }, 'fr')
  expect(r2.etat).toMatchObject({ etape: { nom: 'definition', raison: 'aDefinir', code: '222222' }, info: 'aDefinir' })
  expect(methodeRetenue()).toBe('motDePasse')

  const appelsAvant = serveur.appels.length
  // 15 points de code : « é » composé (e + accent) compte pour un après NFC.
  expect((await soumettre(r2.etat, { nouveau: 'é'.repeat(14), confirmation: 'é'.repeat(14) }, 'fr')).etat.erreur).toBe('tropCourt')
  expect((await soumettre(r2.etat, { nouveau: MDP, confirmation: MDP + 'x' }, 'fr')).etat.erreur).toBe('confirmationDifferente')
  expect(serveur.appels.length).toBe(appelsAvant)

  expect((await soumettre(r2.etat, { nouveau: 'motdepasse123456', confirmation: 'motdepasse123456' }, 'fr')).etat.erreur).toBe('motDePasseCourant')
  const ok = await soumettre(r2.etat, { nouveau: MDP, confirmation: MDP }, 'fr')
  expect(ok.session).toEqual(SESSION)
  expect(serveur.appels.at(-1)!.corps).toMatchObject({ code: '222222', motDePasse: MDP })
})

test('mot de passe oublié : code SMS, puis code et nouveau mot de passe ; code expiré signalé', async () => {
  serveur.reponses['/v1/auth/mot-de-passe/definition'] = (c) => (c.code === '000000' ? probleme(401, 'code-invalide') : Response.json(SESSION))
  const r1 = await oublie(etatInitial('motDePasse'), TEL, 'fr')
  expect(r1.etat.etape).toEqual({ nom: 'definition', raison: 'oublie' })
  expect(chemins()).toEqual(['/v1/auth/otp'])
  const faux = await soumettre(r1.etat, { code: '000000', nouveau: MDP, confirmation: MDP }, 'fr')
  expect(faux.etat).toMatchObject({ etape: { nom: 'definition' }, erreur: 'codeInvalide' })
  const r2 = await soumettre(faux.etat, { code: '333333', nouveau: MDP, confirmation: MDP }, 'fr')
  expect(r2.session).toEqual(SESSION)
})

test('« Mon mot de passe » : contrôle local, actuel faux, absent, succès', async () => {
  let reponse = () => probleme(403, 'identifiants-invalides')
  serveur.reponses['/v1/moi/mot-de-passe'] = () => reponse()
  expect(await changerMotDePasse('x', 'court', 'court')).toEqual({ erreur: 'tropCourt' })
  expect(serveur.appels).toHaveLength(0)

  render(
    <I18nProvider>
      <MotDePasse />
    </I18nProvider>,
  )
  const actuel = screen.getByLabelText('Mot de passe actuel')
  const nouveau = screen.getByLabelText('Nouveau mot de passe')
  expect(actuel).toHaveAttribute('autocomplete', 'current-password')
  expect(nouveau).toHaveAttribute('autocomplete', 'new-password')
  expect(nouveau).toHaveAttribute('type', 'password')
  fireEvent.click(screen.getAllByRole('button', { name: 'Afficher le mot de passe' })[1])
  expect(nouveau).toHaveAttribute('type', 'text')

  fireEvent.change(actuel, { target: { value: 'ancien mot de passe' } })
  fireEvent.change(nouveau, { target: { value: MDP } })
  fireEvent.change(screen.getByLabelText('Confirmez le nouveau mot de passe'), { target: { value: MDP } })
  fireEvent.click(screen.getByRole('button', { name: 'Changer le mot de passe' }))
  expect(await screen.findByText('Le mot de passe actuel est incorrect.')).toBeInTheDocument()
  expect(serveur.appels.at(-1)!.corps).toEqual({ motDePasseActuel: 'ancien mot de passe', nouveauMotDePasse: MDP })

  reponse = () => probleme(409, 'mot-de-passe-absent')
  fireEvent.click(screen.getByRole('button', { name: 'Changer le mot de passe' }))
  expect(await screen.findByText(/vous le créerez à votre prochaine connexion/)).toBeInTheDocument()

  reponse = () => new Response(null, { status: 204 })
  fireEvent.click(screen.getByRole('button', { name: 'Changer le mot de passe' }))
  expect(await screen.findByText(/Mot de passe changé/)).toBeInTheDocument()
  expect(actuel).toHaveValue('')
})

test('écran de connexion : choix du mot de passe retenu, erreur générique, « oublié » mène au nouveau mot de passe', async () => {
  const { Connexion } = await import('@/pages/Connexion')
  serveur.reponses['/v1/auth/mot-de-passe'] = () => probleme(401, 'identifiants-invalides')
  render(
    <I18nProvider>
      <Connexion onSession={() => {}} />
    </I18nProvider>,
  )
  fireEvent.change(screen.getByLabelText('Numéro de téléphone'), { target: { value: '06 00 00 00 03' } })
  fireEvent.click(screen.getByRole('radio', { name: 'Mot de passe' }))
  expect(methodeRetenue()).toBe('motDePasse')
  const champ = screen.getByLabelText('Mot de passe', { selector: 'input[type=password]' })
  expect(champ).toHaveAttribute('autocomplete', 'current-password')
  fireEvent.change(champ, { target: { value: 'mauvais' } })
  fireEvent.click(screen.getByRole('button', { name: 'Se connecter' }))
  expect(await screen.findByText('Numéro ou mot de passe incorrect.')).toBeInTheDocument()

  fireEvent.click(screen.getByRole('button', { name: 'Mot de passe oublié ?' }))
  expect(await screen.findByText('Choisissez un nouveau mot de passe')).toBeInTheDocument()
  expect(screen.getByLabelText('Code reçu par SMS')).toHaveAttribute('autocomplete', 'one-time-code')
  expect(screen.getByLabelText('Nouveau mot de passe')).toHaveAttribute('autocomplete', 'new-password')
  expect(serveur.appels.at(-1)).toEqual({ chemin: '/v1/auth/otp', corps: { telephone: TEL, langue: 'fr' } })
})

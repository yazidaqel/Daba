// Connexion au portail (contrat v0.9.0, « Connexion aux portails web ») : le portail ne connaît pas la méthode
// d'un numéro (OTP, MOT_DE_PASSE, MOT_DE_PASSE_ET_OTP) ; il propose « mot de passe » ou « code SMS » et suit
// les réponses du serveur. Décisions sur `Problem.type` seulement, jamais sur `detail`.
// Les mots de passe ne passent que dans le corps des requêtes : ni état React persistant, ni stockage, ni journal.
// Même machine que web/grossiste/src/connexion.ts (code volontairement séparé, comportement identique).
import { api, appareilId, codeProbleme, enteteMock, type Problem, type S } from '@/api/client'
import type { Cle, Langue } from '@/i18n'

export type Methode = 'motDePasse' | 'code'

export type Etape =
  | { nom: 'identifiant' }
  // Code SMS : connexion OTP, ou second facteur après le mot de passe (`defi`).
  | { nom: 'code'; defi?: string }
  // Nouveau mot de passe : `code` déjà vérifié (403 mot-de-passe-a-definir, ou code gardé), sinon à saisir.
  | { nom: 'definition'; raison: 'aDefinir' | 'oublie'; code?: string }

// Messages de l'écran (clés `connexion.<avis>` de messages.ts).
export type Avis =
  | 'telephoneInvalide' | 'identifiantsInvalides' | 'codeInvalide' | 'tropDeDemandes' | 'desactive'
  | 'appareilRevoque' | 'nonApplicable' | 'tropCourt' | 'tropLong' | 'motDePasseTelephone' | 'motDePasseCourant'
  | 'confirmationDifferente' | 'actuelFaux' | 'absent' | 'erreur'
  // Informations (bascule décidée par le serveur)
  | 'motDePasseRequis' | 'connexionParCode' | 'aDefinir'

export type Etat = {
  etape: Etape
  methode: Methode
  telephone: string
  expireDans?: number
  erreur?: Avis
  // 429 : attente annoncée par Retry-After, en secondes (absente : message d'attente générique).
  attente?: number
  info?: Avis
  // Code laissé valable par un 403 mot-de-passe-requis : « mot de passe oublié » s'en sert sans nouveau SMS.
  codeGarde?: string
}

export type Saisie = { telephone?: string; motDePasse?: string; code?: string; nouveau?: string; confirmation?: string }
export type Resultat = { etat: Etat; session?: S['Session'] }
export type Refus = { erreur: Avis; attente?: number }

export const MIN_MOT_DE_PASSE = 15
export const MAX_MOT_DE_PASSE = 128

// Dernier choix de l'utilisateur sur ce navigateur (jamais le numéro).
const METHODE = 'daba.gc.connexion.methode'
export function methodeRetenue(): Methode {
  try {
    return localStorage.getItem(METHODE) === 'motDePasse' ? 'motDePasse' : 'code'
  } catch {
    return 'code'
  }
}
export function retenirMethode(m: Methode) {
  try {
    localStorage.setItem(METHODE, m)
  } catch {
    // stockage indisponible : le choix n'est simplement pas retenu
  }
}

export const etatInitial = (methode = methodeRetenue()): Etat => ({ etape: { nom: 'identifiant' }, methode, telephone: '' })

export const choisirMethode = (etat: Etat, methode: Methode): Etat => (retenirMethode(methode), { ...etat, methode, erreur: undefined, info: undefined })

// Un point de code = un caractère, après normalisation NFC (comme le serveur).
export const longueur = (s: string) => [...s.normalize('NFC')].length

export function verifierNouveau(nouveau = '', confirmation = ''): Avis | undefined {
  if (longueur(nouveau) < MIN_MOT_DE_PASSE) return 'tropCourt'
  if (longueur(nouveau) > MAX_MOT_DE_PASSE) return 'tropLong'
  if (nouveau !== confirmation) return 'confirmationDifferente'
}

export function avis(p: unknown, invalide: Avis = 'erreur'): Avis {
  switch (codeProbleme(p as Problem | undefined)) {
    case 'requete-invalide': return invalide
    case 'identifiants-invalides': return 'identifiantsInvalides'
    case 'code-invalide': return 'codeInvalide'
    case 'trop-de-demandes': return 'tropDeDemandes'
    case 'utilisateur-desactive': return 'desactive'
    case 'appareil-revoque': return 'appareilRevoque'
    case 'mot-de-passe-non-applicable': return 'nonApplicable'
    case 'mot-de-passe-trop-court': return 'tropCourt'
    case 'mot-de-passe-trop-long': return 'tropLong'
    case 'mot-de-passe-telephone': return 'motDePasseTelephone'
    case 'mot-de-passe-courant': return 'motDePasseCourant'
    case 'mot-de-passe-absent': return 'absent'
    default: return 'erreur'
  }
}

const propre = (etat: Etat): Etat => ({ ...etat, erreur: undefined, attente: undefined, info: undefined })

// Retry-After : en secondes, ou date HTTP.
export function retryAfter(r: Response): number | undefined {
  const v = r.headers.get('Retry-After')?.trim()
  if (!v) return undefined
  const s = /^\d+$/.test(v) ? Number(v) : Math.ceil((Date.parse(v) - Date.now()) / 1000)
  return s > 0 ? s : undefined
}

function refus(error: unknown, response: Response, invalide: Avis): Refus {
  const erreur = avis(error, invalide)
  return { erreur, attente: erreur === 'tropDeDemandes' ? retryAfter(response) : undefined }
}

type Traduire = (cle: Cle, vars?: Record<string, string | number>) => string
export function texteAvis(t: Traduire, a: Avis, attente?: number): string {
  if (a === 'tropDeDemandes' && attente)
    return attente < 60 ? t('connexion.attenteSecondes', { secondes: attente }) : t('connexion.attenteMinutes', { minutes: Math.ceil(attente / 60) })
  return t(`connexion.${a}`)
}

async function envoyerCode(etat: Etat, suivante: Etape, langue: Langue, info?: Avis): Promise<Resultat> {
  const { data, error, response } = await api.POST('/v1/auth/otp', { body: { telephone: etat.telephone, langue } })
  if (!data) return { etat: { ...etat, ...refus(error, response, 'telephoneInvalide') } }
  return { etat: { ...etat, etape: suivante, expireDans: data.expireDans, info } }
}

export async function soumettre(precedent: Etat, s: Saisie, langue: Langue): Promise<Resultat> {
  const etat = propre(precedent)
  const { etape } = etat

  if (etape.nom === 'identifiant') {
    const telephone = s.telephone!
    // Changer de numéro rend caduc le code gardé.
    const avecTel = { ...etat, telephone, codeGarde: telephone === etat.telephone ? etat.codeGarde : undefined }
    if (etat.methode === 'code') return envoyerCode({ ...avecTel, codeGarde: undefined }, { nom: 'code' }, langue)
    const { data, error, response } = await api.POST('/v1/auth/mot-de-passe', {
      body: { telephone, motDePasse: s.motDePasse ?? '', appareilId: appareilId(), langue },
    })
    if (data?.session) return { etat: avecTel, session: data.session }
    if (data?.defi) return { etat: { ...avecTel, etape: { nom: 'code', defi: data.defi }, expireDans: data.expireDans } }
    // Mot de passe juste, mais le compte se connecte par code : on envoie le code.
    if (codeProbleme(error) === 'connexion-par-code')
      return envoyerCode({ ...avecTel, methode: (retenirMethode('code'), 'code') }, { nom: 'code' }, langue, 'connexionParCode')
    return { etat: { ...avecTel, ...refus(error, response, 'identifiantsInvalides') } }
  }

  if (etape.nom === 'code') {
    const code = s.code ?? ''
    const { data, error, response } = await api.POST('/v1/auth/otp/verify', {
      body: { telephone: etat.telephone, code, appareilId: appareilId(), ...(etape.defi && { defi: etape.defi }) },
      headers: enteteMock,
    })
    if (data) return { etat, session: data }
    switch (codeProbleme(error)) {
      // Code juste et non consommé dans ces deux cas.
      case 'mot-de-passe-a-definir':
        retenirMethode('motDePasse')
        return { etat: { ...etat, etape: { nom: 'definition', raison: 'aDefinir', code }, methode: 'motDePasse', info: 'aDefinir' } }
      case 'mot-de-passe-requis':
        retenirMethode('motDePasse')
        return { etat: { ...etat, etape: { nom: 'identifiant' }, methode: 'motDePasse', codeGarde: code, info: 'motDePasseRequis' } }
    }
    return { etat: { ...etat, ...refus(error, response, 'codeInvalide') } }
  }

  const local = verifierNouveau(s.nouveau, s.confirmation)
  if (local) return { etat: { ...etat, erreur: local } }
  const { data, error, response } = await api.POST('/v1/auth/mot-de-passe/definition', {
    body: { telephone: etat.telephone, code: etape.code ?? s.code ?? '', motDePasse: s.nouveau!, appareilId: appareilId() },
  })
  if (data) return { etat: { ...etat, codeGarde: undefined }, session: data }
  // Code reçu plus tôt et expiré entre-temps : retour au début.
  if (codeProbleme(error) === 'code-invalide' && etape.code)
    return { etat: { ...etat, etape: { nom: 'identifiant' }, codeGarde: undefined, erreur: 'codeInvalide' } }
  return { etat: { ...etat, ...refus(error, response, 'codeInvalide') } }
}

// « Mot de passe oublié » : code SMS (ou code gardé), puis nouveau mot de passe.
export async function oublie(precedent: Etat, telephone: string, langue: Langue): Promise<Resultat> {
  const etat = { ...propre(precedent), telephone }
  if (etat.codeGarde && telephone === precedent.telephone)
    return { etat: { ...etat, etape: { nom: 'definition', raison: 'oublie', code: etat.codeGarde } } }
  return envoyerCode({ ...etat, codeGarde: undefined }, { nom: 'definition', raison: 'oublie' }, langue)
}

// « Mon mot de passe » : renvoie le refus à afficher, ou null si le mot de passe a changé.
export async function changerMotDePasse(actuel: string, nouveau: string, confirmation: string): Promise<Refus | null> {
  const local = verifierNouveau(nouveau, confirmation)
  if (local) return { erreur: local }
  const { error, response } = await api.PUT('/v1/moi/mot-de-passe', { body: { motDePasseActuel: actuel, nouveauMotDePasse: nouveau } })
  if (response.ok) return null
  const r = refus(error, response, 'erreur')
  return r.erreur === 'identifiantsInvalides' ? { erreur: 'actuelFaux' } : r
}

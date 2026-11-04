import type { Cle } from '@/i18n/fr'
import { codeProbleme, type Problem } from './client'

// Message à afficher pour une erreur d'API, décidé sur le `type` (liste fixe du contrat) : code connu,
// sinon le `detail` du serveur (déjà dans la langue demandée), sinon un texte selon le statut.
const CODES: Record<string, Cle> = {
  'appareil-revoque': 'erreur.appareilRevoque',
  'utilisateur-desactive': 'erreur.utilisateurDesactive',
  'role-non-autorise': 'erreur.roleNonAutorise',
  'fournisseur-non-configure': 'erreur.fournisseurNonConfigure',
  'journal-interdit': 'erreur.journalInterdit',
  introuvable: 'erreur.introuvable',
  'trop-de-demandes': 'login.tropDEssais',
  'code-invalide': 'login.codeInvalide',
  'identifiants-invalides': 'login.identifiantsInvalides',
  'mot-de-passe-absent': 'mdp.absent',
  'mot-de-passe-non-applicable': 'mdp.nonApplicable',
  'mot-de-passe-trop-court': 'mdp.tropCourt',
  'mot-de-passe-trop-long': 'mdp.tropLong',
  'mot-de-passe-telephone': 'mdp.telephone',
  'mot-de-passe-courant': 'mdp.courant',
}

export function messageErreur(e: unknown, t: (c: Cle, vars?: Record<string, string | number>) => string): string {
  const p = (e ?? {}) as Problem
  if (codeProbleme(p) === 'trop-de-demandes' && p.attente) {
    const duree = p.attente < 60 ? t('login.secondes', { n: p.attente }) : t('login.minutes', { n: Math.ceil(p.attente / 60) })
    return t('login.tropDEssaisAttente', { duree })
  }
  const cle = CODES[codeProbleme(p) ?? '']
  if (cle) return t(cle)
  if (p.detail) return p.detail
  if (p.status === 409) return t('erreur.409')
  return t('app.erreur')
}

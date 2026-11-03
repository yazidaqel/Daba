import type { Cle } from '@/i18n/fr'
import { codeProbleme, type Problem } from './client'

// Message à afficher pour une erreur d'API, décidé sur le `type` (liste fixe du contrat) : code connu,
// sinon le `detail` du serveur (déjà dans la langue demandée), sinon un texte selon le statut.
export function messageErreur(e: unknown, t: (c: Cle) => string): string {
  const p = (e ?? {}) as Problem
  switch (codeProbleme(p)) {
    case 'appareil-revoque': return t('erreur.appareilRevoque')
    case 'utilisateur-desactive': return t('erreur.utilisateurDesactive')
    case 'role-non-autorise': return t('erreur.roleNonAutorise')
    case 'fournisseur-non-configure': return t('erreur.fournisseurNonConfigure')
    case 'journal-interdit': return t('erreur.journalInterdit')
    case 'introuvable': return t('erreur.introuvable')
    case 'trop-de-demandes': return t('login.tropDEssais')
  }
  if (p.detail) return p.detail
  if (p.status === 409) return t('erreur.409')
  return t('app.erreur')
}

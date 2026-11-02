import type { Cle } from '@/i18n/fr'
import { codeProbleme, type Problem } from './client'

const REGLES = ['RG-P1', 'RG-P3', 'RG-P4'] as const

// Message à afficher pour une erreur d'API, décidé sur le `type` (liste fixe du contrat) : règle de
// gestion connue, sinon code, sinon le `detail` du serveur (déjà dans la langue demandée), sinon générique.
export function messageErreur(e: unknown, t: (c: Cle) => string): string {
  const p = (e ?? {}) as Problem
  switch (codeProbleme(p)) {
    case 'consentement-requis': return t('erreur.consentement')
    case 'appareil-revoque': return t('erreur.appareilRevoque')
  }
  const regle = REGLES.find((r) => r === p.regle)
  if (regle) return t(`erreur.${regle}`)
  if (p.status === 409 || p.status === 413) return t(`erreur.${p.status}`)
  if (p.detail) return p.detail
  if (p.status === 422) return t('erreur.422')
  return t('app.erreur')
}

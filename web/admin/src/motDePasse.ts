import type { Cle } from '@/i18n/fr'

// Politique du contrat (MotDePasse) : longueur en points de code après NFC, sans règle de composition.
// ponytail: minimum 15 en dur (le contrat passe de 12 à 15) ; le serveur reste juge (422 mot-de-passe-*).
export const MOT_DE_PASSE_MIN = 15
export const MOT_DE_PASSE_MAX = 128

// Contrôle avant envoi d'un nouveau mot de passe ; null si rien à redire.
export function controlerMotDePasse(nouveau: string, confirmation: string): Cle | null {
  const n = [...nouveau.normalize('NFC')].length
  if (n < MOT_DE_PASSE_MIN) return 'mdp.tropCourt'
  if (n > MOT_DE_PASSE_MAX) return 'mdp.tropLong'
  if (nouveau !== confirmation) return 'mdp.differents'
  return null
}

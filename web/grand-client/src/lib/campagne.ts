import type { S } from '@/api/client'
import type { Cle } from '@/i18n'

export type TypeRemise = S['Remise']['type']
export const TYPES_REMISE: TypeRemise[] = ['POURCENTAGE', 'PRIX_NET', 'PRODUIT_OFFERT', 'PALIER']

export function remiseParDefaut(type: TypeRemise, produitId: string): S['Remise'] {
  switch (type) {
    case 'POURCENTAGE':
      return { type, pourcentage: 10 }
    case 'PRIX_NET':
      return { type, prixNet: 0 }
    case 'PRODUIT_OFFERT':
      return { type, quantiteAchetee: 10, produitOffertId: produitId, quantiteOfferte: 1 }
    case 'PALIER':
      return { type, paliers: [{ quantiteMin: 10, pourcentage: 5 }] }
  }
}

/**
 * Nouveau choix de régions : une offre n'est visible que si la ville ET la région du dépositaire sont
 * ciblées, donc les villes cochées hors des régions choisies sont retirées.
 */
export function ciblerRegions(ciblage: S['Ciblage'], regions: S['Region'][], codes: string[]): S['Ciblage'] {
  if (!codes.length) return { ...ciblage, regions: codes }
  const permises = new Set(regions.filter((r) => codes.includes(r.code)).flatMap((r) => r.villes.map((v) => v.code)))
  return { ...ciblage, regions: codes, villes: ciblage.villes?.filter((v) => permises.has(v)) }
}

const pct = (n: number) => n > 0 && n <= 100

/** Contrôles avant envoi (le serveur revalide, 422) ; renvoie les clés de message des erreurs. */
export function validerCampagne(c: S['CampagneSaisie']): Cle[] {
  const e: Cle[] = []
  if (!c.nom.fr.trim()) e.push('valid.nom')
  if (!c.debut || !c.fin || !(Date.parse(c.fin) > Date.parse(c.debut))) e.push('valid.periode')
  if (c.lignes.length === 0) e.push('valid.lignes')
  for (const l of c.lignes) {
    if (!(l.quantiteTotale >= 1 && l.quantiteMaxParAmbulant >= 1)) e.push('valid.quantites')
    else if (l.quantiteMaxParAmbulant > l.quantiteTotale) e.push('valid.plafond')
    const r = l.remise
    if (
      (r.type === 'POURCENTAGE' && !pct(r.pourcentage)) ||
      (r.type === 'PRIX_NET' && !(r.prixNet > 0)) ||
      (r.type === 'PRODUIT_OFFERT' && !(r.quantiteAchetee >= 1 && r.quantiteOfferte >= 1)) ||
      (r.type === 'PALIER' && (r.paliers.length === 0 || r.paliers.some((p) => !(p.quantiteMin >= 1 && pct(p.pourcentage)))))
    )
      e.push('valid.remise')
  }
  return [...new Set(e)]
}

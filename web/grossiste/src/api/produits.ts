import { useI18n, type Langue } from '@/i18n'
import { $api, type S } from './client'

export const libelle = (l: S['LibelleI18n'], langue: Langue) => l[langue] ?? l.fr

// Libellés des produits cités par un écran (lignes de dépôt, réservations, bons de sortie) :
// GET /v1/produits?ids=… (200 au plus par appel).
// ponytail: au-delà de 200 produits distincts sur un écran, les suivants s'affichent par leur id ; découper en lots si ça arrive.
export function useNomProduit(ids: string[]) {
  const { langue, t } = useI18n()
  const uniques = [...new Set(ids)].sort().slice(0, 200)
  const { data } = $api.useQuery(
    'get',
    '/v1/produits',
    { params: { query: { ids: uniques, taille: uniques.length } }, querySerializer: { array: { style: 'form', explode: false } } },
    { enabled: uniques.length > 0, staleTime: 600_000 },
  )
  const parId = new Map(data?.contenu.map((p) => [p.id, p]))
  return (id: string) => {
    const p = parId.get(id)
    return p ? `${libelle(p.libelle, langue)} · ${p.marque} (${t(`cond.${p.conditionnement}`)})` : `#${id.slice(0, 8)}`
  }
}

import { useI18n, type Langue } from '@/i18n'
import { $api, type S } from './client'

export const libelle = (l: S['LibelleI18n'], langue: Langue) => l[langue] ?? l.fr

// Libellé d'un produit à partir de son id. Les lignes de dépôt et les réservations ne portent que
// `produitId` et le contrat n'a pas de GET /v1/produits/{id} : on lit une page du référentiel.
// ponytail: une seule page de 200 produits ; à remplacer dès que le contrat embarque le produit dans LigneDepot/Reservation.
export function useNomProduit() {
  const { langue, t } = useI18n()
  const { data } = $api.useQuery('get', '/v1/produits', { params: { query: { taille: 200 } } }, { staleTime: 600_000 })
  const parId = new Map(data?.contenu.map((p) => [p.id, p]))
  return (id: string) => {
    const p = parId.get(id)
    return p ? `${libelle(p.libelle, langue)} · ${p.marque} (${t(`cond.${p.conditionnement}`)})` : `#${id.slice(0, 8)}`
  }
}

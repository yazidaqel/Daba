import { api, ok, type S } from '@/api/client'
import { useCharge } from '@/lib/useCharge'

/**
 * Référentiel produits, indexé par id, pour afficher les libellés des lignes de campagne.
 * ponytail: première page de 200 seulement — le contrat n'a ni GET /v1/produits/{id} ni filtre
 * « produits de ma marque » ; à remplacer dès que le back-end en expose un.
 */
export function useProduits() {
  const { data } = useCharge(() => api.GET('/v1/produits', { params: { query: { taille: 200 } } }).then(ok), [])
  return new Map<string, S['Produit']>((data?.contenu ?? []).map((p) => [p.id, p]))
}

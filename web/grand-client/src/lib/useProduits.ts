import { api, ok, type S } from '@/api/client'
import { useCharge } from '@/lib/useCharge'

const parId = (liste: S['Produit'][] = []) => new Map(liste.map((p) => [p.id, p]))

/** Produits du grand client connecté (création de campagne, filtre de la carte des ruptures). */
export function useMesProduits() {
  const { data } = useCharge(() => api.GET('/v1/produits', { params: { query: { mesProduits: true, taille: 200 } } }).then(ok), [])
  return parId(data?.contenu)
}

/** Libellés de produits précis (lignes de campagne et de dépôt), indexés par id. */
export function useProduits(ids: string[]) {
  // ponytail: une seule page de 200 ids (maxItems du contrat) ; découper si une campagne en compte plus.
  const uniques = [...new Set(ids)].sort().slice(0, 200)
  const { data } = useCharge(
    () =>
      uniques.length
        ? api
            .GET('/v1/produits', {
              params: { query: { ids: uniques, taille: 200 } },
              querySerializer: { array: { style: 'form', explode: false } },
            })
            .then(ok)
        : Promise.resolve(undefined),
    [uniques.join()],
  )
  return parId(data?.contenu)
}

export function useCategories() {
  const { data } = useCharge(() => api.GET('/v1/categories').then(ok), [])
  return new Map((data ?? []).map((c) => [c.code, c]))
}

export function useRegions() {
  return useCharge(() => api.GET('/v1/regions').then(ok), []).data ?? []
}

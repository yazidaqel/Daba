import { useI18n } from '@/i18n'
import { useRegions } from '@/lib/useProduits'

/**
 * Suggestions natives (`<datalist>`) pour les filtres `ville` des grossistes et des ruptures.
 * Ces filtres comparent au nom enregistré (ILIKE), pas au code : la valeur est le nom français.
 */
export function ListeVilles({ id }: { id: string }) {
  const { libelle } = useI18n()
  const villes = useRegions().flatMap((r) => r.villes)
  return (
    <datalist id={id}>
      {villes.map((v) => (
        <option key={v.code} value={v.nom.fr}>
          {libelle(v.nom)}
        </option>
      ))}
    </datalist>
  )
}

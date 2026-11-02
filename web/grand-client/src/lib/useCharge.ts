import { useCallback, useEffect, useState } from 'react'

/** Charge une ressource ; `recharger()` relance l'appel. */
export function useCharge<T>(charger: () => Promise<T>, deps: unknown[]) {
  const [etat, setEtat] = useState<{ data?: T; erreur?: unknown; cle?: string }>({})
  const [tour, setTour] = useState(0)
  const cle = JSON.stringify([...deps, tour])

  useEffect(() => {
    let actif = true
    charger().then(
      (data) => actif && setEtat({ data, cle }),
      (erreur) => actif && setEtat({ erreur, cle }),
    )
    return () => {
      actif = false
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [cle])

  const recharger = useCallback(() => setTour((n) => n + 1), [])
  const aJour = etat.cle === cle
  return { data: etat.data, erreur: aJour ? etat.erreur : undefined, chargement: !aJour, recharger }
}

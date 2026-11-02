import { useCallback, useEffect, useState } from 'react'

/** Saisie libre (06 12…, 212…, +212…) → E.164 marocain, ou null. */
export function telephoneE164(saisie: string) {
  const n = saisie.replace(/[\s.-]/g, '').replace(/^(\+212|00212|212|0)/, '')
  return /^[5-7]\d{8}$/.test(n) ? `+212${n}` : null
}

/** Photo réduite à 1024 px en JPEG : quelques dizaines de Ko sur une 3G faible. */
export async function compresserPhoto(fichier: Blob, max = 1024): Promise<Blob> {
  const img = await createImageBitmap(fichier)
  const k = Math.min(1, max / Math.max(img.width, img.height))
  const canvas = document.createElement('canvas')
  canvas.width = Math.round(img.width * k)
  canvas.height = Math.round(img.height * k)
  canvas.getContext('2d')?.drawImage(img, 0, 0, canvas.width, canvas.height)
  return new Promise((ok, ko) => canvas.toBlob((b) => (b ? ok(b) : ko(new Error('photo'))), 'image/jpeg', 0.7))
}

/** Charge une ressource ; garde la dernière donnée pendant un rechargement (sondage). */
export function useApi<T>(charger: () => Promise<{ data?: T }>, cles: unknown[]) {
  const [etat, setEtat] = useState<{ data?: T; erreur: boolean; chargement: boolean }>({
    erreur: false,
    chargement: true,
  })
  const [n, setN] = useState(0)
  useEffect(() => {
    let actif = true
    charger().then(
      (r) => actif && setEtat((e) => ({ data: r.data ?? e.data, erreur: !r.data, chargement: false })),
      () => actif && setEtat((e) => ({ ...e, erreur: true, chargement: false })),
    )
    return () => {
      actif = false
    }
    // `cles` liste les dépendances de `charger`.
    // oxlint-disable-next-line react-hooks/exhaustive-deps
  }, [...cles, n])
  const recharger = useCallback(() => setN((x) => x + 1), [])
  return { ...etat, recharger }
}

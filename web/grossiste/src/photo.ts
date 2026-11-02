import type { S } from '@/api/client'

// Réduit la photo du bon de livraison (faible consommation de données, §10 ; évite le 413).
// En cas d'échec (format non décodable par le navigateur), on envoie le fichier tel quel.
export async function compresserPhoto(fichier: File, cote = 1600): Promise<Blob> {
  try {
    const image = await createImageBitmap(fichier)
    const echelle = Math.min(1, cote / Math.max(image.width, image.height))
    const canvas = document.createElement('canvas')
    canvas.width = Math.round(image.width * echelle)
    canvas.height = Math.round(image.height * echelle)
    canvas.getContext('2d')!.drawImage(image, 0, 0, canvas.width, canvas.height)
    const blob = await new Promise<Blob | null>((ok) => canvas.toBlob(ok, 'image/jpeg', 0.8))
    return blob ?? fichier
  } catch {
    return fichier
  }
}

// Corps multipart de POST /v1/depots/{id}/reception : partie `reception` en JSON, partie photo en image.
export function formulaireReception(reception: S['ReceptionDepot'], photo: Blob) {
  const fd = new FormData()
  fd.append('reception', new Blob([JSON.stringify(reception)], { type: 'application/json' }))
  fd.append('photoBonLivraison', photo, 'bon-livraison.jpg')
  return fd
}

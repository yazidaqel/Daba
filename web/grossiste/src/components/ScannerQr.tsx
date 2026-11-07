import { useEffect, useRef, useState } from 'react'
import { CircleAlert } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { useI18n } from '@/i18n'

type Detecteur = { detect: (source: ImageBitmapSource) => Promise<{ rawValue: string }[]> }

// API native BarcodeDetector quand le navigateur l'a (Chrome Android), sinon la même API en WebAssembly,
// servie par l'application elle-même (pas de CDN).
async function creerDetecteur(): Promise<Detecteur> {
  const natif = (globalThis as { BarcodeDetector?: new (o: object) => Detecteur }).BarcodeDetector
  if (natif) return new natif({ formats: ['qr_code'] })
  const [{ BarcodeDetector, prepareZXingModule }, { default: wasm }] = await Promise.all([
    import('barcode-detector/ponyfill'),
    import('zxing-wasm/reader/zxing_reader.wasm?url'),
  ])
  prepareZXingModule({ overrides: { locateFile: (p: string, prefixe: string) => (p.endsWith('.wasm') ? wasm : prefixe + p) } })
  return new BarcodeDetector({ formats: ['qr_code'] })
}

// Repli sans caméra (webcam absente ou refusée) : le QR est lu dans une photo, toujours dans le navigateur.
// Jamais de code à taper : le QR reste la seule preuve du retrait (RG-P4).
async function lireQrPhoto(fichier: Blob): Promise<string | undefined> {
  const detecteur = await creerDetecteur()
  const image = await createImageBitmap(fichier)
  try {
    const [code] = await detecteur.detect(image)
    return code?.rawValue
  } finally {
    image.close()
  }
}

export function ScannerQr({ onScan }: { onScan: (valeur: string) => void }) {
  const { t } = useI18n()
  const video = useRef<HTMLVideoElement>(null)
  const [actif, setActif] = useState(false)
  const [indisponible, setIndisponible] = useState(false)
  const [photoIllisible, setPhotoIllisible] = useState(false)

  async function lirePhoto(champ: HTMLInputElement) {
    const fichier = champ.files?.[0]
    champ.value = '' // pour pouvoir reprendre la même photo
    if (!fichier) return
    const valeur = await lireQrPhoto(fichier).catch(() => undefined)
    setPhotoIllisible(!valeur)
    if (valeur) onScan(valeur)
  }

  useEffect(() => {
    if (!actif) return
    let flux: MediaStream | undefined
    let minuterie: number | undefined
    let fini = false
    ;(async () => {
      try {
        const detecteur = await creerDetecteur()
        flux = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' } })
        if (fini) return flux.getTracks().forEach((p) => p.stop())
        video.current!.srcObject = flux
        await video.current!.play()
        minuterie = window.setInterval(async () => {
          const [code] = await detecteur.detect(video.current!).catch(() => [])
          if (code && !fini) {
            fini = true
            setActif(false)
            onScan(code.rawValue)
          }
        }, 300)
      } catch {
        setIndisponible(true)
        setActif(false)
      }
    })()
    return () => {
      fini = true
      clearInterval(minuterie)
      flux?.getTracks().forEach((p) => p.stop())
    }
  }, [actif, onScan])

  return (
    <div className="space-y-2">
      <Button type="button" variant={actif ? 'outline' : 'default'} onClick={() => setActif(!actif)}>
        {actif ? t('retrait.arreterCamera') : t('retrait.camera')}
      </Button>
      {indisponible && (
        <p className="text-destructive flex items-center gap-1.5 text-sm">
          <CircleAlert aria-hidden className="size-4" />
          {t('retrait.cameraIndisponible')}
        </p>
      )}
      <video ref={video} muted playsInline className={actif ? 'w-full max-w-md rounded-lg border' : 'hidden'} />
      <div className="space-y-1">
        <Label htmlFor="qr-photo">{t('retrait.photo')}</Label>
        <p id="qr-photo-aide" className="text-muted-foreground text-sm">{t('retrait.photoAide')}</p>
        <Input
          id="qr-photo"
          type="file"
          accept="image/*"
          capture="environment"
          aria-describedby="qr-photo-aide"
          className="max-w-md"
          onChange={(e) => lirePhoto(e.currentTarget)}
        />
        {photoIllisible && (
          <p role="alert" className="text-destructive flex items-center gap-1.5 text-sm">
            <CircleAlert aria-hidden className="size-4" />
            {t('retrait.photoIllisible')}
          </p>
        )}
      </div>
    </div>
  )
}

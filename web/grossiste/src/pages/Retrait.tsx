import { useState, type FormEvent } from 'react'
import { $api, appareilId, queryClient } from '@/api/client'
import { useNomProduit } from '@/api/produits'
import { Erreur, Succes } from '@/components/communs'
import { ScannerQr } from '@/components/ScannerQr'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { useI18n } from '@/i18n'

// Retrait d'une réservation promo : uniquement par le QR de réservation (RG-P4). Le serveur recontrôle
// le plafond par ambulant (RG-P3) ; l'écran borne la quantité remise à la quantité réservée.
export default function Retrait() {
  const { t, date, nombre } = useI18n()
  const nom = useNomProduit()
  const [douchette, setDouchette] = useState('')
  const [qrJeton, setQrJeton] = useState<string | null>(null)
  const [quantite, setQuantite] = useState(0)
  const apercu = $api.useMutation('post', '/v1/retraits/apercu', {
    onSuccess: (r) => setQuantite(r.quantiteReservee),
  })
  const valider = $api.useMutation('post', '/v1/retraits', { onSuccess: () => queryClient.invalidateQueries() })
  const resa = apercu.data

  function lire(jeton: string) {
    const j = jeton.trim()
    if (!j) return
    setQrJeton(j)
    valider.reset()
    apercu.mutate({ body: { qrJeton: j } })
  }

  function recommencer() {
    setQrJeton(null)
    setDouchette('')
    apercu.reset()
    valider.reset()
  }

  function soumettre(e: FormEvent) {
    e.preventDefault()
    valider.mutate({ body: { qrJeton: qrJeton!, quantiteRemise: quantite, appareilId: appareilId() } })
  }

  return (
    <section className="max-w-2xl space-y-4">
      <h1 className="text-2xl font-semibold">{t('retrait.titre')}</h1>

      {!resa && (
        <Card>
          <CardHeader>
            <p className="text-muted-foreground text-sm">{t('retrait.aide')}</p>
          </CardHeader>
          <CardContent className="space-y-6">
            <ScannerQr onScan={lire} />
            <form onSubmit={(e) => (e.preventDefault(), lire(douchette))} className="space-y-2">
              <Label htmlFor="douchette">{t('retrait.douchette')}</Label>
              <p className="text-muted-foreground text-sm">{t('retrait.douchetteAide')}</p>
              <div className="flex gap-2">
                <Input id="douchette" dir="ltr" autoComplete="off" value={douchette} onChange={(e) => setDouchette(e.target.value)} />
                <Button type="submit" disabled={apercu.isPending}>{t('retrait.verifier')}</Button>
              </div>
            </form>
            {apercu.error && <Erreur erreur={apercu.error} />}
          </CardContent>
        </Card>
      )}

      {resa && !valider.isSuccess && (
        <Card>
          <CardHeader>
            <CardTitle>{nom(resa.produitId)}</CardTitle>
          </CardHeader>
          <CardContent>
            <form onSubmit={soumettre} className="space-y-4">
              <dl className="grid grid-cols-2 gap-2 text-sm">
                <dt className="text-muted-foreground">{t('retrait.ambulant')}</dt>
                <dd>{resa.ambulant?.nom ?? '—'}</dd>
                <dt className="text-muted-foreground">{t('retrait.reservee')}</dt>
                <dd className="font-semibold">{nombre(resa.quantiteReservee)}</dd>
                <dt className="text-muted-foreground">{t('reservations.expire')}</dt>
                <dd>{t('retrait.expire', { date: date(resa.expireLe) })}</dd>
              </dl>
              <div className="space-y-1">
                <Label htmlFor="quantite">{t('retrait.quantiteRemise')}</Label>
                <Input
                  id="quantite"
                  type="number"
                  min={1}
                  max={resa.quantiteReservee}
                  className="w-32"
                  value={quantite}
                  onChange={(e) => setQuantite(Math.floor(Number(e.target.value) || 0))}
                />
                <p className="text-muted-foreground text-sm">{t('retrait.quantiteAide', { max: nombre(resa.quantiteReservee) })}</p>
              </div>
              <div className="flex gap-2">
                <Button type="submit" disabled={valider.isPending || quantite < 1 || quantite > resa.quantiteReservee}>
                  {t('retrait.valider')}
                </Button>
                <Button type="button" variant="outline" onClick={recommencer}>{t('app.annuler')}</Button>
              </div>
              {valider.error && <Erreur erreur={valider.error} />}
            </form>
          </CardContent>
        </Card>
      )}

      {valider.isSuccess && (
        <div className="space-y-3">
          <Succes>{t('retrait.ok', { quantite: nombre(valider.data.quantiteRetiree ?? quantite) })}</Succes>
          <Button onClick={recommencer}>{t('retrait.suivant')}</Button>
        </div>
      )}
    </section>
  )
}

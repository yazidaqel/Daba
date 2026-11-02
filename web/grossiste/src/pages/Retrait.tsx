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

// Retrait d'une réservation promo : uniquement par le QR de réservation (RG-P4), un JWS que seul le
// serveur vérifie. L'écran borne la quantité remise à la réservée et affiche le plafond par ambulant ;
// le plafond cumulé (déjà retiré + remis) n'est contrôlé que par le serveur (422 RG-P3).
export default function Retrait() {
  const { t, date, nombre } = useI18n()
  const [douchette, setDouchette] = useState('')
  const [qrJeton, setQrJeton] = useState<string | null>(null)
  const [saisie, setSaisie] = useState<number | null>(null)
  const apercu = $api.useMutation('post', '/v1/retraits/apercu')
  const valider = $api.useMutation('post', '/v1/retraits', { onSuccess: () => queryClient.invalidateQueries() })
  const resa = apercu.data
  const nom = useNomProduit(resa ? [resa.produitId] : [])
  const depot = $api.useQuery('get', '/v1/depots/{depotId}', { params: { path: { depotId: resa?.depotId ?? '' } } }, { enabled: !!resa })
  const plafond = depot.data?.lignes.find((l) => l.produitId === resa?.produitId)?.quantiteMaxParAmbulant
  const max = Math.min(resa?.quantiteReservee ?? 0, plafond ?? Infinity)
  // Proposée par défaut : le maximum remettable, tant que le grossiste n'a rien saisi.
  const quantite = saisie ?? max

  function lire(jeton: string) {
    const j = jeton.trim()
    if (!j) return
    setQrJeton(j)
    setSaisie(null)
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
                <Button type="submit" variant="outline" disabled={apercu.isPending}>{t('retrait.verifier')}</Button>
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
                <dt className="text-muted-foreground">{t('retrait.plafond')}</dt>
                <dd>{plafond == null ? '—' : nombre(plafond)}</dd>
                <dt className="text-muted-foreground">{t('reservations.expire')}</dt>
                <dd>{t('retrait.expire', { date: date(resa.expireLe) })}</dd>
              </dl>
              <div className="space-y-1">
                <Label htmlFor="quantite">{t('retrait.quantiteRemise')}</Label>
                <Input
                  id="quantite"
                  type="number"
                  min={1}
                  max={max}
                  className="w-32"
                  value={quantite}
                  onChange={(e) => setSaisie(Math.floor(Number(e.target.value) || 0))}
                />
                <p className="text-muted-foreground text-sm">{t('retrait.quantiteAide', { max: nombre(max) })}</p>
              </div>
              <div className="flex gap-2">
                <Button type="submit" disabled={valider.isPending || depot.isLoading || quantite < 1 || quantite > max}>
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

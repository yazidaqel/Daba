import { useState } from 'react'
import { api, ok } from '@/api/client'
import { useI18n } from '@/i18n'
import { useCharge } from '@/lib/useCharge'
import { useMesProduits } from '@/lib/useProduits'
import { Carte } from '@/components/Carte'
import { ListeVilles } from '@/components/ListeVilles'
import { Erreur } from '@/components/Erreur'
import { Input } from '@/components/ui/input'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'

/** Carte des ruptures agrégée par cellule geohash (§5.3, §5.4) : aucune donnée par hanout ni ambulant. */
export function Ruptures() {
  const { t, libelle, nombre, date } = useI18n()
  const produits = useMesProduits()
  const [produitId, setProduitId] = useState('')
  const [ville, setVille] = useState('')
  const [precision, setPrecision] = useState<5 | 6>(6)
  const [depuis, setDepuis] = useState('')
  const [jusqua, setJusqua] = useState('')
  const carte = useCharge(
    () =>
      api
        .GET('/v1/analytique/ruptures', {
          params: {
            query: {
              produitId: produitId || undefined,
              ville: ville.trim() || undefined,
              precision,
              depuis: depuis ? new Date(depuis + 'T00:00').toISOString() : undefined,
              // borne exclue : lendemain du dernier jour choisi
              jusqua: jusqua ? new Date(new Date(jusqua + 'T00:00').getTime() + 864e5).toISOString() : undefined,
            },
          },
        })
        .then(ok),
    [produitId, ville, precision, depuis, jusqua],
  )
  const cellules = carte.data?.cellules ?? []
  const max = Math.max(1, ...cellules.map((c) => c.signalements))
  const nomProduit = (pid: string) => {
    const p = produits.get(pid)
    return p ? libelle(p.libelle) : pid.slice(0, 8)
  }

  const filtre = 'h-9 rounded-md border bg-background px-2 text-sm'
  return (
    <section className="space-y-4">
      <h1 className="text-xl font-semibold">{t('ruptures.titre')}</h1>
      <div className="flex flex-wrap items-end gap-2 text-sm">
        <label className="space-y-1">
          <span className="block">{t('ligne.produit')}</span>
          <select className={filtre} value={produitId} onChange={(e) => setProduitId(e.target.value)}>
            <option value="">{t('ruptures.tousProduits')}</option>
            {[...produits.values()].map((p) => (
              <option key={p.id} value={p.id}>
                {libelle(p.libelle)}
              </option>
            ))}
          </select>
        </label>
        <label className="space-y-1">
          <span className="block">{t('ciblage.villes')}</span>
          <Input className="w-40" list="villes-ruptures" value={ville} onChange={(e) => setVille(e.target.value)} />
          <ListeVilles id="villes-ruptures" />
        </label>
        <label className="space-y-1">
          <span className="block">{t('ruptures.precision')}</span>
          <select className={filtre} value={precision} onChange={(e) => setPrecision(Number(e.target.value) as 5 | 6)}>
            <option value={6}>{t('ruptures.precisionFine')}</option>
            <option value={5}>{t('ruptures.precisionLarge')}</option>
          </select>
        </label>
        <label className="space-y-1">
          <span className="block">{t('ruptures.depuis')}</span>
          <Input type="date" value={depuis} onChange={(e) => setDepuis(e.target.value)} />
        </label>
        <label className="space-y-1">
          <span className="block">{t('ruptures.jusqua')}</span>
          <Input type="date" value={jusqua} onChange={(e) => setJusqua(e.target.value)} />
        </label>
      </div>
      <Erreur erreur={carte.erreur} />
      <Carte
        className="h-[28rem] w-full rounded-lg border"
        points={cellules.map((c) => ({
          id: c.geohash + c.produitId,
          lat: c.centre.lat,
          lon: c.centre.lon,
          rayon: 8 + 22 * Math.sqrt(c.signalements / max),
          etiquette: String(c.signalements),
          couleur: c.enCours > 0 ? '#dc2626' : '#0f766e',
        }))}
      />
      {carte.data && (
        <p className="text-xs text-muted-foreground">
          {t('ruptures.anonymat', { n: carte.data.seuilAnonymat })} · {t('ruptures.genereLe', { d: date(carte.data.genereLe) })}
        </p>
      )}
      {carte.data && !cellules.length && <p className="text-sm text-muted-foreground">{t('commun.aucunResultat')}</p>}
      {!!cellules.length && (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>{t('ruptures.zone')}</TableHead>
              <TableHead>{t('ligne.produit')}</TableHead>
              <TableHead>{t('ruptures.signalements')}</TableHead>
              <TableHead>{t('ruptures.resolues')}</TableHead>
              <TableHead>{t('ruptures.enCours')}</TableHead>
              <TableHead>{t('ruptures.delaiMoyen')}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {[...cellules]
              .sort((a, b) => b.signalements - a.signalements)
              .map((c) => (
                <TableRow key={c.geohash + c.produitId}>
                  <TableCell dir="ltr" className="font-mono">
                    {c.geohash}
                  </TableCell>
                  <TableCell>{nomProduit(c.produitId)}</TableCell>
                  <TableCell>{nombre(c.signalements)}</TableCell>
                  <TableCell>{nombre(c.resolues)}</TableCell>
                  <TableCell>{nombre(c.enCours)}</TableCell>
                  <TableCell>{c.delaiMoyenResolutionMinutes === undefined ? '—' : t('ruptures.minutes', { n: nombre(c.delaiMoyenResolutionMinutes) })}</TableCell>
                </TableRow>
              ))}
          </TableBody>
        </Table>
      )}
    </section>
  )
}

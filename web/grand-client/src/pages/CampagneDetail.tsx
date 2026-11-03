import { useState } from 'react'
import { ArrowLeft } from 'lucide-react'
import { api, ok, type S } from '@/api/client'
import { useI18n, type Cle } from '@/i18n'
import { useCharge } from '@/lib/useCharge'
import { useCategories, useProduits, useRegions } from '@/lib/useProduits'
import { Carte } from '@/components/Carte'
import { ListeVilles } from '@/components/ListeVilles'
import { Erreur } from '@/components/Erreur'
import { StatutCampagne, StatutDepot } from '@/components/Statut'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'

const CYCLE: S['StatutCampagne'][][] = [['BROUILLON'], ['PROPOSEE'], ['ACTIVE'], ['EPUISEE', 'EXPIREE'], ['CLOTUREE']]
const ETAPES = ['depose', 'reserve', 'retire', 'revendu'] as const

export function CampagneDetail({ id }: { id: string }) {
  const { t, libelle, date, nombre, montant } = useI18n()
  const chemin = { params: { path: { campagneId: id } } }
  const campagne = useCharge(() => api.GET('/v1/campagnes/{campagneId}', chemin).then(ok), [id])
  const produits = useProduits(
    (campagne.data?.lignes ?? []).flatMap((l) => (l.remise.type === 'PRODUIT_OFFERT' ? [l.produitId, l.remise.produitOffertId] : [l.produitId])),
  )
  const depots = useCharge(() => api.GET('/v1/campagnes/{campagneId}/depots', chemin).then(ok), [id])
  const entonnoir = useCharge(() => api.GET('/v1/campagnes/{campagneId}/entonnoir', chemin).then(ok), [id])
  const [erreur, setErreur] = useState<unknown>()

  const c = campagne.data
  if (campagne.erreur) return <Erreur erreur={campagne.erreur} />
  if (!c) return <p className="text-muted-foreground">{t('commun.chargement')}</p>

  const nomProduit = (pid: string) => {
    const p = produits.get(pid)
    return p ? libelle(p.libelle) : pid.slice(0, 8)
  }
  const tout = () => (campagne.recharger(), depots.recharger(), entonnoir.recharger())

  async function action(f: () => Promise<unknown>) {
    setErreur(undefined)
    try {
      await f()
      tout()
    } catch (e) {
      setErreur(e)
    }
  }

  function decrireRemise(r: S['Remise']) {
    switch (r.type) {
      case 'POURCENTAGE':
        return t('remise.descPourcentage', { p: r.pourcentage })
      case 'PRIX_NET':
        return t('remise.descPrixNet', { m: montant(r.prixNet) })
      case 'PRODUIT_OFFERT':
        return t('remise.descOffert', { a: r.quantiteAchetee, o: r.quantiteOfferte, produit: nomProduit(r.produitOffertId) })
      case 'PALIER':
        return r.paliers.map((p) => t('remise.descPalier', { q: p.quantiteMin, p: p.pourcentage })).join(' · ')
    }
  }

  const rangActuel = CYCLE.findIndex((etape) => etape.includes(c.statut))

  return (
    <section className="space-y-4">
      <div className="flex flex-wrap items-center gap-2">
        <a href="#/campagnes" className="flex items-center gap-1 text-sm text-muted-foreground hover:underline">
          <ArrowLeft className="size-4 rtl:rotate-180" aria-hidden />
          {t('commun.retour')}
        </a>
        <h1 className="me-auto text-xl font-semibold">{libelle(c.nom)}</h1>
        <StatutCampagne statut={c.statut} />
        {c.statut === 'BROUILLON' && (
          <>
            <Button variant="outline" asChild>
              <a href={`#/campagnes/${id}/modifier`}>{t('detail.modifier')}</a>
            </Button>
            <Button
              variant="destructive"
              onClick={() =>
                confirm(t('detail.confirmerSuppression')) &&
                action(async () => {
                  ok(await api.DELETE('/v1/campagnes/{campagneId}', chemin))
                  location.hash = '#/campagnes'
                })
              }
            >
              {t('detail.supprimer')}
            </Button>
          </>
        )}
      </div>
      {c.description && <p className="text-muted-foreground">{libelle(c.description)}</p>}
      <Erreur erreur={erreur} />

      <ol className="flex flex-wrap gap-2 text-sm" aria-label={t('detail.cycle')}>
        {CYCLE.map((etape, i) => (
          <li
            key={etape.join()}
            className={`rounded-full border px-3 py-1 ${i === rangActuel ? 'border-promo bg-promo font-medium text-promo-foreground' : i < rangActuel ? 'border-promo-soft bg-promo-soft text-promo-soft-foreground' : 'text-muted-foreground'}`}
          >
            {etape.map((s) => t(`statut.${s}`)).join(' / ')}
          </li>
        ))}
      </ol>

      <Card>
        <CardHeader>
          <CardTitle>{t('detail.offre')}</CardTitle>
        </CardHeader>
        <CardContent className="space-y-2">
          <p className="text-sm">
            {t('commun.cleValeur', { cle: t('campagne.periode'), valeur: `${date(c.debut)} – ${date(c.fin)}` })} ·{' '}
            {t('commun.cleValeur', { cle: t('campagne.delaiRetrait'), valeur: t('unite.heures', { n: c.delaiRetraitHeures ?? 24 }) })}
          </p>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t('ligne.produit')}</TableHead>
                <TableHead>{t('remise.type')}</TableHead>
                <TableHead>{t('ligne.quantiteTotale')}</TableHead>
                <TableHead>{t('ligne.plafond')}</TableHead>
                <TableHead>{t('ligne.prixConseille')}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {c.lignes.map((l) => (
                <TableRow key={l.produitId}>
                  <TableCell>{nomProduit(l.produitId)}</TableCell>
                  <TableCell className="whitespace-normal">{decrireRemise(l.remise)}</TableCell>
                  <TableCell>{nombre(l.quantiteTotale)}</TableCell>
                  <TableCell>{nombre(l.quantiteMaxParAmbulant)}</TableCell>
                  <TableCell>{l.prixReventeConseille === undefined ? '—' : `${montant(l.prixReventeConseille)} (${t('ligne.indicatif')})`}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
          <Ciblage ciblage={c.ciblage} />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>{t('entonnoir.titre')}</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <p className="text-xs text-muted-foreground">{t('entonnoir.anonyme')}</p>
          <Erreur erreur={entonnoir.erreur} />
          {entonnoir.data && (
            <>
              <Barres e={entonnoir.data.total} />
              <TableEtapes
                titre={t('ligne.produit')}
                lignes={entonnoir.data.parProduit.map((p) => ({ cle: p.produitId, nom: nomProduit(p.produitId), e: p }))}
              />
              <TableEtapes
                titre={t('depots.grossiste')}
                lignes={entonnoir.data.parGrossiste.map((g) => ({ cle: g.grossiste.id, nom: g.grossiste.nom, e: g }))}
              />
            </>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>{t('depots.titre')}</CardTitle>
        </CardHeader>
        <CardContent>
          <Erreur erreur={depots.erreur} />
          {depots.data?.length === 0 && <p className="text-sm text-muted-foreground">{t('depots.aucun')}</p>}
          {!!depots.data?.length && (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t('depots.grossiste')}</TableHead>
                  <TableHead>{t('campagne.statut')}</TableHead>
                  <TableHead>{t('ligne.produit')}</TableHead>
                  <TableHead>{t('depots.proposee')}</TableHead>
                  <TableHead>{t('depots.contreProposee')}</TableHead>
                  <TableHead>{t('entonnoir.depose')}</TableHead>
                  <TableHead>{t('entonnoir.reserve')}</TableHead>
                  <TableHead>{t('entonnoir.retire')}</TableHead>
                  <TableHead>{t('depots.restante')}</TableHead>
                  <TableHead>{t('depots.reception')}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {depots.data.flatMap((d) =>
                  d.lignes.map((l, i) => (
                    <TableRow key={d.id + l.produitId}>
                      {i === 0 && (
                        <>
                          <TableCell rowSpan={d.lignes.length}>
                            {d.grossiste.nom}
                            {d.grossiste.ville && <span className="block text-xs text-muted-foreground">{d.grossiste.ville}</span>}
                          </TableCell>
                          <TableCell rowSpan={d.lignes.length}>
                            <StatutDepot statut={d.statut} />
                            {d.statut === 'CONTRE_PROPOSE' && (
                              <div className="mt-2 flex gap-1">
                                <Button
                                  size="sm"
                                  variant="outline"
                                  onClick={() =>
                                    action(async () =>
                                      ok(await api.POST('/v1/depots/{depotId}/reponse-marque', { params: { path: { depotId: d.id } }, body: { decision: 'ACCEPTER' } })),
                                    )
                                  }
                                >
                                  {t('depots.accepterContre')}
                                </Button>
                                <Button
                                  size="sm"
                                  variant="outline"
                                  onClick={() =>
                                    action(async () =>
                                      ok(await api.POST('/v1/depots/{depotId}/reponse-marque', { params: { path: { depotId: d.id } }, body: { decision: 'REFUSER' } })),
                                    )
                                  }
                                >
                                  {t('depots.refuserContre')}
                                </Button>
                              </div>
                            )}
                          </TableCell>
                        </>
                      )}
                      <TableCell>{nomProduit(l.produitId)}</TableCell>
                      <TableCell>{nombre(l.quantiteProposee)}</TableCell>
                      <TableCell>{l.quantiteContreProposee === undefined ? '—' : nombre(l.quantiteContreProposee)}</TableCell>
                      <TableCell>{nombre(l.quantiteDeposee)}</TableCell>
                      <TableCell>{nombre(l.quantiteReservee)}</TableCell>
                      <TableCell>{nombre(l.quantiteRetiree)}</TableCell>
                      <TableCell>{nombre(l.quantiteRestante)}</TableCell>
                      {i === 0 && (
                        <TableCell rowSpan={d.lignes.length}>
                          {d.recuLe ? date(d.recuLe) : '—'}
                          {d.photoBonLivraisonUrl && (
                            <a href={d.photoBonLivraisonUrl} target="_blank" rel="noreferrer" className="block text-xs underline">
                              {t('depots.photoBL')}
                            </a>
                          )}
                        </TableCell>
                      )}
                    </TableRow>
                  )),
                )}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      {c.statut !== 'EXPIREE' && c.statut !== 'CLOTUREE' && (
        <Affectation campagne={c} depots={depots.data ?? []} nomProduit={nomProduit} onPropose={tout} />
      )}
    </section>
  )
}

function Ciblage({ ciblage }: { ciblage?: S['Ciblage'] }) {
  const { t, nombre, liste, libelle } = useI18n()
  const categories = useCategories()
  const regions = useRegions()
  // Codes de GET /v1/regions ; une ancienne saisie libre s'affiche telle quelle.
  const lieux = new Map(regions.flatMap((r) => [[r.code, r.nom] as const, ...r.villes.map((v) => [v.code, v.nom] as const)]))
  const nom = (code: string) => {
    const n = lieux.get(code)
    return n ? libelle(n) : code
  }
  const cv = (cle: Cle, valeur: string) => t('commun.cleValeur', { cle: t(cle), valeur })
  const c = ciblage ?? {}
  const morceaux = [
    c.regions?.length && cv('ciblage.regions', liste(c.regions.map(nom))),
    c.villes?.length && cv('ciblage.villes', liste(c.villes.map(nom))),
    c.categories?.length && cv('ciblage.categories', liste(c.categories.map((k) => (categories.has(k) ? libelle(categories.get(k)!.libelle) : k)))),
    c.zones?.length && cv('ciblage.zones', nombre(c.zones.length)),
    c.rayonM && cv('ciblage.rayon', nombre(c.rayonM)),
  ].filter(Boolean)
  return <p className="text-sm text-muted-foreground">{morceaux.length ? morceaux.join(' · ') : t('ciblage.aucun')}</p>
}

function Barres({ e }: { e: S['EtapesEntonnoir'] }) {
  const { t, nombre } = useI18n()
  const base = Math.max(e.depose, 1)
  return (
    <div className="space-y-2">
      {ETAPES.map((k) => (
        <div key={k} className="grid grid-cols-[8rem_1fr_auto] items-center gap-2 text-sm">
          <span>{t(`entonnoir.${k}`)}</span>
          <div className="h-5 rounded bg-promo-soft">
            <div className="h-5 rounded bg-promo" style={{ inlineSize: `${Math.min(100, (100 * e[k]) / base)}%` }} />
          </div>
          <span className="tabular-nums">
            {nombre(e[k])} · {t('unite.pourcent', { n: nombre(Math.round((100 * e[k]) / base)) })}
          </span>
        </div>
      ))}
    </div>
  )
}

function TableEtapes({ titre, lignes }: { titre: string; lignes: { cle: string; nom: string; e: S['EtapesEntonnoir'] }[] }) {
  const { t, nombre } = useI18n()
  if (!lignes.length) return null
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>{titre}</TableHead>
          {ETAPES.map((k) => (
            <TableHead key={k}>{t(`entonnoir.${k}`)}</TableHead>
          ))}
        </TableRow>
      </TableHeader>
      <TableBody>
        {lignes.map((l) => (
          <TableRow key={l.cle}>
            <TableCell>{l.nom}</TableCell>
            {ETAPES.map((k) => (
              <TableCell key={k}>{nombre(l.e[k])}</TableCell>
            ))}
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}

/** Choix des grossistes dépositaires sur carte ou liste filtrée, et répartition des quantités (§5.2). */
function Affectation({
  campagne,
  depots,
  nomProduit,
  onPropose,
}: {
  campagne: S['Campagne']
  depots: S['Depot'][]
  nomProduit: (id: string) => string
  onPropose: () => void
}) {
  const { t, nombre } = useI18n()
  const [q, setQ] = useState('')
  const [ville, setVille] = useState('')
  const [choisi, setChoisi] = useState<S['Grossiste']>()
  const [quantites, setQuantites] = useState<Record<string, number>>({})
  const [erreur, setErreur] = useState<unknown>()
  const grossistes = useCharge(
    () =>
      api
        .GET('/v1/grossistes', { params: { query: { q: q.trim().length >= 2 ? q.trim() : undefined, ville: ville.trim() || undefined, taille: 200 } } })
        .then(ok),
    [q, ville],
  )
  const dejaProposes = new Set(depots.filter((d) => d.statut !== 'REFUSE').map((d) => d.grossiste.id))
  const reste = (pid: string) =>
    (campagne.lignes.find((l) => l.produitId === pid)?.quantiteTotale ?? 0) -
    depots
      .filter((d) => d.statut !== 'REFUSE')
      .flatMap((d) => d.lignes)
      .filter((l) => l.produitId === pid)
      .reduce((s, l) => s + (l.quantiteContreProposee ?? l.quantiteProposee), 0)

  async function proposer() {
    if (!choisi) return
    const lignes = campagne.lignes.map((l) => ({ produitId: l.produitId, quantite: quantites[l.produitId] ?? 0 })).filter((l) => l.quantite >= 1)
    if (!lignes.length) return setErreur(t('affectation.aucuneQuantite'))
    setErreur(undefined)
    try {
      ok(
        await api.POST('/v1/campagnes/{campagneId}/depots', {
          params: { path: { campagneId: campagne.id } },
          body: { grossisteId: choisi.id, lignes },
        }),
      )
      setChoisi(undefined)
      setQuantites({})
      onPropose()
    } catch (e) {
      setErreur(e)
    }
  }

  const liste = grossistes.data?.contenu ?? []
  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('affectation.titre')}</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="flex flex-wrap gap-2">
          <Input className="max-w-xs" placeholder={t('affectation.recherche')} value={q} onChange={(e) => setQ(e.target.value)} />
          <Input className="max-w-xs" list="villes-grossistes" placeholder={t('ciblage.villes')} value={ville} onChange={(e) => setVille(e.target.value)} />
          <ListeVilles id="villes-grossistes" />
        </div>
        <Erreur erreur={grossistes.erreur} />
        <div className="grid gap-4 lg:grid-cols-2">
          <Carte
            points={liste.map((g) => ({
              id: g.id,
              lat: g.position.lat,
              lon: g.position.lon,
              couleur: g.id === choisi?.id ? 'primary' : dejaProposes.has(g.id) ? 'promo' : undefined,
            }))}
            onPoint={(gid) => setChoisi(liste.find((g) => g.id === gid))}
          />
          <ul className="max-h-96 divide-y overflow-y-auto rounded-lg border">
            {liste.map((g) => (
              <li key={g.id}>
                <button
                  type="button"
                  className={`w-full p-2 text-start text-sm hover:bg-secondary ${g.id === choisi?.id ? 'bg-secondary font-medium' : ''}`}
                  onClick={() => setChoisi(g)}
                >
                  {g.nom}
                  <span className="block text-xs text-muted-foreground">
                    {[g.adresse, g.ville].filter(Boolean).join(', ')}
                    {dejaProposes.has(g.id) && ` · ${t('affectation.dejaPropose')}`}
                  </span>
                </button>
              </li>
            ))}
            {grossistes.data && !liste.length && <li className="p-2 text-sm text-muted-foreground">{t('commun.aucunResultat')}</li>}
          </ul>
        </div>
        {choisi && (
          <div className="space-y-3 rounded-lg border p-3">
            <p className="font-medium">{t('affectation.repartition', { nom: choisi.nom })}</p>
            {campagne.lignes.map((l) => (
              <label key={l.produitId} className="flex flex-wrap items-center gap-2 text-sm">
                <span className="min-w-48">{nomProduit(l.produitId)}</span>
                <Input
                  type="number"
                  dir="ltr"
                  min={0}
                  max={Math.max(0, reste(l.produitId))}
                  className="w-28"
                  value={quantites[l.produitId] ?? ''}
                  onChange={(e) => setQuantites({ ...quantites, [l.produitId]: e.target.valueAsNumber })}
                />
                <span className="text-muted-foreground">{t('affectation.reste', { n: nombre(reste(l.produitId)) })}</span>
              </label>
            ))}
            <Erreur erreur={erreur} />
            <Button onClick={proposer}>{t('affectation.proposer')}</Button>
          </div>
        )}
      </CardContent>
    </Card>
  )
}

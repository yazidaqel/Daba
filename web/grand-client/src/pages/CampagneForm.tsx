import { useState } from 'react'
import { api, ok, type S } from '@/api/client'
import { LANGUES, useI18n } from '@/i18n'
import { TYPES_REMISE, ciblerRegions, remiseParDefaut, validerCampagne, type TypeRemise } from '@/lib/campagne'
import { isoVersLocal, localVersIso, versCentimes, versDirhams } from '@/lib/format'
import { useCharge } from '@/lib/useCharge'
import { useCategories, useProduits, useRegions } from '@/lib/useProduits'
import { Carte } from '@/components/Carte'
import { Erreur } from '@/components/Erreur'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'

type Saisie = S['CampagneSaisie']
type Ligne = S['LigneCampagne']

const VIDE: Saisie = { nom: { fr: '' }, debut: '', fin: '', lignes: [], ciblage: {}, delaiRetraitHeures: 24 }

function Nombre({ id, valeur, onChange, min = 1, max }: { id?: string; valeur: number; onChange: (n: number) => void; min?: number; max?: number }) {
  return (
    <Input
      id={id}
      type="number"
      dir="ltr"
      min={min}
      max={max}
      value={Number.isNaN(valeur) ? '' : valeur}
      onChange={(e) => onChange(e.target.valueAsNumber)}
    />
  )
}

function Coches({ titre, aide, options: connues, choisis = [], onChange }: { titre: string; aide?: string; options: { code: string; nom: string }[]; choisis?: string[]; onChange: (codes: string[]) => void }) {
  // Une valeur hors référentiel (saisie libre d'avant la v0.4.0) reste visible, pour pouvoir la décocher.
  const options = [...connues, ...choisis.filter((c) => !connues.some((o) => o.code === c)).map((c) => ({ code: c, nom: c }))]
  return (
    <fieldset className="space-y-1 text-sm">
      <legend className="font-medium">{titre}</legend>
      <div className="flex flex-wrap gap-x-3 gap-y-1">
        {options.map((o) => (
          <label key={o.code} className="flex items-center gap-1">
            <input
              type="checkbox"
              checked={choisis.includes(o.code)}
              onChange={(e) => onChange(e.target.checked ? [...choisis, o.code] : choisis.filter((x) => x !== o.code))}
            />
            {o.nom}
          </label>
        ))}
      </div>
      {aide && <p className="text-xs text-muted-foreground">{aide}</p>}
    </fieldset>
  )
}

function Champ({ label, children, aide }: { label: string; children: React.ReactNode; aide?: string }) {
  return (
    <label className="block space-y-1 text-sm">
      <span className="font-medium">{label}</span>
      {children}
      {aide && <span className="block text-xs text-muted-foreground">{aide}</span>}
    </label>
  )
}

export function CampagneForm({ id }: { id?: string }) {
  const { t, libelle } = useI18n()
  const existante = useCharge(
    () => (id ? api.GET('/v1/campagnes/{campagneId}', { params: { path: { campagneId: id } } }).then(ok) : Promise.resolve(undefined)),
    [id],
  )
  const [saisie, setSaisie] = useState<Saisie>()
  const produits = useProduits((saisie ?? existante.data)?.lignes.map((l) => l.produitId) ?? [])
  const categories = useCategories()
  const regions = useRegions()
  const [recherche, setRecherche] = useState('')
  const [trace, setTrace] = useState<number[][]>([])
  const [erreur, setErreur] = useState<unknown>()
  const [occupe, setOccupe] = useState(false)
  const resultats = useCharge(
    () =>
      recherche.trim().length >= 2
        ? api.GET('/v1/produits', { params: { query: { q: recherche.trim(), mesProduits: true, taille: 10 } } }).then(ok)
        : Promise.resolve(undefined),
    [recherche],
  )

  if (existante.chargement) return <p className="text-muted-foreground">{t('commun.chargement')}</p>
  if (existante.erreur) return <Erreur erreur={existante.erreur} />
  if (existante.data && existante.data.statut !== 'BROUILLON') return <Erreur erreur={t('form.nonModifiable')} />

  const c: Saisie = saisie ?? existante.data ?? VIDE
  const maj = (p: Partial<Saisie>) => setSaisie({ ...c, ...p })
  const majLigne = (i: number, p: Partial<Ligne>) => maj({ lignes: c.lignes.map((l, j) => (j === i ? { ...l, ...p } : l)) })
  const ciblage = c.ciblage ?? {}
  const majCiblage = (p: Partial<S['Ciblage']>) => maj({ ciblage: { ...ciblage, ...p } })
  const nomProduit = (pid: string) => {
    const p = produits.get(pid)
    return p ? `${libelle(p.libelle)} · ${p.marque}` : pid.slice(0, 8)
  }
  // Seules les villes des régions cochées se proposent (voir ciblerRegions).
  const regionsCochees = regions.filter((r) => ciblage.regions?.includes(r.code))
  const villesProposees = (regionsCochees.length ? regionsCochees : regions).flatMap((r) => r.villes)

  async function enregistrer(e: React.FormEvent) {
    e.preventDefault()
    const fautes = validerCampagne(c)
    if (fautes.length) return setErreur(fautes.map((f) => t(f)).join(' · '))
    // Les champs de lecture seule de la campagne existante ne repartent pas.
    const corps: Saisie = {
      nom: c.nom,
      description: c.description?.fr ? c.description : undefined,
      debut: c.debut,
      fin: c.fin,
      lignes: c.lignes,
      ciblage: c.ciblage,
      delaiRetraitHeures: c.delaiRetraitHeures,
    }
    setOccupe(true)
    setErreur(undefined)
    try {
      const r = id
        ? ok(await api.PUT('/v1/campagnes/{campagneId}', { params: { path: { campagneId: id } }, body: corps }))
        : ok(await api.POST('/v1/campagnes', { body: corps }))
      location.hash = `#/campagnes/${r.id}`
    } catch (err) {
      setErreur(err)
    } finally {
      setOccupe(false)
    }
  }

  function ajouterProduit(p: S['Produit']) {
    if (c.lignes.some((l) => l.produitId === p.id)) return
    maj({ lignes: [...c.lignes, { produitId: p.id, remise: remiseParDefaut('POURCENTAGE', p.id), quantiteTotale: 100, quantiteMaxParAmbulant: 10 }] })
    setRecherche('')
  }

  function fermerZone() {
    if (trace.length < 3) return
    majCiblage({ zones: [...(ciblage.zones ?? []), { type: 'Polygon', coordinates: [[...trace, trace[0]]] }] })
    setTrace([])
  }

  return (
    <form onSubmit={enregistrer} className="space-y-4">
      <h1 className="text-xl font-semibold">{id ? t('form.titreModifier') : t('form.titreNouvelle')}</h1>

      <Card>
        <CardHeader>
          <CardTitle>{t('form.general')}</CardTitle>
        </CardHeader>
        <CardContent className="grid gap-4 md:grid-cols-2">
          {LANGUES.map((l) => (
            <Champ key={l} label={`${t('campagne.nom')} (${t(`langue.${l}`)})${l === 'fr' ? ' *' : ''}`}>
              <Input
                dir={l === 'ar' || l === 'ary-Arab' ? 'rtl' : 'ltr'}
                value={c.nom[l] ?? ''}
                required={l === 'fr'}
                onChange={(e) => maj({ nom: { ...c.nom, [l]: e.target.value } })}
              />
            </Champ>
          ))}
          {LANGUES.map((l) => (
            <Champ key={l} label={`${t('campagne.description')} (${t(`langue.${l}`)})`}>
              <Input
                dir={l === 'ar' || l === 'ary-Arab' ? 'rtl' : 'ltr'}
                value={c.description?.[l] ?? ''}
                onChange={(e) => maj({ description: { fr: '', ...c.description, [l]: e.target.value } })}
              />
            </Champ>
          ))}
          <Champ label={t('campagne.debut')}>
            <Input type="datetime-local" required value={isoVersLocal(c.debut)} onChange={(e) => maj({ debut: localVersIso(e.target.value) })} />
          </Champ>
          <Champ label={t('campagne.fin')}>
            <Input type="datetime-local" required value={isoVersLocal(c.fin)} onChange={(e) => maj({ fin: localVersIso(e.target.value) })} />
          </Champ>
          <Champ label={t('campagne.delaiRetrait')} aide={t('campagne.delaiRetraitAide')}>
            <Nombre valeur={c.delaiRetraitHeures ?? 24} max={168} onChange={(n) => maj({ delaiRetraitHeures: n })} />
          </Champ>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>{t('form.produits')}</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          {c.lignes.map((l, i) => (
            <fieldset key={l.produitId} className="space-y-3 rounded-lg border p-3">
              <legend className="px-1 font-medium">{nomProduit(l.produitId)}</legend>
              <div className="grid gap-3 md:grid-cols-4">
                <Champ label={t('remise.type')}>
                  <select
                    className="h-9 w-full rounded-md border bg-background px-2"
                    value={l.remise.type}
                    onChange={(e) => majLigne(i, { remise: remiseParDefaut(e.target.value as TypeRemise, l.produitId) })}
                  >
                    {TYPES_REMISE.map((r) => (
                      <option key={r} value={r}>
                        {t(`remise.${r}`)}
                      </option>
                    ))}
                  </select>
                </Champ>
                <EditeurRemise remise={l.remise} onChange={(remise) => majLigne(i, { remise })} produits={c.lignes.map((x) => x.produitId)} nomProduit={nomProduit} />
              </div>
              <div className="grid gap-3 md:grid-cols-3">
                <Champ label={t('ligne.quantiteTotale')}>
                  <Nombre valeur={l.quantiteTotale} onChange={(n) => majLigne(i, { quantiteTotale: n })} />
                </Champ>
                <Champ label={t('ligne.plafond')} aide={t('ligne.plafondAide')}>
                  <Nombre valeur={l.quantiteMaxParAmbulant} onChange={(n) => majLigne(i, { quantiteMaxParAmbulant: n })} />
                </Champ>
                <Champ label={t('ligne.prixConseille')} aide={t('ligne.prixConseilleAide')}>
                  <Input
                    dir="ltr"
                    inputMode="decimal"
                    defaultValue={versDirhams(l.prixReventeConseille)}
                    onChange={(e) => majLigne(i, { prixReventeConseille: versCentimes(e.target.value) })}
                  />
                </Champ>
              </div>
              <Button type="button" variant="ghost" size="sm" onClick={() => maj({ lignes: c.lignes.filter((_, j) => j !== i) })}>
                {t('commun.retirer')}
              </Button>
            </fieldset>
          ))}
          <Champ label={t('form.ajouterProduit')}>
            <Input value={recherche} placeholder={t('form.rechercheProduit')} onChange={(e) => setRecherche(e.target.value)} />
          </Champ>
          {resultats.data && (
            <ul className="divide-y rounded-md border">
              {resultats.data.contenu.length === 0 && <li className="p-2 text-sm text-muted-foreground">{t('commun.aucunResultat')}</li>}
              {resultats.data.contenu.map((p) => (
                <li key={p.id}>
                  <button type="button" className="w-full p-2 text-start text-sm hover:bg-secondary" onClick={() => ajouterProduit(p)}>
                    {libelle(p.libelle)} · {p.marque} · <span dir="ltr">{p.ean}</span> · {t(`conditionnement.${p.conditionnement}`)}
                  </button>
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>{t('form.ciblage')}</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <p className="text-sm text-muted-foreground">{t('ciblage.aide')}</p>
          <Coches titre={t('ciblage.regions')} options={regions.map((r) => ({ code: r.code, nom: libelle(r.nom) }))} choisis={ciblage.regions} onChange={(codes) => maj({ ciblage: ciblerRegions(ciblage, regions, codes) })} />
          <Coches
            titre={t('ciblage.villes')}
            aide={t('ciblage.villesAide')}
            options={villesProposees.map((v) => ({ code: v.code, nom: libelle(v.nom) }))}
            choisis={ciblage.villes}
            onChange={(villes) => majCiblage({ villes })}
          />
          <Coches
            titre={t('ciblage.categories')}
            options={[...categories.values()].map((cat) => ({ code: cat.code, nom: libelle(cat.libelle) }))}
            choisis={ciblage.categories}
            onChange={(categories) => majCiblage({ categories })}
          />
          <div className="max-w-xs">
            <Champ label={t('ciblage.rayon')} aide={t('ciblage.rayonAide')}>
              <Nombre
                valeur={ciblage.rayonM ?? NaN}
                min={500}
                max={50000}
                onChange={(n) => majCiblage({ rayonM: Number.isNaN(n) ? undefined : n })}
              />
            </Champ>
          </div>
          <div className="space-y-2">
            <span className="text-sm font-medium">{t('ciblage.zones')}</span>
            <p className="text-xs text-muted-foreground">{t('ciblage.zonesAide')}</p>
            <Carte
              zones={(ciblage.zones ?? []).map((z) => z.coordinates)}
              trace={trace}
              onClic={(lon, lat) => setTrace([...trace, [lon, lat]])}
            />
            <div className="flex flex-wrap gap-2">
              <Button type="button" variant="outline" size="sm" disabled={trace.length < 3} onClick={fermerZone}>
                {t('ciblage.fermerZone')}
              </Button>
              <Button type="button" variant="ghost" size="sm" onClick={() => (setTrace([]), majCiblage({ zones: [] }))}>
                {t('ciblage.effacerZones')} ({ciblage.zones?.length ?? 0})
              </Button>
            </div>
          </div>
        </CardContent>
      </Card>

      <Erreur erreur={erreur} />
      <div className="flex gap-2">
        <Button type="submit" disabled={occupe}>
          {t('form.enregistrer')}
        </Button>
        <Button type="button" variant="outline" onClick={() => history.back()}>
          {t('commun.annuler')}
        </Button>
      </div>
    </form>
  )
}

function EditeurRemise({
  remise,
  onChange,
  produits,
  nomProduit,
}: {
  remise: S['Remise']
  onChange: (r: S['Remise']) => void
  produits: string[]
  nomProduit: (id: string) => string
}) {
  const { t } = useI18n()
  switch (remise.type) {
    case 'POURCENTAGE':
      return (
        <Champ label={t('remise.pourcentage')}>
          <Nombre valeur={remise.pourcentage} max={100} onChange={(n) => onChange({ ...remise, pourcentage: n })} />
        </Champ>
      )
    case 'PRIX_NET':
      return (
        <Champ label={t('remise.prixNet')}>
          <Input dir="ltr" inputMode="decimal" defaultValue={versDirhams(remise.prixNet || undefined)} onChange={(e) => onChange({ ...remise, prixNet: versCentimes(e.target.value) ?? 0 })} />
        </Champ>
      )
    case 'PRODUIT_OFFERT':
      return (
        <>
          <Champ label={t('remise.quantiteAchetee')}>
            <Nombre valeur={remise.quantiteAchetee} onChange={(n) => onChange({ ...remise, quantiteAchetee: n })} />
          </Champ>
          <Champ label={t('remise.produitOffert')}>
            <select
              className="h-9 w-full rounded-md border bg-background px-2"
              value={remise.produitOffertId}
              onChange={(e) => onChange({ ...remise, produitOffertId: e.target.value })}
            >
              {produits.map((p) => (
                <option key={p} value={p}>
                  {nomProduit(p)}
                </option>
              ))}
            </select>
          </Champ>
          <Champ label={t('remise.quantiteOfferte')}>
            <Nombre valeur={remise.quantiteOfferte} onChange={(n) => onChange({ ...remise, quantiteOfferte: n })} />
          </Champ>
        </>
      )
    case 'PALIER':
      return (
        <div className="space-y-2 md:col-span-3">
          {remise.paliers.map((p, i) => {
            const majPalier = (q: Partial<typeof p>) => onChange({ ...remise, paliers: remise.paliers.map((x, j) => (j === i ? { ...x, ...q } : x)) })
            return (
              <div key={i} className="flex flex-wrap items-end gap-2">
                <Champ label={t('remise.quantiteMin')}>
                  <Nombre valeur={p.quantiteMin} onChange={(n) => majPalier({ quantiteMin: n })} />
                </Champ>
                <Champ label={t('remise.pourcentage')}>
                  <Nombre valeur={p.pourcentage} max={100} onChange={(n) => majPalier({ pourcentage: n })} />
                </Champ>
                <Button type="button" variant="ghost" size="sm" disabled={remise.paliers.length === 1} onClick={() => onChange({ ...remise, paliers: remise.paliers.filter((_, j) => j !== i) })}>
                  {t('commun.retirer')}
                </Button>
              </div>
            )
          })}
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => onChange({ ...remise, paliers: [...remise.paliers, { quantiteMin: (remise.paliers.at(-1)?.quantiteMin ?? 0) + 10, pourcentage: 5 }] })}
          >
            {t('remise.ajouterPalier')}
          </Button>
        </div>
      )
  }
}

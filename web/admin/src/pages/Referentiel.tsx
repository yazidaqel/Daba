import { useState, type FormEvent, type ReactNode } from 'react'
import { FileUp } from 'lucide-react'
import { $api, queryClient, type S } from '@/api/client'
import { Erreur, Etat, Pagination, SELECT, Succes } from '@/components/communs'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { useI18n, type Langue } from '@/i18n'

// En-têtes attendus, tels que décrits par le contrat (importerHanouts, importerProduits).
const COLONNES_HANOUTS = ['code_pos', 'nom', 'gerant', 'telephone', 'lat', 'lon', 'adresse', 'ville', 'ice']
const COLONNES_PRODUITS = ['ean', 'libelle_fr', 'libelle_ar', 'libelle_ary_arab', 'libelle_ary_latn', 'marque', 'categorie', 'conditionnement', 'unites', 'photo_url', 'grand_client_id', 'taux_tva']
const COLONNES_CATEGORIES = ['categorie_libelle_fr', 'categorie_libelle_ar', 'categorie_libelle_ary_arab', 'categorie_libelle_ary_latn']

const libelle = (l: S['LibelleI18n'], langue: Langue) => l[langue] || l.fr

export default function Referentiel() {
  const { t } = useI18n()
  const [onglet, setOnglet] = useState<'produits' | 'hanouts'>('produits')
  return (
    <section className="space-y-4">
      <h1 className="text-2xl font-semibold">{t('ref.titre')}</h1>
      <div className="grid gap-4 lg:grid-cols-2">
        <Import chemin="/v1/admin/hanouts/import" titre={t('ref.importHanouts')} aide={t('ref.importHanoutsAide')} colonnes={COLONNES_HANOUTS} />
        <Import
          chemin="/v1/admin/produits/import"
          titre={t('ref.importProduits')}
          aide={t('ref.importProduitsAide')}
          colonnes={COLONNES_PRODUITS}
          facultatives={COLONNES_CATEGORIES}
        />
      </div>
      <div className="flex gap-2" role="tablist">
        {(['produits', 'hanouts'] as const).map((o) => (
          <Button key={o} size="sm" role="tab" aria-selected={onglet === o} variant={onglet === o ? 'secondary' : 'outline'} onClick={() => setOnglet(o)}>
            {t(`ref.${o}`)}
          </Button>
        ))}
      </div>
      {onglet === 'produits' ? <Produits /> : <Hanouts />}
    </section>
  )
}

function Import({ chemin, titre, aide, colonnes, facultatives }: {
  chemin: '/v1/admin/hanouts/import' | '/v1/admin/produits/import'
  titre: string
  aide: string
  colonnes: string[]
  facultatives?: string[]
}) {
  const { t, nombre } = useI18n()
  const [fichier, setFichier] = useState<File | null>(null)
  const importer = $api.useMutation('post', chemin, { onSuccess: () => queryClient.invalidateQueries() })

  function envoyer(e: FormEvent) {
    e.preventDefault()
    if (!fichier) return
    const fd = new FormData()
    fd.append('fichier', fichier)
    // Le typage décrit la partie ; le corps réel est le FormData.
    importer.mutate({ body: { fichier: '' }, bodySerializer: () => fd })
  }

  const bilan = importer.data
  return (
    <Card>
      <CardHeader>
        <CardTitle>{titre}</CardTitle>
        <p className="text-muted-foreground text-sm">{aide}</p>
      </CardHeader>
      <CardContent className="space-y-3">
        <div className="text-sm">
          <p className="font-medium">{t('ref.colonnes')}</p>
          <p dir="ltr" className="text-start"><code className="break-words">{colonnes.join(';')}</code></p>
          {facultatives && (
            <>
              <p className="mt-1 font-medium">{t('ref.colonnesFacultatives')}</p>
              <p dir="ltr" className="text-start"><code className="break-words">{facultatives.join(';')}</code></p>
            </>
          )}
        </div>
        <form onSubmit={envoyer} className="flex flex-wrap items-end gap-2">
          <div className="space-y-1">
            <Label htmlFor={chemin}>{t('ref.fichier')}</Label>
            <Input id={chemin} type="file" accept=".csv,text/csv" onChange={(e) => setFichier(e.target.files?.[0] ?? null)} />
          </div>
          <Button type="submit" variant="outline" disabled={!fichier || importer.isPending}>
            <FileUp aria-hidden />
            {importer.isPending ? t('ref.importEnCours') : t('ref.importer')}
          </Button>
        </form>
        {importer.error && <Erreur erreur={importer.error} />}
        {bilan && (
          <>
            <Succes>{t('ref.bilan', { crees: nombre(bilan.crees), misAJour: nombre(bilan.misAJour), rejets: nombre(bilan.rejets.length) })}</Succes>
            {bilan.rejets.length > 0 && (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{t('ref.ligne')}</TableHead>
                    <TableHead>{t('ref.motif')}</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {bilan.rejets.map((r) => (
                    <TableRow key={r.ligne + r.erreur}>
                      <TableCell>{r.ligne}</TableCell>
                      <TableCell className="whitespace-normal">{r.erreur}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </>
        )}
      </CardContent>
    </Card>
  )
}

function Recherche({ onChercher, children }: { onChercher: (q: string) => void; children?: ReactNode }) {
  const { t } = useI18n()
  const [saisie, setSaisie] = useState('')
  return (
    <form
      className="flex flex-wrap items-end gap-2"
      onSubmit={(e) => (e.preventDefault(), onChercher(saisie.trim().length >= 2 ? saisie.trim() : ''))}
    >
      <div className="space-y-1">
        <Label htmlFor="r-q">{t('ref.recherche')}</Label>
        <Input id="r-q" autoComplete="off" value={saisie} onChange={(e) => setSaisie(e.target.value)} />
      </div>
      {children}
      <Button type="submit" variant="outline">{t('app.rechercher')}</Button>
    </form>
  )
}

function Produits() {
  const { t, langue } = useI18n()
  const [q, setQ] = useState('')
  const [categorie, setCategorie] = useState('')
  const [page, setPage] = useState(0)
  const categories = $api.useQuery('get', '/v1/categories')
  const requete = $api.useQuery('get', '/v1/produits', {
    params: { query: { q: q || undefined, categorie: categorie || undefined, page, taille: 50 } },
  })
  return (
    <div className="space-y-3">
      <Recherche onChercher={(v) => (setQ(v), setPage(0))}>
        <div className="space-y-1">
          <Label htmlFor="r-cat">{t('ref.categorie')}</Label>
          <select id="r-cat" className={SELECT} value={categorie} onChange={(e) => (setCategorie(e.target.value), setPage(0))}>
            <option value="">{t('ref.toutes')}</option>
            {categories.data?.map((c) => (
              <option key={c.code} value={c.code}>{libelle(c.libelle, langue)}</option>
            ))}
          </select>
        </div>
      </Recherche>
      <Etat requete={requete}>
        {(d) => (
          <>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t('ref.produit')}</TableHead>
                  <TableHead>{t('ref.marque')}</TableHead>
                  <TableHead>{t('ref.categorie')}</TableHead>
                  <TableHead>{t('ref.ean')}</TableHead>
                  <TableHead>{t('ref.conditionnement')}</TableHead>
                  <TableHead>{t('ref.tva')}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {d.contenu.map((p) => (
                  <TableRow key={p.id}>
                    <TableCell className="font-medium">{libelle(p.libelle, langue)}</TableCell>
                    <TableCell>{p.marque}</TableCell>
                    <TableCell>
                      {libelle(p.categorie.libelle, langue)}
                      <code dir="ltr" className="text-muted-foreground ms-1 text-xs">{p.categorie.code}</code>
                    </TableCell>
                    <TableCell><code dir="ltr">{p.ean}</code></TableCell>
                    <TableCell>{t(`cond.${p.conditionnement}`, { n: p.unitesParConditionnement })}</TableCell>
                    <TableCell>{p.tauxTva === undefined ? '—' : `${p.tauxTva} %`}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
            <Pagination meta={d} onPage={setPage} />
          </>
        )}
      </Etat>
    </div>
  )
}

function Hanouts() {
  const { t } = useI18n()
  const [q, setQ] = useState('')
  const [page, setPage] = useState(0)
  const requete = $api.useQuery('get', '/v1/hanouts', { params: { query: { q: q || undefined, page, taille: 50 } } })
  return (
    <div className="space-y-3">
      <Recherche onChercher={(v) => (setQ(v), setPage(0))} />
      <Etat requete={requete}>
        {(d) => (
          <>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t('ref.codePos')}</TableHead>
                  <TableHead>{t('ref.hanout')}</TableHead>
                  <TableHead>{t('ref.gerant')}</TableHead>
                  <TableHead>{t('membre.telephone')}</TableHead>
                  <TableHead>{t('orgs.ville')}</TableHead>
                  <TableHead>{t('orgs.position')}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {d.contenu.map((h) => (
                  <TableRow key={h.id}>
                    <TableCell><code dir="ltr">{h.codePos ?? '—'}</code></TableCell>
                    <TableCell className="font-medium">{h.nom}</TableCell>
                    <TableCell>{h.gerant ?? '—'}</TableCell>
                    <TableCell><bdi dir="ltr">{h.telephone ?? '—'}</bdi></TableCell>
                    <TableCell>{h.ville ?? '—'}</TableCell>
                    <TableCell className="text-xs"><bdi dir="ltr">{h.position.lat.toFixed(5)}, {h.position.lon.toFixed(5)}</bdi></TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
            <Pagination meta={d} onPage={setPage} />
          </>
        )}
      </Etat>
    </div>
  )
}

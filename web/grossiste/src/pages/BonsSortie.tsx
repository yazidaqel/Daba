import { useState, type FormEvent } from 'react'
import { renderSVG } from 'uqr'
import { $api, queryClient, type S } from '@/api/client'
import { libelle, useNomProduit } from '@/api/produits'
import { Charge, Erreur, Etat, Pagination } from '@/components/communs'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { useI18n } from '@/i18n'
import { versE164 } from '@/telephone'

// Bon de sortie grossiste → ambulant (§4.3) : transmis par synchronisation, chargé par scan du QR.
export default function BonsSortie() {
  const { t, date } = useI18n()
  const [page, setPage] = useState(0)
  const [nouveau, setNouveau] = useState(false)
  const [affiche, setAffiche] = useState<S['BonSortie'] | null>(null)
  const requete = $api.useQuery('get', '/v1/bons-sortie', { params: { query: { page, taille: 50 } } })

  if (affiche) return <BonQr bon={affiche} onFermer={() => setAffiche(null)} />

  return (
    <section className="space-y-4">
      <div className="flex items-center gap-3">
        <h1 className="text-2xl font-semibold">{t('bons.titre')}</h1>
        {!nouveau && <Button className="ms-auto" onClick={() => setNouveau(true)}>{t('bons.nouveau')}</Button>}
      </div>
      {nouveau && <NouveauBon onEmis={(b) => (setNouveau(false), setAffiche(b))} onAnnuler={() => setNouveau(false)} />}
      <Etat requete={requete}>
        {(d) => (
          <>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t('bons.numero')}</TableHead>
                  <TableHead>{t('bons.ambulant')}</TableHead>
                  <TableHead>{t('bons.emisLe')}</TableHead>
                  <TableHead className="text-end">{t('bons.lignes')}</TableHead>
                  <TableHead />
                  <TableHead />
                </TableRow>
              </TableHeader>
              <TableBody>
                {d.contenu.map((b) => (
                  <TableRow key={b.id}>
                    <TableCell className="font-medium" dir="ltr">{b.numero}</TableCell>
                    <TableCell>{b.ambulant.nom}</TableCell>
                    <TableCell>{date(b.emisLe)}</TableCell>
                    <TableCell className="text-end">{b.lignes.length}</TableCell>
                    <TableCell>
                      <Charge charge={!!b.chargeLe} />
                    </TableCell>
                    <TableCell className="text-end">
                      <Button size="sm" variant="outline" onClick={() => setAffiche(b)}>{t('bons.voir')}</Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
            <Pagination meta={d} onPage={setPage} />
          </>
        )}
      </Etat>
    </section>
  )
}

function NouveauBon({ onEmis, onAnnuler }: { onEmis: (b: S['BonSortie']) => void; onAnnuler: () => void }) {
  const { t, langue } = useI18n()
  // Clé d'idempotence : un double clic ou un renvoi ne crée pas un second bon.
  const [id] = useState(() => crypto.randomUUID())
  const [telephone, setTelephone] = useState('')
  const [recherche, setRecherche] = useState('')
  const [q, setQ] = useState('')
  const [lignes, setLignes] = useState<S['LigneQuantite'][]>([])
  const [noms, setNoms] = useState<Record<string, string>>({})
  const [erreur, setErreur] = useState<string | null>(null)
  const ean = /^(\d{8}|\d{13})$/.test(q)
  const produits = $api.useQuery(
    'get',
    '/v1/produits',
    { params: { query: ean ? { ean: q, taille: 20 } : { q, taille: 20 } } },
    { enabled: q.length >= 2 },
  )
  const ambulants = $api.useQuery('get', '/v1/ambulants', { params: { query: { taille: 200 } } })
  const emettre = $api.useMutation('post', '/v1/bons-sortie', {
    onSuccess: (b) => (queryClient.invalidateQueries(), onEmis(b)),
  })

  function ajouter(p: S['Produit']) {
    setNoms({ ...noms, [p.id]: `${libelle(p.libelle, langue)} · ${p.marque} (${t(`cond.${p.conditionnement}`)})` })
    if (!lignes.some((l) => l.produitId === p.id)) setLignes([...lignes, { produitId: p.id, quantite: 1 }])
  }

  function soumettre(e: FormEvent) {
    e.preventDefault()
    const tel = versE164(telephone)
    if (!tel) return setErreur(t('login.telephoneInvalide'))
    if (lignes.length === 0) return setErreur(t('bons.aucuneLigne'))
    setErreur(null)
    emettre.mutate({ body: { id, ambulantTelephone: tel, lignes } })
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('bons.nouveau')}</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="space-y-1">
          <Label htmlFor="tel-ambulant">{t('bons.telephoneAmbulant')}</Label>
          <Input id="tel-ambulant" type="tel" dir="ltr" className="max-w-xs" list="ambulants-rattaches" value={telephone} onChange={(e) => setTelephone(e.target.value)} />
          <p className="text-muted-foreground text-sm">{t('bons.choisirAmbulant')}</p>
          <datalist id="ambulants-rattaches">
            {ambulants.data?.contenu.filter((a) => a.telephone).map((a) => <option key={a.id} value={a.telephone}>{a.nom}</option>)}
          </datalist>
        </div>
        <form onSubmit={(e) => (e.preventDefault(), setQ(recherche.trim()))} className="space-y-1">
          <Label htmlFor="recherche">{t('bons.rechercheProduit')}</Label>
          <div className="flex max-w-md gap-2">
            <Input id="recherche" value={recherche} onChange={(e) => setRecherche(e.target.value)} />
            <Button type="submit" variant="outline">{t('bons.chercher')}</Button>
          </div>
        </form>
        {q.length >= 2 && (
          <Etat requete={produits}>
            {(d) => (
              <ul className="max-w-md divide-y rounded-md border">
                {d.contenu.map((p) => (
                  <li key={p.id} className="flex items-center gap-2 px-3 py-2 text-sm">
                    <span>{libelle(p.libelle, langue)} · {p.marque} ({t(`cond.${p.conditionnement}`)})</span>
                    <Button size="sm" variant="outline" className="ms-auto" onClick={() => ajouter(p)}>{t('bons.ajouter')}</Button>
                  </li>
                ))}
              </ul>
            )}
          </Etat>
        )}
        {lignes.length > 0 && (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t('depot.produit')}</TableHead>
                <TableHead className="text-end">{t('bons.quantite')}</TableHead>
                <TableHead />
              </TableRow>
            </TableHeader>
            <TableBody>
              {lignes.map((l, i) => (
                <TableRow key={l.produitId}>
                  <TableCell>{noms[l.produitId]}</TableCell>
                  <TableCell className="text-end">
                    <Input
                      type="number"
                      min={1}
                      className="ms-auto w-28"
                      aria-label={t('bons.quantite')}
                      value={l.quantite}
                      onChange={(e) => setLignes(lignes.map((x, j) => (j === i ? { ...x, quantite: Math.max(1, Math.floor(Number(e.target.value) || 1)) } : x)))}
                    />
                  </TableCell>
                  <TableCell className="text-end">
                    <Button size="sm" variant="ghost" onClick={() => setLignes(lignes.filter((_, j) => j !== i))}>{t('bons.retirer')}</Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
        <form onSubmit={soumettre} className="flex gap-2">
          <Button type="submit" disabled={emettre.isPending || lignes.length > 30}>{t('bons.emettre')}</Button>
          <Button type="button" variant="outline" onClick={onAnnuler}>{t('app.annuler')}</Button>
        </form>
        {erreur && <Erreur erreur={{ detail: erreur }} />}
        {emettre.error && <Erreur erreur={emettre.error} />}
      </CardContent>
    </Card>
  )
}

function BonQr({ bon, onFermer }: { bon: S['BonSortie']; onFermer: () => void }) {
  const { t, date, nombre } = useI18n()
  const nom = useNomProduit(bon.lignes.map((l) => l.produitId))
  return (
    <section className="max-w-2xl space-y-4">
      <h1 className="text-2xl font-semibold">
        {t('bons.titre')} <span dir="ltr">{bon.numero}</span>
      </h1>
      <p>{bon.ambulant.nom} · {date(bon.emisLe)}</p>
      {/* SVG produit localement par uqr à partir du jeton signé du bon. */}
      <div className="bg-qr-fond text-qr-encre w-72 p-2" dangerouslySetInnerHTML={{ __html: renderSVG(bon.qrJeton, { whiteColor: 'transparent', blackColor: 'currentColor' }) }} />
      <p className="text-muted-foreground text-sm">{t('bons.qrAide')}</p>
      <Table>
        <TableBody>
          {bon.lignes.map((l) => (
            <TableRow key={l.produitId}>
              <TableCell>{nom(l.produitId)}</TableCell>
              <TableCell className="text-end">{nombre(l.quantite)}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
      <div className="no-print flex gap-2">
        <Button onClick={() => window.print()}>{t('bons.imprimer')}</Button>
        <Button variant="outline" onClick={onFermer}>{t('bons.fermer')}</Button>
      </div>
    </section>
  )
}

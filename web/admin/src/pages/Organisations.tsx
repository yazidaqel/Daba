import { useState, type FormEvent } from 'react'
import { Plus, Trash2 } from 'lucide-react'
import { Link, useNavigate } from 'react-router'
import { $api, queryClient, type S } from '@/api/client'
import { Erreur, Etat, Pagination, SELECT } from '@/components/communs'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { useI18n } from '@/i18n'
import { versE164 } from '@/telephone'

type Type = 'GROSSISTE' | 'GRAND_CLIENT'

export default function Organisations() {
  const { t, date, nombre } = useI18n()
  const [type, setType] = useState<Type | ''>('')
  const [saisie, setSaisie] = useState('')
  const [q, setQ] = useState('')
  const [page, setPage] = useState(0)
  const [creation, setCreation] = useState(false)
  const requete = $api.useQuery('get', '/v1/admin/organisations', {
    params: { query: { type: type || undefined, q: q || undefined, page, taille: 50 } },
  })

  function chercher(e: FormEvent) {
    e.preventDefault()
    setQ(saisie.trim().length >= 2 ? saisie.trim() : '')
    setPage(0)
  }

  return (
    <section className="space-y-4">
      <div className="flex flex-wrap items-center gap-2">
        <h1 className="me-auto text-2xl font-semibold">{t('orgs.titre')}</h1>
        {!creation && (
          <Button variant="outline" onClick={() => setCreation(true)}>
            <Plus aria-hidden />
            {t('orgs.nouvelle')}
          </Button>
        )}
      </div>
      {creation && <Creation onFin={() => setCreation(false)} />}
      <form onSubmit={chercher} className="flex flex-wrap items-end gap-2">
        <div className="space-y-1">
          <Label htmlFor="type">{t('orgs.type')}</Label>
          <select id="type" className={SELECT} value={type} onChange={(e) => (setType(e.target.value as Type | ''), setPage(0))}>
            <option value="">{t('orgs.tous')}</option>
            <option value="GROSSISTE">{t('typeOrg.GROSSISTE')}</option>
            <option value="GRAND_CLIENT">{t('typeOrg.GRAND_CLIENT')}</option>
          </select>
        </div>
        <div className="space-y-1">
          <Label htmlFor="q">{t('orgs.recherche')}</Label>
          <Input id="q" value={saisie} onChange={(e) => setSaisie(e.target.value)} />
        </div>
        <Button type="submit" variant="outline">{t('app.rechercher')}</Button>
      </form>
      <Etat requete={requete}>
        {(d) => (
          <>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t('orgs.nom')}</TableHead>
                  <TableHead>{t('orgs.type')}</TableHead>
                  <TableHead>{t('orgs.ville')}</TableHead>
                  <TableHead>{t('orgs.utilisateurs')}</TableHead>
                  <TableHead>{t('orgs.creeLe')}</TableHead>
                  <TableHead />
                </TableRow>
              </TableHeader>
              <TableBody>
                {d.contenu.map((o) => (
                  <TableRow key={o.id}>
                    <TableCell className="font-medium">{o.nom}</TableCell>
                    <TableCell>{t(`typeOrg.${o.type}`)}</TableCell>
                    <TableCell>{o.grossiste?.ville ?? '—'}</TableCell>
                    <TableCell>{nombre(o.nombreUtilisateurs)}</TableCell>
                    <TableCell>{date(o.creeLe)}</TableCell>
                    <TableCell className="text-end">
                      <Button asChild size="sm" variant="outline">
                        <Link to={`/organisations/${o.id}`}>{t('app.ouvrir')}</Link>
                      </Button>
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

type Membre = { telephone: string; nom: string }

// Inscription d'un grossiste (fiche avec position) ou d'un grand client, avec ses premiers utilisateurs.
function Creation({ onFin }: { onFin: () => void }) {
  const { t } = useI18n()
  const naviguer = useNavigate()
  const [type, setType] = useState<Type>('GROSSISTE')
  const [nom, setNom] = useState('')
  const [adresse, setAdresse] = useState('')
  const [ville, setVille] = useState('')
  const [lat, setLat] = useState('')
  const [lon, setLon] = useState('')
  const [membres, setMembres] = useState<Membre[]>([{ telephone: '', nom: '' }])
  const [invalide, setInvalide] = useState<string | null>(null)
  const creer = $api.useMutation('post', '/v1/admin/organisations', {
    onSuccess: (o) => (queryClient.invalidateQueries(), naviguer(`/organisations/${o.id}`)),
  })
  const changer = (i: number, m: Partial<Membre>) => setMembres(membres.map((x, j) => (j === i ? { ...x, ...m } : x)))

  function soumettre(e: FormEvent) {
    e.preventDefault()
    const utilisateurs = membres.map((m) => ({ telephone: versE164(m.telephone), nom: m.nom.trim() || undefined }))
    const faux = utilisateurs.findIndex((u) => !u.telephone)
    if (faux >= 0) return setInvalide(t('orgs.telephoneInvalide', { numero: membres[faux].telephone || '—' }))
    setInvalide(null)
    const grossiste = type === 'GROSSISTE'
    creer.mutate({
      body: {
        type,
        nom: nom.trim(),
        utilisateurs: utilisateurs as S['OrganisationSaisie']['utilisateurs'],
        adresse: adresse.trim() || undefined,
        ville: ville.trim() || undefined,
        position: grossiste ? { lat: Number(lat), lon: Number(lon) } : undefined,
      },
    })
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('orgs.nouvelle')}</CardTitle>
      </CardHeader>
      <CardContent>
        <form onSubmit={soumettre} className="space-y-4">
          <div className="grid gap-3 sm:grid-cols-2">
            <div className="space-y-1">
              <Label htmlFor="c-type">{t('orgs.type')}</Label>
              <select id="c-type" className={`${SELECT} w-full`} value={type} onChange={(e) => setType(e.target.value as Type)}>
                <option value="GROSSISTE">{t('typeOrg.GROSSISTE')}</option>
                <option value="GRAND_CLIENT">{t('typeOrg.GRAND_CLIENT')}</option>
              </select>
            </div>
            <div className="space-y-1">
              <Label htmlFor="c-nom">{t('orgs.nom')}</Label>
              <Input id="c-nom" value={nom} onChange={(e) => setNom(e.target.value)} required />
            </div>
            <div className="space-y-1">
              <Label htmlFor="c-adresse">{t('orgs.adresse')}</Label>
              <Input id="c-adresse" value={adresse} onChange={(e) => setAdresse(e.target.value)} />
            </div>
            <div className="space-y-1">
              <Label htmlFor="c-ville">{t('orgs.ville')}</Label>
              <Input id="c-ville" value={ville} onChange={(e) => setVille(e.target.value)} />
            </div>
            {type === 'GROSSISTE' && (
              <>
                <div className="space-y-1">
                  <Label htmlFor="c-lat">{t('orgs.lat')}</Label>
                  <Input id="c-lat" dir="ltr" type="number" step="any" min={-90} max={90} value={lat} onChange={(e) => setLat(e.target.value)} required />
                </div>
                <div className="space-y-1">
                  <Label htmlFor="c-lon">{t('orgs.lon')}</Label>
                  <Input id="c-lon" dir="ltr" type="number" step="any" min={-180} max={180} value={lon} onChange={(e) => setLon(e.target.value)} required />
                </div>
                <p className="text-muted-foreground text-sm sm:col-span-2">{t('orgs.positionAide')}</p>
              </>
            )}
          </div>
          <fieldset className="space-y-2">
            <legend className="font-medium">{t('orgs.premiersUtilisateurs')}</legend>
            <p className="text-muted-foreground text-sm">{t('orgs.premiersAide')}</p>
            {membres.map((m, i) => (
              <div key={i} className="flex flex-wrap items-end gap-2">
                <div className="space-y-1">
                  <Label htmlFor={`m-tel-${i}`}>{t('membre.telephone')}</Label>
                  <Input id={`m-tel-${i}`} type="tel" dir="ltr" value={m.telephone} onChange={(e) => changer(i, { telephone: e.target.value })} required />
                </div>
                <div className="space-y-1">
                  <Label htmlFor={`m-nom-${i}`}>{t('membre.nom')}</Label>
                  <Input id={`m-nom-${i}`} value={m.nom} onChange={(e) => changer(i, { nom: e.target.value })} />
                </div>
                {membres.length > 1 && (
                  <Button type="button" variant="ghost" size="icon" aria-label={t('orgs.retirerMembre')} onClick={() => setMembres(membres.filter((_, j) => j !== i))}>
                    <Trash2 aria-hidden />
                  </Button>
                )}
              </div>
            ))}
            <Button type="button" size="sm" variant="outline" onClick={() => setMembres([...membres, { telephone: '', nom: '' }])}>
              <Plus aria-hidden />
              {t('orgs.ajouterMembre')}
            </Button>
          </fieldset>
          <div className="flex gap-2">
            <Button type="submit" disabled={creer.isPending}>{t('orgs.creer')}</Button>
            <Button type="button" variant="outline" onClick={onFin}>{t('app.annuler')}</Button>
          </div>
          {invalide && <Erreur erreur={{ detail: invalide }} />}
          {creer.error && <Erreur erreur={creer.error} />}
        </form>
      </CardContent>
    </Card>
  )
}

import { useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router'
import { $api, queryClient, type S } from '@/api/client'
import { useNomProduit } from '@/api/produits'
import { Erreur, Etat, StatutDepot, Succes } from '@/components/communs'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { useI18n } from '@/i18n'
import { compresserPhoto, formulaireReception } from '@/photo'

export default function Depot() {
  const { depotId } = useParams() as { depotId: string }
  const { t, date } = useI18n()
  // Suivi en temps réel (§4.2) : relecture régulière tant que le dépôt vit.
  const requete = $api.useQuery(
    'get',
    '/v1/depots/{depotId}',
    { params: { path: { depotId } } },
    { refetchInterval: (q) => (q.state.data?.statut === 'RECU' ? 10_000 : false) },
  )

  return (
    <section className="space-y-6">
      <Link to="/depots" className="text-muted-foreground text-sm hover:underline">
        {t('depot.retour')}
      </Link>
      <Etat requete={requete}>
        {(d) => (
          <>
            <header className="flex flex-wrap items-center gap-3">
              <h1 className="text-2xl font-semibold">{d.campagne.nom}</h1>
              <StatutDepot statut={d.statut} />
              {d.marque && <span className="text-muted-foreground">{d.marque.nom}</span>}
              {d.finCampagne && <span className="text-muted-foreground text-sm">{t('depot.finLe', { date: date(d.finCampagne) })}</span>}
              {d.statut === 'RECU' && <Badge variant="outline">{t('depot.miseAJour')}</Badge>}
            </header>
            <Lignes depot={d} />
            {d.recuLe && <p className="text-sm">{t('depot.recuLe', { date: date(d.recuLe) })}</p>}
            {d.photoBonLivraisonUrl && (
              <a className="text-sm underline" href={d.photoBonLivraisonUrl} target="_blank" rel="noreferrer">
                {t('depot.photoBL')}
              </a>
            )}
            {d.statut === 'PROPOSE' && <Reponse depot={d} />}
            {d.statut === 'CONTRE_PROPOSE' && <Succes>{t('reponse.attenteMarque')}</Succes>}
            {d.statut === 'ACCEPTE' && <Reception depot={d} />}
            {d.statut === 'RECU' && (
              <>
                <Reservations depotId={d.id} />
                {cloturePossible(d) && <Cloture depot={d} />}
              </>
            )}
            {d.statut === 'CLOTURE' && d.cloture && <Succes>{t('cloture.faite', { issue: t(`cloture.${d.cloture.issueReliquat}`) })}</Succes>}
          </>
        )}
      </Etat>
    </section>
  )
}

function Lignes({ depot }: { depot: S['Depot'] }) {
  const { t, nombre } = useI18n()
  const nom = useNomProduit()
  const recu = depot.statut === 'RECU' || depot.statut === 'CLOTURE'
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>{t('depot.produit')}</TableHead>
          <TableHead className="text-end">{t('depot.propose')}</TableHead>
          <TableHead className="text-end">{t('depot.contrePropose')}</TableHead>
          {recu && (
            <>
              <TableHead className="text-end">{t('depot.depose')}</TableHead>
              <TableHead className="text-end">{t('depot.reserve')}</TableHead>
              <TableHead className="text-end">{t('depot.retire')}</TableHead>
              <TableHead className="text-end">{t('depot.restant')}</TableHead>
            </>
          )}
        </TableRow>
      </TableHeader>
      <TableBody>
        {depot.lignes.map((l) => (
          <TableRow key={l.produitId}>
            <TableCell>{nom(l.produitId)}</TableCell>
            <TableCell className="text-end">{nombre(l.quantiteProposee)}</TableCell>
            <TableCell className="text-end">{l.quantiteContreProposee == null ? '—' : nombre(l.quantiteContreProposee)}</TableCell>
            {recu && (
              <>
                <TableCell className="text-end">{nombre(l.quantiteDeposee)}</TableCell>
                <TableCell className="text-end">{nombre(l.quantiteReservee)}</TableCell>
                <TableCell className="text-end">{nombre(l.quantiteRetiree)}</TableCell>
                <TableCell className="text-end font-semibold">{nombre(l.quantiteRestante)}</TableCell>
              </>
            )}
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}

// Le contrat répond 409 tant que la campagne n'est ni épuisée ni expirée ; le Depot ne porte pas son statut,
// on s'en approche par la date de fin ou un dépôt vidé.
const cloturePossible = (d: S['Depot']) =>
  (d.finCampagne != null && new Date(d.finCampagne) < new Date()) || d.lignes.every((l) => l.quantiteRestante + l.quantiteReservee === 0)

const invaliderTout = () => queryClient.invalidateQueries()

// Quantités saisies par ligne, initialisées à partir du dépôt.
function useQuantites(depot: S['Depot'], init: (l: S['LigneDepot']) => number) {
  const [q, setQ] = useState<Record<string, number>>(() => Object.fromEntries(depot.lignes.map((l) => [l.produitId, init(l)])))
  return [q, (id: string, v: string) => setQ({ ...q, [id]: Math.max(0, Math.floor(Number(v) || 0)) })] as const
}

function Reponse({ depot }: { depot: S['Depot'] }) {
  const { t } = useI18n()
  const nom = useNomProduit()
  const [contre, setContre] = useState(false)
  const [commentaire, setCommentaire] = useState('')
  const [quantites, setQuantite] = useQuantites(depot, (l) => l.quantiteProposee)
  const repondre = $api.useMutation('post', '/v1/depots/{depotId}/reponse', { onSuccess: invaliderTout })

  const envoyer = (decision: S['ReponseGrossiste']['decision']) =>
    repondre.mutate({
      params: { path: { depotId: depot.id } },
      body: {
        decision,
        commentaire: commentaire || undefined,
        lignes: decision === 'CONTRE_PROPOSER' ? depot.lignes.map((l) => ({ produitId: l.produitId, quantite: quantites[l.produitId] })) : undefined,
      },
    })

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('reponse.titre')}</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        {contre && (
          <div className="space-y-2">
            {depot.lignes.map((l) => (
              <div key={l.produitId} className="flex flex-wrap items-center gap-3">
                <Label htmlFor={`q-${l.produitId}`} className="min-w-64">{nom(l.produitId)}</Label>
                <Input
                  id={`q-${l.produitId}`}
                  type="number"
                  min={1}
                  max={l.quantiteProposee}
                  className="w-32"
                  aria-label={t('reponse.quantite')}
                  value={quantites[l.produitId]}
                  onChange={(e) => setQuantite(l.produitId, e.target.value)}
                />
              </div>
            ))}
          </div>
        )}
        <div className="space-y-1">
          <Label htmlFor="commentaire">{t('reponse.commentaire')}</Label>
          <Input id="commentaire" maxLength={500} value={commentaire} onChange={(e) => setCommentaire(e.target.value)} />
        </div>
        <div className="flex flex-wrap gap-2">
          {!contre ? (
            <>
              <Button disabled={repondre.isPending} onClick={() => envoyer('ACCEPTER')}>{t('reponse.accepter')}</Button>
              <Button variant="outline" onClick={() => setContre(true)}>{t('reponse.contreProposer')}</Button>
              <Button variant="destructive" disabled={repondre.isPending} onClick={() => envoyer('REFUSER')}>{t('reponse.refuser')}</Button>
            </>
          ) : (
            <>
              <Button
                disabled={repondre.isPending || depot.lignes.some((l) => !(quantites[l.produitId] >= 1 && quantites[l.produitId] <= l.quantiteProposee))}
                onClick={() => envoyer('CONTRE_PROPOSER')}
              >
                {t('reponse.envoyerContre')}
              </Button>
              <Button variant="outline" onClick={() => setContre(false)}>{t('app.annuler')}</Button>
            </>
          )}
        </div>
        {repondre.error && <Erreur erreur={repondre.error} />}
      </CardContent>
    </Card>
  )
}

function Reception({ depot }: { depot: S['Depot'] }) {
  const { t, nombre } = useI18n()
  const nom = useNomProduit()
  const attendu = (l: S['LigneDepot']) => l.quantiteContreProposee ?? l.quantiteProposee
  const [quantites, setQuantite] = useQuantites(depot, attendu)
  const [photo, setPhoto] = useState<File | null>(null)
  const [commentaire, setCommentaire] = useState('')
  const [erreur, setErreur] = useState<string | null>(null)
  const confirmer = $api.useMutation('post', '/v1/depots/{depotId}/reception', { onSuccess: invaliderTout })

  async function soumettre(e: FormEvent) {
    e.preventDefault()
    if (!photo) return setErreur(t('reception.photoRequise'))
    const lignes = depot.lignes.map((l) => ({ produitId: l.produitId, quantiteRecue: quantites[l.produitId] }))
    if (!lignes.some((l) => l.quantiteRecue > 0)) return setErreur(t('reception.auMoinsUn'))
    setErreur(null)
    const reception = { lignes, commentaire: commentaire || undefined }
    const fd = formulaireReception(reception, await compresserPhoto(photo))
    confirmer.mutate({
      params: { path: { depotId: depot.id } },
      // Le typage décrit les deux parties ; le corps réel est le FormData construit ci-dessus.
      body: { reception, photoBonLivraison: '' },
      bodySerializer: () => fd,
    })
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('reception.titre')}</CardTitle>
        <p className="text-muted-foreground text-sm">{t('reception.aide')}</p>
      </CardHeader>
      <CardContent>
        <form onSubmit={soumettre} className="space-y-4">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t('depot.produit')}</TableHead>
                <TableHead className="text-end">{t('reception.attendu')}</TableHead>
                <TableHead className="text-end">{t('reception.recu')}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {depot.lignes.map((l) => (
                <TableRow key={l.produitId}>
                  <TableCell>{nom(l.produitId)}</TableCell>
                  <TableCell className="text-end">{nombre(attendu(l))}</TableCell>
                  <TableCell className="text-end">
                    <Input
                      type="number"
                      min={0}
                      className="ms-auto w-28"
                      aria-label={`${t('reception.recu')} — ${nom(l.produitId)}`}
                      value={quantites[l.produitId]}
                      onChange={(e) => setQuantite(l.produitId, e.target.value)}
                    />
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
          <div className="space-y-1">
            <Label htmlFor="photo">{t('reception.photo')}</Label>
            <Input id="photo" type="file" accept="image/jpeg,image/png,image/webp" capture="environment" onChange={(e) => setPhoto(e.target.files?.[0] ?? null)} />
          </div>
          <div className="space-y-1">
            <Label htmlFor="commentaire-reception">{t('reponse.commentaire')}</Label>
            <Input id="commentaire-reception" maxLength={500} value={commentaire} onChange={(e) => setCommentaire(e.target.value)} />
          </div>
          <Button type="submit" disabled={confirmer.isPending}>{t('reception.confirmer')}</Button>
          {erreur && <Erreur erreur={{ detail: erreur }} />}
          {confirmer.error && <Erreur erreur={confirmer.error} />}
          {confirmer.isSuccess && <Succes>{t('reception.ok')}</Succes>}
        </form>
      </CardContent>
    </Card>
  )
}

function Reservations({ depotId }: { depotId: string }) {
  const { t, date, nombre } = useI18n()
  const nom = useNomProduit()
  const requete = $api.useQuery('get', '/v1/reservations', { params: { query: { depotId, taille: 200 } } }, { refetchInterval: 10_000 })
  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('reservations.titre')}</CardTitle>
      </CardHeader>
      <CardContent>
        <Etat requete={requete}>
          {(d) => (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t('reservations.ambulant')}</TableHead>
                  <TableHead>{t('depot.produit')}</TableHead>
                  <TableHead className="text-end">{t('reservations.quantite')}</TableHead>
                  <TableHead>{t('reservations.statut')}</TableHead>
                  <TableHead>{t('reservations.expire')}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {d.contenu.map((r) => (
                  <TableRow key={r.id}>
                    <TableCell>{r.ambulant?.nom ?? '—'}</TableCell>
                    <TableCell>{nom(r.produitId)}</TableCell>
                    <TableCell className="text-end">{nombre(r.statut === 'RESERVEE' || r.statut === 'EXPIREE' ? r.quantiteReservee : (r.quantiteRetiree ?? r.quantiteReservee))}</TableCell>
                    <TableCell><Badge variant="secondary">{t(`resa.${r.statut}`)}</Badge></TableCell>
                    <TableCell>{date(r.expireLe)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </Etat>
      </CardContent>
    </Card>
  )
}

function Cloture({ depot }: { depot: S['Depot'] }) {
  const { t } = useI18n()
  const [issue, setIssue] = useState<S['ClotureDepot']['issueReliquat']>('RETOURNE')
  const [commentaire, setCommentaire] = useState('')
  const cloturer = $api.useMutation('post', '/v1/depots/{depotId}/cloture', { onSuccess: invaliderTout })
  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('cloture.titre')}</CardTitle>
        <p className="text-muted-foreground text-sm">{t('cloture.aide')}</p>
      </CardHeader>
      <CardContent className="space-y-3">
        <fieldset className="flex flex-wrap gap-4">
          {(['RETOURNE', 'RACHETE'] as const).map((v) => (
            <label key={v} className="flex items-center gap-2 text-sm">
              <input type="radio" name="issue" checked={issue === v} onChange={() => setIssue(v)} />
              {t(`cloture.${v}`)}
            </label>
          ))}
        </fieldset>
        <Input aria-label={t('reponse.commentaire')} placeholder={t('reponse.commentaire')} maxLength={500} value={commentaire} onChange={(e) => setCommentaire(e.target.value)} />
        <Button
          variant="outline"
          disabled={cloturer.isPending}
          onClick={() => cloturer.mutate({ params: { path: { depotId: depot.id } }, body: { issueReliquat: issue, commentaire: commentaire || undefined } })}
        >
          {t('cloture.confirmer')}
        </Button>
        {cloturer.error && <Erreur erreur={cloturer.error} />}
      </CardContent>
    </Card>
  )
}

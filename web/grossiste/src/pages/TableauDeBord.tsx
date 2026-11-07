import { CircleCheck, Clock } from 'lucide-react'
import { Link } from 'react-router'
import { $api } from '@/api/client'
import { Etat, StatutDepot } from '@/components/communs'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { useI18n } from '@/i18n'

const COLONNES = ['quantiteDeposee', 'quantiteReservee', 'quantiteRetiree', 'quantiteRestante'] as const
const TITRES = { quantiteDeposee: 'depot.depose', quantiteReservee: 'depot.reserve', quantiteRetiree: 'depot.retire', quantiteRestante: 'depot.restant' } as const

// Tableau de bord (§4.4), limité à ce que le contrat sert déjà : volumes par ambulant rattaché et par campagne promo.
// Rotation du stock, produits dormants et points attendent le stock grossiste complet (lot 3).
export default function TableauDeBord() {
  const { t } = useI18n()
  return (
    <section className="space-y-6">
      <h1 className="text-2xl font-semibold">{t('tableau.titre')}</h1>
      <VolumesCampagnes />
      <VolumesAmbulants />
    </section>
  )
}

function Tuile({ titre, valeur }: { titre: string; valeur: string }) {
  return (
    <div className="bg-card rounded-lg border p-4">
      <dt className="text-muted-foreground text-sm">{titre}</dt>
      <dd className="text-2xl font-semibold">{valeur}</dd>
    </div>
  )
}

function VolumesCampagnes() {
  const { t, nombre } = useI18n()
  // ponytail: une page de 200 dépôts, assez pour un grossiste du pilote ; paginer au-delà.
  const requete = $api.useQuery('get', '/v1/depots', { params: { query: { taille: 200 } } }, { refetchInterval: 30_000 })
  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('tableau.campagnes')}</CardTitle>
        <p className="text-muted-foreground text-sm">{t('tableau.campagnesAide')}</p>
      </CardHeader>
      <CardContent className="space-y-4">
        <Etat requete={requete}>
          {(d) => {
            // Seuls les dépôts reçus (ou clôturés après réception) portent des quantités.
            const lignes = d.contenu
              .filter((depot) => depot.statut === 'RECU' || depot.statut === 'CLOTURE')
              .map((depot) => ({
                depot,
                total: Object.fromEntries(COLONNES.map((c) => [c, depot.lignes.reduce((s, l) => s + l[c], 0)])) as Record<(typeof COLONNES)[number], number>,
              }))
            if (lignes.length === 0) return <p className="text-muted-foreground">{t('app.vide')}</p>
            const depose = lignes.reduce((s, l) => s + l.total.quantiteDeposee, 0)
            const retire = lignes.reduce((s, l) => s + l.total.quantiteRetiree, 0)
            return (
              <>
                <dl className="grid gap-3 sm:grid-cols-3">
                  <Tuile titre={t('tableau.campagnesRecues')} valeur={nombre(lignes.length)} />
                  <Tuile titre={t('tableau.unitesDeposees')} valeur={nombre(depose)} />
                  <Tuile titre={t('tableau.unitesRetirees')} valeur={nombre(retire)} />
                </dl>
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>{t('depots.campagne')}</TableHead>
                      <TableHead>{t('depots.statut')}</TableHead>
                      {COLONNES.map((c) => <TableHead key={c} className="text-end">{t(TITRES[c])}</TableHead>)}
                      <TableHead className="text-end">{t('tableau.ecoulement')}</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {lignes.map(({ depot, total }) => (
                      <TableRow key={depot.id}>
                        <TableCell className="font-medium">
                          <Link to={`/depots/${depot.id}`} className="hover:underline">{depot.campagne.nom}</Link>
                        </TableCell>
                        <TableCell><StatutDepot statut={depot.statut} /></TableCell>
                        {COLONNES.map((c) => <TableCell key={c} className="text-end">{nombre(total[c])}</TableCell>)}
                        <TableCell className="text-end">
                          {total.quantiteDeposee ? `${nombre(Math.round((100 * total.quantiteRetiree) / total.quantiteDeposee))} %` : '—'}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </>
            )
          }}
        </Etat>
      </CardContent>
    </Card>
  )
}

function VolumesAmbulants() {
  const { t, date, nombre } = useI18n()
  // ponytail: 200 ambulants (maximum d'une page), assez pour le pilote ; un tri serveur par volume si besoin.
  const requete = $api.useQuery('get', '/v1/ambulants', { params: { query: { taille: 200 } } })
  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('tableau.ambulants')}</CardTitle>
        <p className="text-muted-foreground text-sm">{t('tableau.ambulantsAide')}</p>
      </CardHeader>
      <CardContent className="space-y-4">
        <Etat requete={requete}>
          {(d) => {
            const ambulants = [...d.contenu].sort((a, b) => b.quantiteTotale - a.quantiteTotale)
            const actifs = ambulants.filter((a) => a.bons30Jours > 0).length
            return (
              <>
                <dl className="grid gap-3 sm:grid-cols-3">
                  <Tuile titre={t('tableau.ambulantsRattaches')} valeur={nombre(d.total)} />
                  <Tuile titre={t('tableau.ambulantsActifs')} valeur={nombre(actifs)} />
                  <Tuile titre={t('tableau.unitesSorties')} valeur={nombre(ambulants.reduce((s, a) => s + a.quantiteTotale, 0))} />
                </dl>
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>{t('ambulants.nom')}</TableHead>
                      <TableHead className="text-end">{t('ambulants.volume')}</TableHead>
                      <TableHead className="text-end">{t('ambulants.bons')}</TableHead>
                      <TableHead className="text-end">{t('ambulants.bons30')}</TableHead>
                      <TableHead>{t('tableau.regularite')}</TableHead>
                      <TableHead>{t('ambulants.dernier')}</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {ambulants.map((a) => (
                      <TableRow key={a.id}>
                        <TableCell className="font-medium">{a.nom}</TableCell>
                        <TableCell className="text-end">{nombre(a.quantiteTotale)}</TableCell>
                        <TableCell className="text-end">{nombre(a.nombreBons)}</TableCell>
                        <TableCell className="text-end">{nombre(a.bons30Jours)}</TableCell>
                        <TableCell>
                          {a.bons30Jours > 0 ? (
                            <Badge variant="success"><CircleCheck aria-hidden />{t('tableau.actif')}</Badge>
                          ) : (
                            <Badge variant="outline"><Clock aria-hidden />{t('tableau.inactif')}</Badge>
                          )}
                        </TableCell>
                        <TableCell>{a.dernierBonLe ? date(a.dernierBonLe) : '—'}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </>
            )
          }}
        </Etat>
      </CardContent>
    </Card>
  )
}

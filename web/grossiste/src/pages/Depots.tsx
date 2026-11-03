import { Link, useSearchParams } from 'react-router'
import { $api, type S } from '@/api/client'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Etat, Pagination, StatutDepot } from '@/components/communs'
import { useI18n } from '@/i18n'
import type { Cle } from '@/i18n/fr'

const FILTRES: { cle: Cle; statut?: S['StatutDepot'] }[] = [
  { cle: 'depots.filtre.aTraiter', statut: 'PROPOSE' },
  { cle: 'depots.filtre.aReceptionner', statut: 'ACCEPTE' },
  { cle: 'depots.filtre.enCours', statut: 'RECU' },
  { cle: 'depots.filtre.tous' },
]

export default function Depots() {
  const { t, date } = useI18n()
  const [params, setParams] = useSearchParams()
  const statut = (params.get('statut') ?? 'PROPOSE') as S['StatutDepot'] | 'TOUS'
  const page = Number(params.get('page') ?? 0)
  const requete = $api.useQuery(
    'get',
    '/v1/depots',
    { params: { query: { statut: statut === 'TOUS' ? undefined : statut, page, taille: 50 } } },
    { refetchInterval: 30_000 },
  )

  return (
    <section className="space-y-4">
      <h1 className="text-2xl font-semibold">{t('depots.titre')}</h1>
      <div className="flex flex-wrap gap-2">
        {FILTRES.map((f) => (
          <Button
            key={f.cle}
            size="sm"
            variant={(f.statut ?? 'TOUS') === statut ? 'secondary' : 'outline'}
            aria-pressed={(f.statut ?? 'TOUS') === statut}
            onClick={() => setParams({ statut: f.statut ?? 'TOUS' })}
          >
            {t(f.cle)}
          </Button>
        ))}
      </div>
      <Etat requete={requete}>
        {(d) => (
          <>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t('depots.campagne')}</TableHead>
                  <TableHead>{t('depots.marque')}</TableHead>
                  <TableHead>{t('depots.statut')}</TableHead>
                  <TableHead>{t('depots.produits')}</TableHead>
                  <TableHead>{t('depots.fin')}</TableHead>
                  <TableHead />
                </TableRow>
              </TableHeader>
              <TableBody>
                {d.contenu.map((depot) => (
                  <TableRow key={depot.id}>
                    <TableCell className="font-medium">{depot.campagne.nom}</TableCell>
                    <TableCell>{depot.marque?.nom ?? '—'}</TableCell>
                    <TableCell><StatutDepot statut={depot.statut} /></TableCell>
                    <TableCell><Badge variant="secondary">{depot.lignes.length}</Badge></TableCell>
                    <TableCell>{depot.finCampagne ? date(depot.finCampagne) : '—'}</TableCell>
                    <TableCell className="text-end">
                      <Button asChild size="sm" variant="outline">
                        <Link to={`/depots/${depot.id}`}>{t('depots.ouvrir')}</Link>
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
            <Pagination meta={d} onPage={(p) => setParams({ statut, page: String(p) })} />
          </>
        )}
      </Etat>
    </section>
  )
}

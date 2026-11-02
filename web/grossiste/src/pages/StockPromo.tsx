import { Link } from 'react-router'
import { $api } from '@/api/client'
import { useNomProduit } from '@/api/produits'
import { Etat } from '@/components/communs'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Table, TableBody, TableCell, TableFooter, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { useI18n } from '@/i18n'

const COLONNES = ['quantiteDeposee', 'quantiteReservee', 'quantiteRetiree', 'quantiteRestante'] as const

// Stock promo isolé du stock normal (§4.1) : un tableau par campagne, à partir des dépôts reçus.
export default function StockPromo() {
  const { t, nombre } = useI18n()
  // ponytail: une page de 200 dépôts reçus, assez pour un grossiste du pilote ; paginer au-delà.
  const requete = $api.useQuery('get', '/v1/depots', { params: { query: { statut: 'RECU', taille: 200 } } }, { refetchInterval: 15_000 })
  const nom = useNomProduit(requete.data?.contenu.flatMap((d) => d.lignes.map((l) => l.produitId)) ?? [])
  const titres = { quantiteDeposee: 'depot.depose', quantiteReservee: 'depot.reserve', quantiteRetiree: 'depot.retire', quantiteRestante: 'depot.restant' } as const

  return (
    <section className="space-y-4">
      <h1 className="text-2xl font-semibold">{t('stock.titre')}</h1>
      <p className="text-muted-foreground text-sm">{t('stock.aide')}</p>
      <Etat requete={requete}>
        {(d) =>
          d.contenu.map((depot) => (
            <Card key={depot.id}>
              <CardHeader>
                <CardTitle>
                  <Link to={`/depots/${depot.id}`} className="hover:underline">{depot.campagne.nom}</Link>
                  {depot.marque && <span className="text-muted-foreground ms-2 text-sm font-normal">{depot.marque.nom}</span>}
                </CardTitle>
              </CardHeader>
              <CardContent>
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>{t('depot.produit')}</TableHead>
                      {COLONNES.map((c) => <TableHead key={c} className="text-end">{t(titres[c])}</TableHead>)}
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {depot.lignes.map((l) => (
                      <TableRow key={l.produitId}>
                        <TableCell>{nom(l.produitId)}</TableCell>
                        {COLONNES.map((c) => <TableCell key={c} className="text-end">{nombre(l[c])}</TableCell>)}
                      </TableRow>
                    ))}
                  </TableBody>
                  {depot.lignes.length > 1 && (
                    <TableFooter>
                      <TableRow>
                        <TableCell>{t('stock.totalCampagne')}</TableCell>
                        {COLONNES.map((c) => (
                          <TableCell key={c} className="text-end">{nombre(depot.lignes.reduce((s, l) => s + l[c], 0))}</TableCell>
                        ))}
                      </TableRow>
                    </TableFooter>
                  )}
                </Table>
              </CardContent>
            </Card>
          ))
        }
      </Etat>
    </section>
  )
}

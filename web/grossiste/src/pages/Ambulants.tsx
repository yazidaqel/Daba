import { useState } from 'react'
import { $api } from '@/api/client'
import { Etat, Pagination } from '@/components/communs'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { useI18n } from '@/i18n'

// Ambulants rattachés au grossiste (§4.3) : volume et régularité.
export default function Ambulants() {
  const { t, date, nombre } = useI18n()
  const [page, setPage] = useState(0)
  const requete = $api.useQuery('get', '/v1/ambulants', { params: { query: { page, taille: 50 } } })
  return (
    <section className="space-y-4">
      <h1 className="text-2xl font-semibold">{t('ambulants.titre')}</h1>
      <p className="text-muted-foreground text-sm">{t('ambulants.aide')}</p>
      <Etat requete={requete}>
        {(d) => (
          <>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t('ambulants.nom')}</TableHead>
                  <TableHead>{t('ambulants.telephone')}</TableHead>
                  <TableHead className="text-end">{t('ambulants.bons')}</TableHead>
                  <TableHead className="text-end">{t('ambulants.volume')}</TableHead>
                  <TableHead className="text-end">{t('ambulants.bons30')}</TableHead>
                  <TableHead>{t('ambulants.dernier')}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {d.contenu.map((a) => (
                  <TableRow key={a.id}>
                    <TableCell className="font-medium">{a.nom}</TableCell>
                    <TableCell><bdi dir="ltr">{a.telephone ?? '—'}</bdi></TableCell>
                    <TableCell className="text-end">{nombre(a.nombreBons)}</TableCell>
                    <TableCell className="text-end">{nombre(a.quantiteTotale)}</TableCell>
                    <TableCell className="text-end">{nombre(a.bons30Jours)}</TableCell>
                    <TableCell>{a.dernierBonLe ? date(a.dernierBonLe) : '—'}</TableCell>
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

import { useState } from 'react'
import { api, ok, type S } from '@/api/client'
import { useI18n } from '@/i18n'
import { useCharge } from '@/lib/useCharge'
import { Erreur } from '@/components/Erreur'
import { StatutCampagne } from '@/components/Statut'
import { Button } from '@/components/ui/button'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'

const STATUTS: S['StatutCampagne'][] = ['BROUILLON', 'PROPOSEE', 'ACTIVE', 'EPUISEE', 'EXPIREE', 'CLOTUREE']

export function Campagnes() {
  const { t, libelle, date, nombre } = useI18n()
  const [statut, setStatut] = useState<S['StatutCampagne'] | ''>('')
  const [page, setPage] = useState(0)
  const { data, erreur, chargement } = useCharge(
    () => api.GET('/v1/campagnes', { params: { query: { statut: statut || undefined, page, taille: 20 } } }).then(ok),
    [statut, page],
  )

  return (
    <section className="space-y-4">
      <div className="flex flex-wrap items-center gap-2">
        <h1 className="me-auto text-xl font-semibold">{t('campagnes.titre')}</h1>
        <select
          aria-label={t('campagnes.filtreStatut')}
          className="h-9 rounded-md border bg-background px-2 text-sm"
          value={statut}
          onChange={(e) => (setStatut(e.target.value as S['StatutCampagne']), setPage(0))}
        >
          <option value="">{t('campagnes.tousStatuts')}</option>
          {STATUTS.map((s) => (
            <option key={s} value={s}>
              {t(`statut.${s}`)}
            </option>
          ))}
        </select>
        <Button asChild>
          <a href="#/campagnes/nouvelle">{t('campagnes.nouvelle')}</a>
        </Button>
      </div>
      <Erreur erreur={erreur} />
      {chargement && <p className="text-muted-foreground">{t('commun.chargement')}</p>}
      {data && data.contenu.length === 0 && <p className="text-muted-foreground">{t('campagnes.aucune')}</p>}
      {data && data.contenu.length > 0 && (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>{t('campagne.nom')}</TableHead>
              <TableHead>{t('campagne.statut')}</TableHead>
              <TableHead>{t('campagne.periode')}</TableHead>
              <TableHead>{t('entonnoir.depose')}</TableHead>
              <TableHead>{t('entonnoir.reserve')}</TableHead>
              <TableHead>{t('entonnoir.retire')}</TableHead>
              <TableHead>{t('entonnoir.revendu')}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {data.contenu.map((c) => (
              <TableRow key={c.id} className="cursor-pointer" onClick={() => (location.hash = `#/campagnes/${c.id}`)}>
                <TableCell>
                  <a href={`#/campagnes/${c.id}`} className="font-medium hover:underline">
                    {libelle(c.nom)}
                  </a>
                </TableCell>
                <TableCell>
                  <StatutCampagne statut={c.statut} />
                </TableCell>
                <TableCell>
                  {date(c.debut)} – {date(c.fin)}
                </TableCell>
                <TableCell>{nombre(c.entonnoir.depose)}</TableCell>
                <TableCell>{nombre(c.entonnoir.reserve)}</TableCell>
                <TableCell>{nombre(c.entonnoir.retire)}</TableCell>
                <TableCell>{nombre(c.entonnoir.revendu)}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}
      {data && data.total > (page + 1) * data.taille && (
        <Button variant="outline" onClick={() => setPage(page + 1)}>
          {t('commun.suivant')}
        </Button>
      )}
      {page > 0 && (
        <Button variant="outline" className="ms-2" onClick={() => setPage(page - 1)}>
          {t('commun.precedent')}
        </Button>
      )}
    </section>
  )
}

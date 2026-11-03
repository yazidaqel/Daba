import { useState, type FormEvent } from 'react'
import { $api } from '@/api/client'
import { Etat, Pagination, SELECT } from '@/components/communs'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { useI18n } from '@/i18n'
import type { Cle } from '@/i18n/fr'

const OPERATIONS = [
  'CONFIGURATION_CANAUX', 'ORGANISATION_CREEE', 'UTILISATEUR_AJOUTE', 'UTILISATEUR_DESACTIVE',
  'UTILISATEUR_REACTIVE', 'APPAREIL_REVOQUE', 'IMPORT_HANOUTS', 'IMPORT_PRODUITS',
] as const

type Filtres = { depuis: string; jusqua: string; auteurId: string; operation: string }
const VIDE: Filtres = { depuis: '', jusqua: '', auteurId: '', operation: '' }
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

// Jour local → instant ISO ; la borne « jusqu'au » est incluse à l'écran, exclue par l'API (lendemain 0 h).
const debutJour = (jour: string, decalage = 0) => {
  if (!jour) return undefined
  const d = new Date(`${jour}T00:00:00`)
  d.setDate(d.getDate() + decalage)
  return d.toISOString()
}

export default function Audit() {
  const { t, date } = useI18n()
  const [saisie, setSaisie] = useState(VIDE)
  const [filtres, setFiltres] = useState(VIDE)
  const [page, setPage] = useState(0)
  const requete = $api.useQuery('get', '/v1/admin/audit', {
    params: {
      query: {
        depuis: debutJour(filtres.depuis),
        jusqua: debutJour(filtres.jusqua, 1),
        auteurId: filtres.auteurId || undefined,
        operation: filtres.operation || undefined,
        page,
        taille: 50,
      },
    },
  })
  const appliquer = (f: Filtres) => (setSaisie(f), setFiltres(f), setPage(0))
  const libelleOp = (op: string) => ((OPERATIONS as readonly string[]).includes(op) ? t(`op.${op}` as Cle) : op)
  const auteurInvalide = saisie.auteurId !== '' && !UUID.test(saisie.auteurId.trim())

  function soumettre(e: FormEvent) {
    e.preventDefault()
    if (!auteurInvalide) appliquer({ ...saisie, auteurId: saisie.auteurId.trim() })
  }

  return (
    <section className="space-y-4">
      <h1 className="text-2xl font-semibold">{t('audit.titre')}</h1>
      <p className="text-muted-foreground text-sm">{t('audit.aide')}</p>
      <form onSubmit={soumettre} className="flex flex-wrap items-end gap-2">
        <div className="space-y-1">
          <Label htmlFor="a-depuis">{t('audit.depuis')}</Label>
          <Input id="a-depuis" type="date" value={saisie.depuis} onChange={(e) => setSaisie({ ...saisie, depuis: e.target.value })} />
        </div>
        <div className="space-y-1">
          <Label htmlFor="a-jusqua">{t('audit.jusqua')}</Label>
          <Input id="a-jusqua" type="date" value={saisie.jusqua} onChange={(e) => setSaisie({ ...saisie, jusqua: e.target.value })} />
        </div>
        <div className="space-y-1">
          <Label htmlFor="a-op">{t('audit.operation')}</Label>
          <select id="a-op" className={SELECT} value={saisie.operation} onChange={(e) => setSaisie({ ...saisie, operation: e.target.value })}>
            <option value="">{t('audit.toutes')}</option>
            {OPERATIONS.map((o) => <option key={o} value={o}>{libelleOp(o)}</option>)}
          </select>
        </div>
        <div className="space-y-1">
          <Label htmlFor="a-auteur">{t('audit.auteurId')}</Label>
          <Input id="a-auteur" dir="ltr" className="w-80" aria-invalid={auteurInvalide} value={saisie.auteurId} onChange={(e) => setSaisie({ ...saisie, auteurId: e.target.value })} />
        </div>
        <Button type="submit" variant="outline" disabled={auteurInvalide}>{t('audit.filtrer')}</Button>
        <Button type="button" variant="ghost" onClick={() => appliquer(VIDE)}>{t('audit.effacer')}</Button>
      </form>
      {auteurInvalide && <p className="text-destructive text-sm">{t('audit.auteurInvalide')}</p>}
      <Etat requete={requete}>
        {(d) => (
          <>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t('audit.le')}</TableHead>
                  <TableHead>{t('audit.operation')}</TableHead>
                  <TableHead>{t('audit.auteur')}</TableHead>
                  <TableHead>{t('audit.details')}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {d.contenu.map((e) => (
                  <TableRow key={e.id} className="align-top">
                    <TableCell>{date(e.le)}</TableCell>
                    <TableCell>{libelleOp(e.operation)}</TableCell>
                    <TableCell>
                      {e.auteur ? (
                        <Button variant="link" size="sm" className="h-auto px-0" title={t('audit.filtrerAuteur')} onClick={() => appliquer({ ...filtres, auteurId: e.auteur!.id })}>
                          <bdi>{e.auteur.nom}</bdi>
                        </Button>
                      ) : '—'}
                    </TableCell>
                    <TableCell className="whitespace-normal">
                      {e.details && Object.keys(e.details).length > 0 ? (
                        <details>
                          <summary className="cursor-pointer">{t('audit.voir')}</summary>
                          <pre dir="ltr" className="bg-muted mt-1 max-w-xl overflow-x-auto rounded-md p-2 text-start text-xs">{JSON.stringify(e.details, null, 2)}</pre>
                        </details>
                      ) : '—'}
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

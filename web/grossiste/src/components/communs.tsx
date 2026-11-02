import type { ReactNode } from 'react'
import type { S } from '@/api/client'
import { messageErreur } from '@/api/erreurs'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { useI18n } from '@/i18n'

type Requete<T> = { data?: T; error: unknown; isPending: boolean; refetch: () => unknown }

// Chargement / erreur / vide / contenu pour une requête.
export function Etat<T>({ requete, children }: { requete: Requete<T>; children: (d: T) => ReactNode }) {
  const { t } = useI18n()
  if (requete.isPending) return <p className="text-muted-foreground">{t('app.chargement')}</p>
  if (requete.error || !requete.data)
    return <Erreur erreur={requete.error} action={<Button size="sm" variant="outline" onClick={() => requete.refetch()}>{t('app.reessayer')}</Button>} />
  const d = requete.data as T & { contenu?: unknown[] }
  if (Array.isArray(d.contenu) && d.contenu.length === 0) return <p className="text-muted-foreground">{t('app.vide')}</p>
  return children(requete.data)
}

export function Erreur({ erreur, action }: { erreur: unknown; action?: ReactNode }) {
  const { t } = useI18n()
  return (
    <Alert variant="destructive">
      <AlertDescription className="flex items-center gap-3">
        {messageErreur(erreur, t)}
        {action}
      </AlertDescription>
    </Alert>
  )
}

export function Succes({ children }: { children: ReactNode }) {
  return (
    <Alert>
      <AlertDescription>{children}</AlertDescription>
    </Alert>
  )
}

export function StatutDepot({ statut }: { statut: S['StatutDepot'] }) {
  const { t } = useI18n()
  const variante = statut === 'PROPOSE' || statut === 'ACCEPTE' ? 'default' : statut === 'REFUSE' ? 'destructive' : 'secondary'
  return <Badge variant={variante}>{t(`statut.${statut}`)}</Badge>
}

export function Pagination({ meta, onPage }: { meta: S['PageMeta']; onPage: (p: number) => void }) {
  const { t } = useI18n()
  const pages = Math.max(1, Math.ceil(meta.total / meta.taille))
  if (pages === 1) return null
  return (
    <div className="flex items-center justify-end gap-2 text-sm">
      <Button size="sm" variant="outline" disabled={meta.page === 0} onClick={() => onPage(meta.page - 1)}>
        {t('app.precedent')}
      </Button>
      <span>{t('app.page', { page: meta.page + 1, pages })}</span>
      <Button size="sm" variant="outline" disabled={meta.page + 1 >= pages} onClick={() => onPage(meta.page + 1)}>
        {t('app.suivant')}
      </Button>
    </div>
  )
}

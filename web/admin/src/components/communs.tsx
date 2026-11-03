import type { ReactNode } from 'react'
import { CircleAlert, CircleCheck, type LucideIcon } from 'lucide-react'
import type { Problem, S } from '@/api/client'
import { messageErreur } from '@/api/erreurs'
import { Alert, AlertDescription } from '@/components/ui/alert'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from '@/components/ui/alert-dialog'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { useI18n } from '@/i18n'

// Classe des <select> natifs, alignée sur Input.
export const SELECT = 'border-input bg-background h-8 rounded-lg border px-2 text-sm'

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

// Erreur d'API : message décidé sur le type, puis le détail champ par champ d'un 400 (textes du serveur).
export function Erreur({ erreur, action }: { erreur: unknown; action?: ReactNode }) {
  const { t } = useI18n()
  const champs = (erreur as Problem | undefined)?.erreurs ?? []
  return (
    <Alert variant="destructive">
      <CircleAlert aria-hidden />
      <AlertDescription className="space-y-1">
        <div className="flex flex-wrap items-center gap-3">
          {messageErreur(erreur, t)}
          {action}
        </div>
        {champs.length > 0 && (
          <ul className="list-disc ps-5">
            {champs.map((c) => (
              <li key={c.champ + c.message}>
                <code dir="ltr">{c.champ}</code> : {c.message}
              </li>
            ))}
          </ul>
        )}
      </AlertDescription>
    </Alert>
  )
}

export function Succes({ children }: { children: ReactNode }) {
  return (
    <Alert variant="success">
      <CircleCheck aria-hidden />
      <AlertDescription>{children}</AlertDescription>
    </Alert>
  )
}

// Un état s'affiche toujours avec une icône et un libellé, jamais par la couleur seule (charte graphique).
export type Variante = 'secondary' | 'outline' | 'destructive' | 'success' | 'warning'
export function Etiquette({ icone: Icone, variante, children }: { icone: LucideIcon; variante: Variante; children: ReactNode }) {
  return (
    <Badge variant={variante}>
      <Icone aria-hidden />
      {children}
    </Badge>
  )
}

// Confirmation d'une action sensible : rien ne part tant que l'administrateur n'a pas confirmé.
export function Confirmer({
  declencheur,
  titre,
  texte,
  confirmer,
  onConfirmer,
  destructif = true,
}: {
  declencheur: ReactNode
  titre: string
  texte: ReactNode
  confirmer: string
  onConfirmer: () => void
  destructif?: boolean
}) {
  const { t } = useI18n()
  return (
    <AlertDialog>
      <AlertDialogTrigger asChild>{declencheur}</AlertDialogTrigger>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>{titre}</AlertDialogTitle>
          <AlertDialogDescription asChild>
            <div className="space-y-2">{texte}</div>
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel>{t('app.annuler')}</AlertDialogCancel>
          <AlertDialogAction variant={destructif ? 'destructive' : 'default'} onClick={onConfirmer}>{confirmer}</AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
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

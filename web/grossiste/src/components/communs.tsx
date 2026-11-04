import { useState, type ReactNode } from 'react'
import { Archive, ArrowLeftRight, CircleAlert, CircleCheck, CircleSlash, CircleX, Clock, Eye, EyeOff, HandCoins, Inbox, Info, PackageCheck, Tag, Truck, type LucideIcon } from 'lucide-react'
import type { S } from '@/api/client'
import { messageErreur } from '@/api/erreurs'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
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
      <CircleAlert aria-hidden />
      <AlertDescription className="flex items-center gap-3">
        {messageErreur(erreur, t)}
        {action}
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
type Variante = 'secondary' | 'outline' | 'destructive' | 'success' | 'warning' | 'promo'
function Etiquette({ icone: Icone, variante, children }: { icone: LucideIcon; variante: Variante; children: ReactNode }) {
  return (
    <Badge variant={variante}>
      <Icone aria-hidden />
      {children}
    </Badge>
  )
}

const DEPOT: Record<S['StatutDepot'], [LucideIcon, Variante]> = {
  PROPOSE: [Inbox, 'promo'],
  CONTRE_PROPOSE: [ArrowLeftRight, 'secondary'],
  ACCEPTE: [Truck, 'secondary'],
  REFUSE: [CircleX, 'destructive'],
  RECU: [CircleCheck, 'success'],
  CLOTURE: [Archive, 'outline'],
}

export function StatutDepot({ statut }: { statut: S['StatutDepot'] }) {
  const { t } = useI18n()
  const [icone, variante] = DEPOT[statut]
  return <Etiquette icone={icone} variante={variante}>{t(`statut.${statut}`)}</Etiquette>
}

const RESERVATION: Record<S['StatutReservation'], [LucideIcon, Variante]> = {
  RESERVEE: [Clock, 'secondary'],
  RETIREE: [PackageCheck, 'success'],
  EXPIREE: [CircleSlash, 'outline'],
  REVENDUE: [HandCoins, 'success'],
}

export function StatutReservation({ statut }: { statut: S['StatutReservation'] }) {
  const { t } = useI18n()
  const [icone, variante] = RESERVATION[statut]
  return <Etiquette icone={icone} variante={variante}>{t(`resa.${statut}`)}</Etiquette>
}

// Badge de campagne : le safran (promo) est réservé aux promotions.
export function StatutCampagne({ statut }: { statut: S['StatutCampagne'] }) {
  const { t } = useI18n()
  return <Etiquette icone={Tag} variante="promo">{t('depot.campagne', { statut: t(`campagne.${statut}`) })}</Etiquette>
}

export function Charge({ charge }: { charge: boolean }) {
  const { t } = useI18n()
  return charge ? <Etiquette icone={PackageCheck} variante="success">{t('bons.charge')}</Etiquette> : <Etiquette icone={Clock} variante="outline">{t('bons.nonCharge')}</Etiquette>
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

// Champ mot de passe avec bouton afficher/masquer (le libellé du bouton reste le même, son état passe par aria-pressed).
export function ChampMotDePasse({ id, label, autoComplete, value, onChange, aide }: {
  id: string
  label: string
  autoComplete: 'current-password' | 'new-password'
  value: string
  onChange: (v: string) => void
  aide?: string
}) {
  const { t } = useI18n()
  const [visible, setVisible] = useState(false)
  return (
    <div className="space-y-1.5">
      <Label htmlFor={id}>{label}</Label>
      <div className="relative">
        <Input
          id={id}
          type={visible ? 'text' : 'password'}
          autoComplete={autoComplete}
          autoCapitalize="off"
          spellCheck={false}
          className="pe-9"
          value={value}
          onChange={(e) => onChange(e.target.value)}
          aria-describedby={aide ? `${id}-aide` : undefined}
          required
        />
        <Button
          type="button"
          variant="ghost"
          size="icon-sm"
          className="absolute end-0.5 top-0.5"
          aria-label={t('login.afficher')}
          aria-pressed={visible}
          aria-controls={id}
          onClick={() => setVisible(!visible)}
        >
          {visible ? <EyeOff aria-hidden /> : <Eye aria-hidden />}
        </Button>
      </div>
      {aide && <p id={`${id}-aide`} className="text-muted-foreground text-sm">{aide}</p>}
    </div>
  )
}

// Message d'information (bascule de méthode décidée par le serveur, par exemple).
export function Information({ children }: { children: ReactNode }) {
  return (
    <Alert className="bg-primary-soft border-primary/30 text-foreground">
      <Info aria-hidden className="text-primary" />
      <AlertDescription>{children}</AlertDescription>
    </Alert>
  )
}

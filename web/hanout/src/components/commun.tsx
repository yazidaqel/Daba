import { CircleAlert, Package, RefreshCw } from 'lucide-react'
import type { ReactNode } from 'react'
import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { useI18n } from '@/i18n'
import { cn } from '@/lib/utils'
import type { Cle } from '@/textes'

export const champ =
  'w-full min-h-14 rounded-xl border-2 border-input bg-card px-4 text-lg outline-none focus-visible:border-ring'

export const carte = 'rounded-2xl border-2 border-border bg-card p-4'

/** Erreur réseau ou serveur, selon ce que dit le téléphone. */
export const cleErreur = (): Cle => (navigator.onLine ? 'erreur.generique' : 'erreur.reseau')

export function Etat({ chargement, erreur, recharger }: { chargement: boolean; erreur: boolean; recharger: () => void }) {
  const { t } = useI18n()
  if (erreur && !chargement)
    return (
      <div role="alert" className={cn(carte, 'space-y-3 text-center')}>
        <p className="font-semibold">{t(cleErreur())}</p>
        <Button variant="outline" onClick={recharger}>
          <RefreshCw />
          {t('reessayer')}
        </Button>
      </div>
    )
  return (
    <p className="p-6 text-center text-muted-foreground" aria-live="polite">
      {t('chargement')}
    </p>
  )
}

export function Vide() {
  const { t } = useI18n()
  return <p className="p-6 text-center text-muted-foreground">{t('vide')}</p>
}

export function Alerte({ cle }: { cle?: Cle }) {
  const { t } = useI18n()
  if (!cle) return null
  return (
    <p role="alert" className="flex items-center gap-2 font-semibold text-danger">
      <CircleAlert className="shrink-0" />
      {t(cle)}
    </p>
  )
}

/** Somme due (solde, crédit, impayé) : `danger` avec une icône, jamais la couleur seule. */
export function MontantDu({ du, children, className }: { du: boolean; children: ReactNode; className?: string }) {
  return (
    <span className={cn('inline-flex items-center gap-1', du && 'text-danger', className)}>
      {du && <CircleAlert className="size-[1em] shrink-0" aria-hidden />}
      {children}
    </span>
  )
}

export function PhotoProduit({ url, className }: { url?: string; className?: string }) {
  const [echec, setEchec] = useState(false)
  const taille = cn('size-16 shrink-0 rounded-lg bg-muted', className)
  if (!url || echec)
    return (
      <span className={cn(taille, 'grid place-items-center')}>
        <Package className="text-muted-foreground" />
      </span>
    )
  return (
    <img
      src={url}
      alt=""
      loading="lazy"
      decoding="async"
      width={64}
      height={64}
      onError={() => setEchec(true)}
      className={cn(taille, 'object-cover')}
    />
  )
}

import type { S } from '@/api/client'
import { useI18n } from '@/i18n'
import { Badge } from '@/components/ui/badge'

const COULEUR: Record<string, string> = {
  BROUILLON: 'bg-muted text-foreground',
  PROPOSEE: 'bg-amber-100 text-amber-900',
  PROPOSE: 'bg-amber-100 text-amber-900',
  CONTRE_PROPOSE: 'bg-orange-100 text-orange-900',
  ACCEPTE: 'bg-sky-100 text-sky-900',
  ACTIVE: 'bg-emerald-100 text-emerald-900',
  RECU: 'bg-emerald-100 text-emerald-900',
  EPUISEE: 'bg-violet-100 text-violet-900',
  EXPIREE: 'bg-zinc-200 text-zinc-800',
  REFUSE: 'bg-red-100 text-red-900',
  CLOTUREE: 'bg-zinc-200 text-zinc-800',
  CLOTURE: 'bg-zinc-200 text-zinc-800',
}

export function StatutCampagne({ statut }: { statut: S['StatutCampagne'] }) {
  const { t } = useI18n()
  return <Badge className={COULEUR[statut]}>{t(`statut.${statut}`)}</Badge>
}

export function StatutDepot({ statut }: { statut: S['StatutDepot'] }) {
  const { t } = useI18n()
  return <Badge className={COULEUR[statut]}>{t(`depot.${statut}`)}</Badge>
}

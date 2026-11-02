import type { LucideIcon } from 'lucide-react'
import { Archive, ArrowLeftRight, Check, CircleCheck, CircleX, Clock, PackageX, PencilLine, Send, Tag } from 'lucide-react'
import type { S } from '@/api/client'
import { useI18n } from '@/i18n'
import { Badge } from '@/components/ui/badge'

// Charte graphique : un état s'affiche toujours avec une icône et un libellé, jamais par la couleur seule.
const NEUTRE = 'bg-muted text-muted-foreground border-border'
const INFO = 'bg-primary-soft text-primary'
const STATUTS: Record<string, [LucideIcon, string]> = {
  BROUILLON: [PencilLine, NEUTRE],
  PROPOSEE: [Send, INFO],
  PROPOSE: [Send, INFO],
  CONTRE_PROPOSE: [ArrowLeftRight, 'bg-warning-soft text-warning'],
  ACCEPTE: [Check, INFO],
  // Campagne active = promotion en cours : safran, réservé aux promos.
  ACTIVE: [Tag, 'bg-promo-soft text-promo-soft-foreground'],
  RECU: [CircleCheck, 'bg-success-soft text-success'],
  EPUISEE: [PackageX, NEUTRE],
  EXPIREE: [Clock, NEUTRE],
  REFUSE: [CircleX, 'bg-destructive-soft text-destructive'],
  CLOTUREE: [Archive, NEUTRE],
  CLOTURE: [Archive, NEUTRE],
}

function Etat({ code, libelle }: { code: string; libelle: string }) {
  const [Icone, ton] = STATUTS[code]
  return (
    <Badge className={ton}>
      <Icone aria-hidden />
      {libelle}
    </Badge>
  )
}

export function StatutCampagne({ statut }: { statut: S['StatutCampagne'] }) {
  const { t } = useI18n()
  return <Etat code={statut} libelle={t(`statut.${statut}`)} />
}

export function StatutDepot({ statut }: { statut: S['StatutDepot'] }) {
  const { t } = useI18n()
  return <Etat code={statut} libelle={t(`depot.${statut}`)} />
}

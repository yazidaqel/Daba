import { CircleDashed, Clock, FileText, PackageCheck, Phone, SearchX, Truck, UserCheck } from 'lucide-react'
import { useEffect } from 'react'
import { api, type S } from '@/api/client'
import { carte, Etat, PhotoProduit } from '@/components/commun'
import { Button } from '@/components/ui/button'
import { useI18n } from '@/i18n'
import { cn } from '@/lib/utils'
import { useApi } from '@/outils'
import { Lien } from '@/router'

type Statut = S['StatutDemande']
const ETAPES: Statut[] = ['EN_ATTENTE', 'ACCEPTEE', 'EN_ROUTE', 'LIVREE']
const TERMINAUX: Statut[] = ['LIVREE', 'SANS_REPONSE']
// Pas de push dans la PWA (§6) : on interroge le serveur tant que la demande avance.
const SONDAGE_MS = 15_000

// Un état = une icône et un libellé, jamais la couleur seule (charte graphique).
const ETATS: Record<Statut, { Icone: typeof Clock; couleur: string }> = {
  EN_ATTENTE: { Icone: Clock, couleur: 'bg-muted text-foreground' },
  ACCEPTEE: { Icone: UserCheck, couleur: 'bg-primary-soft text-primary' },
  EN_ROUTE: { Icone: Truck, couleur: 'bg-primary-soft text-primary' },
  LIVREE: { Icone: PackageCheck, couleur: 'bg-success-soft text-success' },
  SANS_REPONSE: { Icone: SearchX, couleur: 'bg-danger-soft text-danger' },
}

export function BadgeStatut({ statut }: { statut: Statut }) {
  const { t } = useI18n()
  const { Icone, couleur } = ETATS[statut]
  return (
    <span className={cn('inline-flex shrink-0 items-center gap-1 rounded-full px-3 py-1 text-sm font-semibold', couleur)}>
      <Icone className="size-4" aria-hidden />
      {t(`statut.${statut}`)}
    </span>
  )
}

/** Suivi d'une demande de dépannage (§7.1). */
export function Demande({ id }: { id: string }) {
  const { t, libelle, date } = useI18n()
  const r = useApi(() => api.GET('/v1/demandes-depannage/{demandeId}', { params: { path: { demandeId: id } } }), [id])
  const statut = r.data?.statut
  const { recharger } = r

  useEffect(() => {
    if (!statut || TERMINAUX.includes(statut)) return
    const h = setInterval(() => document.hidden || recharger(), SONDAGE_MS)
    return () => clearInterval(h)
  }, [statut, recharger])

  if (!r.data) return <Etat {...r} />
  const d = r.data
  const rang = ETAPES.indexOf(d.statut)

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-4">
        <PhotoProduit url={d.produit?.photoUrl ?? d.photoUrl} className="size-20" />
        <div>
          <h1 className="text-xl font-bold">{d.produit ? libelle(d.produit.libelle) : t('rupture.produitPhoto')}</h1>
          <p className="text-muted-foreground">{t('quantite', { n: d.quantite })}</p>
        </div>
      </div>

      {d.statut === 'SANS_REPONSE' ? (
        <output className={cn(carte, 'block space-y-3 border-danger')}>
          <p className="flex items-center gap-2 text-lg font-bold text-danger">
            <SearchX />
            {t('statut.SANS_REPONSE')}
          </p>
          <p>{t('demande.sansReponseAide')}</p>
          <Button asChild size="lg" className="w-full">
            <Lien vers="/">{t('demande.redemander')}</Lien>
          </Button>
        </output>
      ) : (
        <ol className="space-y-3" aria-live="polite">
          {ETAPES.map((s, i) => {
            const fait = i <= rang
            const le = d.historique.find((h) => h.statut === s)?.le ?? (i === 0 ? d.creeLe : undefined)
            const libelleEtape = i === 0 && rang > 0 ? t('demande.envoyee') : t(`statut.${s}`)
            const { Icone } = ETATS[s]
            return (
              <li key={s} className={cn('flex items-center gap-3', !fait && 'text-muted-foreground')}>
                {fait ? <Icone className="size-8 shrink-0 text-success" /> : <CircleDashed className="size-8 shrink-0" />}
                <span className="flex-1 font-semibold">
                  {libelleEtape}
                  {i === 0 && rang === 0 && d.rayonElargi && (
                    <span className="block text-sm font-normal">{t('demande.rayonElargi')}</span>
                  )}
                </span>
                {fait && le && <span className="text-sm">{date(le)}</span>}
              </li>
            )
          })}
        </ol>
      )}

      {d.ambulant && (
        <div className={cn(carte, 'flex items-center gap-3')}>
          <p className="flex-1 font-semibold">{t('demande.acceptePar', { nom: d.ambulant.nom })}</p>
          <Button asChild variant="outline">
            <a href={`tel:${d.ambulant.telephone}`}>
              <Phone />
              {t('demande.appeler')}
            </a>
          </Button>
        </div>
      )}

      {d.documentId && (
        <Button asChild size="lg" className="w-full">
          <Lien vers={`/documents/${d.documentId}`}>
            <FileText />
            {t('demande.voirBon')}
          </Lien>
        </Button>
      )}
    </div>
  )
}

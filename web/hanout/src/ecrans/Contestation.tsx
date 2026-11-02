import { useState } from 'react'
import { Scale } from 'lucide-react'
import { api, type S } from '@/api/client'
import { Alerte, champ, cleErreur } from '@/components/commun'
import { Button, buttonVariants } from '@/components/ui/button'
import { useI18n } from '@/i18n'
import { cn } from '@/lib/utils'
import { useApi } from '@/outils'
import type { Cle } from '@/textes'

type Motif = S['ContestationSaisie']['motif']
const MOTIFS: Motif[] = ['MONTANT', 'QUANTITE', 'NON_RECU', 'DEJA_PAYE', 'AUTRE']

/** Contestation OUVERTE sur cette cible (une seule possible), pour ne pas en proposer une seconde. */
export function useContestationOuverte(cibleId: string) {
  const r = useApi(
    () => api.GET('/v1/contestations', { params: { query: { cibleId, statut: 'OUVERTE', taille: 1 } } }),
    [cibleId],
  )
  return { ...r, ouverte: r.data?.contenu[0] }
}

export function ContestationEnCours({ c }: { c: S['Contestation'] }) {
  const { t, date } = useI18n()
  return (
    <output className="flex items-center gap-2 font-semibold text-primary">
      <Scale className="shrink-0" />
      {t('contestation.enCours', { motif: t(`motif.${c.motif}`), date: date(c.creeLe) })}
    </output>
  )
}

/** Contestation d'un document ou d'un solde ; l'ambulant est notifié (§6). */
export function Contestation({
  cible,
  cibleId,
  onFini,
}: Pick<S['ContestationSaisie'], 'cible' | 'cibleId'> & { onFini: () => void }) {
  const { t } = useI18n()
  const [id] = useState(() => crypto.randomUUID())
  const [motif, setMotif] = useState<Motif>()
  const [commentaire, setCommentaire] = useState('')
  const [occupe, setOccupe] = useState(false)
  const [erreur, setErreur] = useState<Cle>()

  async function envoyer(m: Motif) {
    setOccupe(true)
    setErreur(undefined)
    try {
      const { response } = await api.POST('/v1/contestations', {
        body: { id, cible, cibleId, motif: m, commentaire: commentaire.trim() || undefined },
      })
      // 409 : succès seulement si une contestation est bien ouverte sur cette cible (le parent l'affiche).
      const ouverte =
        response.status === 409 &&
        (await api.GET('/v1/contestations', { params: { query: { cibleId, statut: 'OUVERTE', taille: 1 } } })).data
          ?.contenu.length
      if (response.ok || ouverte) onFini()
      else setErreur('erreur.generique')
    } catch {
      setErreur(cleErreur())
    } finally {
      setOccupe(false)
    }
  }

  return (
    <form
      className="space-y-4"
      onSubmit={(e) => {
        e.preventDefault()
        if (motif) void envoyer(motif)
      }}
    >
      <h2 className="text-xl font-bold">{t('contestation.titre')}</h2>
      <fieldset className="grid gap-2">
        <legend className="sr-only">{t('contestation.titre')}</legend>
        {MOTIFS.map((m) => (
          <label
            key={m}
            className={cn(
              buttonVariants({ variant: 'outline' }),
              motif === m && 'border-primary bg-primary-soft text-primary',
              'cursor-pointer justify-start has-focus-visible:ring-[3px] has-focus-visible:ring-ring/50',
            )}
          >
            <input type="radio" name="motif" className="sr-only" checked={motif === m} onChange={() => setMotif(m)} />
            {t(`motif.${m}`)}
          </label>
        ))}
      </fieldset>
      <textarea
        aria-label={t('contestation.commentaire')}
        placeholder={t('contestation.commentaire')}
        maxLength={500}
        rows={3}
        value={commentaire}
        onChange={(e) => setCommentaire(e.target.value)}
        className={`${champ} py-3`}
      />
      <Alerte cle={erreur} />
      <div className="grid grid-cols-2 gap-3">
        <Button type="button" variant="ghost" onClick={() => onFini()}>
          {t('annuler')}
        </Button>
        <Button type="submit" disabled={!motif || occupe}>
          {t('envoyer')}
        </Button>
      </div>
    </form>
  )
}

import { useState } from 'react'
import { contester, MOTIFS, type ContestationSaisie, type Motif } from '@/api/a-venir'
import { Alerte, champ, cleErreur } from '@/components/commun'
import { Button, buttonVariants } from '@/components/ui/button'
import { useI18n } from '@/i18n'
import { cn } from '@/lib/utils'
import type { Cle } from '@/textes'

/** Contestation d'un document ou d'un solde ; l'ambulant est notifié (§6). */
export function Contestation({
  cible,
  cibleId,
  onFini,
}: Pick<ContestationSaisie, 'cible' | 'cibleId'> & { onFini: (envoye: boolean) => void }) {
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
      if (await contester({ id, cible, cibleId, motif: m, commentaire: commentaire.trim() || undefined })) onFini(true)
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
              buttonVariants({ variant: motif === m ? 'default' : 'outline' }),
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
        <Button type="button" variant="ghost" onClick={() => onFini(false)}>
          {t('annuler')}
        </Button>
        <Button type="submit" disabled={!motif || occupe}>
          {t('envoyer')}
        </Button>
      </div>
    </form>
  )
}

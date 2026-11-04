import { useState } from 'react'
import { useI18n } from '@/i18n'
import { changerMotDePasse, texteAvis, type Refus } from '@/connexion'
import { ChampMotDePasse } from '@/components/ChampMotDePasse'
import { Erreur, Message } from '@/components/Erreur'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'

/** « Mon mot de passe » : PUT /v1/moi/mot-de-passe (les autres sessions du compte se ferment). */
export function MotDePasse() {
  const { t } = useI18n()
  const [actuel, setActuel] = useState('')
  const [nouveau, setNouveau] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [resultat, setResultat] = useState<Refus | 'change' | null>(null)
  const [occupe, setOccupe] = useState(false)

  async function envoyer(e: React.FormEvent) {
    e.preventDefault()
    setOccupe(true)
    const refus = await changerMotDePasse(actuel, nouveau, confirmation)
      .catch((): Refus => ({ erreur: 'erreur' }))
      .finally(() => setOccupe(false))
    setResultat(refus ?? 'change')
    if (refus) return
    setActuel('')
    setNouveau('')
    setConfirmation('')
  }

  const refus = resultat === 'change' ? null : resultat
  return (
    <Card className="max-w-md">
      <CardHeader>
        <CardTitle>{t('mdp.titre')}</CardTitle>
      </CardHeader>
      <CardContent>
        <form onSubmit={envoyer} className="space-y-4">
          <ChampMotDePasse id="actuel" label={t('mdp.actuel')} autoComplete="current-password" value={actuel} onChange={setActuel} />
          <ChampMotDePasse id="nouveau" label={t('connexion.nouveau')} autoComplete="new-password" value={nouveau} onChange={setNouveau} aide={t('connexion.regle')} />
          <ChampMotDePasse id="confirmation" label={t('connexion.confirmation')} autoComplete="new-password" value={confirmation} onChange={setConfirmation} />
          {resultat === 'change' && <Message ton="succes">{t('mdp.change')}</Message>}
          {refus?.erreur === 'absent' && <Message ton="info">{t('connexion.absent')}</Message>}
          <Erreur erreur={refus && refus.erreur !== 'absent' && texteAvis(t, refus.erreur, refus.attente)} />
          <Button type="submit" disabled={occupe}>
            {t('mdp.valider')}
          </Button>
        </form>
      </CardContent>
    </Card>
  )
}

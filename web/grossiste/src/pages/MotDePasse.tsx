import { useState, type FormEvent } from 'react'
import { CircleAlert } from 'lucide-react'
import { ChampMotDePasse, Information, Succes } from '@/components/communs'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { changerMotDePasse, texteAvis, type Refus } from '@/connexion'
import { useI18n } from '@/i18n'

// « Mon mot de passe » : PUT /v1/moi/mot-de-passe (les autres sessions du compte se ferment).
export default function MotDePasse() {
  const { t } = useI18n()
  const [actuel, setActuel] = useState('')
  const [nouveau, setNouveau] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [resultat, setResultat] = useState<Refus | 'change' | null>(null)
  const [occupe, setOccupe] = useState(false)

  async function envoyer(e: FormEvent) {
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

  return (
    <Card className="mx-auto max-w-md">
      <CardHeader>
        <CardTitle>{t('mdp.titre')}</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <form onSubmit={envoyer} className="space-y-4">
          <ChampMotDePasse id="actuel" label={t('mdp.actuel')} autoComplete="current-password" value={actuel} onChange={setActuel} />
          <ChampMotDePasse id="nouveau" label={t('login.nouveau')} autoComplete="new-password" value={nouveau} onChange={setNouveau} aide={t('login.regle')} />
          <ChampMotDePasse id="confirmation" label={t('login.confirmation')} autoComplete="new-password" value={confirmation} onChange={setConfirmation} />
          <Button type="submit" disabled={occupe}>{t('mdp.valider')}</Button>
        </form>
        {resultat === 'change' && <Succes>{t('mdp.change')}</Succes>}
        {resultat !== 'change' && resultat?.erreur === 'absent' && <Information>{t('login.absent')}</Information>}
        {resultat && resultat !== 'change' && resultat.erreur !== 'absent' && (
          <Alert variant="destructive" role="alert">
            <CircleAlert aria-hidden />
            <AlertDescription>{texteAvis(t, resultat.erreur, resultat.attente)}</AlertDescription>
          </Alert>
        )}
      </CardContent>
    </Card>
  )
}

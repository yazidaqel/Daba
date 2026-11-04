import { useState, type FormEvent } from 'react'
import { codeProbleme, fetchClient } from '@/api/client'
import { ChampMotDePasse } from '@/components/ChampMotDePasse'
import { Erreur, Succes } from '@/components/communs'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { useI18n } from '@/i18n'
import { controlerMotDePasse, MOT_DE_PASSE_MAX, MOT_DE_PASSE_MIN } from '@/motDePasse'

// Changement de son propre mot de passe (PUT /v1/moi/mot-de-passe) ; les valeurs ne vivent que dans cet état.
export default function MonMotDePasse() {
  const { t } = useI18n()
  const [actuel, setActuel] = useState('')
  const [nouveau, setNouveau] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [erreur, setErreur] = useState<unknown>(null)
  const [fait, setFait] = useState(false)
  const [enCours, setEnCours] = useState(false)

  async function soumettre(e: FormEvent) {
    e.preventDefault()
    setFait(false)
    const probleme = controlerMotDePasse(nouveau, confirmation)
    if (probleme) return setErreur({ detail: t(probleme) })
    setEnCours(true)
    const { error, response } = await fetchClient
      .PUT('/v1/moi/mot-de-passe', { body: { motDePasseActuel: actuel, nouveauMotDePasse: nouveau } })
      .catch(() => ({ error: {}, response: undefined }))
    setEnCours(false)
    // Ici, identifiants-invalides ne vise que le mot de passe actuel (l'utilisateur est connecté).
    setErreur(response?.ok ? null : codeProbleme(error) === 'identifiants-invalides' ? { detail: t('mdp.actuelFaux') } : error)
    if (response?.ok) {
      setFait(true)
      setActuel('')
      setNouveau('')
      setConfirmation('')
    }
  }

  return (
    <Card className="mx-auto max-w-md">
      <CardHeader>
        <CardTitle>{t('mdp.titre')}</CardTitle>
        <p className="text-muted-foreground text-sm">{t('mdp.aide')}</p>
      </CardHeader>
      <CardContent>
        <form onSubmit={soumettre} className="space-y-3">
          <ChampMotDePasse id="actuel" label={t('mdp.actuel')} valeur={actuel} onChange={setActuel} autoComplete="current-password" />
          <ChampMotDePasse
            id="nouveau"
            label={t('mdp.nouveau')}
            valeur={nouveau}
            onChange={setNouveau}
            autoComplete="new-password"
            aide={t('mdp.regle', { min: MOT_DE_PASSE_MIN, max: MOT_DE_PASSE_MAX })}
          />
          <ChampMotDePasse id="confirmation" label={t('mdp.confirmation')} valeur={confirmation} onChange={setConfirmation} autoComplete="new-password" />
          <Button type="submit" disabled={enCours}>{t('mdp.changer')}</Button>
          {erreur != null && <Erreur erreur={erreur} />}
          {fait && <Succes>{t('mdp.change')}</Succes>}
        </form>
      </CardContent>
    </Card>
  )
}

import { useState, type FormEvent } from 'react'
import { CircleAlert } from 'lucide-react'
import { ecrireSession, lireMotifFin } from '@/api/client'
import { ChampMotDePasse, Information } from '@/components/communs'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { choisirMethode, etatInitial, oublie, soumettre, texteAvis, type Etat, type Methode, type Resultat } from '@/connexion'
import { useI18n } from '@/i18n'
import { versE164 } from '@/telephone'

const METHODES: Methode[] = ['motDePasse', 'code']

export default function Login() {
  const { t, langue } = useI18n()
  const [etat, setEtat] = useState<Etat>(etatInitial)
  const [saisieTel, setSaisieTel] = useState('')
  const [motDePasse, setMotDePasse] = useState('')
  const [code, setCode] = useState('')
  const [nouveau, setNouveau] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [occupe, setOccupe] = useState(false)
  // Pourquoi la session précédente a pris fin : affiché jusqu'à la première action.
  const [motif, setMotif] = useState(lireMotifFin)

  // Les mots de passe ne restent pas dans l'état d'un écran à l'autre.
  async function executer(action: () => Promise<Resultat>) {
    setOccupe(true)
    setMotif(null)
    const { etat: suivant, session } = await action()
      .catch((): Resultat => ({ etat: { ...etat, erreur: 'erreur', info: undefined } }))
      .finally(() => setOccupe(false))
    if (session) return ecrireSession(session)
    if (suivant.etape.nom !== etat.etape.nom) vider()
    setEtat(suivant)
  }

  function vider() {
    setMotDePasse('')
    setCode('')
    setNouveau('')
    setConfirmation('')
  }

  function telephoneValide() {
    const tel = versE164(saisieTel)
    if (!tel) setEtat({ ...etat, erreur: 'telephoneInvalide', info: undefined })
    return tel
  }

  function envoyer(e: FormEvent) {
    e.preventDefault()
    if (etat.etape.nom === 'identifiant') {
      const telephone = telephoneValide()
      if (telephone) executer(() => soumettre(etat, { telephone, motDePasse }, langue))
    } else executer(() => soumettre(etat, { code, nouveau, confirmation }, langue))
  }

  function motDePasseOublie() {
    const telephone = telephoneValide()
    if (telephone) executer(() => oublie(etat, telephone, langue))
  }

  const retour = () => (vider(), setEtat({ ...etatInitial(etat.methode), telephone: etat.telephone }))
  const { etape } = etat
  const minutes = Math.round((etat.expireDans ?? 300) / 60)
  const champCode = (
    <div className="space-y-1.5">
      <p className="text-sm">{t('login.codeEnvoye', { telephone: etat.telephone, minutes })}</p>
      <Label htmlFor="code">{t('login.code')}</Label>
      <Input
        id="code"
        dir="ltr"
        inputMode="numeric"
        autoComplete="one-time-code"
        pattern="[0-9]{6}"
        maxLength={6}
        value={code}
        onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))}
        required
      />
    </div>
  )
  const erreur = etat.erreur
    ? texteAvis(t, etat.erreur, etat.attente)
    : motif === 'appareilRevoque' ? t('erreur.appareilRevoque') : motif === 'sessionExpiree' ? t('login.sessionExpiree') : null

  return (
    <div className="mx-auto mt-10 max-w-sm">
      <Card>
        <CardHeader>
          <CardTitle>
            {etape.nom === 'definition' ? t(etape.raison === 'aDefinir' ? 'login.definitionADefinir' : 'login.definitionOublie') : t('login.titre')}
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          {etat.info && <Information>{t(`login.${etat.info}`)}</Information>}
          <form onSubmit={envoyer} className="space-y-4">
            {etape.nom === 'identifiant' && (
              <>
                <div className="space-y-1.5">
                  <Label htmlFor="tel">{t('login.telephone')}</Label>
                  <Input
                    id="tel"
                    type="tel"
                    dir="ltr"
                    autoComplete={etat.methode === 'motDePasse' ? 'username' : 'tel'}
                    value={saisieTel}
                    onChange={(e) => setSaisieTel(e.target.value)}
                    required
                  />
                  <p className="text-muted-foreground text-sm">{t('login.telephoneAide')}</p>
                </div>
                <fieldset className="space-y-2">
                  <legend className="mb-2 text-sm font-medium">{t('login.methode')}</legend>
                  {METHODES.map((m) => (
                    <label key={m} className="flex items-center gap-2 text-sm">
                      <input type="radio" name="methode" value={m} checked={etat.methode === m} onChange={() => setEtat(choisirMethode(etat, m))} />
                      {t(m === 'motDePasse' ? 'login.methodeMotDePasse' : 'login.methodeCode')}
                    </label>
                  ))}
                </fieldset>
                {etat.methode === 'motDePasse' && (
                  <ChampMotDePasse id="mdp" label={t('login.motDePasse')} autoComplete="current-password" value={motDePasse} onChange={setMotDePasse} />
                )}
              </>
            )}
            {etape.nom === 'code' && champCode}
            {etape.nom === 'definition' && (
              <>
                {/* Pour le gestionnaire de mots de passe : le compte auquel ce mot de passe appartient. */}
                <input type="text" autoComplete="username" value={etat.telephone} readOnly hidden />
                {!etape.code && champCode}
                <ChampMotDePasse id="nouveau" label={t('login.nouveau')} autoComplete="new-password" value={nouveau} onChange={setNouveau} aide={t('login.regle')} />
                <ChampMotDePasse id="confirmation" label={t('login.confirmation')} autoComplete="new-password" value={confirmation} onChange={setConfirmation} />
              </>
            )}
            <Button type="submit" className="w-full" disabled={occupe}>
              {etape.nom === 'definition'
                ? t('login.enregistrer')
                : etape.nom === 'identifiant' && etat.methode === 'code' ? t('login.envoyerCode') : t('login.valider')}
            </Button>
            {etape.nom === 'identifiant' && etat.methode === 'motDePasse' && (
              <Button type="button" variant="link" className="w-full" disabled={occupe} onClick={motDePasseOublie}>
                {t('login.oublie')}
              </Button>
            )}
            {etape.nom !== 'identifiant' && (
              <Button type="button" variant="link" className="w-full" onClick={retour}>
                {etape.nom === 'code' ? t('login.changerNumero') : t('login.retour')}
              </Button>
            )}
          </form>
          {erreur && (
            <Alert variant="destructive" role="alert">
              <CircleAlert aria-hidden />
              <AlertDescription>{erreur}</AlertDescription>
            </Alert>
          )}
        </CardContent>
      </Card>
    </div>
  )
}

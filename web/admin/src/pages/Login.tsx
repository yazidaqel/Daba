import { useState, type FormEvent } from 'react'
import { appareilId, codeProbleme, ecrireSession, fetchClient, lireMotifFin } from '@/api/client'
import { messageErreur } from '@/api/erreurs'
import { ChampMotDePasse } from '@/components/ChampMotDePasse'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { useI18n } from '@/i18n'
import type { Cle } from '@/i18n/fr'
import { controlerMotDePasse, MOT_DE_PASSE_MAX, MOT_DE_PASSE_MIN } from '@/motDePasse'
import { versE164 } from '@/telephone'

// Le portail ne connaît pas la méthode d'un numéro à l'avance (aucune énumération) : il propose les deux
// et retient sur ce navigateur le dernier choix, jamais le numéro.
type Choix = 'motDePasse' | 'code'
const CHOIX = 'daba.connexion'
const lireChoix = (): Choix => {
  try {
    // Code SMS par défaut, comme la méthode par défaut du serveur (§10).
    return localStorage.getItem(CHOIX) === 'motDePasse' ? 'motDePasse' : 'code'
  } catch {
    return 'code'
  }
}

type Etape = 'telephone' | 'code' | 'definir'

// Les mots de passe ne passent que par l'état de cet écran et fetchClient (pas de cache de mutation).
export default function Login() {
  const { t, langue } = useI18n()
  const [choix, setChoixEtat] = useState<Choix>(lireChoix)
  const [etape, setEtape] = useState<Etape>('telephone')
  const [saisie, setSaisie] = useState('')
  const [telephone, setTelephone] = useState('')
  const [motDePasse, setMotDePasse] = useState('')
  const [code, setCode] = useState('')
  const [nouveau, setNouveau] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [defi, setDefi] = useState<string | null>(null)
  const [codeConnu, setCodeConnu] = useState(false)
  const [minutes, setMinutes] = useState(5)
  const [info, setInfo] = useState<Cle | null>(null)
  const [enCours, setEnCours] = useState(false)
  const [erreur, setErreur] = useState<string | null>(() => {
    const motif = lireMotifFin()
    return motif === 'appareilRevoque' ? t('erreur.appareilRevoque') : motif === 'sessionExpiree' ? t('login.sessionExpiree') : null
  })

  function setChoix(c: Choix) {
    setChoixEtat(c)
    try {
      localStorage.setItem(CHOIX, c)
    } catch {
      // Navigation privée : le choix ne sera simplement pas retenu.
    }
  }

  function aller(e: Etape, message: Cle | null = null) {
    setEtape(e)
    setInfo(message)
    setErreur(null)
    setMotDePasse('')
    setNouveau('')
    setConfirmation('')
  }

  // Un appel à la fois ; une erreur réseau donne le message générique.
  async function appel(f: () => Promise<void>) {
    setEnCours(true)
    setErreur(null)
    try {
      await f()
    } catch {
      setErreur(t('app.erreur'))
    } finally {
      setEnCours(false)
    }
  }

  const echec = (p: unknown) => setErreur(codeProbleme(p) === 'requete-invalide' ? t('login.telephoneInvalide') : messageErreur(p, t))

  // Code par SMS, vers l'étape de saisie du code ou de définition du mot de passe (oubli).
  async function envoyerCode(tel: string, suite: 'code' | 'definir') {
    const { data, error } = await fetchClient.POST('/v1/auth/otp', { body: { telephone: tel, langue } })
    if (!data) return echec(error)
    setMinutes(Math.round(data.expireDans / 60))
    setDefi(null)
    setCode('')
    setCodeConnu(false)
    aller(suite, suite === 'definir' ? 'login.oubliInfo' : null)
  }

  const numero = () => {
    const tel = versE164(saisie)
    if (!tel) setErreur(t('login.telephoneInvalide'))
    else setTelephone(tel)
    return tel
  }

  function soumettreTelephone(e: FormEvent) {
    e.preventDefault()
    const tel = numero()
    if (!tel) return
    void appel(async () => {
      if (choix === 'code') return envoyerCode(tel, 'code')
      const { data, error } = await fetchClient.POST('/v1/auth/mot-de-passe', {
        body: { telephone: tel, motDePasse, appareilId: appareilId(), langue },
      })
      if (data?.session) return ecrireSession(data.session)
      if (data?.defi) {
        setDefi(data.defi)
        setMinutes(Math.round((data.expireDans ?? 300) / 60))
        setCode('')
        return aller('code', 'login.defiInfo')
      }
      // Mot de passe juste, mais ce compte se connecte par code : on bascule et on envoie le code.
      if (codeProbleme(error) === 'connexion-par-code') {
        setChoix('code')
        await envoyerCode(tel, 'code')
        return setInfo('login.basculeCode')
      }
      setMotDePasse('')
      echec(error)
    })
  }

  function oublie() {
    const tel = numero()
    if (tel) void appel(() => envoyerCode(tel, 'definir'))
  }

  function verifierCode(e: FormEvent) {
    e.preventDefault()
    void appel(async () => {
      const { data, error } = await fetchClient.POST('/v1/auth/otp/verify', {
        body: { telephone, code, appareilId: appareilId(), ...(defi && { defi }) },
      })
      if (data) return ecrireSession(data)
      switch (codeProbleme(error)) {
        // Code juste et non consommé : il sert à créer le mot de passe.
        case 'mot-de-passe-a-definir':
          setCodeConnu(true)
          return aller('definir', 'login.aDefinirInfo')
        // Ce compte exige un mot de passe : retour à l'étape téléphone, en mode mot de passe.
        case 'mot-de-passe-requis':
          setChoix('motDePasse')
          return aller('telephone', 'login.basculeMotDePasse')
      }
      echec(error)
    })
  }

  function definir(e: FormEvent) {
    e.preventDefault()
    const probleme = controlerMotDePasse(nouveau, confirmation)
    if (probleme) return setErreur(t(probleme))
    void appel(async () => {
      const { data, error } = await fetchClient.POST('/v1/auth/mot-de-passe/definition', {
        body: { telephone, code, motDePasse: nouveau, appareilId: appareilId() },
      })
      if (data) return ecrireSession(data)
      // Code refusé : on le redemande (champ visible, renvoi possible).
      if (codeProbleme(error) === 'code-invalide') {
        setCodeConnu(false)
        setCode('')
      }
      echec(error)
    })
  }

  const champCode = (
    <div className="space-y-1">
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
  const changerNumero = (
    <Button type="button" variant="link" className="w-full" onClick={() => (setCode(''), setDefi(null), aller('telephone'))}>
      {t('login.changerNumero')}
    </Button>
  )
  const titres: Record<Etape, Cle> = { telephone: 'login.titre', code: 'login.titreCode', definir: codeConnu ? 'login.titreCreer' : 'login.titreOubli' }

  return (
    <div className="mx-auto mt-10 max-w-sm">
      <Card>
        <CardHeader>
          <CardTitle>{t(titres[etape])}</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          {info && (
            <Alert>
              <AlertDescription>{t(info)}</AlertDescription>
            </Alert>
          )}
          {etape === 'telephone' && (
            <form onSubmit={soumettreTelephone} className="space-y-3">
              <fieldset className="space-y-1">
                <legend className="text-sm font-medium">{t('login.methode')}</legend>
                <div className="flex flex-wrap gap-4 text-sm">
                  {(['motDePasse', 'code'] as const).map((c) => (
                    <label key={c} className="flex items-center gap-2">
                      <input type="radio" name="choix" checked={choix === c} onChange={() => (setChoix(c), setInfo(null), setErreur(null))} />
                      {t(c === 'code' ? 'login.choixCode' : 'login.choixMotDePasse')}
                    </label>
                  ))}
                </div>
              </fieldset>
              <div className="space-y-1">
                <Label htmlFor="tel">{t('login.telephone')}</Label>
                <Input id="tel" type="tel" dir="ltr" autoComplete="username" value={saisie} onChange={(e) => setSaisie(e.target.value)} required />
                <p className="text-muted-foreground text-sm">{t('login.telephoneAide')}</p>
              </div>
              {choix === 'motDePasse' && (
                <ChampMotDePasse id="mdp" label={t('mdp.motDePasse')} valeur={motDePasse} onChange={setMotDePasse} autoComplete="current-password" />
              )}
              <Button type="submit" className="w-full" disabled={enCours}>
                {t(choix === 'code' ? 'login.envoyerCode' : 'login.valider')}
              </Button>
              {choix === 'motDePasse' && (
                <Button type="button" variant="link" className="w-full" disabled={enCours} onClick={oublie}>
                  {t('login.oublie')}
                </Button>
              )}
            </form>
          )}
          {etape === 'code' && (
            <form onSubmit={verifierCode} className="space-y-3">
              <p className="text-sm">{t('login.codeEnvoye', { telephone, minutes })}</p>
              {champCode}
              <Button type="submit" className="w-full" disabled={enCours}>{t('login.valider')}</Button>
              {changerNumero}
            </form>
          )}
          {etape === 'definir' && (
            <form onSubmit={definir} className="space-y-3">
              {/* Pour que le gestionnaire de mots de passe rattache le nouveau mot de passe au numéro. */}
              <input type="text" autoComplete="username" value={telephone} readOnly hidden />
              {!codeConnu && (
                <>
                  <p className="text-sm">{t('login.codeEnvoye', { telephone, minutes })}</p>
                  {champCode}
                </>
              )}
              <ChampMotDePasse
                id="nouveau"
                label={t('mdp.nouveau')}
                valeur={nouveau}
                onChange={setNouveau}
                autoComplete="new-password"
                aide={t('mdp.regle', { min: MOT_DE_PASSE_MIN, max: MOT_DE_PASSE_MAX })}
              />
              <ChampMotDePasse id="confirmation" label={t('mdp.confirmation')} valeur={confirmation} onChange={setConfirmation} autoComplete="new-password" />
              <Button type="submit" className="w-full" disabled={enCours}>{t('login.enregistrerMotDePasse')}</Button>
              {!codeConnu && (
                <Button type="button" variant="link" className="w-full" disabled={enCours} onClick={() => void appel(() => envoyerCode(telephone, 'definir'))}>
                  {t('login.renvoyerCode')}
                </Button>
              )}
              {changerNumero}
            </form>
          )}
          {erreur && (
            <Alert variant="destructive">
              <AlertDescription>{erreur}</AlertDescription>
            </Alert>
          )}
        </CardContent>
      </Card>
    </div>
  )
}

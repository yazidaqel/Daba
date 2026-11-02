import { useState, type FormEvent } from 'react'
import { $api, appareilId, ecrireSession } from '@/api/client'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { useI18n } from '@/i18n'
import { versE164 } from '@/telephone'

export default function Login() {
  const { t, langue } = useI18n()
  const [saisie, setSaisie] = useState('')
  const [telephone, setTelephone] = useState<string | null>(null)
  const [code, setCode] = useState('')
  const [erreur, setErreur] = useState<string | null>(null)
  const demander = $api.useMutation('post', '/v1/auth/otp')
  const verifier = $api.useMutation('post', '/v1/auth/otp/verify')

  function messageStatut(status: number | undefined, invalide: 'login.telephoneInvalide' | 'login.codeInvalide') {
    return status === 429 ? t('login.tropDEssais') : status === 400 || status === 401 ? t(invalide) : t('app.erreur')
  }

  function envoyer(e: FormEvent) {
    e.preventDefault()
    const tel = versE164(saisie)
    if (!tel) return setErreur(t('login.telephoneInvalide'))
    setErreur(null)
    demander.mutate(
      { body: { telephone: tel, langue } },
      { onSuccess: () => setTelephone(tel), onError: (p) => setErreur(messageStatut(p.status, 'login.telephoneInvalide')) },
    )
  }

  function connecter(e: FormEvent) {
    e.preventDefault()
    setErreur(null)
    verifier.mutate(
      { body: { telephone: telephone!, code, appareilId: appareilId() } },
      { onSuccess: ecrireSession, onError: (p) => setErreur(messageStatut(p.status, 'login.codeInvalide')) },
    )
  }

  return (
    <div className="mx-auto mt-10 max-w-sm">
      <Card>
        <CardHeader>
          <CardTitle>{t('login.titre')}</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          {!telephone ? (
            <form onSubmit={envoyer} className="space-y-3">
              <Label htmlFor="tel">{t('login.telephone')}</Label>
              <Input id="tel" type="tel" dir="ltr" autoComplete="tel" value={saisie} onChange={(e) => setSaisie(e.target.value)} required />
              <p className="text-muted-foreground text-sm">{t('login.telephoneAide')}</p>
              <Button type="submit" className="w-full" disabled={demander.isPending}>
                {t('login.envoyerCode')}
              </Button>
            </form>
          ) : (
            <form onSubmit={connecter} className="space-y-3">
              <p className="text-sm">
                {t('login.codeEnvoye', {
                  telephone: telephone,
                  minutes: Math.round((demander.data?.expireDans ?? 300) / 60),
                })}
              </p>
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
              <Button type="submit" className="w-full" disabled={verifier.isPending}>
                {t('login.valider')}
              </Button>
              <Button type="button" variant="link" className="w-full" onClick={() => (setTelephone(null), setCode(''))}>
                {t('login.changerNumero')}
              </Button>
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

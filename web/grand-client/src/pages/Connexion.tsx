import { useState } from 'react'
import { ApiError, api, appareilId, ecrireSession, enteteMock, ok, type S } from '@/api/client'
import { useI18n } from '@/i18n'
import { versE164 } from '@/lib/format'
import { SelecteurLangue } from '@/components/SelecteurLangue'
import { Erreur } from '@/components/Erreur'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

export function Connexion({ onSession, motif }: { onSession: (s: S['Session']) => void; motif?: string }) {
  const { t, langue } = useI18n()
  const [telephone, setTelephone] = useState('')
  const [code, setCode] = useState('')
  const [envoye, setEnvoye] = useState(false)
  // Motif gardé en code (Problem.type) : son texte suit la langue choisie ensuite.
  const [erreur, setErreur] = useState<unknown>(motif && new ApiError(401, { type: `https://daba.ma/problemes/${motif}` }))
  const [occupe, setOccupe] = useState(false)

  async function soumettre(e: React.FormEvent) {
    e.preventDefault()
    const tel = versE164(telephone)
    if (!tel) return setErreur(t('connexion.telInvalide'))
    setOccupe(true)
    setErreur(undefined)
    try {
      if (!envoye) {
        ok(await api.POST('/v1/auth/otp', { body: { telephone: tel, langue } }))
        setEnvoye(true)
      } else {
        const s = ok(await api.POST('/v1/auth/otp/verify', { body: { telephone: tel, code, appareilId: appareilId() }, headers: enteteMock }))
        if (!s.utilisateur.roles.includes('GRAND_CLIENT')) return setErreur(t('connexion.roleRefuse'))
        ecrireSession(s)
        onSession(s)
      }
    } catch (err) {
      setErreur(err)
    } finally {
      setOccupe(false)
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center p-4">
      <Card className="w-full max-w-sm">
        <CardHeader className="flex flex-row items-center justify-between">
          <CardTitle>{t('app.titre')}</CardTitle>
          <SelecteurLangue />
        </CardHeader>
        <CardContent>
          <form onSubmit={soumettre} className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="tel">{t('connexion.telephone')}</Label>
              <Input
                id="tel"
                type="tel"
                dir="ltr"
                autoComplete="tel"
                placeholder="06 12 34 56 78"
                value={telephone}
                disabled={envoye}
                onChange={(e) => setTelephone(e.target.value)}
              />
            </div>
            {envoye && (
              <div className="space-y-2">
                <Label htmlFor="code">{t('connexion.code')}</Label>
                <Input
                  id="code"
                  dir="ltr"
                  inputMode="numeric"
                  autoComplete="one-time-code"
                  maxLength={6}
                  pattern="[0-9]{6}"
                  required
                  value={code}
                  onChange={(e) => setCode(e.target.value)}
                />
                <p className="text-sm text-muted-foreground">{t('connexion.codeEnvoye')}</p>
              </div>
            )}
            <Erreur erreur={erreur} />
            <Button type="submit" className="w-full" disabled={occupe}>
              {envoye ? t('connexion.valider') : t('connexion.recevoirCode')}
            </Button>
            {envoye && (
              <Button type="button" variant="link" className="w-full" onClick={() => (setEnvoye(false), setCode(''))}>
                {t('connexion.changerNumero')}
              </Button>
            )}
          </form>
        </CardContent>
      </Card>
    </div>
  )
}

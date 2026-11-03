import { MessageSquare } from 'lucide-react'
import { useEffect, useState } from 'react'
import { api, codeProbleme, type S } from '@/api/client'
import { Alerte, champ } from '@/components/commun'
import { Button } from '@/components/ui/button'
import { useI18n } from '@/i18n'
import { telephoneE164 } from '@/outils'
import { naviguer } from '@/router'
import { appareilId, lireSession, ouvrirSession } from '@/session'
import type { Cle } from '@/textes'

const CIBLES = { DOCUMENT: '/documents/', RELEVE: '/comptes/', DEMANDE_DEPANNAGE: '/demandes/' } as const
const versCible = (l: S['LienCible']) => CIBLES[l.cible] + l.cibleId

// La session ne s'ouvre qu'une fois le consentement lu : l'accueil ne s'affiche jamais avant l'écran CNDP.
async function apresConnexion(session: S['Session'], suite: string) {
  const c = await api.GET('/v1/moi/consentement', { headers: { Authorization: `Bearer ${session.accessToken}` } })
  naviguer(c.data?.traitementDonnees ? suite : `/consentement?suite=${encodeURIComponent(suite)}`, true)
  ouvrirSession(session)
}

const ERREURS: Record<number, Cle> = { 401: 'erreur.code', 410: 'erreur.lienExpire', 429: 'erreur.tropTentatives' }

/** Connexion OTP sans mot de passe, depuis un lien SMS signé (`jeton`) ou par numéro. */
export function Connexion({ jeton, suite = '/', revoque }: { jeton?: string; suite?: string; revoque?: boolean }) {
  const { t, langue } = useI18n()
  const [lien, setLien] = useState<S['LienCible']>()
  const [etape, setEtape] = useState<'telephone' | 'code'>('telephone')
  const [saisie, setSaisie] = useState('')
  const [code, setCode] = useState('')
  const [erreur, setErreur] = useState<Cle | undefined>(revoque ? 'erreur.appareilRevoque' : undefined)
  const [occupe, setOccupe] = useState(false)

  useEffect(() => {
    if (!jeton) return
    api.GET('/v1/liens/{jeton}', { params: { path: { jeton } } }).then(
      ({ data, response }) => {
        if (data && lireSession()) naviguer(versCible(data), true)
        else if (data) setLien(data)
        else setErreur(response.status === 410 ? 'erreur.lienExpire' : 'erreur.lienInvalide')
      },
      () => setErreur('erreur.reseau'),
    )
  }, [jeton])

  const telephone = telephoneE164(saisie)
  const ident = lien && jeton ? { lienJeton: jeton } : { telephone: telephone ?? '' }

  async function essayer(action: () => Promise<{ response: Response }>, suiteOk: () => Promise<void> | void) {
    setOccupe(true)
    setErreur(undefined)
    try {
      const { response } = await action()
      if (response.ok) await suiteOk()
      else {
        if (response.status === 410) setLien(undefined)
        if ((await codeProbleme(response)) === 'appareil-revoque') return setErreur('erreur.appareilRevoque')
        setErreur(ERREURS[response.status] ?? (response.status === 400 ? 'erreur.telephone' : 'erreur.generique'))
      }
    } catch {
      setErreur('erreur.reseau')
    } finally {
      setOccupe(false)
    }
  }

  function envoyerCode() {
    if (!lien && !telephone) return setErreur('erreur.telephone')
    void essayer(
      () => api.POST('/v1/auth/otp', { body: { ...ident, langue } }),
      () => setEtape('code'),
    )
  }

  function valider() {
    let session: S['Session'] | undefined
    void essayer(
      async () => {
        const r = await api.POST('/v1/auth/otp/verify', { body: { ...ident, code, appareilId: appareilId() } })
        session = r.data
        return r
      },
      async () => {
        if (!session) return
        // Le serveur filtre par rôle, mais un ambulant verrait ici ses propres documents : on refuse.
        if (!session.utilisateur.roles.includes('HANOUT')) return setErreur('erreur.pasHanout')
        await apresConnexion(session, lien ? versCible(lien) : suite)
      },
    )
  }

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold">{t('connexion.titre')}</h1>
      {etape === 'telephone' ? (
        <form
          className="space-y-4"
          onSubmit={(e) => {
            e.preventDefault()
            envoyerCode()
          }}
        >
          {lien ? (
            <p className="text-lg">{t('connexion.lienPour', { tel: lien.telephoneMasque })}</p>
          ) : (
            <label className="block space-y-2">
              <span className="font-semibold">{t('connexion.telephone')}</span>
              <input
                type="tel"
                inputMode="tel"
                autoComplete="tel"
                dir="ltr"
                placeholder="06 12 34 56 78"
                value={saisie}
                onChange={(e) => setSaisie(e.target.value)}
                className={champ}
              />
            </label>
          )}
          <Button type="submit" size="lg" className="w-full" disabled={occupe}>
            <MessageSquare />
            {t('connexion.envoyerCode')}
          </Button>
        </form>
      ) : (
        <form
          className="space-y-4"
          onSubmit={(e) => {
            e.preventDefault()
            valider()
          }}
        >
          <p className="text-lg">{t('connexion.codeEnvoyeA', { tel: lien?.telephoneMasque ?? telephone ?? '' })}</p>
          <label className="block space-y-2">
            <span className="font-semibold">{t('connexion.code')}</span>
            <input
              inputMode="numeric"
              autoComplete="one-time-code"
              dir="ltr"
              maxLength={6}
              value={code}
              onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))}
              className={`${champ} text-center text-2xl tracking-[0.5em]`}
            />
          </label>
          <Button type="submit" size="lg" className="w-full" disabled={occupe || code.length !== 6}>
            {t('connexion.valider')}
          </Button>
          <Button
            type="button"
            variant="ghost"
            className="w-full"
            onClick={() => {
              setEtape('telephone')
              setLien(undefined)
              setCode('')
            }}
          >
            {t('connexion.autreNumero')}
          </Button>
        </form>
      )}
      <Alerte cle={erreur} />
    </div>
  )
}

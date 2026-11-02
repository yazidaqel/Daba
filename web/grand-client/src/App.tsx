import { Suspense, lazy, useEffect, useState } from 'react'
import { ApiError, api, ecrireSession, lireSession, ok } from '@/api/client'
import { useI18n } from '@/i18n'
import { SelecteurLangue } from '@/components/SelecteurLangue'
import { Erreur } from '@/components/Erreur'
import { Button } from '@/components/ui/button'
import { Connexion } from '@/pages/Connexion'
import { Consentement } from '@/pages/Consentement'
import { Campagnes } from '@/pages/Campagnes'
import { MesDonnees } from '@/pages/MesDonnees'

// Pages avec carte : MapLibre est chargé à la demande.
const CampagneForm = lazy(() => import('@/pages/CampagneForm').then((m) => ({ default: m.CampagneForm })))
const CampagneDetail = lazy(() => import('@/pages/CampagneDetail').then((m) => ({ default: m.CampagneDetail })))
const Ruptures = lazy(() => import('@/pages/Ruptures').then((m) => ({ default: m.Ruptures })))

function useHash() {
  const [hash, setHash] = useState(location.hash)
  useEffect(() => {
    const maj = () => setHash(location.hash)
    addEventListener('hashchange', maj)
    return () => removeEventListener('hashchange', maj)
  }, [])
  return hash.replace(/^#\/?/, '').split('/')
}

export function App() {
  const { t } = useI18n()
  const [session, setSession] = useState(lireSession)
  const [consenti, setConsenti] = useState<boolean>()
  const [erreurConsentement, setErreurConsentement] = useState<unknown>()
  const [essai, setEssai] = useState(0)
  const route = useHash()

  const sortir = () => {
    ecrireSession(null)
    setSession(null)
    setConsenti(undefined)
  }

  useEffect(() => {
    const deconnecte = () => (setSession(null), setConsenti(undefined))
    const redemander = () => setConsenti(false)
    addEventListener('daba:deconnexion', deconnecte)
    addEventListener('daba:consentement', redemander)
    return () => {
      removeEventListener('daba:deconnexion', deconnecte)
      removeEventListener('daba:consentement', redemander)
    }
  }, [])

  useEffect(() => {
    if (!session) return
    api
      .GET('/v1/moi/consentement')
      .then(ok)
      .then(
        (c) => setConsenti(c.traitementDonnees),
        // 404 : jamais donné. Autre erreur : on ne redemande pas un consentement peut-être déjà donné.
        (e) => (e instanceof ApiError && e.status === 404 ? setConsenti(false) : setErreurConsentement(e)),
      )
  }, [session, essai])

  if (!session) return <Connexion onSession={setSession} />
  if (consenti === undefined)
    return (
      <div className="space-y-2 p-6">
        {erreurConsentement ? (
          <>
            <Erreur erreur={erreurConsentement} />
            <Button variant="outline" onClick={() => (setErreurConsentement(undefined), setEssai(essai + 1))}>
              {t('commun.reessayer')}
            </Button>
          </>
        ) : (
          <p className="text-muted-foreground">{t('commun.chargement')}</p>
        )}
      </div>
    )
  if (!consenti) return <Consentement onAccepte={() => setConsenti(true)} />

  const [section, id, action] = route
  let page
  if (section === 'ruptures') page = <Ruptures />
  else if (section === 'donnees') page = <MesDonnees onRetrait={sortir} />
  else if (section === 'campagnes' && id === 'nouvelle') page = <CampagneForm />
  else if (section === 'campagnes' && id && action === 'modifier') page = <CampagneForm id={id} />
  else if (section === 'campagnes' && id) page = <CampagneDetail id={id} />
  else page = <Campagnes />

  const lien = (href: string, actif: boolean, texte: string) => (
    <a href={href} className={`rounded-md px-3 py-2 text-sm ${actif ? 'bg-secondary font-medium' : 'text-muted-foreground'}`}>
      {texte}
    </a>
  )

  return (
    <div className="min-h-screen">
      <header className="flex flex-wrap items-center gap-2 border-b px-4 py-2">
        <span className="me-4 text-lg font-bold text-primary">{t('app.titre')}</span>
        {lien('#/campagnes', section !== 'ruptures' && section !== 'donnees', t('nav.campagnes'))}
        {lien('#/ruptures', section === 'ruptures', t('nav.ruptures'))}
        <span className="ms-auto" />
        {lien('#/donnees', section === 'donnees', t('nav.donnees'))}
        <SelecteurLangue />
        <Button variant="ghost" onClick={sortir}>
          {t('app.deconnexion')}
        </Button>
      </header>
      <main className="mx-auto max-w-6xl p-4">
        <Suspense fallback={<p className="text-muted-foreground">{t('commun.chargement')}</p>}>{page}</Suspense>
      </main>
    </div>
  )
}

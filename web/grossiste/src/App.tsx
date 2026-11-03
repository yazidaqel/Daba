import { useState, useSyncExternalStore, type ReactNode } from 'react'
import { BrowserRouter, Navigate, NavLink, Route, Routes } from 'react-router'
import { $api, fetchClient, lireSession, seDeconnecter } from '@/api/client'
import { messageErreur } from '@/api/erreurs'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { LANGUES, NOMS_LANGUES, useI18n, type Langue } from '@/i18n'
import Ambulants from '@/pages/Ambulants'
import BonsSortie from '@/pages/BonsSortie'
import Depot from '@/pages/Depot'
import Depots from '@/pages/Depots'
import Login from '@/pages/Login'
import Retrait from '@/pages/Retrait'
import StockPromo from '@/pages/StockPromo'

const abonner = (f: () => void) => (window.addEventListener('daba-session', f), () => window.removeEventListener('daba-session', f))
const lireJeton = () => lireSession()?.accessToken ?? null

export default function App() {
  const jeton = useSyncExternalStore(abonner, lireJeton)
  const { t } = useI18n()
  if (!jeton)
    return (
      <Cadre>
        <Login />
      </Cadre>
    )
  if (!lireSession()!.utilisateur.roles.includes('GROSSISTE'))
    return (
      <Cadre>
        <Alert variant="destructive">
          <AlertDescription>{t('login.pasGrossiste')}</AlertDescription>
        </Alert>
      </Cadre>
    )
  return (
    <BrowserRouter>
      <Cadre nav>
        <Consentement>
          <Routes>
            <Route path="/" element={<Navigate to="/depots" replace />} />
            <Route path="/depots" element={<Depots />} />
            <Route path="/depots/:depotId" element={<Depot />} />
            <Route path="/stock-promo" element={<StockPromo />} />
            <Route path="/retrait" element={<Retrait />} />
            <Route path="/bons-sortie" element={<BonsSortie />} />
            <Route path="/ambulants" element={<Ambulants />} />
            <Route path="*" element={<Navigate to="/depots" replace />} />
          </Routes>
        </Consentement>
      </Cadre>
    </BrowserRouter>
  )
}

function Cadre({ nav, children }: { nav?: boolean; children: ReactNode }) {
  const { t, langue, setLangue } = useI18n()
  const jeton = useSyncExternalStore(abonner, lireJeton)
  const lien = ({ isActive }: { isActive: boolean }) =>
    `rounded-md px-3 py-2 text-sm font-medium ${isActive ? 'bg-primary-soft text-primary' : 'hover:bg-muted'}`
  return (
    <>
      <header className="border-b">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center gap-2 px-4 py-3">
          <span className="me-4 font-semibold">{t('app.titre')}</span>
          {nav && (
            <nav className="flex flex-wrap gap-1">
              <NavLink to="/depots" className={lien}>{t('nav.depots')}</NavLink>
              <NavLink to="/stock-promo" className={lien}>{t('nav.stockPromo')}</NavLink>
              <NavLink to="/retrait" className={lien}>{t('nav.retrait')}</NavLink>
              <NavLink to="/bons-sortie" className={lien}>{t('nav.bonsSortie')}</NavLink>
              <NavLink to="/ambulants" className={lien}>{t('nav.ambulants')}</NavLink>
            </nav>
          )}
          <div className="ms-auto flex items-center gap-2">
            <select
              aria-label={t('app.langue')}
              className="border-input bg-background h-9 rounded-md border px-2 text-sm"
              value={langue}
              onChange={(e) => setLangue(e.target.value as Langue)}
            >
              {LANGUES.map((l) => (
                <option key={l} value={l}>{NOMS_LANGUES[l]}</option>
              ))}
            </select>
            {jeton && (
              <Button variant="outline" size="sm" onClick={seDeconnecter}>
                {t('app.deconnexion')}
              </Button>
            )}
          </div>
        </div>
      </header>
      <main className="mx-auto max-w-6xl px-4 py-6">{children}</main>
      {nav && <DroitsCndp />}
    </>
  )
}

// Droit d'accès (GET /v1/moi/donnees) et retrait du consentement (loi 09-08).
function DroitsCndp() {
  const { t, langue } = useI18n()
  const [erreur, setErreur] = useState<unknown>(null)
  async function telecharger() {
    const { data, error } = await fetchClient.GET('/v1/moi/donnees')
    setErreur(error ?? null)
    if (!data) return
    const a = document.createElement('a')
    a.href = URL.createObjectURL(new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' }))
    a.download = 'daba-mes-donnees.json'
    a.click()
    setTimeout(() => URL.revokeObjectURL(a.href), 1000)
  }
  async function retirer() {
    if (!window.confirm(t('app.retirerConfirmation'))) return
    const { error } = await fetchClient.PUT('/v1/moi/consentement', {
      body: { versionTexte: `${VERSION_TEXTE}-${langue}`, traitementDonnees: false, langue },
    })
    if (error) setErreur(error)
    else await seDeconnecter()
  }
  return (
    <footer className="no-print text-muted-foreground mx-auto flex max-w-6xl gap-2 px-4 pb-6 text-sm">
      <Button variant="link" size="sm" onClick={telecharger}>{t('app.mesDonnees')}</Button>
      <Button variant="link" size="sm" onClick={retirer}>{t('app.retirerConsentement')}</Button>
      {erreur != null && <span className="text-destructive self-center">{messageErreur(erreur, t)}</span>}
    </footer>
  )
}

// CNDP (loi 09-08) : tant que le consentement n'est pas donné, l'API répond 403 consentement-requis.
const VERSION_TEXTE = 'grossiste-2026-11-v2'

function Consentement({ children }: { children: ReactNode }) {
  const { t, langue } = useI18n()
  const [coche, setCoche] = useState(false)
  const lecture = $api.useQuery('get', '/v1/moi/consentement', {}, { retry: false })
  const enregistrer = $api.useMutation('put', '/v1/moi/consentement', {
    onSuccess: () => lecture.refetch(),
  })
  if (lecture.isPending) return <p>{t('app.chargement')}</p>
  if (lecture.data?.traitementDonnees) return children
  if (lecture.error && lecture.error.status !== 404)
    return (
      <Alert variant="destructive">
        <AlertDescription>{messageErreur(lecture.error, t)}</AlertDescription>
      </Alert>
    )
  return (
    <Card className="mx-auto max-w-xl">
      <CardHeader>
        <CardTitle>{t('consentement.titre')}</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <p className="text-sm leading-relaxed">{t('consentement.texte')}</p>
        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" checked={coche} onChange={(e) => setCoche(e.target.checked)} />
          {t('consentement.accepter')}
        </label>
        <Button
          disabled={!coche || enregistrer.isPending}
          onClick={() => enregistrer.mutate({ body: { versionTexte: `${VERSION_TEXTE}-${langue}`, traitementDonnees: true, langue } })}
        >
          {t('consentement.continuer')}
        </Button>
        {enregistrer.error && (
          <Alert variant="destructive">
            <AlertDescription>{messageErreur(enregistrer.error, t)}</AlertDescription>
          </Alert>
        )}
      </CardContent>
    </Card>
  )
}

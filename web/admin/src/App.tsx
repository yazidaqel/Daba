import { useSyncExternalStore, type ReactNode } from 'react'
import { ShieldAlert } from 'lucide-react'
import { BrowserRouter, Navigate, NavLink, Route, Routes } from 'react-router'
import { lireSession, seDeconnecter } from '@/api/client'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { LANGUES, NOMS_LANGUES, useI18n, type Langue } from '@/i18n'
import Audit from '@/pages/Audit'
import Canaux from '@/pages/Canaux'
import Connexion from '@/pages/Connexion'
import Login from '@/pages/Login'
import MonMotDePasse from '@/pages/MonMotDePasse'
import Organisation from '@/pages/Organisation'
import Organisations from '@/pages/Organisations'
import Referentiel from '@/pages/Referentiel'
import Utilisateurs from '@/pages/Utilisateurs'

const abonner = (f: () => void) => (window.addEventListener('daba-session', f), () => window.removeEventListener('daba-session', f))
const lireJeton = () => lireSession()?.accessToken ?? null

// Les opérations /v1/admin/* sont réservées au rôle ADMIN ; tout autre compte est refusé après l'OTP.
// L'administrateur n'a pas d'écran de consentement : /v1/moi/consentement n'est pas ouvert au rôle ADMIN.
export default function App() {
  const jeton = useSyncExternalStore(abonner, lireJeton)
  const { t } = useI18n()
  if (!jeton)
    return (
      <Cadre>
        <Login />
      </Cadre>
    )
  if (!lireSession()!.utilisateur.roles.includes('ADMIN'))
    return (
      <Cadre>
        <Alert variant="destructive">
          <AlertDescription>{t('login.pasAdmin')}</AlertDescription>
        </Alert>
      </Cadre>
    )
  return (
    <BrowserRouter>
      <Cadre nav>
        <Routes>
          <Route path="/" element={<Navigate to="/organisations" replace />} />
          <Route path="/organisations" element={<Organisations />} />
          <Route path="/organisations/:organisationId" element={<Organisation />} />
          <Route path="/utilisateurs" element={<Utilisateurs />} />
          <Route path="/referentiel" element={<Referentiel />} />
          <Route path="/canaux" element={<Canaux />} />
          <Route path="/connexion" element={<Connexion />} />
          <Route path="/mon-mot-de-passe" element={<MonMotDePasse />} />
          <Route path="/audit" element={<Audit />} />
          <Route path="*" element={<Navigate to="/organisations" replace />} />
        </Routes>
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
              <NavLink to="/organisations" className={lien}>{t('nav.organisations')}</NavLink>
              <NavLink to="/utilisateurs" className={lien}>{t('nav.utilisateurs')}</NavLink>
              <NavLink to="/referentiel" className={lien}>{t('nav.referentiel')}</NavLink>
              <NavLink to="/canaux" className={lien}>{t('nav.canaux')}</NavLink>
              <NavLink to="/connexion" className={lien}>{t('nav.connexion')}</NavLink>
              <NavLink to="/audit" className={lien}>{t('nav.audit')}</NavLink>
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
            {nav && <NavLink to="/mon-mot-de-passe" className={lien}>{t('nav.monMotDePasse')}</NavLink>}
            {jeton && (
              <Button variant="outline" size="sm" onClick={seDeconnecter}>
                {t('app.deconnexion')}
              </Button>
            )}
          </div>
        </div>
      </header>
      <main className="mx-auto max-w-6xl px-4 py-6">{children}</main>
      {nav && (
        <footer className="text-muted-foreground mx-auto flex max-w-6xl items-start gap-2 px-4 pb-6 text-sm">
          <ShieldAlert aria-hidden className="mt-0.5 size-4 shrink-0" />
          <p>{t('app.cndp')}</p>
        </footer>
      )}
    </>
  )
}

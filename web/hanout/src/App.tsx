import { FileText, LogOut, PackageSearch, ShieldCheck, Wallet } from 'lucide-react'
import type { ReactNode } from 'react'
import { deconnecter } from '@/api/client'
import { Button } from '@/components/ui/button'
import { Comptes, Releve } from '@/ecrans/Comptes'
import { Connexion } from '@/ecrans/Connexion'
import { Consentement } from '@/ecrans/Consentement'
import { Demande } from '@/ecrans/Demande'
import { DocumentDetail, Documents } from '@/ecrans/Documents'
import { Donnees } from '@/ecrans/Donnees'
import { Rupture } from '@/ecrans/Rupture'
import { changerLangue, LANGUES, useI18n, type Langue } from '@/i18n'
import { cn } from '@/lib/utils'
import { correspond, Lien, naviguer, useUrl } from '@/router'
import { lireSession } from '@/session'

type Rendu = (p: Record<string, string>, q: URLSearchParams) => ReactNode

const ROUTES: [string, Rendu][] = [
  ['/', () => <Rupture />],
  ['/demandes/:id', (p) => <Demande id={p.id} />],
  ['/documents', (_, q) => <Documents ambulantId={q.get('ambulantId') ?? undefined} />],
  ['/documents/:id', (p) => <DocumentDetail id={p.id} />],
  ['/comptes', () => <Comptes />],
  ['/comptes/:id', (p) => <Releve id={p.id} />],
  ['/consentement', (_, q) => <Consentement suite={q.get('suite') ?? '/'} />],
  ['/donnees', () => <Donnees />],
]

const ONGLETS = [
  { vers: '/', cle: 'nav.rupture', Icone: PackageSearch, actif: (c: string) => c === '/' || c.startsWith('/demandes') },
  { vers: '/documents', cle: 'nav.documents', Icone: FileText, actif: (c: string) => c.startsWith('/documents') },
  { vers: '/comptes', cle: 'nav.solde', Icone: Wallet, actif: (c: string) => c.startsWith('/comptes') },
] as const

export function App() {
  const { t, langue } = useI18n()
  const url = useUrl()
  const [chemin, recherche] = url.split('?')
  const q = new URLSearchParams(recherche)
  const lien = correspond('/l/:jeton', chemin)
  const connecte = lireSession() !== null

  let contenu: ReactNode
  if (lien) contenu = <Connexion key={lien.jeton} jeton={lien.jeton} />
  else if (!connecte || chemin === '/connexion')
    contenu = (
      <Connexion suite={chemin === '/connexion' ? (q.get('suite') ?? '/') : url} revoque={q.has('revoque')} />
    )
  else {
    const route = ROUTES.map(([motif, rendu]) => [correspond(motif, chemin), rendu] as const).find(([p]) => p)
    contenu = route?.[0] ? route[1](route[0], q) : <Rupture />
  }
  const avecOnglets = connecte && !lien && chemin !== '/connexion' && chemin !== '/consentement'

  return (
    <div className="flex min-h-dvh flex-col">
      <header className="sticky top-0 z-10 flex items-center gap-2 border-b border-border bg-card px-4 py-2">
        <Lien vers="/" className="me-auto text-2xl font-bold text-primary">
          Daba
        </Lien>
        <select
          aria-label={t('langue')}
          value={langue}
          onChange={(e) => changerLangue(e.target.value as Langue)}
          className="min-h-12 rounded-xl border-2 border-input bg-card px-2"
        >
          {LANGUES.map((l) => (
            <option key={l.code} value={l.code} lang={l.code}>
              {l.nom}
            </option>
          ))}
        </select>
        {connecte && (
          <Button asChild variant="ghost" size="icon">
            <Lien vers="/donnees" aria-label={t('consentement.titre')}>
              <ShieldCheck />
            </Lien>
          </Button>
        )}
        {connecte && (
          <Button
            variant="ghost"
            size="icon"
            aria-label={t('deconnexion')}
            onClick={() => {
              deconnecter()
              naviguer('/connexion')
            }}
          >
            <LogOut />
          </Button>
        )}
      </header>
      <main className={cn('mx-auto w-full max-w-lg flex-1 p-4', avecOnglets && 'pb-28')}>{contenu}</main>
      {avecOnglets && (
        <nav className="fixed inset-x-0 bottom-0 grid grid-cols-3 border-t border-border bg-card">
          {ONGLETS.map(({ vers, cle, Icone, actif }) => (
            <Lien
              key={vers}
              vers={vers}
              aria-current={actif(chemin) ? 'page' : undefined}
              className={cn(
                'flex min-h-16 flex-col items-center justify-center gap-1 text-sm font-semibold',
                actif(chemin) ? 'text-primary' : 'text-muted-foreground',
              )}
            >
              <Icone className="size-7" />
              {t(cle)}
            </Lien>
          ))}
        </nav>
      )}
    </div>
  )
}

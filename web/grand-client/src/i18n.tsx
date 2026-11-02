import { createContext, useContext, useEffect, useState } from 'react'
import { choisirLangueApi, type S } from '@/api/client'
import { MESSAGES, type Cle } from '@/messages'

export type { Cle }
export type Langue = S['Langue']
export const LANGUES = ['fr', 'ar', 'ary-Arab', 'ary-Latn'] as const satisfies Langue[]

const RTL: Langue[] = ['ar', 'ary-Arab']
const LOCALE: Record<Langue, string> = { fr: 'fr-MA', ar: 'ar-MA', 'ary-Arab': 'ar-MA', 'ary-Latn': 'fr-MA' }

function langueInitiale(): Langue {
  const l = localStorage.getItem('daba.langue') as Langue | null
  return l && (LANGUES as readonly string[]).includes(l) ? l : 'fr'
}

function outils(langue: Langue, setLangue: (l: Langue) => void) {
  const loc = LOCALE[langue]
  return {
    langue,
    setLangue,
    t: (cle: Cle, vars: Record<string, string | number> = {}) =>
      MESSAGES[langue][cle].replace(/\{(\w+)\}/g, (_, k: string) => String(vars[k] ?? `{${k}}`)),
    libelle: (l: S['LibelleI18n']) => l[langue] || l.fr,
    liste: (l: string[]) => new Intl.ListFormat(loc, { type: 'unit' }).format(l),
    nombre: (n: number) => new Intl.NumberFormat(loc).format(n),
    montant: (centimes: number) => new Intl.NumberFormat(loc, { style: 'currency', currency: 'MAD' }).format(centimes / 100),
    date: (iso: string) => new Intl.DateTimeFormat(loc, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(iso)),
  }
}

const Contexte = createContext(outils('fr', () => {}))

export function I18nProvider({ children }: { children: React.ReactNode }) {
  const [langue, setLangue] = useState(langueInitiale)
  choisirLangueApi(langue) // avant les effets des enfants, qui lancent les premiers appels
  useEffect(() => {
    document.documentElement.lang = langue
    document.documentElement.dir = RTL.includes(langue) ? 'rtl' : 'ltr'
    document.title = MESSAGES[langue]['app.titre']
    localStorage.setItem('daba.langue', langue)
  }, [langue])
  return <Contexte.Provider value={outils(langue, setLangue)}>{children}</Contexte.Provider>
}

export const useI18n = () => useContext(Contexte)

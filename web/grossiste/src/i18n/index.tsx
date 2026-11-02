import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import { ar } from './ar'
import { aryArab } from './ary-Arab'
import { aryLatn } from './ary-Latn'
import { fr, type Cle } from './fr'

// Mêmes codes que le schéma Langue du contrat (envoyés dans Accept-Language).
export const LANGUES = ['fr', 'ar', 'ary-Arab', 'ary-Latn'] as const
export type Langue = (typeof LANGUES)[number]

export const NOMS_LANGUES: Record<Langue, string> = {
  fr: 'Français',
  ar: 'العربية',
  'ary-Arab': 'الدارجة',
  'ary-Latn': 'Darija',
}

const DICTIONNAIRES: Record<Langue, Record<Cle, string>> = { fr, ar, 'ary-Arab': aryArab, 'ary-Latn': aryLatn }

export const estRtl = (l: Langue) => l === 'ar' || l === 'ary-Arab'

// Locale Intl : chiffres latins partout (habitude des commerçants), mois en arabe pour ar et ary-Arab.
const LOCALE_INTL: Record<Langue, string> = { fr: 'fr-MA', ar: 'ar-MA', 'ary-Arab': 'ar-MA', 'ary-Latn': 'fr-MA' }

export function traduire(langue: Langue, cle: Cle, vars?: Record<string, string | number>) {
  const texte = DICTIONNAIRES[langue][cle] ?? fr[cle]
  return vars ? texte.replace(/\{(\w+)\}/g, (_, k: string) => String(vars[k] ?? `{${k}}`)) : texte
}

const STOCKAGE = 'daba.langue'

export function langueInitiale(): Langue {
  const l = localStorage.getItem(STOCKAGE)
  return (LANGUES as readonly string[]).includes(l ?? '') ? (l as Langue) : 'fr'
}

type I18n = {
  langue: Langue
  setLangue: (l: Langue) => void
  t: (cle: Cle, vars?: Record<string, string | number>) => string
  date: (iso: string) => string
  nombre: (n: number) => string
}

const Ctx = createContext<I18n | null>(null)

export function I18nProvider({ children }: { children: ReactNode }) {
  const [langue, setLangue] = useState<Langue>(langueInitiale)

  useEffect(() => {
    localStorage.setItem(STOCKAGE, langue)
    document.documentElement.lang = langue
    document.documentElement.dir = estRtl(langue) ? 'rtl' : 'ltr'
    document.title = traduire(langue, 'app.titre')
  }, [langue])

  const locale = `${LOCALE_INTL[langue]}-u-nu-latn`
  const valeur: I18n = {
    langue,
    setLangue,
    t: (cle, vars) => traduire(langue, cle, vars),
    date: (iso) => new Intl.DateTimeFormat(locale, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(iso)),
    nombre: (n) => new Intl.NumberFormat(locale).format(n),
  }
  return <Ctx value={valeur}>{children}</Ctx>
}

export function useI18n() {
  const c = useContext(Ctx)
  if (!c) throw new Error('useI18n hors de I18nProvider')
  return c
}

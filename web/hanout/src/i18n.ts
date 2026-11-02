import { useMemo, useSyncExternalStore } from 'react'
import type { components } from './api/schema'
import { textes, type Cle } from './textes'

export type Langue = components['schemas']['Langue']
type LibelleI18n = components['schemas']['LibelleI18n']

export const LANGUES: { code: Langue; nom: string }[] = [
  { code: 'ary-Arab', nom: 'الدارجة' },
  { code: 'ary-Latn', nom: 'Darija' },
  { code: 'ar', nom: 'العربية' },
  { code: 'fr', nom: 'Français' },
]

const CLE = 'daba.langue'
const estRtl = (l: Langue) => l === 'ar' || l === 'ary-Arab'
// ar-MA garde les chiffres latins, comme les bons imprimés.
const locale = (l: Langue) => (estRtl(l) ? 'ar-MA' : 'fr-MA')

function langueInitiale(): Langue {
  const l = localStorage.getItem(CLE)
  if (LANGUES.some((x) => x.code === l)) return l as Langue
  return navigator.language.startsWith('ar') ? 'ary-Arab' : 'fr'
}

let langue = langueInitiale()
const ecouteurs = new Set<() => void>()

function appliquer() {
  document.documentElement.lang = langue
  document.documentElement.dir = estRtl(langue) ? 'rtl' : 'ltr'
}
appliquer()

export function changerLangue(l: Langue) {
  langue = l
  localStorage.setItem(CLE, l)
  appliquer()
  ecouteurs.forEach((f) => f())
}

export const langueActuelle = () => langue

const abonner = (f: () => void) => {
  ecouteurs.add(f)
  return () => {
    ecouteurs.delete(f)
  }
}

/** Les valeurs insérées (numéro, montant, nom) sont isolées (FSI…PDI) : un +212… reste lisible en RTL. */
export function traduire(l: Langue, cle: Cle, vars?: Record<string, string | number>) {
  return textes[l][cle].replace(/\{(\w+)\}/g, (_, k: string) => `⁨${vars?.[k] ?? ''}⁩`)
}

export function useI18n() {
  const l = useSyncExternalStore(abonner, langueActuelle)
  return useMemo(() => {
    const argent = new Intl.NumberFormat(locale(l), { style: 'currency', currency: 'MAD' })
    const dates = new Intl.DateTimeFormat(locale(l), { dateStyle: 'medium', timeStyle: 'short' })
    return {
      langue: l,
      t: (cle: Cle, vars?: Record<string, string | number>) => traduire(l, cle, vars),
      /** Montant de l'API, en centimes. */
      montant: (centimes: number) => argent.format(centimes / 100),
      date: (iso: string) => dates.format(new Date(iso)),
      libelle: (x?: LibelleI18n) => (x ? (x[l] ?? (l === 'ary-Arab' ? x.ar : undefined) ?? x.fr) : ''),
    }
  }, [l])
}

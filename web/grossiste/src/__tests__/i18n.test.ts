import { expect, test } from 'vitest'
import { ar } from '@/i18n/ar'
import { aryArab } from '@/i18n/ary-Arab'
import { aryLatn } from '@/i18n/ary-Latn'
import { fr } from '@/i18n/fr'
import { estRtl, traduire } from '@/i18n'

const variables = (s: string) => [...s.matchAll(/\{(\w+)\}/g)].map((m) => m[1]).sort()

test.each([
  ['ar', ar],
  ['ary-Arab', aryArab],
  ['ary-Latn', aryLatn],
])('%s : mêmes clés et mêmes variables que fr, aucun texte vide', (_, dico) => {
  expect(Object.keys(dico).sort()).toEqual(Object.keys(fr).sort())
  for (const [cle, texte] of Object.entries(fr)) {
    const d = dico[cle as keyof typeof fr]
    expect(d.trim(), cle).not.toBe('')
    expect(variables(d), cle).toEqual(variables(texte))
  }
})

test('interpolation et sens d’écriture', () => {
  expect(traduire('fr', 'retrait.ok', { quantite: 3 })).toBe('Retrait validé : 3 remis.')
  expect(estRtl('ar') && estRtl('ary-Arab') && !estRtl('ary-Latn') && !estRtl('fr')).toBe(true)
})

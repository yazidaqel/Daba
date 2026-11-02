import { useSyncExternalStore } from 'react'

/**
 * Jetons de couleur (src/index.css) relus pour MapLibre, qui dessine en WebGL et ne connaît pas
 * les variables CSS. Les jetons sont écrits en hexadécimal.
 */
export const jeton = (nom: string) => getComputedStyle(document.documentElement).getPropertyValue(`--${nom}`).trim()

const sombre = () => matchMedia('(prefers-color-scheme: dark)')
/** Mode sombre du système : les couleurs de la carte se recalculent quand il change. */
export function useSombre() {
  return useSyncExternalStore(
    (rappel) => (sombre().addEventListener('change', rappel), () => sombre().removeEventListener('change', rappel)),
    () => sombre().matches,
  )
}

const rgb = (hex: string) => [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16))
const hex = (c: number[]) => '#' + c.map((v) => Math.round(v).toString(16).padStart(2, '0')).join('')

/** `n` teintes de `de` à `a` (interpolation linéaire en sRGB), pour une échelle séquentielle. */
export function echelle(de: string, a: string, n: number): string[] {
  const [x, y] = [rgb(de), rgb(a)]
  return Array.from({ length: n }, (_, k) => hex(x.map((v, i) => v + ((y[i] - v) * k) / (n - 1))))
}

/** Classe 0…n-1 d'une valeur 1…max, en tranches égales. */
export const classe = (v: number, max: number, n: number) => Math.max(0, Math.min(n - 1, Math.floor((n * (v - 1)) / max)))

/** Bornes [min, max] de chaque classe pour la légende ; une classe vide (max < n) est omise. */
export function bornes(max: number, n: number): { classe: number; de: number; a: number }[] {
  const debut = (k: number) => Math.ceil((k * max) / n) + 1
  return Array.from({ length: n }, (_, k) => ({ classe: k, de: debut(k), a: k === n - 1 ? max : debut(k + 1) - 1 })).filter((b) => b.de <= b.a)
}

const luminance = (c: string) =>
  rgb(c)
    .map((v) => v / 255)
    .map((v) => (v <= 0.04045 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4))
    .reduce((s, v, i) => s + v * [0.2126, 0.7152, 0.0722][i], 0)
const contraste = (a: string, b: string) => {
  const [l1, l2] = [luminance(a), luminance(b)].sort((p, q) => q - p)
  return (l1 + 0.05) / (l2 + 0.05)
}
/** Parmi les couleurs de texte proposées, la plus lisible sur `fond`. */
export const lisible = (fond: string, textes: string[]) => textes.reduce((m, t) => (contraste(fond, t) > contraste(fond, m) ? t : m))

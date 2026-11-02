/** "12,50" ou "12.5" (dirhams) → 1250 centimes ; vide ou invalide → undefined. */
export function versCentimes(dh: string): number | undefined {
  const n = Number(dh.trim().replace(',', '.'))
  return dh.trim() === '' || !Number.isFinite(n) || n < 0 ? undefined : Math.round(n * 100)
}

/** 1250 → "12.50", pour la valeur d'un champ de saisie. */
export function versDirhams(centimes: number | undefined): string {
  return centimes === undefined ? '' : (centimes / 100).toFixed(2)
}

/** ISO 8601 → valeur d'un <input type="datetime-local"> (heure locale). */
export function isoVersLocal(iso: string | undefined): string {
  if (!iso) return ''
  const d = new Date(iso)
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`
}

export function localVersIso(local: string): string {
  return local ? new Date(local).toISOString() : ''
}

/** "06 12 34 56 78", "+212612345678" ou "00212…" → E.164, sinon null. */
export function versE164(saisie: string): string | null {
  const n = saisie.replace(/[\s.-]/g, '').replace(/^00/, '+').replace(/^0/, '+212')
  return /^\+212[5-7][0-9]{8}$/.test(n) ? n : null
}

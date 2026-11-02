// Normalise un numéro marocain saisi librement (06…, 6…, 00212…, +212…) au format E.164 du contrat.
export function versE164(saisie: string): string | null {
  let n = saisie.replace(/[\s.\-()]/g, '')
  if (n.startsWith('00')) n = '+' + n.slice(2)
  if (n.startsWith('0')) n = '+212' + n.slice(1)
  else if (/^[5-7]\d{8}$/.test(n)) n = '+212' + n
  return /^\+212[5-7]\d{8}$/.test(n) ? n : null
}

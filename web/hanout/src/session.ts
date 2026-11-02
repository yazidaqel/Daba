import type { components } from './api/schema'

type Session = components['schemas']['Session']
const CLE = 'daba.session'

// Pas de contrôle d'expiration local (horloge souvent fausse sur les téléphones) : le 401 du serveur fait foi.
export function lireSession(): Session | null {
  try {
    return JSON.parse(localStorage.getItem(CLE) ?? 'null')
  } catch {
    return null
  }
}

export function ouvrirSession(s: Session) {
  localStorage.setItem(CLE, JSON.stringify(s))
}

export function fermerSession() {
  localStorage.removeItem(CLE)
  // Les réponses API et photos gardées pour le hors ligne appartiennent à ce compte.
  void globalThis.caches?.delete('api')
  void globalThis.caches?.delete('images')
}

/** UUID de l'appareil, stable (OtpVerify.appareilId). */
export function appareilId() {
  let id = localStorage.getItem('daba.appareil')
  if (!id) {
    id = crypto.randomUUID()
    localStorage.setItem('daba.appareil', id)
  }
  return id
}

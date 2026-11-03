import { useSyncExternalStore } from 'react'
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

// Connexion et déconnexion ne changent pas toujours l'URL (connexion depuis « / ») : l'App s'abonne à la session elle-même.
const CHANGEMENT = 'daba:session'
const abonner = (f: () => void) => {
  addEventListener(CHANGEMENT, f)
  addEventListener('storage', f)
  return () => {
    removeEventListener(CHANGEMENT, f)
    removeEventListener('storage', f)
  }
}
export const useConnecte = () => useSyncExternalStore(abonner, () => localStorage.getItem(CLE) !== null)

export function ouvrirSession(s: Session) {
  localStorage.setItem(CLE, JSON.stringify(s))
  dispatchEvent(new Event(CHANGEMENT))
}

export function fermerSession() {
  localStorage.removeItem(CLE)
  dispatchEvent(new Event(CHANGEMENT))
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

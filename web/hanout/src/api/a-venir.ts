// Endpoints absents du contrat v0.2.0 (§6 : réception / écart, contestation).
// Typés à la main en attendant leur ajout dans api/openapi.yaml par le back-end ;
// à remplacer alors par `api.POST` et les types générés.
import { entetes, surReponse } from './client'

export const MOTIFS = ['MONTANT', 'QUANTITE', 'NON_RECU', 'DEJA_PAYE', 'AUTRE'] as const
export type Motif = (typeof MOTIFS)[number]

export type ReceptionSaisie = {
  id: string
  conforme: boolean
  /** `ligne` = rang dans `Document.lignes` (un produit peut y figurer deux fois, promo et normal). */
  ecarts?: { ligne: number; quantiteRecue: number }[]
}

export type ContestationSaisie = {
  id: string
  cible: 'DOCUMENT' | 'COMPTE_CREDIT'
  cibleId: string
  motif: Motif
  commentaire?: string
}

async function poster(chemin: string, corps: object) {
  const r = await fetch(chemin, {
    method: 'POST',
    headers: entetes(new Headers({ 'Content-Type': 'application/json' })),
    body: JSON.stringify(corps),
  })
  await surReponse(r)
  return r.ok
}

export const confirmerReception = (documentId: string, r: ReceptionSaisie) =>
  poster(`/v1/documents/${documentId}/reception`, r)

export const contester = (c: ContestationSaisie) => poster('/v1/contestations', c)

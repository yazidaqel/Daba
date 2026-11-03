import type { S } from './client'

type Config = S['ConfigurationCanaux']

// Corps du PUT /v1/admin/canaux : la configuration éditée, sans le champ en lecture seule fournisseursDisponibles.
export const corpsPut = (c: Config): Config => ({
  canaux: c.canaux.map(({ canal, actif, fournisseur }) => ({ canal, actif, fournisseur })),
  types: c.types.map(({ type, principal, repli, inactifs }) => ({ type, principal, ...(repli && { repli }), inactifs: inactifs ?? [] })),
  organisations: c.organisations,
})

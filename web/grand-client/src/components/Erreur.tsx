import { ApiError } from '@/api/client'
import { useI18n } from '@/i18n'

/** Affiche une erreur : texte traduit, ou Problem RFC 9457 (règle + erreurs par champ). */
export function Erreur({ erreur }: { erreur: unknown }) {
  const { t } = useI18n()
  if (!erreur) return null
  let texte: string
  let details: string[] = []
  if (typeof erreur === 'string') texte = erreur
  else if (erreur instanceof ApiError) {
    const p = erreur.problem
    texte = p.detail ?? p.title ?? t('erreur.http', { status: erreur.status })
    // 403 : consentement CNDP manquant, ou rôle autre que GRAND_CLIENT (x-roles)
    if (erreur.status === 403) texte = p.type?.endsWith('consentement-requis') ? t('erreur.consentement') : t('connexion.roleRefuse')
    if (p.regle) texte = `${p.regle} — ${texte}`
    // Le message suit Accept-Language ; le nom technique du champ ne s'affiche pas.
    details = (p.erreurs ?? []).map((e) => e.message)
  } else texte = t('erreur.reseau')
  return (
    <div role="alert" className="rounded-md border border-destructive/40 bg-destructive/5 p-3 text-sm text-destructive">
      <p>{texte}</p>
      {details.length > 0 && (
        <ul className="mt-1 list-disc ps-5">
          {details.map((d) => (
            <li key={d}>{d}</li>
          ))}
        </ul>
      )}
    </div>
  )
}

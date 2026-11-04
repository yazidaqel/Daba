import { CircleAlert, CircleCheck, Info } from 'lucide-react'
import { ApiError, codeProbleme } from '@/api/client'
import { useI18n, type Cle } from '@/i18n'

// Codes de Problem.type qui ont un texte propre ; les autres affichent `detail` (traduit par Accept-Language).
const TEXTES: Record<string, Cle> = {
  'consentement-requis': 'erreur.consentement',
  'role-non-autorise': 'connexion.roleRefuse',
  'appareil-revoque': 'erreur.appareilRevoque',
  'code-invalide': 'erreur.codeInvalide',
  'trop-de-demandes': 'erreur.tropDeDemandes',
}

/** Affiche une erreur : texte traduit, ou Problem RFC 9457 (règle + erreurs par champ). */
export function Erreur({ erreur }: { erreur: unknown }) {
  const { t } = useI18n()
  if (!erreur) return null
  let texte: string
  let details: string[] = []
  if (typeof erreur === 'string') texte = erreur
  else if (erreur instanceof ApiError) {
    const p = erreur.problem
    const cle = TEXTES[codeProbleme(p) ?? '']
    texte = cle ? t(cle) : (p.detail ?? p.title ?? t('erreur.http', { status: erreur.status }))
    if (p.regle) texte = `${p.regle} — ${texte}`
    // Le message suit Accept-Language ; le nom technique du champ ne s'affiche pas.
    details = (p.erreurs ?? []).map((e) => e.message)
  } else texte = t('erreur.reseau')
  return (
    <div role="alert" className="flex gap-2 rounded-md border border-destructive/40 bg-destructive-soft p-3 text-sm text-destructive">
      <CircleAlert className="mt-0.5 size-4 shrink-0" aria-hidden />
      <div>
        <p>{texte}</p>
        {details.length > 0 && (
          <ul className="mt-1 list-disc ps-5">
            {details.map((d) => (
              <li key={d}>{d}</li>
            ))}
          </ul>
        )}
      </div>
    </div>
  )
}

/** Message d'information (bascule de méthode décidée par le serveur, par exemple) ou de réussite : icône + texte. */
export function Message({ ton, children }: { ton: 'info' | 'succes'; children: React.ReactNode }) {
  const Icone = ton === 'info' ? Info : CircleCheck
  return (
    <div
      role="status"
      className={`flex gap-2 rounded-md border p-3 text-sm ${ton === 'info' ? 'border-primary/30 bg-primary-soft' : 'border-success/40 bg-success-soft'}`}
    >
      <Icone className={`mt-0.5 size-4 shrink-0 ${ton === 'info' ? 'text-primary' : 'text-success'}`} aria-hidden />
      <p>{children}</p>
    </div>
  )
}

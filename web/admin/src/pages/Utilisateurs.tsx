import { useState, type FormEvent } from 'react'
import { ShieldAlert } from 'lucide-react'
import { $api, type S } from '@/api/client'
import { Etat, Pagination, SELECT } from '@/components/communs'
import FicheUtilisateur from '@/components/FicheUtilisateur'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { useI18n } from '@/i18n'

type Role = S['Utilisateur']['roles'][number]
const ROLES: Role[] = ['AMBULANT', 'HANOUT', 'GROSSISTE', 'GRAND_CLIENT', 'ADMIN']

// Support : la recherche (téléphone ou nom) reste dans l'état de l'écran, jamais dans l'adresse (CNDP).
export default function Utilisateurs() {
  const { t } = useI18n()
  const [saisie, setSaisie] = useState('')
  const [q, setQ] = useState<string | null>(null)
  const [role, setRole] = useState<Role | ''>('')
  const [page, setPage] = useState(0)
  const requete = $api.useQuery(
    'get',
    '/v1/admin/utilisateurs',
    { params: { query: { q: q || undefined, role: role || undefined, page, taille: 20 } } },
    { enabled: q !== null },
  )

  function chercher(e: FormEvent) {
    e.preventDefault()
    setQ(saisie.trim().length >= 2 ? saisie.trim() : '')
    setPage(0)
  }

  return (
    <section className="space-y-4">
      <h1 className="text-2xl font-semibold">{t('users.titre')}</h1>
      <Alert>
        <ShieldAlert aria-hidden />
        <AlertDescription>{t('users.cndp')}</AlertDescription>
      </Alert>
      <form onSubmit={chercher} className="flex flex-wrap items-end gap-2">
        <div className="space-y-1">
          <Label htmlFor="u-q">{t('users.recherche')}</Label>
          <Input id="u-q" autoComplete="off" value={saisie} onChange={(e) => setSaisie(e.target.value)} />
        </div>
        <div className="space-y-1">
          <Label htmlFor="u-role">{t('users.role')}</Label>
          <select id="u-role" className={SELECT} value={role} onChange={(e) => (setRole(e.target.value as Role | ''), setPage(0))}>
            <option value="">{t('users.tousRoles')}</option>
            {ROLES.map((r) => (
              <option key={r} value={r}>{t(`role.${r}`)}</option>
            ))}
          </select>
        </div>
        <Button type="submit">{t('app.rechercher')}</Button>
      </form>
      <p className="text-muted-foreground text-sm">{t('users.rechercheAide')}</p>
      {q !== null && (
        <Etat requete={requete}>
          {(d) => (
            <div className="space-y-3">
              {d.contenu.map((u) => (
                <FicheUtilisateur key={u.id} utilisateur={u} />
              ))}
              <Pagination meta={d} onPage={setPage} />
            </div>
          )}
        </Etat>
      )}
    </section>
  )
}

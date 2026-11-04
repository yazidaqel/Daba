import { useState } from 'react'
import { KeyRound, MessageSquareText, Plus, ShieldAlert, Trash2 } from 'lucide-react'
import { $api, queryClient, type S } from '@/api/client'
import { Erreur, Etat, SELECT, Succes } from '@/components/communs'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { useI18n } from '@/i18n'

type Config = S['ConfigurationAuthentification']
type Methode = S['MethodeConnexion']
const METHODES: Methode[] = ['OTP', 'MOT_DE_PASSE', 'MOT_DE_PASSE_ET_OTP']
const ROLES: S['RolePortail'][] = ['GROSSISTE', 'GRAND_CLIENT', 'ADMIN']

// Méthode de connexion des portails web, par rôle et par organisation (GET/PUT /v1/admin/authentification).
export default function Connexion() {
  const { t } = useI18n()
  const requete = $api.useQuery('get', '/v1/admin/authentification')
  return (
    <section className="space-y-4">
      <h1 className="text-2xl font-semibold">{t('auth.titre')}</h1>
      <Card size="sm">
        <CardContent className="space-y-2 text-sm">
          <p className="flex gap-2"><MessageSquareText aria-hidden className="mt-0.5 size-4 shrink-0" />{t('auth.explicationOTP')}</p>
          <p className="flex gap-2"><KeyRound aria-hidden className="mt-0.5 size-4 shrink-0" />{t('auth.explicationMOT_DE_PASSE')}</p>
          <p className="flex gap-2"><ShieldAlert aria-hidden className="mt-0.5 size-4 shrink-0" />{t('auth.explicationMOT_DE_PASSE_ET_OTP')}</p>
          <p className="text-muted-foreground">{t('auth.explicationCommune')}</p>
        </CardContent>
      </Card>
      <Etat requete={requete}>{(d) => <Formulaire initiale={d} />}</Etat>
    </section>
  )
}

function Formulaire({ initiale }: { initiale: Config }) {
  const { t } = useI18n()
  const [config, setConfig] = useState(initiale)
  const [ajout, setAjout] = useState('')
  const [fermerSessions, setFermerSessions] = useState(false)
  // ponytail: 200 premières organisations (tri par nom) ; une recherche sera utile au-delà.
  const organisations = $api.useQuery('get', '/v1/admin/organisations', { params: { query: { taille: 200 } } })
  const enregistrer = $api.useMutation('put', '/v1/admin/authentification', {
    // fermerSessions est une action ponctuelle, jamais enregistrée : la case se décoche après l'envoi.
    onSuccess: (c) => (setConfig(c), setFermerSessions(false), queryClient.invalidateQueries({ queryKey: ['get', '/v1/admin/authentification'] })),
  })
  const modifier = (m: Partial<Config>) => (setConfig({ ...config, ...m }), enregistrer.reset())
  const methodeRole = (r: S['RolePortail']) => config.roles.find((x) => x.role === r)?.methode ?? 'OTP'
  const setMethodeRole = (role: S['RolePortail'], methode: Methode) =>
    modifier({ roles: ROLES.map((r) => ({ role: r, methode: r === role ? methode : methodeRole(r) })) })
  const nomOrg = (id: string) => organisations.data?.contenu.find((o) => o.id === id)?.nom ?? id
  const libres = organisations.data?.contenu.filter((o) => !config.organisations.some((c) => c.organisationId === o.id)) ?? []
  const choixMethode = (valeur: Methode, onChange: (m: Methode) => void, label: string) => (
    <select aria-label={label} className={SELECT} value={valeur} onChange={(e) => onChange(e.target.value as Methode)}>
      {METHODES.map((m) => <option key={m} value={m}>{t(`methode.${m}`)}</option>)}
    </select>
  )

  return (
    <div className="space-y-4">
      <Card>
        <CardHeader>
          <CardTitle>{t('auth.parRole')}</CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t('users.role')}</TableHead>
                <TableHead>{t('auth.methode')}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {ROLES.map((r) => (
                <TableRow key={r}>
                  <TableCell className="font-medium">{t(`role.${r}`)}</TableCell>
                  <TableCell>{choixMethode(methodeRole(r), (m) => setMethodeRole(r, m), t('auth.methodeDe', { nom: t(`role.${r}`) }))}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
          {methodeRole('ADMIN') !== 'OTP' && (
            <Alert variant="warning">
              <ShieldAlert aria-hidden />
              <AlertDescription>{t('auth.avertissementAdmin')}</AlertDescription>
            </Alert>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>{t('auth.parOrganisation')}</CardTitle>
          <p className="text-muted-foreground text-sm">{t('auth.parOrganisationAide')}</p>
        </CardHeader>
        <CardContent className="space-y-3">
          {config.organisations.length > 0 && (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t('canaux.organisation')}</TableHead>
                  <TableHead>{t('auth.methode')}</TableHead>
                  <TableHead />
                </TableRow>
              </TableHeader>
              <TableBody>
                {config.organisations.map((o, i) => (
                  <TableRow key={o.organisationId}>
                    <TableCell className="font-medium">{nomOrg(o.organisationId)}</TableCell>
                    <TableCell>
                      {choixMethode(
                        o.methode,
                        (m) => modifier({ organisations: config.organisations.map((x, j) => (j === i ? { ...x, methode: m } : x)) }),
                        t('auth.methodeDe', { nom: nomOrg(o.organisationId) }),
                      )}
                    </TableCell>
                    <TableCell className="text-end">
                      <Button
                        size="icon"
                        variant="ghost"
                        aria-label={t('canaux.retirerOrg', { nom: nomOrg(o.organisationId) })}
                        onClick={() => modifier({ organisations: config.organisations.filter((_, j) => j !== i) })}
                      >
                        <Trash2 aria-hidden />
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
          <div className="flex flex-wrap items-center gap-2">
            <select aria-label={t('canaux.organisation')} className={SELECT} value={ajout} onChange={(e) => setAjout(e.target.value)}>
              <option value="">{t('canaux.choisirOrg')}</option>
              {libres.map((o) => <option key={o.id} value={o.id}>{o.nom} · {t(`typeOrg.${o.type}`)}</option>)}
            </select>
            <Button
              size="sm"
              variant="outline"
              disabled={!ajout}
              onClick={() => {
                const type = organisations.data?.contenu.find((o) => o.id === ajout)?.type
                const role = type === 'GRAND_CLIENT' ? 'GRAND_CLIENT' : 'GROSSISTE'
                modifier({ organisations: [...config.organisations, { organisationId: ajout, methode: methodeRole(role) }] })
                setAjout('')
              }}
            >
              <Plus aria-hidden />
              {t('canaux.ajouterOrg')}
            </Button>
          </div>
        </CardContent>
      </Card>

      <div className="space-y-1">
        <label className="flex items-center gap-2 text-sm font-medium">
          <input type="checkbox" checked={fermerSessions} onChange={(e) => (setFermerSessions(e.target.checked), enregistrer.reset())} />
          {t('auth.fermerSessions')}
        </label>
        <p className="text-muted-foreground ps-6 text-sm">{t('auth.fermerSessionsAide')}</p>
      </div>
      <div className="flex flex-wrap items-center gap-2">
        <Button
          disabled={enregistrer.isPending}
          onClick={() =>
            enregistrer.mutate({ body: { roles: ROLES.map((r) => ({ role: r, methode: methodeRole(r) })), organisations: config.organisations, fermerSessions } })
          }
        >
          {t('canaux.enregistrer')}
        </Button>
        <Button variant="outline" onClick={() => (setConfig(initiale), enregistrer.reset())}>{t('canaux.annuler')}</Button>
      </div>
      {enregistrer.error && <Erreur erreur={enregistrer.error} />}
      {enregistrer.isSuccess && <Succes>{t('auth.enregistre')}</Succes>}
    </div>
  )
}

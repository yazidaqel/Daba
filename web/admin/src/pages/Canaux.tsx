import { useState } from 'react'
import { CircleCheck, CircleSlash, KeyRound, Plus, Trash2 } from 'lucide-react'
import { $api, queryClient, type S } from '@/api/client'
import { Erreur, Etat, Etiquette, SELECT, Succes } from '@/components/communs'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { useI18n } from '@/i18n'
import { corpsPut } from '@/api/canaux'

type Config = S['ConfigurationCanaux']
type Canal = S['Canal']
const CANAUX: Canal[] = ['PUSH', 'SMS', 'WHATSAPP']

const basculer = (liste: Canal[] | undefined, canal: Canal) =>
  liste?.includes(canal) ? liste.filter((c) => c !== canal) : [...(liste ?? []), canal]

export default function Canaux() {
  const { t } = useI18n()
  const requete = $api.useQuery('get', '/v1/admin/canaux')
  return (
    <section className="space-y-4">
      <h1 className="text-2xl font-semibold">{t('canaux.titre')}</h1>
      <Alert>
        <KeyRound aria-hidden />
        <AlertDescription>{t('canaux.secrets')}</AlertDescription>
      </Alert>
      <Etat requete={requete}>{(d) => <Formulaire initiale={d} />}</Etat>
    </section>
  )
}

function Formulaire({ initiale }: { initiale: Config }) {
  const { t } = useI18n()
  const [config, setConfig] = useState(initiale)
  const [ajout, setAjout] = useState('')
  // ponytail: 200 premières organisations (tri par nom) ; une recherche sera utile au-delà.
  const organisations = $api.useQuery('get', '/v1/admin/organisations', { params: { query: { taille: 200 } } })
  const enregistrer = $api.useMutation('put', '/v1/admin/canaux', {
    onSuccess: (c) => (setConfig(c), queryClient.invalidateQueries({ queryKey: ['get', '/v1/admin/canaux'] })),
  })
  const nomOrg = (id: string) => organisations.data?.contenu.find((o) => o.id === id)?.nom ?? id
  const modifier = (m: Partial<Config>) => (setConfig({ ...config, ...m }), enregistrer.reset())
  const canal = (i: number, m: Partial<S['ParametreCanal']>) => modifier({ canaux: config.canaux.map((c, j) => (j === i ? { ...c, ...m } : c)) })
  const type = (i: number, m: Partial<S['RoutageMessage']>) => modifier({ types: config.types.map((r, j) => (j === i ? { ...r, ...m } : r)) })
  const org = (i: number, inactifs: Canal[]) => modifier({ organisations: config.organisations.map((o, j) => (j === i ? { ...o, inactifs } : o)) })
  const libres = organisations.data?.contenu.filter((o) => !config.organisations.some((c) => c.organisationId === o.id)) ?? []

  return (
    <div className="space-y-4">
      <Card>
        <CardHeader>
          <CardTitle>{t('canaux.systeme')}</CardTitle>
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t('canaux.canal')}</TableHead>
                <TableHead>{t('canaux.ouvert')}</TableHead>
                <TableHead>{t('canaux.fournisseur')}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {config.canaux.map((c, i) => {
                const dispo = c.fournisseursDisponibles ?? [{ code: c.fournisseur, configure: true }]
                const choisi = dispo.find((f) => f.code === c.fournisseur)
                return (
                  <TableRow key={c.canal}>
                    <TableCell className="font-medium">{t(`canal.${c.canal}`)}</TableCell>
                    <TableCell>
                      <label className="flex items-center gap-2">
                        <input type="checkbox" checked={c.actif} onChange={(e) => canal(i, { actif: e.target.checked })} />
                        {c.actif ? t('canaux.ouvert') : t('canaux.coupe')}
                      </label>
                    </TableCell>
                    <TableCell>
                      <div className="flex flex-wrap items-center gap-2">
                        <select
                          aria-label={t('canaux.fournisseurDe', { canal: t(`canal.${c.canal}`) })}
                          className={SELECT}
                          value={c.fournisseur}
                          onChange={(e) => canal(i, { fournisseur: e.target.value as S['FournisseurCanal'] })}
                        >
                          {dispo.map((f) => (
                            <option key={f.code} value={f.code} disabled={!f.configure && f.code !== c.fournisseur}>
                              {f.configure ? t(`fournisseur.${f.code}`) : t('canaux.nonConfigure', { fournisseur: t(`fournisseur.${f.code}`) })}
                            </option>
                          ))}
                        </select>
                        {/* Sans fournisseursDisponibles, le serveur ne dit rien de l'état : on n'affiche rien. */}
                        {c.fournisseursDisponibles &&
                          (choisi?.configure === false ? (
                            <Etiquette icone={CircleSlash} variante="warning">{t('canaux.nonConfigureCourt')}</Etiquette>
                          ) : (
                            <Etiquette icone={CircleCheck} variante="success">{t('canaux.configure')}</Etiquette>
                          ))}
                      </div>
                    </TableCell>
                  </TableRow>
                )
              })}
            </TableBody>
          </Table>
          <p className="text-muted-foreground mt-2 text-sm">{t('canaux.journalAide')}</p>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>{t('canaux.routage')}</CardTitle>
          <p className="text-muted-foreground text-sm">{t('canaux.routageAide')}</p>
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t('canaux.typeMessage')}</TableHead>
                <TableHead>{t('canaux.principal')}</TableHead>
                <TableHead>{t('canaux.repli')}</TableHead>
                <TableHead>{t('canaux.inactifs')}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {config.types.map((r, i) => (
                <TableRow key={r.type}>
                  <TableCell className="font-medium whitespace-normal">{t(`type.${r.type}`)}</TableCell>
                  <TableCell>
                    <select
                      aria-label={t('canaux.principalDe', { type: t(`type.${r.type}`) })}
                      className={SELECT}
                      value={r.principal}
                      onChange={(e) => {
                        const principal = e.target.value as Canal
                        type(i, { principal, repli: r.repli === principal ? undefined : r.repli })
                      }}
                    >
                      {CANAUX.map((c) => <option key={c} value={c}>{t(`canal.${c}`)}</option>)}
                    </select>
                  </TableCell>
                  <TableCell>
                    <select
                      aria-label={t('canaux.repliDe', { type: t(`type.${r.type}`) })}
                      className={SELECT}
                      value={r.repli ?? ''}
                      onChange={(e) => type(i, { repli: (e.target.value || undefined) as Canal | undefined })}
                    >
                      <option value="">{t('canaux.sansRepli')}</option>
                      {CANAUX.filter((c) => c !== r.principal).map((c) => <option key={c} value={c}>{t(`canal.${c}`)}</option>)}
                    </select>
                  </TableCell>
                  <TableCell>
                    <div className="flex flex-wrap gap-3">
                      {CANAUX.map((c) => (
                        <label key={c} className="flex items-center gap-1">
                          <input type="checkbox" checked={r.inactifs?.includes(c) ?? false} onChange={() => type(i, { inactifs: basculer(r.inactifs, c) })} />
                          {t('canaux.couper', { canal: t(`canal.${c}`) })}
                        </label>
                      ))}
                    </div>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
          <p className="text-muted-foreground mt-2 text-sm">{t('canaux.otpAide')}</p>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>{t('canaux.parOrganisation')}</CardTitle>
          <p className="text-muted-foreground text-sm">{t('canaux.parOrganisationAide')}</p>
        </CardHeader>
        <CardContent className="space-y-3">
          {config.organisations.length > 0 && (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t('canaux.organisation')}</TableHead>
                  <TableHead>{t('canaux.inactifs')}</TableHead>
                  <TableHead />
                </TableRow>
              </TableHeader>
              <TableBody>
                {config.organisations.map((o, i) => (
                  <TableRow key={o.organisationId}>
                    <TableCell className="font-medium">{nomOrg(o.organisationId)}</TableCell>
                    <TableCell>
                      <div className="flex flex-wrap gap-3">
                        {CANAUX.map((c) => (
                          <label key={c} className="flex items-center gap-1">
                            <input type="checkbox" checked={o.inactifs.includes(c)} onChange={() => org(i, basculer(o.inactifs, c))} />
                            {t('canaux.couper', { canal: t(`canal.${c}`) })}
                          </label>
                        ))}
                      </div>
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
              onClick={() => (modifier({ organisations: [...config.organisations, { organisationId: ajout, inactifs: [] }] }), setAjout(''))}
            >
              <Plus aria-hidden />
              {t('canaux.ajouterOrg')}
            </Button>
          </div>
        </CardContent>
      </Card>

      <div className="flex flex-wrap items-center gap-2">
        <Button disabled={enregistrer.isPending} onClick={() => enregistrer.mutate({ body: corpsPut(config) })}>{t('canaux.enregistrer')}</Button>
        <Button variant="outline" onClick={() => (setConfig(initiale), enregistrer.reset())}>{t('canaux.annuler')}</Button>
      </div>
      {enregistrer.error && <Erreur erreur={enregistrer.error} />}
      {enregistrer.isSuccess && <Succes>{t('canaux.enregistre')}</Succes>}
    </div>
  )
}

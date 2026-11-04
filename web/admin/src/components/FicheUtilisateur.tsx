import { Ban, BellOff, BellRing, CircleCheck, CircleSlash, History, KeyRound, LockOpen, MapPin, MapPinOff, ShieldCheck, ShieldOff, Smartphone, UserCheck, UserRound, UserX } from 'lucide-react'
import { Link } from 'react-router'
import { $api, lireSession, queryClient, type S } from '@/api/client'
import { Confirmer, Erreur, Etiquette } from '@/components/communs'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { useI18n } from '@/i18n'

type U = S['UtilisateurAdmin']

// Fiche de support : rôles, organisation, consentement CNDP, appareils ; désactivation et révocation confirmées.
export default function FicheUtilisateur({ utilisateur: u, lienOrganisation = true }: { utilisateur: U; lienOrganisation?: boolean }) {
  const { t, date } = useI18n()
  const options = { onSuccess: () => queryClient.invalidateQueries() }
  const desactiver = $api.useMutation('post', '/v1/admin/utilisateurs/{utilisateurId}/desactivation', options)
  const reactiver = $api.useMutation('post', '/v1/admin/utilisateurs/{utilisateurId}/reactivation', options)
  const revoquer = $api.useMutation('post', '/v1/admin/appareils/{appareilId}/revocation', options)
  const reinitialiser = $api.useMutation('post', '/v1/admin/utilisateurs/{utilisateurId}/reinitialisation-mot-de-passe', options)
  const chemin = { params: { path: { utilisateurId: u.id } } }
  const moi = lireSession()?.utilisateur.id === u.id
  // Seuls les comptes de portail ont un mot de passe (jamais AMBULANT ni HANOUT).
  const portail = u.roles.some((r) => r === 'GROSSISTE' || r === 'GRAND_CLIENT' || r === 'ADMIN')
  const c = u.consentement
  const designation = u.nom ?? u.telephone

  return (
    <Card size="sm">
      <CardHeader>
        <CardTitle className="flex flex-wrap items-center gap-2">
          {u.nom ?? t('utilisateur.sansNom')}
          <span dir="ltr" className="text-muted-foreground font-normal">{u.telephone}</span>
          {u.actif ? (
            <Etiquette icone={UserCheck} variante="success">{t('utilisateur.actif')}</Etiquette>
          ) : (
            <Etiquette icone={UserX} variante="destructive">{t('utilisateur.desactive')}</Etiquette>
          )}
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-3">
        <dl className="grid gap-x-4 gap-y-1 sm:grid-cols-[auto_1fr]">
          <dt className="text-muted-foreground">{t('utilisateur.roles')}</dt>
          <dd>{u.roles.map((r) => t(`role.${r}`)).join(', ')}</dd>
          <dt className="text-muted-foreground">{t('utilisateur.organisation')}</dt>
          <dd>
            {lienOrganisation ? <Link className="text-primary underline-offset-4 hover:underline" to={`/organisations/${u.organisation.id}`}>{u.organisation.nom}</Link> : u.organisation.nom}
            {' · '}
            {t(`typeOrg.${u.organisation.type}`)}
          </dd>
          <dt className="text-muted-foreground">{t('utilisateur.inscrit')}</dt>
          <dd>{date(u.creeLe)}{u.desactiveLe && ` · ${t('utilisateur.desactiveLe', { date: date(u.desactiveLe) })}`}</dd>
          <dt className="text-muted-foreground">{t('auth.methode')}</dt>
          <dd className="flex flex-wrap items-center gap-2">
            {t(`methode.${u.methodeConnexion}`)}
            {portail && (u.motDePasseDefini ? (
              <Etiquette icone={KeyRound} variante="success">{t('mdp.defini')}</Etiquette>
            ) : (
              <Etiquette icone={CircleSlash} variante="outline">{t('mdp.nonDefini')}</Etiquette>
            ))}
            {portail && u.motDePasseModifieLe && <span className="text-muted-foreground">{t('mdp.modifieLe', { date: date(u.motDePasseModifieLe) })}</span>}
          </dd>
          <dt className="text-muted-foreground">{t('consentement.titre')}</dt>
          <dd className="flex flex-wrap items-center gap-2">
            {!c ? (
              <Etiquette icone={CircleSlash} variante="outline">{t('consentement.aucun')}</Etiquette>
            ) : (
              <>
                {c.traitementDonnees ? (
                  <Etiquette icone={ShieldCheck} variante="success">{t('consentement.donnees')}</Etiquette>
                ) : (
                  <Etiquette icone={ShieldOff} variante="warning">{t('consentement.donneesRetire')}</Etiquette>
                )}
                {c.geolocalisation ? (
                  <Etiquette icone={MapPin} variante="secondary">{t('consentement.geo')}</Etiquette>
                ) : (
                  <Etiquette icone={MapPinOff} variante="outline">{t('consentement.geoNon')}</Etiquette>
                )}
                <span className="text-muted-foreground">
                  {t('consentement.version', { version: c.versionTexte, date: date(c.enregistreLe) })}
                </span>
              </>
            )}
          </dd>
        </dl>

        {u.appareils.length === 0 ? (
          <p className="text-muted-foreground">{t('appareil.aucun')}</p>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t('appareil.titre')}</TableHead>
                <TableHead>{t('appareil.vu')}</TableHead>
                <TableHead>{t('appareil.etat')}</TableHead>
                <TableHead />
              </TableRow>
            </TableHeader>
            <TableBody>
              {u.appareils.map((a) => (
                <TableRow key={a.appareilId}>
                  <TableCell>
                    <div className="flex items-center gap-1">
                      <Smartphone aria-hidden className="size-4" />
                      {a.plateforme ?? t('appareil.plateformeInconnue')}
                      {a.prefixeNumerotation && <code dir="ltr" className="text-muted-foreground">{a.prefixeNumerotation}</code>}
                    </div>
                    <code dir="ltr" className="text-muted-foreground text-xs">{a.appareilId}</code>
                    {a.autresComptes.length > 0 && (
                      <div className="text-muted-foreground text-xs">
                        {t('appareil.autresComptes')}{' '}
                        {a.autresComptes.map((c, i) => (
                          <span key={c.id}>{i > 0 && ', '}<bdi>{c.nom ?? c.telephone}</bdi></span>
                        ))}
                      </div>
                    )}
                  </TableCell>
                  <TableCell>
                    {a.vuLe ? date(a.vuLe) : '—'}
                    <div className="text-muted-foreground text-xs">{t('appareil.premiere', { date: date(a.creeLe) })}</div>
                  </TableCell>
                  <TableCell>
                    <div className="flex flex-wrap gap-1">
                      {a.compteActuel ? (
                        <Etiquette icone={UserRound} variante="secondary">{t('appareil.compteActuel')}</Etiquette>
                      ) : (
                        <Etiquette icone={History} variante="outline">{t('appareil.compteAncien')}</Etiquette>
                      )}
                      {a.revoque ? (
                        <Etiquette icone={Ban} variante="destructive">
                          {a.revoqueLe ? t('appareil.revoqueLe', { date: date(a.revoqueLe) }) : t('appareil.revoque')}
                        </Etiquette>
                      ) : a.sessionOuverte ? (
                        <Etiquette icone={LockOpen} variante="success">{t('appareil.sessionOuverte')}</Etiquette>
                      ) : (
                        <Etiquette icone={CircleSlash} variante="outline">{t('appareil.sessionFermee')}</Etiquette>
                      )}
                      {!a.revoque && (a.pushActif ? (
                        <Etiquette icone={BellRing} variante="secondary">{t('appareil.pushActif')}</Etiquette>
                      ) : (
                        <Etiquette icone={BellOff} variante="outline">{t('appareil.pushInactif')}</Etiquette>
                      ))}
                    </div>
                  </TableCell>
                  <TableCell className="text-end">
                    {!a.revoque && (
                      <Confirmer
                        declencheur={<Button size="sm" variant="destructive">{t('appareil.revoquer')}</Button>}
                        titre={t('appareil.revoquerTitre', { nom: designation })}
                        texte={
                          <>
                            <p>{t('appareil.revoquerTexte1')}</p>
                            <p className="text-destructive font-medium">{t('appareil.revoquerTexte2')}</p>
                            {/* La révocation vise l'appareil physique : tous ses comptes sont effacés du téléphone. */}
                            <p className="text-foreground font-medium">{t('appareil.revoquerTousComptes', { n: a.autresComptes.length + 1 })}</p>
                            <ul className="text-foreground list-disc ps-5">
                              {[{ id: u.id, nom: u.nom, telephone: u.telephone }, ...a.autresComptes].map((c) => (
                                <li key={c.id}><bdi>{c.nom ?? c.telephone}</bdi>{c.nom && <> · <bdi dir="ltr">{c.telephone}</bdi></>}</li>
                              ))}
                            </ul>
                            <p>{t('appareil.revoquerTexte3')}</p>
                          </>
                        }
                        confirmer={t('appareil.revoquerConfirmer')}
                        onConfirmer={() => revoquer.mutate({ params: { path: { appareilId: a.appareilId } } })}
                      />
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}

        <div className="flex flex-wrap items-center gap-2">
          {portail && u.motDePasseDefini && (
            <Confirmer
              declencheur={<Button size="sm" variant="outline"><KeyRound aria-hidden />{t('mdp.reinitialiser')}</Button>}
              titre={t('mdp.reinitialiserTitre', { nom: designation })}
              texte={<p>{t(moi ? 'mdp.reinitialiserTexteMoi' : 'mdp.reinitialiserTexte')}</p>}
              confirmer={t('mdp.reinitialiser')}
              onConfirmer={() => reinitialiser.mutate(chemin)}
            />
          )}
          {u.actif && !moi && (
            <Confirmer
              declencheur={<Button size="sm" variant="destructive">{t('utilisateur.desactiver')}</Button>}
              titre={t('utilisateur.desactiverTitre', { nom: designation })}
              texte={<p>{t('utilisateur.desactiverTexte')}</p>}
              confirmer={t('utilisateur.desactiver')}
              onConfirmer={() => desactiver.mutate(chemin)}
            />
          )}
          {!u.actif && (
            <Confirmer
              declencheur={<Button size="sm" variant="outline"><CircleCheck aria-hidden />{t('utilisateur.reactiver')}</Button>}
              titre={t('utilisateur.reactiverTitre', { nom: designation })}
              texte={<p>{t('utilisateur.reactiverTexte')}</p>}
              confirmer={t('utilisateur.reactiver')}
              destructif={false}
              onConfirmer={() => reactiver.mutate(chemin)}
            />
          )}
        </div>
        {[desactiver, reactiver, revoquer, reinitialiser].map((m, i) => m.error && <Erreur key={i} erreur={m.error} />)}
      </CardContent>
    </Card>
  )
}

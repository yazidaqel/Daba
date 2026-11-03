import { useState, type FormEvent } from 'react'
import { ArrowLeft, UserPlus } from 'lucide-react'
import { Link, useParams } from 'react-router'
import { $api, queryClient } from '@/api/client'
import { Erreur, Etat, Succes } from '@/components/communs'
import FicheUtilisateur from '@/components/FicheUtilisateur'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { useI18n } from '@/i18n'
import { versE164 } from '@/telephone'

export default function Organisation() {
  const { t, date, nombre } = useI18n()
  const { organisationId = '' } = useParams()
  const requete = $api.useQuery('get', '/v1/admin/organisations/{organisationId}', { params: { path: { organisationId } } })

  return (
    <section className="space-y-4">
      <Button asChild variant="link" className="px-0">
        <Link to="/organisations">
          <ArrowLeft aria-hidden className="rtl:rotate-180" />
          {t('orgs.retour')}
        </Link>
      </Button>
      <Etat requete={requete}>
        {(o) => (
          <>
            <h1 className="text-2xl font-semibold">{o.nom}</h1>
            <dl className="grid gap-x-4 gap-y-1 text-sm sm:grid-cols-[auto_1fr]">
              <dt className="text-muted-foreground">{t('orgs.type')}</dt>
              <dd>{t(`typeOrg.${o.type}`)}</dd>
              <dt className="text-muted-foreground">{t('orgs.creeLe')}</dt>
              <dd>{date(o.creeLe)}</dd>
              {o.grossiste && (
                <>
                  <dt className="text-muted-foreground">{t('orgs.adresse')}</dt>
                  <dd>{[o.grossiste.adresse, o.grossiste.ville].filter(Boolean).join(', ') || '—'}</dd>
                  <dt className="text-muted-foreground">{t('orgs.position')}</dt>
                  <dd><bdi dir="ltr">{o.grossiste.position.lat}, {o.grossiste.position.lon}</bdi></dd>
                </>
              )}
              <dt className="text-muted-foreground">{t('orgs.utilisateurs')}</dt>
              <dd>{nombre(o.nombreUtilisateurs)}</dd>
            </dl>
            {(o.type === 'GROSSISTE' || o.type === 'GRAND_CLIENT') && <AjoutMembre organisationId={o.id} />}
            <div className="space-y-3">
              {o.utilisateurs.map((u) => (
                <FicheUtilisateur key={u.id} utilisateur={u} lienOrganisation={false} />
              ))}
            </div>
          </>
        )}
      </Etat>
    </section>
  )
}

function AjoutMembre({ organisationId }: { organisationId: string }) {
  const { t } = useI18n()
  const [telephone, setTelephone] = useState('')
  const [nom, setNom] = useState('')
  const [invalide, setInvalide] = useState(false)
  const ajouter = $api.useMutation('post', '/v1/admin/organisations/{organisationId}/utilisateurs', {
    onSuccess: () => (setTelephone(''), setNom(''), queryClient.invalidateQueries()),
  })

  function soumettre(e: FormEvent) {
    e.preventDefault()
    const tel = versE164(telephone)
    setInvalide(!tel)
    if (tel) ajouter.mutate({ params: { path: { organisationId } }, body: { telephone: tel, nom: nom.trim() || undefined } })
  }

  return (
    <Card size="sm">
      <CardHeader>
        <CardTitle>{t('membre.titre')}</CardTitle>
        <p className="text-muted-foreground text-sm">{t('membre.aide')}</p>
      </CardHeader>
      <CardContent className="space-y-3">
        <form onSubmit={soumettre} className="flex flex-wrap items-end gap-2">
          <div className="space-y-1">
            <Label htmlFor="a-tel">{t('membre.telephone')}</Label>
            <Input id="a-tel" type="tel" dir="ltr" value={telephone} onChange={(e) => setTelephone(e.target.value)} required />
          </div>
          <div className="space-y-1">
            <Label htmlFor="a-nom">{t('membre.nom')}</Label>
            <Input id="a-nom" value={nom} onChange={(e) => setNom(e.target.value)} />
          </div>
          <Button type="submit" disabled={ajouter.isPending}>
            <UserPlus aria-hidden />
            {t('membre.ajouter')}
          </Button>
        </form>
        {invalide && <Erreur erreur={{ detail: t('login.telephoneInvalide') }} />}
        {ajouter.error && <Erreur erreur={ajouter.error} />}
        {ajouter.data && <Succes>{t('membre.ajoute', { telephone: ajouter.data.telephone })}</Succes>}
      </CardContent>
    </Card>
  )
}

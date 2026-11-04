import { useState } from 'react'
import { ApiError, ecrireSession, type S } from '@/api/client'
import { useI18n } from '@/i18n'
import { versE164 } from '@/lib/format'
import { choisirMethode, etatInitial, oublie, soumettre, texteAvis, type Etat, type Methode, type Resultat } from '@/connexion'
import { ChampMotDePasse } from '@/components/ChampMotDePasse'
import { SelecteurLangue } from '@/components/SelecteurLangue'
import { Erreur, Message } from '@/components/Erreur'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

const METHODES: Methode[] = ['motDePasse', 'code']

// Séquences de connexion : voir src/connexion.ts (même comportement que le portail grossiste).
export function Connexion({ onSession, motif }: { onSession: (s: S['Session']) => void; motif?: string }) {
  const { t, langue } = useI18n()
  const [etat, setEtat] = useState<Etat>(etatInitial)
  const [saisieTel, setSaisieTel] = useState('')
  const [motDePasse, setMotDePasse] = useState('')
  const [code, setCode] = useState('')
  const [nouveau, setNouveau] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [occupe, setOccupe] = useState(false)
  // Motif de fin de la session précédente, gardé en code (Problem.type) jusqu'à la première action.
  const [motifFin, setMotifFin] = useState<unknown>(motif && new ApiError(401, { type: `https://daba.ma/problemes/${motif}` }))
  const [roleRefuse, setRoleRefuse] = useState(false)

  function vider() {
    setMotDePasse('')
    setCode('')
    setNouveau('')
    setConfirmation('')
  }

  // Les mots de passe ne restent pas dans l'état d'un écran à l'autre.
  async function executer(action: () => Promise<Resultat>) {
    setOccupe(true)
    setMotifFin(undefined)
    setRoleRefuse(false)
    const { etat: suivant, session } = await action()
      .catch((): Resultat => ({ etat: { ...etat, erreur: 'erreur', info: undefined } }))
      .finally(() => setOccupe(false))
    if (session) {
      if (!session.utilisateur.roles.includes('GRAND_CLIENT')) return (vider(), setRoleRefuse(true))
      ecrireSession(session)
      return onSession(session)
    }
    if (suivant.etape.nom !== etat.etape.nom) vider()
    setEtat(suivant)
  }

  function telephoneValide() {
    const tel = versE164(saisieTel)
    if (!tel) setEtat({ ...etat, erreur: 'telephoneInvalide', info: undefined })
    return tel
  }

  function soumettreFormulaire(e: React.FormEvent) {
    e.preventDefault()
    if (etat.etape.nom === 'identifiant') {
      const telephone = telephoneValide()
      if (telephone) executer(() => soumettre(etat, { telephone, motDePasse }, langue))
    } else executer(() => soumettre(etat, { code, nouveau, confirmation }, langue))
  }

  function motDePasseOublie() {
    const telephone = telephoneValide()
    if (telephone) executer(() => oublie(etat, telephone, langue))
  }

  const retour = () => (vider(), setEtat({ ...etatInitial(etat.methode), telephone: etat.telephone }))
  const { etape } = etat
  const champCode = (
    <div className="space-y-2">
      <Label htmlFor="code">{t('connexion.code')}</Label>
      <Input
        id="code"
        dir="ltr"
        inputMode="numeric"
        autoComplete="one-time-code"
        maxLength={6}
        pattern="[0-9]{6}"
        required
        value={code}
        onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))}
      />
      <p className="text-sm text-muted-foreground">{t('connexion.codeEnvoye')}</p>
    </div>
  )

  return (
    <div className="flex min-h-screen items-center justify-center p-4">
      <Card className="w-full max-w-sm">
        <CardHeader className="flex flex-row items-center justify-between">
          <CardTitle>
            {etape.nom === 'definition'
              ? t(etape.raison === 'aDefinir' ? 'connexion.definitionADefinir' : 'connexion.definitionOublie')
              : t('app.titre')}
          </CardTitle>
          <SelecteurLangue />
        </CardHeader>
        <CardContent className="space-y-4">
          {etat.info && <Message ton="info">{t(`connexion.${etat.info}`)}</Message>}
          <form onSubmit={soumettreFormulaire} className="space-y-4">
            {etape.nom === 'identifiant' && (
              <>
                <div className="space-y-2">
                  <Label htmlFor="tel">{t('connexion.telephone')}</Label>
                  <Input
                    id="tel"
                    type="tel"
                    dir="ltr"
                    autoComplete={etat.methode === 'motDePasse' ? 'username' : 'tel'}
                    placeholder="06 12 34 56 78"
                    required
                    value={saisieTel}
                    onChange={(e) => setSaisieTel(e.target.value)}
                  />
                </div>
                <fieldset className="space-y-2">
                  <legend className="mb-2 text-sm font-medium">{t('connexion.methode')}</legend>
                  {METHODES.map((m) => (
                    <label key={m} className="flex items-center gap-2 text-sm">
                      <input type="radio" name="methode" value={m} checked={etat.methode === m} onChange={() => setEtat(choisirMethode(etat, m))} />
                      {t(m === 'motDePasse' ? 'connexion.methodeMotDePasse' : 'connexion.methodeCode')}
                    </label>
                  ))}
                </fieldset>
                {etat.methode === 'motDePasse' && (
                  <ChampMotDePasse id="mdp" label={t('connexion.motDePasse')} autoComplete="current-password" value={motDePasse} onChange={setMotDePasse} />
                )}
              </>
            )}
            {etape.nom === 'code' && champCode}
            {etape.nom === 'definition' && (
              <>
                {/* Pour le gestionnaire de mots de passe : le compte auquel ce mot de passe appartient. */}
                <input type="text" autoComplete="username" value={etat.telephone} readOnly hidden />
                {!etape.code && champCode}
                <ChampMotDePasse
                  id="nouveau"
                  label={t('connexion.nouveau')}
                  autoComplete="new-password"
                  value={nouveau}
                  onChange={setNouveau}
                  aide={t('connexion.regle')}
                />
                <ChampMotDePasse id="confirmation" label={t('connexion.confirmation')} autoComplete="new-password" value={confirmation} onChange={setConfirmation} />
              </>
            )}
            <Erreur erreur={roleRefuse ? t('connexion.roleRefuse') : etat.erreur ? texteAvis(t, etat.erreur, etat.attente) : motifFin} />
            <Button type="submit" className="w-full" disabled={occupe}>
              {etape.nom === 'definition'
                ? t('connexion.enregistrer')
                : etape.nom === 'identifiant' && etat.methode === 'code'
                  ? t('connexion.recevoirCode')
                  : t('connexion.valider')}
            </Button>
            {etape.nom === 'identifiant' && etat.methode === 'motDePasse' && (
              <Button type="button" variant="link" className="w-full" disabled={occupe} onClick={motDePasseOublie}>
                {t('connexion.oublie')}
              </Button>
            )}
            {etape.nom !== 'identifiant' && (
              <Button type="button" variant="link" className="w-full" onClick={retour}>
                {etape.nom === 'code' ? t('connexion.changerNumero') : t('connexion.retour')}
              </Button>
            )}
          </form>
        </CardContent>
      </Card>
    </div>
  )
}

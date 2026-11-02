import { Download, ShieldOff } from 'lucide-react'
import { useState } from 'react'
import { api } from '@/api/client'
import { Alerte, cleErreur } from '@/components/commun'
import { Button } from '@/components/ui/button'
import { useI18n } from '@/i18n'
import { naviguer } from '@/router'
import { fermerSession } from '@/session'
import type { Cle } from '@/textes'

/** Droit d'accès et retrait du consentement (loi 09-08). */
export function Donnees() {
  const { t, langue } = useI18n()
  const [erreur, setErreur] = useState<Cle>()

  async function essayer(action: () => Promise<boolean>) {
    setErreur(undefined)
    try {
      if (!(await action())) setErreur('erreur.generique')
    } catch {
      setErreur(cleErreur())
    }
  }

  const telecharger = () =>
    essayer(async () => {
      const { data } = await api.GET('/v1/moi/donnees', { parseAs: 'blob' })
      if (!data) return false
      const a = document.createElement('a')
      a.href = URL.createObjectURL(data)
      a.download = 'daba-mes-donnees.json'
      a.click()
      return true
    })

  const retirer = () =>
    essayer(async () => {
      const { data } = await api.PUT('/v1/moi/consentement', {
        body: { versionTexte: `hanout-1-${langue}`, traitementDonnees: false, langue },
      })
      if (!data) return false
      fermerSession()
      naviguer('/connexion', true)
      return true
    })

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold">{t('consentement.titre')}</h1>
      <p className="text-lg leading-relaxed">{t('consentement.texte')}</p>
      <div className="grid gap-3">
        <Button size="lg" variant="outline" onClick={telecharger}>
          <Download />
          {t('donnees.telecharger')}
        </Button>
        <Button size="lg" variant="destructive" onClick={retirer}>
          <ShieldOff />
          {t('donnees.retirer')}
        </Button>
      </div>
      <Alerte cle={erreur} />
    </div>
  )
}

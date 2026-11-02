import { ShieldCheck } from 'lucide-react'
import { useState } from 'react'
import { api } from '@/api/client'
import { Alerte, cleErreur } from '@/components/commun'
import { Button } from '@/components/ui/button'
import { useI18n } from '@/i18n'
import { naviguer } from '@/router'
import { fermerSession } from '@/session'
import type { Cle } from '@/textes'

/** Consentement CNDP (loi 09-08), demandé à la première connexion. */
export function Consentement({ suite }: { suite: string }) {
  const { t, langue } = useI18n()
  const [erreur, setErreur] = useState<Cle>()
  const [occupe, setOccupe] = useState(false)

  async function repondre(accord: boolean) {
    setOccupe(true)
    setErreur(undefined)
    try {
      const { data } = await api.PUT('/v1/moi/consentement', {
        body: { versionTexte: `hanout-1-${langue}`, traitementDonnees: accord, langue },
      })
      if (!accord) {
        fermerSession()
        naviguer('/connexion', true)
      } else if (data) naviguer(suite, true)
      else setErreur('erreur.generique')
    } catch {
      setErreur(cleErreur())
    } finally {
      setOccupe(false)
    }
  }

  return (
    <div className="space-y-6">
      <h1 className="flex items-center gap-2 text-2xl font-bold">
        <ShieldCheck className="size-8 text-primary" />
        {t('consentement.titre')}
      </h1>
      <p className="text-lg leading-relaxed">{t('consentement.texte')}</p>
      <div className="grid gap-3">
        <Button size="lg" disabled={occupe} onClick={() => repondre(true)}>
          {t('consentement.accepter')}
        </Button>
        <Button size="lg" variant="outline" disabled={occupe} onClick={() => repondre(false)}>
          {t('consentement.refuser')}
        </Button>
      </div>
      <Alerte cle={erreur} />
    </div>
  )
}

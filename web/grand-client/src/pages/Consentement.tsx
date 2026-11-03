import { useState } from 'react'
import { api, ok } from '@/api/client'
import { useI18n } from '@/i18n'
import { Erreur } from '@/components/Erreur'
import { SelecteurLangue } from '@/components/SelecteurLangue'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'

// Version du texte d'information affiché (journalisée avec le consentement, loi 09-08).
export const VERSION_TEXTE = 'gc-2026-11-v2'

export function Consentement({ onAccepte }: { onAccepte: () => void }) {
  const { t, langue } = useI18n()
  const [coche, setCoche] = useState(false)
  const [erreur, setErreur] = useState<unknown>()

  async function accepter() {
    try {
      ok(await api.PUT('/v1/moi/consentement', { body: { versionTexte: VERSION_TEXTE, traitementDonnees: true, langue } }))
      onAccepte()
    } catch (e) {
      setErreur(e)
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center p-4">
      <Card className="w-full max-w-lg">
        <CardHeader className="flex flex-row items-center justify-between">
          <CardTitle>{t('consentement.titre')}</CardTitle>
          <SelecteurLangue />
        </CardHeader>
        <CardContent className="space-y-4">
          <p className="text-sm leading-relaxed">{t('consentement.texte')}</p>
          <label className="flex items-start gap-2 text-sm">
            <input type="checkbox" className="mt-1" checked={coche} onChange={(e) => setCoche(e.target.checked)} />
            {t('consentement.accord')}
          </label>
          <Erreur erreur={erreur} />
          <Button disabled={!coche} onClick={accepter}>
            {t('consentement.continuer')}
          </Button>
        </CardContent>
      </Card>
    </div>
  )
}

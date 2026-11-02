import { useState } from 'react'
import { api, ok } from '@/api/client'
import { useI18n } from '@/i18n'
import { VERSION_TEXTE } from '@/pages/Consentement'
import { Erreur } from '@/components/Erreur'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'

/** Droit d'accès et retrait du consentement (loi 09-08). */
export function MesDonnees({ onRetrait }: { onRetrait: () => void }) {
  const { t, langue } = useI18n()
  const [erreur, setErreur] = useState<unknown>()

  async function telecharger() {
    try {
      const donnees = ok(await api.GET('/v1/moi/donnees'))
      const a = document.createElement('a')
      a.href = URL.createObjectURL(new Blob([JSON.stringify(donnees, null, 2)], { type: 'application/json' }))
      a.download = 'daba-mes-donnees.json'
      a.click()
      URL.revokeObjectURL(a.href)
    } catch (e) {
      setErreur(e)
    }
  }

  async function retirer() {
    if (!confirm(t('donnees.confirmerRetrait'))) return
    try {
      ok(await api.PUT('/v1/moi/consentement', { body: { versionTexte: VERSION_TEXTE, traitementDonnees: false, langue } }))
      onRetrait()
    } catch (e) {
      setErreur(e)
    }
  }

  return (
    <Card className="max-w-2xl">
      <CardHeader>
        <CardTitle>{t('donnees.titre')}</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <p className="text-sm leading-relaxed">{t('donnees.texte')}</p>
        <Erreur erreur={erreur} />
        <div className="flex flex-wrap gap-2">
          <Button onClick={telecharger}>{t('donnees.telecharger')}</Button>
          <Button variant="destructive" onClick={retirer}>
            {t('donnees.retirer')}
          </Button>
        </div>
      </CardContent>
    </Card>
  )
}

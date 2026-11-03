import { Banknote, FileText, Scale, ShoppingCart, Undo2 } from 'lucide-react'
import { useState } from 'react'
import { api, type S } from '@/api/client'
import { carte, Etat, MontantDu, Vide } from '@/components/commun'
import { Button } from '@/components/ui/button'
import { useI18n } from '@/i18n'
import { cn } from '@/lib/utils'
import { useApi } from '@/outils'
import { Lien } from '@/router'
import { Contestation, ContestationEnCours, useContestationOuverte } from './Contestation'

/** Solde dû à chaque ambulant (karni vu du hanout). */
export function Comptes() {
  const { t, montant } = useI18n()
  const r = useApi(() => api.GET('/v1/karni/comptes', { params: { query: { taille: 200 } } }), [])
  if (!r.data) return <Etat {...r} />
  const total = r.data.contenu.reduce((s, c) => s + Math.max(0, c.solde), 0)

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold">{t('solde.titre')}</h1>
      <div className={cn(carte, 'text-center')}>
        <p>{t('solde.total')}</p>
        <MontantDu du={total > 0} className="text-3xl font-bold">
          {montant(total)}
        </MontantDu>
      </div>
      {r.data.contenu.length === 0 && <Vide />}
      <ul className="space-y-2">
        {r.data.contenu.map((c) => (
          <li key={c.id}>
            <Lien vers={`/comptes/${c.id}`} className={cn(carte, 'flex items-center gap-3')}>
              <span className="flex-1 font-semibold">{c.ambulant.nom}</span>
              <MontantDu du={c.solde > 0} className="text-lg font-bold">
                {montant(c.solde)}
              </MontantDu>
            </Lien>
          </li>
        ))}
      </ul>
    </div>
  )
}

const icones: Record<string, typeof Banknote> = { ACHAT_CREDIT: ShoppingCart, VERSEMENT: Banknote, AVOIR: Undo2 }

type Ecriture = S['Releve']['ecritures'][number]

/** Relevé d'un compte : achats à crédit, versements, avoirs (RG-C1). */
export function Releve({ id }: { id: string }) {
  const { t, montant, date } = useI18n()
  const r = useApi(() => api.GET('/v1/karni/comptes/{compteId}/releve', { params: { path: { compteId: id } } }), [id])
  const contestation = useContestationOuverte(id)
  const [contester, setContester] = useState(false)
  if (!r.data) return <Etat {...r} />
  const { compte, ecritures } = r.data
  const signe = (e: Ecriture) => (e.nature === 'ACHAT_CREDIT' ? '+' : '−')

  return (
    <div className="space-y-6">
      <header className={cn(carte, 'space-y-1 text-center')}>
        <h1 className="text-xl font-bold">{compte.ambulant.nom}</h1>
        <MontantDu du={compte.solde > 0} className="text-3xl font-bold">
          {montant(compte.solde)}
        </MontantDu>
      </header>

      {contestation.ouverte && <ContestationEnCours c={contestation.ouverte} />}
      {contester ? (
        <Contestation
          cible="COMPTE_CREDIT"
          cibleId={compte.id}
          onFini={() => {
            setContester(false)
            contestation.recharger()
          }}
        />
      ) : (
        <div className="grid grid-cols-2 gap-3">
          <Button asChild variant="outline">
            <Lien vers={`/documents?ambulantId=${compte.ambulant.id}`}>
              <FileText />
              {t('solde.documents')}
            </Lien>
          </Button>
          {!contestation.ouverte && !contestation.chargement && (
            <Button variant="outline" onClick={() => setContester(true)}>
              <Scale />
              {t('solde.contester')}
            </Button>
          )}
        </div>
      )}

      <section className="space-y-2">
        <h2 className="text-lg font-bold">{t('solde.historique')}</h2>
        {ecritures.length === 0 && <Vide />}
        <ul className="divide-y divide-border">
          {[...ecritures].reverse().map((e, i) => {
            const Icone = icones[e.nature]
            const ligne = (
              <>
                <Icone className="size-7 shrink-0" />
                <span className="flex-1">
                  <span className="block font-semibold">{t(`nature.${e.nature}`)}</span>
                  <span className="block text-sm text-muted-foreground">
                    {date(e.le)}
                    {e.numero && (
                      <>
                        {' · '}
                        <bdi>{e.numero}</bdi>
                      </>
                    )}
                  </span>
                </span>
                <span className="text-end">
                  <bdi className="block font-bold">
                    {signe(e)} {montant(Math.abs(e.montant))}
                  </bdi>
                  <span className="block text-sm text-muted-foreground">
                    {t('solde.reste', { m: montant(e.soldeApres) })}
                  </span>
                </span>
              </>
            )
            return (
              <li key={i}>
                {e.documentId ? (
                  <Lien vers={`/documents/${e.documentId}`} className="flex items-center gap-3 py-3">
                    {ligne}
                  </Lien>
                ) : (
                  <div className="flex items-center gap-3 py-3">{ligne}</div>
                )}
              </li>
            )
          })}
        </ul>
      </section>
    </div>
  )
}

import { CircleCheck, Download, FileText, Minus, PackageCheck, PackageX, Plus, Scale, Undo2 } from 'lucide-react'
import { useState } from 'react'
import { confirmerReception } from '@/api/a-venir'
import { api, type S } from '@/api/client'
import { Alerte, carte, cleErreur, Etat, Vide } from '@/components/commun'
import { Button } from '@/components/ui/button'
import { useI18n } from '@/i18n'
import { cn } from '@/lib/utils'
import { useApi } from '@/outils'
import { Lien } from '@/router'
import type { Cle } from '@/textes'
import { Contestation } from './Contestation'

type Doc = S['Document']

/** Bons de livraison, factures et avoirs reçus, par ambulant. */
export function Documents({ ambulantId }: { ambulantId?: string }) {
  const { t, montant, date } = useI18n()
  // ponytail: « voir plus » agrandit la page (200 max) ; pagination cumulée si un hanout dépasse 200 bons.
  const [taille, setTaille] = useState(50)
  const r = useApi(() => api.GET('/v1/documents', { params: { query: { ambulantId, taille } } }), [ambulantId, taille])
  if (!r.data) return <Etat {...r} />

  const parAmbulant = new Map<string, Doc[]>()
  for (const d of r.data.contenu) parAmbulant.set(d.ambulant.id, [...(parAmbulant.get(d.ambulant.id) ?? []), d])

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold">{t('docs.titre')}</h1>
      {ambulantId && (
        <Button asChild variant="outline">
          <Lien vers="/documents">{t('docs.tous')}</Lien>
        </Button>
      )}
      {r.data.contenu.length === 0 && <Vide />}
      {[...parAmbulant.values()].map((docs) => (
        <section key={docs[0].ambulant.id} className="space-y-2">
          <h2 className="text-lg font-bold">{docs[0].ambulant.nom}</h2>
          <ul className="space-y-2">
            {docs.map((d) => (
              <li key={d.id}>
                <Lien vers={`/documents/${d.id}`} className={cn(carte, 'flex items-center gap-3')}>
                  {d.type === 'AVOIR' ? <Undo2 className="size-8 shrink-0" /> : <FileText className="size-8 shrink-0" />}
                  <span className="min-w-0 flex-1">
                    <span className="block font-semibold">{t(`type.${d.type}`)}</span>
                    <span className="block text-sm text-muted-foreground">
                      <bdi>{d.numero}</bdi> · {date(d.emisLe)}
                    </span>
                    {d.statut !== 'EMIS' && (
                      <span className="block text-sm font-semibold text-destructive">{t(`doc.${d.statut}`)}</span>
                    )}
                  </span>
                  <span className="font-bold">{montant(d.total)}</span>
                </Lien>
              </li>
            ))}
          </ul>
        </section>
      ))}
      {r.data.total > r.data.contenu.length && taille < 200 && (
        <Button variant="outline" className="w-full" onClick={() => setTaille((x) => x + 50)}>
          {t('docs.plus')}
        </Button>
      )}
    </div>
  )
}

export function DocumentDetail({ id }: { id: string }) {
  const { t, langue, montant, date, libelle } = useI18n()
  const r = useApi(() => api.GET('/v1/documents/{documentId}', { params: { path: { documentId: id } } }), [id])
  const [mode, setMode] = useState<'ecart' | 'contester'>()
  const [envoye, setEnvoye] = useState(false)
  const [erreur, setErreur] = useState<Cle>()
  const [receptionId] = useState(() => crypto.randomUUID())

  if (!r.data) return <Etat {...r} />
  const d = r.data

  async function telecharger() {
    setErreur(undefined)
    try {
      const { data } = await api.GET('/v1/documents/{documentId}/pdf', {
        params: { path: { documentId: id }, query: { langue } },
        parseAs: 'blob',
      })
      if (!data) return setErreur('erreur.generique')
      const url = URL.createObjectURL(data)
      const a = document.createElement('a')
      a.href = url
      a.download = `${d.numero}.pdf`
      a.click()
      setTimeout(() => URL.revokeObjectURL(url), 60_000)
    } catch {
      setErreur(cleErreur())
    }
  }

  async function recuConforme() {
    setErreur(undefined)
    try {
      if (await confirmerReception(id, { id: receptionId, conforme: true })) setEnvoye(true)
      else setErreur('erreur.generique')
    } catch {
      setErreur(cleErreur())
    }
  }

  const fini = (ok: boolean) => {
    setMode(undefined)
    setEnvoye(ok)
  }

  return (
    <div className="space-y-6">
      <header className="space-y-1">
        <h1 className="text-2xl font-bold">{t(`type.${d.type}`)}</h1>
        <p className="text-muted-foreground">
          <bdi>{d.numero}</bdi> · {date(d.emisLe)}
        </p>
        <p className="font-semibold">{d.ambulant.nom}</p>
        {d.statut !== 'EMIS' && <p className="font-semibold text-destructive">{t(`doc.${d.statut}`)}</p>}
        {d.documentOrigineId && (
          <Lien vers={`/documents/${d.documentOrigineId}`} className="block font-semibold text-primary underline">
            {t('doc.origine')}
          </Lien>
        )}
      </header>

      <ul className="divide-y divide-border">
        {d.lignes.map((l, i) => (
          <li key={i} className="flex items-center gap-3 py-3">
            <span className="flex-1">
              <span className="block font-semibold">
                {l.nature === 'ARTICLE' ? libelle(l.libelle) : libelle(l.campagneNom) || t('doc.remise')}
              </span>
              {l.nature === 'ARTICLE' && l.prixUnitaire !== undefined && (
                <span className="block text-sm text-muted-foreground">
                  {l.quantite} × {montant(l.prixUnitaire)}
                </span>
              )}
            </span>
            <span className="font-semibold">{montant(l.montant ?? 0)}</span>
          </li>
        ))}
      </ul>

      <dl className={cn(carte, 'grid grid-cols-2 gap-2 text-lg')}>
        <dt>{t('doc.total')}</dt>
        <dd className="text-end font-bold">{montant(d.total)}</dd>
        <dt>{t('doc.regle')}</dt>
        <dd className="text-end">{montant(d.montantRegle)}</dd>
        <dt>{t('doc.credit')}</dt>
        <dd className="text-end">{montant(d.montantCredit)}</dd>
      </dl>

      {d.confirmationHanout && (
        <p className="flex items-center gap-2 text-succes">
          <CircleCheck />
          {t('doc.confirmeLe', { date: date(d.confirmationHanout.le) })}
        </p>
      )}

      {envoye && (
        <output className="flex items-center gap-2 font-semibold text-succes">
          <CircleCheck />
          {t('envoye')}
        </output>
      )}

      {mode === 'ecart' && <Ecart doc={d} receptionId={receptionId} onFini={fini} />}
      {mode === 'contester' && <Contestation cible="DOCUMENT" cibleId={d.id} onFini={fini} />}

      {!mode && (
        <div className="grid gap-3">
          {d.type !== 'AVOIR' && d.statut !== 'ANNULE' && !envoye && (
            <>
              {!d.confirmationHanout && (
                <Button size="lg" onClick={recuConforme}>
                  <PackageCheck />
                  {t('doc.recuConforme')}
                </Button>
              )}
              <Button size="lg" variant="outline" onClick={() => setMode('ecart')}>
                <PackageX />
                {t('doc.ecart')}
              </Button>
            </>
          )}
          <Button size="lg" variant="outline" onClick={telecharger}>
            <Download />
            {t('doc.pdf')}
          </Button>
          {!envoye && (
            <Button size="lg" variant="ghost" onClick={() => setMode('contester')}>
              <Scale />
              {t('doc.contester')}
            </Button>
          )}
        </div>
      )}
      <Alerte cle={erreur} />
    </div>
  )
}

/** Quantités réellement reçues, ligne par ligne. */
function Ecart({ doc, receptionId, onFini }: { doc: Doc; receptionId: string; onFini: (ok: boolean) => void }) {
  const { t, libelle } = useI18n()
  // Une ligne est repérée par son rang : un même produit peut venir du stock promo et du stock normal.
  const articles = doc.lignes.map((l, ligne) => ({ ...l, ligne })).filter((l) => l.nature === 'ARTICLE')
  const [recu, setRecu] = useState(() => articles.map((l) => l.quantite ?? 0))
  const [erreur, setErreur] = useState<Cle>()
  const [occupe, setOccupe] = useState(false)

  const changer = (i: number, delta: number) => setRecu((r) => r.map((q, j) => (j === i ? Math.max(0, q + delta) : q)))

  async function envoyer() {
    setOccupe(true)
    setErreur(undefined)
    const ecarts = articles
      .filter((l, i) => recu[i] !== l.quantite)
      .map((l) => ({ ligne: l.ligne, quantiteRecue: recu[articles.indexOf(l)] }))
    try {
      if (await confirmerReception(doc.id, { id: receptionId, conforme: ecarts.length === 0, ecarts })) onFini(true)
      else setErreur('erreur.generique')
    } catch {
      setErreur(cleErreur())
    } finally {
      setOccupe(false)
    }
  }

  return (
    <div className="space-y-4">
      <h2 className="text-xl font-bold">{t('doc.quantiteRecue')}</h2>
      <ul className="space-y-3">
        {articles.map((l, i) => (
          <li key={i} className="flex items-center gap-2">
            <span className="flex-1 font-semibold">{libelle(l.libelle)}</span>
            <Button
              size="icon"
              variant="outline"
              aria-label={t('doc.moins', { nom: libelle(l.libelle) })}
              onClick={() => changer(i, -1)}
            >
              <Minus />
            </Button>
            <output className="w-12 text-center text-xl font-bold">{recu[i]}</output>
            <Button
              size="icon"
              variant="outline"
              aria-label={t('doc.plus', { nom: libelle(l.libelle) })}
              onClick={() => changer(i, 1)}
            >
              <Plus />
            </Button>
          </li>
        ))}
      </ul>
      <Alerte cle={erreur} />
      <div className="grid grid-cols-2 gap-3">
        <Button variant="ghost" onClick={() => onFini(false)}>
          {t('annuler')}
        </Button>
        <Button disabled={occupe} onClick={envoyer}>
          {t('envoyer')}
        </Button>
      </div>
    </div>
  )
}

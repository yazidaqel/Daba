import { Camera, Send } from 'lucide-react'
import { useRef, useState } from 'react'
import { api, type S } from '@/api/client'
import { Alerte, carte, champ, cleErreur, Etat, PhotoProduit } from '@/components/commun'
import { Button } from '@/components/ui/button'
import { useI18n } from '@/i18n'
import { cn } from '@/lib/utils'
import { compresserPhoto, useApi } from '@/outils'
import { Lien, naviguer } from '@/router'
import type { Cle } from '@/textes'
import { BadgeStatut } from './Demande'

type Choix = { id: string; produit?: S['Produit']; photo?: Blob; apercu?: string }

const tuile =
  'flex h-full w-full flex-col items-center gap-2 rounded-2xl border-2 border-border p-2 text-center text-sm font-semibold active:bg-accent'

/** Signalement d'une rupture en deux gestes : le produit (ou une photo), puis la quantité. */
export function Rupture() {
  const { t, libelle, date } = useI18n()
  const produits = useApi(() => api.GET('/v1/produits', { params: { query: { habituels: true, taille: 60 } } }), [])
  const demandes = useApi(() => api.GET('/v1/demandes-depannage', { params: { query: { taille: 5 } } }), [])
  const [choix, setChoix] = useState<Choix>()
  const [erreurPhoto, setErreurPhoto] = useState(false)
  // Un id par produit tant que l'envoi n'a pas abouti : un POST perdu puis rejoué ne crée pas de doublon.
  const ids = useRef(new Map<string, string>())
  const idPour = (produitId: string) => {
    if (!ids.current.has(produitId)) ids.current.set(produitId, crypto.randomUUID())
    return ids.current.get(produitId)!
  }

  if (choix) return <Quantite choix={choix} onAnnuler={() => setChoix(undefined)} />

  return (
    <div className="space-y-8">
      <section className="space-y-4">
        <h1 className="text-2xl font-bold">{t('rupture.titre')}</h1>
        <ul className="grid grid-cols-3 gap-3">
          <li>
            <label className={cn(tuile, 'cursor-pointer border-primary bg-secondary text-secondary-foreground')}>
              <Camera className="size-16 p-2" />
              {t('rupture.photo')}
              <input
                type="file"
                accept="image/*"
                capture="environment"
                className="sr-only"
                onChange={async (e) => {
                  const fichier = e.target.files?.[0]
                  if (!fichier) return
                  setErreurPhoto(false)
                  try {
                    // Le JPEG recompressé perd aussi les métadonnées EXIF (position GPS).
                    const photo = await compresserPhoto(fichier)
                    setChoix({ id: crypto.randomUUID(), photo, apercu: URL.createObjectURL(photo) })
                  } catch {
                    setErreurPhoto(true)
                  }
                }}
              />
            </label>
          </li>
          {produits.data?.contenu.map((p) => (
            <li key={p.id}>
              <button type="button" className={tuile} onClick={() => setChoix({ id: idPour(p.id), produit: p })}>
                <PhotoProduit url={p.photoUrl} />
                <span className="line-clamp-2">{libelle(p.libelle)}</span>
              </button>
            </li>
          ))}
        </ul>
        {erreurPhoto && <Alerte cle="erreur.photo" />}
        {!produits.data && <Etat {...produits} />}
        {produits.data?.contenu.length === 0 && <p className="text-muted-foreground">{t('rupture.aucunHabituel')}</p>}
      </section>

      <section className="space-y-3">
        <h2 className="text-xl font-bold">{t('rupture.mesDemandes')}</h2>
        {demandes.data ? (
          <ul className="space-y-2">
            {demandes.data.contenu.map((d) => (
              <li key={d.id}>
                <Lien vers={`/demandes/${d.id}`} className={cn(carte, 'flex items-center gap-3')}>
                  <PhotoProduit url={d.produit?.photoUrl ?? d.photoUrl} className="size-12" />
                  <span className="min-w-0 flex-1">
                    <span className="block truncate font-semibold">
                      {d.produit ? libelle(d.produit.libelle) : t('rupture.produitPhoto')}
                    </span>
                    <span className="block text-sm text-muted-foreground">
                      {t('quantite', { n: d.quantite })} · {date(d.creeLe)}
                    </span>
                  </span>
                  <BadgeStatut statut={d.statut} />
                </Lien>
              </li>
            ))}
          </ul>
        ) : (
          <Etat {...demandes} />
        )}
      </section>
    </div>
  )
}

const QUANTITES = [1, 2, 3, 4, 5, 6, 10, 12, 24]

function Quantite({ choix, onAnnuler }: { choix: Choix; onAnnuler: () => void }) {
  const { t, libelle } = useI18n()
  const [autre, setAutre] = useState('')
  const [occupe, setOccupe] = useState(false)
  const [erreur, setErreur] = useState<Cle>()

  async function envoyer(quantite: number) {
    setOccupe(true)
    setErreur(undefined)
    // Même id à chaque nouvel essai : le serveur rend la demande déjà créée (idempotence).
    const rupture = { id: choix.id, produitId: choix.produit?.id, quantite }
    const photo = choix.photo
    try {
      const { data, response } = photo
        ? await api.POST('/v1/demandes-depannage', {
            body: rupture,
            bodySerializer: () => {
              const f = new FormData()
              f.append('rupture', new Blob([JSON.stringify(rupture)], { type: 'application/json' }))
              f.append('photo', photo, 'rupture.jpg')
              return f
            },
          })
        : await api.POST('/v1/demandes-depannage', { body: rupture })
      if (data) naviguer(`/demandes/${data.id}`)
      else setErreur(response.status === 413 ? 'erreur.photo' : 'erreur.generique')
    } catch {
      setErreur(cleErreur())
    } finally {
      setOccupe(false)
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-4">
        {choix.apercu ? (
          <img src={choix.apercu} alt="" className="size-20 rounded-lg object-cover" />
        ) : (
          <PhotoProduit url={choix.produit?.photoUrl} className="size-20" />
        )}
        <p className="text-xl font-bold">{choix.produit ? libelle(choix.produit.libelle) : t('rupture.produitPhoto')}</p>
      </div>
      <h1 className="text-2xl font-bold">{t('rupture.combien')}</h1>
      <div className="grid grid-cols-3 gap-3">
        {QUANTITES.map((q) => (
          <Button key={q} variant="outline" className="min-h-16 text-2xl" disabled={occupe} onClick={() => envoyer(q)}>
            {q}
          </Button>
        ))}
      </div>
      <form
        className="flex gap-3"
        onSubmit={(e) => {
          e.preventDefault()
          const q = Number(autre)
          if (Number.isInteger(q) && q > 0) void envoyer(q)
        }}
      >
        <input
          type="number"
          inputMode="numeric"
          min={1}
          aria-label={t('rupture.autreQuantite')}
          placeholder={t('rupture.autreQuantite')}
          value={autre}
          onChange={(e) => setAutre(e.target.value)}
          className={champ}
        />
        <Button type="submit" size="icon" className="size-14" aria-label={t('envoyer')} disabled={occupe || !autre}>
          <Send />
        </Button>
      </form>
      <Alerte cle={erreur} />
      <Button variant="ghost" className="w-full" onClick={onAnnuler}>
        {t('annuler')}
      </Button>
    </div>
  )
}

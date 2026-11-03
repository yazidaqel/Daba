import { useEffect, useRef, useState } from 'react'
import { LngLatBounds, Map as Carto, NavigationControl, setWorkerUrl, type GeoJSONSource } from 'maplibre-gl'
import { useI18n } from '@/i18n'
import { jeton, lisible, useSombre } from '@/lib/jetons'
import workerUrl from 'maplibre-gl/dist/maplibre-gl-worker.mjs?worker&url'

// Tuiles OpenFreeMap : libres, sans clé ni compte.
const STYLE = { clair: 'https://tiles.openfreemap.org/styles/liberty', sombre: 'https://tiles.openfreemap.org/styles/dark' }
// Worker fourni par Vite (?worker&url) : MapLibre calcule son URL à l'exécution, ce que Vite ne suit pas.
setWorkerUrl(new URL(workerUrl, location.href).href)

const CASABLANCA: [number, number] = [-7.5898, 33.5731]

/** `couleur` : nom d'un jeton de src/index.css (`primary`, `promo`…) ou valeur hexadécimale. */
export type Point = { id: string; lat: number; lon: number; rayon?: number; etiquette?: string; couleur?: string }

const teinte = (c: string) => (c.startsWith('#') ? c : jeton(c))

type Props = {
  points?: Point[]
  onPoint?: (id: string) => void
  /** Polygones fermés, anneaux en [lon, lat] (GeoJSON). */
  zones?: number[][][][]
  /** Sommets du polygone en cours de tracé. */
  trace?: number[][]
  onClic?: (lon: number, lat: number) => void
  className?: string
}

const vide = { type: 'FeatureCollection' as const, features: [] }

// Le schéma Position ne borne pas lat/lon : une position hors bornes ferait planter MapLibre.
const valide = (p: Point) => Math.abs(p.lat) <= 90 && Math.abs(p.lon) <= 180

export function Carte({ points: tous = [], onPoint, zones = [], trace = [], onClic, className }: Props) {
  const points = tous.filter(valide)
  const conteneur = useRef<HTMLDivElement>(null)
  const carte = useRef<Carto | null>(null)
  const rappels = useRef({ onPoint, onClic })
  // Carte chargée : l'instance elle-même, car un changement de langue la recrée.
  const [prete, setPrete] = useState<Carto | null>(null)
  const { t, langue } = useI18n()
  const sombre = useSombre()
  const cadrage = useRef('')

  useEffect(() => {
    rappels.current = { onPoint, onClic }
  })

  useEffect(() => {
    const m = new Carto({
      container: conteneur.current!,
      style: sombre ? STYLE.sombre : STYLE.clair,
      center: CASABLANCA,
      zoom: 10.5,
      locale: { 'NavigationControl.ZoomIn': t('carte.zoomPlus'), 'NavigationControl.ZoomOut': t('carte.zoomMoins') },
    })
    carte.current = m
    m.addControl(new NavigationControl({ showCompass: false }))
    m.on('load', () => {
      for (const id of ['points', 'zones', 'trace']) m.addSource(id, { type: 'geojson', data: vide })
      // Zones de ciblage d'une campagne : safran, couleur des promotions.
      const promo = jeton('promo')
      m.addLayer({ id: 'zones', type: 'fill', source: 'zones', paint: { 'fill-color': promo, 'fill-opacity': 0.25 } })
      m.addLayer({ id: 'zones-bord', type: 'line', source: 'zones', paint: { 'line-color': promo, 'line-width': 2 } })
      m.addLayer({ id: 'trace', type: 'line', source: 'trace', paint: { 'line-color': promo, 'line-width': 2, 'line-dasharray': [2, 1] } })
      m.addLayer({
        id: 'points',
        type: 'circle',
        source: 'points',
        paint: {
          'circle-radius': ['get', 'rayon'],
          'circle-color': ['get', 'couleur'],
          'circle-opacity': 0.7,
          'circle-stroke-color': jeton('card'),
          'circle-stroke-width': 1.5,
        },
      })
      m.addLayer({
        id: 'etiquettes',
        type: 'symbol',
        source: 'points',
        // Glyphes servis par OpenFreeMap (la pile par défaut de MapLibre y répond 404) ; les étiquettes ne portent que des chiffres.
        layout: { 'text-field': ['get', 'etiquette'], 'text-font': ['Noto Sans Regular'], 'text-size': 12, 'text-allow-overlap': true },
        paint: { 'text-color': ['get', 'texte'] },
      })
      m.on('click', 'points', (e) => {
        const id = e.features?.[0]?.properties?.id
        if (id) rappels.current.onPoint?.(String(id))
      })
      m.on('click', (e) => rappels.current.onClic?.(e.lngLat.lng, e.lngLat.lat))
      m.on('mouseenter', 'points', () => (m.getCanvas().style.cursor = 'pointer'))
      m.on('mouseleave', 'points', () => (m.getCanvas().style.cursor = ''))
      setPrete(m)
    })
    return () => {
      m.remove()
      setPrete(null)
      cadrage.current = ''
    }
    // Recréée au changement de langue (infobulles des contrôles) et de mode sombre (couleurs).
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [langue, sombre])

  useEffect(() => {
    const m = prete
    // Dans le rendu qui suit un changement de langue, `prete` désigne encore l'ancienne carte, déjà retirée.
    if (!m || m !== carte.current) return
    ;(m.getSource('points') as GeoJSONSource).setData({
      type: 'FeatureCollection',
      features: points.map((p) => {
        const couleur = teinte(p.couleur ?? 'muted-foreground')
        return {
          type: 'Feature',
          geometry: { type: 'Point', coordinates: [p.lon, p.lat] },
          properties: { id: p.id, rayon: p.rayon ?? 8, etiquette: p.etiquette ?? '', couleur, texte: lisible(couleur, [jeton('foreground'), jeton('card')]) },
        }
      }),
    })
    ;(m.getSource('zones') as GeoJSONSource).setData({
      type: 'FeatureCollection',
      features: zones.map((coordinates) => ({ type: 'Feature', geometry: { type: 'Polygon', coordinates }, properties: {} })),
    })
    ;(m.getSource('trace') as GeoJSONSource).setData({
      type: 'FeatureCollection',
      features: trace.length > 1 ? [{ type: 'Feature', geometry: { type: 'LineString', coordinates: trace }, properties: {} }] : [],
    })
    // Recadre sur les points quand leur ensemble change (pas sur un simple changement de couleur).
    const ids = points.map((p) => p.id).join()
    if (points.length && ids !== cadrage.current) {
      cadrage.current = ids
      const b = new LngLatBounds()
      for (const p of points) b.extend([p.lon, p.lat])
      m.fitBounds(b, { padding: 40, maxZoom: 13, duration: 0 })
    }
  }, [prete, points, zones, trace])

  return <div ref={conteneur} className={className ?? 'h-96 w-full rounded-lg border'} />
}

import { describe, expect, it } from 'vitest'
import { versCentimes, versDirhams, versE164 } from './format'
import { ciblerRegions, remiseParDefaut, validerCampagne } from './campagne'
import { MESSAGES } from '@/messages'

describe('format', () => {
  it('convertit dirhams et centimes', () => {
    expect(versCentimes('12,50')).toBe(1250)
    expect(versCentimes('0.1')).toBe(10)
    expect(versCentimes('')).toBeUndefined()
    expect(versCentimes('-3')).toBeUndefined()
    expect(versDirhams(1250)).toBe('12.50')
  })
  it('normalise un numéro marocain', () => {
    expect(versE164('06 12 34 56 78')).toBe('+212612345678')
    expect(versE164('00212712345678')).toBe('+212712345678')
    expect(versE164('0812345678')).toBeNull()
  })
  it('retire les villes hors des régions ciblées', () => {
    const nom = { fr: '' }
    const regions = [
      { code: 'CS', nom, villes: [{ code: 'CASABLANCA', nom }] },
      { code: 'RSK', nom, villes: [{ code: 'RABAT', nom }] },
    ]
    expect(ciblerRegions({ villes: ['CASABLANCA', 'RABAT'] }, regions, ['CS']).villes).toEqual(['CASABLANCA'])
    expect(ciblerRegions({ villes: ['RABAT'] }, regions, []).villes).toEqual(['RABAT'])
  })
})

describe('validerCampagne', () => {
  const ligne = { produitId: 'p', remise: remiseParDefaut('POURCENTAGE', 'p'), quantiteTotale: 100, quantiteMaxParAmbulant: 10 }
  const base = { nom: { fr: 'Été' }, debut: '2026-11-01T00:00:00Z', fin: '2026-11-30T00:00:00Z', lignes: [ligne] }

  it('accepte une campagne complète', () => expect(validerCampagne(base)).toEqual([]))
  it('refuse plafond > total, période inversée, remise vide', () => {
    expect(
      validerCampagne({
        ...base,
        fin: base.debut,
        lignes: [{ ...ligne, quantiteMaxParAmbulant: 200, remise: { type: 'PALIER', paliers: [] } }],
      }),
    ).toEqual(['valid.periode', 'valid.plafond', 'valid.remise'])
  })
})

describe('messages', () => {
  it('aucun texte vide dans aucune langue', () => {
    for (const dico of Object.values(MESSAGES)) for (const v of Object.values(dico)) expect(v.trim()).not.toBe('')
  })
})

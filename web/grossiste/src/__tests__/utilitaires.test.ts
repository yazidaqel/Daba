import { expect, test } from 'vitest'
import { formulaireReception } from '@/photo'
import { versE164 } from '@/telephone'

test('versE164 accepte les saisies courantes et refuse le reste', () => {
  expect(versE164('06 12 34 56 78')).toBe('+212612345678')
  expect(versE164('00212-612345678')).toBe('+212612345678')
  expect(versE164('+212 7 12 34 56 78')).toBe('+212712345678')
  expect(versE164('612345678')).toBe('+212612345678')
  expect(versE164('08 12 34 56 78')).toBeNull()
  expect(versE164('0612')).toBeNull()
})

test('réception : partie JSON typée application/json et photo jointe', async () => {
  const reception = { lignes: [{ produitId: 'p1', quantiteRecue: 4 }] }
  const fd = formulaireReception(reception, new Blob(['x'], { type: 'image/jpeg' }))
  const partie = fd.get('reception') as Blob
  expect(partie.type).toBe('application/json')
  expect(JSON.parse(await partie.text())).toEqual(reception)
  expect((fd.get('photoBonLivraison') as File).type).toBe('image/jpeg')
})

#!/usr/bin/env node
// Jeu de démo Daba (dev seulement), créé par la vraie API REST : c'est aussi un parcours de bout en
// bout des flux promo (§7.2) et dépannage (§7.1).
//
//   docker compose down -v && DABA_ADMIN_TELEPHONES=+212600000001 docker compose up -d --build
//   node scripts/demo.mjs          (base vide : le script ne se rejoue pas ; API_URL=http://localhost:8080 par défaut)
//
// Codes OTP lus dans `docker compose logs backend` (en dev, les SMS sont seulement journalisés par
// FournisseursJournal). Les numéros sont fictifs mais dans la plage mobile réelle : localhost seulement.
// Node >= 22, aucune dépendance.

import { execFileSync } from 'node:child_process'
import { randomUUID } from 'node:crypto'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const API = (process.env.API_URL ?? 'http://localhost:8080').replace(/\/$/, '')
const RACINE = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const pause = (ms) => new Promise((r) => setTimeout(r, ms))
const etape = (texte) => console.log(`ok  ${texte}`)
const echec = (texte) => {
  console.error(`ECHEC  ${texte}`)
  process.exit(1)
}
const maintenant = () => new Date().toISOString()
// Un back-end relié à un vrai opérateur SMS enverrait des codes à de vrais abonnés.
if (!['localhost', '127.0.0.1', '[::1]'].includes(new URL(API).hostname)) echec(`${API} : la démo ne vise que localhost`)

// ------------------------------------------------------------------ acteurs (numéros fictifs)

const tel = (n) => `+2126000000${String(n).padStart(2, '0')}`
const appareil = (n) => `00000000-0000-4000-8000-${String(n).padStart(12, '0')}`
const ADMIN = tel(1)
const GROSSISTE = tel(2)
const GRAND_CLIENT = tel(3)
const AMBULANT_1 = tel(4)
const AMBULANT_2 = tel(5)

// Base POS : Derb Omar, Maârif, Sidi Moumen (Casablanca).
const HANOUTS = [
  { n: 11, code: 'POS-CASA-0011', nom: 'Épicerie Al Baraka', gerant: 'Hassan Ait Ali', lat: 33.5942, lon: -7.6078, adresse: 'Rue de Strasbourg, Derb Omar' },
  { n: 12, code: 'POS-CASA-0012', nom: 'Hanout Si Brahim', gerant: 'Brahim Ouazzani', lat: 33.5921, lon: -7.6052, adresse: 'Rue Lahcen Arjoun, Derb Omar' },
  { n: 13, code: 'POS-CASA-0013', nom: 'Épicerie Nour', gerant: 'Fatima Zahra Bennani', lat: 33.5795, lon: -7.6325, adresse: 'Rue Abou Bakr El Kadiri, Maârif' },
  { n: 14, code: 'POS-CASA-0014', nom: 'Hanout Zerktouni', gerant: 'Mustapha El Idrissi', lat: 33.5768, lon: -7.6358, adresse: 'Bd Zerktouni, Maârif' },
  { n: 15, code: 'POS-CASA-0015', nom: 'Épicerie Al Amal', gerant: 'Khadija Tazi', lat: 33.5862, lon: -7.5231, adresse: 'Hay Attacharouk, Sidi Moumen' },
  { n: 16, code: 'POS-CASA-0016', nom: 'Hanout Essalam', gerant: 'Abdelkader Rami', lat: 33.5893, lon: -7.5276, adresse: 'Bd Driss El Harti, Sidi Moumen' },
]

// EAN-13 au préfixe GS1 Maroc (611), clé de contrôle calculée.
const ean = (n) => {
  const base = `6114000000${String(n).padStart(2, '0')}`
  const somme = [...base].reduce((s, c, i) => s + Number(c) * (i % 2 ? 3 : 1), 0)
  return base + ((10 - (somme % 10)) % 10)
}

// [clé, fr, ar, darija arabe, darija latin, marque, catégorie, TVA, prix de vente en centimes, produit du grand client]
const PRODUITS = [
  ['cola', 'Atlas Cola 1 L', 'أطلس كولا 1 لتر', 'كولا أطلس 1 لتر', 'Cola Atlas 1 litr', 'Atlas', 'BOISSONS', 20, 700, true],
  ['eau', 'Eau minérale Atlas 1,5 L', 'مياه معدنية أطلس 1.5 لتر', 'ما أطلس 1.5 لتر', 'Ma Atlas 1,5 litr', 'Atlas', 'BOISSONS', 20, 600, true],
  ['limonade', 'Limonade Atlas citron 33 cl', 'مشروب أطلس بالليمون 33 سل', 'ليموناضة أطلس 33 سل', 'Limonada Atlas 33 cl', 'Atlas', 'BOISSONS', 20, 500, true],
  ['the', 'Thé vert Nejma 200 g', 'شاي أخضر نجمة 200 غ', 'أتاي نجمة 200 غرام', 'Atay Nejma 200 g', 'Nejma', 'EPICERIE', 20, 1500, false],
  ['huile', 'Huile de table Zahra 1 L', 'زيت المائدة الزهرة 1 لتر', 'زيت الزهرة 1 لتر', 'Zit Zahra 1 litr', 'Zahra', 'EPICERIE', 20, 1900, false],
  ['sucre', 'Sucre en morceaux Sahra 1 kg', 'سكر مقطع الصحراء 1 كغ', 'سكر مقرط الصحراء 1 كيلو', 'Sokkar mqerret Sahra 1 kilo', 'Sahra', 'EPICERIE', 0, 1200, false],
  ['farine', 'Farine de blé Sanabil 5 kg', 'دقيق القمح سنابل 5 كغ', 'الفارينة سنابل 5 كيلو', 'Farina Sanabil 5 kilo', 'Sanabil', 'EPICERIE', 0, 3200, false],
  ['lait', 'Lait UHT Bladi 1 L', 'حليب بلادي 1 لتر', 'حليب بلادي 1 لتر', 'Hlib Bladi 1 litr', 'Bladi', 'PRODUITS_LAITIERS', 7, 750, false],
  ['lben', 'Lben Bladi 50 cl', 'لبن بلادي 50 سل', 'اللبن بلادي 50 سل', 'Lben Bladi 50 cl', 'Bladi', 'PRODUITS_LAITIERS', 7, 400, false],
  ['fromage', 'Fromage fondu Bladi 8 portions', 'جبن مطبوخ بلادي 8 قطع', 'فرماج بلادي 8 طرف', 'Fromaj Bladi 8 trif', 'Bladi', 'PRODUITS_LAITIERS', 20, 1300, false],
  ['savon', 'Savon Nqi 125 g', 'صابون نقي 125 غ', 'صابون نقي 125 غرام', 'Sabon Nqi 125 g', 'Nqi', 'HYGIENE', 20, 450, false],
  ['lessive', 'Lessive en poudre Nqi 1 kg', 'مسحوق الغسيل نقي 1 كغ', 'صابون الكيلو نقي 1 كيلو', 'Sabon l-kilo Nqi 1 kilo', 'Nqi', 'HYGIENE', 20, 1800, false],
]
const PRIX = Object.fromEntries(PRODUITS.map((p) => [p[0], p[8]]))

// PNG 1×1 : photo du bon de livraison du dépôt.
const PNG = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==', 'base64')

// ------------------------------------------------------------------ outils HTTP

async function appel(session, methode, chemin, corps) {
  const entetes = { 'Accept-Language': 'fr' }
  if (session) entetes.Authorization = `Bearer ${session.accessToken}`
  let body
  if (corps instanceof FormData) body = corps
  else if (corps !== undefined) {
    entetes['Content-Type'] = 'application/json'
    body = JSON.stringify(corps)
  }
  let rep
  try {
    rep = await fetch(API + chemin, { method: methode, headers: entetes, body })
  } catch (e) {
    echec(`${methode} ${chemin} : ${e.cause?.code ?? e.message} (le back-end tourne-t-il sur ${API} ?)`)
  }
  const texte = await rep.text()
  if (!rep.ok) echec(`${methode} ${chemin} -> ${rep.status}\n${texte}`)
  return texte ? JSON.parse(texte) : null
}

const csv = (lignes) => new Blob([lignes.map((l) => l.join(';')).join('\n')], { type: 'text/csv' })
const formulaire = (parties) => {
  const f = new FormData()
  for (const [nom, valeur, fichier] of parties) f.append(nom, valeur, fichier)
  return f
}
const json = (objet) => new Blob([JSON.stringify(objet)], { type: 'application/json' })

function codeOtp(telephone, depuis) {
  const journal = execFileSync('docker', ['compose', 'logs', 'backend', '--no-color', '--since', depuis], {
    cwd: RACINE,
    encoding: 'utf8',
    stdio: ['ignore', 'pipe', 'pipe'],
    maxBuffer: 64 * 1024 * 1024,
  })
  const motif = new RegExp(`\\[SMS\\] \\${telephone} : \\D*(\\d{6})`, 'g')
  return [...journal.matchAll(motif)].at(-1)?.[1]
}

async function connecter(telephone, n) {
  const depuis = new Date(Date.now() - 15_000).toISOString() // marge pour l'horloge de la VM Docker
  await appel(null, 'POST', '/v1/auth/otp', { telephone, langue: 'fr' })
  let code
  for (let i = 0; i < 20 && !code; i++) {
    await pause(500)
    code = codeOtp(telephone, depuis)
  }
  if (!code) echec(`code OTP de ${telephone} introuvable dans « docker compose logs backend »`)
  const session = await appel(null, 'POST', '/v1/auth/otp/verify', { telephone, code, appareilId: appareil(n) })
  session.appareilId = appareil(n)
  return session
}

async function consentir(session, geolocalisation) {
  await appel(session, 'PUT', '/v1/moi/consentement', {
    versionTexte: 'cndp-2026-10-fr',
    traitementDonnees: true,
    ...(geolocalisation ? { geolocalisation: true } : {}),
    langue: 'fr',
  })
}

/** Pousse des opérations hors ligne ; toute opération non appliquée ou en anomalie arrête le script. */
async function pousser(session, operations) {
  const lot = operations.map((o) => ({ id: randomUUID(), creeLe: maintenant(), ...o }))
  const { resultats } = await appel(session, 'POST', '/v1/sync/operations', { appareilId: session.appareilId, operations: lot })
  for (const r of resultats) {
    if (r.statut !== 'APPLIQUEE' || r.anomalies?.length) echec(`POST /v1/sync/operations : ${JSON.stringify(r)}`)
  }
}

async function tirer(session) {
  const changements = []
  let curseur
  for (;;) {
    const lot = await appel(session, 'GET', '/v1/sync/changements' + (curseur ? `?curseur=${encodeURIComponent(curseur)}` : ''))
    changements.push(...lot.changements)
    curseur = lot.curseur
    if (!lot.plusDeChangements) return changements
  }
}

const numero = (session, seq) => `${session.prefixeNumerotation}-BL-${String(seq).padStart(6, '0')}`

// ------------------------------------------------------------------ démarrage

for (let i = 0; ; i++) {
  const sante = await fetch(`${API}/actuator/health`).then((r) => r.json()).catch(() => null)
  if (sante?.status === 'UP') break
  if (i === 60) echec(`${API}/actuator/health ne répond pas UP après 60 s (docker compose up -d --build ?)`)
  await pause(1000)
}

const admin = await connecter(ADMIN, 1)
if (!admin.utilisateur.roles.includes('ADMIN')) {
  echec(`${ADMIN} n'est pas administrateur : docker compose down -v && DABA_ADMIN_TELEPHONES=${ADMIN} docker compose up -d --build`)
}
if (!admin.utilisateur.nouveau) {
  console.log('Base déjà initialisée : rien n\'est fait. Pour recharger la démo :')
  console.log(`  docker compose down -v && DABA_ADMIN_TELEPHONES=${ADMIN} docker compose up -d --build && node scripts/demo.mjs`)
  process.exit(0)
}
etape(`admin ${ADMIN} connecté`)

// ------------------------------------------------------------------ référentiel (admin)

const grossisteOrg = await appel(admin, 'POST', '/v1/admin/organisations', {
  type: 'GROSSISTE',
  nom: 'Dépôt Derb Omar Distribution',
  utilisateurs: [{ telephone: GROSSISTE, nom: 'Karim Berrada' }],
  position: { lat: 33.5951, lon: -7.6043 },
  adresse: '27 Rue de Strasbourg, Derb Omar',
  ville: 'Casablanca',
})
etape(`grossiste « ${grossisteOrg.nom} » créé`)

const grandClientOrg = await appel(admin, 'POST', '/v1/admin/organisations', {
  type: 'GRAND_CLIENT',
  nom: 'Atlas Boissons',
  utilisateurs: [{ telephone: GRAND_CLIENT, nom: 'Salma Alaoui' }],
})
etape(`grand client « ${grandClientOrg.nom} » créé`)

const bilanProduits = await appel(admin, 'POST', '/v1/admin/produits/import', formulaire([[
  'fichier',
  csv([
    ['ean', 'libelle_fr', 'libelle_ar', 'libelle_ary_arab', 'libelle_ary_latn', 'marque', 'categorie', 'conditionnement', 'unites', 'photo_url', 'grand_client_id', 'taux_tva'],
    ...PRODUITS.map((p, i) => [ean(i + 1), p[1], p[2], p[3], p[4], p[5], p[6], 'UNITE', 1, '', p[9] ? grandClientOrg.id : '', p[7]]),
  ]),
  'produits.csv',
]]))
if (bilanProduits.crees !== PRODUITS.length || bilanProduits.rejets.length) echec(`import produits : ${JSON.stringify(bilanProduits)}`)
etape(`${bilanProduits.crees} produits importés`)

const bilanHanouts = await appel(admin, 'POST', '/v1/admin/hanouts/import', formulaire([[
  'fichier',
  csv([
    ['code_pos', 'nom', 'gerant', 'telephone', 'lat', 'lon', 'adresse', 'ville', 'ice'],
    ...HANOUTS.map((h) => [h.code, h.nom, h.gerant, tel(h.n), h.lat, h.lon, h.adresse, 'Casablanca', '']),
  ]),
  'pos.csv',
]]))
if (bilanHanouts.crees !== HANOUTS.length || bilanHanouts.rejets.length) echec(`import hanouts : ${JSON.stringify(bilanHanouts)}`)
etape(`${bilanHanouts.crees} hanouts POS importés`)

const produits = (await appel(admin, 'GET', '/v1/produits?taille=50')).contenu
const P = Object.fromEntries(PRODUITS.map((p, i) => [p[0], produits.find((x) => x.ean === ean(i + 1)).id]))

// ------------------------------------------------------------------ connexions et consentements CNDP

const grossiste = await connecter(GROSSISTE, 2)
await consentir(grossiste)
const grandClient = await connecter(GRAND_CLIENT, 3)
await consentir(grandClient)
const ambulant1 = await connecter(AMBULANT_1, 4)
await consentir(ambulant1, true)
const ambulant2 = await connecter(AMBULANT_2, 5)
await consentir(ambulant2, true)
etape('grossiste, grand client et 2 ambulants connectés, consentement CNDP enregistré')
const hanouts = {}
for (const h of HANOUTS) {
  const s = await connecter(tel(h.n), h.n)
  if (!s.utilisateur.roles.includes('HANOUT')) echec(`${tel(h.n)} devrait être HANOUT : ${JSON.stringify(s.utilisateur)}`)
  await consentir(s)
  hanouts[h.n] = { ...h, session: s, id: s.utilisateur.hanoutId }
}
etape(`${HANOUTS.length} hanouts connectés (compte créé depuis la base POS), consentement enregistré`)

// ------------------------------------------------------------------ flux promo (§7.2)

const jour = 24 * 3600 * 1000
const campagne = await appel(grandClient, 'POST', '/v1/campagnes', {
  nom: { fr: 'Atlas Cola : -15 % chez votre grossiste', ar: 'أطلس كولا : تخفيض 15 %', 'ary-Arab': 'كولا أطلس : ناقص 15 %', 'ary-Latn': 'Cola Atlas : na9s 15 %' },
  description: { fr: 'Remise de lancement sur Atlas Cola 1 L, Casablanca.' },
  debut: new Date(Date.now() - 10 * 60 * 1000).toISOString(),
  fin: new Date(Date.now() + 30 * jour).toISOString(),
  lignes: [{ produitId: P.cola, remise: { type: 'POURCENTAGE', pourcentage: 15 }, quantiteTotale: 500, quantiteMaxParAmbulant: 60, prixReventeConseille: 700 }],
  ciblage: { regions: ['CASABLANCA_SETTAT'], villes: ['CASABLANCA'], categories: ['BOISSONS'] },
  delaiRetraitHeures: 24,
})
etape(`campagne « ${campagne.nom.fr} » créée (${campagne.statut})`)

const depot = await appel(grandClient, 'POST', `/v1/campagnes/${campagne.id}/depots`, {
  grossisteId: grossisteOrg.id,
  lignes: [{ produitId: P.cola, quantite: 200 }],
})
etape(`dépôt de 200 Atlas Cola proposé au grossiste (${depot.statut})`)

await appel(grossiste, 'POST', `/v1/depots/${depot.id}/reponse`, { decision: 'ACCEPTER' })
etape('grossiste : dépôt accepté')

const recu = await appel(grossiste, 'POST', `/v1/depots/${depot.id}/reception`, formulaire([
  ['reception', json({ lignes: [{ produitId: P.cola, quantiteRecue: 200 }], commentaire: 'Livré par le camion Atlas' })],
  ['photoBonLivraison', new Blob([PNG], { type: 'image/png' }), 'bon-livraison.png'],
]))
if (recu.statut !== 'RECU') echec(`dépôt non reçu : ${JSON.stringify(recu)}`)
const campagneActive = await appel(grandClient, 'GET', `/v1/campagnes/${campagne.id}`)
if (campagneActive.statut !== 'ACTIVE') echec(`campagne ${campagneActive.statut} au lieu de ACTIVE`)
etape('grossiste : réception de 200 unités confirmée, campagne ACTIVE')

const offres = (await appel(ambulant1, 'GET', '/v1/promos')).contenu
const offre = offres.find((o) => o.campagneId === campagne.id)
if (!offre) echec(`offre de la campagne absente de GET /v1/promos : ${JSON.stringify(offres)}`)
etape(`ambulant 1 voit l'offre (${offre.quantiteDisponible} disponibles, plafond ${offre.quantiteMaxParAmbulant})`)

const reservation = await appel(ambulant1, 'POST', '/v1/reservations', { id: randomUUID(), offreId: offre.id, quantite: 48 })
etape(`ambulant 1 : 48 unités réservées (${reservation.statut})`)

const qr = (await appel(ambulant1, 'GET', `/v1/reservations/${reservation.id}`)).qrJeton
if (!qr) echec('réservation sans qrJeton')
const apercu = await appel(grossiste, 'POST', '/v1/retraits/apercu', { qrJeton: qr })
etape(`grossiste : QR scanné, réservation de ${apercu.quantiteReservee} unités ${apercu.statut}`)
const retrait = await appel(grossiste, 'POST', '/v1/retraits', { qrJeton: qr, quantiteRemise: 48, appareilId: grossiste.appareilId })
etape(`grossiste : retrait validé (${retrait.statut}, ${retrait.quantiteRetiree} unités)`)

await pousser(ambulant1, [{
  type: 'CHARGEMENT',
  donnees: { lignes: [{ produitId: P.the, quantite: 20 }, { produitId: P.sucre, quantite: 20 }, { produitId: P.lait, quantite: 30 }] },
}])
etape('ambulant 1 : stock chargé à la main (thé, sucre, lait)')

const h11 = hanouts[11]
const lignesVente = [
  { nature: 'ARTICLE', produitId: P.cola, quantite: 24, prixUnitaire: PRIX.cola, tarif: 'STANDARD', campagneId: campagne.id, reservationId: reservation.id },
  { nature: 'ARTICLE', produitId: P.the, quantite: 6, prixUnitaire: PRIX.the, tarif: 'STANDARD' },
  { nature: 'ARTICLE', produitId: P.lait, quantite: 10, prixUnitaire: PRIX.lait, tarif: 'STANDARD' },
]
const totalVente = lignesVente.reduce((s, l) => s + l.quantite * l.prixUnitaire, 0)
const documentPromo = randomUUID()
await pousser(ambulant1, [{
  type: 'EMISSION_DOCUMENT',
  donnees: {
    documentId: documentPromo,
    type: 'BON_LIVRAISON',
    numero: numero(ambulant1, 1),
    sequence: 1,
    hanoutId: h11.id,
    lignes: lignesVente,
    reglements: [{ mode: 'ESPECES', montant: 20000 }],
  },
}])
etape(`ambulant 1 : bon de livraison ${numero(ambulant1, 1)} à ${h11.nom}, ${totalVente / 100} DH dont 200 DH en espèces`)

const docHanout = await appel(h11.session, 'GET', `/v1/documents/${documentPromo}`)
const compte = (await appel(h11.session, 'GET', '/v1/karni/comptes')).contenu[0]
if (!compte || compte.solde !== totalVente - 20000) echec(`solde du hanout attendu ${totalVente - 20000} : ${JSON.stringify(compte)}`)
etape(`${h11.nom} voit le document ${docHanout.numero} et un solde de ${compte.solde / 100} DH`)

const { total } = await appel(grandClient, 'GET', `/v1/campagnes/${campagne.id}/entonnoir`)
if (total.depose !== 200 || total.reserve !== 48 || total.retire !== 48 || total.revendu !== 24) {
  echec(`entonnoir inattendu : ${JSON.stringify(total)}`)
}
etape(`entonnoir : déposé ${total.depose} → réservé ${total.reserve} → retiré ${total.retire} → revendu ${total.revendu}`)

const campagne2 = await appel(grandClient, 'POST', '/v1/campagnes', {
  nom: { fr: 'Eau Atlas 1,5 L à 5 DH', ar: 'مياه أطلس 1.5 لتر بـ 5 دراهم', 'ary-Arab': 'ما أطلس 1.5 لتر ب 5 دراهم', 'ary-Latn': 'Ma Atlas 1,5 litr b 5 drahem' },
  debut: new Date(Date.now() - 10 * 60 * 1000).toISOString(),
  fin: new Date(Date.now() + 21 * jour).toISOString(),
  lignes: [{ produitId: P.eau, remise: { type: 'PRIX_NET', prixNet: 500 }, quantiteTotale: 1000, quantiteMaxParAmbulant: 120, prixReventeConseille: 600 }],
  ciblage: { villes: ['CASABLANCA'] },
})
await appel(grandClient, 'POST', `/v1/campagnes/${campagne2.id}/depots`, { grossisteId: grossisteOrg.id, lignes: [{ produitId: P.eau, quantite: 300 }] })
etape(`campagne « ${campagne2.nom.fr} » proposée au grossiste, en attente de sa réponse`)

// ------------------------------------------------------------------ flux dépannage (§7.1)

const bon = await appel(grossiste, 'POST', '/v1/bons-sortie', {
  id: randomUUID(),
  ambulantTelephone: AMBULANT_2,
  lignes: [
    { produitId: P.eau, quantite: 24 },
    { produitId: P.huile, quantite: 12 },
    { produitId: P.sucre, quantite: 20 },
    { produitId: P.farine, quantite: 10 },
    { produitId: P.savon, quantite: 30 },
  ],
})
etape(`grossiste : bon de sortie ${bon.numero} émis pour l'ambulant 2`)

const bonRecu = (await tirer(ambulant2)).find((c) => c.entite === 'BON_SORTIE' && c.id === bon.id)
if (!bonRecu) echec('bon de sortie absent de GET /v1/sync/changements')
await pousser(ambulant2, [{ type: 'CHARGEMENT', donnees: { qrJeton: bonRecu.donnees.qrJeton } }])
etape('ambulant 2 : bon reçu par synchronisation et chargé par scan du QR')

const h13 = hanouts[13]
await appel(ambulant2, 'PUT', '/v1/ambulant/disponibilite', { disponible: true, position: { lat: 33.5812, lon: -7.6301 } })
etape(`ambulant 2 disponible à Maârif, à ~300 m de ${h13.nom}`)

const rupture = await appel(h13.session, 'POST', '/v1/demandes-depannage', { id: randomUUID(), produitId: P.huile, quantite: 6 })
etape(`${h13.nom} signale une rupture : 6 huile Zahra (${rupture.statut})`)

const proposees = (await appel(ambulant2, 'GET', '/v1/demandes-depannage?statut=EN_ATTENTE')).contenu
if (!proposees.some((d) => d.id === rupture.id)) echec(`demande non proposée à l'ambulant 2 : ${JSON.stringify(proposees)}`)
await appel(ambulant2, 'POST', `/v1/demandes-depannage/${rupture.id}/acceptation`)
await appel(ambulant2, 'POST', `/v1/demandes-depannage/${rupture.id}/en-route`)
etape('ambulant 2 : demande acceptée, en route')

const documentDepannage = randomUUID()
await pousser(ambulant2, [{
  type: 'EMISSION_DOCUMENT',
  donnees: {
    documentId: documentDepannage,
    type: 'BON_LIVRAISON',
    numero: numero(ambulant2, 1),
    sequence: 1,
    hanoutId: h13.id,
    lignes: [{ nature: 'ARTICLE', produitId: P.huile, quantite: 6, prixUnitaire: PRIX.huile, tarif: 'STANDARD' }],
    reglements: [{ mode: 'ESPECES', montant: 6 * PRIX.huile }],
    demandeDepannageId: rupture.id,
    confirmationHanout: { mode: 'CONFIRMATION_ECRAN', le: maintenant() },
  },
}])
const livree = await appel(h13.session, 'GET', `/v1/demandes-depannage/${rupture.id}`)
if (livree.statut !== 'LIVREE' || livree.documentId !== documentDepannage) echec(`demande non livrée : ${JSON.stringify(livree)}`)
etape(`ambulant 2 : livré, bon ${numero(ambulant2, 1)} payé comptant, demande LIVREE`)

// Demandes laissées ouvertes : eau Atlas chez 3 hanouts d'une même cellule geohash 5, le seuil
// d'anonymat de la carte des ruptures du grand client (§5.4).
for (const n of [14, 11, 12]) {
  const ouverte = await appel(hanouts[n].session, 'POST', '/v1/demandes-depannage', { id: randomUUID(), produitId: P.eau, quantite: 12 })
  const ouvertes = (await appel(ambulant2, 'GET', '/v1/demandes-depannage?statut=EN_ATTENTE')).contenu
  if (!ouvertes.some((d) => d.id === ouverte.id)) echec(`demande de ${hanouts[n].nom} non proposée à l'ambulant 2`)
  etape(`${hanouts[n].nom} signale une rupture : 12 eau Atlas, en attente chez l'ambulant 2`)
}
const carte = await appel(grandClient, 'GET', '/v1/analytique/ruptures?precision=5')
if (!carte.cellules.some((c) => c.produitId === P.eau && c.enCours === 3)) echec(`carte des ruptures : ${JSON.stringify(carte)}`)
etape('carte des ruptures du grand client : 1 cellule (precision 5), 3 ruptures en cours sur l\'eau Atlas')

// ------------------------------------------------------------------ récapitulatif

console.log(`
Démo chargée sur ${API}. Connexion par OTP : le code s'affiche dans « docker compose logs backend »
(ligne [SMS] <téléphone> : …). Portails contre le vrai back-end : API_URL=${API} npm run dev
(web/grand-client : DABA_API=${API}). Les ruptures ouvertes suivent RG-D3 : rayon élargi après 10 min,
SANS_REPONSE après 20 min.
`)
console.table([
  { role: 'Admin', telephone: ADMIN, voir: 'Import référentiel, création des organisations' },
  { role: 'Grossiste', telephone: GROSSISTE, voir: 'Dépôt Atlas Cola reçu (48 retirés), dépôt Eau Atlas à accepter, bon de sortie, ambulant 2 rattaché' },
  { role: 'Grand client', telephone: GRAND_CLIENT, voir: 'Campagne Atlas Cola ACTIVE + entonnoir 200/48/48/24, campagne Eau Atlas PROPOSEE, carte des ruptures (précision 5)' },
  { role: 'Ambulant 1', telephone: AMBULANT_1, voir: 'Promo réservée et retirée, stock promo, BL à Épicerie Al Baraka, karni (133 DH dus)' },
  { role: 'Ambulant 2', telephone: AMBULANT_2, voir: 'Stock chargé par bon de sortie, dépannage livré, 3 demandes ouvertes (eau Atlas)' },
  ...HANOUTS.map((h) => ({
    role: `Hanout ${h.n}`,
    telephone: tel(h.n),
    voir: {
      11: 'BL promo, solde 133 DH, réception à confirmer, rupture eau en attente',
      12: 'Rupture eau en attente',
      13: 'Dépannage livré, BL payé comptant',
      14: 'Rupture eau en attente',
    }[h.n] ?? `${h.nom}, sans activité`,
  })),
])

import 'dart:convert';
import 'dart:io';

import 'package:daba_ambulant/domaine.dart';
import 'package:daba_shared/daba_shared.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:yaml/yaml.dart';

const appareil = '11111111-1111-4111-8111-111111111111';
const moi = '22222222-2222-4222-8222-222222222222';
const hanoutId = '33333333-3333-4333-8333-333333333333';
const coca = '44444444-4444-4444-8444-444444444444';
const campagne = '55555555-5555-4555-8555-555555555555';
const resa = '66666666-6666-4666-8666-666666666666';

Ambulant monter({Map<String, dynamic>? parametres}) {
  final s = Stockage.memoire();
  s.enregistrer('HANOUT', hanoutId, {'id': hanoutId, 'nom': 'Hanout Derb Omar', 'position': {'lat': 33.59, 'lon': -7.61}});
  s.enregistrer('PRODUIT', coca, {
    'id': coca, 'ean': '6111000000017', 'libelle': {'fr': 'Coca 1L', 'ar': 'كوكا 1ل'}, 'marque': 'Coca-Cola',
    'categorie': {'code': 'BOISSONS', 'libelle': {'fr': 'Boissons'}}, 'conditionnement': 'UNITE', 'unitesParConditionnement': 1,
    'tauxTva': 20,
  });
  s.enregistrer('STOCK', coca, {
    'produitId': coca, 'quantite': 100,
    'promo': [{'campagneId': campagne, 'reservationId': resa, 'quantite': 5}],
  });
  s.enregistrer('TARIF', 'standard', {'produitId': coca, 'prixUnitaire': 800});
  if (parametres != null) s.enregistrer('PARAMETRES', 'p', parametres);
  return Ambulant(s, appareilId: appareil, prefixe: 'K7Q2', utilisateurId: moi, nom: 'Said');
}

LigneVente coca1(int q) => LigneVente(produitId: coca, quantite: q, prixUnitaire: 800);

void main() {
  group('vente hors ligne', () {
    test('bon de livraison par défaut, numéroté avec le préfixe de l\'appareil', () {
      final m = monter();
      final d1 = m.emettre(hanoutId: hanoutId, lignes: [coca1(2)], reglements: [const Reglement('ESPECES', 1600)]);
      final d2 = m.emettre(hanoutId: hanoutId, lignes: [coca1(1)]);
      expect(d1['type'], 'BON_LIVRAISON');
      expect([d1['numero'], d2['numero']], ['K7Q2-BL-000001', 'K7Q2-BL-000002']);
      expect(m.s.nombreEnAttente, 2);
      final op = m.s.enAttente().first;
      expect(op['type'], 'EMISSION_DOCUMENT');
      expect(op['donnees']['sequence'], 1);
    });

    test('facture seulement avec les mentions légales, recopiées dans l\'opération (RG-F2)', () {
      expect(() => monter().emettre(hanoutId: hanoutId, lignes: [coca1(1)], type: 'FACTURE'),
          throwsA(isA<VenteRefusee>().having((e) => e.motif, 'motif', RefusVente.factureSansMentions)));
      final mentions = {'ice': '001234567000089', 'identifiantFiscal': '123', 'registreCommerce': '456', 'assujettiTva': false};
      final m = monter(parametres: {'politiqueDepassementCredit': 'AVERTIR', 'seuilEcartInventairePourcent': 5, 'mentionsLegales': mentions});
      final f = m.emettre(hanoutId: hanoutId, lignes: [coca1(1)], type: 'FACTURE');
      expect(f['numero'], 'K7Q2-FA-000001', reason: 'série FA distincte de la série BL');
      expect(m.s.enAttente().single['donnees']['mentionsLegales'], mentions);
    });

    test('le stock promo part en premier et reste tracé jusqu\'à la ligne (RG-S3)', () {
      final m = monter();
      final d = m.emettre(hanoutId: hanoutId, lignes: [coca1(8)], reglements: [const Reglement('ESPECES', 6400)]);
      final lignes = (d['lignes'] as List).cast<Map>();
      expect(lignes.map((l) => (l['quantite'], l['reservationId'])), [(5, resa), (3, null)]);
      expect(lignes.first['campagneId'], campagne);
      expect(lignes.first['campagneNom'], isNotNull, reason: 'référence de la campagne sur la ligne (RG-F3)');
      expect(m.s.enAttente().single['donnees']['lignes'].first, isNot(contains('campagneNom')), reason: 'champ hors LigneDocumentSaisie');
      expect(m.stock(coca), 97);
      expect(m.stockPromo(coca).single.quantite, 0);
    });

    test('premier prix retenu comme tarif standard ; prix connu changé retenu seulement sur demande (§3.4)', () {
      final m = monter();
      const the = '77777777-7777-4777-8777-777777777777';
      m.emettre(hanoutId: hanoutId, lignes: [LigneVente(produitId: the, quantite: 1, prixUnitaire: 1500), coca1(1)]);
      expect(m.prix(the, hanoutId), (prix: 1500, tarif: 'STANDARD'));
      expect([for (final o in m.s.enAttente()) o['type']], ['TARIF', 'EMISSION_DOCUMENT'], reason: 'coca au tarif connu');

      m.emettre(hanoutId: hanoutId, lignes: [LigneVente(produitId: the, quantite: 1, prixUnitaire: 1200, tarif: 'NEGOCIE')]);
      expect(m.prix(the, hanoutId), (prix: 1500, tarif: 'STANDARD'), reason: 'rabais du jour : rien de retenu');
      expect(m.s.enAttente().where((o) => o['type'] == 'TARIF').length, 1);

      m.emettre(hanoutId: hanoutId, lignes: [LigneVente(produitId: the, quantite: 1, prixUnitaire: 1400, tarif: 'NEGOCIE', retenir: true)]);
      expect(m.prix(the, hanoutId), (prix: 1400, tarif: 'NEGOCIE'));
      expect(m.prix(the, 'autre-hanout'), (prix: 1500, tarif: 'STANDARD'));
      m.emettre(hanoutId: hanoutId, lignes: [LigneVente(produitId: the, quantite: 1, prixUnitaire: 1400, tarif: 'NEGOCIE', retenir: true)]);
      expect(m.s.enAttente().where((o) => o['type'] == 'TARIF').length, 2, reason: 'prix inchangé : pas de nouveau tarif');

      Synchronisation(m.s, Api('http://serveur'), appareilId: appareil, prefixe: 'K7Q2').appliquer({
        'entite': 'TARIF', 'id': '88888888-8888-4888-8888-888888888888', 'action': 'UPSERT',
        'donnees': {'produitId': the, 'prixUnitaire': 1550},
      });
      expect(m.s.objets('TARIF', where: "json_extract(donnees, '\$.produitId') = ?", args: [the]).length, 2);
      expect(m.prix(the, 'autre-hanout').prix, 1550);
    });

    test("tarif ou plafond en conflit : l'appareil reprend la version gardée par le serveur", () async {
      final m = monter();
      m.emettre(hanoutId: hanoutId, lignes: [LigneVente(produitId: coca, quantite: 1, prixUnitaire: 900, tarif: 'NEGOCIE', retenir: true)]);
      expect(m.prix(coca, hanoutId).prix, 900);
      m.definirCredit(hanoutId, plafond: 100000);
      const compteServeur = {'id': '99999999-0000-4000-8000-000000000000', 'hanout': {'id': hanoutId, 'nom': 'Hanout Derb Omar'},
        'solde': 0, 'plafond': 50000};
      final client = MockClient((req) async {
        if (req.url.path == '/v1/sync/operations') {
          final ops = (jsonDecode(req.body)['operations'] as List).cast<Map>();
          return http.Response(jsonEncode({'resultats': [
            for (final o in ops)
              o['type'] == 'TARIF'
                  ? {'operationId': o['id'], 'statut': 'EN_CONFLIT', 'conflit': {
                      'code': 'MODIFIE_AILLEURS', 'resolution': 'VERSION_SERVEUR_CONSERVEE',
                      'versionServeur': {'produitId': coca, 'hanoutId': hanoutId, 'prixUnitaire': 850}}}
                  : o['type'] == 'CREDIT_HANOUT'
                      ? {'operationId': o['id'], 'statut': 'EN_CONFLIT', 'conflit': {
                          'code': 'MODIFIE_AILLEURS', 'resolution': 'VERSION_SERVEUR_CONSERVEE', 'versionServeur': compteServeur}}
                      : {'operationId': o['id'], 'statut': 'APPLIQUEE'}
          ]}), 200);
        }
        return http.Response(jsonEncode({'curseur': 'c', 'plusDeChangements': false, 'changements': []}), 200);
      });
      await Synchronisation(m.s, Api('http://serveur', client: client), appareilId: appareil, prefixe: 'K7Q2').synchroniser();
      expect(m.prix(coca, hanoutId), (prix: 850, tarif: 'NEGOCIE'));
      expect(m.s.objets('TARIF').length, 2, reason: 'standard + négocié, sans doublon');
      expect(m.compte(hanoutId)?['plafond'], 50000);
      expect(m.s.objets('COMPTE_CREDIT').length, 1);
    });

    test('une vente qui rend le stock négatif est enregistrée (RG-S1)', () {
      final m = monter();
      m.emettre(hanoutId: hanoutId, lignes: [coca1(200)]);
      expect(m.stockTotal(coca), -95);
    });

    test('remise ponctuelle en ligne séparée, plafonnée', () {
      final m = monter(parametres: {'politiqueDepassementCredit': 'AVERTIR', 'seuilEcartInventairePourcent': 5, 'plafondRemisePonctuellePourcent': 10});
      final d = m.emettre(hanoutId: hanoutId, lignes: [coca1(10)..remise = 500], reglements: [const Reglement('ESPECES', 7500)]);
      expect((d['lignes'] as List).last, containsPair('montant', -500));
      expect(d['total'], 7500);
      expect(() => m.emettre(hanoutId: hanoutId, lignes: [coca1(10)..remise = 900]),
          throwsA(isA<VenteRefusee>().having((e) => e.motif, 'motif', RefusVente.remiseHorsPlafond)));
    });
  });

  group('TVA (prix TTC, RG-F2)', () {
    final assujetti = {
      'politiqueDepassementCredit': 'AVERTIR', 'seuilEcartInventairePourcent': 5,
      'mentionsLegales': {'ice': '001234567000089', 'identifiantFiscal': '1', 'registreCommerce': '2', 'assujettiTva': true},
    };

    test('formule du contrat : M × t / (100 + t), demi au-dessus en valeur absolue', () {
      expect(Ambulant.tvaComprise(1200, 20), 200);
      expect(Ambulant.tvaComprise(850, 7), 56);
      expect(Ambulant.tvaComprise(3, 20), 1, reason: '0,5 arrondi au-dessus');
      expect(Ambulant.tvaComprise(-3, 20), -1, reason: 'demi au-dessus en valeur absolue');
      expect(Ambulant.tvaComprise(1000, 0), 0);
    });

    test("facture d'un assujetti : TVA = somme des lignes, remise comprise", () {
      final m = monter(parametres: assujetti);
      final f = m.emettre(hanoutId: hanoutId, lignes: [coca1(10)..remise = 500], type: 'FACTURE');
      // 5 promo + 5 normaux = deux lignes de 4000 (667 chacune, arrondi par ligne), remise -500 (-83).
      expect(f['tva'], 667 + 667 - 83);
      expect(m.s.enAttente().single['donnees']['lignes'].every((l) => l['tauxTva'] == 20), isTrue);
    });

    test('taux inconnu : pas de facture pour un assujetti, bon de livraison accepté', () {
      final m = monter(parametres: assujetti);
      m.s.enregistrer('PRODUIT', coca, {...m.produit(coca)!}..remove('tauxTva'));
      expect(m.tvaInconnue([coca1(1)]), isTrue);
      expect(() => m.emettre(hanoutId: hanoutId, lignes: [coca1(1)], type: 'FACTURE'),
          throwsA(isA<VenteRefusee>().having((e) => e.motif, 'motif', RefusVente.factureTvaInconnue)));
      final bl = m.emettre(hanoutId: hanoutId, lignes: [coca1(1)]);
      expect(bl.containsKey('tva'), isFalse);
      expect(m.s.enAttente().single['donnees']['lignes'].single.containsKey('tauxTva'), isFalse);
    });
  });

  test('contestation close hors ligne', () {
    final m = monter();
    m.s.enregistrer('CONTESTATION', 'c1', {
      'id': 'c1', 'cible': 'DOCUMENT', 'cibleId': 'd1', 'motif': 'QUANTITE', 'statut': 'OUVERTE',
      'creeLe': '2026-11-02T08:00:00Z', 'ambulant': {'id': moi, 'nom': ''}, 'hanout': {'id': hanoutId, 'nom': ''},
    });
    expect(m.contestations(hanoutId: hanoutId), hasLength(1));
    m.resoudreContestation('c1', commentaire: 'Avoir émis');
    expect(m.contestations(), isEmpty);
    expect(m.s.enAttente().single, containsPair('type', 'RESOLUTION_CONTESTATION'));
  });

  group('karni', () {
    test('solde = crédits − versements (RG-C1), à jour sans réseau', () {
      final m = monter();
      m.emettre(hanoutId: hanoutId, lignes: [coca1(10)], reglements: [const Reglement('ESPECES', 3000)]);
      expect(m.solde(hanoutId), 5000);
      m.versement(hanoutId, 2000);
      expect(m.solde(hanoutId), 3000);
      expect(m.historique(hanoutId), hasLength(2));
    });

    test('plafond dépassé : avertissement puis vente acceptée avec le drapeau (RG-C3)', () {
      final m = monter();
      m.definirCredit(hanoutId, plafond: 5000);
      expect(() => m.emettre(hanoutId: hanoutId, lignes: [coca1(10)]),
          throwsA(isA<VenteRefusee>().having((e) => e.motif, 'motif', RefusVente.creditAvertissement)));
      m.emettre(hanoutId: hanoutId, lignes: [coca1(10)], avertissementAccepte: true);
      expect(m.s.enAttente().last['donnees']['avertissementCreditAccepte'], isTrue);
      expect(m.solde(hanoutId), 8000);
    });

    test('plafond dépassé et politique BLOQUER : vente à crédit refusée, comptant accepté', () {
      final m = monter(parametres: {'politiqueDepassementCredit': 'BLOQUER', 'seuilEcartInventairePourcent': 5});
      m.definirCredit(hanoutId, plafond: 1000);
      expect(() => m.emettre(hanoutId: hanoutId, lignes: [coca1(10)], avertissementAccepte: true),
          throwsA(isA<VenteRefusee>().having((e) => e.motif, 'motif', RefusVente.creditBloque)));
      m.emettre(hanoutId: hanoutId, lignes: [coca1(10)], reglements: [const Reglement('ESPECES', 8000)]);
    });

    test('délai de crédit dépassé', () {
      final m = monter();
      m.s.enregistrer('COMPTE_CREDIT', 'c', {
        'id': 'c', 'hanout': {'id': hanoutId, 'nom': ''}, 'ambulant': {'id': moi, 'nom': ''}, 'solde': 100,
        'delaiMaxJours': 15, 'plusAncienImpayeLe': DateTime.now().subtract(const Duration(days: 20)).toUtc().toIso8601String(),
      });
      expect(m.controleCredit(hanoutId, 100), Depassement.delai);
      expect(m.controleCredit(hanoutId, 0), Depassement.aucun);
    });
  });

  group('chargement', () {
    String qr(Map<String, dynamic> charge) =>
        'eyJhbGciOiJFUzI1NiJ9.${base64Url.encode(utf8.encode(jsonEncode(charge))).replaceAll('=', '')}.signature';
    final bon = {
      'bonSortieId': '77777777-7777-4777-8777-777777777777', 'grossisteId': '88888888-8888-4888-8888-888888888888',
      'ambulantId': moi, 'lignes': [{'produitId': coca, 'quantite': 24}], 'emisLe': '2026-11-02T08:00:00Z',
    };

    test('QR du bon de sortie lu hors ligne, chargé une seule fois', () {
      final m = monter();
      m.chargerBonSortie(qr(bon));
      expect(m.stock(coca), 124);
      expect(m.s.enAttente().single['donnees'], {'qrJeton': qr(bon)});
      expect(() => m.chargerBonSortie(qr(bon)), throwsA(isA<VenteRefusee>().having((e) => e.motif, 'motif', RefusVente.bonDejaCharge)));
    });

    test('bon adressé à un autre ambulant refusé', () {
      expect(() => monter().chargerBonSortie(qr({...bon, 'ambulantId': 'autre'})),
          throwsA(isA<VenteRefusee>().having((e) => e.motif, 'motif', RefusVente.qrAutreAmbulant)));
    });
  });

  test('une journée entière sans réseau, puis tout remonte dans l\'ordre et sans trou', () async {
    final m = monter();
    var reseau = false;
    final recus = <Map>[];
    final client = MockClient((req) async {
      if (!reseau) throw const SocketException('pas de réseau');
      if (req.url.path == '/v1/sync/operations') {
        final ops = (jsonDecode(req.body)['operations'] as List).cast<Map>();
        recus.addAll(ops);
        return http.Response(
            jsonEncode({'resultats': [for (final o in ops) {'operationId': o['id'], 'statut': 'APPLIQUEE'}]}), 200);
      }
      return http.Response(jsonEncode({'curseur': 'c', 'plusDeChangements': false, 'changements': []}), 200);
    });
    final sync = Synchronisation(m.s, Api('http://serveur', client: client), appareilId: appareil, prefixe: 'K7Q2');

    for (var i = 0; i < 60; i++) {
      m.emettre(hanoutId: hanoutId, lignes: [coca1(1)], reglements: [if (i.isEven) const Reglement('ESPECES', 800)]);
      if (i % 10 == 0) m.versement(hanoutId, 400);
      if (i % 15 == 0) await expectLater(sync.synchroniser(), throwsA(isA<HorsLigne>()));
    }
    expect(m.s.nombreEnAttente, 66);
    expect(m.stockTotal(coca), 45);

    reseau = true;
    await sync.synchroniser();
    expect(m.s.nombreEnAttente, 0);
    final sequences = [for (final o in recus) if (o['type'] == 'EMISSION_DOCUMENT') o['donnees']['sequence']];
    expect(sequences, [for (var i = 1; i <= 60; i++) i]);
  });

  test('les opérations produites respectent le contrat api/openapi.yaml', () {
    final contrat = loadYaml(File('../../api/openapi.yaml').readAsStringSync()) as YamlMap;
    final schemas = contrat['components']['schemas'] as YamlMap;
    final types = (schemas['Operation']['discriminator']['mapping'] as YamlMap).keys.toSet();
    final schemaDe = {
      'EMISSION_DOCUMENT': 'EmissionDocument', 'VERSEMENT': 'VersementSaisie', 'CHARGEMENT': 'Chargement',
      'HANOUT': 'NouveauHanout', 'CREDIT_HANOUT': 'CreditHanoutSaisie', 'PARAMETRES_AMBULANT': 'ParametresAmbulantSaisie',
      'RESOLUTION_CONTESTATION': 'ResolutionContestation', 'TARIF': 'Tarif',
    };

    final m = monter(parametres: {
      'politiqueDepassementCredit': 'AVERTIR', 'seuilEcartInventairePourcent': 5,
      'mentionsLegales': {'ice': '001234567000089', 'identifiantFiscal': '1', 'registreCommerce': '2', 'assujettiTva': false},
    });
    final h = m.creerHanout(nomHanout: 'Nouveau', telephone: '+212612345678');
    m.emettre(hanoutId: h['id'] as String, lignes: [coca1(7)..remise = 100], type: 'FACTURE',
        reglements: [const Reglement('CHEQUE', 1000, chequeNumero: '123')], confirmeParHanout: true);
    m.emettre(hanoutId: hanoutId, lignes: [LigneVente(produitId: coca, quantite: 1, prixUnitaire: 750, tarif: 'NEGOCIE', retenir: true)]);
    m.versement(hanoutId, 500);
    m.definirCredit(hanoutId, plafond: 100000, delaiMaxJours: 30);
    m.definirPolitique('BLOQUER');
    m.chargerManuel({coca: 12});
    m.resoudreContestation('99999999-9999-4999-8999-999999999999', commentaire: 'Avoir émis');

    final champsSaisie = (schemas['LigneDocumentSaisie']['properties'] as YamlMap).keys.toSet();
    for (final op in m.s.enAttente()) {
      expect(types, contains(op['type']));
      expect(op.keys.toSet(), {'id', 'type', 'creeLe', 'donnees'});
      final schema = schemas[schemaDe[op['type']]] as YamlMap;
      final donnees = op['donnees'] as Map;
      for (final requis in (schema['required'] as YamlList?) ?? const []) {
        expect(donnees, contains(requis), reason: '${op['type']} : $requis manquant');
      }
      expect((schema['properties'] as YamlMap).keys.toSet(), containsAll(donnees.keys), reason: '${op['type']} : champ hors contrat');
      if (op['type'] == 'EMISSION_DOCUMENT') {
        expect(RegExp(schemas['NumeroDocument']['pattern'] as String).hasMatch(donnees['numero'] as String), isTrue);
        final taux = (schemas['TauxTva']['enum'] as YamlList).toSet();
        for (final l in (donnees['lignes'] as List).cast<Map>()) {
          expect(champsSaisie, containsAll(l.keys));
          expect(taux, contains(l['tauxTva']), reason: 'taux de TVA figé sur chaque ligne');
        }
      }
    }
  });
}

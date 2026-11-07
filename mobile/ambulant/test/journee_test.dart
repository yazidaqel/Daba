import 'dart:convert';
import 'dart:io';

import 'package:daba_ambulant/domaine.dart';
import 'package:daba_ambulant/ecrans/karni.dart';
import 'package:daba_ambulant/ecrans/signature.dart';
import 'package:daba_ambulant/ecrans/stock.dart';
import 'package:daba_ambulant/ecrans/tableau.dart';
import 'package:daba_ambulant/etat.dart';
import 'package:daba_ambulant/ui.dart';
import 'package:daba_shared/daba_shared.dart';
import 'package:flutter/material.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/testing.dart';

import 'vente_test.dart' show coca, coca1, hanoutId, moi, monter;

const the = '77777777-7777-4777-8777-777777777777';
const grossiste = '88888888-8888-4888-8888-888888888888';

/// Document du hanout émis il y a [jours] jours (date réécrite sur l'appareil).
Map<String, dynamic> vieux(Ambulant m, Map<String, dynamic> doc, int jours) {
  final d = {...doc, 'emisLe': DateTime.now().toUtc().subtract(Duration(days: jours)).toIso8601String()};
  m.s.enregistrer('DOCUMENT', d['id'] as String, d, local: true);
  return d;
}

void main() {
  group('retours (§3.2)', () {
    test('hanout → ambulant : entrée en stock seulement si remis en vente ; solde inchangé', () {
      final m = monter();
      m.emettre(hanoutId: hanoutId, lignes: [coca1(5)]);
      final solde = m.solde(hanoutId);
      m.retour(versGrossiste: false, motif: 'INVENDU', quantites: {coca: 3}, hanoutId: hanoutId, remisEnVente: true);
      expect(m.stock(coca), 103, reason: 'la vente a pris le stock promo, le retour entre dans le stock normal');
      m.retour(versGrossiste: false, motif: 'ABIME', quantites: {coca: 2}, hanoutId: hanoutId);
      expect(m.stock(coca), 103, reason: 'produit abîmé : hors stock vendable');
      expect(m.solde(hanoutId), solde, reason: 'le remboursement passe par un avoir distinct');
      final op = m.s.enAttente().last['donnees'];
      expect(op, {'sens': 'HANOUT_VERS_AMBULANT', 'hanoutId': hanoutId, 'remisEnVente': false, 'motif': 'ABIME',
        'lignes': [{'produitId': coca, 'quantite': 2}]});
    });

    test('ambulant → grossiste : sortie de stock, grossiste obligatoire', () {
      final m = monter();
      expect(() => m.retour(versGrossiste: true, motif: 'PERIME', quantites: {coca: 1}), throwsArgumentError);
      m.retour(versGrossiste: true, motif: 'PERIME', quantites: {coca: 4}, grossisteId: grossiste);
      expect(m.stock(coca), 96);
      expect(m.mouvements().first, containsPair('type', 'RETOUR'));
      expect(m.mouvements().first['quantite'], -4);
    });

    test('grossistes connus par les promos, réservations et bons de sortie', () {
      final m = monter();
      m.s.enregistrer('PROMO', 'p', {'grossiste': {'id': grossiste, 'nom': 'Derb Sultan'}});
      m.s.enregistrer('BON_SORTIE', 'b', {'grossisteId': '99999999-9999-4999-8999-999999999999'});
      expect(m.grossistes(), {'99999999-9999-4999-8999-999999999999': null, grossiste: 'Derb Sultan'});
    });
  });

  group('inventaire (RG-S4)', () {
    test('motif exigé au-delà du seuil, même règle que le serveur', () {
      expect(Ambulant.motifRequis(97, 100, 5), isFalse);
      expect(Ambulant.motifRequis(105, 100, 5), isFalse, reason: 'exactement au seuil');
      expect(Ambulant.motifRequis(106, 100, 5), isTrue);
      expect(Ambulant.motifRequis(100, 100, 0), isFalse);
      expect(Ambulant.motifRequis(1, 0, 50), isTrue, reason: 'stock théorique nul');
    });

    test('écart sans motif refusé ; avec motif, le stock local devient le compté', () {
      final m = monter(parametres: {'politiqueDepassementCredit': 'AVERTIR', 'seuilEcartInventairePourcent': 5});
      expect(() => m.inventaire({coca: (compte: 90, motif: ' ')}),
          throwsA(isA<VenteRefusee>().having((e) => e.motif, 'motif', RefusVente.motifEcartRequis)));
      expect(m.s.nombreEnAttente, 0);
      m.inventaire({coca: (compte: 90, motif: 'Casse'), the: (compte: 0, motif: null)});
      expect(m.stock(coca), 90);
      expect(m.stockTotal(coca), 95, reason: 'le stock promo ne fait pas partie de l\'inventaire');
      expect(m.s.enAttente().single['donnees']['lignes'], [
        {'produitId': coca, 'quantiteComptee': 90, 'motifEcart': 'Casse'},
        {'produitId': the, 'quantiteComptee': 0},
      ]);
      expect(m.mouvements().single, containsPair('type', 'AJUSTEMENT'));
      expect(m.mouvements().single['quantite'], -10);
    });
  });

  group('karni : imputation et ancienneté', () {
    test('imputation FIFO par défaut, pièces choisies d\'abord (RG-C2)', () {
      final restes = [('a', 1000), ('b', 500), ('c', 800)];
      expect(Ambulant.imputer(1200, restes), {'a': 1000, 'b': 200});
      expect(Ambulant.imputer(1200, restes, prioritaires: ['c']), {'c': 800, 'a': 400});
      expect(Ambulant.imputer(5000, restes), {'a': 1000, 'b': 500, 'c': 800});
    });

    test('versement sur une pièce choisie : imputations envoyées et restes à jour', () {
      final m = monter();
      m.emettre(hanoutId: hanoutId, lignes: [coca1(1)]); // 800 à crédit
      final d2 = m.emettre(hanoutId: hanoutId, lignes: [coca1(2)]); // 1600
      m.versement(hanoutId, 1000, pieces: [d2['id'] as String]);
      expect(m.s.enAttente().last['donnees']['imputations'], [{'documentId': d2['id'], 'montant': 1000}]);
      expect([for (final r in m.restesDus(hanoutId)) r.$2], [800, 600]);
      m.versement(hanoutId, 900); // FIFO : 800 sur d1, 100 sur d2
      expect(m.s.enAttente().last['donnees'], isNot(contains('imputations')));
      expect([for (final r in m.restesDus(hanoutId)) (r.$1['id'], r.$2)], [(d2['id'], 500)]);
      expect(m.solde(hanoutId), 500);
    });

    test('ancienneté 0-15 / 15-30 / +30 j, total = solde RG-C1', () {
      final m = monter();
      vieux(m, m.emettre(hanoutId: hanoutId, lignes: [coca1(5)]), 40); // 4000
      vieux(m, m.emettre(hanoutId: hanoutId, lignes: [coca1(2)]), 20); // 1600
      m.emettre(hanoutId: hanoutId, lignes: [coca1(1)]); // 800
      m.versement(hanoutId, 1000); // FIFO sur la plus ancienne
      expect(m.anciennete(), (total: 5400, de0a15: 800, de15a30: 1600, plusDe30: 3000));
      // Solde serveur au-delà des pièces connues de l'appareil : compté dans la plus ancienne tranche.
      m.s.enregistrer('COMPTE_CREDIT', 'c', {'hanout': {'id': hanoutId, 'nom': ''}, 'solde': 700});
      expect(m.anciennete(), (total: 6100, de0a15: 800, de15a30: 1600, plusDe30: 3700));
    });
  });

  test('favoris du hanout : les plus achetés en tête (§3.4)', () {
    final m = monter();
    m.emettre(hanoutId: hanoutId, lignes: [LigneVente(produitId: the, quantite: 2, prixUnitaire: 1500), coca1(1)]);
    m.emettre(hanoutId: hanoutId, lignes: [coca1(3)]);
    expect(m.favoris(hanoutId), [coca, the]);
    expect(m.favoris('autre'), isEmpty);
  });

  test('tableau de bord : CA, encaissé, crédit et espèces attendues sans double compte (§3.8)', () {
    final m = monter();
    final jour = DateTime.now().copyWith(hour: 0, minute: 0, second: 0, millisecond: 0, microsecond: 0);
    final d = m.emettre(hanoutId: hanoutId, lignes: [coca1(10)], reglements: [const Reglement('ESPECES', 3000), const Reglement('CHEQUE', 1000, chequeNumero: '1')]);
    vieux(m, m.emettre(hanoutId: hanoutId, lignes: [coca1(1)]), 40);
    m.versement(hanoutId, 500);
    m.versement(hanoutId, 200, mode: 'CHEQUE', chequeNumero: '2');
    expect(m.activite(jour), (ca: 8000, encaisse: 4700, credit: 4000));
    expect(m.especesAttendues(jour), 3500);
    // Le serveur renvoie le versement de livraison : les espèces du document ne comptent plus deux fois.
    m.s.enregistrer('VERSEMENT', 'v', {'hanoutId': hanoutId, 'documentId': d['id'], 'mode': 'ESPECES', 'montant': 3000,
      'imputations': [], 'recuLe': DateTime.now().toUtc().toIso8601String()});
    expect(m.especesAttendues(jour), 3500);
    expect(m.activite(jour).encaisse, 4700, reason: 'règlement de livraison déjà dans montantRegle');
    expect(m.caisseDeclaree(jour), isNull);
    m.declarerCaisse(jour, 3400);
    expect(m.caisseDeclaree(jour), 3400);
  });

  test('signature : mode SIGNATURE et PNG de quelques Ko (§3.4)', () {
    final m = monter();
    m.emettre(hanoutId: hanoutId, lignes: [coca1(1)], signaturePng: 'iVBORw0KGgo=', confirmeParHanout: true);
    expect(m.s.enAttente().single['donnees']['confirmationHanout'], allOf(containsPair('mode', 'SIGNATURE'), contains('le')));
  });

  for (final langue in languesDaba) {
    testWidgets('écrans de fin de journée sur un petit téléphone, en ${langue.toLanguageTag()}', (tester) async {
      final m = monter(parametres: {'politiqueDepassementCredit': 'AVERTIR', 'seuilEcartInventairePourcent': 5});
      m.s.ecrire('session', jsonEncode({'utilisateur': {'id': moi, 'nom': 'Said'}, 'prefixeNumerotation': 'K7Q2'}));
      vieux(m, m.emettre(hanoutId: hanoutId, lignes: [coca1(3)]), 20);
      final daba = Daba(m.s, Api('http://serveur', client: MockClient((_) async => throw const SocketException('hors ligne'))),
          const FlutterSecureStorage());
      tester.view..physicalSize = const Size(360, 740)..devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      Future<void> ouvrir(Widget ecran) async {
        await tester.pumpWidget(DabaScope(daba: daba, child: MaterialApp(
            locale: langue, supportedLocales: languesDaba, localizationsDelegates: delegationsDaba,
            theme: themeDaba(langue, Brightness.light), home: Scaffold(body: ecran))));
        await tester.pumpAndSettle();
      }

      for (final ecran in [const EcranRetour(), EcranRetour(hanoutId: hanoutId), const EcranTableauDeBord(), const OngletKarni(), const EcranSignature()]) {
        await ouvrir(ecran);
      }
      await ouvrir(const EcranInventaire());
      await tester.enterText(find.byType(TextField).first, '90');
      await tester.pump();
      expect(find.byType(TextField), findsNWidgets(2), reason: 'écart de 10 % au-delà du seuil de 5 % : motif demandé');
    });
  }

  testWidgets('PNG de signature réduit à 320 px, quelques Ko', (tester) async {
    final traits = [
      [for (var x = 0.0; x < 360; x += 4) Offset(x, 72 + 40 * (x % 40 < 20 ? 1 : -1))],
      [for (var x = 20.0; x < 340; x += 3) Offset(x, 30 + x / 4)],
    ];
    final png = (await tester.runAsync(() => EcranSignature.png(traits, const Size(360, 144))))!;
    expect(png.sublist(1, 4), 'PNG'.codeUnits);
    expect(png.length, lessThan(16 * 1024));
  });
}

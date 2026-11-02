import 'dart:convert';
import 'dart:io';

import 'package:daba_shared/daba_shared.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

/// Faux serveur : applique les opérations (idempotence par id) et sert des lots de changements.
class FauxServeur {
  final appliquees = <String>[];
  final recus = <List<String>>[];
  final lots = <Map<String, dynamic>>[];
  bool coupe = false;
  int? statutForce;
  String? typeForce;
  String? curseurDemande;

  String _appliquer(String id) {
    if (appliquees.contains(id)) return 'DEJA_APPLIQUEE';
    appliquees.add(id);
    return 'APPLIQUEE';
  }

  late final client = MockClient((req) async {
    if (coupe) throw const SocketException('réseau coupé');
    if (statutForce != null) {
      return http.Response(jsonEncode({'status': statutForce, 'type': ?typeForce}), statutForce!);
    }
    if (req.url.path == '/v1/sync/operations') {
      final ops = (jsonDecode(req.body)['operations'] as List).cast<Map>();
      recus.add([for (final o in ops) o['id'] as String]);
      return http.Response(
          jsonEncode({
            'resultats': [
              for (final o in ops) {'operationId': o['id'], 'statut': _appliquer(o['id'] as String)}
            ]
          }),
          200);
    }
    curseurDemande = req.url.queryParameters['curseur'];
    if (lots.isEmpty) return http.Response(jsonEncode({'curseur': 'fin', 'plusDeChangements': false, 'changements': []}), 200);
    final lot = lots.removeAt(0);
    return http.Response(jsonEncode(lot['_statut'] == null ? lot : {'status': lot['_statut']}), (lot['_statut'] ?? 200) as int);
  });
}

void main() {
  late Stockage s;
  late FauxServeur serveur;
  late Synchronisation sync;
  const appareil = '11111111-1111-4111-8111-111111111111';

  setUp(() {
    s = Stockage.memoire();
    serveur = FauxServeur();
    sync = Synchronisation(s, Api('http://serveur', client: serveur.client), appareilId: appareil);
  });

  group('file d\'opérations', () {
    test('pousse dans l\'ordre du geste et acquitte', () async {
      final ids = [for (var i = 0; i < 3; i++) s.enfiler('VERSEMENT', {'n': i})];
      await sync.synchroniser();
      expect(serveur.recus.single, ids);
      expect(s.nombreEnAttente, 0);
    });

    test('sans réseau, rien n\'est perdu et le rejeu garde les mêmes UUID', () async {
      final id = s.enfiler('VERSEMENT', {'n': 1}, deltas: {('SOLDE', 'h1'): -500});
      serveur.coupe = true;
      await expectLater(sync.synchroniser(), throwsA(isA<HorsLigne>()));
      expect(s.nombreEnAttente, 1);
      expect(s.delta('SOLDE', 'h1'), -500);

      serveur.coupe = false;
      await sync.synchroniser();
      expect(serveur.recus.single, [id]);
      expect(s.nombreEnAttente, 0);
      expect(s.delta('SOLDE', 'h1'), 0, reason: 'le serveur reflète maintenant le versement');
    });

    test('un rejeu déjà appliqué côté serveur est acquitté sans doublon', () async {
      final id = s.enfiler('VERSEMENT', {'n': 1});
      serveur.appliquees.add(id); // la réponse précédente s'était perdue
      await sync.synchroniser();
      expect(s.nombreEnAttente, 0);
      expect(serveur.appliquees.where((x) => x == id), hasLength(1));
    });

    test('les deltas restent tant que le tirage n\'est pas complet', () async {
      s.enfiler('VERSEMENT', {'n': 1}, deltas: {('SOLDE', 'h1'): -500});
      serveur.lots.add({'_statut': 500});
      await expectLater(sync.synchroniser(), throwsA(isA<ProblemeApi>()));
      expect(s.nombreEnAttente, 0);
      expect(s.delta('SOLDE', 'h1'), -500);
    });

    test('opérations rejetées gardées pour affichage', () async {
      final client = MockClient((req) async => http.Response(
          jsonEncode(req.url.path == '/v1/sync/operations'
              ? {
                  'resultats': [
                    {'operationId': (jsonDecode(req.body)['operations'] as List).single['id'], 'statut': 'REJETEE', 'erreur': {'detail': 'x'}}
                  ]
                }
              : {'curseur': 'c', 'plusDeChangements': false, 'changements': []}),
          200));
      s.enfiler('EMISSION_DOCUMENT', {});
      await Synchronisation(s, Api('http://serveur', client: client), appareilId: appareil).synchroniser();
      expect(s.operationsSignalees().single['statut'], 'REJETEE');
    });

    test('un serveur qui n\'acquitte aucune opération envoyée ne fait pas boucler', () async {
      var appels = 0;
      final client = MockClient((req) async {
        if (req.url.path != '/v1/sync/operations') {
          return http.Response(jsonEncode({'curseur': 'c', 'plusDeChangements': false, 'changements': []}), 200);
        }
        appels++;
        return http.Response(jsonEncode({'resultats': [{'operationId': 'inconnue', 'statut': 'APPLIQUEE'}]}), 200);
      });
      s.enfiler('VERSEMENT', {});
      await Synchronisation(s, Api('http://serveur', client: client), appareilId: appareil).synchroniser();
      expect(appels, 1);
      expect(s.nombreEnAttente, 1);
    });

    test('seul un 403 « appareil-revoque » révoque l\'appareil ; un autre 403 ne touche à rien', () async {
      s.enfiler('VERSEMENT', {});
      serveur.statutForce = 403;
      await expectLater(sync.synchroniser(), throwsA(isA<ProblemeApi>()));
      expect(s.nombreEnAttente, 1);
      serveur.typeForce = 'https://daba.ma/problemes/appareil-revoque';
      await expectLater(sync.synchroniser(), throwsA(isA<AppareilRevoque>()));
    });

    test('un document rejeté reste visible, marqué refusé', () async {
      final client = MockClient((req) async => http.Response(
          jsonEncode(req.url.path == '/v1/sync/operations'
              ? {
                  'resultats': [
                    {'operationId': (jsonDecode(req.body)['operations'] as List).single['id'], 'statut': 'REJETEE'}
                  ]
                }
              : {'curseur': 'c', 'plusDeChangements': false, 'changements': []}),
          200));
      s.enregistrer('DOCUMENT', 'd1', {'id': 'd1', 'statut': 'EMIS'}, local: true);
      s.enfiler('EMISSION_DOCUMENT', {'documentId': 'd1'});
      await Synchronisation(s, Api('http://serveur', client: client), appareilId: appareil).synchroniser();
      expect(s.objet('DOCUMENT', 'd1')!['rejete'], isTrue);
    });
  });

  group('tirage', () {
    Map<String, dynamic> lot(String curseur, bool plus, List<Map<String, dynamic>> c) =>
        {'curseur': curseur, 'plusDeChangements': plus, 'changements': c};

    test('suit le curseur tant qu\'il y a des changements', () async {
      serveur.lots
        ..add(lot('c1', true, [
          {'entite': 'HANOUT', 'id': 'h1', 'action': 'UPSERT', 'donnees': {'id': 'h1', 'nom': 'Hanout 1'}}
        ]))
        ..add(lot('c2', false, [
          {'entite': 'HANOUT', 'id': 'h1', 'action': 'SUPPRESSION'},
          {'entite': 'HANOUT', 'id': 'h2', 'action': 'UPSERT', 'donnees': {'id': 'h2', 'nom': 'Hanout 2'}}
        ]));
      await sync.synchroniser();
      expect(s.lire('curseur'), 'c2');
      expect(serveur.curseurDemande, 'c1');
      expect(s.objets('HANOUT').map((h) => h['id']), ['h2']);
    });

    test('un document de vente n\'est jamais supprimé', () async {
      s.enregistrer('DOCUMENT', 'd1', {'id': 'd1'});
      serveur.lots.add(lot('c', false, [{'entite': 'DOCUMENT', 'id': 'd1', 'action': 'SUPPRESSION'}]));
      await sync.synchroniser();
      expect(s.objet('DOCUMENT', 'd1'), isNotNull);
    });

    test('410 : resynchronisation complète sans perdre les objets locaux', () async {
      s.ecrire('curseur', 'vieux');
      s.enregistrer('HANOUT', 'serveur', {'id': 'serveur'});
      s.enregistrer('HANOUT', 'terrain', {'id': 'terrain'}, local: true);
      serveur.lots
        ..add({'_statut': 410})
        ..add(lot('neuf', false, [{'entite': 'HANOUT', 'id': 'h9', 'action': 'UPSERT', 'donnees': {'id': 'h9'}}]));
      await sync.synchroniser();
      expect(serveur.curseurDemande, isNull);
      expect(s.objets('HANOUT').map((h) => h['id']).toSet(), {'terrain', 'h9'});
    });

    test('410 puis coupure : la copie locale n\'est pas purgée', () async {
      s.ecrire('curseur', 'vieux');
      s.enregistrer('PRODUIT', 'p1', {'id': 'p1'});
      serveur.lots.add({'_statut': 410});
      serveur.lots.add({'_statut': 503});
      await expectLater(sync.synchroniser(), throwsA(isA<ProblemeApi>()));
      expect(s.objet('PRODUIT', 'p1'), isNotNull);
      expect(s.lire('curseur'), 'vieux');
    });

    test('les paramètres du serveur remplacent la copie locale', () async {
      s.enregistrer('PARAMETRES', 'local', {'politiqueDepassementCredit': 'BLOQUER', 'mentionsLegales': {'ice': '1'}}, local: true);
      serveur.lots.add(lot('c', false, [
        {'entite': 'PARAMETRES', 'id': 'srv', 'action': 'UPSERT', 'donnees': {'politiqueDepassementCredit': 'AVERTIR', 'seuilEcartInventairePourcent': 5}}
      ]));
      await sync.synchroniser();
      expect(s.objets('PARAMETRES').single['mentionsLegales'], isNull);
    });

    test('rattrape la numérotation sur les documents de l\'appareil', () async {
      serveur.lots.add(lot('c', false, [
        {'entite': 'DOCUMENT', 'id': 'd1', 'action': 'UPSERT', 'donnees': {'type': 'BON_LIVRAISON', 'sequence': 41, 'appareilId': appareil}},
        {'entite': 'DOCUMENT', 'id': 'd2', 'action': 'UPSERT', 'donnees': {'type': 'BON_LIVRAISON', 'sequence': 99, 'appareilId': 'autre'}},
      ]));
      await sync.synchroniser();
      expect(s.sequenceSuivante('BL'), 42);
    });

    test('le compte de crédit du serveur remplace celui créé sur l\'appareil', () async {
      s.enregistrer('COMPTE_CREDIT', 'local', {'id': 'local', 'hanout': {'id': 'h1'}, 'plafond': 100}, local: true);
      serveur.lots.add(lot('c', false, [
        {'entite': 'COMPTE_CREDIT', 'id': 'srv', 'action': 'UPSERT', 'donnees': {'id': 'srv', 'hanout': {'id': 'h1'}, 'solde': 0, 'plafond': 100}}
      ]));
      await sync.synchroniser();
      expect(s.objets('COMPTE_CREDIT').map((c) => c['id']), ['srv']);
    });
  });

  group('numérotation (RG-F1)', () {
    test('continue, par série, au format du contrat', () {
      expect([for (var i = 0; i < 3; i++) s.sequenceSuivante('BL')], [1, 2, 3]);
      expect(s.sequenceSuivante('FA'), 1);
      expect(Stockage.numero('K7Q2', 'BL', 123), 'K7Q2-BL-000123');
      expect(RegExp(r'^[A-Z0-9]{3,6}-(BL|FA|AV)-[0-9]{6,}$').hasMatch(Stockage.numero('K7Q2', 'FA', 1234567)), isTrue);
    });

    test('une émission annulée ne laisse pas de trou', () {
      s.sequenceSuivante('BL');
      expect(() => s.transaction(() {
            s.sequenceSuivante('BL');
            throw StateError('échec pendant l\'émission');
          }), throwsStateError);
      expect(s.sequenceSuivante('BL'), 2);
    });
  });

  test('format des montants', () {
    expect(dh(12550), '125,50 DH');
    expect(dh(-5), '-0,05 DH');
    expect(centimes('12,5'), 1250);
    expect(centimes('abc'), isNull);
    expect(distanceM(33.5731, -7.5898, 33.5731, -7.5898), 0);
    expect(distanceM(33.5731, -7.5898, 33.5821, -7.5898), closeTo(1000, 5));
  });
}

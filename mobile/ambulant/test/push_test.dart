import 'dart:convert';
import 'dart:io';

import 'package:daba_ambulant/etat.dart';
import 'package:daba_ambulant/push.dart';
import 'package:daba_shared/daba_shared.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

void main() {
  group('push → écran', () {
    test('chaque type du serveur ouvre son écran avec sa cible', () {
      Cible? c(String type) => ciblePush({'type': type, 'cibleId': 'x'});
      expect(c('RUPTURE_SIGNALEE'), (vers: Vers.depannage, id: 'x'));
      expect(c('DEMANDE_PRISE')?.vers, Vers.depannage);
      expect(c('PROMO_CONFIRMEE'), (vers: Vers.offre, id: 'x'));
      expect(c('PROMO_BIENTOT_EPUISEE')?.vers, Vers.offre);
      expect(c('RESERVATION_EXPIRE_BIENTOT'), (vers: Vers.reservation, id: 'x'));
      expect(c('PLAFOND_CREDIT_ATTEINT'), (vers: Vers.hanout, id: 'x'));
      expect(c('RECEPTION_ECART'), (vers: Vers.document, id: 'x'));
      expect(c('CONTESTATION')?.vers, Vers.contestations);
    });

    test('type inconnu ou absent : aucun écran ; cible absente : l\'onglet seul', () {
      expect(ciblePush({'type': 'OTP'}), isNull);
      expect(ciblePush({}), isNull);
      expect(ciblePush({'type': 'PROMO_CONFIRMEE'}), (vers: Vers.offre, id: null));
    });
  });

  group('jeton push (PUT /v1/appareils/{id}/push)', () {
    late List<Map<String, dynamic>> envois;
    late bool enLigne;
    late Api api;
    String? jetonFcm;
    var lectures = 0;

    setUp(() {
      envois = [];
      enLigne = true;
      jetonFcm = 'fcm-1';
      lectures = 0;
      api = Api('http://serveur', client: MockClient((req) async {
        if (!enLigne) throw const SocketException('pas de réseau');
        expect(req.method, 'PUT');
        expect(req.url.path, '/v1/appareils/app-1/push');
        envois.add(jsonDecode(req.body) as Map<String, dynamic>);
        return http.Response('', 204);
      }));
    });

    DeclarationPush declaration() => DeclarationPush(api, () async {
          lectures++;
          return jetonFcm;
        });

    test('en ligne : déclaré une fois, au format JetonPush du contrat', () async {
      final d = declaration();
      await d.declarer('app-1');
      await d.declarer('app-1');
      expect(envois, [{'jeton': 'fcm-1', 'plateforme': 'ANDROID', 'langue': 'fr'}]);
      expect(lectures, 1);
    });

    test('hors ligne : rien en file, la synchro suivante réessaie', () async {
      final d = declaration();
      enLigne = false;
      await expectLater(d.declarer('app-1'), throwsA(isA<HorsLigne>()));
      enLigne = true;
      await d.declarer('app-1');
      expect(envois, hasLength(1));
    });

    test('jeton renouvelé, langue changée, nouvelle session : redéclaré', () async {
      final d = declaration();
      await d.declarer('app-1');
      d.jeton = 'fcm-2'; // onTokenRefresh
      await d.declarer('app-1');
      api.langue = 'ary-Latn';
      await d.declarer('app-1');
      d.nouvelleSession();
      await d.declarer('app-1');
      expect(envois.map((e) => '${e['jeton']}|${e['langue']}'), ['fcm-1|fr', 'fcm-2|fr', 'fcm-2|ary-Latn', 'fcm-2|ary-Latn']);
    });

    test('push non configuré (aucun jeton) : aucun appel ; déconnexion : jeton relu ensuite', () async {
      jetonFcm = null;
      final d = declaration();
      await d.declarer('app-1');
      expect(envois, isEmpty);
      jetonFcm = 'fcm-3';
      await d.declarer('app-1');
      d.oublier();
      await d.declarer('app-1');
      expect(envois.map((e) => e['jeton']), ['fcm-3', 'fcm-3']);
      expect(lectures, 3);
    });
  });

  group('push dans l\'app (CNDP, PIN, déconnexion hors ligne)', () {
    late List<String> appels;
    late bool enLigne;
    late int statutDeconnexion;
    late FauxPush push;
    late Stockage s;
    const coffre = FlutterSecureStorage();

    Daba ouvrir({String? versionTexte = versionTexteCndp, Etape etape = Etape.pret}) {
      TestWidgetsFlutterBinding.ensureInitialized();
      FlutterSecureStorage.setMockInitialValues({'jeton': 'acces-1', 'refresh': 'r', 'pin': '1234'});
      appels = [];
      enLigne = true;
      statutDeconnexion = 204;
      push = FauxPush();
      s = Stockage.memoire()
        ..ecrire('appareilId', 'app-1')
        ..ecrire('session', jsonEncode({'utilisateur': {'id': 'moi', 'nom': 'Said'}, 'prefixeNumerotation': 'K7Q2'}))
        ..ecrire('consentement', versionTexte == null ? null : jsonEncode({'versionTexte': versionTexte, 'traitementDonnees': true}));
      final api = Api('http://serveur', client: MockClient((req) async {
        if (!enLigne) throw const SocketException('pas de réseau');
        appels.add('${req.method} ${req.url.path} ${req.headers['Authorization'] ?? ''}'.trim());
        return switch (req.url.path) {
          '/v1/auth/deconnexion' =>
            http.Response(statutDeconnexion == 204 ? '' : jsonEncode({'status': statutDeconnexion}), statutDeconnexion),
          '/v1/appareils/app-1/push' => http.Response('', 204),
          '/v1/sync/operations' => http.Response(jsonEncode({'resultats': []}), 200),
          _ => http.Response(jsonEncode({'curseur': 'c', 'plusDeChangements': false, 'changements': []}), 200),
        };
      }))
        ..jeton = 'acces-1';
      return Daba(s, api, coffre, push)..etape = etape;
    }

    List<String> pushs() => appels.where((a) => a.contains('/push')).toList();

    test('ancien texte CNDP (sans FCM) : Firebase jamais démarré, aucun jeton demandé ni déclaré', () async {
      final daba = ouvrir(versionTexte: '2026-11-v2');
      await daba.synchroniser();
      expect(push.demarrages, 0);
      expect(push.lecturesJeton, 0);
      expect(pushs(), isEmpty);
      expect(daba.consentementProfil, isTrue, reason: 'le texte v2 suffit au profil');
      expect(ouvrir(versionTexte: 'cndp-2026-10-fr').consentementProfil, isFalse, reason: 'version inconnue de l\'app');
    });

    test("texte actuel mais PIN à l'écran : rien ; après le PIN : démarré une fois, jeton déclaré", () async {
      final daba = ouvrir(etape: Etape.verrouille);
      await daba.synchroniser();
      expect(push.demarrages, 0);
      daba.etape = Etape.pret;
      await daba.synchroniser();
      await daba.synchroniser();
      expect(push.demarrages, 1);
      expect(pushs(), ['PUT /v1/appareils/app-1/push Bearer acces-1']);
    });

    test('notifications reçues sous le PIN : ni bandeau, ni synchro, ni déclaration ; la cible attend', () async {
      final daba = ouvrir();
      await daba.synchroniser();
      final avis = <String>[];
      daba.avisPush.listen((a) => avis.add(a.texte));
      daba.etape = Etape.verrouille;
      appels.clear();
      const promo = RemoteMessage(data: {'type': 'PROMO_CONFIRMEE', 'cibleId': 'd'}, notification: RemoteNotification(body: 'Promo'));
      push.premierPlan!(promo);
      push.ouverte!(const RemoteMessage(data: {'type': 'RESERVATION_EXPIRE_BIENTOT', 'cibleId': 'r'}));
      push.nouveauJeton!('fcm-2');
      await pumpEventQueue();
      expect(appels, isEmpty);
      expect(avis, isEmpty);
      expect(daba.cibleEnAttente, (vers: Vers.reservation, id: 'r'), reason: "ouverte par l'accueil après le PIN");
      daba.etape = Etape.pret;
      push.premierPlan!(promo);
      await pumpEventQueue();
      expect(avis, ['Promo']);
      expect(appels, contains(startsWith('GET /v1/sync/changements')));
    });

    test('déconnexion hors ligne : fermeture de session et oubli du jeton FCM rejoués au retour du réseau', () async {
      final daba = ouvrir();
      enLigne = false;
      await daba.deconnecter();
      await daba.rejouerDeconnexion();
      expect(await coffre.read(key: 'sessionAFermer'), 'acces-1');
      expect(s.lire('push.aOublier'), '1');
      expect(push.oublis, greaterThan(0));
      enLigne = true;
      push.fcmJoignable = true;
      await daba.rejouerDeconnexion();
      expect(appels, ['POST /v1/auth/deconnexion Bearer acces-1']);
      expect(await coffre.read(key: 'sessionAFermer'), isNull);
      expect(s.lire('push.aOublier'), isNull);
      appels.clear();
      await daba.rejouerDeconnexion();
      expect(appels, isEmpty, reason: 'plus rien à rejouer');
      daba.dispose();
    });

    test('session déjà fermée côté serveur (401) : la fermeture est considérée faite', () async {
      final daba = ouvrir();
      enLigne = false;
      await daba.deconnecter();
      enLigne = true;
      statutDeconnexion = 401;
      push.fcmJoignable = true;
      await daba.rejouerDeconnexion();
      expect(await coffre.read(key: 'sessionAFermer'), isNull);
      expect(s.lire('push.aOublier'), isNull);
      daba.dispose();
    });

    test('déconnexion en ligne : session fermée tout de suite, rien à rejouer', () async {
      final daba = ouvrir();
      push.fcmJoignable = true;
      await daba.deconnecter();
      await daba.rejouerDeconnexion();
      expect(appels.where((a) => a.startsWith('POST /v1/auth/deconnexion')), hasLength(1));
      expect(await coffre.read(key: 'sessionAFermer'), isNull);
      expect(s.lire('push.aOublier'), isNull);
      daba.dispose();
    });
  });
}

/// Push sans Firebase : compte les appels et garde les écouteurs pour les déclencher à la main.
class FauxPush extends Push {
  int demarrages = 0;
  int lecturesJeton = 0;
  int oublis = 0;
  bool fcmJoignable = false;
  void Function(String)? nouveauJeton;
  void Function(RemoteMessage)? premierPlan;
  void Function(RemoteMessage)? ouverte;

  @override
  Future<void> demarrer({
    required void Function(String jeton) nouveauJeton,
    required void Function(RemoteMessage) premierPlan,
    required void Function(RemoteMessage) ouverte,
  }) async {
    demarrages++;
    actif = true;
    this.nouveauJeton = nouveauJeton;
    this.premierPlan = premierPlan;
    this.ouverte = ouverte;
  }

  @override
  Future<String?> jeton() async {
    lecturesJeton++;
    return actif ? 'fcm-1' : null;
  }

  @override
  Future<bool> oublierJeton() async {
    oublis++;
    return fcmJoignable;
  }

  @override
  Future<void> nommerCanaux(T t) async {}
}

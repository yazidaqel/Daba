import 'dart:convert';
import 'dart:io';

import 'package:daba_ambulant/ecrans/profil.dart';
import 'package:daba_ambulant/etat.dart';
import 'package:daba_ambulant/ui.dart';
import 'package:daba_shared/daba_shared.dart';
import 'package:flutter/material.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

import 'vente_test.dart' show appareil, coca, hanoutId, moi, monter;

/// Faux serveur : répond [resultat] à chaque opération poussée, puis sert [changements].
Synchronisation sync(Stockage s, {Map<String, dynamic> Function(Map op)? resultat, List<Map<String, dynamic>> changements = const []}) {
  final client = MockClient((req) async {
    if (req.url.path == '/v1/sync/operations') {
      final ops = (jsonDecode(req.body)['operations'] as List).cast<Map>();
      return http.Response.bytes(utf8.encode(jsonEncode({'resultats': [
        for (final o in ops) {'operationId': o['id'], 'statut': 'APPLIQUEE', ...?resultat?.call(o)}
      ]})), 200);
    }
    return http.Response(jsonEncode({'curseur': 'c', 'plusDeChangements': false, 'changements': changements}), 200);
  });
  return Synchronisation(s, Api('http://serveur', client: client), appareilId: appareil, prefixe: 'K7Q2');
}

void main() {
  group('profil (§3.1)', () {
    test('saisi hors ligne : tout le profil part, un champ facultatif vide est absent (donc effacé)', () {
      final m = monter();
      expect(m.profil, isNull);
      m.definirProfil(nom: ' Abdelilah Chraibi ', cin: 'bk123456', vehicule: 'Fourgonnette', villes: ['CASABLANCA'], categories: ['EPICERIE']);
      m.definirProfil(nom: 'Abdelilah Chraibi', cin: '', vehicule: '  ', villes: ['CASABLANCA', 'CASABLANCA']);
      final ops = m.s.enAttente();
      expect(ops.map((o) => o['type']), ['PROFIL_AMBULANT', 'PROFIL_AMBULANT']);
      expect(ops.first['donnees'], {
        'nom': 'Abdelilah Chraibi', 'cin': 'BK123456', 'vehicule': 'Fourgonnette', 'villes': ['CASABLANCA'], 'categories': ['EPICERIE'],
      });
      expect(ops.last['donnees'], {'nom': 'Abdelilah Chraibi', 'villes': ['CASABLANCA'], 'categories': []});
      expect(m.profil?['categories'], isEmpty, reason: 'visible tout de suite, sans réseau');
      expect(m.s.objets('PROFIL'), hasLength(1));
      expect(m.emettre(hanoutId: hanoutId, lignes: [])['ambulant']['nom'], 'Abdelilah Chraibi', reason: 'le nom du profil sur les pièces');
    });

    test('nom vide ou CIN hors motif du contrat : rien n\'est enfilé', () {
      final m = monter();
      expect(() => m.definirProfil(nom: '  '), throwsArgumentError);
      expect(() => m.definirProfil(nom: 'Said', cin: 'B-12'), throwsArgumentError);
      expect(m.s.nombreEnAttente, 0);
    });

    test('l\'entité PROFIL du serveur remplace la copie locale', () async {
      final m = monter();
      m.definirProfil(nom: 'Said', categories: ['BOISSONS']);
      await sync(m.s, changements: [
        {'entite': 'PROFIL', 'id': moi, 'action': 'UPSERT', 'donnees': {
          'nom': 'Said Alaoui', 'villes': ['RABAT'], 'categories': ['HYGIENE'], 'modifieLe': '2026-11-03T10:00:00Z'}},
      ]).synchroniser();
      expect(m.s.objets('PROFIL'), hasLength(1));
      expect(m.profil?['categories'], ['HYGIENE']);
      expect(m.nom, 'Said Alaoui');
    });

    test('profil plus récent ailleurs (EN_CONFLIT) : l\'appareil reprend la version du serveur', () async {
      final m = monter();
      m.definirProfil(nom: 'Ancien nom', vehicule: 'Triporteur', categories: ['EPICERIE']);
      const serveur = {'nom': 'Nom saisi ailleurs', 'villes': ['CASABLANCA'], 'categories': ['BOISSONS'], 'modifieLe': '2026-11-03T12:00:00Z'};
      await sync(m.s, resultat: (o) => {'statut': 'EN_CONFLIT', 'conflit': {
        'code': 'MODIFIE_AILLEURS', 'resolution': 'VERSION_SERVEUR_CONSERVEE', 'versionServeur': serveur}}).synchroniser();
      expect(m.s.objets('PROFIL'), [serveur]);
      expect(m.s.operationsSignalees().single['type'], 'PROFIL_AMBULANT', reason: 'affiché dans l\'écran de synchro');
    });
  });

  group('profil refusé par le serveur (REJETEE)', () {
    Future<void> refuser(Stockage s) => sync(s, resultat: (o) => o['type'] != 'PROFIL_AMBULANT' ? {} : {'statut': 'REJETEE',
      'erreur': {'type': 'https://daba.ma/problemes/regle-de-gestion', 'status': 422, 'detail': 'Codes inconnus du référentiel : [ATLANTIDE]'}}).pousser();

    test('le dernier profil du serveur revient, son nom reste sur les pièces', () async {
      final m = monter();
      final serveur = {'nom': 'Said Alaoui', 'villes': ['CASABLANCA'], 'categories': ['BOISSONS'], 'modifieLe': '2026-11-03T10:00:00Z'};
      Synchronisation(m.s, Api('http://serveur'), appareilId: appareil, prefixe: 'K7Q2')
          .appliquer({'entite': 'PROFIL', 'id': moi, 'action': 'UPSERT', 'donnees': serveur});
      m.definirProfil(nom: 'Nom refusé', villes: ['ATLANTIDE']);
      expect(m.nom, 'Nom refusé');
      await refuser(m.s);
      expect(m.s.objets('PROFIL'), [serveur]);
      expect(m.emettre(hanoutId: hanoutId, lignes: [])['ambulant']['nom'], 'Said Alaoui');
      expect(m.s.operationsSignalees().single, containsPair('statut', 'REJETEE'), reason: "affiché dans l'écran de synchro");
    });

    test('consentement-requis (contrat 0.5.1) : pas de retour arrière, le profil attend le consentement', () async {
      final m = monter();
      m.definirProfil(nom: 'Nom en attente', categories: ['EPICERIE']);
      await sync(m.s, resultat: (o) => {'statut': 'REJETEE',
        'erreur': {'type': 'https://daba.ma/problemes/consentement-requis', 'status': 403}}).pousser();
      expect(m.nom, 'Nom en attente');
      expect(m.s.operationsSignalees(), isEmpty);
      expect(m.s.nombreEnAttente, 1);
    });

    test('sans profil serveur : la copie locale disparaît, le nom de la session revient', () async {
      final m = monter();
      m.definirProfil(nom: 'Nom refusé', categories: ['INCONNUE']);
      await refuser(m.s);
      expect(m.profil, isNull);
      expect(m.nom, 'Said');
    });
  });

  test('mentions légales saisies : la facture devient possible (RG-F2)', () {
    final m = monter(parametres: {'politiqueDepassementCredit': 'BLOQUER', 'seuilEcartInventairePourcent': 5});
    expect(m.peutFacturer, isFalse);
    const mentions = {'ice': '001234567000089', 'identifiantFiscal': '1', 'registreCommerce': '2', 'assujettiTva': false};
    m.definirMentions(mentions);
    expect(m.s.enAttente().single['donnees'], {'mentionsLegales': mentions}, reason: 'la politique, absente, est gardée par le serveur');
    expect(m.parametres['politiqueDepassementCredit'], 'BLOQUER');
    expect(m.emettre(hanoutId: hanoutId, lignes: [], type: 'FACTURE')['mentionsLegales'], mentions);
  });

  testWidgets('écran profil en arabe : libellés de catégorie et de ville localisés, enregistrement hors ligne', (tester) async {
    final s = Stockage.memoire()
      ..ecrire('session', jsonEncode({'utilisateur': {'id': moi, 'nom': ''}, 'prefixeNumerotation': 'K7Q2'}))
      ..ecrire('langue', 'ar')
      ..ecrire('consentement', jsonEncode({'versionTexte': versionTexteCndp, 'traitementDonnees': true}))
      ..enregistrer('PROFIL', moi, {'nom': 'Said', 'villes': ['CASABLANCA'], 'categories': ['EPICERIE'], 'modifieLe': '2026-11-03T10:00:00Z'});
    final referentiels = {
      '/v1/categories': [{'code': 'EPICERIE', 'libelle': {'fr': 'Épicerie', 'ar': 'مواد غذائية'}}, {'code': 'HYGIENE', 'libelle': {'fr': 'HYGIENE'}}],
      '/v1/regions': [{'code': 'CASABLANCA_SETTAT', 'nom': {'fr': 'Casablanca-Settat', 'ar': 'الدار البيضاء سطات'},
        'villes': [{'code': 'CASABLANCA', 'nom': {'fr': 'Casablanca', 'ar': 'الدار البيضاء'}}]}],
    };
    final api = Api('http://serveur', client: MockClient((req) async => referentiels[req.url.path] == null
        ? throw const SocketException('synchro hors ligne')
        : http.Response.bytes(utf8.encode(jsonEncode(referentiels[req.url.path])), 200)));
    final daba = Daba(s, api, const FlutterSecureStorage());
    tester.view..physicalSize = const Size(800, 3000)..devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(DabaScope(daba: daba, child: MaterialApp(
      locale: daba.locale, supportedLocales: languesDaba, localizationsDelegates: delegationsDaba, theme: themeDaba(daba.locale, Brightness.light), home: const EcranProfil())));
    await tester.pumpAndSettle();
    expect(find.text('مواد غذائية'), findsOneWidget);
    expect(find.text('HYGIENE'), findsOneWidget, reason: 'sans libellé arabe : repli sur fr, qui vaut le code');
    expect(find.text('الدار البيضاء'), findsOneWidget, reason: 'région dépliée : une de ses villes est choisie');
    await tester.tap(find.text('مواد غذائية'));
    await tester.pump();
    await tester.ensureVisible(find.byType(FilledButton));
    await tester.tap(find.byType(FilledButton));
    await tester.pumpAndSettle();
    expect(s.enAttente().single['donnees'], {'nom': 'Said', 'villes': ['CASABLANCA'], 'categories': []});
  });

  testWidgets("ancien texte CNDP : profil consultable, enregistrement bloqué jusqu'au nouveau consentement", (tester) async {
    final s = Stockage.memoire()
      ..ecrire('session', jsonEncode({'utilisateur': {'id': moi, 'nom': 'Said'}, 'prefixeNumerotation': 'K7Q2'}))
      ..ecrire('consentement', jsonEncode({'versionTexte': '2026-11-v1', 'traitementDonnees': true}));
    final daba = Daba(s, Api('http://serveur', client: MockClient((_) async => throw const SocketException('hors ligne'))),
        const FlutterSecureStorage());
    tester.view..physicalSize = const Size(800, 3000)..devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(DabaScope(daba: daba, child: MaterialApp(
      supportedLocales: languesDaba, localizationsDelegates: delegationsDaba, theme: themeDaba(daba.locale, Brightness.light), home: const EcranProfil())));
    await tester.pumpAndSettle();
    expect(find.textContaining('nouveau texte'), findsOneWidget);
    expect(tester.widget<FilledButton>(find.byType(FilledButton)).onPressed, isNull);
    expect(s.nombreEnAttente, 0);
  });

  group('consentement sur un ancien texte (§10 hors ligne)', () {
    setUp(() => FlutterSecureStorage.setMockInitialValues({'jeton': 'j', 'pin': '1234'}));
    Daba ouvrir(Map<String, dynamic>? consentement) {
      TestWidgetsFlutterBinding.ensureInitialized();
      final s = Stockage.memoire()
        ..ecrire('session', jsonEncode({'utilisateur': {'id': moi, 'nom': 'Said'}, 'prefixeNumerotation': 'K7Q2'}))
        ..ecrire('consentement', consentement == null ? null : jsonEncode(consentement));
      return Daba(s, Api('http://serveur'), const FlutterSecureStorage());
    }

    test("v1 accepté : l'app s'ouvre (PIN) sans réseau, le nouveau texte reste à accepter", () async {
      final daba = ouvrir({'versionTexte': '2026-11-v1', 'traitementDonnees': true});
      await daba.chargerSecrets();
      expect(daba.etape, Etape.verrouille);
      expect(daba.consentementAJour, isFalse);
    });

    test("aucun consentement : l'écran CNDP bloque comme avant", () async {
      final daba = ouvrir(null);
      await daba.chargerSecrets();
      expect(daba.etape, Etape.consentement);
    });
  });

  group('promos ciblées (§9)', () {
    test('SUPPRESSION d\'une PROMO : l\'offre quitte le fil local', () async {
      final m = monter();
      const offre = '88888888-8888-4888-8888-888888888888';
      m.s.enregistrer('PROMO', offre, {'id': offre, 'campagneId': 'c', 'produit': {'id': coca}});
      await sync(m.s, changements: [{'entite': 'PROMO', 'id': offre, 'action': 'SUPPRESSION'}]).synchroniser();
      expect(m.s.objets('PROMO'), isEmpty);
    });

    test('réservation d\'une offre qui ne vise plus l\'ambulant (404) : message clair, offre retirée', () async {
      final s = Stockage.memoire();
      const offre = '88888888-8888-4888-8888-888888888888';
      s.enregistrer('PROMO', offre, {'id': offre});
      final api = Api('http://serveur', client: MockClient((req) async => http.Response(
          jsonEncode({'type': 'https://daba.ma/problemes/introuvable', 'status': 404}), 404)));
      final daba = Daba(s, api, const FlutterSecureStorage());
      await expectLater(daba.reserver(offre, 1), throwsA(isA<OffreIndisponible>()));
      expect(s.objets('PROMO'), isEmpty);
    });
  });
}

import 'dart:async';
import 'dart:convert';

import 'package:daba_shared/daba_shared.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:geolocator/geolocator.dart';
import 'package:path_provider/path_provider.dart';

import 'domaine.dart';

enum Etape { connexion, consentement, pinCreation, verrouille, pret }

/// Version du texte d'information CNDP affiché (journalisée avec le consentement).
const versionTexteCndp = '2026-11-v1';

/// État de l'app : session, synchronisation, disponibilité (dépannage).
class Daba extends ChangeNotifier with WidgetsBindingObserver {
  Daba(this.stockage, this.api, this._coffre) {
    final session = stockage.lire('session');
    if (session != null) _ouvrir(jsonDecode(session) as Map<String, dynamic>);
    locale = localeDuCode(stockage.lire('langue'));
    api
      ..langue = codeLangue(locale)
      ..rafraichir = _rafraichir
      ..surRevocation = () => deconnecter(effacer: true);
  }

  /// Mock Prism du contrat : `Prefer: example=ambulant` choisit la Session d'exemple de l'ambulant.
  static const _mock = bool.fromEnvironment('DABA_MOCK');

  static Future<Daba> demarrer() async {
    final dossier = await getApplicationSupportDirectory();
    final daba = Daba(
      Stockage.ouvrir('${dossier.path}/daba.db'),
      Api(const String.fromEnvironment('DABA_API', defaultValue: 'http://10.0.2.2:4010')),
      const FlutterSecureStorage(),
    );
    await daba._chargerSecrets();
    return daba;
  }

  final Stockage stockage;
  final Api api;
  final FlutterSecureStorage _coffre;

  Etape etape = Etape.connexion;
  late Locale locale;
  Ambulant? metier;
  Synchronisation? _sync;
  Timer? _minuteurSync;

  bool horsLigne = false;
  bool reconnexionRequise = false;
  DateTime? derniereSync;

  Ambulant get m => metier!;
  int get enAttente => stockage.nombreEnAttente;

  /// Identifiant stable de l'appareil (RG-S2), généré une fois sur le téléphone.
  String get appareilId {
    final id = stockage.lire('appareilId') ?? Stockage.nouvelId();
    stockage.ecrire('appareilId', id);
    return id;
  }

  void choisirLangue(Locale l) {
    locale = l;
    api.langue = codeLangue(l);
    stockage.ecrire('langue', codeLangue(l));
    notifyListeners();
  }

  // ------------------------------------------------------------------ session
  Future<void> _chargerSecrets() async {
    api.jeton = await _coffre.read(key: 'jeton');
    if (metier != null && api.jeton != null) {
      etape = stockage.lire('consentement') == null
          ? Etape.consentement
          : (await _coffre.read(key: 'pin') == null ? Etape.pinCreation : Etape.verrouille);
    } else {
      etape = Etape.connexion;
    }
    WidgetsBinding.instance.addObserver(this);
    notifyListeners();
  }

  void _ouvrir(Map<String, dynamic> session) {
    final u = session['utilisateur'] as Map<String, dynamic>;
    metier = Ambulant(stockage,
        appareilId: appareilId,
        prefixe: session['prefixeNumerotation'] as String,
        utilisateurId: u['id'] as String,
        nom: (u['nom'] ?? '') as String);
    _sync = Synchronisation(stockage, api, appareilId: appareilId, prefixe: session['prefixeNumerotation'] as String);
  }

  /// Ambulant de la session enregistrée sur ce téléphone.
  Map<String, dynamic>? get _utilisateurLocal =>
      (jsonDecode(stockage.lire('session') ?? 'null') as Map<String, dynamic>?)?['utilisateur'] as Map<String, dynamic>?;

  Future<int> demanderOtp(String telephone) async {
    // Autre numéro avec des pièces pas encore remontées : refuser avant l'OTP, dont la session
    // invaliderait celle de l'ambulant précédent (il ne pourrait plus pousser sa file sans OTP).
    final local = _utilisateurLocal;
    if (local != null && local['telephone'] != telephone && stockage.nombreEnAttente > 0) {
      throw const AutreAmbulantEnAttente();
    }
    final r = await api.post('/v1/auth/otp', {'telephone': telephone, 'langue': codeLangue(locale)});
    return (r as Map)['expireDans'] as int;
  }

  /// Vérifie l'OTP. Une reconnexion (jeton expiré) garde l'appareil et ses données locales.
  Future<void> verifierOtp(String telephone, String code) async {
    final session = await api.post('/v1/auth/otp/verify', {'telephone': telephone, 'code': code, 'appareilId': appareilId},
        {if (_mock) 'Prefer': 'example=ambulant'}) as Map<String, dynamic>;
    final autreAmbulant = _utilisateurLocal != null && _utilisateurLocal!['id'] != session['utilisateur']['id'];
    Never refuser(Exception e) {
      // La session ouverte par cet OTP ne servira pas : la fermer côté serveur.
      api.post('/v1/auth/deconnexion', null, {'Authorization': 'Bearer ${session['accessToken']}'}).ignore();
      throw e;
    }

    if (!(session['utilisateur']['roles'] as List).contains('AMBULANT') || session['prefixeNumerotation'] == null) {
      refuser(ProblemeApi(403, {'type': 'https://daba.ma/problemes/role-non-autorise', 'status': 403}));
    }
    if (autreAmbulant) {
      // Les pièces de l'ambulant précédent doivent d'abord remonter (RG-C4).
      if (stockage.nombreEnAttente > 0) refuser(const AutreAmbulantEnAttente());
      final id = appareilId; // préfixe par couple (ambulant, appareil), RG-F1
      stockage.effacerTout();
      stockage.ecrire('appareilId', id);
    }
    await _enregistrerJetons(session);
    _ouvrir(session);
    if (stockage.lire('consentement') == null) {
      try {
        final c = await api.get('/v1/moi/consentement') as Map<String, dynamic>;
        if (c['traitementDonnees'] == true) stockage.ecrire('consentement', jsonEncode(c));
      } on ProblemeApi catch (_) {
        // 404 : aucun consentement enregistré
      }
    }
    etape = stockage.lire('consentement') == null
        ? Etape.consentement
        : (await _coffre.read(key: 'pin') == null ? Etape.pinCreation : Etape.pret);
    if (etape == Etape.pret) _demarrerSync();
    notifyListeners();
  }

  /// Chaque connexion OTP ouvre une nouvelle session : seuls les jetons de la dernière servent.
  /// Le moteur de synchro n'est pas recréé : un renouvellement arrive en pleine synchro, qui doit
  /// rester la seule en cours (sinon des deltas locaux seraient oubliés trop tôt).
  Future<void> _enregistrerJetons(Map<String, dynamic> session) async {
    await _coffre.write(key: 'jeton', value: session['accessToken'] as String);
    await _coffre.write(key: 'refresh', value: session['refreshToken'] as String);
    api.jeton = session['accessToken'] as String;
    stockage.ecrire('session', jsonEncode({...session}..remove('accessToken')..remove('refreshToken')));
    reconnexionRequise = false;
  }

  /// Jeton expiré (24 h) : renouvelé sans OTP tant que le refreshToken (90 jours) est valable.
  Future<bool> _rafraichir() async {
    final refresh = await _coffre.read(key: 'refresh');
    if (refresh == null) return false;
    try {
      await _enregistrerJetons(await api.post('/v1/auth/refresh', {'refreshToken': refresh}) as Map<String, dynamic>);
      return true;
    } on ProblemeApi {
      // Session fermée ou remplacée par une autre connexion : il faut repasser par l'OTP.
      reconnexionRequise = true;
      notifyListeners();
      return false;
    }
  }

  bool get consentGeolocalisation =>
      (jsonDecode(stockage.lire('consentement') ?? '{}') as Map)['geolocalisation'] == true;

  /// Consentement CNDP (loi 09-08), exigé par le serveur avant toute opération.
  Future<void> consentir({required bool geolocalisation}) async {
    final c = await api.put('/v1/moi/consentement', {
      'versionTexte': versionTexteCndp,
      'traitementDonnees': true,
      'geolocalisation': geolocalisation,
      'langue': codeLangue(locale),
    });
    stockage.ecrire('consentement', jsonEncode(c));
    if (!geolocalisation && disponible) await changerDisponibilite(false);
    if (etape == Etape.consentement) {
      etape = await _coffre.read(key: 'pin') == null ? Etape.pinCreation : Etape.pret;
      if (etape == Etape.pret) _demarrerSync();
    }
    notifyListeners();
  }

  // ------------------------------------------------------------------ PIN local (§10)
  int _essais = 0;

  Future<void> definirPin(String pin) async {
    await _coffre.write(key: 'pin', value: pin);
    etape = Etape.pret;
    _demarrerSync();
    notifyListeners();
  }

  /// Cinq erreurs ferment la session : il faut repasser par l'OTP.
  Future<bool> deverrouiller(String pin) async {
    if (pin == await _coffre.read(key: 'pin')) {
      _essais = 0;
      etape = Etape.pret;
      _demarrerSync();
      notifyListeners();
      return true;
    }
    if (++_essais >= 5) await deconnecter();
    return false;
  }

  DateTime? _misEnFond;

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.paused) _misEnFond = DateTime.now();
    if (state == AppLifecycleState.resumed && etape == Etape.pret && _misEnFond != null &&
        DateTime.now().difference(_misEnFond!) > const Duration(minutes: 2)) {
      etape = Etape.verrouille;
      notifyListeners();
    }
  }

  /// Ferme la session : pousse d'abord la file, puis ferme la session côté serveur. Sans réseau,
  /// les opérations restent sur l'appareil. `effacer` : appareil déconnecté à distance (§10),
  /// aucun appel au serveur, tout est effacé.
  Future<void> deconnecter({bool effacer = false}) async {
    _minuteurSync?.cancel();
    _minuteurDispo?.cancel();
    if (!effacer) {
      try {
        if (disponible) await changerDisponibilite(false);
        await _sync?.pousser();
        await api.post('/v1/auth/deconnexion');
      } catch (_) {
        // Hors ligne : la session expirera d'elle-même, la file attend sur le téléphone.
      }
    }
    disponible = false;
    position = null;
    await _coffre.delete(key: 'jeton');
    await _coffre.delete(key: 'refresh');
    await _coffre.delete(key: 'pin');
    api.jeton = null;
    if (effacer) stockage.effacerTout();
    _essais = 0;
    etape = Etape.connexion;
    notifyListeners();
  }

  // ------------------------------------------------------------------ synchronisation
  void _demarrerSync() {
    // L'app démarre indisponible : le serveur doit l'être aussi (et oublier la dernière position, CNDP).
    if (!disponible) stockage.ecrire('couperDisponibilite', '1');
    _minuteurSync?.cancel();
    _minuteurSync = Timer.periodic(const Duration(minutes: 1), (_) => synchroniser());
    synchroniser();
  }

  /// Sans réseau, ne fait rien de plus que noter l'état : la file attend le prochain essai.
  Future<void> synchroniser() async {
    final sync = _sync;
    if (sync == null || reconnexionRequise) return;
    try {
      await sync.synchroniser();
      horsLigne = false;
      derniereSync = DateTime.now();
      if (!disponible && stockage.lire('couperDisponibilite') != null) {
        await api.put('/v1/ambulant/disponibilite', {'disponible': false});
        stockage.ecrire('couperDisponibilite', null);
      }
    } on HorsLigne {
      horsLigne = true;
    } on ProblemeApi catch (e) {
      if (e.code == 'non-authentifie') reconnexionRequise = true;
      if (e.consentementRequis) {
        stockage.ecrire('consentement', null);
        etape = Etape.consentement;
      }
    }
    notifyListeners();
  }

  /// Après un geste local : rafraîchit l'écran et tente de remonter tout de suite.
  void apresGeste() {
    notifyListeners();
    synchroniser();
  }

  // ------------------------------------------------------------------ dépannage (en ligne, §3.7)
  bool disponible = false;
  Position? position;
  List<Map<String, dynamic>> demandes = [];
  Timer? _minuteurDispo;
  final _nouvelles = StreamController<Map<String, dynamic>>.broadcast();

  /// Nouvelle demande proposée (alerte).
  Stream<Map<String, dynamic>> get nouvellesDemandes => _nouvelles.stream;

  /// Active ou coupe la disponibilité. La position n'est lue et envoyée que si l'ambulant
  /// est disponible et a consenti à la géolocalisation (CNDP).
  Future<void> changerDisponibilite(bool actif) async {
    if (actif) {
      position = await _lirePosition();
      await api.put('/v1/ambulant/disponibilite',
          {'disponible': true, 'position': {'lat': position!.latitude, 'lon': position!.longitude}});
      disponible = true;
      stockage.ecrire('couperDisponibilite', null);
      _minuteurDispo?.cancel();
      var tour = 0;
      _minuteurDispo = Timer.periodic(const Duration(seconds: 30), (_) async {
        if (++tour % 4 == 0) await _envoyerPosition().catchError((_) {});
        await chargerDemandes().catchError((_) {});
      });
      await chargerDemandes();
    } else {
      _minuteurDispo?.cancel();
      disponible = false;
      position = null;
      // Sans réseau, la coupure côté serveur sera renvoyée à la prochaine synchronisation.
      stockage.ecrire('couperDisponibilite', '1');
      notifyListeners();
      await api.put('/v1/ambulant/disponibilite', {'disponible': false});
      stockage.ecrire('couperDisponibilite', null);
    }
    notifyListeners();
  }

  Future<Position> _lirePosition() async {
    var permission = await Geolocator.checkPermission();
    if (permission == LocationPermission.denied) permission = await Geolocator.requestPermission();
    if (permission == LocationPermission.denied || permission == LocationPermission.deniedForever) {
      throw const PermissionRefusee();
    }
    return Geolocator.getCurrentPosition(locationSettings: const LocationSettings(accuracy: LocationAccuracy.medium));
  }

  Future<void> _envoyerPosition() async {
    if (!disponible) return;
    final lue = await _lirePosition();
    if (!disponible) return; // coupé pendant la lecture : rien ne doit partir (CNDP)
    position = lue;
    await api.put('/v1/ambulant/disponibilite',
        {'disponible': true, 'position': {'lat': position!.latitude, 'lon': position!.longitude}});
  }

  Future<void> chargerDemandes() async {
    final page = await api.get('/v1/demandes-depannage') as Map<String, dynamic>;
    final connues = {for (final d in demandes) d['id']};
    demandes = (page['contenu'] as List).cast<Map<String, dynamic>>();
    for (final d in demandes) {
      // Le hanout de la demande doit exister localement pour y rattacher la vente (RG-D4).
      final h = d['hanout'] as Map<String, dynamic>?;
      if (h != null && stockage.objet('HANOUT', h['id'] as String) == null) {
        stockage.enregistrer('HANOUT', h['id'] as String, {'id': h['id'], 'nom': h['nom'], 'position': h['position']});
      }
      if (d['statut'] == 'EN_ATTENTE' && !connues.contains(d['id'])) _nouvelles.add(d);
    }
    notifyListeners();
  }

  Future<void> agirDemande(String id, String action) async {
    if (action == 'refus') {
      await api.post('/v1/demandes-depannage/$id/refus');
      demandes.removeWhere((d) => d['id'] == id);
    } else {
      final d = await api.post('/v1/demandes-depannage/$id/$action') as Map<String, dynamic>;
      demandes = [for (final x in demandes) x['id'] == id ? d : x];
    }
    notifyListeners();
  }

  // ------------------------------------------------------------------ promos (réservation en ligne, §3.6)
  /// L'`id` est créé avant l'envoi : un nouvel essai après coupure ne crée pas de seconde réservation.
  Future<Map<String, dynamic>> reserver(String offreId, int quantite, {String? id}) async {
    final r = await api.post('/v1/reservations', {'id': id ?? Stockage.nouvelId(), 'offreId': offreId, 'quantite': quantite})
        as Map<String, dynamic>;
    stockage.enregistrer('RESERVATION', r['id'] as String, r);
    notifyListeners();
    return r;
  }

  @override
  void dispose() {
    _minuteurSync?.cancel();
    _minuteurDispo?.cancel();
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }
}

class PermissionRefusee implements Exception {
  const PermissionRefusee();
}

class AutreAmbulantEnAttente implements Exception {
  const AutreAmbulantEnAttente();
}

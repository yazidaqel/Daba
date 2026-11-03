import 'dart:async';
import 'dart:convert';

import 'package:daba_shared/daba_shared.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:geolocator/geolocator.dart';
import 'package:path_provider/path_provider.dart';

import 'domaine.dart';
import 'push.dart';

enum Etape { connexion, consentement, pinCreation, verrouille, pret }

/// Version du texte d'information CNDP affiché (journalisée avec le consentement). v2 : le nom de
/// l'ambulant est montré à ses grossistes et à ses hanouts (contrat v0.5.0). v3 : sous-traitants hors
/// du Maroc, Google Firebase Cloud Messaging (notifications) et Infobip (SMS, codes OTP compris).
const versionTexteCndp = '2026-11-v3';

/// Textes de l'app qui montrent le nom de l'ambulant : le profil en dépend (v2 et suivants). Une
/// version inconnue (posée par un autre client) ne compte pas : liste, pas comparaison de chaînes.
const textesAvecNom = {'2026-11-v2', versionTexteCndp};

/// État de l'app : session, synchronisation, disponibilité (dépannage).
class Daba extends ChangeNotifier with WidgetsBindingObserver {
  Daba(this.stockage, this.api, this._coffre, [Push? push]) : push = push ?? Push() {
    declarationPush = DeclarationPush(api, this.push.jeton);
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
    await daba.chargerSecrets();
    unawaited(daba.rejouerDeconnexion());
    return daba;
  }

  final Stockage stockage;
  final Api api;
  final FlutterSecureStorage _coffre;
  final Push push;
  late final DeclarationPush declarationPush;

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
    push.nommerCanaux(lookupT(l)); // le jeton est redéclaré avec la langue à la prochaine synchro
    notifyListeners();
  }

  // ------------------------------------------------------------------ session
  /// Étape d'ouverture d'après la session et les secrets gardés sur le téléphone (au démarrage).
  Future<void> chargerSecrets() async {
    api.jeton = await _coffre.read(key: 'jeton');
    if (metier != null && api.jeton != null) {
      etape = !_consentementDonne
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
    declarationPush.nouvelleSession(); // nouvelle session : jeton push redéclaré à la première synchro
    // La nouvelle session remplace l'ancienne sur cet appareil, et son jeton push sera redéclaré.
    await _coffre.delete(key: 'sessionAFermer');
    stockage.ecrire('push.aOublier', null);
    _ouvrir(session);
    if (!consentementAJour) {
      try {
        final c = await api.get('/v1/moi/consentement') as Map<String, dynamic>;
        if (c['traitementDonnees'] == true) _consentementActif(c);
      } catch (_) {
        // 404 : aucun consentement enregistré ; coupure : on garde la copie locale
      }
    }
    etape = !_consentementDonne
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

  Map get _consentement => jsonDecode(stockage.lire('consentement') ?? '{}') as Map;

  /// Un consentement, même sur un ancien texte : l'app reste utilisable, hors ligne compris (§10).
  /// Sans aucun consentement, l'écran CNDP bloque comme avant.
  bool get _consentementDonne => _consentement['traitementDonnees'] == true;

  /// Consentement donné sur le texte actuel. Un ancien texte ne bloque que ce qui dépend du nouveau :
  /// le profil (v2, nom montré aux grossistes et aux hanouts) et le push (v3, FCM). Le nouveau texte
  /// est proposé sans bloquer quand le réseau est là.
  bool get consentementAJour => _consentementDonne && _consentement['versionTexte'] == versionTexteCndp;

  /// Consentement sur un texte qui montre le nom (v2 ou plus récent) : profil modifiable.
  bool get consentementProfil => _consentementDonne && textesAvecNom.contains(_consentement['versionTexte']);

  /// Enregistre un consentement actif et relance les opérations qui l'attendaient.
  void _consentementActif(Map<String, dynamic> c) {
    stockage.ecrire('consentement', jsonEncode(c));
    stockage.reprendreApresConsentement();
  }

  bool get consentGeolocalisation => _consentement['geolocalisation'] == true;

  /// Consentement CNDP (loi 09-08), exigé par le serveur avant toute opération.
  Future<void> consentir({required bool geolocalisation}) async {
    final c = await api.put('/v1/moi/consentement', {
      'versionTexte': versionTexteCndp,
      'traitementDonnees': true,
      'geolocalisation': geolocalisation,
      'langue': codeLangue(locale),
    });
    _consentementActif(c as Map<String, dynamic>);
    if (!geolocalisation && disponible) await changerDisponibilite(false);
    if (etape == Etape.consentement) {
      etape = await _coffre.read(key: 'pin') == null ? Etape.pinCreation : Etape.pret;
      if (etape == Etape.pret) _demarrerSync();
    } else if (etape == Etape.pret) {
      synchroniser(); // les opérations qui attendaient ce consentement repartent tout de suite
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
    if (state != AppLifecycleState.resumed) return;
    if (etape == Etape.pret && _misEnFond != null && DateTime.now().difference(_misEnFond!) > const Duration(minutes: 2)) {
      etape = Etape.verrouille;
      _fermerEcrans();
    }
    notifyListeners(); // une notification touchée en fond s'ouvre maintenant, ou après le PIN
  }

  /// Navigateur de l'app : verrouillage et déconnexion ferment tous les écrans ouverts au-dessus de
  /// l'accueil, pour qu'aucun ne reste par-dessus le PIN ou la connexion.
  final navigateur = GlobalKey<NavigatorState>();

  void _fermerEcrans() => navigateur.currentState?.popUntil((r) => r.isFirst);

  /// Ferme la session : pousse d'abord la file, puis ferme la session côté serveur. Sans réseau,
  /// les opérations restent sur l'appareil. `effacer` : appareil déconnecté à distance (§10),
  /// aucun appel au serveur, tout est effacé.
  Future<void> deconnecter({bool effacer = false}) async {
    _minuteurSync?.cancel();
    _minuteurDispo?.cancel();
    if (!effacer) {
      var joignable = true;
      try {
        if (disponible) await changerDisponibilite(false);
        await _sync?.pousser();
      } on HorsLigne {
        joignable = false; // la file attend sur le téléphone
      } catch (_) {
        // refus du serveur : la file attend aussi, la session se ferme quand même
      }
      try {
        if (joignable) await api.post('/v1/auth/deconnexion');
      } on HorsLigne {
        joignable = false;
      } on ProblemeApi {
        // 401 : session déjà fermée ou remplacée côté serveur
      }
      // Hors ligne, le serveur garderait la session et son jeton push : fermeture rejouée (§10).
      final jeton = api.jeton;
      if (!joignable && jeton != null) await _coffre.write(key: 'sessionAFermer', value: jeton);
    }
    disponible = false;
    position = null;
    await _coffre.delete(key: 'jeton');
    await _coffre.delete(key: 'refresh');
    await _coffre.delete(key: 'pin');
    api.jeton = null;
    declarationPush.oublier();
    cibleEnAttente = null;
    if (effacer) stockage.effacerTout();
    stockage.ecrire('push.aOublier', '1');
    unawaited(rejouerDeconnexion()); // appels réseau : ne retardent pas la déconnexion hors ligne
    _essais = 0;
    etape = Etape.connexion;
    _fermerEcrans();
    notifyListeners();
  }

  Timer? _minuteurDeconnexion;
  Future<void>? _rejeu;

  /// Déconnexion faite hors ligne (§10) : fermeture de la session (`sessionAFermer`) et invalidation
  /// du jeton push chez FCM (`push.aOublier`), rejouées au démarrage puis chaque minute jusqu'à
  /// réussite. Une nouvelle connexion OTP les rend inutiles.
  Future<void> rejouerDeconnexion() => _rejeu ??= _rejouer().whenComplete(() => _rejeu = null);

  Future<void> _rejouer() async {
    // ponytail: rejoue avec le jeton d'accès de la session (24 h) ; au-delà le serveur répond 401 et la
    // session reste ouverte jusqu'à son refreshToken, mais le jeton push est déjà invalidé chez FCM.
    final session = await _coffre.read(key: 'sessionAFermer');
    if (session != null) {
      try {
        await api.post('/v1/auth/deconnexion', null, {'Authorization': 'Bearer $session'});
        await _coffre.delete(key: 'sessionAFermer');
      } on ProblemeApi {
        await _coffre.delete(key: 'sessionAFermer'); // 401 : déjà fermée, expirée ou remplacée
      } on HorsLigne {
        // réessayé dans une minute
      }
    }
    if (stockage.lire('push.aOublier') != null && await push.oublierJeton()) stockage.ecrire('push.aOublier', null);
    final reste = await _coffre.read(key: 'sessionAFermer') != null || stockage.lire('push.aOublier') != null;
    _minuteurDeconnexion?.cancel();
    _minuteurDeconnexion = reste ? Timer(const Duration(minutes: 1), rejouerDeconnexion) : null;
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
    if (_pushAutorise) await _activerPush();
    try {
      await sync.synchroniser();
      if (!_referentielsCharges) await chargerReferentiels().catchError((_) {}); // réessayé au prochain tour
      if (_pushAutorise) await declarationPush.declarer(appareilId).catchError((_) {}); // idem, rien en file
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

  bool _referentielsCharges = false;

  /// Régions et catégories (`GET /v1/regions`, `/v1/categories`), gardées sur le téléphone pour
  /// saisir le profil hors ligne ; relues une fois par lancement.
  Future<void> chargerReferentiels() async {
    for (final nom in ['regions', 'categories']) {
      stockage.ecrire('referentiel.$nom', jsonEncode(await api.get('/v1/$nom')));
    }
    _referentielsCharges = true;
  }

  List<Map<String, dynamic>> referentiel(String nom) =>
      (jsonDecode(stockage.lire('referentiel.$nom') ?? '[]') as List).cast<Map<String, dynamic>>();

  /// Après un geste local : rafraîchit l'écran et tente de remonter tout de suite.
  void apresGeste() {
    notifyListeners();
    synchroniser();
  }

  // ------------------------------------------------------------------ push (§9)
  final _avisPush = StreamController<({Cible? cible, String texte})>.broadcast();

  /// Notification reçue app ouverte : bandeau dans l'app au lieu d'une notification système.
  Stream<({Cible? cible, String texte})> get avisPush => _avisPush.stream;

  /// Écran à ouvrir après le toucher d'une notification, pris par l'accueil (après le PIN s'il le faut).
  Cible? cibleEnAttente;

  /// Push seulement session ouverte (PIN passé) et consentement au texte qui nomme FCM (v3, CNDP) :
  /// avant, aucun jeton n'est demandé à Google (initialisation automatique coupée dans le manifeste).
  bool get _pushAutorise => etape == Etape.pret && consentementAJour;

  Future<void> _activerPush() async {
    if (push.actif) return;
    await push.demarrer(
      nouveauJeton: (j) {
        declarationPush.jeton = j;
        if (_pushAutorise) declarationPush.declarer(appareilId).catchError((_) {});
      },
      premierPlan: (m) {
        if (etape != Etape.pret) return; // PIN ou connexion à l'écran : rien ne s'affiche ni ne part
        final cible = ciblePush(m.data);
        synchroniser();
        if (cible?.vers == Vers.depannage && disponible) {
          chargerDemandes().catchError((_) {});
          // Rupture proche : l'alerte de demande existante (son, vibration) en tient lieu.
          if (m.data['type'] == 'RUPTURE_SIGNALEE') return;
        }
        final texte = m.notification?.body ?? m.data['texte'] as String?;
        if (texte != null) _avisPush.add((cible: cible, texte: texte));
      },
      ouverte: (m) {
        if (metier == null) return; // pas de session sur ce téléphone : l'accueil n'est pas joignable
        // Gardée jusqu'au retour au premier plan et au PIN : l'accueil l'ouvre ensuite.
        cibleEnAttente = ciblePush(m.data);
        if (etape != Etape.pret) return;
        synchroniser();
        notifyListeners();
      },
    );
    if (push.actif) await push.nommerCanaux(lookupT(locale));
  }

  /// Permission des notifications, demandée une fois, à la première ouverture de l'accueil.
  void proposerNotifications() {
    if (!push.actif || !_pushAutorise || stockage.lire('push.permissionDemandee') != null) return;
    stockage.ecrire('push.permissionDemandee', '1');
    push.demanderPermission().catchError((_) {});
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
    final Map<String, dynamic> r;
    try {
      r = await api.post('/v1/reservations', {'id': id ?? Stockage.nouvelId(), 'offreId': offreId, 'quantite': quantite})
          as Map<String, dynamic>;
    } on ProblemeApi catch (e) {
      if (e.status != 404) rethrow;
      // L'offre ne vise plus l'ambulant (ou n'existe plus) : elle quitte le fil sans attendre la synchro.
      stockage.supprimer('PROMO', offreId);
      notifyListeners();
      throw const OffreIndisponible();
    }
    stockage.enregistrer('RESERVATION', r['id'] as String, r);
    notifyListeners();
    return r;
  }

  @override
  void dispose() {
    _minuteurSync?.cancel();
    _minuteurDispo?.cancel();
    _minuteurDeconnexion?.cancel();
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }
}

class PermissionRefusee implements Exception {
  const PermissionRefusee();
}

class OffreIndisponible implements Exception {
  const OffreIndisponible();
}

class AutreAmbulantEnAttente implements Exception {
  const AutreAmbulantEnAttente();
}

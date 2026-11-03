import 'package:daba_shared/daba_shared.dart';
import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

/// Écran qu'ouvre une notification.
enum Vers { depannage, offre, reservation, hanout, document, contestations }

typedef Cible = ({Vers vers, String? id});

/// Données d'un push (`type` = `Canaux.TypeMessage` du serveur, `cibleId` = demande, offre ou dépôt,
/// réservation, hanout ou document) → écran à ouvrir. Type inconnu : rien, l'app s'ouvre sur l'accueil.
Cible? ciblePush(Map<String, dynamic> donnees) {
  final vers = switch (donnees['type']) {
    'RUPTURE_SIGNALEE' || 'DEMANDE_PRISE' => Vers.depannage,
    'RESERVATION_EXPIRE_BIENTOT' => Vers.reservation,
    'RECEPTION_ECART' => Vers.document,
    'CONTESTATION' => Vers.contestations,
    // Promo confirmée, bientôt épuisée ou proche de sa fin (§9) ; plafond de crédit d'un hanout.
    final String t when t.startsWith('PROMO_') => Vers.offre,
    final String t when t.startsWith('PLAFOND') => Vers.hanout,
    _ => null,
  };
  return vers == null ? null : (vers: vers, id: donnees['cibleId'] as String?);
}

/// Déclare le jeton push au serveur (`PUT /v1/appareils/{appareilId}/push`), seulement en ligne :
/// sans réseau rien n'est mis en file, la synchronisation suivante réessaie. Redéclaré quand le jeton
/// ou la langue des textes change, et après chaque connexion OTP (nouvelle session).
class DeclarationPush {
  DeclarationPush(this.api, this.lireJeton);
  final Api api;
  final Future<String?> Function() lireJeton;

  /// Dernier jeton connu (lu une fois, puis remplacé par `onTokenRefresh`).
  String? jeton;
  String? _declare;

  /// Lève [HorsLigne] ou [ProblemeApi] : l'appelant ignore, le prochain tour réessaie.
  Future<void> declarer(String appareilId) async {
    final j = jeton ??= await lireJeton();
    if (j == null) return; // push non configuré ou jeton indisponible
    final cle = '$j|${api.langue}';
    if (cle == _declare) return;
    await api.put('/v1/appareils/$appareilId/push', {
      'jeton': j,
      'plateforme': defaultTargetPlatform == TargetPlatform.iOS ? 'IOS' : 'ANDROID',
      'langue': api.langue,
    });
    _declare = cle;
  }

  void nouvelleSession() => _declare = null;

  void oublier() {
    jeton = null;
    _declare = null;
  }
}

// Jeton d'enregistrement FCM (getToken, onTokenRefresh, deleteToken), dépréciés depuis
// firebase_messaging 17 au profit de l'identifiant d'installation (register, onRegistered) : le serveur
// envoie encore par `message.token` (FournisseurFcm). Passer à l'identifiant quand il enverra par `fid`.
// ignore_for_file: deprecated_member_use

/// Push FCM. Sans `google-services.json` (Firebase non configuré), tout est ignoré et l'app marche
/// comme sans push. Aucun texte de notification n'est gardé sur l'appareil (CNDP). L'initialisation
/// automatique de FCM est coupée (manifeste) : aucun jeton n'est créé chez Google avant [demarrer],
/// appelé seulement après le consentement au texte qui nomme FCM (CNDP, loi 09-08).
class Push {
  /// Écoute démarrée et jeton autorisé.
  bool actif = false;
  bool? _configure;
  static const _canaux = MethodChannel('daba/notifications');

  /// Firebase présent sur ce build ; journalisé une seule fois sinon.
  Future<bool> _initialiser() async {
    if (_configure != null) return _configure!;
    try {
      await Firebase.initializeApp();
      return _configure = true;
    } catch (_) {
      debugPrint('Push désactivé : Firebase non configuré (google-services.json absent).');
      return _configure = false;
    }
  }

  /// [ouverte] : notification touchée (app en fond ou fermée) ; [premierPlan] : reçue app ouverte,
  /// Android n'affiche alors rien, c'est l'app qui montre un bandeau. Sans effet après le premier appel.
  Future<void> demarrer({
    required void Function(String jeton) nouveauJeton,
    required void Function(RemoteMessage) premierPlan,
    required void Function(RemoteMessage) ouverte,
  }) async {
    if (actif || !await _initialiser()) return;
    actif = true;
    FirebaseMessaging.instance.onTokenRefresh.listen(nouveauJeton);
    FirebaseMessaging.onMessage.listen(premierPlan);
    FirebaseMessaging.onMessageOpenedApp.listen(ouverte);
    final initiale = await FirebaseMessaging.instance.getInitialMessage();
    if (initiale != null) ouverte(initiale);
  }

  /// Null sans Firebase ou avant [demarrer], ou sans réseau / services Google (réessayé à la synchro suivante).
  Future<String?> jeton() async {
    if (!actif) return null;
    try {
      return await FirebaseMessaging.instance.getToken();
    } catch (_) {
      return null;
    }
  }

  /// Android 13+ : dialogue POST_NOTIFICATIONS ; Android 8 à 12 : accordé d'office.
  Future<void> demanderPermission() async {
    if (actif) await FirebaseMessaging.instance.requestPermission();
  }

  /// Invalide le jeton chez FCM : les envois du serveur vers lui échouent ensuite (il l'efface).
  /// Faux si FCM est injoignable (hors ligne) : à rejouer. Vrai sans Firebase (rien à oublier).
  Future<bool> oublierJeton() async {
    if (!await _initialiser()) return true;
    try {
      await FirebaseMessaging.instance.deleteToken();
      return true;
    } catch (_) {
      return false;
    }
  }

  /// Canaux Android 8+ par priorité (§9), nommés dans la langue de l'app. Le serveur choisit le
  /// canal (`android.notification.channel_id`) ; sans canal, Android prend `moyenne` (manifeste).
  Future<void> nommerCanaux(T t) async {
    if (!actif || defaultTargetPlatform != TargetPlatform.android) return;
    await _canaux.invokeMethod('canaux', [
      {'id': 'haute', 'nom': t.canalHaute, 'importance': 4}, // IMPORTANCE_HIGH : bandeau et son
      {'id': 'moyenne', 'nom': t.canalMoyenne, 'importance': 3}, // DEFAULT : son
      {'id': 'basse', 'nom': t.canalBasse, 'importance': 2}, // LOW : silencieux
    ]).catchError((_) => null);
  }
}

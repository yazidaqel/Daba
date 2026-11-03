import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:http/http.dart' as http;

/// Pas de réseau (ou serveur injoignable) : l'appelant garde l'opération en file.
class HorsLigne implements Exception {
  const HorsLigne();
}

/// Erreur RFC 9457 renvoyée par le serveur. Les clients décident sur `code`, jamais sur `detail`.
class ProblemeApi implements Exception {
  ProblemeApi(this.status, this.corps);
  final int status;
  final Map<String, dynamic> corps;

  String? get type => corps['type'] as String?;

  /// Code fixe du contrat : fin de `https://daba.ma/problemes/<code>`.
  String? get code => type?.split('/').last;
  String? get regle => corps['regle'] as String?;
  String? get detail => corps['detail'] as String?;
  bool get consentementRequis => code == 'consentement-requis';

  /// Appareil déconnecté à distance (§10) : seul ce code autorise l'effacement des données locales.
  bool get appareilRevoque => code == 'appareil-revoque';

  @override
  String toString() => 'ProblemeApi($status, $corps)';
}

/// Client HTTP du contrat `api/openapi.yaml`. Les corps restent en JSON (Map) :
/// l'app les stocke tels quels en SQLite, au format du contrat.
class Api {
  Api(this.baseUrl, {http.Client? client, this.langue = 'fr'}) : _client = client ?? http.Client();

  final String baseUrl;
  final http.Client _client;

  /// Code `Langue` du contrat (fr, ar, ary-Arab, ary-Latn), envoyé en Accept-Language.
  String langue;
  String? jeton;

  /// Renouvelle la session (`POST /v1/auth/refresh`) ; vrai si un nouveau jeton est en place.
  Future<bool> Function()? rafraichir;

  /// Appelé quand un appel, quel qu'il soit, répond `appareil-revoque`.
  void Function()? surRevocation;

  Future<bool>? _rafraichissement;

  static const delai = Duration(seconds: 20);

  Future<dynamic> get(String chemin, {Map<String, String>? query}) => _envoyer('GET', chemin, query: query);
  Future<dynamic> post(String chemin, [Object? corps, Map<String, String> entetes = const {}]) =>
      _envoyer('POST', chemin, corps: corps, entetes: entetes);
  Future<dynamic> put(String chemin, Object corps) => _envoyer('PUT', chemin, corps: corps);

  Future<dynamic> _envoyer(String methode, String chemin,
      {Object? corps, Map<String, String>? query, Map<String, String> entetes = const {}, bool reessai = true}) async {
    final req = http.Request(methode, Uri.parse('$baseUrl$chemin').replace(queryParameters: query))
      ..headers.addAll({
        'Accept': 'application/json, application/problem+json',
        'Accept-Language': langue,
        if (jeton != null) 'Authorization': 'Bearer $jeton',
        if (corps != null) 'Content-Type': 'application/json',
        ...entetes,
      });
    if (corps != null) req.body = jsonEncode(corps);
    final http.Response rep;
    try {
      rep = await http.Response.fromStream(await _client.send(req).timeout(delai));
    } on SocketException {
      throw const HorsLigne();
    } on TimeoutException {
      throw const HorsLigne();
    } on http.ClientException {
      throw const HorsLigne();
    }
    final contenu = rep.body.isEmpty ? null : jsonDecode(utf8.decode(rep.bodyBytes));
    if (rep.statusCode < 400) return contenu;

    final probleme = ProblemeApi(rep.statusCode, contenu is Map<String, dynamic> ? contenu : {'status': rep.statusCode});
    if (probleme.appareilRevoque) {
      surRevocation?.call();
    } else if (rep.statusCode == 401 && reessai && jeton != null && rafraichir != null && !chemin.startsWith('/v1/auth/')) {
      // Jeton expiré : un seul renouvellement pour tous les appels en cours, puis on rejoue l'appel.
      if (await (_rafraichissement ??= rafraichir!().whenComplete(() => _rafraichissement = null))) {
        return _envoyer(methode, chemin, corps: corps, query: query, entetes: entetes, reessai: false);
      }
    }
    throw probleme;
  }
}

import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:http/http.dart' as http;

/// Pas de réseau (ou serveur injoignable) : l'appelant garde l'opération en file.
class HorsLigne implements Exception {
  const HorsLigne();
}

/// Erreur RFC 9457 renvoyée par le serveur.
class ProblemeApi implements Exception {
  ProblemeApi(this.status, this.corps);
  final int status;
  final Map<String, dynamic> corps;

  String? get type => corps['type'] as String?;
  String? get regle => corps['regle'] as String?;
  String? get detail => corps['detail'] as String?;
  bool get consentementRequis => status == 403 && (type ?? '').endsWith('consentement-requis');

  /// Appareil déconnecté à distance (§10). Le contrat ne fixe pas encore ce `type` :
  /// demandé au back-end, d'ici là aucun 403 n'efface l'appareil.
  bool get appareilRevoque => status == 403 && (type ?? '').endsWith('appareil-revoque');

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

  static const delai = Duration(seconds: 20);

  Future<dynamic> get(String chemin, {Map<String, String>? query}) => _envoyer('GET', chemin, query: query);
  Future<dynamic> post(String chemin, [Object? corps]) => _envoyer('POST', chemin, corps: corps);
  Future<dynamic> put(String chemin, Object corps) => _envoyer('PUT', chemin, corps: corps);

  Future<dynamic> _envoyer(String methode, String chemin, {Object? corps, Map<String, String>? query}) async {
    final req = http.Request(methode, Uri.parse('$baseUrl$chemin').replace(queryParameters: query))
      ..headers.addAll({
        'Accept': 'application/json, application/problem+json',
        'Accept-Language': langue,
        if (jeton != null) 'Authorization': 'Bearer $jeton',
        if (corps != null) 'Content-Type': 'application/json',
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
    if (rep.statusCode >= 400) {
      throw ProblemeApi(rep.statusCode, contenu is Map<String, dynamic> ? contenu : {'status': rep.statusCode});
    }
    return contenu;
  }
}

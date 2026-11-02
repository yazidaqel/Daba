import 'dart:convert';

import 'package:sqlite3/sqlite3.dart';
import 'package:uuid/uuid.dart';

/// Base locale SQLite de l'appareil (§10 hors ligne).
///
/// - `objet` : copie locale des entités tirées par `GET /v1/sync/changements`
///   (même forme JSON que le contrat), plus les objets créés sur l'appareil
///   (`local = 1`) tant que le serveur ne les a pas renvoyés.
/// - `operation` : file des opérations à pousser vers `POST /v1/sync/operations`,
///   dans l'ordre du geste.
/// - `delta_local` : effet des opérations pas encore reflété par le serveur
///   (stock, solde) ; supprimé après une synchronisation complète.
/// - `compteur` : dernière séquence émise par série (BL, FA, AV), RG-F1.
class Stockage {
  Stockage(this.db) {
    db.execute('''
      PRAGMA journal_mode = WAL;
      CREATE TABLE IF NOT EXISTS kv (cle TEXT PRIMARY KEY, valeur TEXT NOT NULL);
      CREATE TABLE IF NOT EXISTS objet (
        entite TEXT NOT NULL, id TEXT NOT NULL, donnees TEXT NOT NULL,
        local INTEGER NOT NULL DEFAULT 0, PRIMARY KEY (entite, id));
      CREATE TABLE IF NOT EXISTS operation (
        rang INTEGER PRIMARY KEY AUTOINCREMENT, id TEXT NOT NULL UNIQUE,
        type TEXT NOT NULL, cree_le TEXT NOT NULL, donnees TEXT NOT NULL,
        statut TEXT NOT NULL DEFAULT 'EN_ATTENTE', resultat TEXT);
      CREATE TABLE IF NOT EXISTS delta_local (
        operation_id TEXT NOT NULL, nature TEXT NOT NULL, cle TEXT NOT NULL, delta INTEGER NOT NULL);
      CREATE INDEX IF NOT EXISTS delta_cle ON delta_local (nature, cle);
      CREATE TABLE IF NOT EXISTS compteur (serie TEXT PRIMARY KEY, dernier INTEGER NOT NULL);
    ''');
  }

  factory Stockage.ouvrir(String chemin) => Stockage(sqlite3.open(chemin));
  factory Stockage.memoire() => Stockage(sqlite3.openInMemory());

  final Database db;
  static const _uuid = Uuid();

  /// UUID généré sur l'appareil (clé d'idempotence côté serveur).
  static String nouvelId() => _uuid.v4();

  static String maintenant() => DateTime.now().toUtc().toIso8601String();

  T transaction<T>(T Function() action) {
    db.execute('BEGIN IMMEDIATE');
    try {
      final r = action();
      db.execute('COMMIT');
      return r;
    } catch (_) {
      db.execute('ROLLBACK');
      rethrow;
    }
  }

  // ------------------------------------------------------------------ kv
  String? lire(String cle) {
    final r = db.select('SELECT valeur FROM kv WHERE cle = ?', [cle]);
    return r.isEmpty ? null : r.first['valeur'] as String;
  }

  void ecrire(String cle, String? valeur) => valeur == null
      ? db.execute('DELETE FROM kv WHERE cle = ?', [cle])
      : db.execute('INSERT OR REPLACE INTO kv VALUES (?, ?)', [cle, valeur]);

  // ------------------------------------------------------------------ objets
  void enregistrer(String entite, String id, Map<String, dynamic> donnees, {bool local = false}) =>
      db.execute('INSERT OR REPLACE INTO objet VALUES (?, ?, ?, ?)',
          [entite, id, jsonEncode(donnees), local ? 1 : 0]);

  void supprimer(String entite, String id) =>
      db.execute('DELETE FROM objet WHERE entite = ? AND id = ?', [entite, id]);

  Map<String, dynamic>? objet(String entite, String id) {
    final r = db.select('SELECT donnees FROM objet WHERE entite = ? AND id = ?', [entite, id]);
    return r.isEmpty ? null : jsonDecode(r.first['donnees'] as String) as Map<String, dynamic>;
  }

  /// Objets d'une entité, filtrés par une clause SQL sur `donnees` (json_extract).
  List<Map<String, dynamic>> objets(String entite,
      {String where = '1', List<Object?> args = const [], String orderBy = 'rowid', int? limite}) {
    final r = db.select(
        'SELECT donnees FROM objet WHERE entite = ? AND ($where) ORDER BY $orderBy'
        '${limite == null ? '' : ' LIMIT $limite'}',
        [entite, ...args]);
    return [for (final l in r) jsonDecode(l['donnees'] as String) as Map<String, dynamic>];
  }

  // ------------------------------------------------------------------ file d'opérations
  /// Ajoute une opération à la file et enregistre ses effets locaux.
  /// À appeler dans la même transaction que l'écriture des objets locaux.
  String enfiler(String type, Map<String, dynamic> donnees,
      {Map<(String, String), int> deltas = const {}, String? id}) {
    final opId = id ?? nouvelId();
    db.execute('INSERT INTO operation (id, type, cree_le, donnees) VALUES (?, ?, ?, ?)',
        [opId, type, maintenant(), jsonEncode(donnees)]);
    deltas.forEach((k, v) => db.execute(
        'INSERT INTO delta_local VALUES (?, ?, ?, ?)', [opId, k.$1, k.$2, v]));
    return opId;
  }

  /// Opérations au format du contrat (`Operation`), dans l'ordre du geste.
  List<Map<String, dynamic>> enAttente({int limite = 500}) => [
        for (final r in db.select(
            "SELECT id, type, cree_le, donnees FROM operation WHERE statut = 'EN_ATTENTE' ORDER BY rang LIMIT ?",
            [limite]))
          {
            'id': r['id'],
            'type': r['type'],
            'creeLe': r['cree_le'],
            'donnees': jsonDecode(r['donnees'] as String),
          }
      ];

  int get nombreEnAttente =>
      db.select("SELECT count(*) n FROM operation WHERE statut = 'EN_ATTENTE'").first['n'] as int;

  void marquer(String opId, String statut, Map<String, dynamic> resultat) => db.execute(
      'UPDATE operation SET statut = ?, resultat = ? WHERE id = ?', [statut, jsonEncode(resultat), opId]);

  /// Opérations refusées ou en conflit, à afficher (le serveur les a journalisées).
  List<Map<String, dynamic>> operationsSignalees() => [
        for (final r in db.select(
            "SELECT id, type, cree_le, statut, resultat FROM operation WHERE statut IN ('REJETEE', 'EN_CONFLIT') ORDER BY rang DESC"))
          {
            'id': r['id'],
            'type': r['type'],
            'creeLe': r['cree_le'],
            'statut': r['statut'],
            'resultat': jsonDecode(r['resultat'] as String),
          }
      ];

  /// Somme des effets locaux pas encore reflétés par le serveur.
  int delta(String nature, String cle) => db
      .select('SELECT coalesce(sum(delta), 0) s FROM delta_local WHERE nature = ? AND cle = ?', [nature, cle])
      .first['s'] as int;

  /// Après un tirage complet qui a suivi la poussée, le serveur reflète les opérations acquittées.
  void oublierDeltasAcquittes() => db.execute(
      "DELETE FROM delta_local WHERE operation_id IN (SELECT id FROM operation WHERE statut <> 'EN_ATTENTE')");

  // ------------------------------------------------------------------ numérotation (RG-F1)
  /// Séquence suivante d'une série (BL, FA, AV) ; à appeler dans la transaction qui émet le document
  /// pour qu'un échec ne laisse pas de trou.
  int sequenceSuivante(String serie) {
    db.execute('INSERT OR IGNORE INTO compteur VALUES (?, 0)', [serie]);
    db.execute('UPDATE compteur SET dernier = dernier + 1 WHERE serie = ?', [serie]);
    return db.select('SELECT dernier FROM compteur WHERE serie = ?', [serie]).first['dernier'] as int;
  }

  /// Rattrape le compteur sur un document de l'appareil déjà connu du serveur (réinstallation…).
  void rattraperSequence(String serie, int sequence) {
    db.execute('INSERT OR IGNORE INTO compteur VALUES (?, 0)', [serie]);
    db.execute('UPDATE compteur SET dernier = max(dernier, ?) WHERE serie = ?', [sequence, serie]);
  }

  static String numero(String prefixe, String serie, int sequence) =>
      '$prefixe-$serie-${sequence.toString().padLeft(6, '0')}';

  /// Efface tout (appareil déconnecté à distance, §10).
  void effacerTout() => db.execute('''
      DELETE FROM kv; DELETE FROM objet; DELETE FROM operation;
      DELETE FROM delta_local; DELETE FROM compteur;''');
}

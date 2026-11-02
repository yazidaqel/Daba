import 'api.dart';
import 'stockage.dart';

/// L'appareil a été déconnecté à distance (§10, appareil perdu).
class AppareilRevoque implements Exception {
  const AppareilRevoque();
}

/// Rejoue la file d'opérations (`POST /v1/sync/operations`) puis tire les changements
/// (`GET /v1/sync/changements`). Sans réseau, lève [HorsLigne] et ne perd rien.
class Synchronisation {
  Synchronisation(this.stockage, this.api, {required this.appareilId});

  final Stockage stockage;
  final Api api;
  final String appareilId;
  Future<void>? _enCours;

  static const series = {'BON_LIVRAISON': 'BL', 'FACTURE': 'FA', 'AVOIR': 'AV'};

  /// Une seule synchronisation à la fois : les deltas locaux ne sont oubliés qu'après
  /// une poussée suivie d'un tirage complet.
  Future<void> synchroniser() => _enCours ??= _synchroniser().whenComplete(() => _enCours = null);

  Future<void> _synchroniser() async {
    try {
      await pousser();
      await tirer();
    } on ProblemeApi catch (e) {
      // Seule une révocation explicite efface l'appareil : un autre 403 (rôle) ne doit jamais
      // détruire des pièces de vente pas encore remontées (RG-C4).
      if (e.appareilRevoque) throw const AppareilRevoque();
      rethrow;
    }
    stockage.oublierDeltasAcquittes();
  }

  Future<void> pousser() async {
    while (true) {
      final ops = stockage.enAttente();
      if (ops.isEmpty) return;
      final rep = await api.post('/v1/sync/operations', {'appareilId': appareilId, 'operations': ops})
          as Map<String, dynamic>;
      final envoyes = {for (final o in ops) o['id']};
      final resultats = [
        for (final r in (rep['resultats'] as List).cast<Map<String, dynamic>>())
          if (envoyes.contains(r['operationId'])) r
      ];
      stockage.transaction(() {
        for (final r in resultats) {
          stockage.marquer(r['operationId'] as String, r['statut'] as String, r);
          final op = ops.firstWhere((o) => o['id'] == r['operationId']);
          if (r['statut'] == 'REJETEE' && op['type'] == 'EMISSION_DOCUMENT') {
            // Le document reste visible (jamais supprimé) mais marqué refusé par le serveur.
            final id = op['donnees']['documentId'] as String?;
            final doc = id == null ? null : stockage.objet('DOCUMENT', id);
            if (doc != null) stockage.enregistrer('DOCUMENT', id!, {...doc, 'rejete': true}, local: true);
          }
        }
      });
      // Un serveur qui n'acquitte rien ferait boucler : on retentera à la prochaine synchro.
      if (resultats.isEmpty) return;
    }
  }

  Future<void> tirer() async {
    var curseur = stockage.lire('curseur');
    var purger = false;
    while (true) {
      final Map<String, dynamic> lot;
      try {
        lot = await api.get('/v1/sync/changements', query: {'curseur': ?curseur})
            as Map<String, dynamic>;
      } on ProblemeApi catch (e) {
        if (e.status != 410 || curseur == null) rethrow;
        // Curseur trop ancien : resynchronisation complète. La copie locale n'est purgée qu'avec
        // la première page reçue, pour ne jamais laisser l'appareil vide si le réseau retombe.
        purger = true;
        curseur = null;
        continue;
      }
      stockage.transaction(() {
        if (purger) {
          stockage.db.execute('DELETE FROM objet WHERE local = 0');
          purger = false;
        }
        for (final c in (lot['changements'] as List).cast<Map<String, dynamic>>()) {
          appliquer(c);
        }
        stockage.ecrire('curseur', lot['curseur'] as String);
      });
      curseur = lot['curseur'] as String;
      if (lot['plusDeChangements'] != true) return;
    }
  }

  void appliquer(Map<String, dynamic> c) {
    final entite = c['entite'] as String;
    final id = c['id'] as String;
    if (c['action'] == 'SUPPRESSION') {
      if (entite != 'DOCUMENT') stockage.supprimer(entite, id); // un document de vente ne disparaît jamais
      return;
    }
    final donnees = (c['donnees'] as Map).cast<String, dynamic>();
    stockage.enregistrer(entite, id, donnees);
    switch (entite) {
      case 'DOCUMENT' when donnees['appareilId'] == appareilId:
        stockage.rattraperSequence(series[donnees['type']]!, donnees['sequence'] as int);
      case 'MOUVEMENT' when donnees['operationId'] != null:
        // Le mouvement affiché dès le geste est remplacé par celui du serveur.
        stockage.db.execute(
            "DELETE FROM objet WHERE entite = 'MOUVEMENT' AND local = 1 AND json_extract(donnees, '\$.operationId') = ?",
            [donnees['operationId']]);
      case 'PARAMETRES':
        // Les paramètres du serveur (mentions légales, politique) remplacent toute copie locale.
        stockage.db.execute("DELETE FROM objet WHERE entite = 'PARAMETRES' AND id <> ?", [id]);
      case 'COMPTE_CREDIT':
        // Un compte créé sur l'appareil (plafond saisi hors ligne) est remplacé par celui du serveur.
        stockage.db.execute(
            "DELETE FROM objet WHERE entite = 'COMPTE_CREDIT' AND id <> ? AND json_extract(donnees, '\$.hanout.id') = ?",
            [id, donnees['hanout']['id']]);
    }
  }
}

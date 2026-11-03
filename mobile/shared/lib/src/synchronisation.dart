import 'dart:convert';

import 'api.dart';
import 'stockage.dart';

/// Rejoue la file d'opérations (`POST /v1/sync/operations`) puis tire les changements
/// (`GET /v1/sync/changements`). Sans réseau, lève [HorsLigne] et ne perd rien.
class Synchronisation {
  Synchronisation(this.stockage, this.api, {required this.appareilId, required this.prefixe});

  final Stockage stockage;
  final Api api;
  final String appareilId;

  /// Préfixe de numérotation du couple (ambulant, appareil), RG-F1.
  final String prefixe;
  Future<void>? _enCours;

  static const series = {'BON_LIVRAISON': 'BL', 'FACTURE': 'FA', 'AVOIR': 'AV'};

  /// Une seule synchronisation à la fois : les deltas locaux ne sont oubliés qu'après
  /// une poussée suivie d'un tirage complet.
  Future<void> synchroniser() => _enCours ??= _synchroniser().whenComplete(() => _enCours = null);

  Future<void> _synchroniser() async {
    // Une révocation (`appareil-revoque`) est signalée par l'Api sur n'importe quel appel ;
    // tout autre refus laisse la file intacte (RG-C4).
    await pousser();
    if (await tirer()) stockage.oublierDeltasAcquittes();
  }

  /// Pages tirées au plus par synchronisation (500 changements chacune) ; la suite part
  /// à la synchronisation suivante, depuis le curseur enregistré.
  static const pagesMax = 40;

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
          final opId = r['operationId'] as String;
          if (r['statut'] == 'REJETEE' && ProblemeApi(422, (r['erreur'] as Map?)?.cast<String, dynamic>() ?? {}).consentementRequis) {
            // Refus provisoire (contrat 0.5.1) : ni annulation ni affichage comme refusée ; l'opération
            // attend un consentement actif, puis repart (Stockage.reprendreApresConsentement).
            stockage.marquer(opId, 'CONSENTEMENT', r);
            continue;
          }
          stockage.marquer(opId, r['statut'] as String, r);
          final op = ops.firstWhere((o) => o['id'] == r['operationId']);
          if (r['statut'] == 'REJETEE' && op['type'] == 'EMISSION_DOCUMENT') {
            // Le document reste visible (jamais supprimé) mais marqué refusé par le serveur.
            final id = op['donnees']['documentId'] as String?;
            final doc = id == null ? null : stockage.objet('DOCUMENT', id);
            if (doc != null) stockage.enregistrer('DOCUMENT', id!, {...doc, 'rejete': true}, local: true);
          }
          if (r['statut'] == 'REJETEE' && op['type'] == 'RESOLUTION_CONTESTATION') {
            // Clôture refusée : la contestation est toujours ouverte côté serveur, elle réapparaît.
            final id = op['donnees']['contestationId'] as String;
            final c = stockage.objet('CONTESTATION', id);
            if (c != null) stockage.enregistrer('CONTESTATION', id, {...c, 'statut': 'OUVERTE'}..remove('resolueLe'));
          }
          if (r['statut'] == 'REJETEE' && op['type'] == 'PROFIL_AMBULANT') {
            // Profil refusé (code inconnu…) : on revient au dernier profil du serveur, ou à aucun,
            // pour ne pas imprimer un nom refusé sur les pièces (pas d'écrasement silencieux, §10).
            stockage.db.execute("DELETE FROM objet WHERE entite = 'PROFIL'");
            if (stockage.lire('profilServeur') case final String p) {
              final v = jsonDecode(p) as Map<String, dynamic>;
              stockage.enregistrer('PROFIL', v['id'] as String, (v['donnees'] as Map).cast<String, dynamic>());
            }
          }
          if (r['conflit'] case {'resolution': 'VERSION_SERVEUR_CONSERVEE', 'versionServeur': final Map v}) {
            versionServeur(op['type'] as String, v.cast<String, dynamic>());
          }
        }
      });
      // Un serveur qui n'acquitte rien ferait boucler : on retentera à la prochaine synchro.
      if (resultats.isEmpty) return;
    }
  }

  /// Geste plus ancien qu'une modification faite ailleurs : le serveur a gardé sa valeur, que le
  /// tirage ne renverra pas (elle n'a pas changé) ; l'appareil la reprend à la place de la sienne.
  void versionServeur(String typeOperation, Map<String, dynamic> v) {
    final (entite, where, args) = switch (typeOperation) {
      'TARIF' => ('TARIF', "json_extract(donnees, '\$.produitId') = ? AND json_extract(donnees, '\$.hanoutId') IS ?",
          [v['produitId'], v['hanoutId']]),
      'CREDIT_HANOUT' => ('COMPTE_CREDIT', "json_extract(donnees, '\$.hanout.id') = ?", [v['hanout']?['id']]),
      'PARAMETRES_AMBULANT' => ('PARAMETRES', '1', <Object?>[]),
      'PROFIL_AMBULANT' => ('PROFIL', '1', <Object?>[]),
      _ => (null, '', <Object?>[]),
    };
    if (entite == null) return;
    final local = stockage.db.select('SELECT id FROM objet WHERE entite = ? AND ($where)', [entite, ...args]).firstOrNull;
    final id = (v['id'] ?? local?['id'] ?? Stockage.nouvelId()) as String;
    appliquer({'entite': entite, 'id': id, 'action': 'UPSERT', 'donnees': v});
  }

  /// Vrai si le serveur n'a plus de changements à donner.
  Future<bool> tirer() async {
    var curseur = stockage.lire('curseur');
    var purger = false;
    for (var page = 0; page < pagesMax; page++) {
      final Map<String, dynamic> lot;
      try {
        lot = await api.get('/v1/sync/changements', query: {'curseur': ?curseur})
            as Map<String, dynamic>;
      } on ProblemeApi catch (e) {
        if (e.code != 'curseur-expire' || curseur == null) rethrow;
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
      if (lot['plusDeChangements'] != true) return true;
    }
    return false;
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
      // Documents de cet ambulant sur ce téléphone (réinstallation, retour après un autre ambulant).
      case 'DOCUMENT' when (donnees['numero'] as String? ?? '').startsWith('$prefixe-'):
        stockage.rattraperSequence(series[donnees['type']]!, donnees['sequence'] as int);
      case 'MOUVEMENT' when donnees['operationId'] != null:
        // Le mouvement affiché dès le geste est remplacé par celui du serveur.
        stockage.db.execute(
            "DELETE FROM objet WHERE entite = 'MOUVEMENT' AND local = 1 AND json_extract(donnees, '\$.operationId') = ?",
            [donnees['operationId']]);
      case 'PARAMETRES' || 'PROFIL':
        // Les paramètres et le profil du serveur remplacent toute copie locale (une seule ligne).
        stockage.db.execute('DELETE FROM objet WHERE entite = ? AND id <> ?', [entite, id]);
        // Version serveur gardée à part : elle revient si un profil saisi ensuite est refusé.
        if (entite == 'PROFIL') stockage.ecrire('profilServeur', jsonEncode({'id': id, 'donnees': donnees}));
      case 'TARIF':
        // Un tarif saisi sur l'appareil est remplacé par celui du serveur (même produit, même hanout).
        stockage.db.execute(
            "DELETE FROM objet WHERE entite = 'TARIF' AND id <> ? AND json_extract(donnees, '\$.produitId') = ? "
            "AND json_extract(donnees, '\$.hanoutId') IS ?",
            [id, donnees['produitId'], donnees['hanoutId']]);
      case 'COMPTE_CREDIT':
        // Un compte créé sur l'appareil (plafond saisi hors ligne) est remplacé par celui du serveur.
        stockage.db.execute(
            "DELETE FROM objet WHERE entite = 'COMPTE_CREDIT' AND id <> ? AND json_extract(donnees, '\$.hanout.id') = ?",
            [id, donnees['hanout']['id']]);
    }
  }
}

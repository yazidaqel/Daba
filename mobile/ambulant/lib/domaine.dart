import 'dart:convert';

import 'package:daba_shared/daba_shared.dart';

/// Refus locaux d'une vente ; l'interface les traduit.
enum RefusVente { factureSansMentions, factureTvaInconnue, creditBloque, creditAvertissement, remiseHorsPlafond, qrAutreAmbulant, qrIllisible, bonDejaCharge }

class VenteRefusee implements Exception {
  const VenteRefusee(this.motif);
  final RefusVente motif;
}

enum Depassement { aucun, plafond, delai, plafondEtDelai }

class LigneVente {
  LigneVente({required this.produitId, required this.quantite, required this.prixUnitaire, this.tarif = 'STANDARD', this.remise = 0, this.retenir = false});
  final String produitId;
  int quantite;
  int prixUnitaire; // centimes
  String tarif;
  bool retenir; // prix connu modifié : à retenir comme tarif négocié du hanout (choix explicite)
  int remise; // centimes, remise ponctuelle sur la ligne (ligne séparée, RG-F3)
  int get brut => quantite * prixUnitaire;
}

class Reglement {
  const Reglement(this.mode, this.montant, {this.chequeNumero, this.chequeBanque});
  final String mode; // ESPECES, CHEQUE
  final int montant;
  final String? chequeNumero, chequeBanque;
  Map<String, dynamic> toJson() => {
        'mode': mode,
        'montant': montant,
        if (chequeNumero != null) 'chequeNumero': chequeNumero,
        if (chequeBanque != null) 'chequeBanque': chequeBanque,
      };
}

/// Logique métier de l'app ambulant, entièrement locale : chaque geste écrit les objets
/// locaux et enfile l'opération du contrat dans la même transaction.
class Ambulant {
  Ambulant(this.s, {required this.appareilId, required this.prefixe, required this.utilisateurId, String nom = ''})
      : _nomSession = nom;

  final Stockage s;
  final String appareilId, prefixe, utilisateurId, _nomSession;

  /// Nom affiché sur les pièces : celui du profil (§3.1), sinon celui de la session.
  String get nom => (profil?['nom'] as String?) ?? _nomSession;

  static const _j = "json_extract(donnees, '\$.";

  // ------------------------------------------------------------------ référentiel
  List<Map<String, dynamic>> hanouts({String q = ''}) {
    if (q.trim().isEmpty) return s.objets('HANOUT', orderBy: '${_j}nom\')');
    final m = '%${q.trim()}%';
    return s.objets('HANOUT',
        where: '${_j}nom\') LIKE ? OR ${_j}gerant\') LIKE ? OR ${_j}telephone\') LIKE ? OR ${_j}codePos\') LIKE ?',
        args: [m, m, m, m],
        orderBy: '${_j}nom\')');
  }

  Map<String, dynamic>? hanout(String id) => s.objet('HANOUT', id);

  List<Map<String, dynamic>> produits({String q = ''}) {
    final m = '%${q.trim()}%';
    return s.objets('PRODUIT',
        where: q.trim().isEmpty
            ? '1'
            : '${_j}libelle.fr\') LIKE ? OR ${_j}libelle.ar\') LIKE ? OR ${_j}libelle.ary-Arab\') LIKE ? '
                'OR ${_j}libelle.ary-Latn\') LIKE ? OR ${_j}marque\') LIKE ? OR ${_j}ean\') = ?',
        args: q.trim().isEmpty ? const [] : [m, m, m, m, m, q.trim()],
        orderBy: '${_j}libelle.fr\')');
  }

  Map<String, dynamic>? produit(String id) => s.objet('PRODUIT', id);

  Map<String, dynamic> get parametres =>
      s.objets('PARAMETRES').firstOrNull ?? {'politiqueDepassementCredit': 'AVERTIR', 'seuilEcartInventairePourcent': 0};

  /// Facture possible seulement si le profil porte les mentions légales (RG-F2).
  bool get peutFacturer => parametres['mentionsLegales'] != null;

  /// Taux de TVA du produit tel que l'appareil le connaît (`Produit.tauxTva`), null si inconnu.
  num? tauxTva(String produitId) => produit(produitId)?['tauxTva'] as num?;

  /// Facture d'un ambulant assujetti : chaque produit doit avoir un taux connu, sinon la TVA
  /// ne peut pas figurer sur la pièce (anomalie TVA_INCONNUE, RG-F2). L'app ne la propose pas.
  bool tvaInconnue(List<LigneVente> lignes) =>
      parametres['mentionsLegales']?['assujettiTva'] == true && lignes.any((l) => tauxTva(l.produitId) == null);

  /// TVA comprise dans un montant TTC : M × t / (100 + t), arrondie au centime, demi au-dessus en
  /// valeur absolue (formule du contrat, `TauxTva`).
  static int tvaComprise(int montant, num taux) {
    final t = (taux * 100).round(); // taux en centièmes de point, pour rester en entiers
    final valeurAbsolue = (2 * montant.abs() * t + (10000 + t)) ~/ (2 * (10000 + t));
    return montant < 0 ? -valeurAbsolue : valeurAbsolue;
  }

  // ------------------------------------------------------------------ stock
  Map<String, dynamic>? _ligneStock(String produitId) =>
      s.objets('STOCK', where: '${_j}produitId\') = ?', args: [produitId]).firstOrNull;

  /// Stock normal = dernier état serveur + gestes pas encore reflétés.
  int stock(String produitId) => ((_ligneStock(produitId)?['quantite'] as int?) ?? 0) + s.delta('STOCK', produitId);

  /// Stock promo isolé par réservation (RG-S3).
  List<({String campagneId, String reservationId, int quantite})> stockPromo(String produitId) => [
        for (final p in ((_ligneStock(produitId)?['promo'] as List?) ?? const []).cast<Map>())
          (
            campagneId: p['campagneId'] as String,
            reservationId: p['reservationId'] as String,
            quantite: (p['quantite'] as int) + s.delta('STOCK_PROMO', p['reservationId'] as String),
          )
      ];

  int stockTotal(String produitId) => stock(produitId) + stockPromo(produitId).fold(0, (a, p) => a + p.quantite);

  /// Produits connus en stock (normal ou promo), avec leur quantité.
  List<(Map<String, dynamic>, int)> stockEmbarque() {
    final ids = {
      for (final l in s.objets('STOCK')) l['produitId'] as String,
      for (final r in s.db.select("SELECT DISTINCT cle FROM delta_local WHERE nature = 'STOCK'")) r['cle'] as String,
    };
    return [
      for (final id in ids)
        if (produit(id) case final p?) (p, stockTotal(id))
    ]..sort((a, b) => (a.$1['libelle']['fr'] as String).compareTo(b.$1['libelle']['fr'] as String));
  }

  List<Map<String, dynamic>> mouvements() => s.objets('MOUVEMENT', orderBy: '${_j}horodatage\') DESC', limite: 200);

  // ------------------------------------------------------------------ prix
  ({int? prix, String tarif}) prix(String produitId, String hanoutId) {
    final negocie = s.objets('TARIF', where: '${_j}produitId\') = ? AND ${_j}hanoutId\') = ?', args: [produitId, hanoutId]);
    if (negocie.isNotEmpty) return (prix: negocie.first['prixUnitaire'] as int, tarif: 'NEGOCIE');
    final standard = s.objets('TARIF', where: '${_j}produitId\') = ? AND ${_j}hanoutId\') IS NULL', args: [produitId]);
    return (prix: standard.firstOrNull?['prixUnitaire'] as int?, tarif: 'STANDARD');
  }

  /// Ligne locale du tarif (standard si [hanoutId] est null), pour la mettre à jour sur place.
  ({String id, int prix})? _tarif(String produitId, String? hanoutId) {
    final r = s.db.select(
        "SELECT id, ${_j}prixUnitaire') prix FROM objet WHERE entite = 'TARIF' AND ${_j}produitId') = ? AND ${_j}hanoutId') IS ?",
        [produitId, hanoutId]).firstOrNull;
    return r == null ? null : (id: r['id'] as String, prix: r['prix'] as int);
  }

  /// Tarifs retenus à la vente (§3.4) : le premier prix d'un produit sans tarif devient le tarif
  /// standard ; un prix connu modifié ne devient le tarif négocié du hanout que si l'ambulant l'a
  /// demandé ([LigneVente.retenir]), sinon il ne vaut que pour cette vente (rabais du jour).
  /// À appeler dans la transaction de la vente.
  void _retenirTarifs(String hanoutId, List<LigneVente> lignes) {
    for (final l in lignes) {
      final h = l.retenir ? hanoutId : null;
      if (h == null && prix(l.produitId, hanoutId).prix != null) continue;
      final connu = _tarif(l.produitId, h);
      if (l.prixUnitaire <= 0 || connu?.prix == l.prixUnitaire) continue;
      final tarif = {'produitId': l.produitId, 'hanoutId': ?h, 'prixUnitaire': l.prixUnitaire};
      s.enfiler('TARIF', tarif);
      s.enregistrer('TARIF', connu?.id ?? Stockage.nouvelId(), tarif, local: true);
    }
  }

  // ------------------------------------------------------------------ karni
  Map<String, dynamic>? compte(String hanoutId) =>
      s.objets('COMPTE_CREDIT', where: '${_j}hanout.id\') = ?', args: [hanoutId]).firstOrNull;

  /// RG-C1 : solde du serveur + ventes à crédit et versements pas encore remontés.
  int solde(String hanoutId) => ((compte(hanoutId)?['solde'] as int?) ?? 0) + s.delta('SOLDE', hanoutId);

  /// Hanouts ayant un compte ou un geste de crédit en attente, avec leur solde.
  List<(Map<String, dynamic>, int)> comptes() {
    final ids = {
      for (final c in s.objets('COMPTE_CREDIT')) c['hanout']['id'] as String,
      for (final r in s.db.select("SELECT DISTINCT cle FROM delta_local WHERE nature = 'SOLDE'")) r['cle'] as String,
    };
    return [
      for (final id in ids) (hanout(id) ?? {'id': id, 'nom': compte(id)?['hanout']['nom'] ?? ''}, solde(id))
    ]..sort((a, b) => b.$2.compareTo(a.$2));
  }

  DateTime? _plusAncienImpaye(String hanoutId) {
    final serveur = compte(hanoutId)?['plusAncienImpayeLe'] as String?;
    if (serveur != null) return DateTime.parse(serveur);
    final locaux = s.objets('DOCUMENT',
        where: '${_j}hanout.id\') = ? AND ${_j}montantCredit\') > 0', args: [hanoutId], orderBy: '${_j}emisLe\')');
    return locaux.isEmpty ? null : DateTime.parse(locaux.first['emisLe'] as String);
  }

  /// RG-C3 : la vente mettrait-elle le hanout au-delà du plafond ou du délai ?
  Depassement controleCredit(String hanoutId, int nouveauCredit, {DateTime? maintenant}) {
    if (nouveauCredit <= 0) return Depassement.aucun;
    final c = compte(hanoutId);
    final plafond = c?['plafond'] as int?;
    final delai = c?['delaiMaxJours'] as int?;
    final horsPlafond = plafond != null && solde(hanoutId) + nouveauCredit > plafond;
    final ancien = _plusAncienImpaye(hanoutId);
    final horsDelai =
        delai != null && ancien != null && (maintenant ?? DateTime.now()).difference(ancien).inDays > delai;
    return switch ((horsPlafond, horsDelai)) {
      (true, true) => Depassement.plafondEtDelai,
      (true, false) => Depassement.plafond,
      (false, true) => Depassement.delai,
      _ => Depassement.aucun,
    };
  }

  List<Map<String, dynamic>> documents({String? hanoutId}) => s.objets('DOCUMENT',
      where: hanoutId == null ? '1' : '${_j}hanout.id\') = ?',
      args: [?hanoutId],
      orderBy: '${_j}emisLe\') DESC',
      limite: 200);

  /// Achats et versements du hanout, du plus récent au plus ancien (§3.5).
  List<Map<String, dynamic>> historique(String hanoutId) {
    final v = s.objets('VERSEMENT', where: '${_j}hanoutId\') = ? AND ${_j}documentId\') IS NULL', args: [hanoutId]);
    return [...documents(hanoutId: hanoutId), ...v]
      ..sort((a, b) => ((b['emisLe'] ?? b['recuLe']) as String).compareTo((a['emisLe'] ?? a['recuLe']) as String));
  }

  /// Lignes ARTICLE de la dernière vente au hanout (« dernière commande en un clic », §3.4).
  List<LigneVente> derniereCommande(String hanoutId) {
    final doc = s.objets('DOCUMENT',
            where: "${_j}hanout.id') = ? AND ${_j}type') <> 'AVOIR'", args: [hanoutId], orderBy: '${_j}emisLe\') DESC', limite: 1)
        .firstOrNull;
    if (doc == null) return [];
    final parProduit = <String, LigneVente>{};
    for (final l in (doc['lignes'] as List).cast<Map>()) {
      if (l['nature'] != 'ARTICLE') continue;
      final p = prix(l['produitId'] as String, hanoutId);
      parProduit.update(l['produitId'] as String, (v) => v..quantite += l['quantite'] as int,
          ifAbsent: () => LigneVente(
              produitId: l['produitId'] as String,
              quantite: l['quantite'] as int,
              prixUnitaire: p.prix ?? l['prixUnitaire'] as int,
              tarif: p.tarif));
    }
    return parProduit.values.toList();
  }

  // ------------------------------------------------------------------ vente (§3.4, RG-F*, RG-C3)
  /// Émet un bon de livraison (par défaut) ou une facture, hors ligne. Renvoie le document local.
  Map<String, dynamic> emettre({
    required String hanoutId,
    required List<LigneVente> lignes,
    String type = 'BON_LIVRAISON',
    List<Reglement> reglements = const [],
    bool confirmeParHanout = false,
    bool avertissementAccepte = false,
    String? demandeDepannageId,
  }) {
    final param = parametres;
    final mentions = param['mentionsLegales'];
    if (type == 'FACTURE' && mentions == null) throw const VenteRefusee(RefusVente.factureSansMentions);
    if (type == 'FACTURE' && tvaInconnue(lignes)) throw const VenteRefusee(RefusVente.factureTvaInconnue);
    final plafondRemise = (param['plafondRemisePonctuellePourcent'] as num?) ?? 100;
    if (lignes.any((l) => l.remise > l.brut * plafondRemise / 100)) {
      throw const VenteRefusee(RefusVente.remiseHorsPlafond);
    }
    final total = lignes.fold<int>(0, (a, l) => a + l.brut - l.remise);
    final regle = reglements.fold<int>(0, (a, r) => a + r.montant);
    final credit = total - regle;
    final depasse = controleCredit(hanoutId, credit) != Depassement.aucun;
    if (depasse) {
      if (param['politiqueDepassementCredit'] == 'BLOQUER') throw const VenteRefusee(RefusVente.creditBloque);
      if (!avertissementAccepte) throw const VenteRefusee(RefusVente.creditAvertissement);
    }

    return s.transaction(() {
      _retenirTarifs(hanoutId, lignes);
      final serie = Synchronisation.series[type]!;
      final sequence = s.sequenceSuivante(serie);
      final numero = Stockage.numero(prefixe, serie, sequence);
      final documentId = Stockage.nouvelId();
      final deltas = <(String, String), int>{};
      final saisies = <Map<String, dynamic>>[];
      final affichees = <Map<String, dynamic>>[];

      for (final l in lignes) {
        final libelle = produit(l.produitId)?['libelle'] ?? {'fr': l.produitId};
        // Taux figé dans la pièce tel que l'appareil le connaît, comme les mentions légales.
        final taux = tauxTva(l.produitId);
        var reste = l.quantite;
        // Le stock promo part en premier, pour tracer la promo jusqu'au hanout (RG-S3).
        for (final p in stockPromo(l.produitId)) {
          if (reste == 0) break;
          final q = reste < p.quantite ? reste : p.quantite;
          if (q <= 0) continue;
          reste -= q;
          deltas.update(('STOCK_PROMO', p.reservationId), (v) => v - q, ifAbsent: () => -q);
          saisies.add({
            'nature': 'ARTICLE', 'produitId': l.produitId, 'quantite': q, 'prixUnitaire': l.prixUnitaire,
            'tarif': l.tarif, 'campagneId': p.campagneId, 'reservationId': p.reservationId, 'tauxTva': ?taux,
          });
        }
        if (reste > 0) {
          // RG-S1 : une vente hors ligne n'est jamais bloquée par le stock ; le serveur signale l'anomalie.
          deltas.update(('STOCK', l.produitId), (v) => v - reste, ifAbsent: () => -reste);
          saisies.add({
            'nature': 'ARTICLE', 'produitId': l.produitId, 'quantite': reste, 'prixUnitaire': l.prixUnitaire,
            'tarif': l.tarif, 'tauxTva': ?taux,
          });
        }
        if (l.remise > 0) {
          saisies.add({'nature': 'REMISE_PONCTUELLE', 'produitId': l.produitId, 'montant': -l.remise, 'tauxTva': ?taux});
        }
        for (final sa in saisies.skip(affichees.length)) {
          affichees.add({
            ...sa,
            'libelle': libelle,
            'montant': sa['montant'] ?? sa['quantite'] * sa['prixUnitaire'],
            // Référence de la campagne imprimée sur la ligne (RG-F3)
            if (sa['campagneId'] case final String c) 'campagneNom': _nomCampagne(c),
          });
        }
      }
      if (credit != 0) deltas[('SOLDE', hanoutId)] = credit;
      // TVA d'une facture d'assujetti : somme des TVA de ligne (RG-F2) ; prix TTC.
      final tva = type == 'FACTURE' && (mentions as Map)['assujettiTva'] == true
          ? affichees.fold<int>(0, (a, l) => a + tvaComprise(l['montant'] as int, l['tauxTva'] as num))
          : null;

      final maintenant = Stockage.maintenant();
      final confirmation = confirmeParHanout ? {'mode': 'CONFIRMATION_ECRAN', 'le': maintenant} : null;
      final opId = s.enfiler('EMISSION_DOCUMENT', {
        'documentId': documentId,
        'type': type,
        'numero': numero,
        'sequence': sequence,
        'hanoutId': hanoutId,
        'lignes': saisies,
        'reglements': [for (final r in reglements) r.toJson()],
        'demandeDepannageId': ?demandeDepannageId,
        'confirmationHanout': ?confirmation,
        if (depasse) 'avertissementCreditAccepte': true,
        if (type == 'FACTURE') 'mentionsLegales': mentions,
      }, deltas: deltas);

      final h = hanout(hanoutId);
      final document = {
        'id': documentId,
        'type': type,
        'numero': numero,
        'sequence': sequence,
        'appareilId': appareilId,
        'ambulant': {'id': utilisateurId, 'nom': nom},
        'hanout': {'id': hanoutId, 'nom': h?['nom'] ?? ''},
        'emisLe': maintenant,
        'lignes': affichees,
        'total': total,
        'tva': ?tva,
        'montantRegle': regle,
        'montantCredit': credit,
        'statut': 'EMIS',
        'demandeDepannageId': ?demandeDepannageId,
        'confirmationHanout': ?confirmation,
        if (type == 'FACTURE') 'mentionsLegales': mentions,
      };
      s.enregistrer('DOCUMENT', documentId, document, local: true);
      _mouvementsLocaux(opId, 'VENTE', {for (final e in deltas.entries) if (e.key.$1 == 'STOCK') e.key.$2: e.value},
          documentId: documentId);
      return document;
    });
  }

  Object _nomCampagne(String campagneId) =>
      s.objets('PROMO', where: '${_j}campagneId\') = ?', args: [campagneId]).firstOrNull?['campagneNom'] ??
      {'fr': campagneId.substring(0, 8)};

  // ------------------------------------------------------------------ versements, crédit, paramètres
  void versement(String hanoutId, int montant, {String mode = 'ESPECES', String? chequeNumero, String? chequeBanque}) {
    s.transaction(() {
      final id = Stockage.nouvelId();
      final saisie = {
        'versementId': id, 'hanoutId': hanoutId, 'mode': mode, 'montant': montant,
        'chequeNumero': ?chequeNumero, 'chequeBanque': ?chequeBanque,
      };
      s.enfiler('VERSEMENT', saisie, deltas: {('SOLDE', hanoutId): -montant});
      s.enregistrer('VERSEMENT', id,
          {...saisie, 'imputations': [], 'recuLe': Stockage.maintenant(), 'ambulantId': utilisateurId}, local: true);
    });
  }

  void definirCredit(String hanoutId, {int? plafond, int? delaiMaxJours}) {
    s.transaction(() {
      s.enfiler('CREDIT_HANOUT', {'hanoutId': hanoutId, 'plafond': ?plafond, 'delaiMaxJours': ?delaiMaxJours});
      final c = compte(hanoutId) ??
          {'id': Stockage.nouvelId(), 'ambulant': {'id': utilisateurId, 'nom': nom}, 'hanout': {'id': hanoutId, 'nom': hanout(hanoutId)?['nom'] ?? ''}, 'solde': 0};
      s.enregistrer('COMPTE_CREDIT', c['id'] as String, {...c, 'plafond': plafond, 'delaiMaxJours': delaiMaxJours}, local: true);
    });
  }

  // ------------------------------------------------------------------ contestations (§6)
  /// Contestations ouvertes par les hanouts, reçues par la synchronisation.
  List<Map<String, dynamic>> contestations({String? hanoutId}) => s.objets('CONTESTATION',
      where: "${_j}statut') = 'OUVERTE'${hanoutId == null ? '' : " AND ${_j}hanout.id') = ?"}",
      args: [?hanoutId],
      orderBy: "${_j}creeLe') DESC");

  /// L'ambulant clôt une contestation, hors ligne (opération RESOLUTION_CONTESTATION).
  void resoudreContestation(String contestationId, {String? commentaire}) {
    s.transaction(() {
      s.enfiler('RESOLUTION_CONTESTATION', {'contestationId': contestationId, 'commentaire': ?commentaire});
      final c = s.objet('CONTESTATION', contestationId);
      if (c != null) {
        s.enregistrer('CONTESTATION', contestationId,
            {...c, 'statut': 'RESOLUE', 'resolueLe': Stockage.maintenant(), 'commentaireResolution': ?commentaire},
            local: true);
      }
    });
  }

  void definirPolitique(String politique) => _parametres({'politiqueDepassementCredit': politique});

  /// ICE, IF, RC et TVA (RG-F2) : présentes, elles permettent la FACTURE. Le contrat ne prévoit pas
  /// de les retirer (un champ absent garde la valeur serveur).
  void definirMentions(Map<String, dynamic> mentions) => _parametres({'mentionsLegales': mentions});

  void _parametres(Map<String, dynamic> saisie) {
    s.transaction(() {
      s.enfiler('PARAMETRES_AMBULANT', saisie);
      // Même ligne que celle du serveur, qui la remplacera au prochain tirage.
      final id = s.db.select("SELECT id FROM objet WHERE entite = 'PARAMETRES'").firstOrNull?['id'] as String? ?? 'local';
      s.enregistrer('PARAMETRES', id, {...parametres, ...saisie}, local: true);
    });
  }

  // ------------------------------------------------------------------ profil (§3.1)
  /// Profil reçu du serveur (entité PROFIL) ou saisi sur l'appareil ; null tant qu'il n'existe pas.
  Map<String, dynamic>? get profil => s.objets('PROFIL').firstOrNull;

  /// Motif du contrat (`ProfilAmbulantSaisie.cin`).
  static final motifCin = RegExp(r'^[A-Z]{1,2}[0-9]{1,6}$');

  /// Remplace tout le profil, hors ligne (opération PROFIL_AMBULANT) : un champ facultatif vide est effacé.
  void definirProfil({required String nom, String? cin, String? vehicule, Iterable<String> villes = const [], Iterable<String> categories = const []}) {
    String? net(String? v) => (v == null || v.trim().isEmpty) ? null : v.trim();
    final saisie = {
      'nom': nom.trim(),
      'cin': ?net(cin)?.toUpperCase(),
      'vehicule': ?net(vehicule),
      'villes': villes.toSet().toList(),
      'categories': categories.toSet().toList(),
    };
    if ((saisie['nom'] as String).isEmpty) throw ArgumentError.value(nom, 'nom');
    if (saisie['cin'] case final String c when !motifCin.hasMatch(c)) throw ArgumentError.value(cin, 'cin');
    s.transaction(() {
      final opId = s.enfiler('PROFIL_AMBULANT', saisie);
      // Horodatage du geste, celui que le serveur retiendra (ProfilAmbulant.modifieLe).
      final le = s.db.select('SELECT cree_le FROM operation WHERE id = ?', [opId]).first['cree_le'];
      s.enregistrer('PROFIL', utilisateurId, {...saisie, 'modifieLe': le}, local: true);
    });
  }

  /// Hanout absent de la base POS, créé sur le terrain (§3.3).
  Map<String, dynamic> creerHanout({required String nomHanout, String? gerant, String? telephone, String? adresse, String? ville, String? ice, Map<String, double>? position}) {
    final id = Stockage.nouvelId();
    final donnees = {
      'hanoutId': id, 'nom': nomHanout, 'gerant': ?gerant, 'telephone': ?telephone, 'adresse': ?adresse,
      'ville': ?ville, 'ice': ?ice, 'position': ?position,
    };
    return s.transaction(() {
      s.enfiler('HANOUT', donnees);
      final h = {...donnees..remove('hanoutId'), 'id': id};
      s.enregistrer('HANOUT', id, h, local: true);
      return h;
    });
  }

  // ------------------------------------------------------------------ chargement (§3.2)
  /// Lit la charge utile du QR d'un bon de sortie (JWS) sans vérifier la signature : le serveur le fera.
  static Map<String, dynamic> lireQrBonSortie(String jeton) {
    try {
      final partie = jeton.split('.')[1];
      return jsonDecode(utf8.decode(base64Url.decode(base64Url.normalize(partie)))) as Map<String, dynamic>;
    } catch (_) {
      throw const VenteRefusee(RefusVente.qrIllisible);
    }
  }

  void chargerBonSortie(String qrJeton) {
    final bon = lireQrBonSortie(qrJeton);
    if (bon['ambulantId'] != utilisateurId) throw const VenteRefusee(RefusVente.qrAutreAmbulant);
    final id = bon['bonSortieId'] as String;
    if (s.objet('BON_SORTIE', id)?['chargeLe'] != null) throw const VenteRefusee(RefusVente.bonDejaCharge);
    final lignes = (bon['lignes'] as List).cast<Map>();
    s.transaction(() {
      final opId = s.enfiler('CHARGEMENT', {'qrJeton': qrJeton},
          deltas: {for (final l in lignes) ('STOCK', l['produitId'] as String): l['quantite'] as int});
      s.enregistrer('BON_SORTIE', id, {...?s.objet('BON_SORTIE', id), ...bon, 'id': id, 'chargeLe': Stockage.maintenant()},
          local: true);
      _mouvementsLocaux(opId, 'CHARGEMENT', {for (final l in lignes) l['produitId'] as String: l['quantite'] as int},
          bonSortieId: id);
    });
  }

  void chargerManuel(Map<String, int> quantites, {String? grossisteId}) {
    s.transaction(() {
      final opId = s.enfiler('CHARGEMENT', {
        'grossisteId': ?grossisteId,
        'lignes': [for (final e in quantites.entries) {'produitId': e.key, 'quantite': e.value}],
      }, deltas: {for (final e in quantites.entries) ('STOCK', e.key): e.value});
      _mouvementsLocaux(opId, 'CHARGEMENT', quantites);
    });
  }

  /// Mouvements affichés tout de suite ; remplacés par ceux du serveur (même operationId) à la synchro.
  void _mouvementsLocaux(String opId, String type, Map<String, int> quantites, {String? documentId, String? bonSortieId}) {
    final maintenant = Stockage.maintenant();
    quantites.forEach((produitId, q) {
      final id = Stockage.nouvelId();
      s.enregistrer('MOUVEMENT', id, {
        'id': id, 'type': type, 'produitId': produitId, 'quantite': q, 'auteurId': utilisateurId,
        'appareilId': appareilId, 'horodatage': maintenant, 'recuLe': maintenant, 'operationId': opId,
        'documentId': ?documentId, 'bonSortieId': ?bonSortieId,
      }, local: true);
    });
  }
}

import 'package:daba_shared/daba_shared.dart';
import 'package:flutter/material.dart';
import 'package:share_plus/share_plus.dart';

import '../domaine.dart';
import '../pdf.dart';
import '../ui.dart';

/// Prise de commande rapide, hors ligne (§3.4) : bon de livraison par défaut, facture si le
/// profil a ses mentions légales, règlement espèces / chèque / crédit / partiel.
class EcranVente extends StatefulWidget {
  const EcranVente({super.key, required this.hanoutId, this.lignes = const [], this.demandeDepannageId});
  final String hanoutId;
  final List<LigneVente> lignes;
  final String? demandeDepannageId;

  @override
  State<EcranVente> createState() => _EcranVenteState();
}

class _EcranVenteState extends State<EcranVente> {
  late final List<LigneVente> _lignes = [...widget.lignes];
  String _type = 'BON_LIVRAISON';
  final _especes = TextEditingController();
  final _cheque = TextEditingController();
  final _chequeNumero = TextEditingController();
  final _chequeBanque = TextEditingController();
  bool _especesSaisies = false, _confirme = false;

  int get _total => _lignes.fold(0, (a, l) => a + l.brut - l.remise);

  /// Facture seulement avec les mentions légales, et pour un assujetti si chaque taux de TVA est connu (RG-F2).
  bool get _factureOk => context.daba.m.peutFacturer && !context.daba.m.tvaInconnue(_lignes);
  String get _typeEffectif => _factureOk ? _type : 'BON_LIVRAISON';
  int get _montantCheque => centimes(_cheque.text) ?? 0;
  int get _montantEspeces => _especesSaisies ? (centimes(_especes.text) ?? 0) : _total - _montantCheque;
  int get _credit => _total - _montantEspeces - _montantCheque;

  Future<void> _ajouterProduit() async {
    final p = await showModalBottomSheet<Map<String, dynamic>>(
      context: context,
      isScrollControlled: true,
      builder: (_) => const ChoixProduit(),
    );
    if (p == null || !mounted) return;
    final id = p['id'] as String;
    final existante = _lignes.where((l) => l.produitId == id).firstOrNull;
    if (existante != null) return setState(() => existante.quantite++);
    final prix = context.daba.m.prix(id, widget.hanoutId);
    final ligne = LigneVente(produitId: id, quantite: 1, prixUnitaire: prix.prix ?? 0, tarif: prix.tarif);
    setState(() => _lignes.add(ligne));
    if (prix.prix == null) await _modifier(ligne);
  }

  Future<void> _modifier(LigneVente l) async {
    final t = context.t;
    final prix = TextEditingController(text: (l.prixUnitaire / 100).toStringAsFixed(2));
    final remise = TextEditingController(text: l.remise == 0 ? '' : (l.remise / 100).toStringAsFixed(2));
    final ok = await showDialog<bool>(
      context: context,
      builder: (c) => AlertDialog(
        title: Text(context.lib(context.daba.m.produit(l.produitId)?['libelle'])),
        content: Column(mainAxisSize: MainAxisSize.min, children: [
          ChampMontant(controleur: prix, etiquette: t.prixUnitaire),
          ChampMontant(controleur: remise, etiquette: t.remise),
        ]),
        actions: [
          TextButton(onPressed: () => Navigator.pop(c, false), child: Text(t.annuler)),
          FilledButton(onPressed: () => Navigator.pop(c, true), child: Text(t.valider)),
        ],
      ),
    );
    if (ok != true) return;
    setState(() {
      final p = centimes(prix.text);
      if (p != null && p != l.prixUnitaire) {
        // Changer un prix connu = tarif négocié ; saisir le premier prix d'un produit reste le tarif standard.
        if (l.prixUnitaire != 0) l.tarif = 'NEGOCIE';
        l.prixUnitaire = p;
      }
      l.remise = centimes(remise.text) ?? 0;
    });
  }

  Future<void> _emettre({bool avertissementAccepte = false}) async {
    final t = context.t;
    final daba = context.daba;
    if (_lignes.isEmpty) return;
    if (_credit < 0) return context.message(t.reglementSuperieur);
    if (_montantCheque > 0 && _chequeNumero.text.trim().isEmpty) return context.message(t.champObligatoire(t.chequeNumero));
    try {
      final doc = daba.m.emettre(
        hanoutId: widget.hanoutId,
        lignes: _lignes,
        type: _typeEffectif,
        reglements: [
          if (_montantEspeces > 0) Reglement('ESPECES', _montantEspeces),
          if (_montantCheque > 0)
            Reglement('CHEQUE', _montantCheque, chequeNumero: _chequeNumero.text.trim(), chequeBanque: _chequeBanque.text.trim()),
        ],
        confirmeParHanout: _confirme,
        avertissementAccepte: avertissementAccepte,
        demandeDepannageId: widget.demandeDepannageId,
      );
      daba.apresGeste();
      if (mounted) Navigator.pushReplacement(context, MaterialPageRoute(builder: (_) => EcranDocument(doc['id'] as String)));
    } on VenteRefusee catch (e) {
      if (e.motif == RefusVente.creditAvertissement && await context.confirmer(t.avertissementCredit)) {
        return _emettre(avertissementAccepte: true);
      }
      if (mounted && e.motif != RefusVente.creditAvertissement) context.erreur(e);
    }
  }

  @override
  Widget build(BuildContext context) {
    final t = context.t;
    final m = context.daba.m;
    final h = m.hanout(widget.hanoutId);
    final style = Theme.of(context).textTheme;
    return Scaffold(
      appBar: AppBar(title: Text(t.venteA(h?['nom'] as String? ?? ''))),
      floatingActionButton: FloatingActionButton(heroTag: 'ajout', onPressed: _ajouterProduit, child: const Icon(Icons.add_shopping_cart)),
      body: ListView(padding: const EdgeInsets.only(bottom: 120), children: [
        ListTile(dense: true, title: Text(t.solde), trailing: Text(dh(m.solde(widget.hanoutId)))),
        if (_lignes.isEmpty) Vide(t.panierVide, icone: Icons.add_shopping_cart),
        for (final l in _lignes) _ligne(l),
        const Divider(),
        ListTile(title: Text(t.total, style: style.titleMedium), trailing: Text(dh(_total), style: style.titleLarge)),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: SegmentedButton<String>(
            segments: [
              ButtonSegment(value: 'BON_LIVRAISON', label: Text(t.bonLivraison)),
              ButtonSegment(value: 'FACTURE', label: Text(t.facture), enabled: _factureOk),
            ],
            selected: {_typeEffectif},
            onSelectionChanged: (s) => setState(() => _type = s.first),
          ),
        ),
        if (!_factureOk)
          Padding(
            padding: const EdgeInsets.all(8),
            child: Text(m.peutFacturer ? t.erreurFactureTvaInconnue : t.factureIndisponible, style: style.bodySmall),
          ),
        Padding(
          padding: const EdgeInsets.all(16),
          child: Column(children: [
            ChampMontant(
              controleur: _especes,
              etiquette: t.especes,
              indice: _especesSaisies ? null : (_montantEspeces / 100).toStringAsFixed(2),
              onChanged: (v) => setState(() => _especesSaisies = v.isNotEmpty),
            ),
            ChampMontant(controleur: _cheque, etiquette: t.cheque, onChanged: (_) => setState(() {})),
            if (_montantCheque > 0) ...[
              TextField(controller: _chequeNumero, decoration: InputDecoration(labelText: t.chequeNumero)),
              TextField(controller: _chequeBanque, decoration: InputDecoration(labelText: t.chequeBanque)),
            ],
            const SizedBox(height: 8),
            Row(children: [
              Expanded(child: Text(t.resteACredit, style: style.titleMedium)),
              Text(dh(_credit), style: style.titleMedium?.copyWith(color: _credit > 0 ? Colors.orange.shade800 : null)),
            ]),
            Wrap(spacing: 8, children: [
              ActionChip(label: Text(t.toutEspeces), onPressed: () => setState(() {
                _especesSaisies = false;
                _especes.clear();
                _cheque.clear();
              })),
              ActionChip(label: Text(t.toutCredit), onPressed: () => setState(() {
                _especesSaisies = true;
                _especes.text = '0';
                _cheque.clear();
              })),
            ]),
          ]),
        ),
        CheckboxListTile(value: _confirme, onChanged: (v) => setState(() => _confirme = v!), title: Text(t.confirmationHanout)),
        Padding(
          padding: const EdgeInsets.all(16),
          child: FilledButton.icon(
            onPressed: _lignes.isEmpty ? null : _emettre,
            icon: const Icon(Icons.receipt_long),
            label: Text(_typeEffectif == 'FACTURE' ? t.emettreFacture : t.emettreBon),
          ),
        ),
      ]),
    );
  }

  Widget _ligne(LigneVente l) {
    final m = context.daba.m;
    final p = m.produit(l.produitId);
    final stock = m.stockTotal(l.produitId);
    final t = context.t;
    return ListTile(
      leading: PhotoProduit(p),
      title: Text(context.lib(p?['libelle'])),
      subtitle: Text([
        '${dh(l.prixUnitaire)}${l.tarif == 'NEGOCIE' ? ' · ${t.tarifNegocie}' : ''}',
        if (l.remise > 0) '${t.remise} ${dh(-l.remise)}',
        if (l.quantite > stock) t.stockInsuffisant('$stock'),
      ].join(' · ')),
      onTap: () => _modifier(l),
      trailing: Row(mainAxisSize: MainAxisSize.min, children: [
        IconButton(
          icon: const Icon(Icons.remove_circle_outline),
          onPressed: () => setState(() => l.quantite > 1 ? l.quantite-- : _lignes.remove(l)),
        ),
        Text('${l.quantite}', style: Theme.of(context).textTheme.titleMedium),
        IconButton(icon: const Icon(Icons.add_circle_outline), onPressed: () => setState(() => l.quantite++)),
      ]),
    );
  }
}

class ChoixProduit extends StatefulWidget {
  const ChoixProduit({super.key});

  @override
  State<ChoixProduit> createState() => ChoixProduitState();
}

class ChoixProduitState extends State<ChoixProduit> {
  String _q = '';

  @override
  Widget build(BuildContext context) {
    final m = context.daba.m;
    // Produits en stock d'abord.
    final produits = m.produits(q: _q).map((p) => (p, m.stockTotal(p['id'] as String))).toList()
      ..sort((a, b) => (b.$2 > 0 ? 1 : 0).compareTo(a.$2 > 0 ? 1 : 0));
    return SizedBox(
      height: MediaQuery.sizeOf(context).height * .8,
      child: Column(children: [
        Padding(
          padding: const EdgeInsets.all(12),
          child: TextField(
            autofocus: true,
            decoration: InputDecoration(prefixIcon: const Icon(Icons.search), hintText: context.t.rechercherProduit),
            onChanged: (v) => setState(() => _q = v),
          ),
        ),
        Expanded(
          child: produits.isEmpty
              ? Vide(context.t.aucunProduit)
              : ListView.builder(
                  itemCount: produits.length,
                  itemBuilder: (_, i) => ListTile(
                    leading: PhotoProduit(produits[i].$1),
                    title: Text(context.lib(produits[i].$1['libelle'])),
                    subtitle: Text('${produits[i].$1['marque']} · ${produits[i].$1['ean']}'),
                    trailing: Text('${produits[i].$2}'),
                    onTap: () => Navigator.pop(context, produits[i].$1),
                  ),
                ),
        ),
      ]),
    );
  }
}

/// Bon de livraison ou facture émis : détail et partage du PDF (WhatsApp ou autre, en manuel).
class EcranDocument extends StatelessWidget {
  const EcranDocument(this.id, {super.key});
  final String id;

  @override
  Widget build(BuildContext context) {
    final t = context.t;
    final doc = context.daba.stockage.objet('DOCUMENT', id)!;
    final style = Theme.of(context).textTheme;
    return Scaffold(
      appBar: AppBar(title: Text(doc['numero'] as String)),
      body: ListView(padding: const EdgeInsets.all(16), children: [
        Text(nomType(t, doc['type'] as String), style: style.titleLarge),
        Text('${doc['hanout']['nom']} · ${date(doc['emisLe'] as String)}'),
        if (doc['statut'] != 'EMIS')
          Text(doc['statut'] == 'ANNULE' ? t.docAnnule : t.docPartiellementAnnule, style: TextStyle(color: Colors.red.shade700)),
        if (doc['rejete'] == true) Text(t.docRejete, style: TextStyle(color: Colors.red.shade700)),
        ..._reception(context, doc),
        const Divider(),
        for (final l in (doc['lignes'] as List).cast<Map>())
          ListTile(
            dense: true,
            title: Text(context.lib(l['libelle'])),
            subtitle: Text([
              if (l['nature'] == 'ARTICLE') '${l['quantite']} × ${dh(l['prixUnitaire'] as int)}' else nomNature(t, l['nature'] as String),
              if (l['campagneNom'] != null) '${t.promo} ${context.lib(l['campagneNom'])}',
            ].join(' · ')),
            trailing: Text(dh(l['montant'] as int)),
          ),
        const Divider(),
        ListTile(title: Text(t.total), trailing: Text(dh(doc['total'] as int), style: style.titleLarge)),
        if (doc['tva'] != null) ListTile(dense: true, title: Text(t.tvaComprise), trailing: Text(dh(doc['tva'] as int))),
        ListTile(dense: true, title: Text(t.regle), trailing: Text(dh(doc['montantRegle'] as int))),
        ListTile(dense: true, title: Text(t.resteACredit), trailing: Text(dh(doc['montantCredit'] as int))),
        const SizedBox(height: 16),
        FilledButton.icon(
          onPressed: () async {
            final fichier = await documentPdf(doc, t, context.langue);
            await SharePlus.instance.share(ShareParams(files: [XFile(fichier.path, mimeType: 'application/pdf')], text: doc['numero'] as String));
          },
          icon: const Icon(Icons.share),
          label: Text(t.partagerPdf),
        ),
        const SizedBox(height: 8),
        OutlinedButton.icon(onPressed: () => _envoyerLien(context), icon: const Icon(Icons.sms_outlined), label: Text(t.envoyerLienSms)),
        const SizedBox(height: 8),
        Text(t.smsApresSync, style: style.bodySmall, textAlign: TextAlign.center),
      ]),
    );
  }

  /// Lien signé du document (en ligne), partagé à la main vers l'app SMS.
  Future<void> _envoyerLien(BuildContext context) async {
    final t = context.t;
    try {
      final lien = await context.daba.api.get('/v1/documents/$id/lien') as Map<String, dynamic>;
      await SharePlus.instance.share(ShareParams(text: lien['url'] as String));
    } on ProblemeApi catch (e) {
      if (!context.mounted) return;
      context.message(switch (e.status) {
        404 => t.erreurDocNonSynchronise,
        422 => t.erreurHanoutSansTelephone,
        _ => messageErreur(t, e),
      });
    } catch (e) {
      if (context.mounted) context.erreur(e);
    }
  }

  /// Réception confirmée par le hanout dans sa PWA, ou écart signalé ligne par ligne (§6).
  List<Widget> _reception(BuildContext context, Map<String, dynamic> doc) {
    final r = doc['receptionHanout'] as Map?;
    if (r == null) return const [];
    final t = context.t;
    if (r['conforme'] == true) return [Text(t.receptionConforme(date(r['le'] as String)), style: const TextStyle(color: Colors.green))];
    final lignes = (doc['lignes'] as List).cast<Map>();
    return [
      Text(t.receptionEcart, style: TextStyle(color: Colors.orange.shade800)),
      for (final e in ((r['ecarts'] as List?) ?? const []).cast<Map>())
        if ((e['ligne'] as int) < lignes.length)
          Text(t.receptionEcartLigne(context.lib(lignes[e['ligne'] as int]['libelle']), '${e['quantiteRecue']}',
              '${lignes[e['ligne'] as int]['quantite']}')),
      if (r['commentaire'] case final String c) Text(c),
    ];
  }
}

String nomType(T t, String type) => switch (type) {
      'FACTURE' => t.facture,
      'AVOIR' => t.avoir,
      _ => t.bonLivraison,
    };

String nomNature(T t, String nature) => switch (nature) {
      'REMISE_PROMO' => t.remisePromo,
      _ => t.remise,
    };

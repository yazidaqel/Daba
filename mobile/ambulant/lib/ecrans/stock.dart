import 'package:daba_shared/daba_shared.dart';
import 'package:flutter/material.dart';
import 'package:phosphoricons_flutter/phosphoricons_flutter.dart';
import 'package:mobile_scanner/mobile_scanner.dart';

import '../domaine.dart';
import '../ui.dart';
import 'vente.dart';

/// Stock embarqué (§3.2) : quantités, chargement par QR du bon de sortie ou saisie manuelle,
/// mouvements. Tout fonctionne hors ligne.
class OngletStock extends StatelessWidget {
  const OngletStock({super.key});

  @override
  Widget build(BuildContext context) {
    final t = context.t;
    final m = context.daba.m;
    final stock = m.stockEmbarque();
    final aCharger = context.daba.stockage
        .objets('BON_SORTIE', where: "json_extract(donnees, '\$.chargeLe') IS NULL", orderBy: "json_extract(donnees, '\$.emisLe') DESC");
    return ListView(padding: const EdgeInsets.only(bottom: 24), children: [
      Padding(
        padding: const EdgeInsets.all(12),
        child: Wrap(spacing: 8, runSpacing: 8, children: [
          FilledButton.icon(onPressed: () => scannerBon(context), icon: const Ico(PhosphorIconsRegular.scan), label: Text(t.scannerBonSortie)),
          OutlinedButton.icon(
              onPressed: () => context.ouvrir(const EcranChargementManuel()), icon: const Ico(PhosphorIconsRegular.notePencil), label: Text(t.chargementManuel)),
          OutlinedButton.icon(
              onPressed: () => context.ouvrir(const EcranMouvements()), icon: const Ico(PhosphorIconsRegular.arrowsDownUp), label: Text(t.mouvements)),
          OutlinedButton.icon(
              onPressed: () => context.ouvrir(const EcranRetour()), icon: const Icon(PhosphorIconsRegular.arrowUUpLeft), label: Text(t.retours)),
          OutlinedButton.icon(
              onPressed: () => context.ouvrir(const EcranInventaire()),
              icon: const Ico(PhosphorIconsRegular.listChecks),
              label: Text(t.inventaireFinJournee)),
        ]),
      ),
      for (final b in aCharger)
        Card(
          margin: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
          child: ListTile(
            leading: const Ico(PhosphorIconsRegular.truck),
            title: Text(t.bonACharger(b['numero'] as String? ?? '')),
            subtitle: Text(date(b['emisLe'] as String?)),
            trailing: TextButton(onPressed: () => chargerBon(context, b['qrJeton'] as String), child: Text(t.charger)),
          ),
        ),
      if (stock.isEmpty) Vide(t.stockVide, icone: PhosphorIconsRegular.package),
      for (final (p, q) in stock)
        ListTile(
          leading: PhotoProduit(p),
          title: Text(context.lib(p['libelle'])),
          subtitle: Row(children: [
            Flexible(child: Text(p['marque'] as String? ?? '')),
            if (m.stockPromo(p['id'] as String).any((x) => x.quantite > 0)) ...[
              const SizedBox(width: 8),
              BadgePromo('${t.promo} ${m.stockPromo(p['id'] as String).fold(0, (a, x) => a + x.quantite)}'),
            ],
          ]),
          // Rupture : icône et libellé, pas seulement la couleur (charte).
          trailing: q <= 0 ? Statut(Ton.danger, '${t.rupture} · $q') : Text('$q', style: Theme.of(context).textTheme.titleLarge),
        ),
    ]);
  }
}

Future<void> scannerBon(BuildContext context) async {
  final jeton = await context.ouvrir<String>(EcranScan(titre: context.t.scannerBonSortie));
  if (jeton != null && context.mounted) await chargerBon(context, jeton);
}

/// Affiche les lignes lues dans le QR, puis charge le stock (opération CHARGEMENT).
Future<void> chargerBon(BuildContext context, String jeton) async {
  final t = context.t;
  final daba = context.daba;
  try {
    final bon = Ambulant.lireQrBonSortie(jeton);
    final lignes = (bon['lignes'] as List).cast<Map>();
    final texte = [
      t.chargerCeBon,
      for (final l in lignes) '• ${context.lib(daba.m.produit(l['produitId'] as String)?['libelle'] ?? l['produitId'])} × ${l['quantite']}',
    ].join('\n');
    if (!await context.confirmer(texte)) return;
    daba.m.chargerBonSortie(jeton);
    daba.apresGeste();
    if (context.mounted) context.message(t.stockCharge);
  } on VenteRefusee catch (e) {
    if (context.mounted) context.erreur(e);
  }
}

class EcranScan extends StatelessWidget {
  const EcranScan({super.key, required this.titre});
  final String titre;

  @override
  Widget build(BuildContext context) {
    var lu = false;
    return Scaffold(
      appBar: AppBar(title: Text(titre)),
      body: MobileScanner(
        controller: MobileScannerController(formats: const [BarcodeFormat.qrCode]),
        onDetect: (capture) {
          final v = capture.barcodes.firstOrNull?.rawValue;
          if (v == null || lu) return;
          lu = true;
          Navigator.pop(context, v);
        },
      ),
    );
  }
}

class EcranChargementManuel extends StatefulWidget {
  const EcranChargementManuel({super.key});

  @override
  State<EcranChargementManuel> createState() => _EcranChargementManuelState();
}

class _EcranChargementManuelState extends State<EcranChargementManuel> {
  final _quantites = <String, int>{};

  Future<void> _ajouter() async {
    if (await choisirProduit(context) case final id?) setState(() => _quantites.update(id, (q) => q + 1, ifAbsent: () => 1));
  }

  @override
  Widget build(BuildContext context) {
    final t = context.t;
    return Scaffold(
      appBar: AppBar(title: Text(t.chargementManuel)),
      floatingActionButton: FloatingActionButton(heroTag: 'ajout-chargement', onPressed: _ajouter, child: const Ico(PhosphorIconsRegular.plus)),
      body: ListView(children: [
        if (_quantites.isEmpty) Vide(t.ajouterProduits),
        ...lignesQuantites(context, _quantites),
        Padding(
          padding: const EdgeInsets.all(16),
          child: FilledButton(
            onPressed: _quantites.isEmpty
                ? null
                : () {
                    final lignes = {for (final e in _quantites.entries) if (e.value > 0) e.key: e.value};
                    if (lignes.isEmpty) return;
                    context.daba.m.chargerManuel(lignes);
                    context.daba.apresGeste();
                    Navigator.pop(context);
                    context.message(t.stockCharge);
                  },
            child: Text(t.charger),
          ),
        ),
      ]),
    );
  }
}

class EcranMouvements extends StatelessWidget {
  const EcranMouvements({super.key});

  @override
  Widget build(BuildContext context) {
    final t = context.t;
    final m = context.daba.m;
    final mouvements = m.mouvements();
    final types = {
      'CHARGEMENT': t.mvtChargement, 'VENTE': t.mvtVente, 'RETOUR': t.mvtRetour, 'AJUSTEMENT': t.mvtAjustement, 'PROMO': t.mvtPromo,
    };
    return Scaffold(
      appBar: AppBar(title: Text(t.mouvements)),
      body: mouvements.isEmpty
          ? Vide(t.aucunMouvement)
          : ListView.builder(
              itemCount: mouvements.length,
              itemBuilder: (_, i) {
                final mv = mouvements[i];
                final q = mv['quantite'] as int;
                return ListTile(
                  leading: Ico(q >= 0 ? PhosphorIconsRegular.arrowDownLeft : PhosphorIconsRegular.arrowUpRight, color: q >= 0 ? Zellige.of(context).success : Zellige.of(context).textMuted),
                  title: Text(context.lib(m.produit(mv['produitId'] as String)?['libelle'])),
                  subtitle: Text('${types[mv['type']] ?? mv['type']} · ${date(mv['horodatage'] as String)}'),
                  trailing: Text(q > 0 ? '+$q' : '$q', style: Theme.of(context).textTheme.titleMedium),
                );
              },
            ),
    );
  }
}

Future<String?> choisirProduit(BuildContext context, {String? hanoutId}) async =>
    (await showModalBottomSheet<Map<String, dynamic>>(
        context: context, isScrollControlled: true, builder: (_) => ChoixProduit(hanoutId: hanoutId)))?['id'] as String?;

/// Lignes produit × quantité saisissables (chargement manuel, retour).
List<Widget> lignesQuantites(BuildContext context, Map<String, int> quantites) => [
      for (final e in quantites.entries)
        ListTile(
          key: ValueKey(e.key),
          title: Text(context.lib(context.daba.m.produit(e.key)?['libelle'])),
          trailing: SizedBox(
            width: 90,
            child: TextFormField(
              initialValue: '${e.value}',
              keyboardType: TextInputType.number,
              textAlign: TextAlign.center,
              onChanged: (v) => quantites[e.key] = int.tryParse(v) ?? 0,
            ),
          ),
        ),
    ];

/// Retour d'un hanout vers l'ambulant ou de l'ambulant vers le grossiste, avec motif (§3.2), hors
/// ligne (opération RETOUR). Depuis la fiche d'un hanout, le sens et le hanout sont fixés.
class EcranRetour extends StatefulWidget {
  const EcranRetour({super.key, this.hanoutId});
  final String? hanoutId;

  @override
  State<EcranRetour> createState() => _EcranRetourState();
}

class _EcranRetourState extends State<EcranRetour> {
  late bool _versGrossiste = widget.hanoutId == null;
  late String? _hanoutId = widget.hanoutId;
  String? _grossisteId;
  String _motif = 'ABIME';
  bool _remisEnVente = false;
  final _quantites = <String, int>{};

  Future<void> _ajouter() async {
    if (await choisirProduit(context) case final id?) setState(() => _quantites.update(id, (q) => q + 1, ifAbsent: () => 1));
  }

  Future<void> _choisirHanout() async {
    final id = await showModalBottomSheet<String>(context: context, isScrollControlled: true, builder: (_) => const _ChoixHanout());
    if (id != null) setState(() => _hanoutId = id);
  }

  void _enregistrer() {
    final t = context.t;
    final daba = context.daba;
    if (_versGrossiste && _grossisteId == null) return context.message(t.champObligatoire(t.grossiste));
    if (!_versGrossiste && _hanoutId == null) return context.message(t.choisirHanout);
    if (!_quantites.values.any((q) => q > 0)) return context.message(t.ajouterProduits);
    daba.m.retour(
      versGrossiste: _versGrossiste,
      motif: _motif,
      quantites: _quantites,
      hanoutId: _versGrossiste ? null : _hanoutId,
      grossisteId: _versGrossiste ? _grossisteId : null,
      remisEnVente: _remisEnVente,
    );
    daba.apresGeste();
    Navigator.pop(context);
    context.message(t.retourEnregistre);
  }

  @override
  Widget build(BuildContext context) {
    final t = context.t;
    final m = context.daba.m;
    final grossistes = m.grossistes();
    final motifs = {'ABIME': t.retourABIME, 'PERIME': t.retourPERIME, 'INVENDU': t.retourINVENDU, 'AUTRE': t.retourAUTRE};
    return Scaffold(
      appBar: AppBar(title: Text(t.retours)),
      floatingActionButton: FloatingActionButton(heroTag: 'ajout-retour', onPressed: _ajouter, child: const Ico(PhosphorIconsRegular.plus)),
      body: ListView(padding: const EdgeInsets.only(bottom: 96), children: [
        if (widget.hanoutId == null)
          Padding(
            padding: const EdgeInsets.all(12),
            child: SegmentedButton<bool>(
              segments: [
                ButtonSegment(value: false, label: Text(t.retourHanout)),
                ButtonSegment(value: true, label: Text(t.retourGrossiste)),
              ],
              selected: {_versGrossiste},
              onSelectionChanged: (s) => setState(() => _versGrossiste = s.first),
            ),
          ),
        if (_versGrossiste)
          grossistes.isEmpty
              ? Padding(padding: const EdgeInsets.all(16), child: Statut(Ton.avertissement, t.aucunGrossiste))
              : Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16),
                  child: DropdownButtonFormField<String>(
                    initialValue: _grossisteId,
                    decoration: InputDecoration(labelText: t.grossiste),
                    items: [
                      for (final g in grossistes.entries)
                        DropdownMenuItem(value: g.key, child: Text(g.value ?? t.grossisteCode(g.key.substring(0, 8)))),
                    ],
                    onChanged: (v) => setState(() => _grossisteId = v),
                  ),
                )
        else ...[
          ListTile(
            leading: const Ico(PhosphorIconsRegular.storefront),
            title: Text(_hanoutId == null ? t.choisirHanout : (m.hanout(_hanoutId!)?['nom'] as String? ?? '')),
            trailing: widget.hanoutId == null ? const Icon(PhosphorIconsRegular.caretRight) : null,
            onTap: widget.hanoutId == null ? _choisirHanout : null,
          ),
          SwitchListTile(value: _remisEnVente, onChanged: (v) => setState(() => _remisEnVente = v), title: Text(t.remisEnVente)),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Text(t.retourAvoir, style: Theme.of(context).textTheme.bodySmall),
          ),
        ],
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 0),
          child: Text(t.motif, style: Theme.of(context).textTheme.titleSmall),
        ),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 12),
          child: Wrap(spacing: 8, children: [
            for (final e in motifs.entries)
              ChoiceChip(label: Text(e.value), selected: _motif == e.key, onSelected: (_) => setState(() => _motif = e.key)),
          ]),
        ),
        const Divider(),
        if (_quantites.isEmpty) Vide(t.ajouterProduits),
        ...lignesQuantites(context, _quantites),
        Padding(
          padding: const EdgeInsets.all(16),
          child: FilledButton(onPressed: _quantites.isEmpty ? null : _enregistrer, child: Text(t.enregistrer)),
        ),
      ]),
    );
  }
}

class _ChoixHanout extends StatefulWidget {
  const _ChoixHanout();

  @override
  State<_ChoixHanout> createState() => _ChoixHanoutState();
}

class _ChoixHanoutState extends State<_ChoixHanout> {
  String _q = '';

  @override
  Widget build(BuildContext context) {
    final hanouts = context.daba.m.hanouts(q: _q);
    return SizedBox(
      height: MediaQuery.sizeOf(context).height * .8,
      child: Column(children: [
        Padding(
          padding: const EdgeInsets.all(12),
          child: TextField(
            autofocus: true,
            decoration: InputDecoration(prefixIcon: const Ico(PhosphorIconsRegular.magnifyingGlass), hintText: context.t.rechercherHanout),
            onChanged: (v) => setState(() => _q = v),
          ),
        ),
        Expanded(
          child: ListView.builder(
            itemCount: hanouts.length,
            itemBuilder: (_, i) => ListTile(
              leading: const Ico(PhosphorIconsRegular.storefront),
              title: Text(hanouts[i]['nom'] as String),
              subtitle: Text((hanouts[i]['adresse'] ?? '') as String),
              onTap: () => Navigator.pop(context, hanouts[i]['id'] as String),
            ),
          ),
        ),
      ]),
    );
  }
}

/// Inventaire de fin de journée du stock normal (§3.2) : quantité comptée, écart au stock attendu,
/// motif exigé au-delà du seuil fixé par l'administrateur (RG-S4). Hors ligne (opération INVENTAIRE).
class EcranInventaire extends StatefulWidget {
  const EcranInventaire({super.key});

  @override
  State<EcranInventaire> createState() => _EcranInventaireState();
}

class _EcranInventaireState extends State<EcranInventaire> {
  late final _produits = [for (final (p, _) in context.dabaLu.m.stockEmbarque()) p['id'] as String];
  final _comptes = <String, TextEditingController>{};
  final _motifs = <String, TextEditingController>{};

  TextEditingController _c(Map<String, TextEditingController> m, String id) => m.putIfAbsent(id, TextEditingController.new);

  @override
  void dispose() {
    for (final c in [..._comptes.values, ..._motifs.values]) {
      c.dispose();
    }
    super.dispose();
  }

  Future<void> _ajouter() async {
    final id = await choisirProduit(context);
    if (id != null && !_produits.contains(id)) setState(() => _produits.add(id));
  }

  void _enregistrer() {
    final t = context.t;
    final daba = context.daba;
    final lignes = {
      for (final id in _produits)
        if (int.tryParse(_c(_comptes, id).text.trim()) case final n? when n >= 0) id: (compte: n, motif: _c(_motifs, id).text),
    };
    if (lignes.isEmpty) return context.message(t.ajouterProduits);
    try {
      daba.m.inventaire(lignes);
    } on VenteRefusee catch (e) {
      return context.erreur(e);
    }
    daba.apresGeste();
    Navigator.pop(context);
    context.message(t.inventaireEnregistre);
  }

  @override
  Widget build(BuildContext context) {
    final t = context.t;
    final m = context.daba.m;
    return Scaffold(
      appBar: AppBar(title: Text(t.inventaireFinJournee)),
      floatingActionButton: FloatingActionButton(heroTag: 'ajout-inventaire', onPressed: _ajouter, child: const Ico(PhosphorIconsRegular.plus)),
      body: ListView(padding: const EdgeInsets.only(bottom: 96), children: [
        Padding(padding: const EdgeInsets.all(16), child: Text(t.inventaireAide)),
        if (_produits.isEmpty) Vide(t.ajouterProduits),
        for (final id in _produits) _ligne(context, id, m.stock(id)),
        Padding(
          padding: const EdgeInsets.all(16),
          child: FilledButton(onPressed: _produits.isEmpty ? null : _enregistrer, child: Text(t.enregistrer)),
        ),
      ]),
    );
  }

  Widget _ligne(BuildContext context, String id, int attendu) {
    final t = context.t;
    final m = context.daba.m;
    final compte = int.tryParse(_c(_comptes, id).text.trim());
    final ecart = compte == null ? 0 : compte - attendu;
    final requis = compte != null && Ambulant.motifRequis(compte, attendu, m.seuilEcart);
    return Column(key: ValueKey(id), children: [
      ListTile(
        leading: PhotoProduit(m.produit(id)),
        title: Text(context.lib(m.produit(id)?['libelle'])),
        subtitle: Text([t.attendu('$attendu'), if (ecart != 0) t.ecart(ecart > 0 ? '+$ecart' : '$ecart')].join(' · ')),
        trailing: SizedBox(
          width: 90,
          child: TextField(
            controller: _c(_comptes, id),
            keyboardType: TextInputType.number,
            textAlign: TextAlign.center,
            decoration: InputDecoration(hintText: '$attendu', labelText: t.compte),
            onChanged: (_) => setState(() {}),
          ),
        ),
      ),
      if (requis)
        Padding(
          padding: const EdgeInsetsDirectional.fromSTEB(72, 0, 16, 8),
          child: TextField(
            controller: _c(_motifs, id),
            maxLength: 500,
            decoration: InputDecoration(labelText: t.motifEcart, helperText: t.motifObligatoire),
          ),
        ),
    ]);
  }
}

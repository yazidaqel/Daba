import 'package:daba_shared/daba_shared.dart';
import 'package:flutter/material.dart';
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
          FilledButton.icon(onPressed: () => scannerBon(context), icon: const Icon(Icons.qr_code_scanner), label: Text(t.scannerBonSortie)),
          OutlinedButton.icon(
              onPressed: () => context.ouvrir(const EcranChargementManuel()), icon: const Icon(Icons.edit_note), label: Text(t.chargementManuel)),
          OutlinedButton.icon(
              onPressed: () => context.ouvrir(const EcranMouvements()), icon: const Icon(Icons.swap_vert), label: Text(t.mouvements)),
        ]),
      ),
      for (final b in aCharger)
        Card(
          margin: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
          child: ListTile(
            leading: const Icon(Icons.local_shipping_outlined),
            title: Text(t.bonACharger(b['numero'] as String? ?? '')),
            subtitle: Text(date(b['emisLe'] as String?)),
            trailing: TextButton(onPressed: () => chargerBon(context, b['qrJeton'] as String), child: Text(t.charger)),
          ),
        ),
      if (stock.isEmpty) Vide(t.stockVide, icone: Icons.inventory_2_outlined),
      for (final (p, q) in stock)
        ListTile(
          leading: PhotoProduit(p),
          title: Text(context.lib(p['libelle'])),
          subtitle: Text([
            p['marque'],
            if (m.stockPromo(p['id'] as String).any((x) => x.quantite > 0))
              '${t.promo} ${m.stockPromo(p['id'] as String).fold(0, (a, x) => a + x.quantite)}',
          ].join(' · ')),
          trailing: Text('$q',
              style: Theme.of(context).textTheme.titleLarge?.copyWith(color: q <= 0 ? Theme.of(context).colorScheme.error : null)),
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
    final p = await showModalBottomSheet<Map<String, dynamic>>(
        context: context, isScrollControlled: true, builder: (_) => const ChoixProduit());
    if (p != null) setState(() => _quantites.update(p['id'] as String, (q) => q + 1, ifAbsent: () => 1));
  }

  @override
  Widget build(BuildContext context) {
    final t = context.t;
    final m = context.daba.m;
    return Scaffold(
      appBar: AppBar(title: Text(t.chargementManuel)),
      floatingActionButton: FloatingActionButton(heroTag: 'ajout-chargement', onPressed: _ajouter, child: const Icon(Icons.add)),
      body: ListView(children: [
        if (_quantites.isEmpty) Vide(t.ajouterProduits),
        for (final e in _quantites.entries)
          ListTile(
            title: Text(context.lib(m.produit(e.key)?['libelle'])),
            trailing: SizedBox(
              width: 90,
              child: TextFormField(
                initialValue: '${e.value}',
                keyboardType: TextInputType.number,
                textAlign: TextAlign.center,
                onChanged: (v) => _quantites[e.key] = int.tryParse(v) ?? 0,
              ),
            ),
          ),
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
                  leading: Icon(q >= 0 ? Icons.south_west : Icons.north_east, color: q >= 0 ? Colors.green : Colors.orange),
                  title: Text(context.lib(m.produit(mv['produitId'] as String)?['libelle'])),
                  subtitle: Text('${types[mv['type']] ?? mv['type']} · ${date(mv['horodatage'] as String)}'),
                  trailing: Text(q > 0 ? '+$q' : '$q', style: Theme.of(context).textTheme.titleMedium),
                );
              },
            ),
    );
  }
}

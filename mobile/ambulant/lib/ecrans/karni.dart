import 'package:daba_shared/daba_shared.dart';
import 'package:flutter/material.dart';
import 'package:phosphoricons_flutter/phosphoricons_flutter.dart';

import '../domaine.dart';
import '../ui.dart';
import 'hanouts.dart';
import 'vente.dart';

/// Karni digital (§3.5) : soldes par hanout, total des créances, versements, plafonds.
class OngletKarni extends StatelessWidget {
  const OngletKarni({super.key});

  @override
  Widget build(BuildContext context) {
    final t = context.t;
    final m = context.daba.m;
    final comptes = m.comptes().where((c) => c.$2 != 0 || m.compte(c.$1['id'] as String)?['plafond'] != null).toList();
    final total = comptes.fold<int>(0, (a, c) => a + c.$2);
    final contestations = m.contestations().length;
    return ListView(children: [
      if (contestations > 0)
        ListTile(
          leading: Ico(PhosphorIconsRegular.gavel, color: Zellige.of(context).warning),
          title: Text(t.contestationsOuvertes('$contestations')),
          trailing: const Icon(PhosphorIconsRegular.caretRight),
          onTap: () => context.ouvrir(const EcranContestations()),
        ),
      Card(
        margin: const EdgeInsets.all(12),
        child: ListTile(
          title: Text(t.totalCreances),
          trailing: Text(dh(total), style: Theme.of(context).textTheme.headlineSmall),
        ),
      ),
      if (comptes.isEmpty) Vide(t.aucuneCreance, icone: PhosphorIconsRegular.notebook),
      for (final (h, solde) in comptes) _ligne(context, h['id'] as String, h['nom'] as String, solde),
    ]);
  }

  Widget _ligne(BuildContext context, String id, String nom, int solde) {
    final m = context.daba.m;
    // Un crédit supplémentaire serait-il hors plafond ou hors délai ? (RG-C3)
    final t = context.t;
    final depassement = m.controleCredit(id, 1);
    final plafond = m.compte(id)?['plafond'] as int?;
    return ListTile(
      leading: const Ico(PhosphorIconsRegular.storefront),
      title: Text(nom),
      subtitle: depassement == Depassement.aucun
          ? (plafond == null ? null : Text(t.plafondValeur(dh(plafond))))
          : Align(alignment: AlignmentDirectional.centerStart, child: Statut(Ton.danger, libelleDepassement(t, depassement))),
      trailing: Text(dh(solde), style: Theme.of(context).textTheme.titleMedium),
      onTap: () => context.ouvrir(EcranHanout(id)),
    );
  }
}

/// Versement d'un hanout, imputé FIFO par le serveur (RG-C2).
Future<void> saisirVersement(BuildContext context, String hanoutId) async {
  final t = context.t;
  final daba = context.daba;
  final montant = TextEditingController();
  final numero = TextEditingController();
  var mode = 'ESPECES';
  final ok = await showDialog<bool>(
    context: context,
    builder: (c) => StatefulBuilder(
      builder: (c, setState) => AlertDialog(
        title: Text(t.versement),
        content: Column(mainAxisSize: MainAxisSize.min, children: [
          Text(t.soldeActuel(dh(daba.m.solde(hanoutId)))),
          ChampMontant(controleur: montant, etiquette: t.montant),
          SegmentedButton<String>(
            segments: [ButtonSegment(value: 'ESPECES', label: Text(t.especes)), ButtonSegment(value: 'CHEQUE', label: Text(t.cheque))],
            selected: {mode},
            onSelectionChanged: (s) => setState(() => mode = s.first),
          ),
          if (mode == 'CHEQUE') TextField(controller: numero, decoration: InputDecoration(labelText: t.chequeNumero)),
        ]),
        actions: [
          TextButton(onPressed: () => Navigator.pop(c, false), child: Text(t.annuler)),
          FilledButton(onPressed: () => Navigator.pop(c, true), child: Text(t.enregistrer)),
        ],
      ),
    ),
  );
  final c = centimes(montant.text);
  if (ok != true || c == null || c <= 0) return;
  daba.m.versement(hanoutId, c, mode: mode, chequeNumero: mode == 'CHEQUE' ? numero.text.trim() : null);
  daba.apresGeste();
}

/// Plafond et délai maximal de crédit du hanout (§3.5).
Future<void> reglerCredit(BuildContext context, String hanoutId) async {
  final t = context.t;
  final daba = context.daba;
  final compte = daba.m.compte(hanoutId);
  final plafond = TextEditingController(text: compte?['plafond'] == null ? '' : ((compte!['plafond'] as int) / 100).toStringAsFixed(0));
  final delai = TextEditingController(text: '${compte?['delaiMaxJours'] ?? ''}');
  final ok = await showDialog<bool>(
    context: context,
    builder: (c) => AlertDialog(
      title: Text(t.plafondCredit),
      content: Column(mainAxisSize: MainAxisSize.min, children: [
        ChampMontant(controleur: plafond, etiquette: t.plafond),
        TextField(controller: delai, keyboardType: TextInputType.number, decoration: InputDecoration(labelText: t.delaiJours)),
      ]),
      actions: [
        TextButton(onPressed: () => Navigator.pop(c, false), child: Text(t.annuler)),
        FilledButton(onPressed: () => Navigator.pop(c, true), child: Text(t.enregistrer)),
      ],
    ),
  );
  if (ok != true) return;
  final j = int.tryParse(delai.text.trim());
  daba.m.definirCredit(hanoutId, plafond: centimes(plafond.text), delaiMaxJours: j != null && j > 0 ? j : null);
  daba.apresGeste();
}

/// Une ligne d'historique : document de vente ou versement.
class LigneHistorique extends StatelessWidget {
  const LigneHistorique(this.e, {super.key});
  final Map<String, dynamic> e;

  @override
  Widget build(BuildContext context) {
    final t = context.t;
    if (e['numero'] == null) {
      return ListTile(
        leading: Ico(PhosphorIconsRegular.money, color: Zellige.of(context).success),
        title: Text('${t.versement} · ${e['mode'] == 'CHEQUE' ? t.cheque : t.especes}'),
        subtitle: Text(date(e['recuLe'] as String)),
        trailing: Text(dh(-(e['montant'] as int))),
      );
    }
    final rejete = e['rejete'] == true;
    return ListTile(
      leading: const Ico(PhosphorIconsRegular.receipt),
      title: Text('${nomType(t, e['type'] as String)} ${e['numero']}'),
      subtitle: rejete
          ? Align(alignment: AlignmentDirectional.centerStart, child: Statut(Ton.danger, t.docRejete))
          : Text('${date(e['emisLe'] as String)} · ${t.total} ${dh(e['total'] as int)}'),
      trailing: Text(dh(e['montantCredit'] as int)),
      onTap: () => context.ouvrir(EcranDocument(e['id'] as String)),
    );
  }
}

/// Contestations ouvertes par les hanouts (document ou solde, §6), closes hors ligne par l'ambulant.
class EcranContestations extends StatelessWidget {
  const EcranContestations({super.key, this.hanoutId});
  final String? hanoutId;

  @override
  Widget build(BuildContext context) {
    final t = context.t;
    final daba = context.daba;
    final liste = daba.m.contestations(hanoutId: hanoutId);
    return Scaffold(
      appBar: AppBar(title: Text(t.contestations)),
      body: liste.isEmpty
          ? Vide(t.aucuneContestation)
          : ListView(children: [for (final c in liste) CarteContestation(c)]),
    );
  }
}

class CarteContestation extends StatelessWidget {
  const CarteContestation(this.c, {super.key});
  final Map<String, dynamic> c;

  @override
  Widget build(BuildContext context) {
    final t = context.t;
    final daba = context.daba;
    final doc = c['cible'] == 'DOCUMENT' ? daba.stockage.objet('DOCUMENT', c['cibleId'] as String) : null;
    return Card(
      margin: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
      child: ListTile(
        leading: Ico(PhosphorIconsRegular.gavel, color: Zellige.of(context).warning),
        title: Text('${c['hanout']['nom']} · ${nomMotif(t, c['motif'] as String)}'),
        subtitle: Text([
          if (c['cible'] == 'DOCUMENT') (doc?['numero'] ?? t.document) as String else t.compteCredit,
          date(c['creeLe'] as String),
          if (c['commentaire'] case final String x) x,
        ].join(' · ')),
        trailing: TextButton(onPressed: () => _clore(context), child: Text(t.clore)),
        onTap: doc == null ? null : () => context.ouvrir(EcranDocument(doc['id'] as String)),
      ),
    );
  }

  Future<void> _clore(BuildContext context) async {
    final t = context.t;
    final daba = context.daba;
    final commentaire = TextEditingController();
    final ok = await showDialog<bool>(
      context: context,
      builder: (d) => AlertDialog(
        title: Text(t.clore),
        content: TextField(controller: commentaire, maxLength: 500, decoration: InputDecoration(labelText: t.commentaire)),
        actions: [
          TextButton(onPressed: () => Navigator.pop(d, false), child: Text(t.annuler)),
          FilledButton(onPressed: () => Navigator.pop(d, true), child: Text(t.clore)),
        ],
      ),
    );
    if (ok != true) return;
    final texte = commentaire.text.trim();
    daba.m.resoudreContestation(c['id'] as String, commentaire: texte.isEmpty ? null : texte);
    daba.apresGeste();
  }
}

/// RG-C3 : libellé du dépassement, affiché avec son icône (jamais par la couleur seule).
String libelleDepassement(T t, Depassement d) => switch (d) {
      Depassement.plafond => t.plafondDepasse,
      Depassement.delai => t.delaiDepasse,
      _ => '${t.plafondDepasse} · ${t.delaiDepasse}',
    };

String nomMotif(T t, String motif) => switch (motif) {
      'MONTANT' => t.motifMONTANT,
      'QUANTITE' => t.motifQUANTITE,
      'NON_RECU' => t.motifNON_RECU,
      'DEJA_PAYE' => t.motifDEJA_PAYE,
      _ => t.motifAUTRE,
    };

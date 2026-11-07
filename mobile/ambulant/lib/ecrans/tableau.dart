import 'package:daba_shared/daba_shared.dart';
import 'package:flutter/material.dart';
import 'package:phosphoricons_flutter/phosphoricons_flutter.dart';

import '../ui.dart';

/// Tableau de bord (§3.8), calculé sur le téléphone : chiffre d'affaires, encaissé et crédit accordé
/// du jour, de la semaine et du mois, puis caisse de fin de journée. La marge attend les prix d'achat.
class EcranTableauDeBord extends StatefulWidget {
  const EcranTableauDeBord({super.key});

  @override
  State<EcranTableauDeBord> createState() => _EcranTableauDeBordState();
}

class _EcranTableauDeBordState extends State<EcranTableauDeBord> {
  final _declare = TextEditingController();

  @override
  void dispose() {
    _declare.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final t = context.t;
    final m = context.daba.m;
    final style = Theme.of(context).textTheme;
    final now = DateTime.now();
    final jour = DateTime(now.year, now.month, now.day);
    final periodes = {
      t.aujourdhui: jour,
      t.cetteSemaine: jour.subtract(Duration(days: jour.weekday - 1)),
      t.ceMois: DateTime(now.year, now.month),
    };
    final attendues = m.especesAttendues(jour);
    final declare = m.caisseDeclaree(jour);
    return Scaffold(
      appBar: AppBar(title: Text(t.tableauDeBord)),
      body: ListView(padding: const EdgeInsets.all(12), children: [
        for (final p in periodes.entries)
          Card(
            child: Padding(
              padding: const EdgeInsets.all(12),
              child: Builder(builder: (_) {
                final a = m.activite(p.value);
                return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Text(p.key, style: style.titleMedium),
                  _ligne(t.chiffreAffaires, a.ca, style.titleLarge),
                  _ligne(t.encaisse, a.encaisse, style.bodyLarge),
                  _ligne(t.creditAccorde, a.credit, style.bodyLarge),
                ]);
              }),
            ),
          ),
        Card(
          child: Padding(
            padding: const EdgeInsets.all(12),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Row(children: [const Ico(PhosphorIconsRegular.cashRegister), const SizedBox(width: 8), Expanded(child: Text(t.caisseDuJour, style: style.titleMedium))]),
              _ligne(t.especesAttendues, attendues, style.titleLarge),
              if (declare != null) ...[
                _ligne(t.especesDeclarees, declare, style.bodyLarge),
                declare == attendues
                    ? Statut(Ton.succes, t.caisseJuste)
                    : Statut(Ton.avertissement, t.ecartCaisse(dh(declare - attendues))),
              ],
              const SizedBox(height: 8),
              Row(children: [
                Expanded(child: ChampMontant(controleur: _declare, etiquette: t.especesDeclarees)),
                const SizedBox(width: 8),
                FilledButton(
                  onPressed: () {
                    final c = centimes(_declare.text);
                    if (c == null || c < 0) return;
                    setState(() => m.declarerCaisse(jour, c));
                    _declare.clear();
                  },
                  child: Text(t.enregistrer),
                ),
              ]),
              Text(t.caisseLocale, style: style.bodySmall),
            ]),
          ),
        ),
      ]),
    );
  }

  Widget _ligne(String libelle, int montant, TextStyle? style) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 2),
        child: Row(children: [Expanded(child: Text(libelle)), Text(dh(montant), style: style)]),
      );
}

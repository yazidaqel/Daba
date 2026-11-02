import 'package:daba_shared/daba_shared.dart';
import 'package:flutter/material.dart';
import 'package:phosphoricons_flutter/phosphoricons_flutter.dart';

import '../etat.dart';
import '../ui.dart';

/// État de la file : opérations en attente, opérations refusées ou en conflit (à afficher,
/// contrat `ResultatOperation`), anomalies signalées par le serveur.
class EcranSynchro extends StatefulWidget {
  const EcranSynchro({super.key});

  @override
  State<EcranSynchro> createState() => _EcranSynchroState();
}

class _EcranSynchroState extends State<EcranSynchro> {
  bool _occupe = false;

  @override
  Widget build(BuildContext context) {
    final t = context.t;
    final daba = context.daba;
    final signalees = daba.stockage.operationsSignalees();
    final anomalies = daba.stockage.objets('ANOMALIE',
        where: "json_extract(donnees, '\$.statut') = 'OUVERTE'", orderBy: "json_extract(donnees, '\$.detecteeLe') DESC");
    return Scaffold(
      appBar: AppBar(title: Text(t.synchronisation)),
      body: ListView(children: [
        ListTile(
          leading: Ico(daba.horsLigne ? PhosphorIconsRegular.cloudSlash : PhosphorIconsRegular.cloudCheck),
          title: Text(daba.horsLigne ? t.horsLigne : t.enLigne),
          subtitle: Text(daba.derniereSync == null ? t.jamaisSynchronise : t.derniereSync(date(daba.derniereSync!.toUtc().toIso8601String()))),
        ),
        ListTile(leading: const Ico(PhosphorIconsRegular.hourglass), title: Text(t.operationsEnAttente('${daba.enAttente}'))),
        Padding(
          padding: const EdgeInsets.all(16),
          child: FilledButton.icon(
            onPressed: _occupe
                ? null
                : () async {
                    setState(() => _occupe = true);
                    await daba.synchroniser();
                    if (mounted) setState(() => _occupe = false);
                  },
            icon: const Ico(PhosphorIconsRegular.arrowsClockwise),
            label: Text(t.synchroniserMaintenant),
          ),
        ),
        if (signalees.isNotEmpty) ListTile(title: Text(t.operationsRefusees, style: Theme.of(context).textTheme.titleMedium)),
        for (final o in signalees)
          ListTile(
            leading: Ico(o['statut'] == 'REJETEE' ? PhosphorIconsRegular.prohibit : PhosphorIconsRegular.arrowsSplit, color: Colors.red.shade700),
            title: Text('${nomOperation(t, o['type'] as String)} · ${date(o['creeLe'] as String)}'),
            subtitle: Text((o['resultat']['erreur']?['detail'] ?? o['resultat']['conflit']?['detail'] ?? o['statut']) as String),
          ),
        if (anomalies.isNotEmpty) ListTile(title: Text(t.anomalies, style: Theme.of(context).textTheme.titleMedium)),
        for (final a in anomalies)
          ListTile(
            leading: const Ico(PhosphorIconsRegular.warningCircle, color: Colors.orange),
            title: Text('${a['regle']} · ${nomAnomalie(t, a['type'] as String)}'),
            subtitle: Text('${a['detail'] ?? ''} ${date(a['detecteeLe'] as String)}'),
          ),
      ]),
    );
  }
}

String nomOperation(T t, String type) => switch (type) {
      'CHARGEMENT' => t.opCHARGEMENT,
      'RETOUR' => t.opRETOUR,
      'INVENTAIRE' => t.opINVENTAIRE,
      'EMISSION_DOCUMENT' => t.opEMISSION_DOCUMENT,
      'AVOIR' => t.opAVOIR,
      'VERSEMENT' => t.opVERSEMENT,
      'TARIF' => t.opTARIF,
      'HANOUT' => t.opHANOUT,
      'CREDIT_HANOUT' => t.opCREDIT_HANOUT,
      'RESOLUTION_CONTESTATION' => t.opRESOLUTION_CONTESTATION,
      _ => t.opPARAMETRES_AMBULANT,
    };

String nomAnomalie(T t, String type) => switch (type) {
      'STOCK_NEGATIF' => t.anoSTOCK_NEGATIF,
      'ECART_SANS_MOTIF' => t.anoECART_SANS_MOTIF,
      'NUMEROTATION_TROU' => t.anoNUMEROTATION_TROU,
      'NUMERO_EN_DOUBLE' => t.anoNUMERO_EN_DOUBLE,
      'PLAFOND_CREDIT_DEPASSE' => t.anoPLAFOND_CREDIT_DEPASSE,
      'DELAI_CREDIT_DEPASSE' => t.anoDELAI_CREDIT_DEPASSE,
      'CHARGEMENT_EN_DOUBLE' => t.anoCHARGEMENT_EN_DOUBLE,
      'PROMO_SANS_RETRAIT' => t.anoPROMO_SANS_RETRAIT,
      'DEMANDE_NON_ATTRIBUEE' => t.anoDEMANDE_NON_ATTRIBUEE,
      'TVA_INCONNUE' => t.anoTVA_INCONNUE,
      _ => t.anoREMISE_PLAFOND_DEPASSE,
    };

class EcranParametres extends StatelessWidget {
  const EcranParametres({super.key});

  @override
  Widget build(BuildContext context) {
    final t = context.t;
    final daba = context.daba;
    final politique = daba.m.parametres['politiqueDepassementCredit'] as String;
    return Scaffold(
      appBar: AppBar(title: Text(t.parametres)),
      body: ListView(children: [
        ListTile(title: Text(t.politiqueCredit), subtitle: Text(t.politiqueCreditExplication)),
        RadioGroup<String>(
          groupValue: politique,
          onChanged: (v) {
            if (v == null || v == politique) return;
            daba.m.definirPolitique(v);
            daba.apresGeste();
          },
          child: Column(children: [
            RadioListTile(value: 'AVERTIR', title: Text(t.politiqueAvertir)),
            RadioListTile(value: 'BLOQUER', title: Text(t.politiqueBloquer)),
          ]),
        ),
        const Divider(),
        SwitchListTile(
          value: daba.consentGeolocalisation,
          title: Text(t.consentementGeo),
          onChanged: (v) async {
            try {
              await daba.consentir(geolocalisation: v);
            } catch (e) {
              if (context.mounted) context.erreur(e);
            }
          },
        ),
        const Divider(),
        ListTile(title: Text(t.appareil), subtitle: Text('${daba.m.prefixe} · ${daba.appareilId}')),
        ListTile(title: Text(t.versionCndp), subtitle: const Text(versionTexteCndp)),
      ]),
    );
  }
}

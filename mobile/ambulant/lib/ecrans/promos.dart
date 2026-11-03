import 'package:daba_shared/daba_shared.dart';
import 'package:flutter/material.dart';
import 'package:phosphoricons_flutter/phosphoricons_flutter.dart';
import 'package:qr_flutter/qr_flutter.dart';

import '../ui.dart';

/// Fil des promos (§3.6), livré par la synchro et consultable hors ligne ; la réservation exige le réseau.
class OngletPromos extends StatelessWidget {
  const OngletPromos({super.key});

  @override
  Widget build(BuildContext context) {
    final t = context.t;
    final daba = context.daba;
    final offres = daba.stockage.objets('PROMO', orderBy: "json_extract(donnees, '\$.fin')");
    final nbReservees = daba.stockage
        .objets('RESERVATION', where: "json_extract(donnees, '\$.statut') = 'RESERVEE'")
        .length;
    return RefreshIndicator(
      onRefresh: daba.synchroniser,
      child: ListView(children: [
        ListTile(
          leading: const Ico(PhosphorIconsRegular.qrCode),
          title: Text(t.mesReservations),
          trailing: nbReservees == 0 ? const Icon(PhosphorIconsRegular.caretRight) : BadgePromo('$nbReservees'),
          onTap: () => context.ouvrir(const EcranReservations()),
        ),
        const Divider(height: 1),
        if (offres.isEmpty) Vide(t.aucunePromo, icone: PhosphorIconsRegular.tag),
        for (final o in offres)
          ListTile(
            leading: PhotoProduit(o['produit'] as Map<String, dynamic>),
            title: Text(context.lib(o['produit']['libelle'])),
            // Fil promo : la remise en badge safran (charte : le safran est réservé aux promos).
            subtitle: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Padding(padding: const EdgeInsets.symmetric(vertical: 4), child: BadgePromo(descriptionRemise(context, o['remise'] as Map))),
              Text([
              o['grossiste']['nom'],
              ?distanceGrossiste(context, o),
              t.jusquAu(date(o['fin'] as String)),
            ].join(' · ')),
            ]),
            trailing: Text('${o['quantiteDisponible']}'),
            onTap: () => context.ouvrir(EcranOffre(o)),
          ),
      ]),
    );
  }
}

String descriptionRemise(BuildContext context, Map r) {
  final t = context.t;
  return switch (r['type']) {
    'POURCENTAGE' => ltr(t.remisePourcent('${r['pourcentage']}')),
    'PRIX_NET' => t.remisePrixNet(dh(r['prixNet'] as int)),
    'PRODUIT_OFFERT' => t.remiseOfferte('${r['quantiteAchetee']}', '${r['quantiteOfferte']}'),
    'PALIER' => t.remisePalier((r['paliers'] as List).map((p) => ltr('${p['quantiteMin']}+ : ${p['pourcentage']} %')).join(', ')),
    _ => '',
  };
}

/// Distance calculée sur le téléphone, seulement si la position est connue (ambulant disponible).
String? distanceGrossiste(BuildContext context, Map o) {
  final pos = context.daba.position;
  final g = o['grossiste']['position'] as Map?;
  if (pos == null || g == null) return null;
  return distance(distanceM(pos.latitude, pos.longitude, (g['lat'] as num).toDouble(), (g['lon'] as num).toDouble()));
}

class EcranOffre extends StatefulWidget {
  const EcranOffre(this.offre, {super.key});
  final Map<String, dynamic> offre;

  @override
  State<EcranOffre> createState() => _EcranOffreState();
}

class _EcranOffreState extends State<EcranOffre> {
  int _quantite = 1;
  bool _occupe = false;
  String? _idReservation; // gardé entre deux essais : la réservation est idempotente

  int get _max {
    final o = widget.offre;
    final restant = (o['quantiteMaxParAmbulant'] as int) - (o['quantiteDejaReservee'] as int); // RG-P3
    final dispo = o['quantiteDisponible'] as int;
    return restant < dispo ? restant : dispo;
  }

  Future<void> _reserver() async {
    setState(() => _occupe = true);
    _idReservation ??= Stockage.nouvelId();
    try {
      final r = await context.daba.reserver(widget.offre['id'] as String, _quantite, id: _idReservation);
      if (mounted) Navigator.pushReplacement(context, MaterialPageRoute(builder: (_) => EcranReservation(r)));
    } catch (e) {
      if (mounted) context.erreur(e);
    } finally {
      if (mounted) setState(() => _occupe = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final t = context.t;
    final o = widget.offre;
    final g = o['grossiste'] as Map;
    return Scaffold(
      appBar: AppBar(title: Text(context.lib(o['campagneNom'] ?? o['produit']['libelle']))),
      body: ListView(padding: const EdgeInsets.all(16), children: [
        Row(children: [
          PhotoProduit(o['produit'] as Map<String, dynamic>, taille: 72),
          const SizedBox(width: 12),
          Expanded(child: Text(context.lib(o['produit']['libelle']), style: Theme.of(context).textTheme.titleLarge)),
        ]),
        const SizedBox(height: 12),
        ListTile(
          leading: Ico(PhosphorIconsRegular.sealPercent, color: Zellige.of(context).promo),
          title: Align(alignment: AlignmentDirectional.centerStart, child: BadgePromo(descriptionRemise(context, o['remise'] as Map))),
        ),
        if (o['prixReventeConseille'] != null)
          ListTile(leading: const Ico(PhosphorIconsRegular.storefront), title: Text(t.prixConseille(dh(o['prixReventeConseille'] as int)))),
        ListTile(
          leading: const Ico(PhosphorIconsRegular.warehouse),
          title: Text(g['nom'] as String),
          subtitle: Text([g['adresse'], g['ville'], ?distanceGrossiste(context, o)].whereType<String>().join(' · ')),
        ),
        ListTile(leading: const Ico(PhosphorIconsRegular.package), title: Text(t.quantiteRestante('${o['quantiteDisponible']}'))),
        ListTile(
          leading: const Ico(PhosphorIconsRegular.user),
          title: Text(t.plafondAmbulant('${o['quantiteMaxParAmbulant']}', '${o['quantiteDejaReservee']}')),
        ),
        ListTile(leading: const Ico(PhosphorIconsRegular.calendar), title: Text(t.jusquAu(date(o['fin'] as String)))),
        ListTile(leading: const Ico(PhosphorIconsRegular.timer), title: Text(t.delaiRetrait('${o['delaiRetraitHeures']}'))),
        const Divider(),
        if (_max <= 0)
          Vide(t.plafondAtteint)
        else ...[
          Row(mainAxisAlignment: MainAxisAlignment.center, children: [
            IconButton(onPressed: _quantite > 1 ? () => setState(() => _quantite--) : null, icon: const Ico(PhosphorIconsRegular.minusCircle)),
            Text('$_quantite', style: Theme.of(context).textTheme.headlineSmall),
            IconButton(onPressed: _quantite < _max ? () => setState(() => _quantite++) : null, icon: const Ico(PhosphorIconsRegular.plusCircle)),
          ]),
          FilledButton.icon(
            onPressed: _occupe ? null : _reserver,
            icon: const Ico(PhosphorIconsRegular.bookmarkSimple),
            label: Text(t.reserver),
          ),
          Padding(padding: const EdgeInsets.all(8), child: Text(t.reservationEnLigne, textAlign: TextAlign.center)),
        ],
      ]),
    );
  }
}

class EcranReservations extends StatelessWidget {
  const EcranReservations({super.key});

  @override
  Widget build(BuildContext context) {
    final t = context.t;
    final daba = context.daba;
    final reservations = daba.stockage.objets('RESERVATION', orderBy: "json_extract(donnees, '\$.reserveeLe') DESC", limite: 100);
    return Scaffold(
      appBar: AppBar(title: Text(t.mesReservations)),
      body: reservations.isEmpty
          ? Vide(t.aucuneReservation)
          : ListView(children: [
              for (final r in reservations)
                ListTile(
                  leading: Ico(r['statut'] == 'RESERVEE' ? PhosphorIconsRegular.qrCode : PhosphorIconsRegular.checkCircle),
                  title: Text(context.lib(daba.m.produit(r['produitId'] as String)?['libelle'])),
                  subtitle: Text('${statutReservation(context, r['statut'] as String)} · ${r['grossiste']['nom']} · ${r['quantiteReservee']}'),
                  onTap: () => context.ouvrir(EcranReservation(r)),
                ),
            ]),
    );
  }
}

String statutReservation(BuildContext context, String s) {
  final t = context.t;
  return switch (s) {
    'RESERVEE' => t.statutReservee,
    'RETIREE' => t.statutRetiree,
    'EXPIREE' => t.statutExpiree,
    _ => t.statutRevendue,
  };
}

/// QR code à présenter au grossiste pour le retrait (RG-P4) ; affichable hors ligne.
class EcranReservation extends StatelessWidget {
  const EcranReservation(this.r, {super.key});
  final Map<String, dynamic> r;

  @override
  Widget build(BuildContext context) {
    final t = context.t;
    // Hors ligne, l'heure du téléphone suffit pour ne plus présenter un QR expiré (RG-P2).
    final expiree = this.r['statut'] == 'RESERVEE' && DateTime.parse(this.r['expireLe'] as String).isBefore(DateTime.now());
    final r = expiree ? {...this.r, 'statut': 'EXPIREE'} : this.r;
    final qr = r['qrJeton'] as String?;
    return Scaffold(
      appBar: AppBar(title: Text(t.reservation)),
      body: ListView(padding: const EdgeInsets.all(24), children: [
        if (qr != null && r['statut'] == 'RESERVEE')
          Center(
            child: QrImageView(
              data: qr,
              size: 260,
              // Toujours sombre sur clair, même en mode sombre, pour le scan du grossiste.
              backgroundColor: Zellige.clair.surface,
              eyeStyle: QrEyeStyle(eyeShape: QrEyeShape.square, color: Zellige.clair.text),
              dataModuleStyle: QrDataModuleStyle(dataModuleShape: QrDataModuleShape.square, color: Zellige.clair.text),
            ),
          ),
        const SizedBox(height: 16),
        Text(context.lib(context.daba.m.produit(r['produitId'] as String)?['libelle']),
            style: Theme.of(context).textTheme.titleLarge, textAlign: TextAlign.center),
        ListTile(title: Text(t.quantite), trailing: Text('${r['quantiteReservee']}')),
        ListTile(title: Text(t.statut), trailing: Text(statutReservation(context, r['statut'] as String))),
        ListTile(title: Text(r['grossiste']['nom'] as String), subtitle: Text((r['grossiste']['adresse'] ?? '') as String)),
        if (r['statut'] == 'RESERVEE') ListTile(title: Text(t.aRetirerAvant(date(r['expireLe'] as String)))),
      ]),
    );
  }
}

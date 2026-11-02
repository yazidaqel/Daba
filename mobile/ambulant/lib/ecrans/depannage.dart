import 'package:daba_shared/daba_shared.dart';
import 'package:flutter/material.dart';
import 'package:phosphoricons_flutter/phosphoricons_flutter.dart';

import '../domaine.dart';
import '../ui.dart';
import 'vente.dart';

/// Dépannage sur rupture (§3.7, §7.1). Disponibilité, acceptation, refus et « en route » exigent
/// le réseau (RG-D2 arbitré par le serveur) ; la livraison est une vente, enregistrée hors ligne.
class OngletDepannage extends StatefulWidget {
  const OngletDepannage({super.key});

  @override
  State<OngletDepannage> createState() => _OngletDepannageState();
}

class _OngletDepannageState extends State<OngletDepannage> {
  bool _occupe = false;

  Future<void> _faire(Future<void> Function() action) async {
    setState(() => _occupe = true);
    try {
      await action();
    } catch (e) {
      if (mounted) context.erreur(e);
    } finally {
      if (mounted) setState(() => _occupe = false);
    }
  }

  Future<void> _basculer(bool actif) async {
    final daba = context.daba;
    final t = context.t;
    if (actif && !daba.consentGeolocalisation) {
      if (!await context.confirmer(t.consentementGeoDemande)) return;
      await _faire(() => daba.consentir(geolocalisation: true));
      if (!daba.consentGeolocalisation) return;
    }
    await _faire(() => daba.changerDisponibilite(actif));
  }

  Future<void> _accepter(String id) {
    final daba = context.daba;
    final prise = context.t.demandePriseParAutre;
    return _faire(() async {
      try {
        await daba.agirDemande(id, 'acceptation');
      } on ProblemeApi catch (e) {
        if (e.status != 409) rethrow;
        // RG-D2 : un autre ambulant l'a prise.
        daba.demandes.removeWhere((d) => d['id'] == id);
        if (mounted) context.message(prise);
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final t = context.t;
    final daba = context.daba;
    return RefreshIndicator(
      onRefresh: () => _faire(daba.chargerDemandes),
      child: ListView(children: [
        SwitchListTile(
          value: daba.disponible,
          onChanged: _occupe ? null : _basculer,
          title: Text(daba.disponible ? t.disponible : t.indisponible),
          subtitle: Text(t.disponibleExplication),
          secondary: Ico(PhosphorIconsRegular.moped, color: daba.disponible ? Zellige.of(context).success : null),
        ),
        if (_occupe) const LinearProgressIndicator(),
        const Divider(),
        if (daba.demandes.isEmpty) Vide(daba.disponible ? t.aucuneDemande : t.activerPourRecevoir, icone: PhosphorIconsRegular.bell),
        for (final d in daba.demandes) _carte(d),
      ]),
    );
  }

  Widget _carte(Map<String, dynamic> d) {
    final t = context.t;
    final daba = context.daba;
    final h = d['hanout'] as Map<String, dynamic>?;
    final pos = daba.position;
    final hp = h?['position'] as Map?;
    final dist = pos == null || hp == null
        ? (h?['distanceM'] as int?)
        : distanceM(pos.latitude, pos.longitude, (hp['lat'] as num).toDouble(), (hp['lon'] as num).toDouble());
    final statut = d['statut'] as String;
    final id = d['id'] as String;
    return Card(
      margin: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Row(children: [
            PhotoProduit(d['produit'] as Map<String, dynamic>?),
            const SizedBox(width: 8),
            Expanded(
              child: Text('${context.lib(d['produit']?['libelle'] ?? t.produitEnPhoto)} × ${d['quantite']}',
                  style: Theme.of(context).textTheme.titleMedium),
            ),
            Chip(label: Text(statutDemande(t, statut))),
          ]),
          if (d['photoUrl'] case final String url)
            Image.network(url, height: 120, cacheHeight: 240, errorBuilder: (_, _, _) => const SizedBox()),
          if (h != null)
            Text([
              h['nom'],
              if (dist != null) distance(dist),
              if (h['solde'] != null) '${t.solde} ${dh(h['solde'] as int)}',
              if (h['derniereVenteLe'] != null) t.derniereVente(date(h['derniereVenteLe'] as String)),
            ].join(' · ')),
          if (d['expireLe'] != null && statut == 'EN_ATTENTE') Text(t.repondreAvant(date(d['expireLe'] as String))),
          const SizedBox(height: 8),
          Wrap(spacing: 8, children: switch (statut) {
            'EN_ATTENTE' => [
                FilledButton.tonal(onPressed: _occupe ? null : () => _accepter(id), child: Text(t.accepter)),
                OutlinedButton(onPressed: _occupe ? null : () => _faire(() => daba.agirDemande(id, 'refus')), child: Text(t.refuser)),
              ],
            'ACCEPTEE' => [FilledButton.tonal(onPressed: _occupe ? null : () => _faire(() => daba.agirDemande(id, 'en-route')), child: Text(t.enRoute))],
            'EN_ROUTE' when h != null => [FilledButton.tonalIcon(onPressed: () => _livrer(d), icon: const Ico(PhosphorIconsRegular.cashRegister), label: Text(t.livrer))],
            _ => const <Widget>[],
          }),
        ]),
      ),
    );
  }

  /// RG-D4 : la livraison est une vente liée à la demande, au comptant ou à crédit.
  void _livrer(Map<String, dynamic> d) {
    final m = context.daba.m;
    final hanoutId = d['hanout']['id'] as String;
    final produitId = d['produit']?['id'] as String?;
    if (produitId != null) context.daba.stockage.enregistrer('PRODUIT', produitId, {...?m.produit(produitId), ...d['produit']});
    final prix = produitId == null ? null : m.prix(produitId, hanoutId);
    context.ouvrir(EcranVente(
      hanoutId: hanoutId,
      demandeDepannageId: d['id'] as String,
      lignes: [
        if (produitId != null)
          LigneVente(produitId: produitId, quantite: d['quantite'] as int, prixUnitaire: prix?.prix ?? 0, tarif: prix!.tarif),
      ],
    ));
  }
}

String statutDemande(T t, String s) => switch (s) {
      'EN_ATTENTE' => t.statutEnAttente,
      'ACCEPTEE' => t.statutAcceptee,
      'EN_ROUTE' => t.statutEnRoute,
      'LIVREE' => t.statutLivree,
      _ => t.statutSansReponse,
    };

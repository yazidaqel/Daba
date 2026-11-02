import 'package:daba_shared/daba_shared.dart';
import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:latlong2/latlong.dart';

import '../ui.dart';
import 'karni.dart';
import 'vente.dart';

const _casablanca = LatLng(33.5731, -7.5898);

/// Hanouts de la base POS (synchronisés) et créés sur le terrain : recherche, carte, fiche (§3.3).
class OngletHanouts extends StatefulWidget {
  const OngletHanouts({super.key});

  @override
  State<OngletHanouts> createState() => _OngletHanoutsState();
}

class _OngletHanoutsState extends State<OngletHanouts> {
  String _q = '';
  bool _carte = false;

  @override
  Widget build(BuildContext context) {
    final t = context.t;
    final daba = context.daba;
    final pos = daba.position; // connue seulement quand l'ambulant est disponible (CNDP)
    var hanouts = daba.m.hanouts(q: _q);
    int? dist(Map h) => pos == null || h['position'] == null
        ? null
        : distanceM(pos.latitude, pos.longitude, (h['position']['lat'] as num).toDouble(), (h['position']['lon'] as num).toDouble());
    if (pos != null) hanouts = [...hanouts]..sort((a, b) => (dist(a) ?? 1 << 30).compareTo(dist(b) ?? 1 << 30));

    return Scaffold(
      floatingActionButton: FloatingActionButton.extended(
        heroTag: 'nouveau-hanout',
        onPressed: () => context.ouvrir(const EcranNouveauHanout()),
        icon: const Icon(Icons.add_business_outlined),
        label: Text(t.nouveauHanout),
      ),
      body: Column(children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(12, 8, 12, 4),
          child: Row(children: [
            Expanded(
              child: TextField(
                decoration: InputDecoration(prefixIcon: const Icon(Icons.search), hintText: t.rechercherHanout, isDense: true),
                onChanged: (v) => setState(() => _q = v),
              ),
            ),
            IconButton(
              tooltip: _carte ? t.liste : t.carte,
              onPressed: () => setState(() => _carte = !_carte),
              icon: Icon(_carte ? Icons.list : Icons.map_outlined),
            ),
          ]),
        ),
        Expanded(
          child: hanouts.isEmpty
              ? Vide(t.aucunHanout, icone: Icons.storefront_outlined)
              : _carte
                  ? _Carte(hanouts: hanouts, centre: pos == null ? null : LatLng(pos.latitude, pos.longitude))
                  : ListView.builder(
                      itemCount: hanouts.length,
                      itemBuilder: (_, i) {
                        final h = hanouts[i];
                        final d = dist(h);
                        final solde = daba.m.solde(h['id'] as String);
                        return ListTile(
                          leading: const CircleAvatar(child: Icon(Icons.storefront)),
                          title: Text(h['nom'] as String),
                          subtitle: Text([h['gerant'], h['adresse'], if (d != null) distance(d)].whereType<String>().join(' · ')),
                          trailing: solde == 0 ? null : Text(dh(solde)),
                          onTap: () => context.ouvrir(EcranHanout(h['id'] as String)),
                        );
                      },
                    ),
        ),
      ]),
    );
  }
}

class _Carte extends StatelessWidget {
  const _Carte({required this.hanouts, this.centre});
  final List<Map<String, dynamic>> hanouts;
  final LatLng? centre;

  @override
  Widget build(BuildContext context) => FlutterMap(
        options: MapOptions(initialCenter: centre ?? _casablanca, initialZoom: 14),
        children: [
          TileLayer(urlTemplate: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png', userAgentPackageName: 'ma.daba.daba_ambulant'),
          MarkerLayer(markers: [
            if (centre != null) Marker(point: centre!, child: const Icon(Icons.my_location, color: Colors.blue)),
            for (final h in hanouts)
              if (h['position'] case {'lat': final num lat, 'lon': final num lon} when lat.abs() <= 90 && lon.abs() <= 180)
                Marker(
                  point: LatLng(lat.toDouble(), lon.toDouble()),
                  width: 44,
                  height: 44,
                  child: IconButton(
                    tooltip: h['nom'] as String,
                    icon: Icon(Icons.location_on, color: Theme.of(context).colorScheme.primary, size: 36),
                    onPressed: () => context.ouvrir(EcranHanout(h['id'] as String)),
                  ),
                ),
          ]),
        ],
      );
}

/// Fiche hanout : coordonnées, solde du karni, historique, et actions de vente.
class EcranHanout extends StatelessWidget {
  const EcranHanout(this.id, {super.key});
  final String id;

  @override
  Widget build(BuildContext context) {
    final t = context.t;
    final m = context.daba.m;
    final h = m.hanout(id) ?? {'id': id, 'nom': ''};
    final compte = m.compte(id);
    final historique = m.historique(id);
    return Scaffold(
      appBar: AppBar(title: Text(h['nom'] as String)),
      floatingActionButton: FloatingActionButton.extended(
        heroTag: 'vendre',
        onPressed: () => context.ouvrir(EcranVente(hanoutId: id)),
        icon: const Icon(Icons.point_of_sale),
        label: Text(t.vendre),
      ),
      body: ListView(padding: const EdgeInsets.only(bottom: 96), children: [
        if (h['photoFacadeUrl'] case final String url)
          Image.network(url, height: 160, fit: BoxFit.cover, cacheHeight: 320, errorBuilder: (_, _, _) => const SizedBox()),
        ListTile(
          leading: const Icon(Icons.person_outline),
          title: Text((h['gerant'] ?? '-') as String),
          subtitle: Text([h['telephone'], h['adresse'], h['ville'], if (h['ice'] != null) 'ICE ${h['ice']}']
              .whereType<String>()
              .join(' · ')),
        ),
        Card(
          margin: const EdgeInsets.all(12),
          child: ListTile(
            title: Text(t.solde),
            subtitle: Text([
              if (compte?['plafond'] != null) t.plafondValeur(dh(compte!['plafond'] as int)),
              if (compte?['delaiMaxJours'] != null) t.delaiValeur('${compte!['delaiMaxJours']}'),
            ].join(' · ')),
            trailing: Text(dh(m.solde(id)), style: Theme.of(context).textTheme.titleLarge),
          ),
        ),
        Wrap(spacing: 8, alignment: WrapAlignment.center, children: [
          OutlinedButton.icon(
            onPressed: m.derniereCommande(id).isEmpty
                ? null
                : () => context.ouvrir(EcranVente(hanoutId: id, lignes: m.derniereCommande(id))),
            icon: const Icon(Icons.replay),
            label: Text(t.derniereCommande),
          ),
          OutlinedButton.icon(
            onPressed: () => saisirVersement(context, id),
            icon: const Icon(Icons.payments_outlined),
            label: Text(t.versement),
          ),
          OutlinedButton.icon(
            onPressed: () => reglerCredit(context, id),
            icon: const Icon(Icons.tune),
            label: Text(t.plafondCredit),
          ),
        ]),
        for (final c in m.contestations(hanoutId: id)) CarteContestation(c),
        const Divider(),
        ListTile(title: Text(t.historique, style: Theme.of(context).textTheme.titleMedium)),
        if (historique.isEmpty) Vide(t.aucunMouvement),
        for (final e in historique) LigneHistorique(e),
      ]),
    );
  }
}

/// Création d'un hanout absent de la base POS, hors ligne (UUID de l'appareil).
class EcranNouveauHanout extends StatefulWidget {
  const EcranNouveauHanout({super.key});

  @override
  State<EcranNouveauHanout> createState() => _EcranNouveauHanoutState();
}

class _EcranNouveauHanoutState extends State<EcranNouveauHanout> {
  final _c = {for (final k in ['nom', 'gerant', 'telephone', 'adresse', 'ville', 'ice']) k: TextEditingController()};
  bool _position = true;

  String? _val(String k) => _c[k]!.text.trim().isEmpty ? null : _c[k]!.text.trim();

  void _enregistrer() {
    final t = context.t;
    final daba = context.daba;
    if (_val('nom') == null) return context.message(t.champObligatoire(t.nomHanout));
    var tel = _val('telephone')?.replaceAll(RegExp(r'[^0-9+]'), '');
    if (tel != null && tel.startsWith('0')) tel = '+212${tel.substring(1)}';
    if (tel != null && !RegExp(r'^\+212[5-7][0-9]{8}$').hasMatch(tel)) return context.message(t.telephoneInvalide);
    if (_val('ice') != null && !RegExp(r'^[0-9]{15}$').hasMatch(_val('ice')!)) return context.message(t.iceInvalide);
    final pos = _position ? daba.position : null;
    final h = daba.m.creerHanout(
      nomHanout: _val('nom')!,
      gerant: _val('gerant'),
      telephone: tel,
      adresse: _val('adresse'),
      ville: _val('ville'),
      ice: _val('ice'),
      position: pos == null ? null : {'lat': pos.latitude, 'lon': pos.longitude},
    );
    daba.apresGeste();
    Navigator.pop(context);
    context.ouvrir(EcranHanout(h['id'] as String));
  }

  @override
  Widget build(BuildContext context) {
    final t = context.t;
    final libelles = {
      'nom': t.nomHanout, 'gerant': t.gerant, 'telephone': t.telephone, 'adresse': t.adresse, 'ville': t.ville, 'ice': t.ice,
    };
    return Scaffold(
      appBar: AppBar(title: Text(t.nouveauHanout)),
      body: ListView(padding: const EdgeInsets.all(16), children: [
        for (final e in _c.entries)
          Padding(
            padding: const EdgeInsets.only(bottom: 8),
            child: TextField(
              controller: e.value,
              keyboardType: e.key == 'telephone' || e.key == 'ice' ? TextInputType.phone : TextInputType.text,
              decoration: InputDecoration(labelText: libelles[e.key]),
            ),
          ),
        if (context.daba.position != null)
          SwitchListTile(value: _position, onChanged: (v) => setState(() => _position = v), title: Text(t.positionActuelle)),
        const SizedBox(height: 16),
        FilledButton(onPressed: _enregistrer, child: Text(t.enregistrer)),
      ]),
    );
  }
}

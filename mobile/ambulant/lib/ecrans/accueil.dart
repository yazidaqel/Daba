import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../ui.dart';
import 'connexion.dart';
import 'depannage.dart';
import 'hanouts.dart';
import 'karni.dart';
import 'promos.dart';
import 'stock.dart';
import 'synchro.dart';

class EcranAccueil extends StatefulWidget {
  const EcranAccueil({super.key});

  @override
  State<EcranAccueil> createState() => _EcranAccueilState();
}

class _EcranAccueilState extends State<EcranAccueil> {
  int _onglet = 0;
  StreamSubscription<Map<String, dynamic>>? _alertes;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _alertes ??= context.daba.nouvellesDemandes.listen((d) {
      if (!mounted) return;
      HapticFeedback.heavyImpact();
      SystemSound.play(SystemSoundType.alert);
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
        duration: const Duration(seconds: 10),
        content: Text(context.t.alerteDemande(context.lib(d['produit']?['libelle']), '${d['quantite']}')),
        action: SnackBarAction(label: context.t.voir, onPressed: () => setState(() => _onglet = 4)),
      ));
    });
  }

  @override
  void dispose() {
    _alertes?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final t = context.t;
    final daba = context.daba;
    final onglets = [t.ongletHanouts, t.ongletStock, t.ongletKarni, t.ongletPromos, t.ongletDepannage];
    return Scaffold(
      appBar: AppBar(
        title: Text(onglets[_onglet]),
        actions: [
          IconButton(
            tooltip: t.synchronisation,
            onPressed: () => context.ouvrir(const EcranSynchro()),
            icon: Badge(
              isLabelVisible: daba.enAttente > 0,
              label: Text('${daba.enAttente}'),
              child: Icon(daba.horsLigne ? Icons.cloud_off : Icons.cloud_done_outlined),
            ),
          ),
          const MenuLangue(),
          PopupMenuButton<int>(
            onSelected: (v) => v == 0 ? context.ouvrir(const EcranParametres()) : _deconnecter(),
            itemBuilder: (_) => [
              PopupMenuItem(value: 0, child: Text(t.parametres)),
              PopupMenuItem(value: 1, child: Text(t.deconnexion)),
            ],
          ),
        ],
      ),
      body: Column(children: [
        if (daba.reconnexionRequise)
          MaterialBanner(
            content: Text(t.reconnexionRequise),
            actions: [
              TextButton(onPressed: () => context.ouvrir(const EcranConnexion(reconnexion: true)), child: Text(t.seConnecter)),
            ],
          ),
        Expanded(
          child: IndexedStack(index: _onglet, children: const [
            OngletHanouts(),
            OngletStock(),
            OngletKarni(),
            OngletPromos(),
            OngletDepannage(),
          ]),
        ),
      ]),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _onglet,
        onDestinationSelected: (i) => setState(() => _onglet = i),
        destinations: [
          NavigationDestination(icon: const Icon(Icons.storefront_outlined), label: onglets[0]),
          NavigationDestination(icon: const Icon(Icons.inventory_2_outlined), label: onglets[1]),
          NavigationDestination(icon: const Icon(Icons.menu_book_outlined), label: onglets[2]),
          NavigationDestination(icon: const Icon(Icons.local_offer_outlined), label: onglets[3]),
          NavigationDestination(
            icon: Badge(
              isLabelVisible: daba.demandes.any((d) => d['statut'] == 'EN_ATTENTE'),
              child: Icon(daba.disponible ? Icons.delivery_dining : Icons.delivery_dining_outlined),
            ),
            label: onglets[4],
          ),
        ],
      ),
    );
  }

  Future<void> _deconnecter() async {
    final daba = context.daba;
    final t = context.t;
    final texte = daba.enAttente > 0 ? t.deconnexionAvecAttente('${daba.enAttente}') : t.deconnexionConfirmer;
    if (await context.confirmer(texte)) await daba.deconnecter();
  }
}

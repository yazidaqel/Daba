import 'dart:async';

import 'package:flutter/material.dart';
import 'package:phosphoricons_flutter/phosphoricons_flutter.dart';
import 'package:flutter/services.dart';

import '../etat.dart';
import '../push.dart';
import '../ui.dart';
import 'connexion.dart';
import 'depannage.dart';
import 'hanouts.dart';
import 'karni.dart';
import 'profil.dart';
import 'promos.dart';
import 'stock.dart';
import 'synchro.dart';
import 'tableau.dart';
import 'vente.dart';

class EcranAccueil extends StatefulWidget {
  const EcranAccueil({super.key});

  @override
  State<EcranAccueil> createState() => _EcranAccueilState();
}

class _EcranAccueilState extends State<EcranAccueil> {
  int _onglet = 0;
  StreamSubscription<Map<String, dynamic>>? _alertes;
  StreamSubscription<({Cible? cible, String texte})>? _avis;

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
    // Push reçu app ouverte : bandeau au lieu d'une notification système (le texte vient du serveur).
    _avis ??= context.daba.avisPush.listen((a) {
      if (!mounted) return;
      final cible = a.cible;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
        duration: const Duration(seconds: 8),
        content: Text(a.texte),
        action: cible == null ? null : SnackBarAction(label: context.t.voir, onPressed: () => _ouvrirCible(cible)),
      ));
    });
    context.dabaLu.proposerNotifications();
  }

  @override
  void dispose() {
    _alertes?.cancel();
    _avis?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final t = context.t;
    final daba = context.daba;
    _proposerProfil(daba);
    // Notification touchée : ouverte seulement app au premier plan, donc après un éventuel verrouillage.
    final cible = daba.cibleEnAttente;
    if (cible != null && WidgetsBinding.instance.lifecycleState == AppLifecycleState.resumed) {
      daba.cibleEnAttente = null;
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) _ouvrirCible(cible);
      });
    }
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
              child: Ico(daba.horsLigne ? PhosphorIconsRegular.cloudSlash : PhosphorIconsRegular.cloudCheck),
            ),
          ),
          const MenuLangue(),
          PopupMenuButton<int>(
            icon: const Ico(PhosphorIconsRegular.dotsThreeVertical),
            onSelected: (v) => switch (v) {
              0 => context.ouvrir(const EcranParametres()),
              2 => context.ouvrir(const EcranProfil()),
              3 => context.ouvrir(const EcranTableauDeBord()),
              _ => _deconnecter(),
            },
            itemBuilder: (_) => [
              PopupMenuItem(value: 3, child: Text(t.tableauDeBord)),
              PopupMenuItem(value: 2, child: Text(t.profil)),
              PopupMenuItem(value: 0, child: Text(t.parametres)),
              PopupMenuItem(value: 1, child: Text(t.deconnexion)),
            ],
          ),
        ],
      ),
      body: Column(children: [
        // Ancien texte CNDP accepté : l'app reste utilisable, le nouveau est proposé quand le réseau est là.
        if ((!daba.consentementAJour || daba.stockage.attenteConsentement) && !daba.horsLigne)
          MaterialBanner(
            leading: const Ico(PhosphorIconsRegular.info),
            content: Text(t.consentementNouveauTexte),
            actions: [TextButton(onPressed: () => context.ouvrir(const EcranConsentement()), child: Text(t.lireEtAccepter))],
          ),
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
          NavigationDestination(icon: const Ico(PhosphorIconsRegular.storefront), label: onglets[0]),
          NavigationDestination(icon: const Ico(PhosphorIconsRegular.package), label: onglets[1]),
          NavigationDestination(icon: const Ico(PhosphorIconsRegular.notebook), label: onglets[2]),
          NavigationDestination(icon: const Ico(PhosphorIconsRegular.tag), label: onglets[3]),
          NavigationDestination(
            icon: Badge(
              isLabelVisible: daba.demandes.any((d) => d['statut'] == 'EN_ATTENTE'),
              child: const Ico(PhosphorIconsRegular.moped),
            ),
            label: onglets[4],
          ),
        ],
      ),
    );
  }

  /// Profil proposé une fois après la première connexion, si le serveur n'en a envoyé aucun (§3.1).
  void _proposerProfil(Daba daba) {
    if (daba.derniereSync == null || !daba.consentementProfil || daba.m.profil != null || daba.stockage.lire('profilPropose') != null) return;
    daba.stockage.ecrire('profilPropose', '1');
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) context.ouvrir(const EcranProfil(propose: true));
    });
  }

  /// Écran d'une notification touchée. Objet pas encore synchronisé : l'onglet seul, il s'y affichera.
  void _ouvrirCible(Cible c) {
    final s = context.daba.stockage;
    final id = c.id ?? '';
    setState(() => _onglet = switch (c.vers) {
          Vers.depannage => 4,
          Vers.offre || Vers.reservation => 3,
          Vers.hanout || Vers.document || Vers.contestations => 2,
        });
    final offre = s.objet('PROMO', id) ??
        s.objets('PROMO', where: "json_extract(donnees, '\$.depotId') = ?", args: [id], limite: 1).firstOrNull;
    final reservation = s.objet('RESERVATION', id);
    final Widget? ecran = switch (c.vers) {
      Vers.offre => offre == null ? null : EcranOffre(offre),
      Vers.reservation => reservation == null ? const EcranReservations() : EcranReservation(reservation),
      Vers.hanout => s.objet('HANOUT', id) == null ? null : EcranHanout(id),
      Vers.document => s.objet('DOCUMENT', id) == null ? null : EcranDocument(id),
      Vers.contestations => const EcranContestations(),
      Vers.depannage => null,
    };
    if (ecran != null) context.ouvrir(ecran);
  }

  Future<void> _deconnecter() async {
    final daba = context.daba;
    final t = context.t;
    final texte = daba.enAttente > 0 ? t.deconnexionAvecAttente('${daba.enAttente}') : t.deconnexionConfirmer;
    if (await context.confirmer(texte)) await daba.deconnecter();
  }
}

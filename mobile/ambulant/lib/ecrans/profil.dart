import 'package:flutter/material.dart';
import 'package:phosphoricons_flutter/phosphoricons_flutter.dart';

import 'package:daba_shared/daba_shared.dart';

import '../domaine.dart';
import '../ui.dart';
import 'connexion.dart';

/// Profil de l'ambulant (§3.1), saisi hors ligne. [propose] : ouvert une fois après la première
/// connexion, l'ambulant peut le remettre à plus tard.
class EcranProfil extends StatefulWidget {
  const EcranProfil({super.key, this.propose = false});
  final bool propose;

  @override
  State<EcranProfil> createState() => _EcranProfilState();
}

class _EcranProfilState extends State<EcranProfil> {
  final _form = GlobalKey<FormState>();
  late final TextEditingController _nom, _cin, _vehicule;
  late final Set<String> _villes, _categories;

  @override
  void initState() {
    super.initState();
    final daba = context.dabaLu;
    final p = daba.m.profil ?? const {};
    _nom = TextEditingController(text: (p['nom'] ?? daba.m.nom) as String);
    _cin = TextEditingController(text: p['cin'] as String?);
    _vehicule = TextEditingController(text: p['vehicule'] as String?);
    _villes = {...?(p['villes'] as List?)?.cast<String>()};
    _categories = {...?(p['categories'] as List?)?.cast<String>()};
    // Listes à jour si le réseau est là ; sinon celles gardées sur le téléphone.
    daba.chargerReferentiels().then((_) => mounted ? setState(() {}) : null, onError: (_) {});
  }

  @override
  void dispose() {
    for (final c in [_nom, _cin, _vehicule]) {
      c.dispose();
    }
    super.dispose();
  }

  /// Catégories du référentiel, sinon celles des produits synchronisés ; les codes déjà choisis
  /// restent affichés (libellé localisé, code à défaut).
  List<({String code, Object? libelle})> _listeCategories() {
    final daba = context.daba;
    final parCode = <String, Object?>{
      for (final p in daba.m.produits()) if (p['categorie'] case {'code': final String c}) c: p['categorie']['libelle'],
      for (final c in daba.referentiel('categories')) c['code'] as String: c['libelle'],
    };
    for (final c in _categories) {
      parCode.putIfAbsent(c, () => null);
    }
    return [for (final e in parCode.entries) (code: e.key, libelle: e.value)]
      ..sort((a, b) => context.lib(a.libelle ?? a.code).compareTo(context.lib(b.libelle ?? b.code)));
  }

  void _enregistrer() {
    if (!_form.currentState!.validate()) return;
    final daba = context.daba;
    daba.m.definirProfil(nom: _nom.text, cin: _cin.text, vehicule: _vehicule.text, villes: _villes, categories: _categories);
    daba.apresGeste();
    context.message(context.t.profilEnregistre);
    Navigator.pop(context);
  }

  @override
  Widget build(BuildContext context) {
    final t = context.t;
    final regions = context.daba.referentiel('regions');
    final aJour = context.daba.consentementProfil;
    final connues = {for (final r in regions) for (final v in (r['villes'] as List).cast<Map>()) v['code']};
    final categories = _listeCategories();
    final titre = Theme.of(context).textTheme.titleMedium;
    return Scaffold(
      appBar: AppBar(title: Text(t.profil)),
      body: Form(
        key: _form,
        child: ListView(padding: const EdgeInsets.symmetric(vertical: 16), children: [
          Padding(padding: const EdgeInsets.symmetric(horizontal: 16), child: Text(t.profilExplication)),
          // Le nom n'est montré aux grossistes et aux hanouts qu'avec le texte qui le dit (v2).
          if (!aJour)
            ListTile(
              leading: Ico(PhosphorIconsRegular.warning, color: Zellige.of(context).warning),
              title: Text(t.profilConsentementRequis),
              trailing: TextButton(onPressed: () => context.ouvrir(const EcranConsentement()), child: Text(t.lireEtAccepter)),
            ),
          const SizedBox(height: 16),
          _champ(TextFormField(
            controller: _nom,
            maxLength: 100,
            textCapitalization: TextCapitalization.words,
            decoration: InputDecoration(labelText: t.nomComplet, prefixIcon: const Ico(PhosphorIconsRegular.user)),
            validator: (v) => (v ?? '').trim().isEmpty ? t.champObligatoire(t.nomComplet) : null,
          )),
          _champ(TextFormField(
            controller: _cin,
            maxLength: 8,
            textCapitalization: TextCapitalization.characters,
            textDirection: TextDirection.ltr,
            decoration: InputDecoration(labelText: t.cin, hintText: 'BK123456', prefixIcon: const Ico(PhosphorIconsRegular.identificationCard)),
            validator: (v) {
              final c = (v ?? '').trim().toUpperCase();
              return c.isEmpty || Ambulant.motifCin.hasMatch(c) ? null : t.cinInvalide;
            },
          )),
          _champ(TextFormField(
            controller: _vehicule,
            maxLength: 100,
            decoration: InputDecoration(labelText: t.vehicule, prefixIcon: const Ico(PhosphorIconsRegular.truck)),
          )),
          ListTile(title: Text(t.zonesCouvertes, style: titre)),
          if (regions.isEmpty) ...[
            ListTile(leading: const Ico(PhosphorIconsRegular.cloudSlash), title: Text(t.listeApresConnexion)),
            _puces([for (final v in _villes) (code: v, libelle: null)], _villes),
          ] else ...[
            for (final r in regions)
              ExpansionTile(
                leading: const Ico(PhosphorIconsRegular.mapPin),
                title: Text(context.lib(r['nom'])),
                initiallyExpanded: (r['villes'] as List).any((v) => _villes.contains(v['code'])),
                children: [
                  _puces([for (final v in (r['villes'] as List).cast<Map>()) (code: v['code'] as String, libelle: v['nom'])], _villes),
                ],
              ),
            // Ville gardée au profil mais absente du référentiel : visible pour pouvoir la retirer.
            _puces([for (final v in _villes) if (!connues.contains(v)) (code: v, libelle: null)], _villes),
          ],
          ListTile(title: Text(t.categoriesVendues, style: titre)),
          _puces(categories, _categories),
          const Divider(height: 32),
          ListTile(
            leading: const Ico(PhosphorIconsRegular.receipt),
            title: Text(t.mentionsLegales),
            subtitle: Text(_resumeMentions(context)),
            trailing: const Icon(PhosphorIconsRegular.caretRight),
            onTap: () => context.ouvrir(const EcranMentions()).then((_) => mounted ? setState(() {}) : null),
          ),
          Padding(
            padding: const EdgeInsets.all(16),
            child: FilledButton.icon(onPressed: aJour ? _enregistrer : null, icon: const Ico(PhosphorIconsRegular.check), label: Text(t.enregistrer)),
          ),
          if (widget.propose)
            Center(child: TextButton(onPressed: () => Navigator.pop(context), child: Text(t.plusTard))),
        ]),
      ),
    );
  }

  Widget _champ(Widget champ) => Padding(padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4), child: champ);

  Widget _puces(List<({String code, Object? libelle})> choix, Set<String> retenus) => Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
        child: Wrap(spacing: 8, runSpacing: 4, children: [
          for (final c in choix)
            FilterChip(
              label: Text(context.lib(c.libelle ?? c.code)),
              selected: retenus.contains(c.code),
              onSelected: (oui) => setState(() => oui ? retenus.add(c.code) : retenus.remove(c.code)),
            ),
        ]),
      );
}

String _resumeMentions(BuildContext context) {
  final m = context.daba.m.parametres['mentionsLegales'] as Map?;
  return m == null ? context.t.mentionsAucune : ltr('ICE ${m['ice']}');
}

/// Mentions légales de l'ambulant auto-entrepreneur ou société (RG-F2), envoyées hors ligne
/// par l'opération PARAMETRES_AMBULANT : présentes, elles permettent la facture.
class EcranMentions extends StatefulWidget {
  const EcranMentions({super.key});

  @override
  State<EcranMentions> createState() => _EcranMentionsState();
}

class _EcranMentionsState extends State<EcranMentions> {
  final _form = GlobalKey<FormState>();
  late final TextEditingController _ice, _if, _rc;
  late bool _tva;

  @override
  void initState() {
    super.initState();
    final m = (context.dabaLu.m.parametres['mentionsLegales'] as Map?) ?? const {};
    _ice = TextEditingController(text: m['ice'] as String?);
    _if = TextEditingController(text: m['identifiantFiscal'] as String?);
    _rc = TextEditingController(text: m['registreCommerce'] as String?);
    _tva = m['assujettiTva'] == true;
  }

  @override
  void dispose() {
    for (final c in [_ice, _if, _rc]) {
      c.dispose();
    }
    super.dispose();
  }

  void _enregistrer() {
    if (!_form.currentState!.validate()) return;
    final daba = context.daba;
    daba.m.definirMentions({
      'ice': _ice.text.trim(),
      'identifiantFiscal': _if.text.trim(),
      'registreCommerce': _rc.text.trim(),
      'assujettiTva': _tva,
    });
    daba.apresGeste();
    Navigator.pop(context);
  }

  @override
  Widget build(BuildContext context) {
    final t = context.t;
    String? requis(String? v, String champ) => (v ?? '').trim().isEmpty ? t.champObligatoire(champ) : null;
    Widget champ(TextEditingController c, String libelle, {String? Function(String?)? validator, TextInputType? clavier}) => Padding(
          padding: const EdgeInsets.symmetric(vertical: 4),
          child: TextFormField(
            controller: c,
            keyboardType: clavier,
            textDirection: TextDirection.ltr,
            decoration: InputDecoration(labelText: libelle),
            validator: validator ?? (v) => requis(v, libelle),
          ),
        );
    return Scaffold(
      appBar: AppBar(title: Text(t.mentionsLegales)),
      body: Form(
        key: _form,
        child: ListView(padding: const EdgeInsets.all(16), children: [
          Text(t.mentionsExplication),
          const SizedBox(height: 16),
          champ(_ice, t.ice, clavier: TextInputType.number,
              validator: (v) => RegExp(r'^[0-9]{15}$').hasMatch((v ?? '').trim()) ? null : t.iceInvalide),
          champ(_if, t.identifiantFiscal),
          champ(_rc, t.registreCommerce),
          SwitchListTile(contentPadding: EdgeInsets.zero, value: _tva, title: Text(t.assujettiTva), onChanged: (v) => setState(() => _tva = v)),
          const SizedBox(height: 16),
          FilledButton.icon(onPressed: _enregistrer, icon: const Ico(PhosphorIconsRegular.check), label: Text(t.enregistrer)),
        ]),
      ),
    );
  }
}

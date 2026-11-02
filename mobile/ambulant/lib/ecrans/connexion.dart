import 'package:daba_shared/daba_shared.dart';
import 'package:flutter/material.dart';
import 'package:phosphoricons_flutter/phosphoricons_flutter.dart';

import '../ui.dart';

/// Sélecteur de langue, présent dès l'écran de connexion.
class MenuLangue extends StatelessWidget {
  const MenuLangue({super.key});

  @override
  Widget build(BuildContext context) {
    final t = context.t;
    final noms = [t.langueFr, t.langueAr, t.langueAryArab, t.langueAryLatn];
    return PopupMenuButton<Locale>(
      icon: const Ico(PhosphorIconsRegular.translate),
      tooltip: t.langue,
      onSelected: context.daba.choisirLangue,
      itemBuilder: (_) => [
        for (var i = 0; i < languesDaba.length; i++) PopupMenuItem(value: languesDaba[i], child: Text(noms[i])),
      ],
    );
  }
}

/// Inscription et connexion par numéro de téléphone + OTP SMS (§3.1). Exige le réseau.
class EcranConnexion extends StatefulWidget {
  const EcranConnexion({super.key, this.reconnexion = false});
  final bool reconnexion;

  @override
  State<EcranConnexion> createState() => _EcranConnexionState();
}

class _EcranConnexionState extends State<EcranConnexion> {
  final _tel = TextEditingController();
  final _code = TextEditingController();
  bool _codeEnvoye = false, _occupe = false;

  /// 06 12 34 56 78 → +212612345678
  String get _e164 {
    final c = _tel.text.replaceAll(RegExp(r'[^0-9+]'), '');
    if (c.startsWith('+')) return c;
    if (c.startsWith('00')) return '+${c.substring(2)}';
    return '+212${c.startsWith('0') ? c.substring(1) : c}';
  }

  Future<void> _agir() async {
    final daba = context.daba;
    setState(() => _occupe = true);
    try {
      if (!_codeEnvoye) {
        if (!RegExp(r'^\+212[5-7][0-9]{8}$').hasMatch(_e164)) {
          context.message(context.t.telephoneInvalide);
          return;
        }
        await daba.demanderOtp(_e164);
        setState(() => _codeEnvoye = true);
      } else {
        await daba.verifierOtp(_e164, _code.text.trim());
        if (widget.reconnexion && mounted) Navigator.pop(context);
      }
    } catch (e) {
      if (mounted) context.erreur(e);
    } finally {
      if (mounted) setState(() => _occupe = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final t = context.t;
    return Scaffold(
      appBar: AppBar(title: Text(t.titreApp), actions: const [MenuLangue()]),
      body: ListView(padding: const EdgeInsets.all(24), children: [
        Text(widget.reconnexion ? t.reconnexionExplication : t.connexionExplication),
        const SizedBox(height: 24),
        TextField(
          controller: _tel,
          enabled: !_codeEnvoye,
          keyboardType: TextInputType.phone,
          textDirection: TextDirection.ltr,
          decoration: InputDecoration(labelText: t.telephone, hintText: '06 12 34 56 78', prefixIcon: const Ico(PhosphorIconsRegular.phone)),
        ),
        if (_codeEnvoye) ...[
          const SizedBox(height: 16),
          TextField(
            controller: _code,
            autofocus: true,
            keyboardType: TextInputType.number,
            maxLength: 6,
            textDirection: TextDirection.ltr,
            decoration: InputDecoration(labelText: t.codeSms, prefixIcon: const Ico(PhosphorIconsRegular.chatText)),
          ),
          TextButton(onPressed: () => setState(() => _codeEnvoye = false), child: Text(t.changerNumero)),
        ],
        const SizedBox(height: 24),
        FilledButton(
          onPressed: _occupe ? null : _agir,
          child: Text(_codeEnvoye ? t.seConnecter : t.recevoirCode),
        ),
      ]),
    );
  }
}

/// Consentement CNDP (loi 09-08) : traitement des données, et géolocalisation à part,
/// collectée seulement quand l'ambulant est disponible.
class EcranConsentement extends StatefulWidget {
  const EcranConsentement({super.key});

  @override
  State<EcranConsentement> createState() => _EcranConsentementState();
}

class _EcranConsentementState extends State<EcranConsentement> {
  bool _donnees = false, _geo = false, _occupe = false;

  @override
  Widget build(BuildContext context) {
    final t = context.t;
    return Scaffold(
      appBar: AppBar(title: Text(t.consentementTitre), actions: const [MenuLangue()]),
      body: ListView(padding: const EdgeInsets.all(24), children: [
        Text(t.consentementTexte),
        const SizedBox(height: 16),
        CheckboxListTile(value: _donnees, onChanged: (v) => setState(() => _donnees = v!), title: Text(t.consentementDonnees)),
        CheckboxListTile(value: _geo, onChanged: (v) => setState(() => _geo = v!), title: Text(t.consentementGeo)),
        const SizedBox(height: 16),
        FilledButton(
          onPressed: !_donnees || _occupe
              ? null
              : () async {
                  setState(() => _occupe = true);
                  try {
                    await context.daba.consentir(geolocalisation: _geo);
                  } catch (e) {
                    if (context.mounted) context.erreur(e);
                  } finally {
                    if (mounted) setState(() => _occupe = false);
                  }
                },
          child: Text(t.accepter),
        ),
      ]),
    );
  }
}

/// Code PIN local (§10) : création après la première connexion, puis déverrouillage hors ligne.
class EcranPin extends StatefulWidget {
  const EcranPin({super.key, required this.creation});
  final bool creation;

  @override
  State<EcranPin> createState() => _EcranPinState();
}

class _EcranPinState extends State<EcranPin> {
  final _pin = TextEditingController();
  String? _premier;

  Future<void> _valider() async {
    final pin = _pin.text;
    final t = context.t;
    if (pin.length != 4) return;
    _pin.clear();
    if (!widget.creation) {
      if (!await context.daba.deverrouiller(pin) && mounted) context.message(t.pinFaux);
    } else if (_premier == null) {
      setState(() => _premier = pin);
    } else if (_premier == pin) {
      await context.daba.definirPin(pin);
    } else {
      setState(() => _premier = null);
      context.message(t.pinDifferent);
    }
  }

  @override
  Widget build(BuildContext context) {
    final t = context.t;
    return Scaffold(
      appBar: AppBar(title: Text(t.titreApp), actions: const [MenuLangue()]),
      body: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(children: [
          Text(!widget.creation ? t.pinSaisir : (_premier == null ? t.pinCreer : t.pinConfirmer),
              style: Theme.of(context).textTheme.titleLarge),
          const SizedBox(height: 24),
          TextField(
            controller: _pin,
            autofocus: true,
            obscureText: true,
            maxLength: 4,
            textAlign: TextAlign.center,
            keyboardType: TextInputType.number,
            style: const TextStyle(fontSize: 32, letterSpacing: 16),
            onChanged: (v) => v.length == 4 ? _valider() : null,
          ),
          if (!widget.creation)
            TextButton(onPressed: () => context.daba.deconnecter(), child: Text(t.pinOublie)),
        ]),
      ),
    );
  }
}

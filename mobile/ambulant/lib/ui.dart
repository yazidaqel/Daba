import 'package:daba_shared/daba_shared.dart';
import 'package:flutter/material.dart';

import 'domaine.dart';
import 'etat.dart';

class DabaScope extends InheritedNotifier<Daba> {
  const DabaScope({super.key, required Daba daba, required super.child}) : super(notifier: daba);
}

extension Contexte on BuildContext {
  Daba get daba => dependOnInheritedWidgetOfExactType<DabaScope>()!.notifier!;
  T get t => T.of(this);
  Locale get langue => Localizations.localeOf(this);
  String lib(Object? libelleI18n) => libelle(libelleI18n, langue);

  void message(String texte) =>
      ScaffoldMessenger.of(this)..hideCurrentSnackBar()..showSnackBar(SnackBar(content: Text(texte)));

  void erreur(Object e) => message(messageErreur(t, e));

  Future<T2?> ouvrir<T2>(Widget ecran) => Navigator.of(this).push<T2>(MaterialPageRoute(builder: (_) => ecran));

  Future<bool> confirmer(String texte) async =>
      await showDialog<bool>(
        context: this,
        builder: (c) => AlertDialog(
          content: Text(texte),
          actions: [
            TextButton(onPressed: () => Navigator.pop(c, false), child: Text(t.annuler)),
            FilledButton(onPressed: () => Navigator.pop(c, true), child: Text(t.confirmer)),
          ],
        ),
      ) ??
      false;
}

String messageErreur(T t, Object e) => switch (e) {
      HorsLigne() => t.erreurHorsLigne,
      PermissionRefusee() => t.erreurPermissionPosition,
      AutreAmbulantEnAttente() => t.erreurAutreAmbulant,
      VenteRefusee(:final motif) => switch (motif) {
          RefusVente.factureSansMentions => t.erreurFactureSansMentions,
          RefusVente.factureTvaInconnue => t.erreurFactureTvaInconnue,
          RefusVente.creditBloque => t.erreurCreditBloque,
          RefusVente.creditAvertissement => t.avertissementCredit,
          RefusVente.remiseHorsPlafond => t.erreurRemisePlafond,
          RefusVente.qrAutreAmbulant => t.erreurQrAutreAmbulant,
          RefusVente.qrIllisible => t.erreurQrIllisible,
          RefusVente.bonDejaCharge => t.erreurBonDejaCharge,
        },
      // Décision sur le code fixe du contrat (`Problem.type`), jamais sur `detail`.
      ProblemeApi(code: 'appareil-revoque') => t.erreurAppareilRevoque,
      ProblemeApi(code: 'consentement-requis') => t.erreurConsentement,
      ProblemeApi(code: 'code-invalide') => t.erreurCodeInvalide,
      ProblemeApi(code: 'non-authentifie') => t.reconnexionRequise,
      ProblemeApi(code: 'trop-de-demandes') => t.erreurTropDEssais,
      ProblemeApi(code: 'lien-expire' || 'curseur-expire') => t.erreurCodeExpire,
      ProblemeApi(regle: 'RG-P3') => t.erreurPlafondPromo,
      ProblemeApi(code: 'conflit') || ProblemeApi(status: 409) => t.erreurConflit,
      ProblemeApi(:final status) => t.erreurServeur(status.toString()),
      _ => t.erreurInconnue,
    };

/// Champ de saisie d'un montant en dirhams (renvoie des centimes via [centimes]).
class ChampMontant extends StatelessWidget {
  const ChampMontant({super.key, required this.controleur, required this.etiquette, this.onChanged, this.indice});
  final TextEditingController controleur;
  final String etiquette;
  final String? indice;
  final ValueChanged<String>? onChanged;

  @override
  Widget build(BuildContext context) => TextField(
        controller: controleur,
        keyboardType: const TextInputType.numberWithOptions(decimal: true),
        decoration: InputDecoration(
          labelText: etiquette,
          suffixText: 'DH',
          hintText: indice,
          floatingLabelBehavior: indice == null ? null : FloatingLabelBehavior.always,
        ),
        onChanged: onChanged,
      );
}

class Vide extends StatelessWidget {
  const Vide(this.texte, {super.key, this.icone = Icons.inbox_outlined});
  final String texte;
  final IconData icone;

  @override
  Widget build(BuildContext context) => Center(
        child: Padding(
          padding: const EdgeInsets.all(32),
          child: Column(mainAxisSize: MainAxisSize.min, children: [
            Icon(icone, size: 48, color: Theme.of(context).colorScheme.outline),
            const SizedBox(height: 12),
            Text(texte, textAlign: TextAlign.center),
          ]),
        ),
      );
}

/// Photo produit (compressée côté serveur) ; une icône si absente ou hors ligne.
class PhotoProduit extends StatelessWidget {
  const PhotoProduit(this.produit, {super.key, this.taille = 40});
  final Map<String, dynamic>? produit;
  final double taille;

  @override
  Widget build(BuildContext context) {
    final url = produit?['photoUrl'] as String?;
    final icone = Icon(Icons.inventory_2_outlined, size: taille * .7);
    return SizedBox.square(
      dimension: taille,
      child: url == null
          ? icone
          : Image.network(url, cacheWidth: (taille * 2).round(), errorBuilder: (_, _, _) => icone),
    );
  }
}

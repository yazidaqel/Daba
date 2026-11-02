import 'package:flutter/material.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter_localizations/flutter_localizations.dart';

import '../l10n/app_localizations.dart';

/// Langues de l'interface : français, arabe (RTL), darija en caractères arabes (RTL) et latins.
const languesDaba = [
  Locale('fr'),
  Locale('ar'),
  Locale('ary'),
  Locale.fromSubtags(languageCode: 'ary', scriptCode: 'Latn'),
];

/// Code `Langue` du contrat (Accept-Language, LibelleI18n).
String codeLangue(Locale l) => switch (l) {
      Locale(languageCode: 'ary', scriptCode: 'Latn') => 'ary-Latn',
      Locale(languageCode: 'ary') => 'ary-Arab',
      _ => l.languageCode,
    };

Locale localeDuCode(String? code) => switch (code) {
      'ar' => languesDaba[1],
      'ary-Arab' => languesDaba[2],
      'ary-Latn' => languesDaba[3],
      _ => languesDaba[0],
    };

/// Texte d'un `LibelleI18n` dans la langue courante ; retombe sur `fr` (toujours présent).
String libelle(Object? l, Locale locale) {
  if (l is! Map) return l?.toString() ?? '';
  return (l[codeLangue(locale)] ?? l['fr'] ?? '') as String;
}

/// Material et Cupertino ne connaissent pas la darija : on leur sert l'arabe (RTL) pour
/// l'écriture arabe et le français (LTR) pour l'écriture latine.
class _Repli<L> extends LocalizationsDelegate<L> {
  const _Repli(this.base);
  final LocalizationsDelegate<L> base;

  Locale _vers(Locale l) =>
      l.languageCode != 'ary' ? l : (l.scriptCode == 'Latn' ? const Locale('fr') : const Locale('ar'));

  @override
  bool isSupported(Locale locale) => base.isSupported(_vers(locale));
  @override
  Future<L> load(Locale locale) => base.load(_vers(locale));
  @override
  bool shouldReload(covariant LocalizationsDelegate<L> old) => false;
}

const delegationsDaba = <LocalizationsDelegate<dynamic>>[
  T.delegate,
  _Repli<MaterialLocalizations>(GlobalMaterialLocalizations.delegate),
  _Repli<WidgetsLocalizations>(GlobalWidgetsLocalizations.delegate),
  _Repli<CupertinoLocalizations>(GlobalCupertinoLocalizations.delegate),
];

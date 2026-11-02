import 'package:flutter/material.dart';
import 'package:phosphoricons_flutter/phosphoricons_flutter.dart';

/// IBM Plex, embarquée dans ce package (police commune aux apps et aux portails Daba).
const policeLatine = 'packages/daba_shared/IBMPlexSans';
const policeArabe = 'packages/daba_shared/IBMPlexSansArabic';

/// Arabe et darija en caractères arabes : face arabe ; français et darija latine : face latine.
bool ecritureArabe(Locale l) => l.languageCode == 'ar' || (l.languageCode == 'ary' && l.scriptCode != 'Latn');

/// Thème Daba pour une langue : la police suit l'écriture, l'autre face sert de repli
/// (nom de hanout en arabe sur un écran français, et inversement). Icônes Phosphor, graisse Regular.
ThemeData themeDaba(Locale locale) {
  final arabe = ecritureArabe(locale);
  return ThemeData(
    colorSchemeSeed: const Color(0xFF0B7A55),
    useMaterial3: true,
    fontFamily: arabe ? policeArabe : policeLatine,
    fontFamilyFallback: [arabe ? policeLatine : policeArabe],
    // Flèche de retour : icône de sens, en miroir en arabe.
    actionIconTheme: ActionIconThemeData(backButtonIconBuilder: (_) => const Icon(PhosphorIconsRegular.arrowLeft)),
    segmentedButtonTheme: const SegmentedButtonThemeData(
      selectedIcon: Directionality(textDirection: TextDirection.ltr, child: Icon(PhosphorIconsRegular.check)),
    ),
  );
}

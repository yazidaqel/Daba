import 'package:flutter/material.dart';
import 'package:phosphoricons_flutter/phosphoricons_flutter.dart';

/// IBM Plex, embarquée dans ce package (police commune aux apps et aux portails Daba).
const policeLatine = 'packages/daba_shared/IBMPlexSans';
const policeArabe = 'packages/daba_shared/IBMPlexSansArabic';

/// Arabe et darija en caractères arabes : face arabe ; français et darija latine : face latine.
bool ecritureArabe(Locale l) => l.languageCode == 'ar' || (l.languageCode == 'ary' && l.scriptCode != 'Latn');

/// Jetons de la palette Zellige (`docs/charte-graphique.md`), seule source des couleurs.
/// Ceux qui ne sont pas des rôles Material passent par cette extension du thème.
class Zellige extends ThemeExtension<Zellige> {
  const Zellige({
    required this.primary,
    required this.primarySoft,
    required this.onPrimary,
    required this.promo,
    required this.promoSoft,
    required this.onPromo,
    required this.background,
    required this.surface,
    required this.border,
    required this.text,
    required this.textMuted,
    required this.success,
    required this.successSoft,
    required this.warning,
    required this.warningSoft,
    required this.danger,
    required this.dangerSoft,
  });

  final Color primary, primarySoft, onPrimary;

  /// Safran : promotions uniquement (fil promo, badge promo, ligne promo d'un BL). Jamais un avertissement.
  final Color promo, promoSoft, onPromo;
  final Color background, surface, border, text, textMuted;
  final Color success, successSoft, warning, warningSoft, danger, dangerSoft;

  static const clair = Zellige(
    primary: Color(0xFF0F5E59),
    primarySoft: Color(0xFFE6F2F1),
    onPrimary: Color(0xFFFFFFFF),
    promo: Color(0xFFE0A526),
    promoSoft: Color(0xFFFBEFD3),
    onPromo: Color(0xFF3D2A02),
    background: Color(0xFFF6F1E7),
    surface: Color(0xFFFFFDF8),
    border: Color(0xFFE4DCCB),
    text: Color(0xFF1D2B2A),
    textMuted: Color(0xFF6B6559),
    success: Color(0xFF2E7D32),
    successSoft: Color(0xFFE6F2E6),
    warning: Color(0xFFB45309),
    warningSoft: Color(0xFFFDEBD8),
    danger: Color(0xFFB42318),
    dangerSoft: Color(0xFFFDECEA),
  );

  static const sombre = Zellige(
    primary: Color(0xFF4FB3A9),
    primarySoft: Color(0xFF123B38),
    onPrimary: Color(0xFF062B29),
    promo: Color(0xFFEFC764),
    promoSoft: Color(0xFF3A2C0A),
    onPromo: Color(0xFF3D2A02),
    background: Color(0xFF121A19),
    surface: Color(0xFF1A2423),
    border: Color(0xFF2C3A38),
    text: Color(0xFFECE7DC),
    textMuted: Color(0xFFA39D90),
    success: Color(0xFF6BBF6F),
    successSoft: Color(0xFF16301A),
    warning: Color(0xFFF0A060),
    warningSoft: Color(0xFF3A2410),
    danger: Color(0xFFF2867A),
    dangerSoft: Color(0xFF3A1714),
  );

  static Zellige of(BuildContext context) => Theme.of(context).extension<Zellige>()!;

  @override
  Zellige copyWith() => this;

  @override
  Zellige lerp(Zellige? other, double t) => t < .5 || other == null ? this : other;
}

/// Thème Daba pour une langue et une luminosité (réglage système) : la police suit l'écriture,
/// l'autre face sert de repli (nom de hanout en arabe sur un écran français, et inversement).
/// Icônes Phosphor, graisse Regular.
ThemeData themeDaba(Locale locale, Brightness luminosite) {
  final arabe = ecritureArabe(locale);
  final z = luminosite == Brightness.light ? Zellige.clair : Zellige.sombre;
  final schema = ColorScheme(
    brightness: luminosite,
    primary: z.primary,
    onPrimary: z.onPrimary,
    primaryContainer: z.primarySoft,
    onPrimaryContainer: z.primary,
    // Pas de couleur secondaire dans la charte : les rôles secondaires reprennent le primaire.
    secondary: z.primary,
    onSecondary: z.onPrimary,
    secondaryContainer: z.primarySoft,
    onSecondaryContainer: z.primary,
    tertiary: z.primary,
    onTertiary: z.onPrimary,
    error: z.danger,
    onError: z.surface,
    errorContainer: z.dangerSoft,
    onErrorContainer: z.danger,
    surface: z.surface,
    onSurface: z.text,
    onSurfaceVariant: z.textMuted,
    surfaceContainerLowest: z.surface,
    surfaceContainerLow: z.surface,
    surfaceContainer: z.surface,
    surfaceContainerHigh: z.surface,
    surfaceContainerHighest: z.surface,
    // Contour des champs et boutons : texte secondaire (contraste suffisant) ; séparateurs : `border`.
    outline: z.textMuted,
    outlineVariant: z.border,
    inverseSurface: z.text,
    onInverseSurface: z.background,
    inversePrimary: luminosite == Brightness.light ? Zellige.sombre.primary : Zellige.clair.primary,
    surfaceTint: z.surface,
  );
  return ThemeData(
    colorScheme: schema,
    useMaterial3: true,
    scaffoldBackgroundColor: z.background,
    dividerColor: z.border,
    appBarTheme: AppBarTheme(backgroundColor: z.background, foregroundColor: z.text, surfaceTintColor: z.background),
    navigationBarTheme: NavigationBarThemeData(backgroundColor: z.surface, indicatorColor: z.primarySoft),
    cardTheme: CardThemeData(color: z.surface, surfaceTintColor: z.surface),
    extensions: [z],
    fontFamily: arabe ? policeArabe : policeLatine,
    fontFamilyFallback: [arabe ? policeLatine : policeArabe],
    // Flèche de retour : icône de sens, en miroir en arabe.
    actionIconTheme: ActionIconThemeData(backButtonIconBuilder: (_) => const Icon(PhosphorIconsRegular.arrowLeft)),
    segmentedButtonTheme: const SegmentedButtonThemeData(
      selectedIcon: Directionality(textDirection: TextDirection.ltr, child: Icon(PhosphorIconsRegular.check)),
    ),
  );
}

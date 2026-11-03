import 'dart:io';

import 'package:daba_shared/daba_shared.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

/// La charte (`docs/charte-graphique.md`) est la source unique des couleurs : le thème doit la suivre.
void main() {
  test('les jetons du thème sont ceux de la charte Zellige, en clair et en sombre', () {
    final lignes = File('../../docs/charte-graphique.md').readAsLinesSync();
    final charte = {
      for (final l in lignes)
        if (RegExp(r'^\| `([a-z-]+)` \| `(#[0-9A-F]{6})` \| `(#[0-9A-F]{6})`').firstMatch(l) case final m?)
          m.group(1)!: (m.group(2)!, m.group(3)!),
    };
    String hex(Color c) => '#${(c.toARGB32() & 0xFFFFFF).toRadixString(16).padLeft(6, '0').toUpperCase()}';
    Map<String, String> jetons(Zellige z) => {
          'primary': hex(z.primary), 'primary-soft': hex(z.primarySoft), 'on-primary': hex(z.onPrimary),
          'promo': hex(z.promo), 'promo-soft': hex(z.promoSoft), 'on-promo': hex(z.onPromo),
          'background': hex(z.background), 'surface': hex(z.surface), 'border': hex(z.border),
          'text': hex(z.text), 'text-muted': hex(z.textMuted),
          'success': hex(z.success), 'success-soft': hex(z.successSoft),
          'warning': hex(z.warning), 'warning-soft': hex(z.warningSoft),
          'danger': hex(z.danger), 'danger-soft': hex(z.dangerSoft),
        };
    for (final e in jetons(Zellige.clair).entries) {
      expect(e.value, charte[e.key]!.$1, reason: '${e.key} (clair)');
    }
    for (final e in jetons(Zellige.sombre).entries) {
      expect(e.value, charte[e.key]!.$2, reason: '${e.key} (sombre)');
    }
  });

  test('police selon l\'écriture, thème clair et sombre', () {
    expect(themeDaba(const Locale('ar'), Brightness.light).textTheme.bodyMedium!.fontFamily, policeArabe);
    expect(themeDaba(const Locale.fromSubtags(languageCode: 'ary', scriptCode: 'Latn'), Brightness.dark).textTheme.bodyMedium!.fontFamily,
        policeLatine);
    final sombre = themeDaba(const Locale('fr'), Brightness.dark);
    expect(sombre.colorScheme.primary, Zellige.sombre.primary);
    expect(sombre.extension<Zellige>(), Zellige.sombre);
  });
}

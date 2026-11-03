import 'dart:io';

import 'package:daba_shared/daba_shared.dart';
import 'package:daba_shared/daba_shared.dart' as shared show dh;
import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart' show Locale;
import 'package:path_provider/path_provider.dart';
import 'package:pdf/pdf.dart';
import 'package:pdf/widgets.dart' as pw;

import 'ecrans/vente.dart' show nomNature, nomType;

/// PDF du document, généré sur le téléphone (hors ligne) pour un partage manuel.
Future<File> documentPdf(Map<String, dynamic> doc, T t, Locale locale) async {
  // Polices IBM Plex du package partagé, comme à l'écran.
  Future<pw.Font> police(String fichier) async =>
      pw.Font.ttf(await rootBundle.load('packages/daba_shared/assets/polices/$fichier.ttf'));
  final latine = await police('IBMPlexSans-Regular');
  final latineGras = await police('IBMPlexSans-SemiBold');
  final arabe = await police('IBMPlexSansArabic-Regular');
  final rtl = locale.languageCode == 'ar' || (locale.languageCode == 'ary' && locale.scriptCode != 'Latn');
  final mentions = doc['mentionsLegales'] as Map?;
  String lib(Object? l) => libelle(l, locale);
  // Le PDF n'a pas de glyphe pour les isolats LRI/PDI des montants à l'écran.
  String dh(int centimes) => sansIsolats(shared.dh(centimes));

  final pdf = pw.Document(theme: pw.ThemeData.withFont(base: latine, bold: latineGras, fontFallback: [arabe]));
  pdf.addPage(pw.Page(
    pageFormat: PdfPageFormat.a5,
    textDirection: rtl ? pw.TextDirection.rtl : pw.TextDirection.ltr,
    build: (_) => pw.Column(crossAxisAlignment: pw.CrossAxisAlignment.start, children: [
      pw.Text('${nomType(t, doc['type'] as String)} ${doc['numero']}', style: pw.TextStyle(fontSize: 16, fontWeight: pw.FontWeight.bold)),
      pw.Text(date(doc['emisLe'] as String)),
      pw.SizedBox(height: 8),
      pw.Text('${t.vendeur} : ${doc['ambulant']['nom']}'),
      if (mentions != null) ...[
        pw.Text('ICE ${mentions['ice']} · IF ${mentions['identifiantFiscal']} · RC ${mentions['registreCommerce']}'),
        pw.Text(mentions['assujettiTva'] == true ? t.tvaAssujetti : t.tvaNonAssujetti),
      ],
      pw.Text('${t.client} : ${doc['hanout']['nom']}'),
      pw.SizedBox(height: 12),
      pw.TableHelper.fromTextArray(
        headers: [t.produit, t.quantite, t.prixUnitaire, t.montant],
        data: [
          for (final l in (doc['lignes'] as List).cast<Map>())
            [
              [
                if (l['nature'] != 'ARTICLE') nomNature(t, l['nature'] as String),
                lib(l['libelle']),
                // RG-F3 : référence de la campagne sur la ligne promo
                if (l['campagneNom'] != null) '${t.promo} ${lib(l['campagneNom'])}',
              ].join(' · '),
              l['nature'] == 'ARTICLE' ? '${l['quantite']}' : '',
              l['nature'] == 'ARTICLE' ? dh(l['prixUnitaire'] as int) : '',
              dh(l['montant'] as int),
            ],
        ],
      ),
      pw.SizedBox(height: 12),
      if (doc['tva'] != null) pw.Text('${t.tva} : ${dh(doc['tva'] as int)}'),
      pw.Text('${t.total} : ${dh(doc['total'] as int)}', style: pw.TextStyle(fontWeight: pw.FontWeight.bold)),
      pw.Text('${t.regle} : ${dh(doc['montantRegle'] as int)}'),
      pw.Text('${t.resteACredit} : ${dh(doc['montantCredit'] as int)}'),
    ]),
  ));
  final fichier = File('${(await getTemporaryDirectory()).path}/${doc['numero']}.pdf');
  return fichier.writeAsBytes(await pdf.save());
}

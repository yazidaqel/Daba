import 'dart:convert';
import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:daba_shared/daba_shared.dart';
import 'package:flutter/material.dart';
import 'package:phosphoricons_flutter/phosphoricons_flutter.dart';

import '../ui.dart';

/// Signature du hanout sur l'écran (§3.4), renvoyée en PNG base64 (`ConfirmationHanout.signaturePng`).
/// Le pavé est clair dans les deux thèmes : c'est le papier de la pièce imprimée.
class EcranSignature extends StatefulWidget {
  const EcranSignature({super.key});

  /// Largeur du PNG envoyé : quelques Ko (appareils à 2 Go, coût des données) ; le contrat ne fixe
  /// pas de taille maximale.
  static const largeurPng = 320;

  /// Traits tracés sur un pavé de [source], réduits à [largeurPng] pixels de large.
  static Future<Uint8List> png(List<List<Offset>> traits, Size source) async {
    final echelle = largeurPng / source.width;
    final enregistreur = ui.PictureRecorder();
    final canvas = Canvas(enregistreur)..scale(echelle);
    _Trace(traits).paint(canvas, source);
    final image = await enregistreur.endRecording().toImage(largeurPng, (source.height * echelle).ceil());
    final octets = (await image.toByteData(format: ui.ImageByteFormat.png))!.buffer.asUint8List();
    image.dispose();
    return octets;
  }

  @override
  State<EcranSignature> createState() => _EcranSignatureState();
}

class _EcranSignatureState extends State<EcranSignature> {
  final _traits = <List<Offset>>[];
  Size _taille = Size.zero;

  Future<void> _valider() async {
    final png = await EcranSignature.png(_traits, _taille);
    if (mounted) Navigator.pop(context, base64Encode(png));
  }

  @override
  Widget build(BuildContext context) {
    final t = context.t;
    final z = Zellige.of(context);
    return Scaffold(
      appBar: AppBar(title: Text(t.signatureHanout)),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(children: [
            const Spacer(),
            AspectRatio(
              aspectRatio: 2.5,
              child: LayoutBuilder(builder: (_, c) {
                _taille = c.biggest;
                return Container(
                  decoration: BoxDecoration(
                      color: Zellige.clair.surface, border: Border.all(color: z.border), borderRadius: BorderRadius.circular(12)),
                  child: ClipRRect(
                    borderRadius: BorderRadius.circular(12),
                    child: GestureDetector(
                      onPanStart: (d) => setState(() => _traits.add([d.localPosition])),
                      onPanUpdate: (d) => setState(() => _traits.last.add(d.localPosition)),
                      child: CustomPaint(painter: _Trace(_traits), size: Size.infinite),
                    ),
                  ),
                );
              }),
            ),
            const Spacer(),
            // Actions en bas, à portée du pouce.
            Row(children: [
              Expanded(
                child: OutlinedButton.icon(
                  onPressed: _traits.isEmpty ? null : () => setState(_traits.clear),
                  icon: const Ico(PhosphorIconsRegular.eraser),
                  label: Text(t.effacer),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: FilledButton.icon(
                  onPressed: _traits.isEmpty ? null : _valider,
                  icon: const Ico(PhosphorIconsRegular.signature),
                  label: Text(t.valider),
                ),
              ),
            ]),
          ]),
        ),
      ),
    );
  }
}

class _Trace extends CustomPainter {
  _Trace(this.traits);
  final List<List<Offset>> traits;

  @override
  void paint(Canvas canvas, Size size) {
    final p = Paint()
      ..color = Zellige.clair.text
      ..strokeWidth = 3
      ..strokeCap = StrokeCap.round
      ..strokeJoin = StrokeJoin.round
      ..style = PaintingStyle.stroke;
    for (final trait in traits) {
      if (trait.length == 1) canvas.drawCircle(trait.first, 1.5, p..style = PaintingStyle.fill);
      p.style = PaintingStyle.stroke;
      canvas.drawPath(Path()..addPolygon(trait, false), p);
    }
  }

  @override
  bool shouldRepaint(_Trace old) => true;
}

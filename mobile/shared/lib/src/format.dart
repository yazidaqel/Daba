import 'dart:math';

/// Fragment gauche-à-droite (montant, téléphone, pourcentage) isolé du texte qui l'entoure
/// (LRI…PDI) : en arabe, sans isolement, « -33,00 DH » s'affiche « DH 33,00- » et
/// « +2126… » « 2126…+ ».
String ltr(String s) => '\u2066$s\u2069';

/// Retire les isolats de [ltr] (le PDF n'a pas de glyphe pour eux).
String sansIsolats(String s) => s.replaceAll(RegExp('[\u2066\u2069]'), '');

/// Montant en centimes de dirham → « 125,50 DH » (chiffres latins, usage marocain), isolé par [ltr].
String dh(int centimes) {
  final signe = centimes < 0 ? '-' : '';
  final a = centimes.abs();
  return ltr('$signe${a ~/ 100},${(a % 100).toString().padLeft(2, '0')} DH');
}

/// Saisie « 12,5 » ou « 12.50 » → 1250 centimes ; null si illisible.
int? centimes(String saisie) {
  final v = double.tryParse(saisie.trim().replaceAll(' ', '').replaceAll(',', '.'));
  return v == null || v < 0 ? null : (v * 100).round();
}

String date(String? iso) {
  if (iso == null) return '';
  final d = DateTime.parse(iso).toLocal();
  String p(int n) => n.toString().padLeft(2, '0');
  return '${p(d.day)}/${p(d.month)}/${d.year} ${p(d.hour)}:${p(d.minute)}';
}

/// Distance en mètres, calculée sur l'appareil (aucune position envoyée au serveur, CNDP).
int distanceM(double lat1, double lon1, double lat2, double lon2) {
  const r = 6371000.0;
  double rad(double d) => d * pi / 180;
  final a = pow(sin(rad(lat2 - lat1) / 2), 2) + cos(rad(lat1)) * cos(rad(lat2)) * pow(sin(rad(lon2 - lon1) / 2), 2);
  // Coordonnées hors bornes (donnée erronée) : a peut dépasser 1, asin donnerait NaN.
  return (2 * r * asin(sqrt(a.clamp(0.0, 1.0)))).round();
}

/// Virgule décimale, comme les montants : « 1,2 km ».
String distance(int m) => m < 1000 ? '$m m' : '${(m / 1000).toStringAsFixed(1).replaceAll('.', ',')} km';

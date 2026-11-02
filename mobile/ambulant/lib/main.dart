import 'package:daba_shared/daba_shared.dart';
import 'package:flutter/material.dart';

import 'ecrans/accueil.dart';
import 'ecrans/connexion.dart';
import 'etat.dart';
import 'ui.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(AppAmbulant(await Daba.demarrer()));
}

class AppAmbulant extends StatelessWidget {
  const AppAmbulant(this.daba, {super.key});
  final Daba daba;

  @override
  Widget build(BuildContext context) => DabaScope(
        daba: daba,
        child: ListenableBuilder(
          listenable: daba,
          builder: (context, _) => MaterialApp(
            onGenerateTitle: (c) => T.of(c).titreApp,
            debugShowCheckedModeBanner: false,
            locale: daba.locale,
            supportedLocales: languesDaba,
            localizationsDelegates: delegationsDaba,
            theme: ThemeData(colorSchemeSeed: const Color(0xFF0B7A55), useMaterial3: true),
            home: switch (daba.etape) {
              Etape.connexion => const EcranConnexion(),
              Etape.consentement => const EcranConsentement(),
              Etape.pinCreation => const EcranPin(creation: true),
              Etape.verrouille => const EcranPin(creation: false),
              Etape.pret => const EcranAccueil(),
            },
          ),
        ),
      );
}

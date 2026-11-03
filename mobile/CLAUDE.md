# Apps mobiles Daba (Flutter)

Référence : cahier des charges §3 (app ambulant), §6 (app hanout), §10 (hors ligne, ergonomie, sécurité).

- Flutter 3.47 / Dart 3.13. Deux apps : `ambulant/` et `hanout/`, plus un package partagé pour le code commun (sync, i18n, modèles générés depuis `../api/openapi.yaml`).
- Hors ligne par défaut : base locale SQLite, file d'opérations rejouée à la synchronisation, UUID générés côté appareil. Seules la réservation d'une promo et les actions de dépannage (disponibilité, accepter ou refuser, en route) exigent le réseau, ainsi que le lien signé d'un document à envoyer par SMS (hors ligne, l'app partage le PDF généré sur l'appareil).
- Une journée entière de vente, facturation et encaissement doit tenir sans réseau.
- Langues : darija (arabe et latin), arabe (RTL), français. Aucun texte en dur.
- Cible Android 8+ sur appareils à 2 Go de RAM ; images compressées, sync différentielle.
- Géolocalisation de l'ambulant seulement avec son accord et quand il est « disponible » (CNDP).
- Code PIN local, déconnexion à distance d'un appareil perdu.
- Le contrat d'API `../api/openapi.yaml` se lit, il ne se modifie pas ici : demander le changement côté back-end.

## Structure

- `shared/` (package `daba_shared`) : client HTTP du contrat (`lib/src/api.dart`, corps JSON au format du contrat), base SQLite et file d'opérations (`lib/src/stockage.dart`), synchronisation (`lib/src/synchronisation.dart`), langues et traductions (`lib/src/langues.dart`, `lib/l10n/*.arb`).
- `ambulant/` : app du vendeur ambulant (lot 1). Logique métier hors ligne dans `lib/domaine.dart`, état et appels en ligne dans `lib/etat.dart`, écrans dans `lib/ecrans/`.
- Pas de client généré par openapi-generator : le générateur Dart (7.27) aplatit les `oneOf` à discriminant (`Operation`, `Changement`, `Remise`) sur une seule branche et perdrait des données. Le test `ambulant/test/vente_test.dart` vérifie à la place les opérations produites contre `../api/openapi.yaml`.
- Couleurs : uniquement les jetons de `../docs/charte-graphique.md` (palette Zellige), dans `Zellige` (`ThemeExtension`) et le `ColorScheme` de `themeDaba` ; clair et sombre suivent le système ; un test vérifie les jetons contre la charte. Un état passe par `Statut` (icône + libellé), le safran par `BadgePromo` (promotions seulement).
- Apparence : `shared/lib/src/theme.dart` (police IBM Plex Sans / Plex Sans Arabic selon l'écriture, embarquée dans `shared/assets/polices/`) ; icônes Phosphor Regular (`phosphoricons_flutter`), via le widget `Ico` qui empêche le miroir en arabe ; seules les icônes de sens (flèche, chevron) passent par `Icon` et s'inversent.
- Push FCM (`ambulant/lib/push.dart`) : `google-services.json` n'est pas versionné ; le placer dans `ambulant/android/app/` (projet Firebase, app Android `ma.daba.daba_ambulant`). Sans lui, le plugin Gradle Google services n'est pas appliqué et l'app tourne sans push. Initialisation automatique de FCM coupée (manifeste) : Firebase démarre et le jeton est demandé seulement PIN passé et consentement au texte CNDP v3 (qui nomme FCM et Infobip). Jeton déclaré (`PUT /v1/appareils/{id}/push`) à la synchro, seulement en ligne. Déconnexion hors ligne : fermeture de session et `deleteToken` rejoués au démarrage puis chaque minute. Canaux privés sur l'écran verrouillé. Canaux Android `haute`, `moyenne` (défaut), `basse` (§9), choisis par le serveur ; données du push : `type` (`Canaux.TypeMessage`) et `cibleId`.
- Stock et solde affichés = dernier état serveur + `delta_local` des opérations non encore reflétées ; ces deltas sont effacés après une poussée suivie d'un tirage complet.

## Commandes

Prérequis : Flutter 3.47.9 (Dart 3.13.7), Android SDK, Node (pour le mock).

- Dépendances : `cd mobile/shared && flutter pub get`, puis `cd mobile/ambulant && flutter pub get`
- Traductions (après modification d'un `.arb`) : `cd mobile/shared && flutter gen-l10n` (fichiers générés commités)
- Analyse : `cd mobile && dart analyze shared ambulant`
- Tests : `cd mobile/shared && flutter test`, puis `cd mobile/ambulant && flutter test`
- Mock du contrat (port 4010, à la racine du dépôt) : `npx @stoplight/prism-cli@5 mock -h 0.0.0.0 api/openapi.yaml`
- Émulateur Android 8 à 2 Go de RAM : AVD `daba_api26` (`system-images;android-26;google_apis;x86_64`, `hw.ramSize=2048`), puis `emulator -avd daba_api26`
- NDK 28.2.13676358 requis (SQLite compilé par le package sqlite3). Sous Windows, l'installation automatique de Gradle échoue (`sdkmanager.bat` coupe sur `;`) : l'installer depuis PowerShell avec `sdkmanager --install '"ndk;28.2.13676358"'`.
- Le mock Prism statique répond toujours `plusDeChangements: true` et n'acquitte pas les UUID envoyés : l'app borne le tirage à 40 pages par synchro et garde ses opérations en attente, c'est normal.
- Lancer sur l'émulateur contre le mock : `cd mobile/ambulant && flutter run -d emulator-5554 --dart-define=DABA_MOCK=true` (API par défaut `http://10.0.2.2:4010` ; `DABA_MOCK` envoie `Prefer: example=ambulant` à la connexion pour obtenir la Session d'exemple de l'ambulant)
- Autre serveur : `flutter run --dart-define=DABA_API=http://10.0.2.2:8080` (back-end local)

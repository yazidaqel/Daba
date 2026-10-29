# Apps mobiles Daba (Flutter)

Référence : cahier des charges §3 (app ambulant), §6 (app hanout), §10 (hors ligne, ergonomie, sécurité).

- Flutter 3.47 / Dart 3.13. Deux apps : `ambulant/` et `hanout/`, plus un package partagé pour le code commun (sync, i18n, modèles générés depuis `../api/openapi.yaml`).
- Hors ligne par défaut : base locale SQLite, file d'opérations rejouée à la synchronisation, UUID générés côté appareil. Seule la réservation d'une promo exige le réseau.
- Une journée entière de vente, facturation et encaissement doit tenir sans réseau.
- Langues : darija (arabe et latin), arabe (RTL), français. Aucun texte en dur.
- Cible Android 8+ sur appareils à 2 Go de RAM ; images compressées, sync différentielle.
- Géolocalisation de l'ambulant seulement avec son accord et quand il est « disponible » (CNDP).
- Code PIN local, déconnexion à distance d'un appareil perdu.
- Le contrat d'API `../api/openapi.yaml` se lit, il ne se modifie pas ici : demander le changement côté back-end.

## Commandes

À compléter dès que le squelette existe.

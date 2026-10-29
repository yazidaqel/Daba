# Contrat d'API

`openapi.yaml` (à créer par la session fondations) est le contrat unique entre le back-end, les apps mobiles et les portails web.

- Seul l'agent back-end le modifie ; mobile et web le lisent.
- Tout changement transverse commence ici, puis chaque côté l'implémente.
- Changement incompatible = nouvelle version de l'endpoint, pas de rupture silencieuse (les apps hors ligne tournent avec d'anciennes versions).

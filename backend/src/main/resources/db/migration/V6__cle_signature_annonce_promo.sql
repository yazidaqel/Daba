-- Clé de signature des jetons générée au premier démarrage quand DABA_JWT_SECRET n'est pas fourni :
-- jamais de clé publique par défaut.
CREATE TABLE cle_signature (
  id  int PRIMARY KEY CHECK (id = 1),
  cle text NOT NULL
);

-- Annonce d'un dépôt reçu aux ambulants (offre publiée + notification), différée au début de la campagne.
ALTER TABLE depot ADD COLUMN annonce_le timestamptz;
UPDATE depot SET annonce_le = recu_le WHERE statut IN ('RECU', 'CLOTURE');

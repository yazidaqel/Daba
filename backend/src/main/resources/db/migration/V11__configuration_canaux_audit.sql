-- Couche canaux paramétrée par l'administrateur (§9) : une seule ligne, jamais de secret
-- (clés d'API et comptes de service restent dans l'environnement du serveur).
CREATE TABLE configuration_canaux (
  id            boolean PRIMARY KEY DEFAULT true CHECK (id),
  configuration jsonb NOT NULL,
  modifiee_le   timestamptz NOT NULL DEFAULT now(),
  modifiee_par  uuid
);

-- Journal d'audit des opérations sensibles (§10), jamais modifié.
CREATE TABLE journal_audit (
  id             bigserial PRIMARY KEY,
  le             timestamptz NOT NULL DEFAULT now(),
  utilisateur_id uuid,
  appareil_id    uuid,
  operation      text NOT NULL,
  details        jsonb
);

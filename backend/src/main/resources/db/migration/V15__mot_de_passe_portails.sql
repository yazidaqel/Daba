-- Connexion des portails web par mot de passe (§10, 3 novembre 2026) : empreinte Argon2id seulement
-- (jamais le mot de passe), et jamais pour AMBULANT ni HANOUT, qui restent sur le code SMS (§6).
ALTER TABLE utilisateur ADD COLUMN mot_de_passe_hash text, ADD COLUMN mot_de_passe_modifie_le timestamptz;
ALTER TABLE utilisateur ADD CONSTRAINT mot_de_passe_portails
  CHECK (mot_de_passe_hash IS NULL OR role IN ('GROSSISTE', 'GRAND_CLIENT', 'ADMIN'));

-- Méthode de connexion choisie par l'administrateur (par rôle, exceptions par organisation) : une seule ligne.
CREATE TABLE configuration_authentification (
  id            boolean PRIMARY KEY DEFAULT true CHECK (id),
  configuration jsonb NOT NULL,
  modifiee_le   timestamptz NOT NULL DEFAULT now(),
  modifiee_par  uuid
);

-- Échecs consécutifs de mot de passe par numéro (inscrit ou non : pas d'énumération) et par appareil
-- connu du compte (appareil 0 = tout autre appareil) ; après 5 échecs, attente doublée à chaque échec
-- (30 s à 15 min). Purgé 24 h après le dernier essai (CNDP).
CREATE TABLE tentative_mot_de_passe (
  telephone      text NOT NULL,
  appareil       uuid NOT NULL,
  echecs         int NOT NULL,
  prochain_essai timestamptz,
  modifie_le     timestamptz NOT NULL,
  PRIMARY KEY (telephone, appareil)
);
CREATE INDEX tentative_mot_de_passe_par_date ON tentative_mot_de_passe (modifie_le);

-- MOT_DE_PASSE_ET_OTP : nonce du défi qui a demandé ce code ; le défi ne vaut qu'avec ce code-là.
ALTER TABLE otp ADD COLUMN defi uuid;

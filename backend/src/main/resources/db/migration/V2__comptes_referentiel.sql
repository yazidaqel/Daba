-- Outbox : registre de publication d'événements de Spring Modulith 2.1 (schéma v2, PostgreSQL).
CREATE TABLE event_publication (
  id                     uuid NOT NULL PRIMARY KEY,
  listener_id            text NOT NULL,
  event_type             text NOT NULL,
  serialized_event       text NOT NULL,
  publication_date       timestamptz NOT NULL,
  completion_date        timestamptz,
  status                 text,
  completion_attempts    int,
  last_resubmission_date timestamptz
);
CREATE INDEX event_publication_serialized_event_hash_idx ON event_publication USING hash (serialized_event);
CREATE INDEX event_publication_by_completion_date_idx ON event_publication (completion_date);

-- Commun : journal des changements tirés par l'app ambulant (destinataire null = tous les ambulants).
CREATE SEQUENCE changement_seq;
CREATE TABLE changement (
  seq          bigint NOT NULL,
  destinataire uuid,
  entite       text NOT NULL,
  entite_id    uuid NOT NULL,
  action       text NOT NULL CHECK (action IN ('UPSERT', 'SUPPRESSION')),
  donnees      jsonb,
  modifie_le   timestamptz NOT NULL,
  CONSTRAINT changement_objet UNIQUE NULLS NOT DISTINCT (entite, entite_id, destinataire)
);
CREATE INDEX changement_par_seq ON changement (seq);
CREATE TABLE changement_purge (seq bigint NOT NULL);

-- Comptes
CREATE TABLE organisation (
  id      uuid PRIMARY KEY,
  type    text NOT NULL CHECK (type IN ('AMBULANT', 'HANOUT', 'GROSSISTE', 'GRAND_CLIENT', 'PLATEFORME')),
  nom     text NOT NULL,
  cree_le timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE utilisateur (
  id              uuid PRIMARY KEY,
  organisation_id uuid NOT NULL REFERENCES organisation (id),
  telephone       text NOT NULL UNIQUE,
  nom             text,
  role            text NOT NULL CHECK (role IN ('AMBULANT', 'HANOUT', 'GROSSISTE', 'GRAND_CLIENT', 'ADMIN')),
  hanout_id       uuid,
  cree_le         timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX utilisateur_par_organisation ON utilisateur (organisation_id);

CREATE TABLE appareil (
  id                 uuid PRIMARY KEY,
  utilisateur_id     uuid NOT NULL REFERENCES utilisateur (id),
  prefixe            text UNIQUE,
  jeton_push         text,
  plateforme         text,
  langue             text,
  revoque            boolean NOT NULL DEFAULT false,
  cree_le            timestamptz NOT NULL DEFAULT now(),
  derniere_connexion timestamptz
);
CREATE INDEX appareil_par_utilisateur ON appareil (utilisateur_id);

-- Code OTP en cours par numéro : seule l'empreinte HMAC du code est conservée.
CREATE TABLE otp (
  telephone     text PRIMARY KEY,
  code_hash     text NOT NULL,
  expire_le     timestamptz NOT NULL,
  essais        int NOT NULL DEFAULT 0,
  envois        int NOT NULL DEFAULT 1,
  fenetre_debut timestamptz NOT NULL DEFAULT now()
);

-- Consentements CNDP (loi 09-08) : journal horodaté, jamais modifié.
CREATE TABLE consentement (
  id                 bigserial PRIMARY KEY,
  utilisateur_id     uuid NOT NULL REFERENCES utilisateur (id),
  version_texte      text NOT NULL,
  traitement_donnees boolean NOT NULL,
  geolocalisation    boolean,
  langue             text,
  enregistre_le      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX consentement_par_utilisateur ON consentement (utilisateur_id, id DESC);

-- Canaux : trace des envois (jamais le texte, qui peut contenir un code OTP).
CREATE TABLE notification (
  id             bigserial PRIMARY KEY,
  type           text NOT NULL,
  utilisateur_id uuid,
  canal          text NOT NULL,
  envoyee        boolean NOT NULL,
  le             timestamptz NOT NULL DEFAULT now()
);

-- Référentiel
CREATE TABLE categorie (
  code    text PRIMARY KEY,
  libelle jsonb NOT NULL
);

CREATE TABLE produit (
  id                         uuid PRIMARY KEY,
  ean                        text NOT NULL UNIQUE,
  libelle                    jsonb NOT NULL,
  marque                     text NOT NULL,
  categorie                  text NOT NULL REFERENCES categorie (code),
  conditionnement            text NOT NULL CHECK (conditionnement IN ('UNITE', 'PACK', 'CARTON')),
  unites_par_conditionnement int NOT NULL CHECK (unites_par_conditionnement > 0),
  photo_url                  text,
  grand_client_id            uuid
);

-- Base POS fournie par RED, plus les hanouts créés sur le terrain par les ambulants.
CREATE TABLE hanout (
  id               uuid PRIMARY KEY,
  code_pos         text UNIQUE,
  nom              text NOT NULL,
  gerant           text,
  telephone        text,
  position         geography(Point, 4326),
  adresse          text,
  ville            text,
  photo_facade_url text,
  ice              text,
  cree_par         uuid,
  cree_le          timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX hanout_par_position ON hanout USING gist (position);
CREATE INDEX hanout_par_telephone ON hanout (telephone);

CREATE TABLE grossiste (
  id       uuid PRIMARY KEY,
  nom      text NOT NULL,
  position geography(Point, 4326) NOT NULL,
  adresse  text,
  ville    text
);
CREATE INDEX grossiste_par_position ON grossiste USING gist (position);

-- Disponibilité de l'ambulant : position conservée seulement tant qu'il est disponible (CNDP).
CREATE TABLE disponibilite (
  ambulant_id uuid PRIMARY KEY,
  disponible  boolean NOT NULL,
  depuis      timestamptz,
  position    geography(Point, 4326),
  position_le timestamptz,
  CHECK (disponible OR position IS NULL)
);
CREATE INDEX disponibilite_par_position ON disponibilite USING gist (position) WHERE disponible;

-- Demandes de dépannage (§7.1).
CREATE TABLE demande (
  id                    uuid PRIMARY KEY,
  hanout_id             uuid NOT NULL,
  hanout_utilisateur_id uuid NOT NULL,
  produit_id            uuid,
  photo_id              uuid,
  quantite              int NOT NULL CHECK (quantite > 0),
  statut                text NOT NULL DEFAULT 'EN_ATTENTE'
    CHECK (statut IN ('EN_ATTENTE', 'ACCEPTEE', 'EN_ROUTE', 'LIVREE', 'SANS_REPONSE')),
  position              geography(Point, 4326) NOT NULL,
  ville                 text,
  rayon_m               int NOT NULL,
  rayon_elargi          boolean NOT NULL DEFAULT false,
  expire_le             timestamptz NOT NULL,
  ambulant_id           uuid,
  document_id           uuid,
  historique            jsonb NOT NULL,
  cree_le               timestamptz NOT NULL DEFAULT now(),
  livree_le             timestamptz,
  CHECK (produit_id IS NOT NULL OR photo_id IS NOT NULL)
);
CREATE INDEX demande_par_hanout ON demande (hanout_id, cree_le DESC);
CREATE INDEX demande_en_attente ON demande (expire_le) WHERE statut = 'EN_ATTENTE';
CREATE INDEX demande_par_produit ON demande (produit_id, cree_le);

-- Ambulants à qui la demande a été envoyée (RG-D1), et leur refus éventuel.
CREATE TABLE demande_destinataire (
  demande_id  uuid NOT NULL REFERENCES demande (id),
  ambulant_id uuid NOT NULL,
  distance_m  int NOT NULL,
  refusee     boolean NOT NULL DEFAULT false,
  PRIMARY KEY (demande_id, ambulant_id)
);
CREATE INDEX demande_destinataire_par_ambulant ON demande_destinataire (ambulant_id);

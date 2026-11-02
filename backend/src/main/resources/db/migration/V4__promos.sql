-- Photos (bon de livraison d'un dépôt, rupture signalée en photo).
-- ponytail: en base ; passer à un stockage objet compatible S3 si le volume grossit.
CREATE TABLE photo (
  id           uuid PRIMARY KEY,
  type_contenu text NOT NULL,
  contenu      bytea NOT NULL,
  cree_le      timestamptz NOT NULL DEFAULT now()
);

-- Promos (§7.2)
CREATE TABLE campagne (
  id                   uuid PRIMARY KEY,
  grand_client_id      uuid NOT NULL,
  nom                  jsonb NOT NULL,
  description          jsonb,
  debut                timestamptz NOT NULL,
  fin                  timestamptz NOT NULL,
  ciblage              jsonb,
  delai_retrait_heures int NOT NULL DEFAULT 24,
  statut               text NOT NULL DEFAULT 'BROUILLON'
    CHECK (statut IN ('BROUILLON', 'PROPOSEE', 'ACTIVE', 'EPUISEE', 'EXPIREE', 'CLOTUREE')),
  cree_le              timestamptz NOT NULL DEFAULT now(),
  CHECK (fin > debut)
);
CREATE INDEX campagne_par_grand_client ON campagne (grand_client_id, cree_le DESC);

CREATE TABLE campagne_ligne (
  campagne_id               uuid NOT NULL REFERENCES campagne (id) ON DELETE CASCADE,
  produit_id                uuid NOT NULL,
  remise                    jsonb NOT NULL,
  quantite_totale           int NOT NULL CHECK (quantite_totale > 0),
  quantite_max_par_ambulant int NOT NULL CHECK (quantite_max_par_ambulant > 0),
  prix_revente_conseille    bigint,
  PRIMARY KEY (campagne_id, produit_id)
);

CREATE TABLE depot (
  id           uuid PRIMARY KEY,
  campagne_id  uuid NOT NULL REFERENCES campagne (id) ON DELETE CASCADE,
  grossiste_id uuid NOT NULL,
  statut       text NOT NULL DEFAULT 'PROPOSE'
    CHECK (statut IN ('PROPOSE', 'CONTRE_PROPOSE', 'ACCEPTE', 'REFUSE', 'RECU', 'CLOTURE')),
  commentaire  text,
  recu_le      timestamptz,
  photo_id     uuid REFERENCES photo (id),
  cloture      jsonb,
  cree_le      timestamptz NOT NULL DEFAULT now(),
  UNIQUE (campagne_id, grossiste_id)
);
CREATE INDEX depot_par_grossiste ON depot (grossiste_id, cree_le DESC);

-- Une ligne de dépôt = une offre promo (campagne × dépôt × produit) ; son id est l'offreId.
CREATE TABLE depot_ligne (
  id                       uuid PRIMARY KEY,
  depot_id                 uuid NOT NULL REFERENCES depot (id) ON DELETE CASCADE,
  produit_id               uuid NOT NULL,
  quantite_proposee        int NOT NULL CHECK (quantite_proposee > 0),
  quantite_contre_proposee int,
  quantite_deposee         int NOT NULL DEFAULT 0,
  UNIQUE (depot_id, produit_id)
);

CREATE TABLE reservation (
  id               uuid PRIMARY KEY,
  offre_id         uuid NOT NULL REFERENCES depot_ligne (id),
  depot_id         uuid NOT NULL,
  campagne_id      uuid NOT NULL,
  produit_id       uuid NOT NULL,
  ambulant_id      uuid NOT NULL,
  quantite_reservee int NOT NULL CHECK (quantite_reservee > 0),
  quantite_retiree int,
  statut           text NOT NULL DEFAULT 'RESERVEE' CHECK (statut IN ('RESERVEE', 'RETIREE', 'EXPIREE')),
  reservee_le      timestamptz NOT NULL DEFAULT now(),
  expire_le        timestamptz NOT NULL,
  retiree_le       timestamptz,
  rappel_envoye    boolean NOT NULL DEFAULT false
);
CREATE INDEX reservation_par_offre ON reservation (offre_id);
CREATE INDEX reservation_par_ambulant ON reservation (ambulant_id, campagne_id, produit_id);
CREATE INDEX reservation_a_expirer ON reservation (expire_le) WHERE statut = 'RESERVEE';

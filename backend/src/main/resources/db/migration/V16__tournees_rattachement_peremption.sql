-- §3.3 : tournées planifiées, remplacées en entier par l'opération TOURNEE. Une tournée supprimée
-- reste en base (supprimee) pour qu'une modification plus ancienne venue d'un autre appareil soit
-- reconnue comme un conflit. hanouts : liste ordonnée d'identifiants de hanout.
CREATE TABLE tournee (
  id                  uuid PRIMARY KEY,
  ambulant_id         uuid NOT NULL REFERENCES utilisateur (id),
  nom                 text,
  jours               jsonb NOT NULL DEFAULT '[]',
  hanouts             jsonb NOT NULL DEFAULT '[]',
  supprimee           boolean NOT NULL DEFAULT false,
  modifie_le_appareil timestamptz NOT NULL
);
CREATE INDEX tournee_par_ambulant ON tournee (ambulant_id) WHERE NOT supprimee;

-- §3.3 : visites sans vente (une vente est déjà une visite). id = celui de l'opération VISITE.
CREATE TABLE visite (
  id          uuid PRIMARY KEY,
  ambulant_id uuid NOT NULL REFERENCES utilisateur (id),
  appareil_id uuid NOT NULL,
  hanout_id   uuid NOT NULL REFERENCES hanout (id),
  horodatage  timestamptz NOT NULL,
  motif       text NOT NULL CHECK (motif IN ('FERME', 'PAS_BESOIN', 'GERANT_ABSENT', 'AUTRE')),
  recu_le     timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX visite_par_ambulant ON visite (ambulant_id, hanout_id, horodatage DESC);

-- Zone de tournée de repli (§9) : hanouts servis dans les 30 derniers jours.
CREATE INDEX document_par_ambulant ON document (ambulant_id, emis_le DESC);

-- §3.1 : grossistes fournisseurs déclarés par l'ambulant (rattachement, priorité des promos §9).
ALTER TABLE profil_ambulant ADD COLUMN grossistes jsonb NOT NULL DEFAULT '[]';
CREATE INDEX profil_ambulant_par_grossiste ON profil_ambulant USING gin (grossistes);

-- §3.2, §3.8 : date de péremption et prix d'achat d'une ligne chargée (CHARGEMENT seulement).
ALTER TABLE mouvement
  ADD COLUMN date_peremption date,
  ADD COLUMN prix_unitaire bigint CHECK (prix_unitaire >= 0);

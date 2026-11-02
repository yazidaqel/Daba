-- Stock : le stock embarqué est la somme des mouvements (RG-S2) ; jamais stocké à part.
CREATE TABLE mouvement (
  id              uuid PRIMARY KEY,
  ambulant_id     uuid NOT NULL,
  organisation_id uuid NOT NULL,
  type            text NOT NULL CHECK (type IN ('CHARGEMENT', 'VENTE', 'RETOUR', 'AJUSTEMENT', 'PROMO')),
  produit_id      uuid NOT NULL,
  quantite        int NOT NULL,
  auteur_id       uuid NOT NULL,
  appareil_id     uuid NOT NULL,
  horodatage      timestamptz NOT NULL,
  recu_le         timestamptz NOT NULL DEFAULT now(),
  operation_id    uuid,
  document_id     uuid,
  bon_sortie_id   uuid,
  campagne_id     uuid,
  reservation_id  uuid,
  motif           text
);
CREATE INDEX mouvement_par_ambulant ON mouvement (ambulant_id, produit_id);
CREATE INDEX mouvement_par_reservation ON mouvement (reservation_id) WHERE reservation_id IS NOT NULL;

CREATE SEQUENCE bon_sortie_numero;
CREATE TABLE bon_sortie (
  id           uuid PRIMARY KEY,
  numero       text NOT NULL UNIQUE,
  grossiste_id uuid NOT NULL,
  ambulant_id  uuid NOT NULL,
  lignes       jsonb NOT NULL,
  emis_le      timestamptz NOT NULL DEFAULT now(),
  charge_le    timestamptz
);
CREATE INDEX bon_sortie_par_grossiste ON bon_sortie (grossiste_id, emis_le DESC);
CREATE INDEX bon_sortie_par_ambulant ON bon_sortie (ambulant_id);

-- Ventes : documents jamais modifiés ni supprimés (RG-C4) ; un avoir est un nouveau document.
CREATE TABLE document (
  id                   uuid PRIMARY KEY,
  type                 text NOT NULL CHECK (type IN ('BON_LIVRAISON', 'FACTURE', 'AVOIR')),
  numero               text NOT NULL,
  serie                text NOT NULL,
  sequence             bigint NOT NULL,
  appareil_id          uuid NOT NULL,
  ambulant_id          uuid NOT NULL,
  organisation_id      uuid NOT NULL,
  hanout_id            uuid NOT NULL,
  emis_le              timestamptz NOT NULL,
  recu_le              timestamptz NOT NULL DEFAULT now(),
  lignes               jsonb NOT NULL,
  total                bigint NOT NULL,
  montant_regle        bigint NOT NULL,
  montant_credit       bigint NOT NULL,
  reglements           jsonb,
  document_origine_id  uuid REFERENCES document (id),
  motif_avoir          text,
  commentaire          text,
  demande_depannage_id uuid,
  mentions_legales     jsonb,
  confirmation_hanout  jsonb,
  operation_id         uuid
);
CREATE INDEX document_par_organisation ON document (organisation_id, emis_le DESC);
CREATE INDEX document_par_hanout ON document (hanout_id, emis_le DESC);
CREATE INDEX document_par_serie ON document (serie, sequence);
CREATE INDEX document_par_numero ON document (numero);
CREATE INDEX document_par_origine ON document (document_origine_id) WHERE document_origine_id IS NOT NULL;

-- Interdit toute modification ou suppression d'un document émis (RG-C4).
CREATE FUNCTION document_immuable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION 'RG-C4 : un document émis ne se modifie ni ne se supprime ; émettre un avoir';
END $$;
CREATE TRIGGER document_immuable BEFORE UPDATE OR DELETE ON document FOR EACH ROW EXECUTE FUNCTION document_immuable();

-- Karni : un compte de crédit par couple ambulant ↔ hanout.
CREATE TABLE compte_credit (
  id                  uuid PRIMARY KEY,
  ambulant_id         uuid NOT NULL,
  organisation_id     uuid NOT NULL,
  hanout_id           uuid NOT NULL,
  plafond             bigint,
  delai_max_jours     int,
  modifie_le_appareil timestamptz,
  UNIQUE (ambulant_id, hanout_id)
);
CREATE INDEX compte_credit_par_hanout ON compte_credit (hanout_id);

CREATE TABLE versement (
  id              uuid PRIMARY KEY,
  ambulant_id     uuid NOT NULL,
  organisation_id uuid NOT NULL,
  hanout_id       uuid NOT NULL,
  mode            text NOT NULL CHECK (mode IN ('ESPECES', 'CHEQUE')),
  montant         bigint NOT NULL CHECK (montant > 0),
  cheque_numero   text,
  cheque_banque   text,
  verse_le        timestamptz NOT NULL,
  recu_le         timestamptz NOT NULL DEFAULT now(),
  operation_id    uuid
);
CREATE INDEX versement_par_compte ON versement (ambulant_id, hanout_id);

CREATE TABLE imputation (
  versement_id uuid NOT NULL REFERENCES versement (id),
  document_id  uuid NOT NULL REFERENCES document (id),
  montant      bigint NOT NULL CHECK (montant > 0),
  PRIMARY KEY (versement_id, document_id)
);
CREATE INDEX imputation_par_document ON imputation (document_id);

CREATE TABLE tarif (
  id                  uuid PRIMARY KEY,
  ambulant_id         uuid NOT NULL,
  produit_id          uuid NOT NULL,
  hanout_id           uuid,
  prix_unitaire       bigint NOT NULL CHECK (prix_unitaire >= 0),
  modifie_le_appareil timestamptz NOT NULL,
  CONSTRAINT tarif_unique UNIQUE NULLS NOT DISTINCT (ambulant_id, produit_id, hanout_id)
);

CREATE TABLE parametres_ambulant (
  ambulant_id                 uuid PRIMARY KEY,
  politique_depassement       text NOT NULL DEFAULT 'AVERTIR' CHECK (politique_depassement IN ('BLOQUER', 'AVERTIR')),
  plafond_remise_pourcent     numeric(5, 2),
  seuil_ecart_pourcent        numeric(5, 2) NOT NULL DEFAULT 5,
  mentions_legales            jsonb,
  modifie_le_appareil         timestamptz
);

-- Synchronisation : chaque opération reçue est journalisée, appliquée ou rejetée (idempotence par UUID).
CREATE TABLE operation_sync (
  id          uuid PRIMARY KEY,
  ambulant_id uuid NOT NULL,
  appareil_id uuid NOT NULL,
  type        text NOT NULL,
  cree_le     timestamptz,
  recu_le     timestamptz NOT NULL DEFAULT now(),
  statut      text NOT NULL CHECK (statut IN ('APPLIQUEE', 'EN_CONFLIT', 'REJETEE')),
  contenu     jsonb NOT NULL,
  resultat    jsonb
);
CREATE INDEX operation_sync_par_ambulant ON operation_sync (ambulant_id, recu_le DESC);

CREATE TABLE anomalie (
  id           uuid PRIMARY KEY,
  ambulant_id  uuid NOT NULL,
  operation_id uuid,
  type         text NOT NULL,
  regle        text NOT NULL,
  produit_id   uuid,
  detail       text,
  statut       text NOT NULL DEFAULT 'OUVERTE' CHECK (statut IN ('OUVERTE', 'RESOLUE')),
  detectee_le  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX anomalie_par_ambulant ON anomalie (ambulant_id, detectee_le DESC);

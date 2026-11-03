-- Plusieurs comptes sur un même téléphone (préfixe par couple ambulant/appareil, RG-F1) : l'appareil
-- garde un seul compte courant (sa session), et cet historique dit quels comptes l'ont utilisé et quand.
-- La révocation reste par appareil physique (§10) : elle vaut pour tous les comptes de l'historique.
CREATE TABLE appareil_utilisateur (
  utilisateur_id     uuid NOT NULL REFERENCES utilisateur (id),
  appareil_id        uuid NOT NULL REFERENCES appareil (id),
  premiere_connexion timestamptz NOT NULL,
  derniere_connexion timestamptz,
  vu_le              timestamptz,
  PRIMARY KEY (utilisateur_id, appareil_id)
);
CREATE INDEX appareil_utilisateur_par_appareil ON appareil_utilisateur (appareil_id);

-- Reprise : le compte courant de chaque appareil, puis les ambulants passés avant lui, connus par
-- leur préfixe (dates inconnues : première connexion = création de l'appareil).
INSERT INTO appareil_utilisateur (utilisateur_id, appareil_id, premiere_connexion, derniere_connexion, vu_le)
SELECT utilisateur_id, id, cree_le, derniere_connexion, vu_le FROM appareil;
INSERT INTO appareil_utilisateur (utilisateur_id, appareil_id, premiere_connexion)
SELECT p.utilisateur_id, p.appareil_id, a.cree_le FROM prefixe_numerotation p JOIN appareil a ON a.id = p.appareil_id
ON CONFLICT DO NOTHING;

-- Dernier appel authentifié : par compte désormais (V13 le gardait par appareil).
ALTER TABLE appareil DROP COLUMN vu_le;

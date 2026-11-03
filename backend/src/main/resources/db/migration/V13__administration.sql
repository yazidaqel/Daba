-- Back-office (§2, §10) : compte désactivé par l'administrateur (null = actif), révocation datée
-- d'un appareil, dernier appel authentifié (support), lecture du journal d'audit par date.
ALTER TABLE utilisateur ADD COLUMN desactive_le timestamptz;
ALTER TABLE appareil ADD COLUMN revoque_le timestamptz, ADD COLUMN vu_le timestamptz;
CREATE INDEX journal_audit_par_date ON journal_audit (le DESC, id DESC);

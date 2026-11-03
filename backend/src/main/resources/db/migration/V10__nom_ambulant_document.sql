-- CNDP (§5.4) et RG-C4 : le nom de l'ambulant imprimé sur une pièce est figé à son émission ; les
-- listes retombent sur le téléphone masqué quand il retire son consentement, pas les pièces émises.
ALTER TABLE document ADD COLUMN ambulant_nom text;
UPDATE document d SET ambulant_nom = u.nom FROM utilisateur u WHERE u.id = d.ambulant_id AND u.nom IS NOT NULL;

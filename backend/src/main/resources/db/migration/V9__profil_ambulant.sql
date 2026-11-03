-- §3.1 : profil de l'ambulant. Le nom reste dans utilisateur.nom ; ICE, IF, RC et TVA dans
-- parametres_ambulant.mentions_legales (RG-F2). villes et categories : codes du référentiel.
CREATE TABLE profil_ambulant (
  utilisateur_id      uuid PRIMARY KEY REFERENCES utilisateur (id),
  cin                 text,
  vehicule            text,
  villes              jsonb NOT NULL DEFAULT '[]',
  categories          jsonb NOT NULL DEFAULT '[]',
  modifie_le_appareil timestamptz NOT NULL
);

-- §9 : ambulants qui ont déjà acheté une marque (ciblage d'une promo par catégorie).
CREATE INDEX mouvement_par_produit ON mouvement (produit_id, ambulant_id);

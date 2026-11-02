-- RG-F1 : préfixe de numérotation par couple (ambulant, appareil), plus par appareil seul.
CREATE TABLE prefixe_numerotation (
  utilisateur_id uuid NOT NULL REFERENCES utilisateur (id),
  appareil_id    uuid NOT NULL REFERENCES appareil (id),
  prefixe        text NOT NULL UNIQUE,
  PRIMARY KEY (utilisateur_id, appareil_id)
);
INSERT INTO prefixe_numerotation (utilisateur_id, appareil_id, prefixe)
SELECT utilisateur_id, id, prefixe FROM appareil WHERE prefixe IS NOT NULL;
ALTER TABLE appareil DROP COLUMN prefixe;

-- Déconnexion côté serveur : un jeton n'est valable que pour la session en cours de l'appareil.
ALTER TABLE appareil ADD COLUMN session uuid;

-- RG-F2 : taux de TVA par produit (null = inconnu).
ALTER TABLE produit ADD COLUMN taux_tva numeric(4, 2) CHECK (taux_tva IN (0, 7, 10, 14, 20));

-- Un mouvement PROMO (retrait chez le grossiste) porte l'id de sa réservation comme operationId.
UPDATE mouvement SET operation_id = reservation_id WHERE operation_id IS NULL AND type = 'PROMO';
ALTER TABLE mouvement ALTER COLUMN operation_id SET NOT NULL;

-- Réception d'une livraison confirmée par le hanout (§6), à part du document immuable (RG-C4).
CREATE TABLE reception_hanout (
  document_id    uuid PRIMARY KEY REFERENCES document (id),
  id             uuid NOT NULL UNIQUE,
  utilisateur_id uuid NOT NULL,
  conforme       boolean NOT NULL,
  ecarts         jsonb,
  commentaire    text,
  le             timestamptz NOT NULL DEFAULT now()
);

-- Contestation d'un document ou d'un solde par le hanout (§6), close par l'ambulant.
CREATE TABLE contestation (
  id                     uuid PRIMARY KEY,
  hanout_id              uuid NOT NULL,
  utilisateur_id         uuid NOT NULL,
  ambulant_id            uuid NOT NULL,
  cible                  text NOT NULL CHECK (cible IN ('DOCUMENT', 'COMPTE_CREDIT')),
  cible_id               uuid NOT NULL,
  motif                  text NOT NULL CHECK (motif IN ('MONTANT', 'QUANTITE', 'NON_RECU', 'DEJA_PAYE', 'AUTRE')),
  commentaire            text,
  statut                 text NOT NULL DEFAULT 'OUVERTE' CHECK (statut IN ('OUVERTE', 'RESOLUE')),
  cree_le                timestamptz NOT NULL DEFAULT now(),
  resolue_le             timestamptz,
  commentaire_resolution text
);
CREATE UNIQUE INDEX contestation_ouverte_par_cible ON contestation (cible_id) WHERE statut = 'OUVERTE';
CREATE INDEX contestation_par_hanout ON contestation (hanout_id, cree_le DESC);
CREATE INDEX contestation_par_ambulant ON contestation (ambulant_id, cree_le DESC);

-- Référentiel géographique du ciblage (§5.1) : les 12 régions et leurs principales villes.
CREATE TABLE region (
  code text PRIMARY KEY,
  nom  jsonb NOT NULL
);
CREATE TABLE ville (
  code        text PRIMARY KEY,
  region_code text NOT NULL REFERENCES region (code),
  nom         jsonb NOT NULL
);
INSERT INTO region (code, nom) VALUES
  ('TANGER_TETOUAN_AL_HOCEIMA', '{"fr":"Tanger-Tétouan-Al Hoceïma","ar":"طنجة تطوان الحسيمة"}'),
  ('ORIENTAL', '{"fr":"L''Oriental","ar":"الشرق"}'),
  ('FES_MEKNES', '{"fr":"Fès-Meknès","ar":"فاس مكناس"}'),
  ('RABAT_SALE_KENITRA', '{"fr":"Rabat-Salé-Kénitra","ar":"الرباط سلا القنيطرة"}'),
  ('BENI_MELLAL_KHENIFRA', '{"fr":"Béni Mellal-Khénifra","ar":"بني ملال خنيفرة"}'),
  ('CASABLANCA_SETTAT', '{"fr":"Casablanca-Settat","ar":"الدار البيضاء سطات"}'),
  ('MARRAKECH_SAFI', '{"fr":"Marrakech-Safi","ar":"مراكش آسفي"}'),
  ('DRAA_TAFILALET', '{"fr":"Drâa-Tafilalet","ar":"درعة تافيلالت"}'),
  ('SOUSS_MASSA', '{"fr":"Souss-Massa","ar":"سوس ماسة"}'),
  ('GUELMIM_OUED_NOUN', '{"fr":"Guelmim-Oued Noun","ar":"كلميم واد نون"}'),
  ('LAAYOUNE_SAKIA_EL_HAMRA', '{"fr":"Laâyoune-Sakia El Hamra","ar":"العيون الساقية الحمراء"}'),
  ('DAKHLA_OUED_ED_DAHAB', '{"fr":"Dakhla-Oued Ed-Dahab","ar":"الداخلة وادي الذهب"}');
INSERT INTO ville (code, region_code, nom) VALUES
  ('TANGER', 'TANGER_TETOUAN_AL_HOCEIMA', '{"fr":"Tanger","ar":"طنجة"}'),
  ('TETOUAN', 'TANGER_TETOUAN_AL_HOCEIMA', '{"fr":"Tétouan","ar":"تطوان"}'),
  ('AL_HOCEIMA', 'TANGER_TETOUAN_AL_HOCEIMA', '{"fr":"Al Hoceïma","ar":"الحسيمة"}'),
  ('LARACHE', 'TANGER_TETOUAN_AL_HOCEIMA', '{"fr":"Larache","ar":"العرائش"}'),
  ('KSAR_EL_KEBIR', 'TANGER_TETOUAN_AL_HOCEIMA', '{"fr":"Ksar El Kébir","ar":"القصر الكبير"}'),
  ('CHEFCHAOUEN', 'TANGER_TETOUAN_AL_HOCEIMA', '{"fr":"Chefchaouen","ar":"شفشاون"}'),
  ('OUJDA', 'ORIENTAL', '{"fr":"Oujda","ar":"وجدة"}'),
  ('NADOR', 'ORIENTAL', '{"fr":"Nador","ar":"الناظور"}'),
  ('BERKANE', 'ORIENTAL', '{"fr":"Berkane","ar":"بركان"}'),
  ('TAOURIRT', 'ORIENTAL', '{"fr":"Taourirt","ar":"تاوريرت"}'),
  ('FES', 'FES_MEKNES', '{"fr":"Fès","ar":"فاس"}'),
  ('MEKNES', 'FES_MEKNES', '{"fr":"Meknès","ar":"مكناس"}'),
  ('TAZA', 'FES_MEKNES', '{"fr":"Taza","ar":"تازة"}'),
  ('IFRANE', 'FES_MEKNES', '{"fr":"Ifrane","ar":"إفران"}'),
  ('RABAT', 'RABAT_SALE_KENITRA', '{"fr":"Rabat","ar":"الرباط"}'),
  ('SALE', 'RABAT_SALE_KENITRA', '{"fr":"Salé","ar":"سلا"}'),
  ('KENITRA', 'RABAT_SALE_KENITRA', '{"fr":"Kénitra","ar":"القنيطرة"}'),
  ('TEMARA', 'RABAT_SALE_KENITRA', '{"fr":"Témara","ar":"تمارة"}'),
  ('KHEMISSET', 'RABAT_SALE_KENITRA', '{"fr":"Khémisset","ar":"الخميسات"}'),
  ('BENI_MELLAL', 'BENI_MELLAL_KHENIFRA', '{"fr":"Béni Mellal","ar":"بني ملال"}'),
  ('KHOURIBGA', 'BENI_MELLAL_KHENIFRA', '{"fr":"Khouribga","ar":"خريبكة"}'),
  ('KHENIFRA', 'BENI_MELLAL_KHENIFRA', '{"fr":"Khénifra","ar":"خنيفرة"}'),
  ('FQUIH_BEN_SALAH', 'BENI_MELLAL_KHENIFRA', '{"fr":"Fquih Ben Salah","ar":"الفقيه بن صالح"}'),
  ('CASABLANCA', 'CASABLANCA_SETTAT', '{"fr":"Casablanca","ar":"الدار البيضاء"}'),
  ('MOHAMMEDIA', 'CASABLANCA_SETTAT', '{"fr":"Mohammédia","ar":"المحمدية"}'),
  ('EL_JADIDA', 'CASABLANCA_SETTAT', '{"fr":"El Jadida","ar":"الجديدة"}'),
  ('SETTAT', 'CASABLANCA_SETTAT', '{"fr":"Settat","ar":"سطات"}'),
  ('BERRECHID', 'CASABLANCA_SETTAT', '{"fr":"Berrechid","ar":"برشيد"}'),
  ('BENSLIMANE', 'CASABLANCA_SETTAT', '{"fr":"Benslimane","ar":"بنسليمان"}'),
  ('MARRAKECH', 'MARRAKECH_SAFI', '{"fr":"Marrakech","ar":"مراكش"}'),
  ('SAFI', 'MARRAKECH_SAFI', '{"fr":"Safi","ar":"آسفي"}'),
  ('ESSAOUIRA', 'MARRAKECH_SAFI', '{"fr":"Essaouira","ar":"الصويرة"}'),
  ('EL_KELAA_DES_SRAGHNA', 'MARRAKECH_SAFI', '{"fr":"El Kelaâ des Sraghna","ar":"قلعة السراغنة"}'),
  ('ERRACHIDIA', 'DRAA_TAFILALET', '{"fr":"Errachidia","ar":"الرشيدية"}'),
  ('OUARZAZATE', 'DRAA_TAFILALET', '{"fr":"Ouarzazate","ar":"ورزازات"}'),
  ('MIDELT', 'DRAA_TAFILALET', '{"fr":"Midelt","ar":"ميدلت"}'),
  ('TINGHIR', 'DRAA_TAFILALET', '{"fr":"Tinghir","ar":"تنغير"}'),
  ('AGADIR', 'SOUSS_MASSA', '{"fr":"Agadir","ar":"أكادير"}'),
  ('INEZGANE', 'SOUSS_MASSA', '{"fr":"Inezgane","ar":"إنزكان"}'),
  ('TAROUDANT', 'SOUSS_MASSA', '{"fr":"Taroudant","ar":"تارودانت"}'),
  ('TIZNIT', 'SOUSS_MASSA', '{"fr":"Tiznit","ar":"تيزنيت"}'),
  ('GUELMIM', 'GUELMIM_OUED_NOUN', '{"fr":"Guelmim","ar":"كلميم"}'),
  ('TAN_TAN', 'GUELMIM_OUED_NOUN', '{"fr":"Tan-Tan","ar":"طانطان"}'),
  ('LAAYOUNE', 'LAAYOUNE_SAKIA_EL_HAMRA', '{"fr":"Laâyoune","ar":"العيون"}'),
  ('DAKHLA', 'DAKHLA_OUED_ED_DAHAB', '{"fr":"Dakhla","ar":"الداخلة"}');

// ignore: unused_import
import 'package:intl/intl.dart' as intl;

import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for French (`fr`).
class TFr extends T {
  TFr([String locale = 'fr']) : super(locale);

  @override
  String get titreApp => 'Daba';

  @override
  String get langue => 'Langue';

  @override
  String get langueFr => 'Français';

  @override
  String get langueAr => 'العربية';

  @override
  String get langueAryArab => 'الدارجة';

  @override
  String get langueAryLatn => 'Darija (latin)';

  @override
  String get connexionExplication =>
      'Entrez votre numéro de téléphone : vous recevrez un code par SMS.';

  @override
  String get reconnexionExplication =>
      'Votre session a expiré. Reconnectez-vous : vos ventes enregistrées restent sur le téléphone.';

  @override
  String get telephone => 'Téléphone';

  @override
  String get telephoneInvalide => 'Numéro de téléphone invalide';

  @override
  String get codeSms => 'Code reçu par SMS';

  @override
  String get changerNumero => 'Changer de numéro';

  @override
  String get recevoirCode => 'Recevoir le code';

  @override
  String get seConnecter => 'Se connecter';

  @override
  String get consentementTitre => 'Vos données personnelles';

  @override
  String get consentementTexte =>
      'Daba enregistre vos ventes, votre stock et le crédit de vos clients pour vous rendre service (loi 09-08, déclaration CNDP). Votre nom est montré aux grossistes et aux hanouts avec qui vous travaillez. Aucune donnée nominative n\'est transmise aux marques. Votre position n\'est collectée que si vous l\'acceptez, et seulement quand vous êtes « disponible » pour le dépannage. Deux sous-traitants hors du Maroc interviennent : Google Firebase Cloud Messaging pour les notifications (un identifiant de votre téléphone et le contenu des notifications) et Infobip pour les SMS (votre numéro et le texte des messages, codes de connexion compris). Vous pouvez retirer votre accord et demander vos données à tout moment.';

  @override
  String get consentementDonnees => 'J\'accepte le traitement de mes données';

  @override
  String get consentementGeo =>
      'J\'accepte la géolocalisation quand je suis disponible';

  @override
  String get consentementGeoDemande =>
      'Pour recevoir les demandes de dépannage, Daba doit connaître votre position, seulement quand vous êtes disponible. Acceptez-vous ?';

  @override
  String get accepter => 'Accepter';

  @override
  String get refuser => 'Refuser';

  @override
  String get annuler => 'Annuler';

  @override
  String get confirmer => 'Confirmer';

  @override
  String get valider => 'Valider';

  @override
  String get enregistrer => 'Enregistrer';

  @override
  String get voir => 'Voir';

  @override
  String get pinCreer => 'Choisissez un code PIN à 4 chiffres';

  @override
  String get pinConfirmer => 'Confirmez le code PIN';

  @override
  String get pinSaisir => 'Entrez votre code PIN';

  @override
  String get pinFaux => 'Code PIN incorrect';

  @override
  String get pinDifferent => 'Les deux codes sont différents';

  @override
  String get pinOublie => 'Code oublié ? Se reconnecter par SMS';

  @override
  String get ongletHanouts => 'Hanouts';

  @override
  String get ongletStock => 'Stock';

  @override
  String get ongletKarni => 'Karni';

  @override
  String get ongletPromos => 'Promos';

  @override
  String get ongletDepannage => 'Dépannage';

  @override
  String get synchronisation => 'Synchronisation';

  @override
  String get parametres => 'Paramètres';

  @override
  String get deconnexion => 'Se déconnecter';

  @override
  String get deconnexionConfirmer => 'Se déconnecter de ce téléphone ?';

  @override
  String deconnexionAvecAttente(String n) {
    return '$n opérations ne sont pas encore envoyées. Elles restent sur le téléphone et partiront à votre prochaine connexion. Se déconnecter ?';
  }

  @override
  String get reconnexionRequise =>
      'Session expirée : reconnectez-vous pour envoyer vos ventes. Vous pouvez continuer à vendre.';

  @override
  String alerteDemande(String produit, String quantite) {
    return 'Rupture près de vous : $produit × $quantite';
  }

  @override
  String get nouveauHanout => 'Nouveau hanout';

  @override
  String get rechercherHanout => 'Nom, gérant ou téléphone';

  @override
  String get liste => 'Liste';

  @override
  String get carte => 'Carte';

  @override
  String get aucunHanout => 'Aucun hanout. Synchronisez ou créez-en un.';

  @override
  String get vendre => 'Vendre';

  @override
  String get solde => 'Solde';

  @override
  String soldeActuel(String montant) {
    return 'Solde actuel : $montant';
  }

  @override
  String plafondValeur(String montant) {
    return 'Plafond $montant';
  }

  @override
  String delaiValeur(String jours) {
    return 'Délai $jours j';
  }

  @override
  String get derniereCommande => 'Dernière commande';

  @override
  String get versement => 'Versement';

  @override
  String get plafondCredit => 'Plafond de crédit';

  @override
  String get historique => 'Historique';

  @override
  String get aucunMouvement => 'Rien pour l\'instant';

  @override
  String get nomHanout => 'Nom du hanout';

  @override
  String get gerant => 'Gérant';

  @override
  String get adresse => 'Adresse';

  @override
  String get ville => 'Ville';

  @override
  String get ice => 'ICE';

  @override
  String get iceInvalide => 'L\'ICE compte 15 chiffres';

  @override
  String champObligatoire(String champ) {
    return '$champ est obligatoire';
  }

  @override
  String get positionActuelle =>
      'Enregistrer ma position actuelle comme position du hanout';

  @override
  String venteA(String hanout) {
    return 'Vente · $hanout';
  }

  @override
  String get panierVide => 'Ajoutez des produits';

  @override
  String get total => 'Total';

  @override
  String get bonLivraison => 'Bon de livraison';

  @override
  String get facture => 'Facture';

  @override
  String get avoir => 'Avoir';

  @override
  String get factureIndisponible =>
      'La facture demande un ICE et vos mentions légales dans votre profil.';

  @override
  String get especes => 'Espèces';

  @override
  String get cheque => 'Chèque';

  @override
  String get chequeNumero => 'N° du chèque';

  @override
  String get chequeBanque => 'Banque';

  @override
  String get resteACredit => 'Reste à crédit';

  @override
  String get toutEspeces => 'Tout en espèces';

  @override
  String get toutCredit => 'Tout à crédit';

  @override
  String get confirmationHanout => 'Le hanout a confirmé la livraison';

  @override
  String get emettreBon => 'Émettre le bon de livraison';

  @override
  String get emettreFacture => 'Émettre la facture';

  @override
  String get reglementSuperieur => 'Le règlement dépasse le total';

  @override
  String get prixUnitaire => 'Prix unitaire';

  @override
  String get remise => 'Remise';

  @override
  String get retenirPrixHanout => 'Retenir ce prix pour ce hanout';

  @override
  String get remisePromo => 'Remise promo';

  @override
  String get tarifNegocie => 'tarif négocié';

  @override
  String stockInsuffisant(String n) {
    return 'stock $n';
  }

  @override
  String get rechercherProduit => 'Produit, marque ou code-barres';

  @override
  String get aucunProduit => 'Aucun produit';

  @override
  String get regle => 'Réglé';

  @override
  String get partagerPdf => 'Partager le PDF (WhatsApp…)';

  @override
  String get smsApresSync =>
      'Le hanout reçoit aussi un lien par SMS dès que la vente est synchronisée.';

  @override
  String get docAnnule => 'Annulé par avoir';

  @override
  String get docPartiellementAnnule => 'Partiellement annulé par avoir';

  @override
  String get promo => 'promo';

  @override
  String get vendeur => 'Vendeur';

  @override
  String get client => 'Client';

  @override
  String get produit => 'Produit';

  @override
  String get produitEnPhoto => 'Produit en photo';

  @override
  String get quantite => 'Quantité';

  @override
  String get montant => 'Montant';

  @override
  String get statut => 'Statut';

  @override
  String get scannerBonSortie => 'Scanner un bon de sortie';

  @override
  String get chargementManuel => 'Chargement manuel';

  @override
  String get mouvements => 'Mouvements';

  @override
  String bonACharger(String numero) {
    return 'Bon $numero à charger';
  }

  @override
  String get charger => 'Charger';

  @override
  String get chargerCeBon => 'Charger ce bon dans votre stock ?';

  @override
  String get stockCharge => 'Stock chargé';

  @override
  String get stockVide => 'Stock vide : scannez un bon de sortie.';

  @override
  String get ajouterProduits => 'Ajoutez les produits chargés';

  @override
  String get mvtChargement => 'Chargement';

  @override
  String get mvtVente => 'Vente';

  @override
  String get mvtRetour => 'Retour';

  @override
  String get mvtAjustement => 'Ajustement';

  @override
  String get mvtPromo => 'Promo';

  @override
  String get totalCreances => 'Total des créances';

  @override
  String get aucuneCreance => 'Aucun crédit en cours';

  @override
  String get plafond => 'Plafond';

  @override
  String get delaiJours => 'Délai maximal (jours)';

  @override
  String get avertissementCredit =>
      'Ce hanout dépasse son plafond ou son délai de crédit. Vendre quand même à crédit ?';

  @override
  String get politiqueCredit => 'Dépassement de crédit';

  @override
  String get politiqueCreditExplication =>
      'Quand un hanout dépasse son plafond ou son délai';

  @override
  String get politiqueAvertir => 'M\'avertir, je décide';

  @override
  String get politiqueBloquer => 'Bloquer la vente à crédit';

  @override
  String get mesReservations => 'Mes réservations';

  @override
  String get aucunePromo => 'Aucune promo pour l\'instant';

  @override
  String get aucuneReservation => 'Aucune réservation';

  @override
  String jusquAu(String date) {
    return 'jusqu\'au $date';
  }

  @override
  String remisePourcent(String pourcentage) {
    return '-$pourcentage %';
  }

  @override
  String remisePrixNet(String prix) {
    return 'Prix net $prix';
  }

  @override
  String remiseOfferte(String achetee, String offerte) {
    return '$achetee achetés + $offerte offerts';
  }

  @override
  String remisePalier(String paliers) {
    return 'Paliers : $paliers';
  }

  @override
  String prixConseille(String prix) {
    return 'Prix de revente conseillé : $prix (indicatif)';
  }

  @override
  String quantiteRestante(String n) {
    return '$n disponibles';
  }

  @override
  String plafondAmbulant(String max, String deja) {
    return 'Maximum $max par vendeur, déjà réservé : $deja';
  }

  @override
  String delaiRetrait(String heures) {
    return 'À retirer sous $heures h';
  }

  @override
  String get plafondAtteint =>
      'Vous avez atteint le maximum autorisé, ou l\'offre est épuisée.';

  @override
  String get reserver => 'Réserver';

  @override
  String get reservationEnLigne =>
      'La réservation exige une connexion internet.';

  @override
  String get reservation => 'Réservation';

  @override
  String get statutReservee => 'Réservée';

  @override
  String get statutRetiree => 'Retirée';

  @override
  String get statutExpiree => 'Expirée';

  @override
  String get statutRevendue => 'Revendue';

  @override
  String aRetirerAvant(String date) {
    return 'À retirer avant le $date';
  }

  @override
  String get disponible => 'Disponible pour le dépannage';

  @override
  String get indisponible => 'Indisponible';

  @override
  String get disponibleExplication =>
      'Activez pendant la tournée pour recevoir les ruptures des hanouts proches. Votre position n\'est envoyée que pendant que c\'est activé.';

  @override
  String get aucuneDemande => 'Aucune demande pour l\'instant';

  @override
  String get activerPourRecevoir =>
      'Activez la disponibilité pour recevoir les demandes';

  @override
  String get demandePriseParAutre =>
      'Un autre vendeur a déjà pris cette demande';

  @override
  String derniereVente(String date) {
    return 'dernière vente $date';
  }

  @override
  String repondreAvant(String date) {
    return 'Répondre avant $date';
  }

  @override
  String get enRoute => 'Je suis en route';

  @override
  String get livrer => 'Livrer et encaisser';

  @override
  String get statutEnAttente => 'En attente';

  @override
  String get statutAcceptee => 'Acceptée';

  @override
  String get statutEnRoute => 'En route';

  @override
  String get statutLivree => 'Livrée';

  @override
  String get statutSansReponse => 'Sans réponse';

  @override
  String get horsLigne =>
      'Hors ligne : vos gestes sont gardés sur le téléphone';

  @override
  String get enLigne => 'En ligne';

  @override
  String get jamaisSynchronise => 'Jamais synchronisé';

  @override
  String derniereSync(String date) {
    return 'Dernière synchronisation : $date';
  }

  @override
  String operationsEnAttente(String n) {
    return '$n opérations en attente d\'envoi';
  }

  @override
  String get synchroniserMaintenant => 'Synchroniser maintenant';

  @override
  String get operationsRefusees => 'Opérations refusées par le serveur';

  @override
  String get anomalies => 'Anomalies signalées';

  @override
  String get appareil => 'Appareil';

  @override
  String get versionCndp => 'Version du texte CNDP accepté';

  @override
  String get erreurHorsLigne =>
      'Pas de connexion internet. Réessayez quand le réseau revient.';

  @override
  String get erreurPermissionPosition =>
      'Autorisez l\'accès à la position dans les réglages du téléphone.';

  @override
  String get erreurFactureSansMentions =>
      'Facture impossible : ICE et mentions légales manquants dans votre profil.';

  @override
  String get erreurCreditBloque =>
      'Vente à crédit bloquée : plafond ou délai dépassé. Encaissez en espèces ou par chèque.';

  @override
  String get erreurRemisePlafond => 'Remise au-delà du plafond autorisé';

  @override
  String get erreurQrAutreAmbulant =>
      'Ce bon de sortie est adressé à un autre vendeur';

  @override
  String get erreurQrIllisible => 'QR code illisible';

  @override
  String get erreurBonDejaCharge => 'Ce bon est déjà chargé';

  @override
  String get erreurConflit =>
      'Action impossible : la situation a changé (quantité épuisée, demande déjà prise…)';

  @override
  String get erreurPlafondPromo =>
      'Maximum par vendeur dépassé pour cette promo';

  @override
  String get erreurTropDEssais => 'Trop d\'essais. Patientez quelques minutes.';

  @override
  String get erreurCodeInvalide => 'Code incorrect';

  @override
  String get erreurCodeExpire => 'Code expiré, demandez-en un nouveau';

  @override
  String get erreurConsentement =>
      'Votre accord sur les données personnelles est nécessaire';

  @override
  String erreurServeur(String code) {
    return 'Erreur du serveur ($code)';
  }

  @override
  String get erreurInconnue => 'Erreur inattendue';

  @override
  String get opCHARGEMENT => 'Chargement';

  @override
  String get opRETOUR => 'Retour';

  @override
  String get opINVENTAIRE => 'Inventaire';

  @override
  String get opEMISSION_DOCUMENT => 'Vente';

  @override
  String get opAVOIR => 'Avoir';

  @override
  String get opVERSEMENT => 'Versement';

  @override
  String get opTARIF => 'Tarif';

  @override
  String get opHANOUT => 'Nouveau hanout';

  @override
  String get opCREDIT_HANOUT => 'Plafond de crédit';

  @override
  String get opPARAMETRES_AMBULANT => 'Paramètres';

  @override
  String get anoSTOCK_NEGATIF => 'Stock négatif';

  @override
  String get anoECART_SANS_MOTIF => 'Écart d\'inventaire sans motif';

  @override
  String get anoNUMEROTATION_TROU => 'Trou dans la numérotation';

  @override
  String get anoNUMERO_EN_DOUBLE => 'Numéro en double';

  @override
  String get anoPLAFOND_CREDIT_DEPASSE => 'Plafond de crédit dépassé';

  @override
  String get anoDELAI_CREDIT_DEPASSE => 'Délai de crédit dépassé';

  @override
  String get anoCHARGEMENT_EN_DOUBLE => 'Bon de sortie chargé deux fois';

  @override
  String get anoPROMO_SANS_RETRAIT =>
      'Vente promo au-delà de la quantité retirée';

  @override
  String get anoDEMANDE_NON_ATTRIBUEE =>
      'Livraison d\'une demande prise par un autre';

  @override
  String get anoREMISE_PLAFOND_DEPASSE => 'Remise au-delà du plafond';

  @override
  String get erreurAutreAmbulant =>
      'Un autre vendeur a des ventes pas encore envoyées sur ce téléphone. Il doit se reconnecter et synchroniser d\'abord.';

  @override
  String get docRejete => 'Refusé par le serveur : voir Synchronisation';

  @override
  String get tva => 'TVA';

  @override
  String get tvaAssujetti => 'Assujetti à la TVA';

  @override
  String get tvaNonAssujetti => 'Non assujetti à la TVA';

  @override
  String get erreurFactureTvaInconnue =>
      'Facture impossible : un produit du panier n\'a pas de taux de TVA connu. Émettez un bon de livraison.';

  @override
  String get tvaComprise => 'Dont TVA';

  @override
  String get envoyerLienSms => 'Envoyer le lien par SMS';

  @override
  String get erreurDocNonSynchronise =>
      'Synchronisez d\'abord la vente pour obtenir son lien.';

  @override
  String get erreurHanoutSansTelephone =>
      'Ce hanout n\'a pas de numéro de téléphone.';

  @override
  String get erreurAppareilRevoque =>
      'Ce téléphone a été déconnecté à distance. Reconnectez-vous.';

  @override
  String receptionConforme(String date) {
    return 'Réception confirmée par le hanout le $date';
  }

  @override
  String get receptionEcart => 'Le hanout a signalé un écart à la réception';

  @override
  String receptionEcartLigne(String produit, String recu, String livre) {
    return '$produit : $recu reçus sur $livre';
  }

  @override
  String get contestations => 'Contestations';

  @override
  String contestationsOuvertes(String n) {
    return '$n contestations ouvertes';
  }

  @override
  String get aucuneContestation => 'Aucune contestation ouverte';

  @override
  String get clore => 'Clore';

  @override
  String get commentaire => 'Commentaire';

  @override
  String get document => 'Document';

  @override
  String get compteCredit => 'Solde du compte';

  @override
  String get motifMONTANT => 'Montant';

  @override
  String get motifQUANTITE => 'Quantité';

  @override
  String get motifNON_RECU => 'Non reçu';

  @override
  String get motifDEJA_PAYE => 'Déjà payé';

  @override
  String get motifAUTRE => 'Autre';

  @override
  String get opRESOLUTION_CONTESTATION => 'Contestation close';

  @override
  String get anoTVA_INCONNUE => 'Taux de TVA inconnu';

  @override
  String get erreurRoleNonAmbulant =>
      'Ce numéro n\'est pas un compte de vendeur ambulant.';

  @override
  String get rupture => 'Rupture';

  @override
  String get plafondDepasse => 'Plafond dépassé';

  @override
  String get delaiDepasse => 'Délai dépassé';

  @override
  String get profil => 'Mon profil';

  @override
  String get profilExplication =>
      'Votre nom est montré aux grossistes et aux hanouts avec qui vous travaillez, jamais aux marques. Vos catégories choisissent les promos que vous recevez.';

  @override
  String get nomComplet => 'Nom et prénom';

  @override
  String get cin => 'CIN (facultatif)';

  @override
  String get cinInvalide =>
      'CIN : 1 ou 2 lettres puis jusqu\'à 6 chiffres (ex. BK123456)';

  @override
  String get vehicule => 'Véhicule (facultatif)';

  @override
  String get zonesCouvertes => 'Zones couvertes';

  @override
  String get categoriesVendues => 'Catégories vendues';

  @override
  String get listeApresConnexion => 'Liste disponible après une connexion';

  @override
  String get plusTard => 'Plus tard';

  @override
  String get profilEnregistre => 'Profil enregistré';

  @override
  String get opPROFIL_AMBULANT => 'Profil';

  @override
  String get mentionsLegales => 'Mentions légales (facture)';

  @override
  String get mentionsExplication =>
      'Avec un ICE, vous pouvez émettre des factures en plus des bons de livraison.';

  @override
  String get mentionsAucune => 'Aucune : bons de livraison seulement';

  @override
  String get identifiantFiscal => 'Identifiant fiscal (IF)';

  @override
  String get registreCommerce => 'Registre du commerce (RC)';

  @override
  String get assujettiTva => 'Assujetti à la TVA';

  @override
  String get offreNonCiblee => 'Cette offre ne vous est plus proposée.';

  @override
  String get consentementNouveauTexte =>
      'Le texte sur vos données a changé : votre nom est montré aux grossistes et aux hanouts avec qui vous travaillez, et les notifications et les SMS passent par Google Firebase Cloud Messaging et Infobip, hors du Maroc.';

  @override
  String get lireEtAccepter => 'Lire et accepter';

  @override
  String get profilConsentementRequis =>
      'Acceptez d\'abord le nouveau texte sur vos données pour enregistrer votre profil.';

  @override
  String get canalHaute => 'Urgent : dépannage, promos, réservations';

  @override
  String get canalMoyenne => 'Suivi : crédit, demandes, contestations';

  @override
  String get canalBasse => 'Stock embarqué';

  @override
  String get retours => 'Retours';

  @override
  String get retourHanout => 'Retour d\'un hanout';

  @override
  String get retourGrossiste => 'Retour au grossiste';

  @override
  String get choisirHanout => 'Choisir le hanout';

  @override
  String get grossiste => 'Grossiste';

  @override
  String grossisteCode(String code) {
    return 'Grossiste $code';
  }

  @override
  String get aucunGrossiste =>
      'Aucun grossiste connu : chargez d\'abord un bon de sortie.';

  @override
  String get motif => 'Motif';

  @override
  String get retourABIME => 'Abîmé';

  @override
  String get retourPERIME => 'Périmé';

  @override
  String get retourINVENDU => 'Invendu';

  @override
  String get retourAUTRE => 'Autre';

  @override
  String get remisEnVente => 'Remettre dans le stock vendable';

  @override
  String get retourAvoir =>
      'Le remboursement ou la baisse du solde se fait ensuite par un avoir.';

  @override
  String get retourEnregistre => 'Retour enregistré';

  @override
  String get inventaireFinJournee => 'Inventaire de fin de journée';

  @override
  String get inventaireAide =>
      'Comptez le stock normal : le stock promo est suivi à part.';

  @override
  String attendu(String quantite) {
    return 'Attendu : $quantite';
  }

  @override
  String ecart(String ecart) {
    return 'Écart : $ecart';
  }

  @override
  String get compte => 'Compté';

  @override
  String get motifEcart => 'Motif de l\'écart';

  @override
  String get motifObligatoire => 'Motif obligatoire';

  @override
  String get erreurMotifEcart => 'Écart trop grand : indiquez un motif.';

  @override
  String get inventaireEnregistre => 'Inventaire enregistré';

  @override
  String get faireSigner => 'Faire signer le hanout';

  @override
  String get signatureHanout => 'Signature du hanout';

  @override
  String get signeParHanout => 'Signé par le hanout';

  @override
  String get effacer => 'Effacer';

  @override
  String get choisirPieces => 'Choisir les pièces à régler';

  @override
  String get imputationFifo =>
      'Sinon, le versement règle les pièces les plus anciennes.';

  @override
  String resteDu(String montant) {
    return 'Reste $montant';
  }

  @override
  String get anciennete0a15 => '0 à 15 j';

  @override
  String get anciennete15a30 => '15 à 30 j';

  @override
  String get anciennetePlus30 => 'Plus de 30 j';

  @override
  String get topDebiteurs => 'Plus gros débiteurs en premier';

  @override
  String get favori => 'Favori du hanout';

  @override
  String get tableauDeBord => 'Tableau de bord';

  @override
  String get aujourdhui => 'Aujourd\'hui';

  @override
  String get cetteSemaine => 'Cette semaine';

  @override
  String get ceMois => 'Ce mois';

  @override
  String get chiffreAffaires => 'Chiffre d\'affaires';

  @override
  String get encaisse => 'Encaissé';

  @override
  String get creditAccorde => 'Crédit accordé';

  @override
  String get caisseDuJour => 'Caisse du jour';

  @override
  String get especesAttendues => 'Espèces attendues';

  @override
  String get especesDeclarees => 'Espèces comptées';

  @override
  String ecartCaisse(String montant) {
    return 'Écart de caisse : $montant';
  }

  @override
  String get caisseJuste => 'Caisse juste';

  @override
  String get caisseLocale => 'Gardé sur ce téléphone seulement.';
}

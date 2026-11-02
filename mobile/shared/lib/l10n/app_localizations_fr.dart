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
      'Daba enregistre vos ventes, votre stock et le crédit de vos clients pour vous rendre service (loi 09-08, déclaration CNDP). Aucune donnée nominative n\'est transmise aux marques. Votre position n\'est collectée que si vous l\'acceptez, et seulement quand vous êtes « disponible » pour le dépannage. Vous pouvez retirer votre accord et demander vos données à tout moment.';

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
}

import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:intl/intl.dart' as intl;

import 'app_localizations_ar.dart';
import 'app_localizations_ary.dart';
import 'app_localizations_fr.dart';

// ignore_for_file: type=lint

/// Callers can lookup localized strings with an instance of T
/// returned by `T.of(context)`.
///
/// Applications need to include `T.delegate()` in their app's
/// `localizationDelegates` list, and the locales they support in the app's
/// `supportedLocales` list. For example:
///
/// ```dart
/// import 'l10n/app_localizations.dart';
///
/// return MaterialApp(
///   localizationsDelegates: T.localizationsDelegates,
///   supportedLocales: T.supportedLocales,
///   home: MyApplicationHome(),
/// );
/// ```
///
/// ## Update pubspec.yaml
///
/// Please make sure to update your pubspec.yaml to include the following
/// packages:
///
/// ```yaml
/// dependencies:
///   # Internationalization support.
///   flutter_localizations:
///     sdk: flutter
///   intl: any # Use the pinned version from flutter_localizations
///
///   # Rest of dependencies
/// ```
///
/// ## iOS Applications
///
/// iOS applications define key application metadata, including supported
/// locales, in an Info.plist file that is built into the application bundle.
/// To configure the locales supported by your app, you’ll need to edit this
/// file.
///
/// First, open your project’s ios/Runner.xcworkspace Xcode workspace file.
/// Then, in the Project Navigator, open the Info.plist file under the Runner
/// project’s Runner folder.
///
/// Next, select the Information Property List item, select Add Item from the
/// Editor menu, then select Localizations from the pop-up menu.
///
/// Select and expand the newly-created Localizations item then, for each
/// locale your application supports, add a new item and select the locale
/// you wish to add from the pop-up menu in the Value field. This list should
/// be consistent with the languages listed in the T.supportedLocales
/// property.
abstract class T {
  T(String locale)
    : localeName = intl.Intl.canonicalizedLocale(locale.toString());

  final String localeName;

  static T of(BuildContext context) {
    return Localizations.of<T>(context, T)!;
  }

  static const LocalizationsDelegate<T> delegate = _TDelegate();

  /// A list of this localizations delegate along with the default localizations
  /// delegates.
  ///
  /// Returns a list of localizations delegates containing this delegate along with
  /// GlobalMaterialLocalizations.delegate, GlobalCupertinoLocalizations.delegate,
  /// and GlobalWidgetsLocalizations.delegate.
  ///
  /// Additional delegates can be added by appending to this list in
  /// MaterialApp. This list does not have to be used at all if a custom list
  /// of delegates is preferred or required.
  static const List<LocalizationsDelegate<dynamic>> localizationsDelegates =
      <LocalizationsDelegate<dynamic>>[
        delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
      ];

  /// A list of this localizations delegate's supported locales.
  static const List<Locale> supportedLocales = <Locale>[
    Locale('ar'),
    Locale('ary'),
    Locale.fromSubtags(languageCode: 'ary', scriptCode: 'Latn'),
    Locale('fr'),
  ];

  /// No description provided for @titreApp.
  ///
  /// In fr, this message translates to:
  /// **'Daba'**
  String get titreApp;

  /// No description provided for @langue.
  ///
  /// In fr, this message translates to:
  /// **'Langue'**
  String get langue;

  /// No description provided for @langueFr.
  ///
  /// In fr, this message translates to:
  /// **'Français'**
  String get langueFr;

  /// No description provided for @langueAr.
  ///
  /// In fr, this message translates to:
  /// **'العربية'**
  String get langueAr;

  /// No description provided for @langueAryArab.
  ///
  /// In fr, this message translates to:
  /// **'الدارجة'**
  String get langueAryArab;

  /// No description provided for @langueAryLatn.
  ///
  /// In fr, this message translates to:
  /// **'Darija (latin)'**
  String get langueAryLatn;

  /// No description provided for @connexionExplication.
  ///
  /// In fr, this message translates to:
  /// **'Entrez votre numéro de téléphone : vous recevrez un code par SMS.'**
  String get connexionExplication;

  /// No description provided for @reconnexionExplication.
  ///
  /// In fr, this message translates to:
  /// **'Votre session a expiré. Reconnectez-vous : vos ventes enregistrées restent sur le téléphone.'**
  String get reconnexionExplication;

  /// No description provided for @telephone.
  ///
  /// In fr, this message translates to:
  /// **'Téléphone'**
  String get telephone;

  /// No description provided for @telephoneInvalide.
  ///
  /// In fr, this message translates to:
  /// **'Numéro de téléphone invalide'**
  String get telephoneInvalide;

  /// No description provided for @codeSms.
  ///
  /// In fr, this message translates to:
  /// **'Code reçu par SMS'**
  String get codeSms;

  /// No description provided for @changerNumero.
  ///
  /// In fr, this message translates to:
  /// **'Changer de numéro'**
  String get changerNumero;

  /// No description provided for @recevoirCode.
  ///
  /// In fr, this message translates to:
  /// **'Recevoir le code'**
  String get recevoirCode;

  /// No description provided for @seConnecter.
  ///
  /// In fr, this message translates to:
  /// **'Se connecter'**
  String get seConnecter;

  /// No description provided for @consentementTitre.
  ///
  /// In fr, this message translates to:
  /// **'Vos données personnelles'**
  String get consentementTitre;

  /// No description provided for @consentementTexte.
  ///
  /// In fr, this message translates to:
  /// **'Daba enregistre vos ventes, votre stock et le crédit de vos clients pour vous rendre service (loi 09-08, déclaration CNDP). Votre nom est montré aux grossistes et aux hanouts avec qui vous travaillez. Aucune donnée nominative n\'est transmise aux marques. Votre position n\'est collectée que si vous l\'acceptez, et seulement quand vous êtes « disponible » pour le dépannage. Deux sous-traitants hors du Maroc interviennent : Google Firebase Cloud Messaging pour les notifications (un identifiant de votre téléphone et le contenu des notifications) et Infobip pour les SMS (votre numéro et le texte des messages, codes de connexion compris). Vous pouvez retirer votre accord et demander vos données à tout moment.'**
  String get consentementTexte;

  /// No description provided for @consentementDonnees.
  ///
  /// In fr, this message translates to:
  /// **'J\'accepte le traitement de mes données'**
  String get consentementDonnees;

  /// No description provided for @consentementGeo.
  ///
  /// In fr, this message translates to:
  /// **'J\'accepte la géolocalisation quand je suis disponible'**
  String get consentementGeo;

  /// No description provided for @consentementGeoDemande.
  ///
  /// In fr, this message translates to:
  /// **'Pour recevoir les demandes de dépannage, Daba doit connaître votre position, seulement quand vous êtes disponible. Acceptez-vous ?'**
  String get consentementGeoDemande;

  /// No description provided for @accepter.
  ///
  /// In fr, this message translates to:
  /// **'Accepter'**
  String get accepter;

  /// No description provided for @refuser.
  ///
  /// In fr, this message translates to:
  /// **'Refuser'**
  String get refuser;

  /// No description provided for @annuler.
  ///
  /// In fr, this message translates to:
  /// **'Annuler'**
  String get annuler;

  /// No description provided for @confirmer.
  ///
  /// In fr, this message translates to:
  /// **'Confirmer'**
  String get confirmer;

  /// No description provided for @valider.
  ///
  /// In fr, this message translates to:
  /// **'Valider'**
  String get valider;

  /// No description provided for @enregistrer.
  ///
  /// In fr, this message translates to:
  /// **'Enregistrer'**
  String get enregistrer;

  /// No description provided for @voir.
  ///
  /// In fr, this message translates to:
  /// **'Voir'**
  String get voir;

  /// No description provided for @pinCreer.
  ///
  /// In fr, this message translates to:
  /// **'Choisissez un code PIN à 4 chiffres'**
  String get pinCreer;

  /// No description provided for @pinConfirmer.
  ///
  /// In fr, this message translates to:
  /// **'Confirmez le code PIN'**
  String get pinConfirmer;

  /// No description provided for @pinSaisir.
  ///
  /// In fr, this message translates to:
  /// **'Entrez votre code PIN'**
  String get pinSaisir;

  /// No description provided for @pinFaux.
  ///
  /// In fr, this message translates to:
  /// **'Code PIN incorrect'**
  String get pinFaux;

  /// No description provided for @pinDifferent.
  ///
  /// In fr, this message translates to:
  /// **'Les deux codes sont différents'**
  String get pinDifferent;

  /// No description provided for @pinOublie.
  ///
  /// In fr, this message translates to:
  /// **'Code oublié ? Se reconnecter par SMS'**
  String get pinOublie;

  /// No description provided for @ongletHanouts.
  ///
  /// In fr, this message translates to:
  /// **'Hanouts'**
  String get ongletHanouts;

  /// No description provided for @ongletStock.
  ///
  /// In fr, this message translates to:
  /// **'Stock'**
  String get ongletStock;

  /// No description provided for @ongletKarni.
  ///
  /// In fr, this message translates to:
  /// **'Karni'**
  String get ongletKarni;

  /// No description provided for @ongletPromos.
  ///
  /// In fr, this message translates to:
  /// **'Promos'**
  String get ongletPromos;

  /// No description provided for @ongletDepannage.
  ///
  /// In fr, this message translates to:
  /// **'Dépannage'**
  String get ongletDepannage;

  /// No description provided for @synchronisation.
  ///
  /// In fr, this message translates to:
  /// **'Synchronisation'**
  String get synchronisation;

  /// No description provided for @parametres.
  ///
  /// In fr, this message translates to:
  /// **'Paramètres'**
  String get parametres;

  /// No description provided for @deconnexion.
  ///
  /// In fr, this message translates to:
  /// **'Se déconnecter'**
  String get deconnexion;

  /// No description provided for @deconnexionConfirmer.
  ///
  /// In fr, this message translates to:
  /// **'Se déconnecter de ce téléphone ?'**
  String get deconnexionConfirmer;

  /// No description provided for @deconnexionAvecAttente.
  ///
  /// In fr, this message translates to:
  /// **'{n} opérations ne sont pas encore envoyées. Elles restent sur le téléphone et partiront à votre prochaine connexion. Se déconnecter ?'**
  String deconnexionAvecAttente(String n);

  /// No description provided for @reconnexionRequise.
  ///
  /// In fr, this message translates to:
  /// **'Session expirée : reconnectez-vous pour envoyer vos ventes. Vous pouvez continuer à vendre.'**
  String get reconnexionRequise;

  /// No description provided for @alerteDemande.
  ///
  /// In fr, this message translates to:
  /// **'Rupture près de vous : {produit} × {quantite}'**
  String alerteDemande(String produit, String quantite);

  /// No description provided for @nouveauHanout.
  ///
  /// In fr, this message translates to:
  /// **'Nouveau hanout'**
  String get nouveauHanout;

  /// No description provided for @rechercherHanout.
  ///
  /// In fr, this message translates to:
  /// **'Nom, gérant ou téléphone'**
  String get rechercherHanout;

  /// No description provided for @liste.
  ///
  /// In fr, this message translates to:
  /// **'Liste'**
  String get liste;

  /// No description provided for @carte.
  ///
  /// In fr, this message translates to:
  /// **'Carte'**
  String get carte;

  /// No description provided for @aucunHanout.
  ///
  /// In fr, this message translates to:
  /// **'Aucun hanout. Synchronisez ou créez-en un.'**
  String get aucunHanout;

  /// No description provided for @vendre.
  ///
  /// In fr, this message translates to:
  /// **'Vendre'**
  String get vendre;

  /// No description provided for @solde.
  ///
  /// In fr, this message translates to:
  /// **'Solde'**
  String get solde;

  /// No description provided for @soldeActuel.
  ///
  /// In fr, this message translates to:
  /// **'Solde actuel : {montant}'**
  String soldeActuel(String montant);

  /// No description provided for @plafondValeur.
  ///
  /// In fr, this message translates to:
  /// **'Plafond {montant}'**
  String plafondValeur(String montant);

  /// No description provided for @delaiValeur.
  ///
  /// In fr, this message translates to:
  /// **'Délai {jours} j'**
  String delaiValeur(String jours);

  /// No description provided for @derniereCommande.
  ///
  /// In fr, this message translates to:
  /// **'Dernière commande'**
  String get derniereCommande;

  /// No description provided for @versement.
  ///
  /// In fr, this message translates to:
  /// **'Versement'**
  String get versement;

  /// No description provided for @plafondCredit.
  ///
  /// In fr, this message translates to:
  /// **'Plafond de crédit'**
  String get plafondCredit;

  /// No description provided for @historique.
  ///
  /// In fr, this message translates to:
  /// **'Historique'**
  String get historique;

  /// No description provided for @aucunMouvement.
  ///
  /// In fr, this message translates to:
  /// **'Rien pour l\'instant'**
  String get aucunMouvement;

  /// No description provided for @nomHanout.
  ///
  /// In fr, this message translates to:
  /// **'Nom du hanout'**
  String get nomHanout;

  /// No description provided for @gerant.
  ///
  /// In fr, this message translates to:
  /// **'Gérant'**
  String get gerant;

  /// No description provided for @adresse.
  ///
  /// In fr, this message translates to:
  /// **'Adresse'**
  String get adresse;

  /// No description provided for @ville.
  ///
  /// In fr, this message translates to:
  /// **'Ville'**
  String get ville;

  /// No description provided for @ice.
  ///
  /// In fr, this message translates to:
  /// **'ICE'**
  String get ice;

  /// No description provided for @iceInvalide.
  ///
  /// In fr, this message translates to:
  /// **'L\'ICE compte 15 chiffres'**
  String get iceInvalide;

  /// No description provided for @champObligatoire.
  ///
  /// In fr, this message translates to:
  /// **'{champ} est obligatoire'**
  String champObligatoire(String champ);

  /// No description provided for @positionActuelle.
  ///
  /// In fr, this message translates to:
  /// **'Enregistrer ma position actuelle comme position du hanout'**
  String get positionActuelle;

  /// No description provided for @venteA.
  ///
  /// In fr, this message translates to:
  /// **'Vente · {hanout}'**
  String venteA(String hanout);

  /// No description provided for @panierVide.
  ///
  /// In fr, this message translates to:
  /// **'Ajoutez des produits'**
  String get panierVide;

  /// No description provided for @total.
  ///
  /// In fr, this message translates to:
  /// **'Total'**
  String get total;

  /// No description provided for @bonLivraison.
  ///
  /// In fr, this message translates to:
  /// **'Bon de livraison'**
  String get bonLivraison;

  /// No description provided for @facture.
  ///
  /// In fr, this message translates to:
  /// **'Facture'**
  String get facture;

  /// No description provided for @avoir.
  ///
  /// In fr, this message translates to:
  /// **'Avoir'**
  String get avoir;

  /// No description provided for @factureIndisponible.
  ///
  /// In fr, this message translates to:
  /// **'La facture demande un ICE et vos mentions légales dans votre profil.'**
  String get factureIndisponible;

  /// No description provided for @especes.
  ///
  /// In fr, this message translates to:
  /// **'Espèces'**
  String get especes;

  /// No description provided for @cheque.
  ///
  /// In fr, this message translates to:
  /// **'Chèque'**
  String get cheque;

  /// No description provided for @chequeNumero.
  ///
  /// In fr, this message translates to:
  /// **'N° du chèque'**
  String get chequeNumero;

  /// No description provided for @chequeBanque.
  ///
  /// In fr, this message translates to:
  /// **'Banque'**
  String get chequeBanque;

  /// No description provided for @resteACredit.
  ///
  /// In fr, this message translates to:
  /// **'Reste à crédit'**
  String get resteACredit;

  /// No description provided for @toutEspeces.
  ///
  /// In fr, this message translates to:
  /// **'Tout en espèces'**
  String get toutEspeces;

  /// No description provided for @toutCredit.
  ///
  /// In fr, this message translates to:
  /// **'Tout à crédit'**
  String get toutCredit;

  /// No description provided for @confirmationHanout.
  ///
  /// In fr, this message translates to:
  /// **'Le hanout a confirmé la livraison'**
  String get confirmationHanout;

  /// No description provided for @emettreBon.
  ///
  /// In fr, this message translates to:
  /// **'Émettre le bon de livraison'**
  String get emettreBon;

  /// No description provided for @emettreFacture.
  ///
  /// In fr, this message translates to:
  /// **'Émettre la facture'**
  String get emettreFacture;

  /// No description provided for @reglementSuperieur.
  ///
  /// In fr, this message translates to:
  /// **'Le règlement dépasse le total'**
  String get reglementSuperieur;

  /// No description provided for @prixUnitaire.
  ///
  /// In fr, this message translates to:
  /// **'Prix unitaire'**
  String get prixUnitaire;

  /// No description provided for @remise.
  ///
  /// In fr, this message translates to:
  /// **'Remise'**
  String get remise;

  /// No description provided for @retenirPrixHanout.
  ///
  /// In fr, this message translates to:
  /// **'Retenir ce prix pour ce hanout'**
  String get retenirPrixHanout;

  /// No description provided for @remisePromo.
  ///
  /// In fr, this message translates to:
  /// **'Remise promo'**
  String get remisePromo;

  /// No description provided for @tarifNegocie.
  ///
  /// In fr, this message translates to:
  /// **'tarif négocié'**
  String get tarifNegocie;

  /// No description provided for @stockInsuffisant.
  ///
  /// In fr, this message translates to:
  /// **'stock {n}'**
  String stockInsuffisant(String n);

  /// No description provided for @rechercherProduit.
  ///
  /// In fr, this message translates to:
  /// **'Produit, marque ou code-barres'**
  String get rechercherProduit;

  /// No description provided for @aucunProduit.
  ///
  /// In fr, this message translates to:
  /// **'Aucun produit'**
  String get aucunProduit;

  /// No description provided for @regle.
  ///
  /// In fr, this message translates to:
  /// **'Réglé'**
  String get regle;

  /// No description provided for @partagerPdf.
  ///
  /// In fr, this message translates to:
  /// **'Partager le PDF (WhatsApp…)'**
  String get partagerPdf;

  /// No description provided for @smsApresSync.
  ///
  /// In fr, this message translates to:
  /// **'Le hanout reçoit aussi un lien par SMS dès que la vente est synchronisée.'**
  String get smsApresSync;

  /// No description provided for @docAnnule.
  ///
  /// In fr, this message translates to:
  /// **'Annulé par avoir'**
  String get docAnnule;

  /// No description provided for @docPartiellementAnnule.
  ///
  /// In fr, this message translates to:
  /// **'Partiellement annulé par avoir'**
  String get docPartiellementAnnule;

  /// No description provided for @promo.
  ///
  /// In fr, this message translates to:
  /// **'promo'**
  String get promo;

  /// No description provided for @vendeur.
  ///
  /// In fr, this message translates to:
  /// **'Vendeur'**
  String get vendeur;

  /// No description provided for @client.
  ///
  /// In fr, this message translates to:
  /// **'Client'**
  String get client;

  /// No description provided for @produit.
  ///
  /// In fr, this message translates to:
  /// **'Produit'**
  String get produit;

  /// No description provided for @produitEnPhoto.
  ///
  /// In fr, this message translates to:
  /// **'Produit en photo'**
  String get produitEnPhoto;

  /// No description provided for @quantite.
  ///
  /// In fr, this message translates to:
  /// **'Quantité'**
  String get quantite;

  /// No description provided for @montant.
  ///
  /// In fr, this message translates to:
  /// **'Montant'**
  String get montant;

  /// No description provided for @statut.
  ///
  /// In fr, this message translates to:
  /// **'Statut'**
  String get statut;

  /// No description provided for @scannerBonSortie.
  ///
  /// In fr, this message translates to:
  /// **'Scanner un bon de sortie'**
  String get scannerBonSortie;

  /// No description provided for @chargementManuel.
  ///
  /// In fr, this message translates to:
  /// **'Chargement manuel'**
  String get chargementManuel;

  /// No description provided for @mouvements.
  ///
  /// In fr, this message translates to:
  /// **'Mouvements'**
  String get mouvements;

  /// No description provided for @bonACharger.
  ///
  /// In fr, this message translates to:
  /// **'Bon {numero} à charger'**
  String bonACharger(String numero);

  /// No description provided for @charger.
  ///
  /// In fr, this message translates to:
  /// **'Charger'**
  String get charger;

  /// No description provided for @chargerCeBon.
  ///
  /// In fr, this message translates to:
  /// **'Charger ce bon dans votre stock ?'**
  String get chargerCeBon;

  /// No description provided for @stockCharge.
  ///
  /// In fr, this message translates to:
  /// **'Stock chargé'**
  String get stockCharge;

  /// No description provided for @stockVide.
  ///
  /// In fr, this message translates to:
  /// **'Stock vide : scannez un bon de sortie.'**
  String get stockVide;

  /// No description provided for @ajouterProduits.
  ///
  /// In fr, this message translates to:
  /// **'Ajoutez les produits chargés'**
  String get ajouterProduits;

  /// No description provided for @mvtChargement.
  ///
  /// In fr, this message translates to:
  /// **'Chargement'**
  String get mvtChargement;

  /// No description provided for @mvtVente.
  ///
  /// In fr, this message translates to:
  /// **'Vente'**
  String get mvtVente;

  /// No description provided for @mvtRetour.
  ///
  /// In fr, this message translates to:
  /// **'Retour'**
  String get mvtRetour;

  /// No description provided for @mvtAjustement.
  ///
  /// In fr, this message translates to:
  /// **'Ajustement'**
  String get mvtAjustement;

  /// No description provided for @mvtPromo.
  ///
  /// In fr, this message translates to:
  /// **'Promo'**
  String get mvtPromo;

  /// No description provided for @totalCreances.
  ///
  /// In fr, this message translates to:
  /// **'Total des créances'**
  String get totalCreances;

  /// No description provided for @aucuneCreance.
  ///
  /// In fr, this message translates to:
  /// **'Aucun crédit en cours'**
  String get aucuneCreance;

  /// No description provided for @plafond.
  ///
  /// In fr, this message translates to:
  /// **'Plafond'**
  String get plafond;

  /// No description provided for @delaiJours.
  ///
  /// In fr, this message translates to:
  /// **'Délai maximal (jours)'**
  String get delaiJours;

  /// No description provided for @avertissementCredit.
  ///
  /// In fr, this message translates to:
  /// **'Ce hanout dépasse son plafond ou son délai de crédit. Vendre quand même à crédit ?'**
  String get avertissementCredit;

  /// No description provided for @politiqueCredit.
  ///
  /// In fr, this message translates to:
  /// **'Dépassement de crédit'**
  String get politiqueCredit;

  /// No description provided for @politiqueCreditExplication.
  ///
  /// In fr, this message translates to:
  /// **'Quand un hanout dépasse son plafond ou son délai'**
  String get politiqueCreditExplication;

  /// No description provided for @politiqueAvertir.
  ///
  /// In fr, this message translates to:
  /// **'M\'avertir, je décide'**
  String get politiqueAvertir;

  /// No description provided for @politiqueBloquer.
  ///
  /// In fr, this message translates to:
  /// **'Bloquer la vente à crédit'**
  String get politiqueBloquer;

  /// No description provided for @mesReservations.
  ///
  /// In fr, this message translates to:
  /// **'Mes réservations'**
  String get mesReservations;

  /// No description provided for @aucunePromo.
  ///
  /// In fr, this message translates to:
  /// **'Aucune promo pour l\'instant'**
  String get aucunePromo;

  /// No description provided for @aucuneReservation.
  ///
  /// In fr, this message translates to:
  /// **'Aucune réservation'**
  String get aucuneReservation;

  /// No description provided for @jusquAu.
  ///
  /// In fr, this message translates to:
  /// **'jusqu\'au {date}'**
  String jusquAu(String date);

  /// No description provided for @remisePourcent.
  ///
  /// In fr, this message translates to:
  /// **'-{pourcentage} %'**
  String remisePourcent(String pourcentage);

  /// No description provided for @remisePrixNet.
  ///
  /// In fr, this message translates to:
  /// **'Prix net {prix}'**
  String remisePrixNet(String prix);

  /// No description provided for @remiseOfferte.
  ///
  /// In fr, this message translates to:
  /// **'{achetee} achetés + {offerte} offerts'**
  String remiseOfferte(String achetee, String offerte);

  /// No description provided for @remisePalier.
  ///
  /// In fr, this message translates to:
  /// **'Paliers : {paliers}'**
  String remisePalier(String paliers);

  /// No description provided for @prixConseille.
  ///
  /// In fr, this message translates to:
  /// **'Prix de revente conseillé : {prix} (indicatif)'**
  String prixConseille(String prix);

  /// No description provided for @quantiteRestante.
  ///
  /// In fr, this message translates to:
  /// **'{n} disponibles'**
  String quantiteRestante(String n);

  /// No description provided for @plafondAmbulant.
  ///
  /// In fr, this message translates to:
  /// **'Maximum {max} par vendeur, déjà réservé : {deja}'**
  String plafondAmbulant(String max, String deja);

  /// No description provided for @delaiRetrait.
  ///
  /// In fr, this message translates to:
  /// **'À retirer sous {heures} h'**
  String delaiRetrait(String heures);

  /// No description provided for @plafondAtteint.
  ///
  /// In fr, this message translates to:
  /// **'Vous avez atteint le maximum autorisé, ou l\'offre est épuisée.'**
  String get plafondAtteint;

  /// No description provided for @reserver.
  ///
  /// In fr, this message translates to:
  /// **'Réserver'**
  String get reserver;

  /// No description provided for @reservationEnLigne.
  ///
  /// In fr, this message translates to:
  /// **'La réservation exige une connexion internet.'**
  String get reservationEnLigne;

  /// No description provided for @reservation.
  ///
  /// In fr, this message translates to:
  /// **'Réservation'**
  String get reservation;

  /// No description provided for @statutReservee.
  ///
  /// In fr, this message translates to:
  /// **'Réservée'**
  String get statutReservee;

  /// No description provided for @statutRetiree.
  ///
  /// In fr, this message translates to:
  /// **'Retirée'**
  String get statutRetiree;

  /// No description provided for @statutExpiree.
  ///
  /// In fr, this message translates to:
  /// **'Expirée'**
  String get statutExpiree;

  /// No description provided for @statutRevendue.
  ///
  /// In fr, this message translates to:
  /// **'Revendue'**
  String get statutRevendue;

  /// No description provided for @aRetirerAvant.
  ///
  /// In fr, this message translates to:
  /// **'À retirer avant le {date}'**
  String aRetirerAvant(String date);

  /// No description provided for @disponible.
  ///
  /// In fr, this message translates to:
  /// **'Disponible pour le dépannage'**
  String get disponible;

  /// No description provided for @indisponible.
  ///
  /// In fr, this message translates to:
  /// **'Indisponible'**
  String get indisponible;

  /// No description provided for @disponibleExplication.
  ///
  /// In fr, this message translates to:
  /// **'Activez pendant la tournée pour recevoir les ruptures des hanouts proches. Votre position n\'est envoyée que pendant que c\'est activé.'**
  String get disponibleExplication;

  /// No description provided for @aucuneDemande.
  ///
  /// In fr, this message translates to:
  /// **'Aucune demande pour l\'instant'**
  String get aucuneDemande;

  /// No description provided for @activerPourRecevoir.
  ///
  /// In fr, this message translates to:
  /// **'Activez la disponibilité pour recevoir les demandes'**
  String get activerPourRecevoir;

  /// No description provided for @demandePriseParAutre.
  ///
  /// In fr, this message translates to:
  /// **'Un autre vendeur a déjà pris cette demande'**
  String get demandePriseParAutre;

  /// No description provided for @derniereVente.
  ///
  /// In fr, this message translates to:
  /// **'dernière vente {date}'**
  String derniereVente(String date);

  /// No description provided for @repondreAvant.
  ///
  /// In fr, this message translates to:
  /// **'Répondre avant {date}'**
  String repondreAvant(String date);

  /// No description provided for @enRoute.
  ///
  /// In fr, this message translates to:
  /// **'Je suis en route'**
  String get enRoute;

  /// No description provided for @livrer.
  ///
  /// In fr, this message translates to:
  /// **'Livrer et encaisser'**
  String get livrer;

  /// No description provided for @statutEnAttente.
  ///
  /// In fr, this message translates to:
  /// **'En attente'**
  String get statutEnAttente;

  /// No description provided for @statutAcceptee.
  ///
  /// In fr, this message translates to:
  /// **'Acceptée'**
  String get statutAcceptee;

  /// No description provided for @statutEnRoute.
  ///
  /// In fr, this message translates to:
  /// **'En route'**
  String get statutEnRoute;

  /// No description provided for @statutLivree.
  ///
  /// In fr, this message translates to:
  /// **'Livrée'**
  String get statutLivree;

  /// No description provided for @statutSansReponse.
  ///
  /// In fr, this message translates to:
  /// **'Sans réponse'**
  String get statutSansReponse;

  /// No description provided for @horsLigne.
  ///
  /// In fr, this message translates to:
  /// **'Hors ligne : vos gestes sont gardés sur le téléphone'**
  String get horsLigne;

  /// No description provided for @enLigne.
  ///
  /// In fr, this message translates to:
  /// **'En ligne'**
  String get enLigne;

  /// No description provided for @jamaisSynchronise.
  ///
  /// In fr, this message translates to:
  /// **'Jamais synchronisé'**
  String get jamaisSynchronise;

  /// No description provided for @derniereSync.
  ///
  /// In fr, this message translates to:
  /// **'Dernière synchronisation : {date}'**
  String derniereSync(String date);

  /// No description provided for @operationsEnAttente.
  ///
  /// In fr, this message translates to:
  /// **'{n} opérations en attente d\'envoi'**
  String operationsEnAttente(String n);

  /// No description provided for @synchroniserMaintenant.
  ///
  /// In fr, this message translates to:
  /// **'Synchroniser maintenant'**
  String get synchroniserMaintenant;

  /// No description provided for @operationsRefusees.
  ///
  /// In fr, this message translates to:
  /// **'Opérations refusées par le serveur'**
  String get operationsRefusees;

  /// No description provided for @anomalies.
  ///
  /// In fr, this message translates to:
  /// **'Anomalies signalées'**
  String get anomalies;

  /// No description provided for @appareil.
  ///
  /// In fr, this message translates to:
  /// **'Appareil'**
  String get appareil;

  /// No description provided for @versionCndp.
  ///
  /// In fr, this message translates to:
  /// **'Version du texte CNDP accepté'**
  String get versionCndp;

  /// No description provided for @erreurHorsLigne.
  ///
  /// In fr, this message translates to:
  /// **'Pas de connexion internet. Réessayez quand le réseau revient.'**
  String get erreurHorsLigne;

  /// No description provided for @erreurPermissionPosition.
  ///
  /// In fr, this message translates to:
  /// **'Autorisez l\'accès à la position dans les réglages du téléphone.'**
  String get erreurPermissionPosition;

  /// No description provided for @erreurFactureSansMentions.
  ///
  /// In fr, this message translates to:
  /// **'Facture impossible : ICE et mentions légales manquants dans votre profil.'**
  String get erreurFactureSansMentions;

  /// No description provided for @erreurCreditBloque.
  ///
  /// In fr, this message translates to:
  /// **'Vente à crédit bloquée : plafond ou délai dépassé. Encaissez en espèces ou par chèque.'**
  String get erreurCreditBloque;

  /// No description provided for @erreurRemisePlafond.
  ///
  /// In fr, this message translates to:
  /// **'Remise au-delà du plafond autorisé'**
  String get erreurRemisePlafond;

  /// No description provided for @erreurQrAutreAmbulant.
  ///
  /// In fr, this message translates to:
  /// **'Ce bon de sortie est adressé à un autre vendeur'**
  String get erreurQrAutreAmbulant;

  /// No description provided for @erreurQrIllisible.
  ///
  /// In fr, this message translates to:
  /// **'QR code illisible'**
  String get erreurQrIllisible;

  /// No description provided for @erreurBonDejaCharge.
  ///
  /// In fr, this message translates to:
  /// **'Ce bon est déjà chargé'**
  String get erreurBonDejaCharge;

  /// No description provided for @erreurConflit.
  ///
  /// In fr, this message translates to:
  /// **'Action impossible : la situation a changé (quantité épuisée, demande déjà prise…)'**
  String get erreurConflit;

  /// No description provided for @erreurPlafondPromo.
  ///
  /// In fr, this message translates to:
  /// **'Maximum par vendeur dépassé pour cette promo'**
  String get erreurPlafondPromo;

  /// No description provided for @erreurTropDEssais.
  ///
  /// In fr, this message translates to:
  /// **'Trop d\'essais. Patientez quelques minutes.'**
  String get erreurTropDEssais;

  /// No description provided for @erreurCodeInvalide.
  ///
  /// In fr, this message translates to:
  /// **'Code incorrect'**
  String get erreurCodeInvalide;

  /// No description provided for @erreurCodeExpire.
  ///
  /// In fr, this message translates to:
  /// **'Code expiré, demandez-en un nouveau'**
  String get erreurCodeExpire;

  /// No description provided for @erreurConsentement.
  ///
  /// In fr, this message translates to:
  /// **'Votre accord sur les données personnelles est nécessaire'**
  String get erreurConsentement;

  /// No description provided for @erreurServeur.
  ///
  /// In fr, this message translates to:
  /// **'Erreur du serveur ({code})'**
  String erreurServeur(String code);

  /// No description provided for @erreurInconnue.
  ///
  /// In fr, this message translates to:
  /// **'Erreur inattendue'**
  String get erreurInconnue;

  /// No description provided for @opCHARGEMENT.
  ///
  /// In fr, this message translates to:
  /// **'Chargement'**
  String get opCHARGEMENT;

  /// No description provided for @opRETOUR.
  ///
  /// In fr, this message translates to:
  /// **'Retour'**
  String get opRETOUR;

  /// No description provided for @opINVENTAIRE.
  ///
  /// In fr, this message translates to:
  /// **'Inventaire'**
  String get opINVENTAIRE;

  /// No description provided for @opEMISSION_DOCUMENT.
  ///
  /// In fr, this message translates to:
  /// **'Vente'**
  String get opEMISSION_DOCUMENT;

  /// No description provided for @opAVOIR.
  ///
  /// In fr, this message translates to:
  /// **'Avoir'**
  String get opAVOIR;

  /// No description provided for @opVERSEMENT.
  ///
  /// In fr, this message translates to:
  /// **'Versement'**
  String get opVERSEMENT;

  /// No description provided for @opTARIF.
  ///
  /// In fr, this message translates to:
  /// **'Tarif'**
  String get opTARIF;

  /// No description provided for @opHANOUT.
  ///
  /// In fr, this message translates to:
  /// **'Nouveau hanout'**
  String get opHANOUT;

  /// No description provided for @opCREDIT_HANOUT.
  ///
  /// In fr, this message translates to:
  /// **'Plafond de crédit'**
  String get opCREDIT_HANOUT;

  /// No description provided for @opPARAMETRES_AMBULANT.
  ///
  /// In fr, this message translates to:
  /// **'Paramètres'**
  String get opPARAMETRES_AMBULANT;

  /// No description provided for @anoSTOCK_NEGATIF.
  ///
  /// In fr, this message translates to:
  /// **'Stock négatif'**
  String get anoSTOCK_NEGATIF;

  /// No description provided for @anoECART_SANS_MOTIF.
  ///
  /// In fr, this message translates to:
  /// **'Écart d\'inventaire sans motif'**
  String get anoECART_SANS_MOTIF;

  /// No description provided for @anoNUMEROTATION_TROU.
  ///
  /// In fr, this message translates to:
  /// **'Trou dans la numérotation'**
  String get anoNUMEROTATION_TROU;

  /// No description provided for @anoNUMERO_EN_DOUBLE.
  ///
  /// In fr, this message translates to:
  /// **'Numéro en double'**
  String get anoNUMERO_EN_DOUBLE;

  /// No description provided for @anoPLAFOND_CREDIT_DEPASSE.
  ///
  /// In fr, this message translates to:
  /// **'Plafond de crédit dépassé'**
  String get anoPLAFOND_CREDIT_DEPASSE;

  /// No description provided for @anoDELAI_CREDIT_DEPASSE.
  ///
  /// In fr, this message translates to:
  /// **'Délai de crédit dépassé'**
  String get anoDELAI_CREDIT_DEPASSE;

  /// No description provided for @anoCHARGEMENT_EN_DOUBLE.
  ///
  /// In fr, this message translates to:
  /// **'Bon de sortie chargé deux fois'**
  String get anoCHARGEMENT_EN_DOUBLE;

  /// No description provided for @anoPROMO_SANS_RETRAIT.
  ///
  /// In fr, this message translates to:
  /// **'Vente promo au-delà de la quantité retirée'**
  String get anoPROMO_SANS_RETRAIT;

  /// No description provided for @anoDEMANDE_NON_ATTRIBUEE.
  ///
  /// In fr, this message translates to:
  /// **'Livraison d\'une demande prise par un autre'**
  String get anoDEMANDE_NON_ATTRIBUEE;

  /// No description provided for @anoREMISE_PLAFOND_DEPASSE.
  ///
  /// In fr, this message translates to:
  /// **'Remise au-delà du plafond'**
  String get anoREMISE_PLAFOND_DEPASSE;

  /// No description provided for @erreurAutreAmbulant.
  ///
  /// In fr, this message translates to:
  /// **'Un autre vendeur a des ventes pas encore envoyées sur ce téléphone. Il doit se reconnecter et synchroniser d\'abord.'**
  String get erreurAutreAmbulant;

  /// No description provided for @docRejete.
  ///
  /// In fr, this message translates to:
  /// **'Refusé par le serveur : voir Synchronisation'**
  String get docRejete;

  /// No description provided for @tva.
  ///
  /// In fr, this message translates to:
  /// **'TVA'**
  String get tva;

  /// No description provided for @tvaAssujetti.
  ///
  /// In fr, this message translates to:
  /// **'Assujetti à la TVA'**
  String get tvaAssujetti;

  /// No description provided for @tvaNonAssujetti.
  ///
  /// In fr, this message translates to:
  /// **'Non assujetti à la TVA'**
  String get tvaNonAssujetti;

  /// No description provided for @erreurFactureTvaInconnue.
  ///
  /// In fr, this message translates to:
  /// **'Facture impossible : un produit du panier n\'a pas de taux de TVA connu. Émettez un bon de livraison.'**
  String get erreurFactureTvaInconnue;

  /// No description provided for @tvaComprise.
  ///
  /// In fr, this message translates to:
  /// **'Dont TVA'**
  String get tvaComprise;

  /// No description provided for @envoyerLienSms.
  ///
  /// In fr, this message translates to:
  /// **'Envoyer le lien par SMS'**
  String get envoyerLienSms;

  /// No description provided for @erreurDocNonSynchronise.
  ///
  /// In fr, this message translates to:
  /// **'Synchronisez d\'abord la vente pour obtenir son lien.'**
  String get erreurDocNonSynchronise;

  /// No description provided for @erreurHanoutSansTelephone.
  ///
  /// In fr, this message translates to:
  /// **'Ce hanout n\'a pas de numéro de téléphone.'**
  String get erreurHanoutSansTelephone;

  /// No description provided for @erreurAppareilRevoque.
  ///
  /// In fr, this message translates to:
  /// **'Ce téléphone a été déconnecté à distance. Reconnectez-vous.'**
  String get erreurAppareilRevoque;

  /// No description provided for @receptionConforme.
  ///
  /// In fr, this message translates to:
  /// **'Réception confirmée par le hanout le {date}'**
  String receptionConforme(String date);

  /// No description provided for @receptionEcart.
  ///
  /// In fr, this message translates to:
  /// **'Le hanout a signalé un écart à la réception'**
  String get receptionEcart;

  /// No description provided for @receptionEcartLigne.
  ///
  /// In fr, this message translates to:
  /// **'{produit} : {recu} reçus sur {livre}'**
  String receptionEcartLigne(String produit, String recu, String livre);

  /// No description provided for @contestations.
  ///
  /// In fr, this message translates to:
  /// **'Contestations'**
  String get contestations;

  /// No description provided for @contestationsOuvertes.
  ///
  /// In fr, this message translates to:
  /// **'{n} contestations ouvertes'**
  String contestationsOuvertes(String n);

  /// No description provided for @aucuneContestation.
  ///
  /// In fr, this message translates to:
  /// **'Aucune contestation ouverte'**
  String get aucuneContestation;

  /// No description provided for @clore.
  ///
  /// In fr, this message translates to:
  /// **'Clore'**
  String get clore;

  /// No description provided for @commentaire.
  ///
  /// In fr, this message translates to:
  /// **'Commentaire'**
  String get commentaire;

  /// No description provided for @document.
  ///
  /// In fr, this message translates to:
  /// **'Document'**
  String get document;

  /// No description provided for @compteCredit.
  ///
  /// In fr, this message translates to:
  /// **'Solde du compte'**
  String get compteCredit;

  /// No description provided for @motifMONTANT.
  ///
  /// In fr, this message translates to:
  /// **'Montant'**
  String get motifMONTANT;

  /// No description provided for @motifQUANTITE.
  ///
  /// In fr, this message translates to:
  /// **'Quantité'**
  String get motifQUANTITE;

  /// No description provided for @motifNON_RECU.
  ///
  /// In fr, this message translates to:
  /// **'Non reçu'**
  String get motifNON_RECU;

  /// No description provided for @motifDEJA_PAYE.
  ///
  /// In fr, this message translates to:
  /// **'Déjà payé'**
  String get motifDEJA_PAYE;

  /// No description provided for @motifAUTRE.
  ///
  /// In fr, this message translates to:
  /// **'Autre'**
  String get motifAUTRE;

  /// No description provided for @opRESOLUTION_CONTESTATION.
  ///
  /// In fr, this message translates to:
  /// **'Contestation close'**
  String get opRESOLUTION_CONTESTATION;

  /// No description provided for @anoTVA_INCONNUE.
  ///
  /// In fr, this message translates to:
  /// **'Taux de TVA inconnu'**
  String get anoTVA_INCONNUE;

  /// No description provided for @erreurRoleNonAmbulant.
  ///
  /// In fr, this message translates to:
  /// **'Ce numéro n\'est pas un compte de vendeur ambulant.'**
  String get erreurRoleNonAmbulant;

  /// No description provided for @rupture.
  ///
  /// In fr, this message translates to:
  /// **'Rupture'**
  String get rupture;

  /// No description provided for @plafondDepasse.
  ///
  /// In fr, this message translates to:
  /// **'Plafond dépassé'**
  String get plafondDepasse;

  /// No description provided for @delaiDepasse.
  ///
  /// In fr, this message translates to:
  /// **'Délai dépassé'**
  String get delaiDepasse;

  /// No description provided for @profil.
  ///
  /// In fr, this message translates to:
  /// **'Mon profil'**
  String get profil;

  /// No description provided for @profilExplication.
  ///
  /// In fr, this message translates to:
  /// **'Votre nom est montré aux grossistes et aux hanouts avec qui vous travaillez, jamais aux marques. Vos catégories choisissent les promos que vous recevez.'**
  String get profilExplication;

  /// No description provided for @nomComplet.
  ///
  /// In fr, this message translates to:
  /// **'Nom et prénom'**
  String get nomComplet;

  /// No description provided for @cin.
  ///
  /// In fr, this message translates to:
  /// **'CIN (facultatif)'**
  String get cin;

  /// No description provided for @cinInvalide.
  ///
  /// In fr, this message translates to:
  /// **'CIN : 1 ou 2 lettres puis jusqu\'à 6 chiffres (ex. BK123456)'**
  String get cinInvalide;

  /// No description provided for @vehicule.
  ///
  /// In fr, this message translates to:
  /// **'Véhicule (facultatif)'**
  String get vehicule;

  /// No description provided for @zonesCouvertes.
  ///
  /// In fr, this message translates to:
  /// **'Zones couvertes'**
  String get zonesCouvertes;

  /// No description provided for @categoriesVendues.
  ///
  /// In fr, this message translates to:
  /// **'Catégories vendues'**
  String get categoriesVendues;

  /// No description provided for @listeApresConnexion.
  ///
  /// In fr, this message translates to:
  /// **'Liste disponible après une connexion'**
  String get listeApresConnexion;

  /// No description provided for @plusTard.
  ///
  /// In fr, this message translates to:
  /// **'Plus tard'**
  String get plusTard;

  /// No description provided for @profilEnregistre.
  ///
  /// In fr, this message translates to:
  /// **'Profil enregistré'**
  String get profilEnregistre;

  /// No description provided for @opPROFIL_AMBULANT.
  ///
  /// In fr, this message translates to:
  /// **'Profil'**
  String get opPROFIL_AMBULANT;

  /// No description provided for @mentionsLegales.
  ///
  /// In fr, this message translates to:
  /// **'Mentions légales (facture)'**
  String get mentionsLegales;

  /// No description provided for @mentionsExplication.
  ///
  /// In fr, this message translates to:
  /// **'Avec un ICE, vous pouvez émettre des factures en plus des bons de livraison.'**
  String get mentionsExplication;

  /// No description provided for @mentionsAucune.
  ///
  /// In fr, this message translates to:
  /// **'Aucune : bons de livraison seulement'**
  String get mentionsAucune;

  /// No description provided for @identifiantFiscal.
  ///
  /// In fr, this message translates to:
  /// **'Identifiant fiscal (IF)'**
  String get identifiantFiscal;

  /// No description provided for @registreCommerce.
  ///
  /// In fr, this message translates to:
  /// **'Registre du commerce (RC)'**
  String get registreCommerce;

  /// No description provided for @assujettiTva.
  ///
  /// In fr, this message translates to:
  /// **'Assujetti à la TVA'**
  String get assujettiTva;

  /// No description provided for @offreNonCiblee.
  ///
  /// In fr, this message translates to:
  /// **'Cette offre ne vous est plus proposée.'**
  String get offreNonCiblee;

  /// No description provided for @consentementNouveauTexte.
  ///
  /// In fr, this message translates to:
  /// **'Le texte sur vos données a changé : votre nom est montré aux grossistes et aux hanouts avec qui vous travaillez, et les notifications et les SMS passent par Google Firebase Cloud Messaging et Infobip, hors du Maroc.'**
  String get consentementNouveauTexte;

  /// No description provided for @lireEtAccepter.
  ///
  /// In fr, this message translates to:
  /// **'Lire et accepter'**
  String get lireEtAccepter;

  /// No description provided for @profilConsentementRequis.
  ///
  /// In fr, this message translates to:
  /// **'Acceptez d\'abord le nouveau texte sur vos données pour enregistrer votre profil.'**
  String get profilConsentementRequis;

  /// No description provided for @canalHaute.
  ///
  /// In fr, this message translates to:
  /// **'Urgent : dépannage, promos, réservations'**
  String get canalHaute;

  /// No description provided for @canalMoyenne.
  ///
  /// In fr, this message translates to:
  /// **'Suivi : crédit, demandes, contestations'**
  String get canalMoyenne;

  /// No description provided for @canalBasse.
  ///
  /// In fr, this message translates to:
  /// **'Stock embarqué'**
  String get canalBasse;
}

class _TDelegate extends LocalizationsDelegate<T> {
  const _TDelegate();

  @override
  Future<T> load(Locale locale) {
    return SynchronousFuture<T>(lookupT(locale));
  }

  @override
  bool isSupported(Locale locale) =>
      <String>['ar', 'ary', 'fr'].contains(locale.languageCode);

  @override
  bool shouldReload(_TDelegate old) => false;
}

T lookupT(Locale locale) {
  // Lookup logic when language+script codes are specified.
  switch (locale.languageCode) {
    case 'ary':
      {
        switch (locale.scriptCode) {
          case 'Latn':
            return TAryLatn();
        }
        break;
      }
  }

  // Lookup logic when only language code is specified.
  switch (locale.languageCode) {
    case 'ar':
      return TAr();
    case 'ary':
      return TAry();
    case 'fr':
      return TFr();
  }

  throw FlutterError(
    'T.delegate failed to load unsupported locale "$locale". This is likely '
    'an issue with the localizations generation tool. Please file an issue '
    'on GitHub with a reproducible sample app and the gen-l10n configuration '
    'that was used.',
  );
}

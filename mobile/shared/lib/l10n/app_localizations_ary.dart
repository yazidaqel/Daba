// ignore: unused_import
import 'package:intl/intl.dart' as intl;

import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Moroccan Arabic (`ary`).
class TAry extends T {
  TAry([String locale = 'ary']) : super(locale);

  @override
  String get titreApp => 'دابا';

  @override
  String get langue => 'اللغة';

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
      'دخل النمرة ديال التيليفون ديالك، غادي يوصلك كود ف SMS.';

  @override
  String get reconnexionExplication =>
      'السيسيون سالات. عاود دخل: البيعات ديالك باقيين ف التيليفون.';

  @override
  String get telephone => 'التيليفون';

  @override
  String get telephoneInvalide => 'النمرة ماشي صحيحة';

  @override
  String get codeSms => 'الكود اللي جاك ف SMS';

  @override
  String get changerNumero => 'بدل النمرة';

  @override
  String get recevoirCode => 'صيفط ليا الكود';

  @override
  String get seConnecter => 'دخل';

  @override
  String get consentementTitre => 'المعلومات الشخصية ديالك';

  @override
  String get consentementTexte =>
      'دابا كيسجل البيعات، السلعة والكريدي ديال الكليان باش يعاونك (قانون 09-08، تصريح CNDP). حتى معلومة شخصية ما كتمشي للماركات. البلاصة ديالك ما كتتاخدش غير إلا قبلتي، وغير ملي تكون «موجود» للدبانة. تقدر تحيد الموافقة وتطلب المعلومات ديالك فأي وقت.';

  @override
  String get consentementDonnees => 'موافق على المعالجة ديال المعلومات ديالي';

  @override
  String get consentementGeo => 'موافق يتعرف البلاصة ديالي ملي نكون موجود';

  @override
  String get consentementGeoDemande =>
      'باش توصلك طلبات الدبانة، دابا خاصو يعرف البلاصة ديالك غير ملي تكون موجود. موافق؟';

  @override
  String get accepter => 'قبل';

  @override
  String get refuser => 'رفض';

  @override
  String get annuler => 'لغي';

  @override
  String get confirmer => 'أكد';

  @override
  String get valider => 'صادق';

  @override
  String get enregistrer => 'سجل';

  @override
  String get voir => 'شوف';

  @override
  String get pinCreer => 'ختار كود سري ديال 4 أرقام';

  @override
  String get pinConfirmer => 'عاود الكود السري';

  @override
  String get pinSaisir => 'دخل الكود السري ديالك';

  @override
  String get pinFaux => 'الكود السري غالط';

  @override
  String get pinDifferent => 'الجوج كودات ماشي بحال بحال';

  @override
  String get pinOublie => 'نسيتي الكود؟ عاود دخل ب SMS';

  @override
  String get ongletHanouts => 'الحوانت';

  @override
  String get ongletStock => 'السلعة';

  @override
  String get ongletKarni => 'الكرني';

  @override
  String get ongletPromos => 'البروموات';

  @override
  String get ongletDepannage => 'الدبانة';

  @override
  String get synchronisation => 'السانكرو';

  @override
  String get parametres => 'الإعدادات';

  @override
  String get deconnexion => 'خرج';

  @override
  String get deconnexionConfirmer => 'تخرج من هاد التيليفون؟';

  @override
  String deconnexionAvecAttente(String n) {
    return '$n عمليات باقي ما تصيفطو. غادي يبقاو ف التيليفون ويتصيفطو ملي تعاود تدخل. تخرج؟';
  }

  @override
  String get reconnexionRequise =>
      'السيسيون سالات: عاود دخل باش تصيفط البيعات. تقدر تكمل تبيع.';

  @override
  String alerteDemande(String produit, String quantite) {
    return 'حانوت قريب خاصو: $produit × $quantite';
  }

  @override
  String get nouveauHanout => 'حانوت جديد';

  @override
  String get rechercherHanout => 'السمية، مول الحانوت ولا التيليفون';

  @override
  String get liste => 'ليستة';

  @override
  String get carte => 'الخريطة';

  @override
  String get aucunHanout => 'ما كاين حتى حانوت. دير سانكرو ولا زيد واحد.';

  @override
  String get vendre => 'بيع';

  @override
  String get solde => 'الكريدي';

  @override
  String soldeActuel(String montant) {
    return 'الكريدي دابا: $montant';
  }

  @override
  String plafondValeur(String montant) {
    return 'الحد $montant';
  }

  @override
  String delaiValeur(String jours) {
    return 'المدة $jours يوم';
  }

  @override
  String get derniereCommande => 'آخر كوموند';

  @override
  String get versement => 'خلاص';

  @override
  String get plafondCredit => 'الحد ديال الكريدي';

  @override
  String get historique => 'التاريخ';

  @override
  String get aucunMouvement => 'والو دابا';

  @override
  String get nomHanout => 'سمية الحانوت';

  @override
  String get gerant => 'مول الحانوت';

  @override
  String get adresse => 'العنوان';

  @override
  String get ville => 'المدينة';

  @override
  String get ice => 'ICE';

  @override
  String get iceInvalide => 'ICE فيه 15 رقم';

  @override
  String champObligatoire(String champ) {
    return '$champ ضروري';
  }

  @override
  String get positionActuelle => 'سجل البلاصة ديالي دابا كبلاصة ديال الحانوت';

  @override
  String venteA(String hanout) {
    return 'بيعة · $hanout';
  }

  @override
  String get panierVide => 'زيد السلعة';

  @override
  String get total => 'المجموع';

  @override
  String get bonLivraison => 'بون ليفريزون';

  @override
  String get facture => 'فاكتورة';

  @override
  String get avoir => 'أفوار';

  @override
  String get factureIndisponible =>
      'الفاكتورة خاصها ICE والمعلومات القانونية ف البروفيل ديالك.';

  @override
  String get especes => 'كاش';

  @override
  String get cheque => 'شيك';

  @override
  String get chequeNumero => 'النمرة ديال الشيك';

  @override
  String get chequeBanque => 'البنكة';

  @override
  String get resteACredit => 'اللي بقى كريدي';

  @override
  String get toutEspeces => 'كلشي كاش';

  @override
  String get toutCredit => 'كلشي كريدي';

  @override
  String get confirmationHanout => 'مول الحانوت أكد التوصيل';

  @override
  String get emettreBon => 'طلع البون';

  @override
  String get emettreFacture => 'طلع الفاكتورة';

  @override
  String get reglementSuperieur => 'الخلاص كثر من المجموع';

  @override
  String get prixUnitaire => 'الثمن ديال الوحدة';

  @override
  String get remise => 'تخفيض';

  @override
  String get remisePromo => 'تخفيض البرومو';

  @override
  String get tarifNegocie => 'ثمن متافقين عليه';

  @override
  String stockInsuffisant(String n) {
    return 'السلعة $n';
  }

  @override
  String get rechercherProduit => 'السلعة، الماركة ولا الكود';

  @override
  String get aucunProduit => 'ما كاينة حتى سلعة';

  @override
  String get regle => 'مخلص';

  @override
  String get partagerPdf => 'بارطاجي PDF (واتساب…)';

  @override
  String get smsApresSync =>
      'الحانوت حتى هو كيوصلو لينك ف SMS ملي البيعة تتسانكرونيزا.';

  @override
  String get docAnnule => 'ملغي ب أفوار';

  @override
  String get docPartiellementAnnule => 'ملغي شوية ب أفوار';

  @override
  String get promo => 'برومو';

  @override
  String get vendeur => 'البياع';

  @override
  String get client => 'الكليان';

  @override
  String get produit => 'السلعة';

  @override
  String get produitEnPhoto => 'سلعة ف تصويرة';

  @override
  String get quantite => 'الكمية';

  @override
  String get montant => 'المبلغ';

  @override
  String get statut => 'الحالة';

  @override
  String get scannerBonSortie => 'سكاني بون ديال الخروج';

  @override
  String get chargementManuel => 'شارجي باليد';

  @override
  String get mouvements => 'الحركات';

  @override
  String bonACharger(String numero) {
    return 'البون $numero خاصو يتشارجا';
  }

  @override
  String get charger => 'شارجي';

  @override
  String get chargerCeBon => 'تشارجي هاد البون ف السلعة ديالك؟';

  @override
  String get stockCharge => 'السلعة تشارجات';

  @override
  String get stockVide => 'ما عندك سلعة: سكاني بون ديال الخروج.';

  @override
  String get ajouterProduits => 'زيد السلعة اللي شارجيتي';

  @override
  String get mvtChargement => 'شارجمون';

  @override
  String get mvtVente => 'بيعة';

  @override
  String get mvtRetour => 'روتور';

  @override
  String get mvtAjustement => 'تصحيح';

  @override
  String get mvtPromo => 'برومو';

  @override
  String get totalCreances => 'مجموع الكريدي';

  @override
  String get aucuneCreance => 'ما كاين حتى كريدي';

  @override
  String get plafond => 'الحد';

  @override
  String get delaiJours => 'أكبر مدة (أيام)';

  @override
  String get avertissementCredit =>
      'هاد الحانوت فات الحد ولا المدة ديال الكريدي. تبيع ليه بالكريدي واخا؟';

  @override
  String get politiqueCredit => 'فوتان الكريدي';

  @override
  String get politiqueCreditExplication => 'ملي شي حانوت يفوت الحد ولا المدة';

  @override
  String get politiqueAvertir => 'نبهني وأنا نقرر';

  @override
  String get politiqueBloquer => 'بلوكي البيع بالكريدي';

  @override
  String get mesReservations => 'الريزيرفاسيونات ديالي';

  @override
  String get aucunePromo => 'ما كاين حتى برومو دابا';

  @override
  String get aucuneReservation => 'ما كاين حتى ريزيرفاسيون';

  @override
  String jusquAu(String date) {
    return 'حتى ل $date';
  }

  @override
  String remisePourcent(String pourcentage) {
    return '-$pourcentage %';
  }

  @override
  String remisePrixNet(String prix) {
    return 'الثمن الصافي $prix';
  }

  @override
  String remiseOfferte(String achetee, String offerte) {
    return 'شري $achetee وخود $offerte فابور';
  }

  @override
  String remisePalier(String paliers) {
    return 'دروج: $paliers';
  }

  @override
  String prixConseille(String prix) {
    return 'الثمن المنصوح بيه: $prix (غير للمعلومة)';
  }

  @override
  String quantiteRestante(String n) {
    return '$n موجودين';
  }

  @override
  String plafondAmbulant(String max, String deja) {
    return 'الماكسيموم $max لكل بياع، محجوز: $deja';
  }

  @override
  String delaiRetrait(String heures) {
    return 'خاصك تجيبها ف $heures ساعة';
  }

  @override
  String get plafondAtteint => 'وصلتي للماكسيموم، ولا البرومو سالا.';

  @override
  String get reserver => 'ريزيرفي';

  @override
  String get reservationEnLigne => 'الريزيرفاسيون خاصها الأنترنيت.';

  @override
  String get reservation => 'ريزيرفاسيون';

  @override
  String get statutReservee => 'محجوزة';

  @override
  String get statutRetiree => 'تجابت';

  @override
  String get statutExpiree => 'سالات';

  @override
  String get statutRevendue => 'تباعت';

  @override
  String aRetirerAvant(String date) {
    return 'جيبها قبل $date';
  }

  @override
  String get disponible => 'موجود للدبانة';

  @override
  String get indisponible => 'ماشي موجود';

  @override
  String get disponibleExplication =>
      'شعلو ف الطورني باش يوصلوك الحوانت القراب اللي خاصهم السلعة. البلاصة ديالك ما كتمشي غير ملي يكون مشعول.';

  @override
  String get aucuneDemande => 'ما كاين حتى طلب دابا';

  @override
  String get activerPourRecevoir => 'شعل « موجود » باش يوصلوك الطلبات';

  @override
  String get demandePriseParAutre => 'شي بياع آخر خدا هاد الطلب';

  @override
  String derniereVente(String date) {
    return 'آخر بيعة $date';
  }

  @override
  String repondreAvant(String date) {
    return 'جاوب قبل $date';
  }

  @override
  String get enRoute => 'راني جاي';

  @override
  String get livrer => 'وصل وخلص';

  @override
  String get statutEnAttente => 'كتسنى';

  @override
  String get statutAcceptee => 'مقبولة';

  @override
  String get statutEnRoute => 'ف الطريق';

  @override
  String get statutLivree => 'توصلات';

  @override
  String get statutSansReponse => 'بلا جواب';

  @override
  String get horsLigne => 'بلا أنترنيت: داكشي اللي درتي محفوظ ف التيليفون';

  @override
  String get enLigne => 'كاين الأنترنيت';

  @override
  String get jamaisSynchronise => 'عمرك ما درتي سانكرو';

  @override
  String derniereSync(String date) {
    return 'آخر سانكرو: $date';
  }

  @override
  String operationsEnAttente(String n) {
    return '$n عمليات كتسنى تتصيفط';
  }

  @override
  String get synchroniserMaintenant => 'دير سانكرو دابا';

  @override
  String get operationsRefusees => 'عمليات رفضها السيرفور';

  @override
  String get anomalies => 'مشاكل تلقاو';

  @override
  String get appareil => 'التيليفون';

  @override
  String get versionCndp => 'النسخة ديال نص CNDP اللي قبلتي';

  @override
  String get erreurHorsLigne => 'ما كاينش الأنترنيت. عاود ملي ترجع الريزو.';

  @override
  String get erreurPermissionPosition =>
      'عطي الحق للبلاصة ف الإعدادات ديال التيليفون.';

  @override
  String get erreurFactureSansMentions =>
      'ما يمكنش الفاكتورة: ICE والمعلومات القانونية ناقصين ف البروفيل.';

  @override
  String get erreurCreditBloque =>
      'البيع بالكريدي مبلوكي: فات الحد ولا المدة. خلص كاش ولا ب شيك.';

  @override
  String get erreurRemisePlafond => 'التخفيض فات الحد المسموح';

  @override
  String get erreurQrAutreAmbulant => 'هاد البون ديال بياع آخر';

  @override
  String get erreurQrIllisible => 'QR ما تقراش';

  @override
  String get erreurBonDejaCharge => 'هاد البون تشارجا من قبل';

  @override
  String get erreurConflit =>
      'ما يمكنش: الحالة تبدلات (السلعة سالات، الطلب تخدا…)';

  @override
  String get erreurPlafondPromo => 'فتي الماكسيموم ديال هاد البرومو';

  @override
  String get erreurTropDEssais => 'بزاف ديال المحاولات. تسنى شي دقايق.';

  @override
  String get erreurCodeInvalide => 'الكود غالط';

  @override
  String get erreurCodeExpire => 'الكود سالا، طلب واحد جديد';

  @override
  String get erreurConsentement => 'خاص الموافقة ديالك على المعلومات الشخصية';

  @override
  String erreurServeur(String code) {
    return 'مشكل ف السيرفور ($code)';
  }

  @override
  String get erreurInconnue => 'مشكل ما متوقعش';

  @override
  String get opCHARGEMENT => 'شارجمون';

  @override
  String get opRETOUR => 'روتور';

  @override
  String get opINVENTAIRE => 'الجرد';

  @override
  String get opEMISSION_DOCUMENT => 'بيعة';

  @override
  String get opAVOIR => 'أفوار';

  @override
  String get opVERSEMENT => 'خلاص';

  @override
  String get opTARIF => 'ثمن';

  @override
  String get opHANOUT => 'حانوت جديد';

  @override
  String get opCREDIT_HANOUT => 'الحد ديال الكريدي';

  @override
  String get opPARAMETRES_AMBULANT => 'الإعدادات';

  @override
  String get anoSTOCK_NEGATIF => 'السلعة تحت الصفر';

  @override
  String get anoECART_SANS_MOTIF => 'فرق ف الجرد بلا سبب';

  @override
  String get anoNUMEROTATION_TROU => 'نمرة ناقصة';

  @override
  String get anoNUMERO_EN_DOUBLE => 'نمرة عاودات';

  @override
  String get anoPLAFOND_CREDIT_DEPASSE => 'فات الحد ديال الكريدي';

  @override
  String get anoDELAI_CREDIT_DEPASSE => 'فاتت المدة ديال الكريدي';

  @override
  String get anoCHARGEMENT_EN_DOUBLE => 'البون تشارجا جوج مرات';

  @override
  String get anoPROMO_SANS_RETRAIT => 'بعتي برومو كثر من اللي جبتي';

  @override
  String get anoDEMANDE_NON_ATTRIBUEE => 'وصلتي طلب خداه بياع آخر';

  @override
  String get anoREMISE_PLAFOND_DEPASSE => 'التخفيض فات الحد';

  @override
  String get erreurAutreAmbulant =>
      'كاين بياع آخر عندو بيعات باقي ما تصيفطو ف هاد التيليفون. خاصو يدخل ويدير سانكرو هو الأول.';

  @override
  String get docRejete => 'السيرفور رفضو: شوف السانكرو';

  @override
  String get tva => 'TVA';

  @override
  String get tvaAssujetti => 'خاضع ل TVA';

  @override
  String get tvaNonAssujetti => 'ماشي خاضع ل TVA';

  @override
  String get erreurFactureTvaInconnue =>
      'ما يمكنش الفاكتورة: شي سلعة ما عندهاش نسبة TVA معروفة. طلع بون ليفريزون.';

  @override
  String get tvaComprise => 'فيها TVA';

  @override
  String get envoyerLienSms => 'صيفط اللينك ف SMS';

  @override
  String get erreurDocNonSynchronise =>
      'دير سانكرو للبيعة هي الأولى باش تاخد اللينك.';

  @override
  String get erreurHanoutSansTelephone => 'هاد الحانوت ما عندوش نمرة تيليفون.';

  @override
  String get erreurAppareilRevoque => 'هاد التيليفون تقطع من بعيد. عاود دخل.';

  @override
  String receptionConforme(String date) {
    return 'مول الحانوت أكد التوصيل نهار $date';
  }

  @override
  String get receptionEcart => 'مول الحانوت قال كاين فرق ف التوصيل';

  @override
  String receptionEcartLigne(String produit, String recu, String livre) {
    return '$produit: وصلو $recu من $livre';
  }

  @override
  String get contestations => 'الشكايات';

  @override
  String contestationsOuvertes(String n) {
    return '$n شكايات مفتوحين';
  }

  @override
  String get aucuneContestation => 'ما كاينة حتى شكاية';

  @override
  String get clore => 'سد';

  @override
  String get commentaire => 'تعليق';

  @override
  String get document => 'الوثيقة';

  @override
  String get compteCredit => 'الكريدي ديال الحساب';

  @override
  String get motifMONTANT => 'المبلغ';

  @override
  String get motifQUANTITE => 'الكمية';

  @override
  String get motifNON_RECU => 'ما وصلش';

  @override
  String get motifDEJA_PAYE => 'مخلص من قبل';

  @override
  String get motifAUTRE => 'شي حاجة أخرى';

  @override
  String get opRESOLUTION_CONTESTATION => 'شكاية تسدات';

  @override
  String get anoTVA_INCONNUE => 'نسبة TVA ما معروفاش';

  @override
  String get erreurRoleNonAmbulant => 'هاد النمرة ماشي ديال بياع متجول.';
}

/// The translations for Moroccan Arabic, using the Latin script (`ary_Latn`).
class TAryLatn extends TAry {
  TAryLatn() : super('ary_Latn');

  @override
  String get titreApp => 'Daba';

  @override
  String get langue => 'Lougha';

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
      'Dkhel nmra dyal tilifoun dyalek, ghadi ywsslek code f SMS.';

  @override
  String get reconnexionExplication =>
      'Session salat. 3awed dkhel: lbi3at dyalek ba9yin f tilifoun.';

  @override
  String get telephone => 'Tilifoun';

  @override
  String get telephoneInvalide => 'Nmra machi s7i7a';

  @override
  String get codeSms => 'Code li jak f SMS';

  @override
  String get changerNumero => 'Beddel nmra';

  @override
  String get recevoirCode => 'Siftli l code';

  @override
  String get seConnecter => 'Dkhel';

  @override
  String get consentementTitre => 'Lma3loumat chakhsiya dyalek';

  @override
  String get consentementTexte =>
      'Daba kaysajjel lbi3at, sel3a w lcrédit dyal lclients bach y3awnek (9anoun 09-08, tasri7 CNDP). 7ta ma3louma chakhsiya ma katmchi l marques. Blassa dyalek ma kattakhed ghir ila 9bilti, w ghir mli tkoun « mojoud » l dépannage. T9der t7iyed lmowafa9a w tetlob lma3loumat dyalek f ay we9t.';

  @override
  String get consentementDonnees =>
      'Mowafe9 3la lmou3alaja dyal lma3loumat dyali';

  @override
  String get consentementGeo => 'Mowafe9 yt3ref blassa dyali mli nkoun mojoud';

  @override
  String get consentementGeoDemande =>
      'Bach ywsslouk talabat dépannage, Daba khasso y3ref blassa dyalek ghir mli tkoun mojoud. Mowafe9?';

  @override
  String get accepter => '9bel';

  @override
  String get refuser => 'Rfed';

  @override
  String get annuler => 'Lghi';

  @override
  String get confirmer => 'Akked';

  @override
  String get valider => 'Sade9';

  @override
  String get enregistrer => 'Sajjel';

  @override
  String get voir => 'Chouf';

  @override
  String get pinCreer => 'Khtar code sirri dyal 4 ar9am';

  @override
  String get pinConfirmer => '3awed l code sirri';

  @override
  String get pinSaisir => 'Dkhel l code sirri dyalek';

  @override
  String get pinFaux => 'L code sirri ghalet';

  @override
  String get pinDifferent => 'Jouj codes machi b7al b7al';

  @override
  String get pinOublie => 'Nsiti l code? 3awed dkhel b SMS';

  @override
  String get ongletHanouts => 'L7wanet';

  @override
  String get ongletStock => 'Sel3a';

  @override
  String get ongletKarni => 'Karni';

  @override
  String get ongletPromos => 'Promos';

  @override
  String get ongletDepannage => 'Dépannage';

  @override
  String get synchronisation => 'Synchro';

  @override
  String get parametres => 'Parametres';

  @override
  String get deconnexion => 'Khrej';

  @override
  String get deconnexionConfirmer => 'Tkhrej men had tilifoun?';

  @override
  String deconnexionAvecAttente(String n) {
    return '$n 3amaliyat ba9i ma tsiftou. Ghadi yb9aw f tilifoun w ytsiftou mli t3awed tdkhel. Tkhrej?';
  }

  @override
  String get reconnexionRequise =>
      'Session salat: 3awed dkhel bach tsifet lbi3at. T9der tkemmel tbi3.';

  @override
  String alerteDemande(String produit, String quantite) {
    return '7anout 9rib khasso: $produit × $quantite';
  }

  @override
  String get nouveauHanout => '7anout jdid';

  @override
  String get rechercherHanout => 'Smiya, mol l7anout wla tilifoun';

  @override
  String get liste => 'Lista';

  @override
  String get carte => 'Kharita';

  @override
  String get aucunHanout => 'Ma kayn 7ta 7anout. Dir synchro wla zid wa7ed.';

  @override
  String get vendre => 'Bi3';

  @override
  String get solde => 'Lcrédit';

  @override
  String soldeActuel(String montant) {
    return 'Lcrédit daba: $montant';
  }

  @override
  String plafondValeur(String montant) {
    return 'L7ed $montant';
  }

  @override
  String delaiValeur(String jours) {
    return 'Lmodda $jours youm';
  }

  @override
  String get derniereCommande => 'Akher commande';

  @override
  String get versement => 'Khlass';

  @override
  String get plafondCredit => 'L7ed dyal lcrédit';

  @override
  String get historique => 'Tarikh';

  @override
  String get aucunMouvement => 'Walou daba';

  @override
  String get nomHanout => 'Smiyat l7anout';

  @override
  String get gerant => 'Mol l7anout';

  @override
  String get adresse => 'L3onwan';

  @override
  String get ville => 'Lmdina';

  @override
  String get ice => 'ICE';

  @override
  String get iceInvalide => 'ICE fih 15 ra9m';

  @override
  String champObligatoire(String champ) {
    return '$champ darouri';
  }

  @override
  String get positionActuelle =>
      'Sajjel blassa dyali daba ka blassa dyal l7anout';

  @override
  String venteA(String hanout) {
    return 'Bi3a · $hanout';
  }

  @override
  String get panierVide => 'Zid sel3a';

  @override
  String get total => 'Lmajmou3';

  @override
  String get bonLivraison => 'Bon livraison';

  @override
  String get facture => 'Facture';

  @override
  String get avoir => 'Avoir';

  @override
  String get factureIndisponible =>
      'Facture khassha ICE w lma3loumat l9anouniya f profil dyalek.';

  @override
  String get especes => 'Cash';

  @override
  String get cheque => 'Chèque';

  @override
  String get chequeNumero => 'Nmra dyal chèque';

  @override
  String get chequeBanque => 'Banka';

  @override
  String get resteACredit => 'Li b9a crédit';

  @override
  String get toutEspeces => 'Kolchi cash';

  @override
  String get toutCredit => 'Kolchi crédit';

  @override
  String get confirmationHanout => 'Mol l7anout akked tawsil';

  @override
  String get emettreBon => 'Tle3 l bon';

  @override
  String get emettreFacture => 'Tle3 l facture';

  @override
  String get reglementSuperieur => 'Lkhlass kter men lmajmou3';

  @override
  String get prixUnitaire => 'Taman dyal lwa7da';

  @override
  String get remise => 'Takhfid';

  @override
  String get remisePromo => 'Takhfid promo';

  @override
  String get tarifNegocie => 'taman mtaf9in 3lih';

  @override
  String stockInsuffisant(String n) {
    return 'sel3a $n';
  }

  @override
  String get rechercherProduit => 'Sel3a, marque wla code';

  @override
  String get aucunProduit => 'Ma kayna 7ta sel3a';

  @override
  String get regle => 'Mkhalles';

  @override
  String get partagerPdf => 'Partagi PDF (WhatsApp…)';

  @override
  String get smsApresSync =>
      'L7anout 7ta howa kywsslou lien f SMS mli lbi3a tetsynchroniza.';

  @override
  String get docAnnule => 'Mlghi b avoir';

  @override
  String get docPartiellementAnnule => 'Mlghi chwiya b avoir';

  @override
  String get promo => 'promo';

  @override
  String get vendeur => 'Lbya3';

  @override
  String get client => 'Client';

  @override
  String get produit => 'Sel3a';

  @override
  String get produitEnPhoto => 'Sel3a f tswira';

  @override
  String get quantite => 'Lkemmiya';

  @override
  String get montant => 'Lmablagh';

  @override
  String get statut => 'L7ala';

  @override
  String get scannerBonSortie => 'Scanni bon dyal lkhrouj';

  @override
  String get chargementManuel => 'Charji b lyedd';

  @override
  String get mouvements => 'L7arakat';

  @override
  String bonACharger(String numero) {
    return 'Bon $numero khasso ytcharja';
  }

  @override
  String get charger => 'Charji';

  @override
  String get chargerCeBon => 'Tcharji had l bon f sel3a dyalek?';

  @override
  String get stockCharge => 'Sel3a tcharjat';

  @override
  String get stockVide => 'Ma 3endek sel3a: scanni bon dyal lkhrouj.';

  @override
  String get ajouterProduits => 'Zid sel3a li charjiti';

  @override
  String get mvtChargement => 'Chargement';

  @override
  String get mvtVente => 'Bi3a';

  @override
  String get mvtRetour => 'Retour';

  @override
  String get mvtAjustement => 'Tas7i7';

  @override
  String get mvtPromo => 'Promo';

  @override
  String get totalCreances => 'Majmou3 lcrédit';

  @override
  String get aucuneCreance => 'Ma kayn 7ta crédit';

  @override
  String get plafond => 'L7ed';

  @override
  String get delaiJours => 'Akbar modda (iyam)';

  @override
  String get avertissementCredit =>
      'Had l7anout fat l7ed wla lmodda dyal lcrédit. Tbi3 lih b lcrédit wakha?';

  @override
  String get politiqueCredit => 'Fwatan lcrédit';

  @override
  String get politiqueCreditExplication =>
      'Mli chi 7anout yfout l7ed wla lmodda';

  @override
  String get politiqueAvertir => 'Nebbehni w ana n9errer';

  @override
  String get politiqueBloquer => 'Bloqui lbi3 b lcrédit';

  @override
  String get mesReservations => 'Réservations dyali';

  @override
  String get aucunePromo => 'Ma kayn 7ta promo daba';

  @override
  String get aucuneReservation => 'Ma kayn 7ta réservation';

  @override
  String jusquAu(String date) {
    return '7ta l $date';
  }

  @override
  String remisePourcent(String pourcentage) {
    return '-$pourcentage %';
  }

  @override
  String remisePrixNet(String prix) {
    return 'Taman safi $prix';
  }

  @override
  String remiseOfferte(String achetee, String offerte) {
    return 'Chri $achetee w khoud $offerte fabor';
  }

  @override
  String remisePalier(String paliers) {
    return 'Drouj: $paliers';
  }

  @override
  String prixConseille(String prix) {
    return 'Taman lmansou7 bih: $prix (ghir l lma3louma)';
  }

  @override
  String quantiteRestante(String n) {
    return '$n mojoudin';
  }

  @override
  String plafondAmbulant(String max, String deja) {
    return 'Maximum $max l kol bya3, m7jouz: $deja';
  }

  @override
  String delaiRetrait(String heures) {
    return 'Khassek tjibha f $heures sa3a';
  }

  @override
  String get plafondAtteint => 'Wsselti l maximum, wla promo salat.';

  @override
  String get reserver => 'Réservi';

  @override
  String get reservationEnLigne => 'Réservation khassha internet.';

  @override
  String get reservation => 'Réservation';

  @override
  String get statutReservee => 'M7jouza';

  @override
  String get statutRetiree => 'Tjabt';

  @override
  String get statutExpiree => 'Salat';

  @override
  String get statutRevendue => 'Tba3t';

  @override
  String aRetirerAvant(String date) {
    return 'Jibha 9bel $date';
  }

  @override
  String get disponible => 'Mojoud l dépannage';

  @override
  String get indisponible => 'Machi mojoud';

  @override
  String get disponibleExplication =>
      'Ch3elo f tournée bach ywsslouk l7wanet l9rab li khasshom sel3a. Blassa dyalek ma katmchi ghir mli ykoun mch3oul.';

  @override
  String get aucuneDemande => 'Ma kayn 7ta talab daba';

  @override
  String get activerPourRecevoir => 'Ch3el « mojoud » bach ywsslouk talabat';

  @override
  String get demandePriseParAutre => 'Chi bya3 akher khda had talab';

  @override
  String derniereVente(String date) {
    return 'akher bi3a $date';
  }

  @override
  String repondreAvant(String date) {
    return 'Jaweb 9bel $date';
  }

  @override
  String get enRoute => 'Rani jay';

  @override
  String get livrer => 'Wessel w khalles';

  @override
  String get statutEnAttente => 'Katsenna';

  @override
  String get statutAcceptee => 'Ma9boula';

  @override
  String get statutEnRoute => 'F tri9';

  @override
  String get statutLivree => 'Twsslat';

  @override
  String get statutSansReponse => 'Bla jawab';

  @override
  String get horsLigne => 'Bla internet: dakchi li derti m7fouz f tilifoun';

  @override
  String get enLigne => 'Kayn internet';

  @override
  String get jamaisSynchronise => '3omrek ma derti synchro';

  @override
  String derniereSync(String date) {
    return 'Akher synchro: $date';
  }

  @override
  String operationsEnAttente(String n) {
    return '$n 3amaliyat katsenna ttsifet';
  }

  @override
  String get synchroniserMaintenant => 'Dir synchro daba';

  @override
  String get operationsRefusees => '3amaliyat rfedha serveur';

  @override
  String get anomalies => 'Machakil tl9aw';

  @override
  String get appareil => 'Tilifoun';

  @override
  String get versionCndp => 'Nouskha dyal nass CNDP li 9bilti';

  @override
  String get erreurHorsLigne => 'Ma kaynch internet. 3awed mli trja3 réseau.';

  @override
  String get erreurPermissionPosition =>
      '3ti l7e9 l blassa f l\'i3dadat dyal tilifoun.';

  @override
  String get erreurFactureSansMentions =>
      'Ma ymkench facture: ICE w lma3loumat l9anouniya na9sin f profil.';

  @override
  String get erreurCreditBloque =>
      'Lbi3 b lcrédit mbloki: fat l7ed wla lmodda. Khalles cash wla b chèque.';

  @override
  String get erreurRemisePlafond => 'Takhfid fat l7ed lmasmou7';

  @override
  String get erreurQrAutreAmbulant => 'Had l bon dyal bya3 akher';

  @override
  String get erreurQrIllisible => 'QR ma t9rach';

  @override
  String get erreurBonDejaCharge => 'Had l bon tcharja men 9bel';

  @override
  String get erreurConflit =>
      'Ma ymkench: l7ala tbeddlat (sel3a salat, talab tkhda…)';

  @override
  String get erreurPlafondPromo => 'Fetti l maximum dyal had promo';

  @override
  String get erreurTropDEssais => 'Bzaf dyal lmo7awalat. Tsenna chi d9ay9.';

  @override
  String get erreurCodeInvalide => 'L code ghalet';

  @override
  String get erreurCodeExpire => 'L code sala, tleb wa7ed jdid';

  @override
  String get erreurConsentement =>
      'Khass lmowafa9a dyalek 3la lma3loumat chakhsiya';

  @override
  String erreurServeur(String code) {
    return 'Mochkil f serveur ($code)';
  }

  @override
  String get erreurInconnue => 'Mochkil ma mtwa9e3ch';

  @override
  String get opCHARGEMENT => 'Chargement';

  @override
  String get opRETOUR => 'Retour';

  @override
  String get opINVENTAIRE => 'Jard';

  @override
  String get opEMISSION_DOCUMENT => 'Bi3a';

  @override
  String get opAVOIR => 'Avoir';

  @override
  String get opVERSEMENT => 'Khlass';

  @override
  String get opTARIF => 'Taman';

  @override
  String get opHANOUT => '7anout jdid';

  @override
  String get opCREDIT_HANOUT => 'L7ed dyal lcrédit';

  @override
  String get opPARAMETRES_AMBULANT => 'Parametres';

  @override
  String get anoSTOCK_NEGATIF => 'Sel3a ta7t sifr';

  @override
  String get anoECART_SANS_MOTIF => 'Far9 f jard bla sabab';

  @override
  String get anoNUMEROTATION_TROU => 'Nmra na9sa';

  @override
  String get anoNUMERO_EN_DOUBLE => 'Nmra 3awdat';

  @override
  String get anoPLAFOND_CREDIT_DEPASSE => 'Fat l7ed dyal lcrédit';

  @override
  String get anoDELAI_CREDIT_DEPASSE => 'Fatet lmodda dyal lcrédit';

  @override
  String get anoCHARGEMENT_EN_DOUBLE => 'L bon tcharja jouj marrat';

  @override
  String get anoPROMO_SANS_RETRAIT => 'Be3ti promo kter men li jebti';

  @override
  String get anoDEMANDE_NON_ATTRIBUEE => 'Wsselti talab khdah bya3 akher';

  @override
  String get anoREMISE_PLAFOND_DEPASSE => 'Takhfid fat l7ed';

  @override
  String get erreurAutreAmbulant =>
      'Kayn bya3 akher 3ando bi3at ba9i ma tsiftou f had tilifoun. Khasso ydkhel w ydir synchro howa lowel.';

  @override
  String get docRejete => 'Serveur rfdo: chouf synchro';

  @override
  String get tva => 'TVA';

  @override
  String get tvaAssujetti => 'Khade3 l TVA';

  @override
  String get tvaNonAssujetti => 'Machi khade3 l TVA';

  @override
  String get erreurFactureTvaInconnue =>
      'Ma ymkench facture: chi sel3a ma 3andhach nisbat TVA ma3roufa. Tle3 bon livraison.';

  @override
  String get tvaComprise => 'Fiha TVA';

  @override
  String get envoyerLienSms => 'Sifet lien f SMS';

  @override
  String get erreurDocNonSynchronise =>
      'Dir synchro l lbi3a howa lowel bach takhod lien.';

  @override
  String get erreurHanoutSansTelephone =>
      'Had l7anout ma 3andouch nmra tilifoun.';

  @override
  String get erreurAppareilRevoque =>
      'Had tilifoun t9te3 men b3id. 3awed dkhel.';

  @override
  String receptionConforme(String date) {
    return 'Mol l7anout akked tawsil nhar $date';
  }

  @override
  String get receptionEcart => 'Mol l7anout gal kayn far9 f tawsil';

  @override
  String receptionEcartLigne(String produit, String recu, String livre) {
    return '$produit: wslou $recu men $livre';
  }

  @override
  String get contestations => 'Chkayat';

  @override
  String contestationsOuvertes(String n) {
    return '$n chkayat mftou7in';
  }

  @override
  String get aucuneContestation => 'Ma kayna 7ta chkaya';

  @override
  String get clore => 'Sedd';

  @override
  String get commentaire => 'Ta3li9';

  @override
  String get document => 'Lwati9a';

  @override
  String get compteCredit => 'Lcrédit dyal l7sab';

  @override
  String get motifMONTANT => 'Lmablagh';

  @override
  String get motifQUANTITE => 'Lkemmiya';

  @override
  String get motifNON_RECU => 'Ma wselch';

  @override
  String get motifDEJA_PAYE => 'Mkhalles men 9bel';

  @override
  String get motifAUTRE => 'Chi 7aja akhra';

  @override
  String get opRESOLUTION_CONTESTATION => 'Chkaya tseddat';

  @override
  String get anoTVA_INCONNUE => 'Nisbat TVA ma ma3roufach';

  @override
  String get erreurRoleNonAmbulant => 'Had nmra machi dyal bya3 mtjowel.';
}

// ignore: unused_import
import 'package:intl/intl.dart' as intl;

import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Arabic (`ar`).
class TAr extends T {
  TAr([String locale = 'ar']) : super(locale);

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
      'أدخل رقم هاتفك، وستتوصل برمز عبر رسالة قصيرة.';

  @override
  String get reconnexionExplication =>
      'انتهت صلاحية جلستك. أعد الاتصال: مبيعاتك المسجلة تبقى على الهاتف.';

  @override
  String get telephone => 'الهاتف';

  @override
  String get telephoneInvalide => 'رقم الهاتف غير صالح';

  @override
  String get codeSms => 'الرمز المتوصل به عبر الرسالة';

  @override
  String get changerNumero => 'تغيير الرقم';

  @override
  String get recevoirCode => 'استلام الرمز';

  @override
  String get seConnecter => 'تسجيل الدخول';

  @override
  String get consentementTitre => 'معطياتك الشخصية';

  @override
  String get consentementTexte =>
      'يسجل دابا مبيعاتك ومخزونك وديون زبنائك لخدمتك (القانون 09-08، تصريح اللجنة الوطنية لمراقبة حماية المعطيات). لا تُرسل أي معطيات اسمية إلى العلامات التجارية. لا يُجمع موقعك إلا بموافقتك، وفقط عندما تكون «متاحاً» للإغاثة. يمكنك سحب موافقتك وطلب معطياتك في أي وقت.';

  @override
  String get consentementDonnees => 'أوافق على معالجة معطياتي';

  @override
  String get consentementGeo => 'أوافق على تحديد موقعي عندما أكون متاحاً';

  @override
  String get consentementGeoDemande =>
      'لاستقبال طلبات الإغاثة، يحتاج دابا إلى معرفة موقعك فقط عندما تكون متاحاً. هل توافق؟';

  @override
  String get accepter => 'قبول';

  @override
  String get refuser => 'رفض';

  @override
  String get annuler => 'إلغاء';

  @override
  String get confirmer => 'تأكيد';

  @override
  String get valider => 'تأكيد';

  @override
  String get enregistrer => 'حفظ';

  @override
  String get voir => 'عرض';

  @override
  String get pinCreer => 'اختر رمزاً سرياً من 4 أرقام';

  @override
  String get pinConfirmer => 'أكد الرمز السري';

  @override
  String get pinSaisir => 'أدخل رمزك السري';

  @override
  String get pinFaux => 'الرمز السري غير صحيح';

  @override
  String get pinDifferent => 'الرمزان مختلفان';

  @override
  String get pinOublie => 'نسيت الرمز؟ أعد الاتصال عبر رسالة قصيرة';

  @override
  String get ongletHanouts => 'البقالات';

  @override
  String get ongletStock => 'المخزون';

  @override
  String get ongletKarni => 'الكرني';

  @override
  String get ongletPromos => 'العروض';

  @override
  String get ongletDepannage => 'الإغاثة';

  @override
  String get synchronisation => 'المزامنة';

  @override
  String get parametres => 'الإعدادات';

  @override
  String get deconnexion => 'تسجيل الخروج';

  @override
  String get deconnexionConfirmer => 'تسجيل الخروج من هذا الهاتف؟';

  @override
  String deconnexionAvecAttente(String n) {
    return '$n عمليات لم تُرسل بعد. ستبقى على الهاتف وتُرسل عند اتصالك القادم. تسجيل الخروج؟';
  }

  @override
  String get reconnexionRequise =>
      'انتهت الجلسة: أعد الاتصال لإرسال مبيعاتك. يمكنك مواصلة البيع.';

  @override
  String alerteDemande(String produit, String quantite) {
    return 'نفاد قريب منك: $produit × $quantite';
  }

  @override
  String get nouveauHanout => 'بقالة جديدة';

  @override
  String get rechercherHanout => 'الاسم أو المسير أو الهاتف';

  @override
  String get liste => 'قائمة';

  @override
  String get carte => 'خريطة';

  @override
  String get aucunHanout => 'لا توجد بقالات. قم بالمزامنة أو أنشئ واحدة.';

  @override
  String get vendre => 'بيع';

  @override
  String get solde => 'الرصيد';

  @override
  String soldeActuel(String montant) {
    return 'الرصيد الحالي: $montant';
  }

  @override
  String plafondValeur(String montant) {
    return 'السقف $montant';
  }

  @override
  String delaiValeur(String jours) {
    return 'المهلة $jours يوم';
  }

  @override
  String get derniereCommande => 'آخر طلبية';

  @override
  String get versement => 'دفعة';

  @override
  String get plafondCredit => 'سقف الدين';

  @override
  String get historique => 'السجل';

  @override
  String get aucunMouvement => 'لا شيء حالياً';

  @override
  String get nomHanout => 'اسم البقالة';

  @override
  String get gerant => 'المسير';

  @override
  String get adresse => 'العنوان';

  @override
  String get ville => 'المدينة';

  @override
  String get ice => 'ICE';

  @override
  String get iceInvalide => 'يتكون ICE من 15 رقماً';

  @override
  String champObligatoire(String champ) {
    return '$champ إلزامي';
  }

  @override
  String get positionActuelle => 'تسجيل موقعي الحالي كموقع للبقالة';

  @override
  String venteA(String hanout) {
    return 'بيع · $hanout';
  }

  @override
  String get panierVide => 'أضف منتجات';

  @override
  String get total => 'المجموع';

  @override
  String get bonLivraison => 'وصل التسليم';

  @override
  String get facture => 'فاتورة';

  @override
  String get avoir => 'إشعار دائن';

  @override
  String get factureIndisponible =>
      'تتطلب الفاتورة رقم ICE والبيانات القانونية في ملفك.';

  @override
  String get especes => 'نقداً';

  @override
  String get cheque => 'شيك';

  @override
  String get chequeNumero => 'رقم الشيك';

  @override
  String get chequeBanque => 'البنك';

  @override
  String get resteACredit => 'المتبقي ديناً';

  @override
  String get toutEspeces => 'الكل نقداً';

  @override
  String get toutCredit => 'الكل ديناً';

  @override
  String get confirmationHanout => 'أكدت البقالة الاستلام';

  @override
  String get emettreBon => 'إصدار وصل التسليم';

  @override
  String get emettreFacture => 'إصدار الفاتورة';

  @override
  String get reglementSuperieur => 'المبلغ المدفوع يتجاوز المجموع';

  @override
  String get prixUnitaire => 'ثمن الوحدة';

  @override
  String get remise => 'تخفيض';

  @override
  String get remisePromo => 'تخفيض العرض';

  @override
  String get tarifNegocie => 'سعر متفاوض عليه';

  @override
  String stockInsuffisant(String n) {
    return 'المخزون $n';
  }

  @override
  String get rechercherProduit => 'منتج أو علامة أو رمز شريطي';

  @override
  String get aucunProduit => 'لا توجد منتجات';

  @override
  String get regle => 'مدفوع';

  @override
  String get partagerPdf => 'مشاركة PDF (واتساب…)';

  @override
  String get smsApresSync =>
      'تتوصل البقالة أيضاً برابط عبر رسالة قصيرة بعد مزامنة البيع.';

  @override
  String get docAnnule => 'ملغى بإشعار دائن';

  @override
  String get docPartiellementAnnule => 'ملغى جزئياً بإشعار دائن';

  @override
  String get promo => 'عرض';

  @override
  String get vendeur => 'البائع';

  @override
  String get client => 'الزبون';

  @override
  String get produit => 'المنتج';

  @override
  String get produitEnPhoto => 'منتج في صورة';

  @override
  String get quantite => 'الكمية';

  @override
  String get montant => 'المبلغ';

  @override
  String get statut => 'الحالة';

  @override
  String get scannerBonSortie => 'مسح وصل الخروج';

  @override
  String get chargementManuel => 'شحن يدوي';

  @override
  String get mouvements => 'الحركات';

  @override
  String bonACharger(String numero) {
    return 'وصل $numero للشحن';
  }

  @override
  String get charger => 'شحن';

  @override
  String get chargerCeBon => 'شحن هذا الوصل في مخزونك؟';

  @override
  String get stockCharge => 'تم شحن المخزون';

  @override
  String get stockVide => 'المخزون فارغ: امسح وصل خروج.';

  @override
  String get ajouterProduits => 'أضف المنتجات المشحونة';

  @override
  String get mvtChargement => 'شحن';

  @override
  String get mvtVente => 'بيع';

  @override
  String get mvtRetour => 'إرجاع';

  @override
  String get mvtAjustement => 'تسوية';

  @override
  String get mvtPromo => 'عرض';

  @override
  String get totalCreances => 'مجموع الديون';

  @override
  String get aucuneCreance => 'لا توجد ديون جارية';

  @override
  String get plafond => 'السقف';

  @override
  String get delaiJours => 'المهلة القصوى (أيام)';

  @override
  String get avertissementCredit =>
      'تجاوزت هذه البقالة سقف أو مهلة الدين. البيع بالدين رغم ذلك؟';

  @override
  String get politiqueCredit => 'تجاوز الدين';

  @override
  String get politiqueCreditExplication => 'عندما تتجاوز بقالة سقفها أو مهلتها';

  @override
  String get politiqueAvertir => 'تنبيهي وأنا أقرر';

  @override
  String get politiqueBloquer => 'منع البيع بالدين';

  @override
  String get mesReservations => 'حجوزاتي';

  @override
  String get aucunePromo => 'لا توجد عروض حالياً';

  @override
  String get aucuneReservation => 'لا توجد حجوزات';

  @override
  String jusquAu(String date) {
    return 'حتى $date';
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
    return '$achetee مشتراة + $offerte مجاناً';
  }

  @override
  String remisePalier(String paliers) {
    return 'شرائح: $paliers';
  }

  @override
  String prixConseille(String prix) {
    return 'ثمن البيع المقترح: $prix (إرشادي)';
  }

  @override
  String quantiteRestante(String n) {
    return '$n متوفرة';
  }

  @override
  String plafondAmbulant(String max, String deja) {
    return 'الحد الأقصى $max لكل بائع، محجوز: $deja';
  }

  @override
  String delaiRetrait(String heures) {
    return 'يُسحب خلال $heures ساعة';
  }

  @override
  String get plafondAtteint => 'بلغت الحد المسموح به، أو نفد العرض.';

  @override
  String get reserver => 'حجز';

  @override
  String get reservationEnLigne => 'يتطلب الحجز اتصالاً بالإنترنت.';

  @override
  String get reservation => 'حجز';

  @override
  String get statutReservee => 'محجوزة';

  @override
  String get statutRetiree => 'مسحوبة';

  @override
  String get statutExpiree => 'منتهية';

  @override
  String get statutRevendue => 'مباعة';

  @override
  String aRetirerAvant(String date) {
    return 'يجب سحبها قبل $date';
  }

  @override
  String get disponible => 'متاح للإغاثة';

  @override
  String get indisponible => 'غير متاح';

  @override
  String get disponibleExplication =>
      'فعّله خلال الجولة لاستقبال حالات نفاد البقالات القريبة. لا يُرسل موقعك إلا أثناء التفعيل.';

  @override
  String get aucuneDemande => 'لا توجد طلبات حالياً';

  @override
  String get activerPourRecevoir => 'فعّل الإتاحة لاستقبال الطلبات';

  @override
  String get demandePriseParAutre => 'أخذ بائع آخر هذا الطلب';

  @override
  String derniereVente(String date) {
    return 'آخر بيع $date';
  }

  @override
  String repondreAvant(String date) {
    return 'الرد قبل $date';
  }

  @override
  String get enRoute => 'أنا في الطريق';

  @override
  String get livrer => 'تسليم وتحصيل';

  @override
  String get statutEnAttente => 'في الانتظار';

  @override
  String get statutAcceptee => 'مقبولة';

  @override
  String get statutEnRoute => 'في الطريق';

  @override
  String get statutLivree => 'مسلمة';

  @override
  String get statutSansReponse => 'بدون رد';

  @override
  String get horsLigne => 'غير متصل: عملياتك محفوظة على الهاتف';

  @override
  String get enLigne => 'متصل';

  @override
  String get jamaisSynchronise => 'لم تتم المزامنة بعد';

  @override
  String derniereSync(String date) {
    return 'آخر مزامنة: $date';
  }

  @override
  String operationsEnAttente(String n) {
    return '$n عمليات في انتظار الإرسال';
  }

  @override
  String get synchroniserMaintenant => 'زامن الآن';

  @override
  String get operationsRefusees => 'عمليات رفضها الخادم';

  @override
  String get anomalies => 'مخالفات مرصودة';

  @override
  String get appareil => 'الجهاز';

  @override
  String get versionCndp => 'نسخة نص اللجنة الوطنية المقبول';

  @override
  String get erreurHorsLigne =>
      'لا يوجد اتصال بالإنترنت. أعد المحاولة عند عودة الشبكة.';

  @override
  String get erreurPermissionPosition =>
      'اسمح بالوصول إلى الموقع في إعدادات الهاتف.';

  @override
  String get erreurFactureSansMentions =>
      'لا يمكن إصدار الفاتورة: ICE والبيانات القانونية غير موجودة في ملفك.';

  @override
  String get erreurCreditBloque =>
      'البيع بالدين ممنوع: تم تجاوز السقف أو المهلة. حصّل نقداً أو بشيك.';

  @override
  String get erreurRemisePlafond => 'التخفيض يتجاوز السقف المسموح به';

  @override
  String get erreurQrAutreAmbulant => 'وصل الخروج هذا موجه لبائع آخر';

  @override
  String get erreurQrIllisible => 'رمز QR غير مقروء';

  @override
  String get erreurBonDejaCharge => 'تم شحن هذا الوصل من قبل';

  @override
  String get erreurConflit =>
      'إجراء غير ممكن: تغير الوضع (نفدت الكمية، أُخذ الطلب…)';

  @override
  String get erreurPlafondPromo => 'تم تجاوز الحد الأقصى لكل بائع في هذا العرض';

  @override
  String get erreurTropDEssais => 'محاولات كثيرة. انتظر بضع دقائق.';

  @override
  String get erreurCodeInvalide => 'الرمز غير صحيح';

  @override
  String get erreurCodeExpire => 'انتهت صلاحية الرمز، اطلب رمزاً جديداً';

  @override
  String get erreurConsentement => 'موافقتك على المعطيات الشخصية ضرورية';

  @override
  String erreurServeur(String code) {
    return 'خطأ في الخادم ($code)';
  }

  @override
  String get erreurInconnue => 'خطأ غير متوقع';

  @override
  String get opCHARGEMENT => 'شحن';

  @override
  String get opRETOUR => 'إرجاع';

  @override
  String get opINVENTAIRE => 'جرد';

  @override
  String get opEMISSION_DOCUMENT => 'بيع';

  @override
  String get opAVOIR => 'إشعار دائن';

  @override
  String get opVERSEMENT => 'دفعة';

  @override
  String get opTARIF => 'سعر';

  @override
  String get opHANOUT => 'بقالة جديدة';

  @override
  String get opCREDIT_HANOUT => 'سقف الدين';

  @override
  String get opPARAMETRES_AMBULANT => 'الإعدادات';

  @override
  String get anoSTOCK_NEGATIF => 'مخزون سالب';

  @override
  String get anoECART_SANS_MOTIF => 'فرق في الجرد بدون سبب';

  @override
  String get anoNUMEROTATION_TROU => 'ثغرة في الترقيم';

  @override
  String get anoNUMERO_EN_DOUBLE => 'رقم مكرر';

  @override
  String get anoPLAFOND_CREDIT_DEPASSE => 'تجاوز سقف الدين';

  @override
  String get anoDELAI_CREDIT_DEPASSE => 'تجاوز مهلة الدين';

  @override
  String get anoCHARGEMENT_EN_DOUBLE => 'وصل خروج مشحون مرتين';

  @override
  String get anoPROMO_SANS_RETRAIT => 'بيع عرض يتجاوز الكمية المسحوبة';

  @override
  String get anoDEMANDE_NON_ATTRIBUEE => 'تسليم طلب أخذه بائع آخر';

  @override
  String get anoREMISE_PLAFOND_DEPASSE => 'تخفيض يتجاوز السقف';

  @override
  String get erreurAutreAmbulant =>
      'لدى بائع آخر مبيعات لم تُرسل بعد على هذا الهاتف. يجب أن يعيد الاتصال ويزامن أولاً.';

  @override
  String get docRejete => 'رفضه الخادم: انظر المزامنة';

  @override
  String get tva => 'الضريبة على القيمة المضافة';

  @override
  String get tvaAssujetti => 'خاضع للضريبة على القيمة المضافة';

  @override
  String get tvaNonAssujetti => 'غير خاضع للضريبة على القيمة المضافة';

  @override
  String get erreurFactureTvaInconnue =>
      'لا يمكن إصدار الفاتورة: منتج في السلة بدون نسبة ضريبة معروفة. أصدر وصل تسليم.';

  @override
  String get tvaComprise => 'منها الضريبة على القيمة المضافة';

  @override
  String get envoyerLienSms => 'إرسال الرابط عبر رسالة قصيرة';

  @override
  String get erreurDocNonSynchronise => 'زامن البيع أولاً للحصول على رابطه.';

  @override
  String get erreurHanoutSansTelephone => 'هذه البقالة ليس لديها رقم هاتف.';

  @override
  String get erreurAppareilRevoque => 'تم فصل هذا الهاتف عن بعد. أعد الاتصال.';

  @override
  String receptionConforme(String date) {
    return 'أكدت البقالة الاستلام في $date';
  }

  @override
  String get receptionEcart => 'أبلغت البقالة عن فرق عند الاستلام';

  @override
  String receptionEcartLigne(String produit, String recu, String livre) {
    return '$produit: استلم $recu من $livre';
  }

  @override
  String get contestations => 'الاعتراضات';

  @override
  String contestationsOuvertes(String n) {
    return '$n اعتراضات مفتوحة';
  }

  @override
  String get aucuneContestation => 'لا توجد اعتراضات مفتوحة';

  @override
  String get clore => 'إغلاق';

  @override
  String get commentaire => 'تعليق';

  @override
  String get document => 'وثيقة';

  @override
  String get compteCredit => 'رصيد الحساب';

  @override
  String get motifMONTANT => 'المبلغ';

  @override
  String get motifQUANTITE => 'الكمية';

  @override
  String get motifNON_RECU => 'لم يُستلم';

  @override
  String get motifDEJA_PAYE => 'مدفوع مسبقاً';

  @override
  String get motifAUTRE => 'أخرى';

  @override
  String get opRESOLUTION_CONTESTATION => 'اعتراض مغلق';

  @override
  String get anoTVA_INCONNUE => 'نسبة الضريبة غير معروفة';

  @override
  String get erreurRoleNonAmbulant => 'هذا الرقم ليس حساب بائع متجول.';

  @override
  String get rupture => 'نفاد';

  @override
  String get plafondDepasse => 'تجاوز السقف';

  @override
  String get delaiDepasse => 'تجاوز المهلة';
}

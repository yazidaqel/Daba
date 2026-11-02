package ma.daba.ventes;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.stereotype.Component;

import com.ibm.icu.text.ArabicShaping;
import com.ibm.icu.text.ArabicShapingException;
import com.ibm.icu.text.Bidi;

/**
 * PDF d'un bon de livraison, d'une facture ou d'un avoir, en français, en arabe (RTL) ou en darija
 * (caractères arabes ou latins). L'arabe est mis en forme (ICU4J) puis réordonné pour l'affichage.
 */
@Component
class PdfDocuments {

	private static final String POLICE = "/net/sf/jasperreports/fonts/dejavu/DejaVuSans.ttf";
	private static final float MARGE = 50;
	private static final float INTERLIGNE = 16;

	private static final Map<String, Map<String, String>> LIBELLES = Map.of(
			"fr", Map.of("BON_LIVRAISON", "Bon de livraison", "FACTURE", "Facture", "AVOIR", "Avoir", "date", "Date",
					"vendeur", "Vendeur", "client", "Client", "total", "Total", "regle", "Réglé", "credit", "À crédit", "annule", "Annulé par avoir"),
			"ar", Map.of("BON_LIVRAISON", "وصل التسليم", "FACTURE", "فاتورة", "AVOIR", "إشعار دائن", "date", "التاريخ",
					"vendeur", "البائع", "client", "الزبون", "total", "المجموع", "regle", "المدفوع", "credit", "بالكريدي", "annule", "ملغى بإشعار دائن"),
			"ary-Arab", Map.of("BON_LIVRAISON", "بون ديال التسليم", "FACTURE", "فاكتورة", "AVOIR", "أفوار", "date", "التاريخ",
					"vendeur", "البياع", "client", "الكليان", "total", "المجموع", "regle", "تخلص", "credit", "بالكريدي", "annule", "تلغى بأفوار"),
			"ary-Latn", Map.of("BON_LIVRAISON", "Bon dyal livraison", "FACTURE", "Facture", "AVOIR", "Avoir", "date", "Tarikh",
					"vendeur", "Biya3", "client", "Client", "total", "Majmou3", "regle", "Tkhless", "credit", "B lcredit", "annule", "Tlgha b avoir"));

	byte[] generer(Ventes.Document document, String langue) {
		var l = LIBELLES.getOrDefault(langue, LIBELLES.get("fr"));
		var rtl = "ar".equals(langue) || "ary-Arab".equals(langue);
		var date = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("Africa/Casablanca")).format(document.emisLe());
		var lignes = new ArrayList<String>();
		lignes.add(l.get(document.type()) + " " + document.numero());
		lignes.add(l.get("date") + " : " + date);
		lignes.add(l.get("vendeur") + " : " + document.ambulant().nom());
		lignes.add(l.get("client") + " : " + document.hanout().nom());
		if (document.mentionsLegales() != null) {
			var m = document.mentionsLegales();
			lignes.add("ICE " + m.ice() + " · IF " + m.identifiantFiscal() + " · RC " + m.registreCommerce());
		}
		lignes.add("");
		for (var ligne : document.lignes()) {
			var libelle = ligne.libelle().getOrDefault(langue, ligne.libelle().get("fr"));
			var campagne = ligne.campagneNom() == null ? "" : " [" + ligne.campagneNom().getOrDefault(langue, ligne.campagneNom().get("fr")) + "]";
			lignes.add("ARTICLE".equals(ligne.nature())
					? libelle + " — " + ligne.quantite() + " × " + dirhams(ligne.prixUnitaire()) + " = " + dirhams(ligne.montant())
					: libelle + campagne + " : " + dirhams(ligne.montant()));
		}
		lignes.add("");
		lignes.add(l.get("total") + " : " + dirhams(document.total()));
		if (!"AVOIR".equals(document.type())) {
			lignes.add(l.get("regle") + " : " + dirhams(document.montantRegle()));
			lignes.add(l.get("credit") + " : " + dirhams(document.montantCredit()));
		}
		if (!"EMIS".equals(document.statut())) {
			lignes.add(l.get("annule"));
		}
		return ecrire(lignes, rtl);
	}

	private byte[] ecrire(List<String> lignes, boolean rtl) {
		try (var pdf = new PDDocument(); var sortie = new ByteArrayOutputStream()) {
			PDFont police;
			try (var flux = PdfDocuments.class.getResourceAsStream(POLICE)) {
				police = PDType0Font.load(pdf, flux);
			}
			PDPageContentStream contenu = null;
			float y = 0;
			for (int i = 0; i < lignes.size(); i++) {
				if (contenu == null || y < MARGE) {
					if (contenu != null) {
						contenu.close();
					}
					var page = new PDPage(PDRectangle.A4);
					pdf.addPage(page);
					contenu = new PDPageContentStream(pdf, page);
					y = PDRectangle.A4.getHeight() - MARGE;
				}
				var taille = i == 0 ? 16 : 11;
				var texte = affichable(police, visuel(lignes.get(i), rtl));
				var largeur = police.getStringWidth(texte) / 1000 * taille;
				contenu.beginText();
				contenu.setFont(police, taille);
				contenu.newLineAtOffset(rtl ? PDRectangle.A4.getWidth() - MARGE - largeur : MARGE, y);
				contenu.showText(texte);
				contenu.endText();
				y -= i == 0 ? INTERLIGNE * 2 : INTERLIGNE;
			}
			contenu.close();
			pdf.save(sortie);
			return sortie.toByteArray();
		}
		catch (IOException e) {
			throw new IllegalStateException("PDF non généré", e);
		}
	}

	/** Mise en forme des lettres arabes (formes contextuelles) puis ordre d'affichage visuel. */
	static String visuel(String texte, boolean rtl) {
		try {
			var forme = new ArabicShaping(ArabicShaping.LETTERS_SHAPE | ArabicShaping.TEXT_DIRECTION_LOGICAL).shape(texte);
			var bidi = new Bidi(forme, rtl ? Bidi.DIRECTION_DEFAULT_RIGHT_TO_LEFT : Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT);
			return bidi.writeReordered(Bidi.DO_MIRRORING);
		}
		catch (ArabicShapingException e) {
			return texte;
		}
	}

	/** Remplace les caractères absents de la police plutôt que d'échouer. */
	private static String affichable(PDFont police, String texte) {
		var resultat = new StringBuilder();
		texte.codePoints().forEach(c -> {
			var caractere = new String(Character.toChars(c));
			try {
				police.encode(caractere);
				resultat.append(caractere);
			}
			catch (IOException | IllegalArgumentException e) {
				resultat.append('?');
			}
		});
		return resultat.toString();
	}

	static String dirhams(long centimes) {
		return NotificationsVentes.dirhams(centimes) + " DH";
	}

}

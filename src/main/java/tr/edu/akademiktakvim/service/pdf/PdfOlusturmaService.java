package tr.edu.akademiktakvim.service.pdf;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import tr.edu.akademiktakvim.domain.Etkinlik;

/**
 * Filtrelenmis akademik takvimi kurumsal gorunumlu bir PDF belgesine cevirir
 * (is analizi Bolum 5.4).
 *
 * <p><b>Aciklama alani bilerek disarida birakildi.</b> Is analizi: "Uzun
 * aciklamalar PDF'e eklenmeyecektir. Boylece daha kisa ve okunabilir bir cikti
 * elde edilecektir." Aciklamalar degisken uzunlukta oldugu icin tablo satirlarini
 * sisirir ve sayfa duzenini bozar.</p>
 *
 * <p>Birim sutunu KOSULLU eklenir: kullanici tek bir fakulte sectiyse bu bilgi
 * zaten baslikta yazar ve her satirda tekrar etmesi yer israfidir. Karisik
 * sonuclarda ise sutun gereklidir.</p>
 */
@Service
public class PdfOlusturmaService {

    private static final DateTimeFormatter TARIH_BICIMI = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    // Kurumsal gorunum icin sinirli ve tutarli bir renk paleti.
    // Web arayuzuyle ayni bordo (#96131F) ve sicak notr griler kullanilir;
    // basili cikti ile ekran ayni kimligi tasir.
    private static final Color BASLIK_ARKA = new Color(150, 19, 31);
    private static final Color BASLIK_YAZI = Color.WHITE;
    private static final Color SATIR_ALTERNATIF = new Color(250, 244, 244);
    private static final Color CIZGI = new Color(226, 214, 213);
    private static final Color IKINCIL_YAZI = new Color(106, 92, 93);

    /** Baslikta armanin kaplayacagi kare alanin kenari (punto). */
    private static final float ARMA_KENARI = 52f;

    private final PdfFontSaglayici fontSaglayici;
    private final PdfLogoSaglayici logoSaglayici;
    private final String universiteAdi;

    public PdfOlusturmaService(PdfFontSaglayici fontSaglayici,
                               PdfLogoSaglayici logoSaglayici,
                               @Value("${uygulama.universite-adi:Üniversite}") String universiteAdi) {
        this.fontSaglayici = fontSaglayici;
        this.logoSaglayici = logoSaglayici;
        this.universiteAdi = universiteAdi;
    }

    /**
     * Etkinlik listesinden PDF uretir.
     *
     * @param etkinlikler kronolojik sirali etkinlikler (siralamayi cagiran garanti eder)
     * @param filtreOzeti baslikta gosterilecek filtre bilgileri
     * @return PDF dosyasinin ikili icerigi
     */
    public byte[] olustur(List<Etkinlik> etkinlikler, FiltreOzeti filtreOzeti) {
        // A4 dikey; alt kenar bosluk fazla cunku alt bilgi satiri orada duruyor
        Document belge = new Document(PageSize.A4, 40, 40, 40, 55);
        ByteArrayOutputStream cikti = new ByteArrayOutputStream();

        try {
            PdfWriter yazici = PdfWriter.getInstance(belge, cikti);
            yazici.setPageEvent(new PdfSayfaOlaylari(fontSaglayici, universiteAdi));

            belge.open();
            baslikYaz(belge, filtreOzeti);

            if (etkinlikler.isEmpty()) {
                bosSonucYaz(belge);
            } else {
                belge.add(tabloOlustur(etkinlikler, filtreOzeti.birimSutunuGerekli()));
                belge.add(ozetSatiriYaz(etkinlikler.size()));
            }

            belge.close();
            return cikti.toByteArray();

        } catch (DocumentException ex) {
            throw new IllegalStateException("PDF oluşturulurken hata oluştu.", ex);
        }
    }

    // ----------------------------------------------------------------- BASLIK

    private void baslikYaz(Document belge, FiltreOzeti ozet) throws DocumentException {
        belge.add(ustBilgiBlogu());

        // Hangi filtrelerle uretildigi belgede yazili olmali: aksi halde ciktiya
        // bakan kisi eksik bir takvime baktigini fark edemez.
        Paragraph filtreSatiri = new Paragraph(ozet.metinOlarak(), fontSaglayici.soluk(9f, IKINCIL_YAZI));
        filtreSatiri.setAlignment(Element.ALIGN_CENTER);
        filtreSatiri.setSpacingAfter(14f);
        belge.add(filtreSatiri);
    }

    /**
     * Kurum armasi ile kurum adi ve belge basligini iceren ust blogu uretir.
     *
     * <p>Cerceve<b>siz</b> uc sutunlu bir tablo kullanilir: solda arma, ortada
     * metin, sagda armayla ayni genislikte bos bir sutun. Sagdaki bos sutun
     * olmasaydi metin blogu armanin genisligi kadar saga kayar ve sayfada
     * ortalanmis gorunmezdi.</p>
     *
     * <p>Arma yoksa (dosya eksik) sutun bos kalir; duzen bozulmaz.</p>
     */
    private PdfPTable ustBilgiBlogu() {
        PdfPTable ust = new PdfPTable(new float[]{ARMA_KENARI, 400f, ARMA_KENARI});
        ust.setWidthPercentage(100);
        ust.getDefaultCell().setBorder(Rectangle.NO_BORDER);

        ust.addCell(armaHucresi());
        ust.addCell(kurumMetniHucresi());
        ust.addCell(bosHucre());

        return ust;
    }

    private PdfPCell armaHucresi() {
        PdfPCell hucre = new PdfPCell();
        hucre.setBorder(Rectangle.NO_BORDER);
        hucre.setVerticalAlignment(Element.ALIGN_MIDDLE);
        logoSaglayici.arma(ARMA_KENARI).ifPresent(hucre::addElement);
        return hucre;
    }

    private PdfPCell kurumMetniHucresi() {
        Paragraph kurum = new Paragraph(universiteAdi, fontSaglayici.kalin(15f));
        kurum.setAlignment(Element.ALIGN_CENTER);

        Paragraph baslik = new Paragraph("Akademik Takvim", fontSaglayici.kalin(12f));
        baslik.setAlignment(Element.ALIGN_CENTER);

        PdfPCell hucre = new PdfPCell();
        hucre.setBorder(Rectangle.NO_BORDER);
        hucre.setVerticalAlignment(Element.ALIGN_MIDDLE);
        hucre.addElement(kurum);
        hucre.addElement(baslik);
        return hucre;
    }

    private PdfPCell bosHucre() {
        PdfPCell hucre = new PdfPCell();
        hucre.setBorder(Rectangle.NO_BORDER);
        return hucre;
    }

    private void bosSonucYaz(Document belge) throws DocumentException {
        Paragraph bos = new Paragraph(
                "Seçilen filtrelere uygun etkinlik bulunamadı.",
                fontSaglayici.normal(10f));
        bos.setAlignment(Element.ALIGN_CENTER);
        bos.setSpacingBefore(30f);
        belge.add(bos);
    }

    private Paragraph ozetSatiriYaz(int adet) {
        Paragraph ozet = new Paragraph("Toplam " + adet + " etkinlik listelenmiştir.",
                fontSaglayici.soluk(8.5f, IKINCIL_YAZI));
        ozet.setAlignment(Element.ALIGN_RIGHT);
        ozet.setSpacingBefore(8f);
        return ozet;
    }

    // ------------------------------------------------------------------ TABLO

    private PdfPTable tabloOlustur(List<Etkinlik> etkinlikler, boolean birimSutunu)
            throws DocumentException {

        // Sutun genislikleri: etkinlik adi en genis, tarihler sabit dar
        float[] genislikler = birimSutunu
                ? new float[]{34f, 13f, 13f, 9f, 16f, 15f}
                : new float[]{42f, 15f, 15f, 10f, 18f};

        PdfPTable tablo = new PdfPTable(genislikler);
        tablo.setWidthPercentage(100);
        tablo.setSpacingBefore(4f);
        // Ilk satir her yeni sayfada tekrarlansin - cok sayfali takvimde sart
        tablo.setHeaderRows(1);

        basliklariEkle(tablo, birimSutunu);

        int satirNo = 0;
        for (Etkinlik e : etkinlikler) {
            // Zebra desen: uzun tablolarda satir takibini kolaylastirir
            Color arkaPlan = (satirNo % 2 == 0) ? Color.WHITE : SATIR_ALTERNATIF;

            tablo.addCell(govdeHucresi(e.getAd(), Element.ALIGN_LEFT, arkaPlan));
            tablo.addCell(govdeHucresi(bicimlendir(e.getBaslangicTarihi()), Element.ALIGN_CENTER, arkaPlan));
            tablo.addCell(govdeHucresi(bicimlendir(e.getBitisTarihi()), Element.ALIGN_CENTER, arkaPlan));
            tablo.addCell(govdeHucresi(e.getDonem().getEtiket(), Element.ALIGN_CENTER, arkaPlan));
            tablo.addCell(govdeHucresi(e.getKategori().getAd(), Element.ALIGN_LEFT, arkaPlan));
            if (birimSutunu) {
                tablo.addCell(govdeHucresi(e.getBirim().getAd(), Element.ALIGN_LEFT, arkaPlan));
            }
            satirNo++;
        }
        return tablo;
    }

    private void basliklariEkle(PdfPTable tablo, boolean birimSutunu) {
        tablo.addCell(baslikHucresi("Etkinlik Adı"));
        tablo.addCell(baslikHucresi("Başlangıç"));
        tablo.addCell(baslikHucresi("Bitiş"));
        tablo.addCell(baslikHucresi("Dönem"));
        tablo.addCell(baslikHucresi("Kategori"));
        if (birimSutunu) {
            tablo.addCell(baslikHucresi("Birim"));
        }
    }

    private PdfPCell baslikHucresi(String metin) {
        Font font = fontSaglayici.kalin(9f);
        font.setColor(BASLIK_YAZI);

        PdfPCell hucre = new PdfPCell(new Phrase(metin, font));
        hucre.setBackgroundColor(BASLIK_ARKA);
        hucre.setHorizontalAlignment(Element.ALIGN_CENTER);
        hucre.setVerticalAlignment(Element.ALIGN_MIDDLE);
        hucre.setPadding(6f);
        hucre.setBorderColor(BASLIK_ARKA);
        return hucre;
    }

    private PdfPCell govdeHucresi(String metin, int hizalama, Color arkaPlan) {
        PdfPCell hucre = new PdfPCell(new Phrase(metin, fontSaglayici.normal(8.5f)));
        hucre.setBackgroundColor(arkaPlan);
        hucre.setHorizontalAlignment(hizalama);
        hucre.setVerticalAlignment(Element.ALIGN_MIDDLE);
        hucre.setPaddingTop(5f);
        hucre.setPaddingBottom(5f);
        hucre.setPaddingLeft(6f);
        hucre.setPaddingRight(6f);
        // Yalnizca yatay cizgiler: dikey cizgisiz tablo daha sakin okunur
        hucre.setBorder(Rectangle.TOP | Rectangle.BOTTOM);
        hucre.setBorderColor(CIZGI);
        return hucre;
    }

    private String bicimlendir(LocalDate tarih) {
        return tarih.format(TARIH_BICIMI);
    }

    // ------------------------------------------------------------ FILTRE OZETI

    /**
     * PDF basliginda gosterilecek filtre bilgisi.
     *
     * @param birimSutunuGerekli tek bir birim secilmediyse {@code true};
     *                           tabloya Birim sutunu eklenip eklenmeyecegini belirler
     */
    public record FiltreOzeti(String egitimYili, String birim, String kategoriler,
                              String donem, boolean birimSutunuGerekli) {

        /** Baslikta tek satirda gosterilecek okunabilir ozet. */
        public String metinOlarak() {
            StringBuilder sb = new StringBuilder();
            ekle(sb, "Eğitim Yılı", egitimYili);
            ekle(sb, "Birim", birim);
            ekle(sb, "Dönem", donem);
            ekle(sb, "Kategoriler", kategoriler);
            return sb.isEmpty() ? "Tüm etkinlikler" : sb.toString();
        }

        private void ekle(StringBuilder sb, String etiket, String deger) {
            if (deger == null || deger.isBlank()) {
                return;
            }
            if (!sb.isEmpty()) {
                sb.append("   |   ");
            }
            sb.append(etiket).append(": ").append(deger);
        }
    }
}

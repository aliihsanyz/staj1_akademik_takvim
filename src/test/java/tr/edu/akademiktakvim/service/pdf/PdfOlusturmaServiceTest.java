package tr.edu.akademiktakvim.service.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tr.edu.akademiktakvim.TestVeriUretici;
import tr.edu.akademiktakvim.domain.Birim;
import tr.edu.akademiktakvim.domain.EgitimYili;
import tr.edu.akademiktakvim.domain.Etkinlik;
import tr.edu.akademiktakvim.domain.Kategori;

/**
 * PDF uretiminin testleri.
 *
 * <p>Uretilen PDF, PDFBox ile GERI OKUNARAK dogrulanir. Yalnizca "byte
 * dizisi bos degil" demek yetersizdir; asil risk Turkce karakterlerin bozuk
 * basilmasi ve filtre disi kayitlarin ciktiya sizmasidir.</p>
 */
@DisplayName("PdfOlusturmaService")
class PdfOlusturmaServiceTest {

    private PdfOlusturmaService servis;

    private Birim genel;
    private Birim muhendislik;
    private Kategori dersSinav;
    private Kategori tatil;
    private EgitimYili yil;

    @BeforeEach
    void hazirla() {
        PdfFontSaglayici fontSaglayici = new PdfFontSaglayici();
        fontSaglayici.yukle();
        servis = new PdfOlusturmaService(fontSaglayici, "Örnek Üniversitesi");

        genel = TestVeriUretici.genelBirim(1L);
        muhendislik = TestVeriUretici.aktifBirim(2L, "Mühendislik Fakültesi");
        dersSinav = TestVeriUretici.aktifKategori(1L, "Ders ve Sınav Tarihleri");
        tatil = TestVeriUretici.aktifKategori(2L, "Resmî Tatiller");
        yil = TestVeriUretici.aktifEgitimYili(1L);
    }

    private Etkinlik etkinlik(Long id, String ad, Birim birim, Kategori kategori) {
        return TestVeriUretici.etkinlik(id, ad,
                LocalDate.of(2026, 4, 6), LocalDate.of(2026, 4, 17), birim, kategori, yil);
    }

    private PdfOlusturmaService.FiltreOzeti ozet(boolean birimSutunu) {
        return new PdfOlusturmaService.FiltreOzeti(
                "2025-2026", "Mühendislik Fakültesi", "Ders ve Sınav Tarihleri",
                "Bahar", birimSutunu);
    }

    /**
     * Uretilen PDF'in metnini geri okur.
     *
     * <p>Bosluklar tek bicime indirgenir: PDF tablo hucreleri dar sutunlarda
     * satir kaydirir ve "Üniversite Geneli" metni ciktida "Üniversite\nGeneli"
     * olarak gorunur. Normalize edilmezse testler, urunde bir sorun olmadigi
     * halde satir kaydirmasi yuzunden kirilir.</p>
     */
    private String pdfMetni(byte[] pdf) throws IOException {
        try (PDDocument belge = Loader.loadPDF(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(belge).replaceAll("\\s+", " ");
        }
    }

    @Test
    @DisplayName("geçerli bir PDF dosyası üretilir")
    void gecerliPdfUretilir() throws IOException {
        byte[] pdf = servis.olustur(
                List.of(etkinlik(1L, "Ara Sınavlar", muhendislik, dersSinav)), ozet(false));

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).startsWith("%PDF-");

        try (PDDocument belge = Loader.loadPDF(pdf)) {
            assertThat(belge.getNumberOfPages()).isGreaterThanOrEqualTo(1);
        }
    }

    @Test
    @DisplayName("Türkçe karakterler PDF'te DOĞRU basılır")
    void turkceKarakterlerDogruBasilir() throws IOException {
        // Bu testin varlık sebebi: OpenPDF'in varsayılan Helvetica fontu WinAnsi
        // kodlamalıdır ve ş/ğ/İ/ı karakterlerini bozuk basar. Gömülü DejaVu
        // fontu bir gün kaldırılırsa bu test hemen kırılmalıdır.
        String zorAd = "Güz Yarıyılı Şenliği ĞÜŞİÖÇ ğüşıöç";

        byte[] pdf = servis.olustur(
                List.of(etkinlik(1L, zorAd, muhendislik, dersSinav)), ozet(false));

        String metin = pdfMetni(pdf);

        assertThat(metin).contains(zorAd);
        assertThat(metin).doesNotContain("?");
        assertThat(metin).contains("Örnek Üniversitesi");
    }

    @Test
    @DisplayName("PDF yalnızca verilen etkinlikleri içerir")
    void filtreDisiKayitSizmaz() throws IOException {
        // İŞ KURALI: "PDF çıktısı yalnızca kullanıcının seçtiği filtrelere
        // uygun kayıtları içerir."
        byte[] pdf = servis.olustur(List.of(
                etkinlik(1L, "Dahil Edilen Sınav", muhendislik, dersSinav)), ozet(false));

        String metin = pdfMetni(pdf);

        assertThat(metin).contains("Dahil Edilen Sınav");
        assertThat(metin).doesNotContain("Hariç Tutulan Tatil");
    }

    @Test
    @DisplayName("uzun açıklamalar PDF'e DAHİL EDİLMEZ")
    void aciklamaPdfeGirmez() throws IOException {
        // İş analizi Bölüm 5.4: "Uzun açıklamalar PDF'e eklenmeyecektir.
        // Böylece daha kısa ve okunabilir bir çıktı elde edilecektir."
        Etkinlik e = etkinlik(1L, "Ara Sınavlar", muhendislik, dersSinav);
        e.setAciklama("BU ACIKLAMA CIKTIDA GORUNMEMELIDIR");

        String metin = pdfMetni(servis.olustur(List.of(e), ozet(false)));

        assertThat(metin).contains("Ara Sınavlar");
        assertThat(metin).doesNotContain("BU ACIKLAMA CIKTIDA GORUNMEMELIDIR");
    }

    @Test
    @DisplayName("başlıkta hangi filtrelerle üretildiği yazar")
    void filtreOzetiBaslikYazilir() throws IOException {
        String metin = pdfMetni(servis.olustur(
                List.of(etkinlik(1L, "Ara Sınavlar", muhendislik, dersSinav)), ozet(false)));

        // Çıktıya bakan kişi eksik bir takvime baktığını fark edebilmeli
        assertThat(metin).contains("2025-2026");
        assertThat(metin).contains("Mühendislik Fakültesi");
    }

    @Test
    @DisplayName("birim sütunu isteğe göre eklenir")
    void birimSutunuKosulludur() throws IOException {
        List<Etkinlik> etkinlikler = List.of(
                etkinlik(1L, "Ara Sınavlar", muhendislik, dersSinav),
                etkinlik(2L, "Resmî Tatil", genel, tatil));

        String sutunlu = pdfMetni(servis.olustur(etkinlikler, ozet(true)));
        String sutunsuz = pdfMetni(servis.olustur(etkinlikler, ozet(false)));

        // Sütun açıkken her satırın birimi gövdede görünür
        assertThat(sutunlu)
                .contains("Birim")
                .contains("Üniversite Geneli");

        // Sütun kapalıyken birim adı tablo gövdesinde HİÇ geçmez;
        // tek birim seçildiğinde bu bilgi zaten başlıkta yazar.
        assertThat(sutunsuz).doesNotContain("Üniversite Geneli");
    }

    @Test
    @DisplayName("boş sonuçta anlaşılır bir mesaj basılır")
    void bosSonucMesaji() throws IOException {
        String metin = pdfMetni(servis.olustur(List.of(), ozet(false)));

        assertThat(metin).contains("Seçilen filtrelere uygun etkinlik bulunamadı");
    }

    @Test
    @DisplayName("çok sayıda etkinlikte sayfalama çalışır ve başlık tekrarlanır")
    void cokSayfaliCikti() throws IOException {
        List<Etkinlik> cokEtkinlik = new java.util.ArrayList<>();
        for (long i = 1; i <= 80; i++) {
            cokEtkinlik.add(etkinlik(i, "Etkinlik " + i, muhendislik, dersSinav));
        }

        byte[] pdf = servis.olustur(cokEtkinlik, ozet(false));

        try (PDDocument belge = Loader.loadPDF(pdf)) {
            assertThat(belge.getNumberOfPages()).isGreaterThan(1);
        }

        String metin = pdfMetni(pdf);
        assertThat(metin).contains("Etkinlik 1").contains("Etkinlik 80");
        // Tablo başlığı her sayfada yinelenmeli
        assertThat(metin.split("Etkinlik Adı", -1).length - 1).isGreaterThan(1);
        assertThat(metin).contains("Toplam 80 etkinlik listelenmiştir");
    }

    @Test
    @DisplayName("filtre özeti metni okunabilir biçimde birleştirilir")
    void filtreOzetiMetni() {
        assertThat(ozet(false).metinOlarak())
                .contains("Eğitim Yılı: 2025-2026")
                .contains("Birim: Mühendislik Fakültesi")
                .contains("Dönem: Bahar");

        assertThat(new PdfOlusturmaService.FiltreOzeti(null, null, null, null, false)
                .metinOlarak()).isEqualTo("Tüm etkinlikler");
    }
}

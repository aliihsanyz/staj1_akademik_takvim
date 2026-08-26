package tr.edu.akademiktakvim.service.pdf;

import java.io.IOException;
import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import org.openpdf.text.Font;
import org.openpdf.text.pdf.BaseFont;

/**
 * PDF ciktisinda kullanilan Turkce uyumlu fontlari saglar.
 *
 * <p><b>Bu sinif neden var?</b> OpenPDF'in yerlesik fontlari (Helvetica, Times)
 * WinAnsi kodlamasi kullanir ve Turkce'ye ozgu <b>ş, ğ, İ, ı</b> karakterlerini
 * ya bos kutu ya da yanlis harf olarak basar. Bunu asmanin tek yolu, Unicode
 * destekleyen bir TrueType fontu {@code IDENTITY_H} kodlamasiyla ve PDF'e
 * GOMULU olarak kullanmaktir.</p>
 *
 * <p>DejaVu Sans secildi: Bitstream Vera lisansi serbest dagitima ve gomuluye
 * izin verir. Windows'un Arial'i gomulu kullanildiginda lisans sorunu cikarir.</p>
 *
 * <p>Fontlar bir kez yuklenip onbellege alinir; her PDF istegi icin 740 KB'lik
 * dosyayi tekrar okumak gereksiz olurdu.</p>
 */
@Component
public class PdfFontSaglayici {

    private static final Logger log = LoggerFactory.getLogger(PdfFontSaglayici.class);

    private static final String NORMAL_YOL = "fonts/DejaVuSans.ttf";
    private static final String KALIN_YOL = "fonts/DejaVuSans-Bold.ttf";

    private BaseFont normalTaban;
    private BaseFont kalinTaban;

    /**
     * Fontlari uygulama aciliste yukler.
     *
     * <p>Ilk PDF istegini beklemek yerine burada yuklenmesinin sebebi: font
     * dosyasi eksikse bunu ilk kullanicinin PDF indirmeye calistigi anda degil,
     * acilista ogrenmek isteriz.</p>
     */
    @PostConstruct
    public void yukle() {
        this.normalTaban = tabanFontYukle(NORMAL_YOL);
        this.kalinTaban = tabanFontYukle(KALIN_YOL);
        log.info("PDF fontlari yuklendi (Turkce karakter destegi aktif).");
    }

    /** Tablo govdesi ve normal metin. */
    public Font normal(float boyut) {
        return new Font(normalTaban, boyut, Font.NORMAL);
    }

    /** Baslik ve tablo basligi. */
    public Font kalin(float boyut) {
        return new Font(kalinTaban, boyut, Font.NORMAL);
    }

    /** Alt bilgi gibi ikincil metinler icin soluk renkli kucuk font. */
    public Font soluk(float boyut, java.awt.Color renk) {
        Font font = new Font(normalTaban, boyut, Font.NORMAL);
        font.setColor(renk);
        return font;
    }

    private BaseFont tabanFontYukle(String kaynakYolu) {
        try (InputStream akis = new ClassPathResource(kaynakYolu).getInputStream()) {
            byte[] fontVerisi = akis.readAllBytes();
            // IDENTITY_H: yatay Unicode kodlamasi - Turkce karakterlerin sarti.
            // EMBEDDED:   font PDF'e gomulur, boylece belge her bilgisayarda ayni gorunur.
            return BaseFont.createFont(kaynakYolu, BaseFont.IDENTITY_H, BaseFont.EMBEDDED,
                    BaseFont.CACHED, fontVerisi, null);
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "PDF fontu yuklenemedi: " + kaynakYolu
                            + ". src/main/resources/fonts/ altinda bulunmalidir.", ex);
        }
    }
}

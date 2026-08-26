package tr.edu.akademiktakvim.service.pdf;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

/**
 * Yuklenen PDF'ten satir satir metin cikarir.
 *
 * <p><b>{@code setSortByPosition(true)} neden sart?</b> Akademik takvimler tablo
 * halindedir. Varsayilan ayarla PDFBox, metni icerik akisindaki siraya gore
 * dizer; bu sira gorsel duzenle ayni olmak zorunda degildir. Sonucta
 * "Ara Sınavlar" ile "10-21 Kasım 2025" farkli satirlara dusebilir ve aralarindaki
 * iliski kaybolur. Konuma gore siralama, ayni gorsel satirdaki hucreleri yan yana
 * getirir.</p>
 *
 * <p><b>Not - denenip vazgecilen yaklasim:</b> Baslangicta, metin parcalarinin X
 * koordinatlarina bakip genis bosluklara ayirici ekleyen ozel bir cikarici
 * yazilmisti. Olcumde, PDFBox'in kendi font olcusune dayali kelime ayirma mantigi
 * ile BIREBIR AYNI ciktiyi urettigi gorulunce kaldirildi: ek deger uretmeyen
 * ama hata yapabilecek 40 satirlik karmasikligi tasimanin anlami yoktu.</p>
 */
@Component
public class PdfMetinCikarici {

    /**
     * Bir satir bu kadar cok kez birebir tekrarliyorsa ust/alt bilgidir.
     * Gercek bir etkinlik adinin belge boyunca 3 kez birebir tekrar etmesi beklenmez.
     */
    private static final int TEKRAR_ESIGI = 3;

    /**
     * PDF'i satirlara ayirir.
     *
     * @param akis PDF ikili icerigi
     * @return temizlenmis, bos olmayan satirlar
     */
    public List<String> satirlariCikar(InputStream akis) throws IOException {
        try (PDDocument belge = Loader.loadPDF(new RandomAccessReadBuffer(akis))) {

            if (belge.isEncrypted()) {
                throw new IOException("Şifreli PDF dosyaları okunamaz.");
            }

            PDFTextStripper cikarici = new PDFTextStripper();
            cikarici.setSortByPosition(true);
            cikarici.setStartPage(1);
            cikarici.setEndPage(belge.getNumberOfPages());

            return satirlariTemizle(cikarici.getText(belge));
        }
    }

    /**
     * Ham metni kullanilabilir satirlara cevirir: bosluk normalizasyonu,
     * bos ve anlamsiz satirlarin atilmasi, her sayfada yinelenen ust/alt
     * bilgilerin elenmesi.
     */
    private List<String> satirlariTemizle(String hamMetin) {
        String[] hamSatirlar = hamMetin.split("\\R");

        // Once tekrar sayilir: her sayfada yinelenen satirlar ust/alt bilgidir.
        Map<String, Integer> tekrarSayaci = new HashMap<>();
        for (String satir : hamSatirlar) {
            String t = normalize(satir);
            if (!t.isBlank()) {
                tekrarSayaci.merge(t, 1, Integer::sum);
            }
        }

        List<String> sonuc = new ArrayList<>();
        for (String satir : hamSatirlar) {
            String temiz = normalize(satir);

            if (temiz.isBlank() || anlamsizMi(temiz)) {
                continue;
            }
            if (tekrarSayaci.getOrDefault(temiz, 0) >= TEKRAR_ESIGI) {
                continue;
            }
            sonuc.add(temiz);
        }
        return sonuc;
    }

    /**
     * Satiri karsilastirilabilir hale getirir.
     *
     * <p>Cizgi turleri tek bicime indirgenir; aksi halde tarih ayristiricinin
     * aralik ayirici deseni "–" (en cizgisi) kullanan belgelerde caliskmaz.</p>
     */
    private String normalize(String satir) {
        return satir
                .replace(' ', ' ')   // kirilmayan bosluk (PDF'lerde cok yaygin)
                .replace('–', '-')   // en cizgisi
                .replace('—', '-')   // em cizgisi
                .replace('﻿', ' ')   // bayt sirasi isareti
                .replaceAll("\\s{2,}", " ")
                .trim();
    }

    /** Tek basina sayfa numarasi, nokta dizisi gibi anlamsiz satirlar. */
    private boolean anlamsizMi(String satir) {
        if (satir.length() < 2) {
            return true;
        }
        if (satir.matches("^\\d{1,3}$")) {          // yalnizca sayfa numarasi
            return true;
        }
        return satir.matches("^[.\\-_=\\s]+$");     // yalnizca noktalama
    }
}

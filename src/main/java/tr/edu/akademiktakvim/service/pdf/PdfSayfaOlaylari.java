package tr.edu.akademiktakvim.service.pdf;

import java.awt.Color;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.ColumnText;
import org.openpdf.text.pdf.PdfPageEventHelper;
import org.openpdf.text.pdf.PdfWriter;

/**
 * Her sayfanin altina "Sayfa X" bilgisi ve olusturma zaman damgasi yazar.
 *
 * <p>Toplam sayfa sayisi ({@code X / Y} bicimi) bilerek kullanilmadi: OpenPDF'te
 * toplami yazabilmek icin belgeyi iki kez islemek ya da sablon (PdfTemplate)
 * kullanmak gerekir. Basili takvimde tek basina sayfa numarasi yeterlidir ve
 * kod bu sekilde cok daha yalin kalir.</p>
 */
class PdfSayfaOlaylari extends PdfPageEventHelper {

    private static final DateTimeFormatter ZAMAN_BICIMI =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final PdfFontSaglayici fontSaglayici;
    private final String universiteAdi;
    private final LocalDateTime olusturmaZamani;

    PdfSayfaOlaylari(PdfFontSaglayici fontSaglayici, String universiteAdi) {
        this.fontSaglayici = fontSaglayici;
        this.universiteAdi = universiteAdi;
        this.olusturmaZamani = LocalDateTime.now();
    }

    @Override
    public void onEndPage(PdfWriter yazici, Document belge) {
        Font altFont = fontSaglayici.soluk(8f, new Color(120, 120, 120));

        float altY = belge.bottom() - 18;

        // Sol alt: kaynak bilgisi
        ColumnText.showTextAligned(yazici.getDirectContent(), Element.ALIGN_LEFT,
                new Phrase(universiteAdi + " - Akademik Takvim", altFont),
                belge.left(), altY, 0);

        // Orta alt: sayfa numarasi
        ColumnText.showTextAligned(yazici.getDirectContent(), Element.ALIGN_CENTER,
                new Phrase("Sayfa " + yazici.getPageNumber(), altFont),
                (belge.left() + belge.right()) / 2, altY, 0);

        // Sag alt: belgenin ne zaman uretildigi.
        // Dinamik bir sistemden alindigi icin, ciktinin ne zamanki veriyi
        // yansittigi okuyucu acisindan onemlidir.
        ColumnText.showTextAligned(yazici.getDirectContent(), Element.ALIGN_RIGHT,
                new Phrase("Oluşturulma: " + olusturmaZamani.format(ZAMAN_BICIMI), altFont),
                belge.right(), altY, 0);
    }
}

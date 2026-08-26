package tr.edu.akademiktakvim.domain.enums;

import java.time.LocalDate;
import java.time.Month;

/**
 * Akademik donem. Is analizi Bolum 5.2: her etkinligin yaninda
 * Guz / Bahar / Yaz etiketi gosterilir.
 */
public enum Donem {

    GUZ("Güz"),
    BAHAR("Bahar"),
    YAZ("Yaz");

    private final String etiket;

    Donem(String etiket) {
        this.etiket = etiket;
    }

    public String getEtiket() {
        return etiket;
    }

    /**
     * Tarihten donem tahmini yapar. PDF ice aktarma sirasinda, kaynak belgede
     * donem bilgisi acikca yazmadiginda basvurulan varsayilan kuraldir.
     *
     * <p>Eylul-Ocak arasi Guz, Subat-Haziran arasi Bahar, Temmuz-Agustos Yaz
     * kabul edilir. Tahmin kesin degildir; kullanici onay ekraninda duzeltebilir.</p>
     */
    public static Donem tarihtenTahminEt(LocalDate tarih) {
        Month ay = tarih.getMonth();
        return switch (ay) {
            case SEPTEMBER, OCTOBER, NOVEMBER, DECEMBER, JANUARY -> GUZ;
            case FEBRUARY, MARCH, APRIL, MAY, JUNE -> BAHAR;
            case JULY, AUGUST -> YAZ;
        };
    }
}

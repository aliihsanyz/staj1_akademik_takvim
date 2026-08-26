package tr.edu.akademiktakvim.service.pdf;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Component;

import tr.edu.akademiktakvim.domain.Kategori;

/**
 * Etkinlik adindan hangi kategoriye ait oldugunu tahmin eder.
 *
 * <p>PDF'lerde kategori bilgisi cogu zaman yazmaz; "Güz Yarıyılı Final
 * Sınavları" satirinin sinav kategorisine ait oldugunu okuyucu baglamdan
 * anlar. Bu sinif ayni cikarimi anahtar kelimelerle yapar.</p>
 *
 * <p><b>Tahmin kesin degildir ve oyle de olmasi gerekmez.</b> Amaci kullanicinin
 * onay ekraninda 30 satiri tek tek doldurmasini engellemektir; yanlis tahminleri
 * kullanici duzeltir. Bu yuzden tahminle birlikte bir GUVEN skoru da dondurulur.</p>
 */
@Component
public class KategoriTahminEdici {

    /**
     * Anahtar kelime -> kategori eslesmeleri.
     *
     * <p><b>SIRA KRITIKTIR: en ozelden en genele.</b> Ilk eslesen kural kazanir,
     * bu yuzden liste guven skoruna gore AZALAN sirada tutulur - yuksek skor
     * zaten "bu kelime bu kategoriyi kesin belirler" demektir.</p>
     *
     * <p>Bir testte yakalanan somut ornek: "Ders Kayıtları" satiri, genel "ders"
     * kurali daha ustte oldugu icin <i>Ders ve Sınav</i> olarak siniflandiriliyordu.
     * Oysa "kayıt" kelimesi burada "ders" kelimesinden DAHA BELIRLEYICIDIR;
     * kayit kurali yukari alinarak duzeltildi. Genel "ders" kurali artik en
     * altta, yalnizca daha ozel hicbir kural eslesmediginde devreye girer.</p>
     */
    private static final List<Kural> KURALLAR = List.of(
            // 95 - tatil adlari neredeyse hicbir zaman baska anlama gelmez
            new Kural("RESMI_TATIL", 95, "tatil", "bayram", "resmî tatil", "resmi tatil",
                    "yılbaşı", "yilbasi", "ramazan", "kurban", "cumhuriyet",
                    "zafer", "egemenlik", "gençlik", "genclik", "emek ve dayanışma",
                    "atatürk", "ataturk", "anma"),

            // 90 - sinav turleri belirleyicidir
            new Kural("DERS_SINAV", 90, "sınav", "sinav", "vize", "final", "bütünleme",
                    "butunleme", "mazeret", "tek ders", "quiz"),

            // 90 - kayit/basvuru kelimeleri, icinde "ders" gecse bile belirleyicidir
            //      ("Ders Kayıtları", "Ders Seçimi" gibi)
            new Kural("KAYIT_BASVURU", 90, "kayıt", "kayit", "başvuru", "basvuru",
                    "yerleştirme", "yerlestirme", "ders seçim", "ders secim",
                    "danışman onay", "danisman onay", "harç", "harc",
                    "katkı payı", "katki payi"),

            // 70 - etkinlik turleri
            new Kural("AKADEMIK_IDARI", 70, "mezuniyet", "senato", "kurul", "toplantı",
                    "toplanti", "tören", "toren", "şenlik", "senlik", "kariyer",
                    "oryantasyon", "seminer", "konferans", "ilan", "duyuru"),

            // 80 puanli ama EN GENEL kural: yukaridakilerin hicbiri eslesmediyse
            // "ders" gecen her sey ders/sinav kategorisine dusulur.
            // Skoru yuksek ama ozgullugu dusuk oldugu icin bilerek en altta.
            new Kural("DERS_SINAV", 80, "ders", "yarıyıl", "yariyil", "dönem başlangıc",
                    "donem baslangic", "öğretim", "ogretim")
    );

    /** Hicbir kural eslesmezse bu kategoriye dusulur. */
    private static final String VARSAYILAN_KOD = "AKADEMIK_IDARI";
    private static final int VARSAYILAN_GUVEN = 40;

    /**
     * Etkinlik adina en uygun kategoriyi bulur.
     *
     * @param etkinlikAdi PDF'ten cikarilan ad
     * @param kategoriler sistemde tanimli aktif kategoriler
     * @return eslesen kategori ve guven skoru
     */
    public Tahmin tahminEt(String etkinlikAdi, List<Kategori> kategoriler) {
        if (etkinlikAdi == null || kategoriler.isEmpty()) {
            return new Tahmin(null, 0);
        }

        // Locale.ROOT sart: sistem locale'i tr_TR oldugunda "I" harfi noktasiz
        // "ı" ya donusur ve "SINAV" -> "sınav" beklentimiz bozulur.
        String kucuk = etkinlikAdi.toLowerCase(Locale.ROOT);

        for (Kural kural : KURALLAR) {
            if (kural.eslesiyorMu(kucuk)) {
                Optional<Kategori> kategori = kodaGoreBul(kategoriler, kural.kategoriKodu());
                if (kategori.isPresent()) {
                    return new Tahmin(kategori.get(), kural.guven());
                }
            }
        }

        // Varsayilan kategori de sistemde yoksa ilk aktif kategoriye dusulur;
        // boylece ice aktarma kategori eksikligi yuzunden tamamen durmaz.
        Kategori varsayilan = kodaGoreBul(kategoriler, VARSAYILAN_KOD)
                .orElse(kategoriler.get(0));
        return new Tahmin(varsayilan, VARSAYILAN_GUVEN);
    }

    private Optional<Kategori> kodaGoreBul(List<Kategori> kategoriler, String kod) {
        return kategoriler.stream()
                .filter(k -> kod.equalsIgnoreCase(k.getKod()))
                .findFirst();
    }

    /** Tek bir eslesme kurali. */
    private record Kural(String kategoriKodu, int guven, String... anahtarKelimeler) {

        boolean eslesiyorMu(String kucukMetin) {
            for (String kelime : anahtarKelimeler) {
                if (kucukMetin.contains(kelime)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Tahmin sonucu.
     *
     * @param guven 0-100; onay ekraninda dusuk guvenli satirlar vurgulanir
     */
    public record Tahmin(Kategori kategori, int guven) {

        public boolean basariliMi() {
            return kategori != null;
        }
    }
}

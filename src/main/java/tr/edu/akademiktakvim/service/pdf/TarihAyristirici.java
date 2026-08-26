package tr.edu.akademiktakvim.service.pdf;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Serbest metin icindeki Turkce tarih ifadelerini yakalar ve
 * {@link TarihAraligi} nesnesine cevirir.
 *
 * <p>Universitelerin yayimladigi takvim PDF'lerinde tarih yazimi standart
 * degildir. Ayni belgede bile su varyasyonlar bir arada gorulebilir:</p>
 * <pre>
 *   15.09.2025                       -> tek gun
 *   15/09/2025 - 20/09/2025          -> tam aralik
 *   15 Eylül 2025                    -> Turkce ay adi
 *   15-20 Eylül 2025                 -> ayni ay icinde kisa aralik
 *   28 Eylül - 3 Ekim 2025           -> farkli aylar, yil bir kez yazilmis
 *   28 Aralık - 3 Ocak 2026          -> yil donumunu asan aralik
 *   15 Eylül 2025 Pazartesi          -> gun adi ekli
 * </pre>
 *
 * <h3>Iki kritik tasarim kurali</h3>
 * <ol>
 *   <li><b>Desenler en ozelden en genele denenir.</b> Tek tarih deseni once
 *       denenirse "15-20 Eylül 2025" ifadesinden yalnizca "20 Eylül 2025"
 *       yakalanir ve aralik bilgisi sessizce kaybolur.</li>
 *   <li><b>Bir desen METINSEL olarak eslestiginde arama orada biter</b> - o
 *       desenin urettigi tarih gecersiz olsa bile daha genel desenlere
 *       DUSULMEZ. Aksi halde "20.09.2025 - 15.09.2025" gibi ters sirali,
 *       yani hatali bir aralik reddedilmek yerine "20.09.2025" tek gunune
 *       donusur ve kullanici yanlis veriyi fark etmez.</li>
 * </ol>
 */
@Component
public class TarihAyristirici {

    /**
     * Turkce ay adlari.
     *
     * <p>Hem "Eylül" hem "Eylul" (Turkce karakter kullanilmadan yazilmis)
     * bicimleri eslesir; kaynak PDF'ler sik sik ikincisini kullanir.</p>
     */
    private static final Map<String, Integer> AYLAR = ayHaritasiOlustur();

    /** Ay adlarinin regex icinde kullanilacak alternatifleri. */
    private static final String AY_KALIBI = String.join("|", AYLAR.keySet());

    /**
     * Turkce harflerin buyuk/kucuk eslesmesi icin gereken bayraklar.
     *
     * <p><b>{@code UNICODE_CASE} neden sart?</b> Java'da
     * {@code CASE_INSENSITIVE} tek basina YALNIZCA ASCII harfleri kapsar.
     * "Şubat" kelimesindeki "Ş" ASCII disi oldugu icin, bu bayrak olmadan
     * desen "şubat" ile eslesmez. Sonuc: 12 ayin 11'i calisir, yalnizca
     * Subat sessizce basarisiz olur.</p>
     */
    private static final int TURKCE_BAYRAKLAR = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

    /** Turkce gun adlari - tarih aranmadan once temizlenir. */
    private static final Pattern GUN_ADLARI = Pattern.compile(
            "\\b(Pazartesi|Salı|Sali|Çarşamba|Carsamba|Perşembe|Persembe|Cuma|Cumartesi|Pazar)\\b",
            TURKCE_BAYRAKLAR);

    /** Kabul edilen aralik ayiricilari: kisa cizgi, en cizgi, em cizgi, "ile". */
    private static final String AYIRICI = "\\s*(?:-|–|—|ile)\\s*";

    // ---------------------------------------------------------------- DESENLER

    /** 15.09.2025 - 20.09.2025 */
    private static final Pattern SAYISAL_ARALIK = Pattern.compile(
            "(\\d{1,2})[./](\\d{1,2})[./](\\d{4})" + AYIRICI + "(\\d{1,2})[./](\\d{1,2})[./](\\d{4})");

    /** 15 Eylül 2025 - 20 Eylül 2025 */
    private static final Pattern TAM_TURKCE_ARALIK = Pattern.compile(
            "(\\d{1,2})\\s+(" + AY_KALIBI + ")\\s+(\\d{4})" + AYIRICI
                    + "(\\d{1,2})\\s+(" + AY_KALIBI + ")\\s+(\\d{4})",
            TURKCE_BAYRAKLAR);

    /** 28 Eylül - 3 Ekim 2025  (yil yalnizca sonda yazilmis, iki farkli ay) */
    private static final Pattern FARKLI_AY_ARALIK = Pattern.compile(
            "(\\d{1,2})\\s+(" + AY_KALIBI + ")" + AYIRICI
                    + "(\\d{1,2})\\s+(" + AY_KALIBI + ")\\s+(\\d{4})",
            TURKCE_BAYRAKLAR);

    /** 15-20 Eylül 2025  (ayni ay, gun araligi) */
    private static final Pattern AYNI_AY_ARALIK = Pattern.compile(
            "(\\d{1,2})" + AYIRICI + "(\\d{1,2})\\s+(" + AY_KALIBI + ")\\s+(\\d{4})",
            TURKCE_BAYRAKLAR);

    /** 15.09.2025 veya 15/09/2025 */
    private static final Pattern SAYISAL_TEK = Pattern.compile(
            "(\\d{1,2})[./](\\d{1,2})[./](\\d{4})");

    /** 15 Eylül 2025 */
    private static final Pattern TURKCE_TEK = Pattern.compile(
            "(\\d{1,2})\\s+(" + AY_KALIBI + ")\\s+(\\d{4})",
            TURKCE_BAYRAKLAR);

    /**
     * Desenler ve karsilik gelen cevirici islevler - EN OZELDEN EN GENELE.
     * Sira degistirilirse ayristirma sessizce bozulur; testler bunu korur.
     */
    private final List<DesenKurali> kurallar = List.of(
            new DesenKurali(SAYISAL_ARALIK, this::sayisalAraligaCevir),
            new DesenKurali(TAM_TURKCE_ARALIK, this::tamTurkceAraligaCevir),
            new DesenKurali(FARKLI_AY_ARALIK, this::farkliAyAraligaCevir),
            new DesenKurali(AYNI_AY_ARALIK, this::ayniAyAraligaCevir),
            new DesenKurali(SAYISAL_TEK, this::sayisalTekeCevir),
            new DesenKurali(TURKCE_TEK, this::turkceTekeCevir));

    /**
     * Metinden tarih araligini cikarir.
     *
     * @return tarih bulunamazsa ya da bulunan tarih gecersizse
     *         {@link Optional#empty()}
     */
    public Optional<TarihAraligi> ayristir(String metin) {
        if (metin == null || metin.isBlank()) {
            return Optional.empty();
        }
        String temiz = onTemizlik(metin);

        for (DesenKurali kural : kurallar) {
            Matcher m = kural.desen().matcher(temiz);
            if (m.find()) {
                // Desen metinsel olarak eslesti. Uretilen tarih gecersiz olsa
                // bile daha genel desenlere DUSULMEZ - sinif javadoc'undaki
                // 2. tasarim kurali.
                return kural.cevirici().apply(m);
            }
        }
        return Optional.empty();
    }

    /**
     * Tarih ifadesini metinden cikarip GERIYE KALANI dondurur.
     *
     * <p>Kalan metin etkinligin adi olarak kullanilir. Ornegin
     * "Güz Yarıyılı Ara Sınavları 10-21 Kasım 2025" satirindan tarih
     * cikarilinca geriye "Güz Yarıyılı Ara Sınavları" kalir.</p>
     */
    public String tarihiCikar(String metin) {
        if (metin == null) {
            return "";
        }
        String kalan = onTemizlik(metin);
        for (DesenKurali kural : kurallar) {
            Matcher m = kural.desen().matcher(kalan);
            if (m.find()) {
                kalan = m.replaceAll(" ");
                break;
            }
        }
        // Tarih cikarilinca geride kalan noktalama ve fazla bosluklar temizlenir
        return kalan.replaceAll("[\\s.:;,\\-–—]+$", "")
                .replaceAll("^[\\s.:;,\\-–—]+", "")
                .replaceAll("\\s{2,}", " ")
                .trim();
    }

    /** Metinde gecerli bir tarih ifadesi var mi? */
    public boolean tarihIceriyorMu(String metin) {
        return ayristir(metin).isPresent();
    }

    // ------------------------------------------------------------ CEVIRICILER

    private Optional<TarihAraligi> sayisalAraligaCevir(Matcher m) {
        return araligaCevir(
                tarihKur(sayi(m, 3), sayi(m, 2), sayi(m, 1)),
                tarihKur(sayi(m, 6), sayi(m, 5), sayi(m, 4)));
    }

    private Optional<TarihAraligi> tamTurkceAraligaCevir(Matcher m) {
        return araligaCevir(
                tarihKur(sayi(m, 3), ay(m.group(2)), sayi(m, 1)),
                tarihKur(sayi(m, 6), ay(m.group(5)), sayi(m, 4)));
    }

    private Optional<TarihAraligi> farkliAyAraligaCevir(Matcher m) {
        int yil = sayi(m, 5);
        LocalDate baslangic = tarihKur(yil, ay(m.group(2)), sayi(m, 1));
        LocalDate bitis = tarihKur(yil, ay(m.group(4)), sayi(m, 3));

        // YIL DONUMU: "28 Aralık - 3 Ocak 2026" ifadesinde yazili olan yil
        // BITIS tarihine aittir; baslangic bir onceki yildadir. Bu duzeltme
        // olmasaydi aralik ters cikar ve satir tamamen elenirdi.
        if (baslangic != null && bitis != null && baslangic.isAfter(bitis)) {
            baslangic = baslangic.minusYears(1);
        }
        return araligaCevir(baslangic, bitis);
    }

    private Optional<TarihAraligi> ayniAyAraligaCevir(Matcher m) {
        int ay = ay(m.group(3));
        int yil = sayi(m, 4);
        return araligaCevir(tarihKur(yil, ay, sayi(m, 1)), tarihKur(yil, ay, sayi(m, 2)));
    }

    private Optional<TarihAraligi> sayisalTekeCevir(Matcher m) {
        LocalDate tarih = tarihKur(sayi(m, 3), sayi(m, 2), sayi(m, 1));
        return araligaCevir(tarih, tarih);
    }

    private Optional<TarihAraligi> turkceTekeCevir(Matcher m) {
        LocalDate tarih = tarihKur(sayi(m, 3), ay(m.group(2)), sayi(m, 1));
        return araligaCevir(tarih, tarih);
    }

    // ------------------------------------------------------------- YARDIMCILAR

    /** Gun adlarini, ozel bosluk/cizgi karakterlerini ve fazla bosluklari temizler. */
    private String onTemizlik(String metin) {
        return GUN_ADLARI.matcher(metin).replaceAll(" ")
                .replace(' ', ' ')          // kirilmayan bosluk (PDF'lerde yaygin)
                .replaceAll("\\s{2,}", " ")
                .trim();
    }

    private int sayi(Matcher m, int grup) {
        return Integer.parseInt(m.group(grup));
    }

    /**
     * Ay adini sayiya cevirir.
     *
     * <p>{@code Locale.ROOT} zorunlu: sistemin varsayilan locale'i tr_TR
     * oldugunda {@code "KASIM".toLowerCase()} cagrisi "I" harfini noktasiz
     * "ı" yapar ve harita anahtarlariyla eslesme bozulur.</p>
     */
    private int ay(String ayAdi) {
        Integer ay = AYLAR.get(ayAdi.toLowerCase(Locale.ROOT));
        return ay != null ? ay : -1;
    }

    /** Gecersiz tarihte (orn. 31 Şubat) istisna firlatmak yerine null doner. */
    private LocalDate tarihKur(int yil, int ay, int gun) {
        if (ay < 1 || ay > 12 || gun < 1 || gun > 31 || yil < 1900 || yil > 2200) {
            return null;
        }
        try {
            return LocalDate.of(yil, ay, gun);
        } catch (DateTimeException ex) {
            return null;
        }
    }

    /** Iki tarihi araliga cevirir; eksik ya da ters sirali ise bos doner. */
    private Optional<TarihAraligi> araligaCevir(LocalDate baslangic, LocalDate bitis) {
        if (baslangic == null || bitis == null) {
            return Optional.empty();
        }
        if (bitis.isBefore(baslangic)) {
            // Ters sirali aralik bir ayristirma hatasidir; tahminle duzeltilmez.
            // Satir onay ekraninda kullaniciya gosterilir, o karar verir.
            return Optional.empty();
        }
        return Optional.of(new TarihAraligi(baslangic, bitis));
    }

    private static Map<String, Integer> ayHaritasiOlustur() {
        Map<String, Integer> harita = new LinkedHashMap<>();
        ayEkle(harita, 1, "ocak");
        ayEkle(harita, 2, "şubat", "subat");
        ayEkle(harita, 3, "mart");
        ayEkle(harita, 4, "nisan");
        ayEkle(harita, 5, "mayıs", "mayis");
        ayEkle(harita, 6, "haziran");
        ayEkle(harita, 7, "temmuz");
        ayEkle(harita, 8, "ağustos", "agustos");
        ayEkle(harita, 9, "eylül", "eylul");
        ayEkle(harita, 10, "ekim");
        ayEkle(harita, 11, "kasım", "kasim");
        ayEkle(harita, 12, "aralık", "aralik");
        return harita;
    }

    private static void ayEkle(Map<String, Integer> harita, int ayNo, String... adlar) {
        for (String ad : adlar) {
            harita.put(ad, ayNo);
        }
    }

    /** Bir desen ve onun eslesmesini tarihe ceviren islev. */
    private record DesenKurali(Pattern desen, Function<Matcher, Optional<TarihAraligi>> cevirici) {
    }

    /** Ayristirilan tarih araligi. Tek gunluk etkinlikte iki alan esittir. */
    public record TarihAraligi(LocalDate baslangic, LocalDate bitis) {

        public boolean tekGunMu() {
            return baslangic.equals(bitis);
        }
    }
}

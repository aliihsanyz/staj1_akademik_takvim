package tr.edu.akademiktakvim.service.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import tr.edu.akademiktakvim.service.pdf.TarihAyristirici.TarihAraligi;

/**
 * {@link TarihAyristirici} birim testleri.
 *
 * <p>Universitelerin gercek takvim PDF'lerinde karsilasilan tarih yazim
 * bicimlerinin tamami burada sinanir. Bu sinif ayni zamanda ayristiricinin
 * "sozlesmesi"dir: hangi bicimleri destekledigi buradan okunur.</p>
 */
@DisplayName("TarihAyristirici")
class TarihAyristiriciTest {

    private final TarihAyristirici ayristirici = new TarihAyristirici();

    @Nested
    @DisplayName("Tek gün tarihleri")
    class TekGun {

        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource({
                "'15.09.2025',                    2025-09-15",
                "'15/09/2025',                    2025-09-15",
                "'1.1.2026',                      2026-01-01",
                "'15 Eylül 2025',                 2025-09-15",
                "'15 Eylul 2025',                 2025-09-15",
                "'1 Ocak 2026',                   2026-01-01",
                "'30 Ağustos 2026',               2026-08-30",
                "'23 Nisan 2026',                 2026-04-23",
        })
        void tekTarihYakalanir(String metin, LocalDate beklenen) {
            TarihAraligi aralik = ayristirici.ayristir(metin).orElseThrow();

            assertThat(aralik.baslangic()).isEqualTo(beklenen);
            assertThat(aralik.bitis()).isEqualTo(beklenen);
            assertThat(aralik.tekGunMu()).isTrue();
        }

        @ParameterizedTest(name = "gün adı temizlenir: {0}")
        @ValueSource(strings = {
                "15 Eylül 2025 Pazartesi",
                "Pazartesi 15 Eylül 2025",
                "15 Eylül 2025 PAZARTESİ",
                "15.09.2025 Pazartesi",
        })
        void gunAdlariGozArdiEdilir(String metin) {
            TarihAraligi aralik = ayristirici.ayristir(metin).orElseThrow();
            assertThat(aralik.baslangic()).isEqualTo(LocalDate.of(2025, 9, 15));
        }
    }

    @Nested
    @DisplayName("Tarih aralıkları")
    class Araliklar {

        @ParameterizedTest(name = "{0} -> {1} .. {2}")
        @CsvSource({
                // Tam sayısal aralık
                "'15.09.2025 - 20.09.2025',           2025-09-15, 2025-09-20",
                "'10/11/2025 - 21/11/2025',           2025-11-10, 2025-11-21",
                "'01.06.2026-14.06.2026',             2026-06-01, 2026-06-14",
                // Tam Türkçe aralık
                "'15 Eylül 2025 - 20 Eylül 2025',     2025-09-15, 2025-09-20",
                "'26 Ocak 2026 - 30 Ocak 2026',       2026-01-26, 2026-01-30",
                // Aynı ay, kısa aralık
                "'15-20 Eylül 2025',                  2025-09-15, 2025-09-20",
                "'08-12 Eylül 2025',                  2025-09-08, 2025-09-12",
                "'2-6 Şubat 2026',                    2026-02-02, 2026-02-06",
                // Farklı ay, yıl bir kez yazılmış
                "'28 Eylül - 3 Ekim 2025',            2025-09-28, 2025-10-03",
                "'5 Ocak - 18 Ocak 2026',             2026-01-05, 2026-01-18",
        })
        void aralikYakalanir(String metin, LocalDate baslangic, LocalDate bitis) {
            TarihAraligi aralik = ayristirici.ayristir(metin).orElseThrow();

            assertThat(aralik.baslangic()).isEqualTo(baslangic);
            assertThat(aralik.bitis()).isEqualTo(bitis);
        }

        @ParameterizedTest(name = "ayırıcı: {0}")
        @ValueSource(strings = {
                "15 Eylül - 20 Eylül 2025",
                "15 Eylül – 20 Eylül 2025",     // en çizgisi
                "15 Eylül — 20 Eylül 2025",     // em çizgisi
                "15 Eylül ile 20 Eylül 2025",
        })
        void farkliAyiricilarKabulEdilir(String metin) {
            TarihAraligi aralik = ayristirici.ayristir(metin).orElseThrow();

            assertThat(aralik.baslangic()).isEqualTo(LocalDate.of(2025, 9, 15));
            assertThat(aralik.bitis()).isEqualTo(LocalDate.of(2025, 9, 20));
        }

        @Test
        @DisplayName("yıl dönümünü aşan aralıkta başlangıç bir önceki yıla düşer")
        void yilDonumuDogruHesaplanir() {
            // "28 Aralık - 3 Ocak 2026": yazılı olan yıl BİTİŞ tarihine aittir.
            // Bu kural olmasaydı aralık ters çıkar ve satır sessizce elenirdi.
            TarihAraligi aralik = ayristirici.ayristir("28 Aralık - 3 Ocak 2026").orElseThrow();

            assertThat(aralik.baslangic()).isEqualTo(LocalDate.of(2025, 12, 28));
            assertThat(aralik.bitis()).isEqualTo(LocalDate.of(2026, 1, 3));
        }
    }

    @Nested
    @DisplayName("Türkçe karakter duyarlılığı")
    class TurkceKarakterler {

        /**
         * REGRESYON TESTI.
         *
         * <p>Java'da {@code Pattern.CASE_INSENSITIVE} TEK BASINA yalnizca ASCII
         * harfleri kapsar. "Şubat" kelimesindeki "Ş" harfi ASCII disi oldugu icin,
         * {@code Pattern.UNICODE_CASE} eklenmeden desen "şubat" ile eslesmiyordu.
         * Sonuc: 12 ayin 11'i calisiyor, yalnizca Subat sessizce basarisiz oluyordu.
         * Gercek bir PDF testinde yakalandi.</p>
         */
        @ParameterizedTest(name = "büyük harfli ay adı: {0}")
        @CsvSource({
                "'2 ŞUBAT 2026',      2026-02-02",
                "'2 Şubat 2026',      2026-02-02",
                "'2 şubat 2026',      2026-02-02",
                "'5 AĞUSTOS 2026',    2026-08-05",
                "'5 EYLÜL 2025',      2025-09-05",
                "'5 KASIM 2025',      2025-11-05",
                "'5 ARALIK 2025',     2025-12-05",
                "'5 MAYIS 2026',      2026-05-05",
        })
        void asciiDisiBuyukHarfliAylarEslesir(String metin, LocalDate beklenen) {
            assertThat(ayristirici.ayristir(metin))
                    .as("ASCII dışı büyük harfle başlayan ay adı eşleşmeli")
                    .isPresent()
                    .get()
                    .extracting(TarihAraligi::baslangic)
                    .isEqualTo(beklenen);
        }

        @Test
        @DisplayName("12 ayın tamamı hem Türkçe hem ASCII yazımıyla tanınır")
        void tumAylarTaninir() {
            String[][] aylar = {
                    {"Ocak", "Ocak"}, {"Şubat", "Subat"}, {"Mart", "Mart"},
                    {"Nisan", "Nisan"}, {"Mayıs", "Mayis"}, {"Haziran", "Haziran"},
                    {"Temmuz", "Temmuz"}, {"Ağustos", "Agustos"}, {"Eylül", "Eylul"},
                    {"Ekim", "Ekim"}, {"Kasım", "Kasim"}, {"Aralık", "Aralik"}
            };

            for (int i = 0; i < aylar.length; i++) {
                for (String yazim : aylar[i]) {
                    Optional<TarihAraligi> sonuc = ayristirici.ayristir("15 " + yazim + " 2026");

                    assertThat(sonuc)
                            .as("Ay tanınmalı: %s", yazim)
                            .isPresent();
                    assertThat(sonuc.get().baslangic().getMonthValue())
                            .as("Ay numarası doğru olmalı: %s", yazim)
                            .isEqualTo(i + 1);
                }
            }
        }
    }

    @Nested
    @DisplayName("Geçersiz girdiler")
    class GecersizGirdiler {

        @ParameterizedTest(name = "tarih bulunamaz: \"{0}\"")
        @ValueSource(strings = {
                "Derslerin başlaması",
                "GÜZ YARIYILI",
                "Bu takvim Senato kararı ile değiştirilebilir.",
                "2025-2026 Akademik Yılı",      // yıl var ama tam tarih yok
                "",
                "   ",
        })
        void tarihIcermeyenMetinBosDoner(String metin) {
            assertThat(ayristirici.ayristir(metin)).isEmpty();
        }

        @Test
        void nullGuvenliDir() {
            assertThat(ayristirici.ayristir(null)).isEmpty();
            assertThat(ayristirici.tarihiCikar(null)).isEmpty();
        }

        @ParameterizedTest(name = "takvimde olmayan gün: {0}")
        @ValueSource(strings = {"31.02.2026", "32.01.2026", "15.13.2025"})
        void gecersizTarihReddedilir(String metin) {
            assertThat(ayristirici.ayristir(metin)).isEmpty();
        }

        @Test
        @DisplayName("ters sıralı aralık kabul edilmez")
        void tersAralikReddedilir() {
            // Ayrıştırma hatası olduğu için sessizce kabul edilmez;
            // satır onay ekranında kullanıcıya gösterilir.
            assertThat(ayristirici.ayristir("20.09.2025 - 15.09.2025")).isEmpty();
        }
    }

    @Nested
    @DisplayName("Desen önceliği")
    class DesenOnceligi {

        /**
         * Desenler en ozelden en genele denenmelidir. Tek tarih deseni once
         * denenirse "15-20 Eylül 2025" ifadesinden yalnizca "20 Eylül 2025"
         * yakalanir ve aralik bilgisi SESSIZCE kaybolur.
         */
        @Test
        void araliklarTekTarihtenOnceDenenir() {
            TarihAraligi aralik = ayristirici.ayristir("15-20 Eylül 2025").orElseThrow();

            assertThat(aralik.tekGunMu())
                    .as("Aralık, tek tarih olarak ayrıştırılmamalı")
                    .isFalse();
            assertThat(aralik.baslangic()).isEqualTo(LocalDate.of(2025, 9, 15));
        }
    }

    @Nested
    @DisplayName("Tarihi metinden çıkarma")
    class TarihiCikarma {

        @ParameterizedTest(name = "\"{0}\" -> \"{1}\"")
        @CsvSource({
                "'Güz Yarıyılı Ara Sınavları 10-21 Kasım 2025',   'Güz Yarıyılı Ara Sınavları'",
                "'15.09.2025 Derslerin Başlaması',                'Derslerin Başlaması'",
                "'Ders Kayıtları 08-12 Eylül 2025',               'Ders Kayıtları'",
                "'Mezuniyet Töreni : 26 Haziran 2026',            'Mezuniyet Töreni'",
                "'Yılbaşı Tatili - 01.01.2026',                   'Yılbaşı Tatili'",
        })
        void tarihCikarilincaEtkinlikAdiKalir(String satir, String beklenenAd) {
            assertThat(ayristirici.tarihiCikar(satir)).isEqualTo(beklenenAd);
        }

        @Test
        void tarihYoksaMetinAynenDoner() {
            assertThat(ayristirici.tarihiCikar("Derslerin Başlaması"))
                    .isEqualTo("Derslerin Başlaması");
        }
    }
}

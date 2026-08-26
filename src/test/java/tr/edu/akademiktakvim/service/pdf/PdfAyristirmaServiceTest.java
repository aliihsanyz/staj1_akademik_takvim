package tr.edu.akademiktakvim.service.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import tr.edu.akademiktakvim.TestVeriUretici;
import tr.edu.akademiktakvim.domain.Kategori;
import tr.edu.akademiktakvim.domain.enums.Donem;
import tr.edu.akademiktakvim.dto.IceAktarmaDTO;

/**
 * {@link PdfAyristirmaService} birim testleri.
 *
 * <p>Testler PDF dosyasi olusturmak yerine dogrudan SATIR LISTESI ile calisir.
 * Sebep: burada sinanan sey PDF okuma degil, satirlarin etkinliklere
 * cevrilmesi mantigidir. Ikisini ayirmak testleri hem hizli hem de
 * hata sebebini net gosteren testler yapar.</p>
 */
@DisplayName("PdfAyristirmaService")
class PdfAyristirmaServiceTest {

    private PdfAyristirmaService servis;
    private List<Kategori> kategoriler;

    @BeforeEach
    void hazirla() {
        servis = new PdfAyristirmaService(
                new PdfMetinCikarici(),
                new TarihAyristirici(),
                new KategoriTahminEdici(),
                null);   // Repository yalnizca onizle() yolunda kullanilir

        kategoriler = List.of(
                kategoriKodlu(1L, "Ders ve Sınav Tarihleri", "DERS_SINAV"),
                kategoriKodlu(2L, "Kayıt ve Başvuru Tarihleri", "KAYIT_BASVURU"),
                kategoriKodlu(3L, "Resmî Tatiller", "RESMI_TATIL"),
                kategoriKodlu(4L, "Akademik ve İdari Etkinlikler", "AKADEMIK_IDARI"));
    }

    private Kategori kategoriKodlu(Long id, String ad, String kod) {
        Kategori k = new Kategori(ad, kod, "#112233", id.intValue());
        org.springframework.test.util.ReflectionTestUtils.setField(k, "id", id);
        return k;
    }

    // ====================================================== BÖLÜM BAŞLIKLARI

    @Nested
    @DisplayName("Bölüm başlığı tespiti")
    class BolumBasligi {

        @ParameterizedTest(name = "\"{0}\" -> {1}")
        @CsvSource({
                "'GÜZ YARIYILI',            GUZ",
                "'Güz Yarıyılı',            GUZ",
                "'GUZ YARIYILI',            GUZ",
                "'BAHAR YARIYILI',          BAHAR",
                "'Bahar Dönemi',            BAHAR",
                "'YAZ OKULU',               YAZ",
        })
        void basliklarTaninir(String satir, Donem beklenen) {
            assertThat(servis.donemBasligiMi(satir)).isEqualTo(beklenen);
        }

        /**
         * REGRESYON TESTI.
         *
         * <p>PDF'lerde baslik fontlarindaki harf araligi yuzunden metin
         * "G ÜZ YARIYILI" gibi bolunebiliyor. Bu davranis PDFBox'in kendi
         * ciktisinda da goruluyor, bize ozgu degil. Eslesme bosluklar
         * kaldirilarak yapilmazsa basliklar taninmaz ve TUM etkinliklerin
         * donemi tahmine kalir.</p>
         */
        @ParameterizedTest(name = "harf aralığı bozulmuş: \"{0}\"")
        @CsvSource({
                "'G ÜZ YARIYILI',           GUZ",
                "'B AHAR YARIYILI',         BAHAR",
                "'Y AZ OKULU',              YAZ",
                "'G Ü Z  Y A R I Y I L I',  GUZ",
        })
        void harfAraligiBozulmusBasliklarTaninir(String satir, Donem beklenen) {
            assertThat(servis.donemBasligiMi(satir)).isEqualTo(beklenen);
        }

        /**
         * REGRESYON TESTI.
         *
         * <p>Gercek bir testte "Bahar Yarıyılı Kayıt Yenileme 2-6 Şubat 2026"
         * satiri baslik sanilmisti. Sonuc iki katli zarardi: (1) etkinlik
         * sessizce kayboldu, (2) donem baglami bozuldugu icin ARDINDAN GELEN
         * tum yaz etkinlikleri BAHAR olarak isaretlendi.</p>
         */
        @ParameterizedTest(name = "başlık DEĞİL: \"{0}\"")
        @ValueSource(strings = {
                "Bahar Yarıyılı Kayıt Yenileme 2-6 Şubat 2026",
                "Güz Yarıyılı Ara Sınavları 10-21 Kasım 2025",
                "Yaz Okulu Kayıtları 1-3 Temmuz 2026",
                "2025-2026 GÜZ YARIYILI",
                "Bu takvim Senato kararı ile değiştirilebilir ve yarıyıl başında güncellenir.",
        })
        void tarihIcerenSatirBaslikSayilmaz(String satir) {
            assertThat(servis.donemBasligiMi(satir))
                    .as("Tarih veya yıl içeren satır başlık sayılmamalı")
                    .isNull();
        }

        @ParameterizedTest(name = "dönem kelimesi yok: \"{0}\"")
        @ValueSource(strings = {
                "AKADEMİK TAKVİM",
                "Ders Kayıtları",
                "Önemli Notlar",
        })
        void donemKelimesiOlmayanBaslikSayilmaz(String satir) {
            assertThat(servis.donemBasligiMi(satir)).isNull();
        }
    }

    // ========================================================== AYRIŞTIRMA

    @Nested
    @DisplayName("Satırların etkinliklere çevrilmesi")
    class SatirAyristirma {

        @Test
        @DisplayName("bölüm başlığı sonraki etkinliklerin dönemini belirler")
        void baslikDonemBaglamiKurar() {
            List<String> satirlar = List.of(
                    "GÜZ YARIYILI",
                    "Derslerin Başlaması 15 Eylül 2025",
                    "BAHAR YARIYILI",
                    "Derslerin Başlaması 9 Şubat 2026",
                    "YAZ OKULU",
                    "Yaz Okulu Kayıtları 1-3 Temmuz 2026");

            IceAktarmaDTO.OnizlemeYaniti sonuc =
                    servis.satirlariAyristir(satirlar, kategoriler, "test.pdf");

            assertThat(sonuc.etkinlikler()).hasSize(3);
            assertThat(sonuc.etkinlikler().get(0).donem()).isEqualTo(Donem.GUZ);
            assertThat(sonuc.etkinlikler().get(1).donem()).isEqualTo(Donem.BAHAR);
            assertThat(sonuc.etkinlikler().get(2).donem()).isEqualTo(Donem.YAZ);
        }

        @Test
        @DisplayName("başlık yoksa dönem başlangıç tarihinden tahmin edilir")
        void baslikYoksaTarihtenTahminEdilir() {
            List<String> satirlar = List.of("Ara Sınavlar 10-21 Kasım 2025");

            IceAktarmaDTO.AyristirilanEtkinlik etkinlik =
                    servis.satirlariAyristir(satirlar, kategoriler, "test.pdf")
                            .etkinlikler().get(0);

            assertThat(etkinlik.donem()).isEqualTo(Donem.GUZ);
            assertThat(etkinlik.uyarilar())
                    .as("Tahmin yapıldığı kullanıcıya bildirilmeli")
                    .anyMatch(u -> u.contains("tahmin"));
        }

        @Test
        @DisplayName("etkinlik adı tarihten arındırılır")
        void etkinlikAdiTemizlenir() {
            List<String> satirlar = List.of("Güz Yarıyılı Ara Sınavları 10-21 Kasım 2025");

            IceAktarmaDTO.AyristirilanEtkinlik etkinlik =
                    servis.satirlariAyristir(satirlar, kategoriler, "test.pdf")
                            .etkinlikler().get(0);

            assertThat(etkinlik.ad()).isEqualTo("Güz Yarıyılı Ara Sınavları");
            assertThat(etkinlik.baslangicTarihi()).isEqualTo(java.time.LocalDate.of(2025, 11, 10));
            assertThat(etkinlik.bitisTarihi()).isEqualTo(java.time.LocalDate.of(2025, 11, 21));
        }

        @Test
        @DisplayName("tarihi olmayan satırlar gizlenmez, kullanıcıya gösterilir")
        void tarihsizSatirlarRaporlanir() {
            List<String> satirlar = List.of(
                    "ÖRNEK ÜNİVERSİTESİ AKADEMİK TAKVİMİ",
                    "Ara Sınavlar 10-21 Kasım 2025",
                    "Not: Bu takvim değiştirilebilir.");

            IceAktarmaDTO.OnizlemeYaniti sonuc =
                    servis.satirlariAyristir(satirlar, kategoriler, "test.pdf");

            assertThat(sonuc.etkinlikler()).hasSize(1);
            assertThat(sonuc.ayristirilamayanSatirlar())
                    .as("Atlanan satırlar sessizce yutulmamalı")
                    .hasSize(2);
            assertThat(sonuc.istatistik().atlananSatir()).isEqualTo(2);
        }

        @Test
        @DisplayName("kategori anahtar kelimeye göre tahmin edilir")
        void kategoriTahminEdilir() {
            List<String> satirlar = List.of(
                    "Final Sınavları 5-18 Ocak 2026",
                    "Ders Kayıtları 8-12 Eylül 2025",
                    "Cumhuriyet Bayramı 29 Ekim 2025");

            List<IceAktarmaDTO.AyristirilanEtkinlik> etkinlikler =
                    servis.satirlariAyristir(satirlar, kategoriler, "test.pdf").etkinlikler();

            assertThat(etkinlikler.get(0).kategoriAdi()).isEqualTo("Ders ve Sınav Tarihleri");
            assertThat(etkinlikler.get(1).kategoriAdi()).isEqualTo("Kayıt ve Başvuru Tarihleri");
            assertThat(etkinlikler.get(2).kategoriAdi()).isEqualTo("Resmî Tatiller");
        }

        @Test
        @DisplayName("olağandışı uzun etkinlik için uyarı üretilir")
        void uzunSureUyarisi() {
            List<String> satirlar = List.of("Şüpheli Etkinlik 01.01.2026 - 31.12.2026");

            IceAktarmaDTO.AyristirilanEtkinlik etkinlik =
                    servis.satirlariAyristir(satirlar, kategoriler, "test.pdf")
                            .etkinlikler().get(0);

            assertThat(etkinlik.uyarilar()).anyMatch(u -> u.contains("olağandışı uzun"));
            assertThat(etkinlik.guvenSkoru()).isLessThan(60);
        }

        @Test
        @DisplayName("kaynak satır saklanır (kullanıcı karşılaştırabilsin)")
        void kaynakSatirSaklanir() {
            String satir = "Ara Sınavlar 10-21 Kasım 2025";

            IceAktarmaDTO.AyristirilanEtkinlik etkinlik =
                    servis.satirlariAyristir(List.of(satir), kategoriler, "test.pdf")
                            .etkinlikler().get(0);

            assertThat(etkinlik.kaynakSatir()).isEqualTo(satir);
        }

        @Test
        @DisplayName("boş satır listesi hata vermez")
        void bosListeGuvenlidir() {
            IceAktarmaDTO.OnizlemeYaniti sonuc =
                    servis.satirlariAyristir(List.of(), kategoriler, "bos.pdf");

            assertThat(sonuc.etkinlikler()).isEmpty();
            assertThat(sonuc.istatistik().bulunanEtkinlik()).isZero();
        }
    }

    // =========================================================== İSTATİSTİK

    @Test
    @DisplayName("istatistik güven dağılımını doğru raporlar")
    void istatistikDogruHesaplanir() {
        List<String> satirlar = List.of(
                "GÜZ YARIYILI",
                "Final Sınavları 5-18 Ocak 2026",          // yüksek güven
                "Şüpheli Kayıt 01.01.2026 - 31.12.2026",   // düşük güven (uzun süre)
                "Tarihsiz bir satır");

        IceAktarmaDTO.Istatistik istatistik =
                servis.satirlariAyristir(satirlar, kategoriler, "test.pdf").istatistik();

        assertThat(istatistik.bulunanEtkinlik()).isEqualTo(2);
        assertThat(istatistik.yuksekGuven() + istatistik.dusukGuven()).isEqualTo(2);
        assertThat(istatistik.atlananSatir()).isEqualTo(1);
    }

    @Test
    @DisplayName("TestVeriUretici ile kurulan kategoriler de çalışır")
    void yardimciUretecUyumlu() {
        assertThat(TestVeriUretici.aktifKategori(1L, "Test").isAktif()).isTrue();
    }
}

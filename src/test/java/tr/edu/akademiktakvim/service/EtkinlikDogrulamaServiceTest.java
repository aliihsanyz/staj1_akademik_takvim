package tr.edu.akademiktakvim.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import tr.edu.akademiktakvim.TestVeriUretici;
import tr.edu.akademiktakvim.domain.Birim;
import tr.edu.akademiktakvim.domain.EgitimYili;
import tr.edu.akademiktakvim.domain.Kategori;
import tr.edu.akademiktakvim.domain.Kullanici;
import tr.edu.akademiktakvim.domain.enums.Donem;
import tr.edu.akademiktakvim.dto.EtkinlikIstekDTO;
import tr.edu.akademiktakvim.exception.IsKuraliIhlaliException;
import tr.edu.akademiktakvim.exception.YetkisizIslemException;

/**
 * Is analizi Bolum 7'deki temel is kurallarinin testleri.
 *
 * <p>Bu testler veritabani gerektirmez: {@link EtkinlikDogrulamaService}
 * bilerek saf bir kural sinifi olarak tasarlandi, repository bagimliligi yok.
 * Bu sayede kurallar hizli ve kirilgan olmayan testlerle korunabiliyor.</p>
 */
@DisplayName("EtkinlikDogrulamaService - temel iş kuralları")
class EtkinlikDogrulamaServiceTest {

    private EtkinlikDogrulamaService dogrulama;

    private Birim aktifBirim;
    private Kategori aktifKategori;
    private EgitimYili aktifYil;
    private Kullanici superAdmin;

    @BeforeEach
    void hazirla() {
        dogrulama = new EtkinlikDogrulamaService(new YetkiKontrolService());
        aktifBirim = TestVeriUretici.aktifBirim(1L, "Mühendislik Fakültesi");
        aktifKategori = TestVeriUretici.aktifKategori(1L, "Ders ve Sınav Tarihleri");
        aktifYil = TestVeriUretici.aktifEgitimYili(1L);
        superAdmin = TestVeriUretici.superAdmin("admin");
    }

    private EtkinlikIstekDTO istek(LocalDate baslangic, LocalDate bitis) {
        return new EtkinlikIstekDTO("Ara Sınavlar", null, baslangic, bitis,
                Donem.GUZ, 1L, 1L, 1L);
    }

    // ================================================================ KURAL 2

    @Nested
    @DisplayName("İŞ KURALI 2: Bitiş tarihi başlangıçtan önce olamaz")
    class TarihSirasi {

        @Test
        @DisplayName("bitiş < başlangıç ise reddedilir")
        void bitisBaslangictanOnceReddedilir() {
            LocalDate baslangic = LocalDate.of(2026, 3, 10);
            LocalDate bitis = LocalDate.of(2026, 3, 5);

            assertThatThrownBy(() -> dogrulama.tarihSirasiniDogrula(baslangic, bitis))
                    .isInstanceOf(IsKuraliIhlaliException.class)
                    .hasMessageContaining("Bitiş tarihi başlangıç tarihinden önce olamaz")
                    .extracting(ex -> ((IsKuraliIhlaliException) ex).getHataKodu())
                    .isEqualTo("TARIH_SIRASI_HATALI");
        }

        @Test
        @DisplayName("bitiş = başlangıç kabul edilir (tek günlük etkinlik)")
        void ayniGunKabulEdilir() {
            LocalDate gun = LocalDate.of(2026, 3, 10);

            assertThatCode(() -> dogrulama.tarihSirasiniDogrula(gun, gun))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("bitiş > başlangıç kabul edilir")
        void normalAralikKabulEdilir() {
            assertThatCode(() -> dogrulama.tarihSirasiniDogrula(
                    LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 20)))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("bir gün fark bile olsa yakalanır (sınır durumu)")
        void birGunFarkYakalanir() {
            assertThatThrownBy(() -> dogrulama.tarihSirasiniDogrula(
                    LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 9)))
                    .isInstanceOf(IsKuraliIhlaliException.class);
        }

        @Test
        @DisplayName("tarihlerden biri null ise bu kural sessiz geçer")
        void nullTarihlerBuKuralaTakilmaz() {
            // Boş alan kontrolü ayrı bir kuralın (KURAL 1) sorumluluğudur;
            // burada NullPointerException fırlatmak kullanıcıya anlamsız
            // bir 500 hatası dönmesine yol açardı.
            assertThatCode(() -> dogrulama.tarihSirasiniDogrula(null, LocalDate.now()))
                    .doesNotThrowAnyException();
            assertThatCode(() -> dogrulama.tarihSirasiniDogrula(LocalDate.now(), null))
                    .doesNotThrowAnyException();
        }
    }

    // ================================================================ KURAL 1

    @Nested
    @DisplayName("İŞ KURALI 1: Zorunlu alanlar boş bırakılamaz")
    class ZorunluAlanlar {

        @Test
        void bosAdReddedilir() {
            EtkinlikIstekDTO istek = new EtkinlikIstekDTO("   ", null,
                    LocalDate.now(), LocalDate.now(), Donem.GUZ, 1L, 1L, 1L);

            assertThatThrownBy(() -> dogrulama.zorunluAlanlariDogrula(istek))
                    .isInstanceOf(IsKuraliIhlaliException.class)
                    .hasMessageContaining("Etkinlik adı");
        }

        @Test
        void bosDonemReddedilir() {
            EtkinlikIstekDTO istek = new EtkinlikIstekDTO("Sınav", null,
                    LocalDate.now(), LocalDate.now(), null, 1L, 1L, 1L);

            assertThatThrownBy(() -> dogrulama.zorunluAlanlariDogrula(istek))
                    .isInstanceOf(IsKuraliIhlaliException.class)
                    .hasMessageContaining("Dönem");
        }

        @Test
        void bosBirimReddedilir() {
            EtkinlikIstekDTO istek = new EtkinlikIstekDTO("Sınav", null,
                    LocalDate.now(), LocalDate.now(), Donem.GUZ, 1L, 1L, null);

            assertThatThrownBy(() -> dogrulama.zorunluAlanlariDogrula(istek))
                    .isInstanceOf(IsKuraliIhlaliException.class)
                    .hasMessageContaining("Birim");
        }

        @Test
        void bosKategoriReddedilir() {
            EtkinlikIstekDTO istek = new EtkinlikIstekDTO("Sınav", null,
                    LocalDate.now(), LocalDate.now(), Donem.GUZ, 1L, null, 1L);

            assertThatThrownBy(() -> dogrulama.zorunluAlanlariDogrula(istek))
                    .isInstanceOf(IsKuraliIhlaliException.class)
                    .hasMessageContaining("Kategori");
        }

        @Test
        void tumAlanlarDoluysaGecer() {
            assertThatCode(() -> dogrulama.zorunluAlanlariDogrula(
                    istek(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2))))
                    .doesNotThrowAnyException();
        }
    }

    // ================================================================ KURAL 4

    @Nested
    @DisplayName("İŞ KURALI 4: Pasif tanımlar yeni kayıtta seçilemez")
    class PasifTanimlar {

        @Test
        void pasifBirimReddedilir() {
            Birim pasif = TestVeriUretici.birim(9L, "Kapanan Fakülte",
                    tr.edu.akademiktakvim.domain.enums.BirimTuru.FAKULTE, false);

            assertThatThrownBy(() -> dogrulama.tanimlarinAktifOlduguDogrula(
                    pasif, aktifKategori, aktifYil))
                    .isInstanceOf(IsKuraliIhlaliException.class)
                    .hasMessageContaining("Pasif durumdaki birim")
                    .extracting(ex -> ((IsKuraliIhlaliException) ex).getHataKodu())
                    .isEqualTo("BIRIM_PASIF");
        }

        @Test
        void pasifKategoriReddedilir() {
            Kategori pasif = TestVeriUretici.kategori(9L, "Kullanılmayan", false);

            assertThatThrownBy(() -> dogrulama.tanimlarinAktifOlduguDogrula(
                    aktifBirim, pasif, aktifYil))
                    .isInstanceOf(IsKuraliIhlaliException.class)
                    .extracting(ex -> ((IsKuraliIhlaliException) ex).getHataKodu())
                    .isEqualTo("KATEGORI_PASIF");
        }

        @Test
        void pasifEgitimYiliReddedilir() {
            EgitimYili pasif = TestVeriUretici.egitimYili(9L, "2019-2020", false);

            assertThatThrownBy(() -> dogrulama.tanimlarinAktifOlduguDogrula(
                    aktifBirim, aktifKategori, pasif))
                    .isInstanceOf(IsKuraliIhlaliException.class)
                    .extracting(ex -> ((IsKuraliIhlaliException) ex).getHataKodu())
                    .isEqualTo("EGITIM_YILI_PASIF");
        }

        @Test
        void hepsiAktifseGecer() {
            assertThatCode(() -> dogrulama.tanimlarinAktifOlduguDogrula(
                    aktifBirim, aktifKategori, aktifYil))
                    .doesNotThrowAnyException();
        }
    }

    // ============================================================ TÜM ZİNCİR

    @Nested
    @DisplayName("Tüm kurallar birlikte")
    class TumZincir {

        @Test
        @DisplayName("geçerli istek tüm kurallardan geçer")
        void gecerliIstekKabulEdilir() {
            assertThatCode(() -> dogrulama.kaydetmedenOnceDogrula(
                    istek(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 18)),
                    aktifBirim, aktifKategori, aktifYil, superAdmin))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("yetkisiz kullanıcı, diğer kurallar geçerli olsa bile reddedilir")
        void yetkiKontroluDeZincirdeCalisir() {
            Birim baskaBirim = TestVeriUretici.aktifBirim(2L, "Fen-Edebiyat Fakültesi");
            Kullanici birimYoneticisi =
                    TestVeriUretici.birimYoneticisi("muh.yonetici", aktifBirim);

            EtkinlikIstekDTO gecerliIstek = new EtkinlikIstekDTO("Sınav", null,
                    LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 18),
                    Donem.GUZ, 1L, 1L, 2L);

            assertThatThrownBy(() -> dogrulama.kaydetmedenOnceDogrula(
                    gecerliIstek, baskaBirim, aktifKategori, aktifYil, birimYoneticisi))
                    .isInstanceOf(YetkisizIslemException.class);
        }

        @Test
        @DisplayName("hata kodu makine tarafından okunabilir")
        void hataKoduSabittir() {
            // Ön yüz, mesaj METNİNE değil bu KODA göre dallanmalıdır;
            // metin değişse bile arayüz bozulmaz.
            IsKuraliIhlaliException hata = new IsKuraliIhlaliException("mesaj");
            assertThat(hata.getHataKodu()).isEqualTo("IS_KURALI_IHLALI");
        }
    }
}

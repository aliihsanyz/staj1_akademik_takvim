package tr.edu.akademiktakvim.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import tr.edu.akademiktakvim.TestVeriUretici;
import tr.edu.akademiktakvim.domain.Etkinlik;
import tr.edu.akademiktakvim.dto.EtkinlikGorunumDTO;
import tr.edu.akademiktakvim.dto.EtkinlikGorunumDTO.DurumTuru;

/**
 * Is analizi Bolum 5.2'deki geri sayim ozelliginin testleri.
 *
 * <p>Sabit bir {@link Clock} enjekte edilir; boylece testler takvim
 * ilerledikce kirilmaz. {@code LocalDate.now()} dogrudan cagrilsaydi bu
 * testler her gun farkli sonuc verirdi.</p>
 */
@DisplayName("EtkinlikMapper - kalan gün hesaplama")
class EtkinlikMapperTest {

    /** Testlerde "bugün" her zaman 15 Mart 2026. */
    private static final LocalDate BUGUN = LocalDate.of(2026, 3, 15);

    private EtkinlikMapper mapper;

    @BeforeEach
    void hazirla() {
        Clock sabitSaat = Clock.fixed(
                BUGUN.atStartOfDay(ZoneId.of("Europe/Istanbul")).toInstant(),
                ZoneId.of("Europe/Istanbul"));
        mapper = new EtkinlikMapper(sabitSaat);
    }

    @ParameterizedTest(name = "{0} .. {1} -> \"{2}\"")
    @CsvSource({
            // Gelecek etkinlikler
            "2026-03-25, 2026-03-27, 'Başlamasına 10 gün kaldı'",
            "2026-03-16, 2026-03-18, 'Yarın başlıyor'",
            "2026-04-15, 2026-04-20, 'Başlamasına 31 gün kaldı'",
            // Bugün
            "2026-03-15, 2026-03-15, 'Bugün'",
            "2026-03-15, 2026-03-20, 'Bugün başlıyor'",
            "2026-03-10, 2026-03-15, 'Bugün sona eriyor'",
            // Devam eden
            "2026-03-10, 2026-03-20, 'Devam ediyor (bitmesine 5 gün)'",
            // Geçmiş
            "2026-03-01, 2026-03-10, 'Üzerinden 5 gün geçti'",
            "2026-03-01, 2026-03-14, 'Dün sona erdi'",
            "2026-01-01, 2026-01-05, 'Üzerinden 69 gün geçti'",
    })
    void kalanGunMetniDogruUretilir(LocalDate baslangic, LocalDate bitis, String beklenen) {
        assertThat(mapper.kalanGunMetni(baslangic, bitis, BUGUN)).isEqualTo(beklenen);
    }

    @ParameterizedTest(name = "{0} .. {1} -> {2}")
    @CsvSource({
            "2026-03-20, 2026-03-25, GELECEK",
            "2026-03-15, 2026-03-15, DEVAM_EDIYOR",
            "2026-03-10, 2026-03-20, DEVAM_EDIYOR",
            "2026-03-15, 2026-03-20, DEVAM_EDIYOR",   // bugün başlıyor
            "2026-03-10, 2026-03-15, DEVAM_EDIYOR",   // bugün son gün
            "2026-03-01, 2026-03-14, GECMIS",
    })
    void durumDogruHesaplanir(LocalDate baslangic, LocalDate bitis, DurumTuru beklenen) {
        assertThat(mapper.durumHesapla(baslangic, bitis, BUGUN)).isEqualTo(beklenen);
    }

    @Test
    @DisplayName("bitiş günü etkinlik hâlâ devam ediyor sayılır")
    void bitisGunuDahildir() {
        // Sınav son gününde "geçmiş" yazması kullanıcıyı yanıltırdı.
        assertThat(mapper.durumHesapla(
                LocalDate.of(2026, 3, 10), BUGUN, BUGUN))
                .isEqualTo(DurumTuru.DEVAM_EDIYOR);
    }

    @Test
    @DisplayName("entity tüm alanlarıyla görünüme çevrilir")
    void tamDonusumYapilir() {
        Etkinlik etkinlik = TestVeriUretici.etkinlik(42L, "Bahar Ara Sınavları",
                LocalDate.of(2026, 4, 6), LocalDate.of(2026, 4, 17),
                TestVeriUretici.aktifBirim(1L, "Mühendislik Fakültesi"),
                TestVeriUretici.aktifKategori(2L, "Ders ve Sınav Tarihleri"),
                TestVeriUretici.aktifEgitimYili(3L));

        EtkinlikGorunumDTO gorunum = mapper.gorunumeCevir(etkinlik);

        assertThat(gorunum.id()).isEqualTo(42L);
        assertThat(gorunum.ad()).isEqualTo("Bahar Ara Sınavları");
        assertThat(gorunum.birimId()).isEqualTo(1L);
        assertThat(gorunum.birimAdi()).isEqualTo("Mühendislik Fakültesi");
        assertThat(gorunum.kategoriId()).isEqualTo(2L);
        assertThat(gorunum.egitimYiliId()).isEqualTo(3L);
        assertThat(gorunum.tekGun()).isFalse();
        assertThat(gorunum.genelBirim()).isFalse();
        assertThat(gorunum.donemEtiketi()).isEqualTo("Güz");
        assertThat(gorunum.durum()).isEqualTo(DurumTuru.GELECEK);
        assertThat(gorunum.kalanGunMetni()).isEqualTo("Başlamasına 22 gün kaldı");
    }

    @Test
    @DisplayName("Genel birim etkinliği görünümde işaretlenir")
    void genelBirimIsaretlenir() {
        Etkinlik etkinlik = TestVeriUretici.etkinlik(1L, "Resmî Tatil",
                LocalDate.of(2026, 4, 23), LocalDate.of(2026, 4, 23),
                TestVeriUretici.genelBirim(1L),
                TestVeriUretici.aktifKategori(1L, "Resmî Tatiller"),
                TestVeriUretici.aktifEgitimYili(1L));

        EtkinlikGorunumDTO gorunum = mapper.gorunumeCevir(etkinlik);

        assertThat(gorunum.genelBirim()).isTrue();
        assertThat(gorunum.tekGun()).isTrue();
    }
}

package tr.edu.akademiktakvim.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import tr.edu.akademiktakvim.TestVeriUretici;
import tr.edu.akademiktakvim.domain.Birim;
import tr.edu.akademiktakvim.domain.EgitimYili;
import tr.edu.akademiktakvim.domain.Etkinlik;
import tr.edu.akademiktakvim.domain.Kategori;
import tr.edu.akademiktakvim.domain.Kullanici;
import tr.edu.akademiktakvim.domain.enums.Donem;
import tr.edu.akademiktakvim.dto.EtkinlikIstekDTO;
import tr.edu.akademiktakvim.exception.KayitBulunamadiException;
import tr.edu.akademiktakvim.exception.YetkisizIslemException;
import tr.edu.akademiktakvim.mapper.EtkinlikMapper;
import tr.edu.akademiktakvim.repository.BirimRepository;
import tr.edu.akademiktakvim.repository.EgitimYiliRepository;
import tr.edu.akademiktakvim.repository.EtkinlikRepository;
import tr.edu.akademiktakvim.repository.KategoriRepository;

/**
 * {@link EtkinlikService} akis testleri.
 *
 * <p>Burada is kurallarinin ICERIGI degil, servisin dogru SIRAYLA dogru
 * isbirlikcileri cagirdigi sinanir. Kurallarin kendisi
 * {@code EtkinlikDogrulamaServiceTest} icinde test edilir.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("EtkinlikService")
class EtkinlikServiceTest {

    @Mock private EtkinlikRepository etkinlikRepository;
    @Mock private BirimRepository birimRepository;
    @Mock private KategoriRepository kategoriRepository;
    @Mock private EgitimYiliRepository egitimYiliRepository;
    @Mock private IslemKaydiService islemKaydiService;

    private EtkinlikService servis;

    private Birim muhendislik;
    private Birim fenEdebiyat;
    private Kategori kategori;
    private EgitimYili egitimYili;
    private Kullanici admin;

    @BeforeEach
    void hazirla() {
        YetkiKontrolService yetki = new YetkiKontrolService();
        EtkinlikDogrulamaService dogrulama = new EtkinlikDogrulamaService(yetki);
        EtkinlikMapper mapper = new EtkinlikMapper(
                Clock.system(ZoneId.of("Europe/Istanbul")));

        servis = new EtkinlikService(etkinlikRepository, birimRepository, kategoriRepository,
                egitimYiliRepository, dogrulama, yetki, islemKaydiService, mapper);

        muhendislik = TestVeriUretici.aktifBirim(1L, "Mühendislik Fakültesi");
        fenEdebiyat = TestVeriUretici.aktifBirim(2L, "Fen-Edebiyat Fakültesi");
        kategori = TestVeriUretici.aktifKategori(1L, "Ders ve Sınav Tarihleri");
        egitimYili = TestVeriUretici.aktifEgitimYili(1L);
        admin = TestVeriUretici.superAdmin("admin");

        when(birimRepository.findById(1L)).thenReturn(Optional.of(muhendislik));
        when(birimRepository.findById(2L)).thenReturn(Optional.of(fenEdebiyat));
        when(kategoriRepository.findById(1L)).thenReturn(Optional.of(kategori));
        when(egitimYiliRepository.findById(1L)).thenReturn(Optional.of(egitimYili));
    }

    private Etkinlik ornekEtkinlik(Long id, Birim birim) {
        return TestVeriUretici.etkinlik(id, "Ara Sınavlar",
                LocalDate.of(2026, 4, 6), LocalDate.of(2026, 4, 17),
                birim, kategori, egitimYili);
    }

    // ================================================================== SİLME

    @Test
    @DisplayName("silme: kayıt silinir VE işlem geçmişine yazılır")
    void silmeIslemKaydiOlusturur() {
        Etkinlik etkinlik = ornekEtkinlik(5L, muhendislik);
        when(etkinlikRepository.findByIdWithDetay(5L)).thenReturn(Optional.of(etkinlik));

        servis.sil(5L, admin);

        verify(islemKaydiService).silmeKaydet("admin", etkinlik);
        verify(etkinlikRepository).delete(etkinlik);
    }

    @Test
    @DisplayName("silme: önce işlem kaydı yazılır, SONRA silinir")
    void denetimKaydiSilmedenOnceYazilir() {
        // Sıra ters olsaydı ve denetim kaydı başarısız olsaydı, silinen kaydın
        // izi tamamen kaybolurdu. İş analizi silme geçmişinin korunmasını şart koşuyor.
        Etkinlik etkinlik = ornekEtkinlik(5L, muhendislik);
        when(etkinlikRepository.findByIdWithDetay(5L)).thenReturn(Optional.of(etkinlik));

        servis.sil(5L, admin);

        InOrder sira = Mockito.inOrder(islemKaydiService, etkinlikRepository);
        sira.verify(islemKaydiService).silmeKaydet("admin", etkinlik);
        sira.verify(etkinlikRepository).delete(etkinlik);
    }

    @Test
    @DisplayName("silme: yetkisiz kullanıcı hiçbir şey silemez")
    void yetkisizSilmeEngellenir() {
        Etkinlik baskaBirimEtkinligi = ornekEtkinlik(5L, fenEdebiyat);
        when(etkinlikRepository.findByIdWithDetay(5L))
                .thenReturn(Optional.of(baskaBirimEtkinligi));

        Kullanici birimYoneticisi =
                TestVeriUretici.birimYoneticisi("muh.yonetici", muhendislik);

        assertThatThrownBy(() -> servis.sil(5L, birimYoneticisi))
                .isInstanceOf(YetkisizIslemException.class);

        verify(etkinlikRepository, never()).delete(any(Etkinlik.class));
        verify(islemKaydiService, never()).silmeKaydet(any(), any());
    }

    @Test
    @DisplayName("silme: olmayan kayıt 404 üretir")
    void olmayanKayitSilinemez() {
        when(etkinlikRepository.findByIdWithDetay(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servis.sil(99L, admin))
                .isInstanceOf(KayitBulunamadiException.class)
                .hasMessageContaining("99");
    }

    // ================================================================ EKLEME

    @Test
    @DisplayName("ekleme: kayıt kaydedilir ve işlem geçmişine yazılır")
    void eklemeIslemKaydiOlusturur() {
        Etkinlik kaydedilen = ornekEtkinlik(10L, muhendislik);
        when(etkinlikRepository.save(any(Etkinlik.class))).thenReturn(kaydedilen);

        EtkinlikIstekDTO istek = new EtkinlikIstekDTO("Ara Sınavlar", null,
                LocalDate.of(2026, 4, 6), LocalDate.of(2026, 4, 17),
                Donem.BAHAR, 1L, 1L, 1L);

        servis.olustur(istek, admin);

        verify(etkinlikRepository).save(any(Etkinlik.class));
        verify(islemKaydiService).eklemeKaydet(eq("admin"), any(Etkinlik.class));
    }

    @Test
    @DisplayName("ekleme: iş kuralı ihlalinde HİÇBİR ŞEY kaydedilmez")
    void kuralIhlalindeKayitYapilmaz() {
        EtkinlikIstekDTO hataliIstek = new EtkinlikIstekDTO("Ara Sınavlar", null,
                LocalDate.of(2026, 4, 17), LocalDate.of(2026, 4, 6),   // ters tarih
                Donem.BAHAR, 1L, 1L, 1L);

        assertThatThrownBy(() -> servis.olustur(hataliIstek, admin))
                .isInstanceOf(tr.edu.akademiktakvim.exception.IsKuraliIhlaliException.class);

        verify(etkinlikRepository, never()).save(any());
        verify(islemKaydiService, never()).eklemeKaydet(any(), any());
    }

    // ============================================================ GÜNCELLEME

    @Test
    @DisplayName("güncelleme: kaydı erişilemeyen bir birime TAŞIMA engellenir")
    void kayitBaskaBirimeTasinamaz() {
        // Yalnızca mevcut birim kontrol edilseydi, bir Birim Yöneticisi kendi
        // kaydını başka birime taşıyıp o birimin takvimini kirletebilirdi.
        Etkinlik kendiKaydi = ornekEtkinlik(5L, muhendislik);
        when(etkinlikRepository.findByIdWithDetay(5L)).thenReturn(Optional.of(kendiKaydi));

        Kullanici birimYoneticisi =
                TestVeriUretici.birimYoneticisi("muh.yonetici", muhendislik);

        EtkinlikIstekDTO tasimaIstegi = new EtkinlikIstekDTO("Ara Sınavlar", null,
                LocalDate.of(2026, 4, 6), LocalDate.of(2026, 4, 17),
                Donem.BAHAR, 1L, 1L, 2L);   // hedef: Fen-Edebiyat

        assertThatThrownBy(() -> servis.guncelle(5L, tasimaIstegi, birimYoneticisi))
                .isInstanceOf(YetkisizIslemException.class);

        verify(etkinlikRepository, never()).save(any());
    }

    @Test
    @DisplayName("güncelleme: kendi birimi içinde serbesttir")
    void kendiBirimindeGuncellemeSerbest() {
        Etkinlik kendiKaydi = ornekEtkinlik(5L, muhendislik);
        when(etkinlikRepository.findByIdWithDetay(5L)).thenReturn(Optional.of(kendiKaydi));
        when(etkinlikRepository.save(any(Etkinlik.class))).thenReturn(kendiKaydi);

        Kullanici birimYoneticisi =
                TestVeriUretici.birimYoneticisi("muh.yonetici", muhendislik);

        EtkinlikIstekDTO istek = new EtkinlikIstekDTO("Ara Sınavlar (Güncellendi)", null,
                LocalDate.of(2026, 4, 6), LocalDate.of(2026, 4, 20),
                Donem.BAHAR, 1L, 1L, 1L);

        assertThatCode(() -> servis.guncelle(5L, istek, birimYoneticisi))
                .doesNotThrowAnyException();

        verify(etkinlikRepository).save(any(Etkinlik.class));
        verify(islemKaydiService).guncellemeKaydet(eq("muh.yonetici"), any(), any());
    }
}

package tr.edu.akademiktakvim.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tr.edu.akademiktakvim.TestVeriUretici;
import tr.edu.akademiktakvim.domain.Birim;
import tr.edu.akademiktakvim.domain.Kullanici;
import tr.edu.akademiktakvim.exception.YetkisizIslemException;

/**
 * IS KURALI 3: "Birim Yoneticisi baska birimin kayitlarini degistiremez."
 *
 * <p>Bu, sistemin en kritik guvenlik kuralidir: rol kontrolu tek basina
 * yetmez, cunku iki Birim Yoneticisi ayni role sahiptir ama farkli birimlere
 * erisir. Kontrol kayit bazlidir (row-level).</p>
 */
@DisplayName("YetkiKontrolService - İŞ KURALI 3")
class YetkiKontrolServiceTest {

    private YetkiKontrolService yetki;

    private Birim muhendislik;
    private Birim fenEdebiyat;
    private Birim genel;

    @BeforeEach
    void hazirla() {
        yetki = new YetkiKontrolService();
        muhendislik = TestVeriUretici.aktifBirim(1L, "Mühendislik Fakültesi");
        fenEdebiyat = TestVeriUretici.aktifBirim(2L, "Fen-Edebiyat Fakültesi");
        genel = TestVeriUretici.genelBirim(3L);
    }

    @Test
    @DisplayName("Süper Admin her birime erişebilir")
    void superAdminHerBirimeErisir() {
        Kullanici admin = TestVeriUretici.superAdmin("admin");

        assertThat(yetki.birimeErisebilirMi(admin, muhendislik.getId())).isTrue();
        assertThat(yetki.birimeErisebilirMi(admin, fenEdebiyat.getId())).isTrue();
        assertThat(yetki.birimeErisebilirMi(admin, genel.getId())).isTrue();
        // Var olmayan bir birim kimliği için bile rol kontrolü geçer;
        // kaydın varlığı servis katmanında ayrıca doğrulanır.
        assertThat(yetki.birimeErisebilirMi(admin, 999L)).isTrue();
    }

    @Test
    @DisplayName("Birim Yöneticisi yalnızca kendi birimine erişebilir")
    void birimYoneticisiSadeceKendiBirimine() {
        Kullanici yonetici = TestVeriUretici.birimYoneticisi("muh.yonetici", muhendislik);

        assertThat(yetki.birimeErisebilirMi(yonetici, muhendislik.getId())).isTrue();
        assertThat(yetki.birimeErisebilirMi(yonetici, fenEdebiyat.getId())).isFalse();
        assertThat(yetki.birimeErisebilirMi(yonetici, genel.getId())).isFalse();
    }

    @Test
    @DisplayName("birden fazla birime yetkili yönetici hepsine erişebilir")
    void cokluBirimYetkisi() {
        Kullanici yonetici = TestVeriUretici.birimYoneticisi(
                "coklu.yonetici", muhendislik, fenEdebiyat);

        assertThat(yetki.birimeErisebilirMi(yonetici, muhendislik.getId())).isTrue();
        assertThat(yetki.birimeErisebilirMi(yonetici, fenEdebiyat.getId())).isTrue();
        assertThat(yetki.birimeErisebilirMi(yonetici, genel.getId())).isFalse();
    }

    @Test
    @DisplayName("hiçbir birime atanmamış yönetici hiçbir şeye erişemez")
    void birimsizYoneticiErisemez() {
        Kullanici yonetici = TestVeriUretici.birimYoneticisi("bos.yonetici");

        assertThat(yetki.birimeErisebilirMi(yonetici, muhendislik.getId())).isFalse();
    }

    @Test
    @DisplayName("null kullanıcı veya null birim reddedilir")
    void nullDegerlerReddedilir() {
        Kullanici yonetici = TestVeriUretici.birimYoneticisi("muh.yonetici", muhendislik);

        assertThat(yetki.birimeErisebilirMi(null, 1L)).isFalse();
        assertThat(yetki.birimeErisebilirMi(yonetici, null)).isFalse();
        assertThat(yetki.birimeErisebilirMi(null, null)).isFalse();
    }

    @Test
    @DisplayName("erişim yoksa YetkisizIslemException fırlatılır")
    void erisimYoksaIstisnaFirlatilir() {
        Kullanici yonetici = TestVeriUretici.birimYoneticisi("muh.yonetici", muhendislik);

        assertThatThrownBy(() -> yetki.birimeErisimiDogrula(
                yonetici, fenEdebiyat.getId(), "etkinlik silme"))
                .isInstanceOf(YetkisizIslemException.class)
                .hasMessageContaining("etkinlik silme");
    }

    @Test
    @DisplayName("erişim varsa istisna fırlatılmaz")
    void erisimVarsaSessizGecer() {
        Kullanici yonetici = TestVeriUretici.birimYoneticisi("muh.yonetici", muhendislik);

        assertThatCode(() -> yetki.birimeErisimiDogrula(
                yonetici, muhendislik.getId(), "etkinlik ekleme"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Süper Admin'e özel işlemler Birim Yöneticisine kapalıdır")
    void superAdminOzelIslemleri() {
        Kullanici admin = TestVeriUretici.superAdmin("admin");
        Kullanici yonetici = TestVeriUretici.birimYoneticisi("muh.yonetici", muhendislik);

        assertThatCode(() -> yetki.superAdminDogrula(admin, "kategori ekleme"))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> yetki.superAdminDogrula(yonetici, "kategori ekleme"))
                .isInstanceOf(YetkisizIslemException.class)
                .hasMessageContaining("Süper Admin");

        assertThatThrownBy(() -> yetki.superAdminDogrula(null, "kategori ekleme"))
                .isInstanceOf(YetkisizIslemException.class);
    }
}

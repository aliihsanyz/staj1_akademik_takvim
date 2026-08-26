package tr.edu.akademiktakvim;

import java.time.LocalDate;

import org.springframework.test.util.ReflectionTestUtils;

import tr.edu.akademiktakvim.domain.Birim;
import tr.edu.akademiktakvim.domain.EgitimYili;
import tr.edu.akademiktakvim.domain.Etkinlik;
import tr.edu.akademiktakvim.domain.Kategori;
import tr.edu.akademiktakvim.domain.Kullanici;
import tr.edu.akademiktakvim.domain.enums.BirimTuru;
import tr.edu.akademiktakvim.domain.enums.Donem;
import tr.edu.akademiktakvim.domain.enums.Rol;

/**
 * Testlerde kullanilan nesneleri hazir kurar.
 *
 * <p>Entity kimlikleri veritabani tarafindan uretildigi icin setter'lari yoktur.
 * Testlerde kimlik gerektiginden {@code ReflectionTestUtils} ile atanir; bu,
 * yalnizca test amaciyla entity'ye setter eklemekten daha temiz bir yoldur.</p>
 */
public final class TestVeriUretici {

    private TestVeriUretici() {
    }

    public static Birim birim(Long id, String ad, BirimTuru tur, boolean aktif) {
        Birim birim = new Birim(ad, ad.replace(' ', '_'), tur);
        birim.setAktif(aktif);
        kimlikAta(birim, id);
        return birim;
    }

    public static Birim aktifBirim(Long id, String ad) {
        return birim(id, ad, BirimTuru.FAKULTE, true);
    }

    public static Birim genelBirim(Long id) {
        return birim(id, "Üniversite Geneli", BirimTuru.GENEL, true);
    }

    public static Kategori kategori(Long id, String ad, boolean aktif) {
        Kategori kategori = new Kategori(ad, ad.replace(' ', '_'), "#123456", 1);
        kategori.setAktif(aktif);
        kimlikAta(kategori, id);
        return kategori;
    }

    public static Kategori aktifKategori(Long id, String ad) {
        return kategori(id, ad, true);
    }

    public static EgitimYili egitimYili(Long id, String ad, boolean aktif) {
        EgitimYili yil = new EgitimYili(ad,
                LocalDate.of(2025, 9, 1), LocalDate.of(2026, 8, 31));
        yil.setAktif(aktif);
        kimlikAta(yil, id);
        return yil;
    }

    public static EgitimYili aktifEgitimYili(Long id) {
        return egitimYili(id, "2025-2026", true);
    }

    public static Kullanici superAdmin(String kullaniciAdi) {
        Kullanici k = new Kullanici(kullaniciAdi, "hash", "Süper Admin", Rol.SUPER_ADMIN);
        kimlikAta(k, 1L);
        return k;
    }

    /** Yalnizca verilen birimlere yetkili bir Birim Yoneticisi. */
    public static Kullanici birimYoneticisi(String kullaniciAdi, Birim... birimler) {
        Kullanici k = new Kullanici(kullaniciAdi, "hash", "Birim Yöneticisi", Rol.BIRIM_YONETICISI);
        kimlikAta(k, 2L);
        for (Birim b : birimler) {
            k.birimEkle(b);
        }
        return k;
    }

    public static Etkinlik etkinlik(Long id, String ad, LocalDate baslangic, LocalDate bitis,
                                    Birim birim, Kategori kategori, EgitimYili yil) {
        Etkinlik e = new Etkinlik(ad, baslangic, bitis, Donem.GUZ, yil, kategori, birim);
        kimlikAta(e, id);
        return e;
    }

    private static void kimlikAta(Object entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
    }
}

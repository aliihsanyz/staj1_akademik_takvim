package tr.edu.akademiktakvim.service;

import org.springframework.stereotype.Service;

import tr.edu.akademiktakvim.domain.Birim;
import tr.edu.akademiktakvim.domain.Kullanici;
import tr.edu.akademiktakvim.exception.YetkisizIslemException;

/**
 * IS KURALI 3: "Birim Yoneticisi baska birimin kayitlarini degistiremez."
 *
 * <p>Bu kontrol bilerek SERVIS katmaninda yapilir, controller'da degil.
 * Sebep: ayni kural hem REST uclarindan hem de PDF ice aktarma akisindan
 * gecen kayitlar icin gecerlidir. Kontrol controller'da olsaydi, ice aktarma
 * yolu kurali atlayabilirdi.</p>
 *
 * <p>Spring Security'nin rol tabanli kontrolu (hasRole) burada yetmez: rol
 * dogru olsa bile (BIRIM_YONETICISI) hangi birime dokunabildigi veriye baglidir.
 * Bu yuzden kayit bazli (row-level) yetki kontrolu gerekir.</p>
 */
@Service
public class YetkiKontrolService {

    /**
     * Kullanicinin verilen birim uzerinde islem yapip yapamayacagini soyler.
     *
     * @return Super Admin ise her zaman {@code true}; Birim Yoneticisi ise
     *         yalnizca kendisine atanmis birimler icin {@code true}
     */
    public boolean birimeErisebilirMi(Kullanici kullanici, Long birimId) {
        if (kullanici == null || birimId == null) {
            return false;
        }
        if (kullanici.superAdminMi()) {
            return true;
        }
        return kullanici.getBirimler().stream()
                .map(Birim::getId)
                .anyMatch(birimId::equals);
    }

    /**
     * Erisim yoksa {@link YetkisizIslemException} firlatir.
     * Cagiran kodun ayrica {@code if} yazmasina gerek kalmaz.
     */
    public void birimeErisimiDogrula(Kullanici kullanici, Long birimId, String islemAciklamasi) {
        if (!birimeErisebilirMi(kullanici, birimId)) {
            throw new YetkisizIslemException(
                    "Bu birim üzerinde işlem yapma yetkiniz yok: " + islemAciklamasi);
        }
    }

    /**
     * Yalnizca Super Admin'in yapabilecegi islemler icin (sistem tanimlari,
     * kullanici yonetimi, islem kayitlarini goruntuleme).
     */
    public void superAdminDogrula(Kullanici kullanici, String islemAciklamasi) {
        if (kullanici == null || !kullanici.superAdminMi()) {
            throw new YetkisizIslemException(
                    "Bu işlem yalnızca Süper Admin tarafından yapılabilir: " + islemAciklamasi);
        }
    }
}

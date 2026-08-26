package tr.edu.akademiktakvim.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tr.edu.akademiktakvim.domain.Kullanici;
import tr.edu.akademiktakvim.exception.YetkisizIslemException;
import tr.edu.akademiktakvim.repository.KullaniciRepository;

/**
 * O anda oturum acmis kullaniciyi veritabani kaydi olarak dondurur.
 *
 * <p>Spring Security'nin {@code Authentication} nesnesi yalnizca kullanici adini
 * ve rollerini tasir. Ancak yetki kontrolu icin kullanicinin HANGI BIRIMLERE
 * bagli oldugunu da bilmemiz gerekir; bu bilgi yalnizca veritabaninda vardir.
 * Bu sinif iki dunyayi birlestirir.</p>
 */
@Service
public class OturumService {

    private final KullaniciRepository kullaniciRepository;

    public OturumService(KullaniciRepository kullaniciRepository) {
        this.kullaniciRepository = kullaniciRepository;
    }

    /**
     * Oturum acmis kullaniciyi dondurur.
     *
     * @throws YetkisizIslemException oturum yoksa veya kullanici pasiflestirilmisse
     */
    @Transactional(readOnly = true)
    public Kullanici gecerliKullanici() {
        Authentication kimlik = SecurityContextHolder.getContext().getAuthentication();

        if (kimlik == null || !kimlik.isAuthenticated() || "anonymousUser".equals(kimlik.getPrincipal())) {
            throw new YetkisizIslemException("Bu işlem için oturum açmanız gerekiyor.");
        }

        Kullanici kullanici = kullaniciRepository.findByKullaniciAdi(kimlik.getName())
                .orElseThrow(() -> new YetkisizIslemException(
                        "Oturum açan kullanıcı sistemde bulunamadı: " + kimlik.getName()));

        // Kullanici oturum acikken pasiflestirilmis olabilir; her istekte kontrol edilir.
        if (!kullanici.isAktif()) {
            throw new YetkisizIslemException("Kullanıcı hesabınız pasif durumda.");
        }

        return kullanici;
    }
}

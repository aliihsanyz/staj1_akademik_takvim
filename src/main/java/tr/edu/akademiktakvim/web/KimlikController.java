package tr.edu.akademiktakvim.web;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import tr.edu.akademiktakvim.domain.Birim;
import tr.edu.akademiktakvim.domain.Kullanici;
import tr.edu.akademiktakvim.exception.IsKuraliIhlaliException;
import tr.edu.akademiktakvim.repository.KullaniciRepository;
import tr.edu.akademiktakvim.service.OturumService;

/**
 * Yonetim paneline giris / cikis ve oturum bilgisi uclari.
 */
@RestController
@RequestMapping("/api/kimlik")
public class KimlikController {

    private final AuthenticationManager kimlikYoneticisi;
    private final OturumService oturumService;
    private final KullaniciRepository kullaniciRepository;

    /**
     * Oturumu HTTP oturumuna yazmak icin gerekir.
     *
     * <p>Spring Security 6'da {@code SecurityContextHolder}'a yazmak TEK BASINA
     * yetmez; baglam acikca depoya kaydedilmezse sonraki istekte oturum
     * kaybolur. Bu, elle giris ucu yazarken en sik yapilan hatadir.</p>
     */
    private final SecurityContextRepository baglamDeposu = new HttpSessionSecurityContextRepository();

    public KimlikController(AuthenticationManager kimlikYoneticisi,
                            OturumService oturumService,
                            KullaniciRepository kullaniciRepository) {
        this.kimlikYoneticisi = kimlikYoneticisi;
        this.oturumService = oturumService;
        this.kullaniciRepository = kullaniciRepository;
    }

    /** Giris istegi govdesi. */
    public record GirisIstegi(
            @NotBlank(message = "Kullanıcı adı boş bırakılamaz") String kullaniciAdi,
            @NotBlank(message = "Şifre boş bırakılamaz") String sifre) {
    }

    /** Oturum acan kullanicinin on yuze donen bilgisi. */
    public record OturumBilgisi(String kullaniciAdi, String adSoyad, String rol,
                                String rolEtiketi, boolean superAdmin,
                                List<Map<String, Object>> birimler) {
    }

    /**
     * Kullanici adi ve sifre ile giris yapar.
     *
     * <p>Basarili olursa oturum cerezi ({@code JSESSIONID}) yanitla birlikte
     * gonderilir; sonraki isteklerde tarayici bunu otomatik tasir.</p>
     */
    @PostMapping("/giris")
    public OturumBilgisi giris(@Valid @RequestBody GirisIstegi istek,
                               HttpServletRequest httpIstek,
                               HttpServletResponse httpYanit) {
        Authentication kimlik;
        try {
            kimlik = kimlikYoneticisi.authenticate(
                    new UsernamePasswordAuthenticationToken(istek.kullaniciAdi(), istek.sifre()));
        } catch (DisabledException ex) {
            throw new IsKuraliIhlaliException("Kullanıcı hesabınız pasif durumda.", "HESAP_PASIF");
        } catch (BadCredentialsException ex) {
            // Kullanici adinin var olup olmadigi bilgisi sizdirilmez
            throw new IsKuraliIhlaliException("Kullanıcı adı veya şifre hatalı.", "GIRIS_BASARISIZ");
        }

        // Oturum sabitleme (session fixation) saldirisina karsi: giristen once
        // varsa eski oturumu gecersiz kil, yeni kimlik yeni oturumda tasinsin.
        HttpSession eskiOturum = httpIstek.getSession(false);
        if (eskiOturum != null) {
            eskiOturum.invalidate();
        }

        SecurityContext baglam = SecurityContextHolder.createEmptyContext();
        baglam.setAuthentication(kimlik);
        SecurityContextHolder.setContext(baglam);
        baglamDeposu.saveContext(baglam, httpIstek, httpYanit);

        Kullanici kullanici = kullaniciRepository.findByKullaniciAdi(istek.kullaniciAdi())
                .orElseThrow(() -> new IsKuraliIhlaliException("Kullanıcı bulunamadı."));
        kullanici.setSonGiris(LocalDateTime.now());
        kullaniciRepository.save(kullanici);

        return oturumBilgisineCevir(kullanici);
    }

    /** Oturumu sonlandirir. */
    @PostMapping("/cikis")
    public Map<String, String> cikis(HttpServletRequest httpIstek) {
        HttpSession oturum = httpIstek.getSession(false);
        if (oturum != null) {
            oturum.invalidate();
        }
        SecurityContextHolder.clearContext();
        return Map.of("mesaj", "Çıkış yapıldı.");
    }

    /**
     * Oturum acan kullanicinin bilgisi.
     *
     * <p>On yuz sayfa acilisinda bunu cagirir: 200 donerse yonetim panelini,
     * 401 donerse giris formunu gosterir.</p>
     */
    @GetMapping("/ben")
    public OturumBilgisi ben() {
        return oturumBilgisineCevir(oturumService.gecerliKullanici());
    }

    private OturumBilgisi oturumBilgisineCevir(Kullanici k) {
        List<Map<String, Object>> birimler = k.getBirimler().stream()
                .map(this::birimOzeti)
                .toList();

        return new OturumBilgisi(
                k.getKullaniciAdi(),
                k.getAdSoyad(),
                k.getRol().name(),
                k.getRol().getEtiket(),
                k.superAdminMi(),
                birimler);
    }

    private Map<String, Object> birimOzeti(Birim b) {
        return Map.of("id", b.getId(), "ad", b.getAd());
    }
}

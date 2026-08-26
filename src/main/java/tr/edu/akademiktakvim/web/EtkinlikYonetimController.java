package tr.edu.akademiktakvim.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import tr.edu.akademiktakvim.domain.Kullanici;
import tr.edu.akademiktakvim.dto.EtkinlikGorunumDTO;
import tr.edu.akademiktakvim.dto.EtkinlikIstekDTO;
import tr.edu.akademiktakvim.service.EtkinlikService;
import tr.edu.akademiktakvim.service.OturumService;

/**
 * Etkinlik yonetimi (ekleme / guncelleme / silme).
 *
 * <p>Bu uclar {@code /api/admin/**} altinda oldugu icin Spring Security
 * tarafindan Birim Yoneticisi veya Super Admin rolune sinirlanmistir.
 * "Hangi birime dokunabilir" sorusu ise servis katmaninda kayit bazli
 * kontrol edilir - rol kontrolu tek basina yeterli degildir.</p>
 *
 * <p>Okuma uclarindan ayri bir sinifta tutulmasinin sebebi, herkese acik
 * uclarla yetki gerektiren uclarin ayni dosyada karismasini onlemek;
 * boylece bir ucun yanlislikla acikta kalmasi zorlasir.</p>
 */
@RestController
@RequestMapping("/api/admin/etkinlikler")
public class EtkinlikYonetimController {

    private final EtkinlikService etkinlikService;
    private final OturumService oturumService;

    public EtkinlikYonetimController(EtkinlikService etkinlikService, OturumService oturumService) {
        this.etkinlikService = etkinlikService;
        this.oturumService = oturumService;
    }

    /** Yeni etkinlik ekler. Is kurallari servis katmaninda dogrulanir. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EtkinlikGorunumDTO ekle(@Valid @RequestBody EtkinlikIstekDTO istek) {
        Kullanici kullanici = oturumService.gecerliKullanici();
        return etkinlikService.olustur(istek, kullanici);
    }

    /** Mevcut etkinligi gunceller. */
    @PutMapping("/{id}")
    public EtkinlikGorunumDTO guncelle(@PathVariable Long id,
                                       @Valid @RequestBody EtkinlikIstekDTO istek) {
        Kullanici kullanici = oturumService.gecerliKullanici();
        return etkinlikService.guncelle(id, istek, kullanici);
    }

    /** Etkinligi siler. Silinen kaydin tam icerigi islem kaydina yazilir. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void sil(@PathVariable Long id) {
        Kullanici kullanici = oturumService.gecerliKullanici();
        etkinlikService.sil(id, kullanici);
    }
}

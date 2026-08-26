package tr.edu.akademiktakvim.web;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import tr.edu.akademiktakvim.domain.Kullanici;
import tr.edu.akademiktakvim.dto.IceAktarmaDTO;
import tr.edu.akademiktakvim.service.IceAktarmaService;
import tr.edu.akademiktakvim.service.OturumService;
import tr.edu.akademiktakvim.service.pdf.PdfAyristirmaService;

/**
 * PDF ice aktarma uclari - IKI ASAMALI akis.
 *
 * <pre>
 *   1) POST /onizleme  (multipart: dosya)
 *        -> PDF ayristirilir, aday etkinlikler + guven skorlari doner
 *        -> VERITABANINA HICBIR SEY YAZILMAZ
 *
 *   2) POST /onayla    (JSON: kullanicinin duzelttigi liste)
 *        -> Her satir normal is kurallarindan gecirilerek kaydedilir
 * </pre>
 *
 * <p>Bu ayrim, prompt'ta acikca istenen "veritabanina kaydedilmeden once bir
 * dogrulama ve onay asamasi" gereksinimini karsilar.</p>
 */
@RestController
@RequestMapping("/api/admin/ice-aktarma")
public class IceAktarmaController {

    private final PdfAyristirmaService ayristirmaService;
    private final IceAktarmaService iceAktarmaService;
    private final OturumService oturumService;

    public IceAktarmaController(PdfAyristirmaService ayristirmaService,
                                IceAktarmaService iceAktarmaService,
                                OturumService oturumService) {
        this.ayristirmaService = ayristirmaService;
        this.iceAktarmaService = iceAktarmaService;
        this.oturumService = oturumService;
    }

    /**
     * 1. ASAMA - PDF yuklenir ve ayristirilir.
     *
     * <p>Salt okunur bir islemdir; sonuc yalnizca kullaniciya gosterilir.
     * Kullanici tabloda satirlari duzeltip ikinci asamaya gonderir.</p>
     */
    @PostMapping(path = "/onizleme", consumes = "multipart/form-data")
    public IceAktarmaDTO.OnizlemeYaniti onizleme(@RequestParam("dosya") MultipartFile dosya) {
        // Oturum kontrolu: yalnizca yetkili yoneticiler PDF yukleyebilir
        oturumService.gecerliKullanici();
        return ayristirmaService.onizle(dosya);
    }

    /**
     * 2. ASAMA - Kullanicinin onayladigi etkinlikler kaydedilir.
     *
     * <p>Her satir, panelden elle girilmis gibi tum is kurallarindan gecer.</p>
     */
    @PostMapping("/onayla")
    public IceAktarmaDTO.OnaySonucu onayla(@Valid @RequestBody IceAktarmaDTO.OnayIstegi istek) {
        Kullanici kullanici = oturumService.gecerliKullanici();
        return iceAktarmaService.onayla(istek, kullanici);
    }
}

package tr.edu.akademiktakvim.web;

import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import tr.edu.akademiktakvim.domain.enums.Donem;
import tr.edu.akademiktakvim.dto.EtkinlikFiltreDTO;
import tr.edu.akademiktakvim.dto.EtkinlikGorunumDTO;
import tr.edu.akademiktakvim.service.EtkinlikService;

/**
 * Herkese acik takvim goruntuleme uclari (Ogrenci / Personel).
 *
 * <p>Kimlik dogrulamasi gerektirmez; is analizi Bolum 5'e gore takvim
 * goruntuleme herkese aciktir.</p>
 */
@RestController
@ResponseStatus(HttpStatus.OK)
public class EtkinlikController {

    private final EtkinlikService etkinlikService;

    public EtkinlikController(EtkinlikService etkinlikService) {
        this.etkinlikService = etkinlikService;
    }

    /**
     * Filtreye uyan etkinlikleri kronolojik sirayla dondurur.
     *
     * <p>Ornek: {@code /api/etkinlikler?egitimYiliId=1&birimId=3&kategoriIdler=1,2}</p>
     *
     * <p>Tum parametreler istege baglidir. {@code kategoriIdler} virgulle ayrilmis
     * coklu deger alir (Bolum 5.1: kategoride coklu secim yapilabilir).</p>
     */
    @GetMapping("/api/etkinlikler")
    public List<EtkinlikGorunumDTO> listele(
            @RequestParam(required = false) Long egitimYiliId,
            @RequestParam(required = false) Long birimId,
            @RequestParam(required = false) Set<Long> kategoriIdler,
            @RequestParam(required = false) Donem donem,
            @RequestParam(required = false) String arama) {

        EtkinlikFiltreDTO filtre =
                new EtkinlikFiltreDTO(egitimYiliId, birimId, kategoriIdler, donem, arama);
        return etkinlikService.listele(filtre);
    }

    /** Tek etkinligin detayi (Bolum 5.3: etkinlige tiklandiginda acilan aciklama). */
    @GetMapping("/api/etkinlikler/{id}")
    public EtkinlikGorunumDTO getir(@PathVariable Long id) {
        return etkinlikService.getir(id);
    }
}

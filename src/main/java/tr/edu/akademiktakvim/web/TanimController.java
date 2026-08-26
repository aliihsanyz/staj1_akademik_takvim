package tr.edu.akademiktakvim.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import tr.edu.akademiktakvim.dto.TanimDTO;
import tr.edu.akademiktakvim.service.OturumService;
import tr.edu.akademiktakvim.service.TanimService;

/**
 * Sistem tanimlari: birim, kategori, egitim yili.
 *
 * <p>Iki farkli yol onekiyle sunulur ve bu bilincli bir ayrimdir:</p>
 * <ul>
 *   <li>{@code /api/tanimlar/**} - HERKESE ACIK okuma. On yuzun filtre
 *       listelerini doldurmasi icin gereklidir ve yalnizca AKTIF kayitlari doner.</li>
 *   <li>{@code /api/admin/tanimlar/**} - yalnizca SUPER ADMIN. Yazma islemleri
 *       ve pasif kayitlarin da gorulebildigi tam liste.</li>
 * </ul>
 */
@RestController
public class TanimController {

    private final TanimService tanimService;
    private final OturumService oturumService;

    public TanimController(TanimService tanimService, OturumService oturumService) {
        this.tanimService = tanimService;
        this.oturumService = oturumService;
    }

    // ================================================== HERKESE ACIK (yalnizca aktifler)

    /** Takvim ekranindaki birim listesi. Yalnizca aktif birimler doner. */
    @GetMapping("/api/tanimlar/birimler")
    public List<TanimDTO.BirimGorunum> acikBirimler() {
        return tanimService.birimleriListele(true);
    }

    /** Takvim ekranindaki kategori cipleri. Yalnizca aktif kategoriler doner. */
    @GetMapping("/api/tanimlar/kategoriler")
    public List<TanimDTO.KategoriGorunum> acikKategoriler() {
        return tanimService.kategorileriListele(true);
    }

    /** Ust cubuktaki egitim yili secici. Yalnizca aktif yillar doner. */
    @GetMapping("/api/tanimlar/egitim-yillari")
    public List<TanimDTO.EgitimYiliGorunum> acikEgitimYillari() {
        return tanimService.egitimYillariniListele(true);
    }

    // ============================================================ BIRIM YONETIMI

    /**
     * @param yalnizcaAktif varsayilan {@code false}: yonetim panelinde pasif
     *                      kayitlar da gorunmelidir ki tekrar aktife alinabilsinler.
     */
    @GetMapping("/api/admin/tanimlar/birimler")
    public List<TanimDTO.BirimGorunum> birimler(
            @RequestParam(defaultValue = "false") boolean yalnizcaAktif) {
        return tanimService.birimleriListele(yalnizcaAktif);
    }

    @PostMapping("/api/admin/tanimlar/birimler")
    @ResponseStatus(HttpStatus.CREATED)
    public TanimDTO.BirimGorunum birimEkle(@Valid @RequestBody TanimDTO.BirimIstek istek) {
        return tanimService.birimEkle(istek, kullaniciAdi());
    }

    @PutMapping("/api/admin/tanimlar/birimler/{id}")
    public TanimDTO.BirimGorunum birimGuncelle(@PathVariable Long id,
                                               @Valid @RequestBody TanimDTO.BirimIstek istek) {
        return tanimService.birimGuncelle(id, istek, kullaniciAdi());
    }

    @DeleteMapping("/api/admin/tanimlar/birimler/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void birimSil(@PathVariable Long id) {
        tanimService.birimSil(id, kullaniciAdi());
    }

    // ========================================================= KATEGORI YONETIMI

    @GetMapping("/api/admin/tanimlar/kategoriler")
    public List<TanimDTO.KategoriGorunum> kategoriler(
            @RequestParam(defaultValue = "false") boolean yalnizcaAktif) {
        return tanimService.kategorileriListele(yalnizcaAktif);
    }

    @PostMapping("/api/admin/tanimlar/kategoriler")
    @ResponseStatus(HttpStatus.CREATED)
    public TanimDTO.KategoriGorunum kategoriEkle(@Valid @RequestBody TanimDTO.KategoriIstek istek) {
        return tanimService.kategoriEkle(istek, kullaniciAdi());
    }

    @PutMapping("/api/admin/tanimlar/kategoriler/{id}")
    public TanimDTO.KategoriGorunum kategoriGuncelle(@PathVariable Long id,
                                                     @Valid @RequestBody TanimDTO.KategoriIstek istek) {
        return tanimService.kategoriGuncelle(id, istek, kullaniciAdi());
    }

    @DeleteMapping("/api/admin/tanimlar/kategoriler/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void kategoriSil(@PathVariable Long id) {
        tanimService.kategoriSil(id, kullaniciAdi());
    }

    // ====================================================== EGITIM YILI YONETIMI

    @GetMapping("/api/admin/tanimlar/egitim-yillari")
    public List<TanimDTO.EgitimYiliGorunum> egitimYillari(
            @RequestParam(defaultValue = "false") boolean yalnizcaAktif) {
        return tanimService.egitimYillariniListele(yalnizcaAktif);
    }

    @PostMapping("/api/admin/tanimlar/egitim-yillari")
    @ResponseStatus(HttpStatus.CREATED)
    public TanimDTO.EgitimYiliGorunum egitimYiliEkle(
            @Valid @RequestBody TanimDTO.EgitimYiliIstek istek) {
        return tanimService.egitimYiliEkle(istek, kullaniciAdi());
    }

    @PutMapping("/api/admin/tanimlar/egitim-yillari/{id}")
    public TanimDTO.EgitimYiliGorunum egitimYiliGuncelle(
            @PathVariable Long id, @Valid @RequestBody TanimDTO.EgitimYiliIstek istek) {
        return tanimService.egitimYiliGuncelle(id, istek, kullaniciAdi());
    }

    @DeleteMapping("/api/admin/tanimlar/egitim-yillari/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void egitimYiliSil(@PathVariable Long id) {
        tanimService.egitimYiliSil(id, kullaniciAdi());
    }

    // ------------------------------------------------------------- yardimcilar

    private String kullaniciAdi() {
        return oturumService.gecerliKullanici().getKullaniciAdi();
    }
}

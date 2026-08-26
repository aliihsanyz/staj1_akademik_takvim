package tr.edu.akademiktakvim.service;

import java.time.LocalDate;

import org.springframework.stereotype.Service;

import tr.edu.akademiktakvim.domain.Birim;
import tr.edu.akademiktakvim.domain.EgitimYili;
import tr.edu.akademiktakvim.domain.Kategori;
import tr.edu.akademiktakvim.domain.Kullanici;
import tr.edu.akademiktakvim.dto.EtkinlikIstekDTO;
import tr.edu.akademiktakvim.exception.IsKuraliIhlaliException;

/**
 * Etkinlik is kurallarinin TEK dogrulama noktasi.
 *
 * <p>Is analizi Bolum 7'deki kurallar burada toplandi. Bu sinifin ayri
 * tutulmasinin sebebi, ayni kurallarin iki farkli yoldan gecen veriye
 * uygulanmasi gerekmesidir:</p>
 * <ol>
 *   <li>Yonetim panelinden elle girilen etkinlik</li>
 *   <li>PDF ice aktarma sonrasi onaylanan etkinlik</li>
 * </ol>
 *
 * <p>Kurallar {@code EtkinlikService} icine gomulseydi, ice aktarma yolu
 * bir kismini atlayabilirdi. Tek sinifta toplamak ayni zamanda birim
 * testlerini veritabani olmadan yazilabilir kilar.</p>
 */
@Service
public class EtkinlikDogrulamaService {

    private final YetkiKontrolService yetkiKontrolService;

    public EtkinlikDogrulamaService(YetkiKontrolService yetkiKontrolService) {
        this.yetkiKontrolService = yetkiKontrolService;
    }

    /**
     * Bir etkinlik kaydedilmeden once tum is kurallarini sirayla uygular.
     *
     * @param istek      kullanicidan gelen veri
     * @param birim      cozulmus birim kaydi
     * @param kategori   cozulmus kategori kaydi
     * @param egitimYili cozulmus egitim yili kaydi
     * @param kullanici  islemi yapan yonetici
     * @throws IsKuraliIhlaliException ilk ihlal edilen kuralda
     */
    public void kaydetmedenOnceDogrula(EtkinlikIstekDTO istek, Birim birim, Kategori kategori,
                                       EgitimYili egitimYili, Kullanici kullanici) {
        zorunluAlanlariDogrula(istek);
        tarihSirasiniDogrula(istek.baslangicTarihi(), istek.bitisTarihi());
        tanimlarinAktifOlduguDogrula(birim, kategori, egitimYili);
        yetkiKontrolService.birimeErisimiDogrula(kullanici, birim.getId(),
                "etkinlik kaydi (" + birim.getAd() + ")");
    }

    // ------------------------------------------------------------- IS KURALI 1

    /**
     * IS KURALI 1: "Etkinlik adi, tarih, donem, kategori ve birim bilgileri
     * bos birakilamaz."
     *
     * <p>DTO uzerindeki {@code @NotBlank}/{@code @NotNull} anotasyonlari REST
     * yolunda bu isi zaten yapar; ancak PDF ice aktarma akisinda nesneler
     * programatik olarak uretildigi icin ayni kontrol burada da tekrarlanir.</p>
     */
    public void zorunluAlanlariDogrula(EtkinlikIstekDTO istek) {
        if (istek.ad() == null || istek.ad().isBlank()) {
            throw new IsKuraliIhlaliException("Etkinlik adı boş bırakılamaz.", "AD_BOS");
        }
        if (istek.baslangicTarihi() == null) {
            throw new IsKuraliIhlaliException("Başlangıç tarihi boş bırakılamaz.", "BASLANGIC_BOS");
        }
        if (istek.bitisTarihi() == null) {
            throw new IsKuraliIhlaliException("Bitiş tarihi boş bırakılamaz.", "BITIS_BOS");
        }
        if (istek.donem() == null) {
            throw new IsKuraliIhlaliException("Dönem boş bırakılamaz.", "DONEM_BOS");
        }
        if (istek.kategoriId() == null) {
            throw new IsKuraliIhlaliException("Kategori boş bırakılamaz.", "KATEGORI_BOS");
        }
        if (istek.birimId() == null) {
            throw new IsKuraliIhlaliException("Birim boş bırakılamaz.", "BIRIM_BOS");
        }
        if (istek.egitimYiliId() == null) {
            throw new IsKuraliIhlaliException("Eğitim yılı boş bırakılamaz.", "EGITIM_YILI_BOS");
        }
    }

    // ------------------------------------------------------------- IS KURALI 2

    /**
     * IS KURALI 2: "Bitis tarihi, baslangic tarihinden once olamaz."
     *
     * <p>Esitlik gecerlidir; tek gunluk etkinliklerde baslangic ve bitis ayni gundur.</p>
     */
    public void tarihSirasiniDogrula(LocalDate baslangic, LocalDate bitis) {
        if (baslangic == null || bitis == null) {
            return; // Bos alan kontrolu zorunluAlanlariDogrula icinde yapilir
        }
        if (bitis.isBefore(baslangic)) {
            throw new IsKuraliIhlaliException(
                    "Bitiş tarihi başlangıç tarihinden önce olamaz. "
                            + "(Başlangıç: " + baslangic + ", Bitiş: " + bitis + ")",
                    "TARIH_SIRASI_HATALI");
        }
    }

    // ------------------------------------------------------------- IS KURALI 4

    /**
     * IS KURALI 4: "Pasif hale getirilen birim, egitim yili veya kategori
     * yeni kayitlarda secilemez."
     *
     * <p>Not: Mevcut kayitlar bozulmaz. Pasiflestirme yalnizca YENI secimleri
     * engeller; gecmis takvim verisi oldugu gibi durur.</p>
     */
    public void tanimlarinAktifOlduguDogrula(Birim birim, Kategori kategori, EgitimYili egitimYili) {
        if (!birim.isAktif()) {
            throw new IsKuraliIhlaliException(
                    "Pasif durumdaki birim yeni kayıtlarda seçilemez: " + birim.getAd(),
                    "BIRIM_PASIF");
        }
        if (!kategori.isAktif()) {
            throw new IsKuraliIhlaliException(
                    "Pasif durumdaki kategori yeni kayıtlarda seçilemez: " + kategori.getAd(),
                    "KATEGORI_PASIF");
        }
        if (!egitimYili.isAktif()) {
            throw new IsKuraliIhlaliException(
                    "Pasif durumdaki eğitim yılı yeni kayıtlarda seçilemez: " + egitimYili.getAd(),
                    "EGITIM_YILI_PASIF");
        }
    }
}

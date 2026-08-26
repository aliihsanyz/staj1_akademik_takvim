package tr.edu.akademiktakvim.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tr.edu.akademiktakvim.domain.Birim;
import tr.edu.akademiktakvim.domain.EgitimYili;
import tr.edu.akademiktakvim.domain.Etkinlik;
import tr.edu.akademiktakvim.domain.Kategori;
import tr.edu.akademiktakvim.domain.Kullanici;
import tr.edu.akademiktakvim.domain.enums.BirimTuru;
import tr.edu.akademiktakvim.dto.EtkinlikFiltreDTO;
import tr.edu.akademiktakvim.dto.EtkinlikGorunumDTO;
import tr.edu.akademiktakvim.dto.EtkinlikIstekDTO;
import tr.edu.akademiktakvim.exception.KayitBulunamadiException;
import tr.edu.akademiktakvim.mapper.EtkinlikMapper;
import tr.edu.akademiktakvim.repository.BirimRepository;
import tr.edu.akademiktakvim.repository.EgitimYiliRepository;
import tr.edu.akademiktakvim.repository.EtkinlikRepository;
import tr.edu.akademiktakvim.repository.EtkinlikSpecification;
import tr.edu.akademiktakvim.repository.KategoriRepository;

/**
 * Etkinlik is mantiginin merkezi.
 *
 * <p>Sorumluluk dagilimi bilerek boyle kuruldu:</p>
 * <ul>
 *   <li>Bu sinif: akis yonetimi (bul, dogrula, kaydet, denetime yaz)</li>
 *   <li>{@link EtkinlikDogrulamaService}: is kurallari</li>
 *   <li>{@link IslemKaydiService}: denetim kaydi</li>
 *   <li>{@link EtkinlikMapper}: DTO donusumu ve geri sayim</li>
 * </ul>
 * Boylece her sinif tek bir sebeple degisir (Tek Sorumluluk Ilkesi).
 */
@Service
public class EtkinlikService {

    /** IS KURALI 5: etkinlikler her zaman kronolojik listelenir. */
    private static final Sort KRONOLOJIK_SIRA =
            Sort.by(Sort.Order.asc("baslangicTarihi"), Sort.Order.asc("ad"));

    private final EtkinlikRepository etkinlikRepository;
    private final BirimRepository birimRepository;
    private final KategoriRepository kategoriRepository;
    private final EgitimYiliRepository egitimYiliRepository;
    private final EtkinlikDogrulamaService dogrulamaService;
    private final YetkiKontrolService yetkiKontrolService;
    private final IslemKaydiService islemKaydiService;
    private final EtkinlikMapper mapper;

    public EtkinlikService(EtkinlikRepository etkinlikRepository,
                           BirimRepository birimRepository,
                           KategoriRepository kategoriRepository,
                           EgitimYiliRepository egitimYiliRepository,
                           EtkinlikDogrulamaService dogrulamaService,
                           YetkiKontrolService yetkiKontrolService,
                           IslemKaydiService islemKaydiService,
                           EtkinlikMapper mapper) {
        this.etkinlikRepository = etkinlikRepository;
        this.birimRepository = birimRepository;
        this.kategoriRepository = kategoriRepository;
        this.egitimYiliRepository = egitimYiliRepository;
        this.dogrulamaService = dogrulamaService;
        this.yetkiKontrolService = yetkiKontrolService;
        this.islemKaydiService = islemKaydiService;
        this.mapper = mapper;
    }

    // ------------------------------------------------------------------ OKUMA

    /**
     * Filtreye uyan etkinlikleri kronolojik sirayla dondurur.
     *
     * <p>Sayfalama yok: bir egitim yilina ait takvim en fazla birkac yuz satirdir
     * ve on yuz bunlari aylara gore gruplayarak tek sayfada gosterir. Sayfalama
     * eklemek, ay gruplamasini bolerek arayuzu bozardi.</p>
     */
    @Transactional(readOnly = true)
    public List<EtkinlikGorunumDTO> listele(EtkinlikFiltreDTO filtre) {
        Long genelBirimId = genelBirimIdBul();
        List<Etkinlik> etkinlikler = etkinlikRepository.findAll(
                EtkinlikSpecification.filtrele(filtre, genelBirimId), KRONOLOJIK_SIRA);

        return etkinlikler.stream().map(mapper::gorunumeCevir).toList();
    }

    /** Tek etkinligi detayiyla getirir (etkinlik detayi ve .ics indirme icin). */
    @Transactional(readOnly = true)
    public EtkinlikGorunumDTO getir(Long id) {
        return mapper.gorunumeCevir(etkinlikBul(id));
    }

    /** PDF ve ICS uretimi entity uzerinden calistigi icin ayri bir okuma metodu. */
    @Transactional(readOnly = true)
    public List<Etkinlik> entityListele(EtkinlikFiltreDTO filtre) {
        Long genelBirimId = genelBirimIdBul();
        List<Etkinlik> ozetler = etkinlikRepository.findAll(
                EtkinlikSpecification.filtrele(filtre, genelBirimId), KRONOLOJIK_SIRA);

        if (ozetler.isEmpty()) {
            return List.of();
        }
        // Iliskileri tek sorguda yukle (N+1 onlemi)
        List<Long> idler = ozetler.stream().map(Etkinlik::getId).toList();
        return etkinlikRepository.findAllByIdWithDetay(idler);
    }

    @Transactional(readOnly = true)
    public Etkinlik entityGetir(Long id) {
        return etkinlikBul(id);
    }

    // ----------------------------------------------------------------- YAZMA

    /**
     * Yeni etkinlik olusturur.
     *
     * <p>Sira onemlidir: once ilgili tanimlar cozulur, sonra TUM is kurallari
     * dogrulanir, ancak ondan sonra kayit yapilir. Boylece yarim kalmis
     * kayit olusmaz.</p>
     */
    @Transactional
    public EtkinlikGorunumDTO olustur(EtkinlikIstekDTO istek, Kullanici kullanici) {
        Birim birim = birimBul(istek.birimId());
        Kategori kategori = kategoriBul(istek.kategoriId());
        EgitimYili egitimYili = egitimYiliBul(istek.egitimYiliId());

        dogrulamaService.kaydetmedenOnceDogrula(istek, birim, kategori, egitimYili, kullanici);

        Etkinlik etkinlik = new Etkinlik(istek.ad().trim(), istek.baslangicTarihi(),
                istek.bitisTarihi(), istek.donem(), egitimYili, kategori, birim);
        etkinlik.setAciklama(bosluklariTemizle(istek.aciklama()));
        etkinlik.setOlusturan(kullanici.getKullaniciAdi());

        Etkinlik kaydedilen = etkinlikRepository.save(etkinlik);
        islemKaydiService.eklemeKaydet(kullanici.getKullaniciAdi(), kaydedilen);

        return mapper.gorunumeCevir(kaydedilen);
    }

    /**
     * Mevcut etkinligi gunceller.
     *
     * <p>Yetki iki kez kontrol edilir ve bu bilincli bir karardir:</p>
     * <ol>
     *   <li>Kaydin MEVCUT birimi uzerinde yetki var mi? (baskasinin kaydina dokunamasin)</li>
     *   <li>Kaydin YENI birimi uzerinde yetki var mi? (kaydi erisemeyecegi bir
     *       birime tasiyip kacirmasin)</li>
     * </ol>
     * Yalnizca birincisi kontrol edilseydi, bir Birim Yoneticisi kendi kaydini
     * baska bir birime tasiyabilir ve o birimin takvimini kirletebilirdi.
     */
    @Transactional
    public EtkinlikGorunumDTO guncelle(Long id, EtkinlikIstekDTO istek, Kullanici kullanici) {
        Etkinlik mevcut = etkinlikBul(id);

        // (1) Mevcut kayit uzerinde yetki
        yetkiKontrolService.birimeErisimiDogrula(
                kullanici, mevcut.getBirim().getId(),
                "mevcut kayit (" + mevcut.getBirim().getAd() + ")");

        Map<String, Object> eskiDeger = denetimOzeti(mevcut);

        Birim yeniBirim = birimBul(istek.birimId());
        Kategori kategori = kategoriBul(istek.kategoriId());
        EgitimYili egitimYili = egitimYiliBul(istek.egitimYiliId());

        // (2) Hedef birim uzerinde yetki + diger tum is kurallari
        dogrulamaService.kaydetmedenOnceDogrula(istek, yeniBirim, kategori, egitimYili, kullanici);

        mevcut.setAd(istek.ad().trim());
        mevcut.setAciklama(bosluklariTemizle(istek.aciklama()));
        mevcut.setBaslangicTarihi(istek.baslangicTarihi());
        mevcut.setBitisTarihi(istek.bitisTarihi());
        mevcut.setDonem(istek.donem());
        mevcut.setEgitimYili(egitimYili);
        mevcut.setKategori(kategori);
        mevcut.setBirim(yeniBirim);
        mevcut.setGuncelleyen(kullanici.getKullaniciAdi());

        Etkinlik kaydedilen = etkinlikRepository.save(mevcut);
        islemKaydiService.guncellemeKaydet(kullanici.getKullaniciAdi(), eskiDeger, kaydedilen);

        return mapper.gorunumeCevir(kaydedilen);
    }

    /**
     * Etkinligi siler.
     *
     * <p>Kalici (hard) silme yapilir; ancak silmeden ONCE kaydin tamami denetim
     * gecmisine yazilir. Is analizi Bolum 12: "Kayit veritabanindan kaldirilir,
     * ancak islem gecmisinde silme kaydi tutulur."</p>
     */
    @Transactional
    public void sil(Long id, Kullanici kullanici) {
        Etkinlik etkinlik = etkinlikBul(id);

        yetkiKontrolService.birimeErisimiDogrula(
                kullanici, etkinlik.getBirim().getId(),
                "silme (" + etkinlik.getBirim().getAd() + ")");

        // Once denetime yaz, sonra sil. Ters sirada olsaydi silme basarili olup
        // denetim kaydi basarisiz oldugunda iz tamamen kaybolurdu.
        islemKaydiService.silmeKaydet(kullanici.getKullaniciAdi(), etkinlik);
        etkinlikRepository.delete(etkinlik);
    }

    // ------------------------------------------------------------- yardimcilar

    /**
     * "Universite Geneli" biriminin kimligini bulur.
     *
     * <p>Bulunamazsa {@code null} doner ve filtreleme yalnizca secilen birimi
     * dikkate alir. Sistem bu birim olmadan da calisir, sadece genel etkinlikler
     * fakulte gorunumlerinde listelenmez.</p>
     */
    private Long genelBirimIdBul() {
        return birimRepository.findFirstByTur(BirimTuru.GENEL)
                .map(Birim::getId)
                .orElse(null);
    }

    private Etkinlik etkinlikBul(Long id) {
        return etkinlikRepository.findByIdWithDetay(id)
                .orElseThrow(() -> KayitBulunamadiException.of("Etkinlik", id));
    }

    private Birim birimBul(Long id) {
        return birimRepository.findById(id)
                .orElseThrow(() -> KayitBulunamadiException.of("Birim", id));
    }

    private Kategori kategoriBul(Long id) {
        return kategoriRepository.findById(id)
                .orElseThrow(() -> KayitBulunamadiException.of("Kategori", id));
    }

    private EgitimYili egitimYiliBul(Long id) {
        return egitimYiliRepository.findById(id)
                .orElseThrow(() -> KayitBulunamadiException.of("Egitim yili", id));
    }

    private String bosluklariTemizle(String metin) {
        if (metin == null) {
            return null;
        }
        String temiz = metin.trim();
        return temiz.isEmpty() ? null : temiz;
    }

    private Map<String, Object> denetimOzeti(Etkinlik e) {
        Map<String, Object> ozet = new LinkedHashMap<>();
        ozet.put("id", e.getId());
        ozet.put("ad", e.getAd());
        ozet.put("aciklama", e.getAciklama());
        ozet.put("baslangicTarihi", String.valueOf(e.getBaslangicTarihi()));
        ozet.put("bitisTarihi", String.valueOf(e.getBitisTarihi()));
        ozet.put("donem", e.getDonem().name());
        ozet.put("egitimYili", e.getEgitimYili().getAd());
        ozet.put("kategori", e.getKategori().getAd());
        ozet.put("birim", e.getBirim().getAd());
        return ozet;
    }
}

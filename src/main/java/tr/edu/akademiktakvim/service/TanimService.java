package tr.edu.akademiktakvim.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tr.edu.akademiktakvim.domain.Birim;
import tr.edu.akademiktakvim.domain.EgitimYili;
import tr.edu.akademiktakvim.domain.Kategori;
import tr.edu.akademiktakvim.domain.enums.IslemTuru;
import tr.edu.akademiktakvim.dto.TanimDTO;
import tr.edu.akademiktakvim.exception.IsKuraliIhlaliException;
import tr.edu.akademiktakvim.exception.KayitBulunamadiException;
import tr.edu.akademiktakvim.repository.BirimRepository;
import tr.edu.akademiktakvim.repository.EgitimYiliRepository;
import tr.edu.akademiktakvim.repository.EtkinlikRepository;
import tr.edu.akademiktakvim.repository.KategoriRepository;

/**
 * Sistem tanimlarinin (birim, kategori, egitim yili) yonetimi.
 *
 * <p>Is analizi Bolum 6.2: "Yeni bir fakulte acilmasi, yeni egitim yilina
 * gecilmesi veya farkli bir etkinlik kategorisinin eklenmesi durumunda yazilim
 * kodunda degisiklik yapilmamalidir." Bu servis o gereksinimi karsilar.</p>
 *
 * <p><b>Silme yerine pasiflestirme:</b> Kullanilmis bir tanim silinmez, pasife
 * alinir. Silinseydi ona bagli gecmis etkinlikler de yok olurdu; oysa gecmis
 * takvim verisi korunmalidir. Silme yalnizca hic kullanilmamis tanimlar icin
 * mumkundur.</p>
 */
@Service
public class TanimService {

    private final BirimRepository birimRepository;
    private final KategoriRepository kategoriRepository;
    private final EgitimYiliRepository egitimYiliRepository;
    private final EtkinlikRepository etkinlikRepository;
    private final IslemKaydiService islemKaydiService;

    public TanimService(BirimRepository birimRepository,
                        KategoriRepository kategoriRepository,
                        EgitimYiliRepository egitimYiliRepository,
                        EtkinlikRepository etkinlikRepository,
                        IslemKaydiService islemKaydiService) {
        this.birimRepository = birimRepository;
        this.kategoriRepository = kategoriRepository;
        this.egitimYiliRepository = egitimYiliRepository;
        this.etkinlikRepository = etkinlikRepository;
        this.islemKaydiService = islemKaydiService;
    }

    // =================================================================== BIRIM

    /**
     * Birimleri listeler.
     *
     * @param yalnizcaAktif herkese acik takvim ekraninda {@code true} verilir;
     *                      yonetim panelinde pasifler de gorunmelidir.
     */
    @Transactional(readOnly = true)
    public List<TanimDTO.BirimGorunum> birimleriListele(boolean yalnizcaAktif) {
        List<Birim> birimler = yalnizcaAktif
                ? birimRepository.findByAktifTrueOrderByAdAsc()
                : birimRepository.findAllByOrderByAdAsc();
        return birimler.stream().map(this::birimGorunumuneCevir).toList();
    }

    @Transactional
    public TanimDTO.BirimGorunum birimEkle(TanimDTO.BirimIstek istek, String kullaniciAdi) {
        if (birimRepository.existsByKod(istek.kod())) {
            throw new IsKuraliIhlaliException(
                    "Bu birim kodu zaten kullanılıyor: " + istek.kod(), "KOD_TEKRARI");
        }
        Birim birim = new Birim(istek.ad().trim(), istek.kod().trim(), istek.tur());
        birim.setAktif(istek.aktif());

        Birim kaydedilen = birimRepository.save(birim);
        islemKaydiService.tanimIslemiKaydet(kullaniciAdi, IslemTuru.EKLE, "BIRIM",
                kaydedilen.getId(), kaydedilen.getAd());
        return birimGorunumuneCevir(kaydedilen);
    }

    @Transactional
    public TanimDTO.BirimGorunum birimGuncelle(Long id, TanimDTO.BirimIstek istek, String kullaniciAdi) {
        Birim birim = birimRepository.findById(id)
                .orElseThrow(() -> KayitBulunamadiException.of("Birim", id));

        // Kod baskasina aitse catisma var demektir
        birimRepository.findByKod(istek.kod())
                .filter(baskasi -> !baskasi.getId().equals(id))
                .ifPresent(baskasi -> {
                    throw new IsKuraliIhlaliException(
                            "Bu birim kodu zaten kullanılıyor: " + istek.kod(), "KOD_TEKRARI");
                });

        birim.setAd(istek.ad().trim());
        birim.setKod(istek.kod().trim());
        birim.setTur(istek.tur());
        birim.setAktif(istek.aktif());

        Birim kaydedilen = birimRepository.save(birim);
        islemKaydiService.tanimIslemiKaydet(kullaniciAdi, IslemTuru.GUNCELLE, "BIRIM",
                kaydedilen.getId(), kaydedilen.getAd());
        return birimGorunumuneCevir(kaydedilen);
    }

    @Transactional
    public void birimSil(Long id, String kullaniciAdi) {
        Birim birim = birimRepository.findById(id)
                .orElseThrow(() -> KayitBulunamadiException.of("Birim", id));

        if (etkinlikRepository.existsByBirimId(id)) {
            throw new IsKuraliIhlaliException(
                    "Bu birime bağlı etkinlikler olduğu için silinemez. "
                            + "Bunun yerine birimi pasif duruma alabilirsiniz.",
                    "KULLANIMDA");
        }
        islemKaydiService.tanimIslemiKaydet(kullaniciAdi, IslemTuru.SIL, "BIRIM",
                birim.getId(), birim.getAd());
        birimRepository.delete(birim);
    }

    // ================================================================ KATEGORI

    @Transactional(readOnly = true)
    public List<TanimDTO.KategoriGorunum> kategorileriListele(boolean yalnizcaAktif) {
        List<Kategori> kategoriler = yalnizcaAktif
                ? kategoriRepository.findByAktifTrueOrderBySiraAscAdAsc()
                : kategoriRepository.findAllByOrderBySiraAscAdAsc();
        return kategoriler.stream().map(this::kategoriGorunumuneCevir).toList();
    }

    @Transactional
    public TanimDTO.KategoriGorunum kategoriEkle(TanimDTO.KategoriIstek istek, String kullaniciAdi) {
        if (kategoriRepository.existsByKod(istek.kod())) {
            throw new IsKuraliIhlaliException(
                    "Bu kategori kodu zaten kullanılıyor: " + istek.kod(), "KOD_TEKRARI");
        }
        Kategori kategori = new Kategori(istek.ad().trim(), istek.kod().trim(),
                istek.renk(), istek.sira());
        kategori.setAktif(istek.aktif());

        Kategori kaydedilen = kategoriRepository.save(kategori);
        islemKaydiService.tanimIslemiKaydet(kullaniciAdi, IslemTuru.EKLE, "KATEGORI",
                kaydedilen.getId(), kaydedilen.getAd());
        return kategoriGorunumuneCevir(kaydedilen);
    }

    @Transactional
    public TanimDTO.KategoriGorunum kategoriGuncelle(Long id, TanimDTO.KategoriIstek istek,
                                                     String kullaniciAdi) {
        Kategori kategori = kategoriRepository.findById(id)
                .orElseThrow(() -> KayitBulunamadiException.of("Kategori", id));

        kategoriRepository.findByKod(istek.kod())
                .filter(baskasi -> !baskasi.getId().equals(id))
                .ifPresent(baskasi -> {
                    throw new IsKuraliIhlaliException(
                            "Bu kategori kodu zaten kullanılıyor: " + istek.kod(), "KOD_TEKRARI");
                });

        kategori.setAd(istek.ad().trim());
        kategori.setKod(istek.kod().trim());
        kategori.setRenk(istek.renk());
        kategori.setSira(istek.sira());
        kategori.setAktif(istek.aktif());

        Kategori kaydedilen = kategoriRepository.save(kategori);
        islemKaydiService.tanimIslemiKaydet(kullaniciAdi, IslemTuru.GUNCELLE, "KATEGORI",
                kaydedilen.getId(), kaydedilen.getAd());
        return kategoriGorunumuneCevir(kaydedilen);
    }

    @Transactional
    public void kategoriSil(Long id, String kullaniciAdi) {
        Kategori kategori = kategoriRepository.findById(id)
                .orElseThrow(() -> KayitBulunamadiException.of("Kategori", id));

        if (etkinlikRepository.existsByKategoriId(id)) {
            throw new IsKuraliIhlaliException(
                    "Bu kategoriye bağlı etkinlikler olduğu için silinemez. "
                            + "Bunun yerine kategoriyi pasif duruma alabilirsiniz.",
                    "KULLANIMDA");
        }
        islemKaydiService.tanimIslemiKaydet(kullaniciAdi, IslemTuru.SIL, "KATEGORI",
                kategori.getId(), kategori.getAd());
        kategoriRepository.delete(kategori);
    }

    // ============================================================= EGITIM YILI

    @Transactional(readOnly = true)
    public List<TanimDTO.EgitimYiliGorunum> egitimYillariniListele(boolean yalnizcaAktif) {
        List<EgitimYili> yillar = yalnizcaAktif
                ? egitimYiliRepository.findByAktifTrueOrderByAdDesc()
                : egitimYiliRepository.findAllByOrderByAdDesc();
        return yillar.stream().map(this::egitimYiliGorunumuneCevir).toList();
    }

    @Transactional
    public TanimDTO.EgitimYiliGorunum egitimYiliEkle(TanimDTO.EgitimYiliIstek istek, String kullaniciAdi) {
        if (egitimYiliRepository.existsByAd(istek.ad())) {
            throw new IsKuraliIhlaliException(
                    "Bu eğitim yılı zaten tanımlı: " + istek.ad(), "AD_TEKRARI");
        }
        tarihSirasiniDogrula(istek.baslangicTarihi(), istek.bitisTarihi());

        EgitimYili yil = new EgitimYili(istek.ad().trim(), istek.baslangicTarihi(), istek.bitisTarihi());
        yil.setAktif(istek.aktif());

        EgitimYili kaydedilen = egitimYiliRepository.save(yil);
        islemKaydiService.tanimIslemiKaydet(kullaniciAdi, IslemTuru.EKLE, "EGITIM_YILI",
                kaydedilen.getId(), kaydedilen.getAd());
        return egitimYiliGorunumuneCevir(kaydedilen);
    }

    @Transactional
    public TanimDTO.EgitimYiliGorunum egitimYiliGuncelle(Long id, TanimDTO.EgitimYiliIstek istek,
                                                         String kullaniciAdi) {
        EgitimYili yil = egitimYiliRepository.findById(id)
                .orElseThrow(() -> KayitBulunamadiException.of("Eğitim yılı", id));

        egitimYiliRepository.findByAd(istek.ad())
                .filter(baskasi -> !baskasi.getId().equals(id))
                .ifPresent(baskasi -> {
                    throw new IsKuraliIhlaliException(
                            "Bu eğitim yılı zaten tanımlı: " + istek.ad(), "AD_TEKRARI");
                });

        tarihSirasiniDogrula(istek.baslangicTarihi(), istek.bitisTarihi());

        yil.setAd(istek.ad().trim());
        yil.setBaslangicTarihi(istek.baslangicTarihi());
        yil.setBitisTarihi(istek.bitisTarihi());
        yil.setAktif(istek.aktif());

        EgitimYili kaydedilen = egitimYiliRepository.save(yil);
        islemKaydiService.tanimIslemiKaydet(kullaniciAdi, IslemTuru.GUNCELLE, "EGITIM_YILI",
                kaydedilen.getId(), kaydedilen.getAd());
        return egitimYiliGorunumuneCevir(kaydedilen);
    }

    @Transactional
    public void egitimYiliSil(Long id, String kullaniciAdi) {
        EgitimYili yil = egitimYiliRepository.findById(id)
                .orElseThrow(() -> KayitBulunamadiException.of("Eğitim yılı", id));

        if (etkinlikRepository.existsByEgitimYiliId(id)) {
            throw new IsKuraliIhlaliException(
                    "Bu eğitim yılına bağlı etkinlikler olduğu için silinemez. "
                            + "Bunun yerine pasif duruma alabilirsiniz.",
                    "KULLANIMDA");
        }
        islemKaydiService.tanimIslemiKaydet(kullaniciAdi, IslemTuru.SIL, "EGITIM_YILI",
                yil.getId(), yil.getAd());
        egitimYiliRepository.delete(yil);
    }

    // ------------------------------------------------------------- yardimcilar

    private void tarihSirasiniDogrula(java.time.LocalDate baslangic, java.time.LocalDate bitis) {
        if (bitis.isBefore(baslangic)) {
            throw new IsKuraliIhlaliException(
                    "Eğitim yılının bitiş tarihi başlangıç tarihinden önce olamaz.",
                    "TARIH_SIRASI_HATALI");
        }
    }

    private TanimDTO.BirimGorunum birimGorunumuneCevir(Birim b) {
        return new TanimDTO.BirimGorunum(b.getId(), b.getAd(), b.getKod(), b.getTur(),
                b.getTur().getEtiket(), b.isAktif());
    }

    private TanimDTO.KategoriGorunum kategoriGorunumuneCevir(Kategori k) {
        return new TanimDTO.KategoriGorunum(k.getId(), k.getAd(), k.getKod(), k.getRenk(),
                k.getSira(), k.isAktif());
    }

    private TanimDTO.EgitimYiliGorunum egitimYiliGorunumuneCevir(EgitimYili y) {
        return new TanimDTO.EgitimYiliGorunum(y.getId(), y.getAd(), y.getBaslangicTarihi(),
                y.getBitisTarihi(), y.isAktif());
    }
}

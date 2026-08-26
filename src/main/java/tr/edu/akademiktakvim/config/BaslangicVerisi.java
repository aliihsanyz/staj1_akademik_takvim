package tr.edu.akademiktakvim.config;

import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import tr.edu.akademiktakvim.domain.Birim;
import tr.edu.akademiktakvim.domain.EgitimYili;
import tr.edu.akademiktakvim.domain.Etkinlik;
import tr.edu.akademiktakvim.domain.Kategori;
import tr.edu.akademiktakvim.domain.Kullanici;
import tr.edu.akademiktakvim.domain.enums.BirimTuru;
import tr.edu.akademiktakvim.domain.enums.Donem;
import tr.edu.akademiktakvim.domain.enums.Rol;
import tr.edu.akademiktakvim.repository.BirimRepository;
import tr.edu.akademiktakvim.repository.EgitimYiliRepository;
import tr.edu.akademiktakvim.repository.EtkinlikRepository;
import tr.edu.akademiktakvim.repository.KategoriRepository;
import tr.edu.akademiktakvim.repository.KullaniciRepository;

/**
 * Sistem ilk kez calistirildiginda temel kayitlari olusturur.
 *
 * <p>Yalnizca ilgili tablo BOS ise calisir; mevcut veriye dokunmaz. Boylece
 * uygulama her yeniden baslatildiginda tekrar tekrar kayit uretmez.</p>
 *
 * <p><b>Guvenlik notu:</b> varsayilan yonetici sifresi
 * {@code uygulama.varsayilan-admin-sifresi} ayariyla belirlenir. Gercek
 * kurulumda bu deger ortam degiskeniyle verilmeli ve ilk giristen sonra
 * degistirilmelidir.</p>
 */
@Component
public class BaslangicVerisi implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(BaslangicVerisi.class);

    private final BirimRepository birimRepository;
    private final KategoriRepository kategoriRepository;
    private final EgitimYiliRepository egitimYiliRepository;
    private final KullaniciRepository kullaniciRepository;
    private final EtkinlikRepository etkinlikRepository;
    private final PasswordEncoder sifreleyici;

    @Value("${uygulama.varsayilan-admin-sifresi:admin123}")
    private String varsayilanAdminSifresi;

    @Value("${uygulama.ornek-veri-yukle:true}")
    private boolean ornekVeriYukle;

    public BaslangicVerisi(BirimRepository birimRepository,
                           KategoriRepository kategoriRepository,
                           EgitimYiliRepository egitimYiliRepository,
                           KullaniciRepository kullaniciRepository,
                           EtkinlikRepository etkinlikRepository,
                           PasswordEncoder sifreleyici) {
        this.birimRepository = birimRepository;
        this.kategoriRepository = kategoriRepository;
        this.egitimYiliRepository = egitimYiliRepository;
        this.kullaniciRepository = kullaniciRepository;
        this.etkinlikRepository = etkinlikRepository;
        this.sifreleyici = sifreleyici;
    }

    @Override
    @Transactional
    public void run(String... args) {
        birimleriOlustur();
        kategorileriOlustur();
        egitimYillariniOlustur();
        kullanicilariOlustur();
        if (ornekVeriYukle) {
            ornekEtkinlikleriOlustur();
        }
    }

    // ------------------------------------------------------------------ BIRIM

    private void birimleriOlustur() {
        if (birimRepository.count() > 0) {
            return;
        }
        // GENEL birim ilk sirada: filtreleme mantiginin dayandigi kayittir.
        birimRepository.saveAll(List.of(
                new Birim("Üniversite Geneli", "GENEL", BirimTuru.GENEL),
                new Birim("Mühendislik Fakültesi", "MUH_FAK", BirimTuru.FAKULTE),
                new Birim("Fen-Edebiyat Fakültesi", "FEN_EDB_FAK", BirimTuru.FAKULTE),
                new Birim("İktisadi ve İdari Bilimler Fakültesi", "IIBF", BirimTuru.FAKULTE),
                new Birim("Eğitim Fakültesi", "EGT_FAK", BirimTuru.FAKULTE),
                new Birim("Fen Bilimleri Enstitüsü", "FEN_BIL_ENS", BirimTuru.ENSTITU),
                new Birim("Sosyal Bilimler Enstitüsü", "SOS_BIL_ENS", BirimTuru.ENSTITU),
                new Birim("Teknik Bilimler Meslek Yüksekokulu", "TEK_MYO", BirimTuru.MYO)));

        log.info("Başlangıç verisi: 8 birim oluşturuldu.");
    }

    // --------------------------------------------------------------- KATEGORI

    /**
     * Is analizi Bolum 5.1'de sayilan dort kategori birebir olusturulur.
     * Super Admin daha sonra panelden yenisini ekleyebilir.
     */
    private void kategorileriOlustur() {
        if (kategoriRepository.count() > 0) {
            return;
        }
        kategoriRepository.saveAll(List.of(
                new Kategori("Ders ve Sınav Tarihleri", "DERS_SINAV", "#1F4E6B", 1),
                new Kategori("Kayıt ve Başvuru Tarihleri", "KAYIT_BASVURU", "#0F766E", 2),
                new Kategori("Resmî Tatiller", "RESMI_TATIL", "#9A3412", 3),
                new Kategori("Akademik ve İdari Etkinlikler", "AKADEMIK_IDARI", "#4C1D95", 4)));

        log.info("Başlangıç verisi: 4 kategori oluşturuldu.");
    }

    // ------------------------------------------------------------ EGITIM YILI

    private void egitimYillariniOlustur() {
        if (egitimYiliRepository.count() > 0) {
            return;
        }
        egitimYiliRepository.saveAll(List.of(
                new EgitimYili("2025-2026", LocalDate.of(2025, 9, 1), LocalDate.of(2026, 8, 31)),
                new EgitimYili("2026-2027", LocalDate.of(2026, 9, 1), LocalDate.of(2027, 8, 31))));

        log.info("Başlangıç verisi: 2 eğitim yılı oluşturuldu.");
    }

    // --------------------------------------------------------------- KULLANICI

    private void kullanicilariOlustur() {
        if (kullaniciRepository.count() > 0) {
            return;
        }
        Kullanici admin = new Kullanici("admin", sifreleyici.encode(varsayilanAdminSifresi),
                "Sistem Yöneticisi", Rol.SUPER_ADMIN);
        admin.setEposta("admin@universite.edu.tr");
        kullaniciRepository.save(admin);

        // Ornek birim yoneticisi: yalnizca Muhendislik Fakultesine yetkili.
        // Yetki kurallarini elle denemek icin kullanilabilir.
        birimRepository.findByKod("MUH_FAK").ifPresent(muhFak -> {
            Kullanici birimYoneticisi = new Kullanici("muh.yonetici",
                    sifreleyici.encode(varsayilanAdminSifresi),
                    "Mühendislik Fakültesi Sekreteri", Rol.BIRIM_YONETICISI);
            birimYoneticisi.setEposta("muh.sekreter@universite.edu.tr");
            birimYoneticisi.birimEkle(muhFak);
            kullaniciRepository.save(birimYoneticisi);
        });

        log.warn("Başlangıç verisi: 'admin' ve 'muh.yonetici' kullanıcıları oluşturuldu. "
                + "VARSAYILAN ŞİFRE KULLANILIYOR - ilk girişten sonra değiştirin.");
    }

    // --------------------------------------------------------------- ETKINLIK

    private void ornekEtkinlikleriOlustur() {
        if (etkinlikRepository.count() > 0) {
            return;
        }
        EgitimYili yil = egitimYiliRepository.findByAd("2025-2026").orElse(null);
        if (yil == null) {
            return;
        }

        Birim genel = birimRepository.findByKod("GENEL").orElseThrow();
        Birim muhFak = birimRepository.findByKod("MUH_FAK").orElseThrow();
        Birim fenEdb = birimRepository.findByKod("FEN_EDB_FAK").orElseThrow();
        Birim fenBilEns = birimRepository.findByKod("FEN_BIL_ENS").orElseThrow();

        Kategori dersSinav = kategoriRepository.findByKod("DERS_SINAV").orElseThrow();
        Kategori kayit = kategoriRepository.findByKod("KAYIT_BASVURU").orElseThrow();
        Kategori tatil = kategoriRepository.findByKod("RESMI_TATIL").orElseThrow();
        Kategori akademik = kategoriRepository.findByKod("AKADEMIK_IDARI").orElseThrow();

        List<Etkinlik> etkinlikler = List.of(
                // --- GUZ DONEMI ---
                etkinlik("Güz Yarıyılı Ders Kayıtları", LocalDate.of(2025, 9, 8),
                        LocalDate.of(2025, 9, 12), Donem.GUZ, yil, kayit, genel,
                        "Öğrenciler otomasyon sistemi üzerinden ders seçimlerini yapar ve "
                                + "danışman onayına gönderir."),
                etkinlik("Güz Yarıyılı Derslerin Başlaması", LocalDate.of(2025, 9, 15),
                        LocalDate.of(2025, 9, 15), Donem.GUZ, yil, dersSinav, genel, null),
                etkinlik("Cumhuriyet Bayramı", LocalDate.of(2025, 10, 28),
                        LocalDate.of(2025, 10, 29), Donem.GUZ, yil, tatil, genel,
                        "28 Ekim öğleden sonra başlar."),
                etkinlik("Güz Yarıyılı Ara Sınavları", LocalDate.of(2025, 11, 10),
                        LocalDate.of(2025, 11, 21), Donem.GUZ, yil, dersSinav, genel, null),
                etkinlik("Mühendislik Fakültesi Kariyer Günleri", LocalDate.of(2025, 11, 26),
                        LocalDate.of(2025, 11, 27), Donem.GUZ, yil, akademik, muhFak,
                        "Sektör temsilcileriyle söyleşi ve staj görüşmeleri."),
                etkinlik("Yılbaşı Tatili", LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 1, 1), Donem.GUZ, yil, tatil, genel, null),
                etkinlik("Güz Yarıyılı Final Sınavları", LocalDate.of(2026, 1, 5),
                        LocalDate.of(2026, 1, 18), Donem.GUZ, yil, dersSinav, genel, null),
                etkinlik("Güz Yarıyılı Bütünleme Sınavları", LocalDate.of(2026, 1, 26),
                        LocalDate.of(2026, 1, 30), Donem.GUZ, yil, dersSinav, genel, null),

                // --- BAHAR DONEMI ---
                etkinlik("Bahar Yarıyılı Ders Kayıtları", LocalDate.of(2026, 2, 2),
                        LocalDate.of(2026, 2, 6), Donem.BAHAR, yil, kayit, genel, null),
                etkinlik("Bahar Yarıyılı Derslerin Başlaması", LocalDate.of(2026, 2, 9),
                        LocalDate.of(2026, 2, 9), Donem.BAHAR, yil, dersSinav, genel, null),
                etkinlik("Lisansüstü Programlara Başvuru", LocalDate.of(2026, 2, 16),
                        LocalDate.of(2026, 2, 27), Donem.BAHAR, yil, kayit, fenBilEns,
                        "Yüksek lisans ve doktora programları için online başvurular alınır."),
                etkinlik("Bahar Yarıyılı Ara Sınavları", LocalDate.of(2026, 4, 6),
                        LocalDate.of(2026, 4, 17), Donem.BAHAR, yil, dersSinav, genel, null),
                etkinlik("Ulusal Egemenlik ve Çocuk Bayramı", LocalDate.of(2026, 4, 23),
                        LocalDate.of(2026, 4, 23), Donem.BAHAR, yil, tatil, genel, null),
                etkinlik("Fen-Edebiyat Fakültesi Bilim Şenliği", LocalDate.of(2026, 5, 11),
                        LocalDate.of(2026, 5, 13), Donem.BAHAR, yil, akademik, fenEdb, null),
                etkinlik("Emek ve Dayanışma Günü", LocalDate.of(2026, 5, 1),
                        LocalDate.of(2026, 5, 1), Donem.BAHAR, yil, tatil, genel, null),
                etkinlik("Gençlik ve Spor Bayramı", LocalDate.of(2026, 5, 19),
                        LocalDate.of(2026, 5, 19), Donem.BAHAR, yil, tatil, genel, null),
                etkinlik("Bahar Yarıyılı Final Sınavları", LocalDate.of(2026, 6, 1),
                        LocalDate.of(2026, 6, 14), Donem.BAHAR, yil, dersSinav, genel, null),
                etkinlik("Mezuniyet Töreni", LocalDate.of(2026, 6, 26),
                        LocalDate.of(2026, 6, 26), Donem.BAHAR, yil, akademik, genel, null),

                // --- YAZ DONEMI ---
                etkinlik("Yaz Okulu Kayıtları", LocalDate.of(2026, 7, 1),
                        LocalDate.of(2026, 7, 3), Donem.YAZ, yil, kayit, genel, null),
                etkinlik("Yaz Okulu Derslerin Başlaması", LocalDate.of(2026, 7, 6),
                        LocalDate.of(2026, 7, 6), Donem.YAZ, yil, dersSinav, genel, null),
                etkinlik("Zafer Bayramı", LocalDate.of(2026, 8, 30),
                        LocalDate.of(2026, 8, 30), Donem.YAZ, yil, tatil, genel, null));

        etkinlikRepository.saveAll(etkinlikler);
        log.info("Başlangıç verisi: {} örnek etkinlik oluşturuldu.", etkinlikler.size());
    }

    private Etkinlik etkinlik(String ad, LocalDate baslangic, LocalDate bitis, Donem donem,
                              EgitimYili yil, Kategori kategori, Birim birim, String aciklama) {
        Etkinlik e = new Etkinlik(ad, baslangic, bitis, donem, yil, kategori, birim);
        e.setAciklama(aciklama);
        e.setOlusturan("sistem");
        return e;
    }
}

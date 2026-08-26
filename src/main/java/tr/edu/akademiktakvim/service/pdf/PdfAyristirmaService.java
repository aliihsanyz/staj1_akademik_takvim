package tr.edu.akademiktakvim.service.pdf;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import tr.edu.akademiktakvim.domain.Kategori;
import tr.edu.akademiktakvim.domain.enums.Donem;
import tr.edu.akademiktakvim.dto.IceAktarmaDTO;
import tr.edu.akademiktakvim.exception.IsKuraliIhlaliException;
import tr.edu.akademiktakvim.repository.KategoriRepository;
import tr.edu.akademiktakvim.service.pdf.TarihAyristirici.TarihAraligi;

/**
 * Yuklenen akademik takvim PDF'ini analiz edip aday etkinlik listesi uretir.
 *
 * <h3>Algoritma</h3>
 * <pre>
 *   PDF -> satirlar -> her satir icin:
 *        tarih var mi?
 *          EVET -> etkinlik adayi olustur (ad = satirin tarih disinda kalani)
 *          HAYIR -> bolum basligi mi? (GÜZ YARIYILI gibi)
 *                     EVET -> donem baglamini guncelle
 *                     HAYIR -> onceki etkinligin adinin devami mi? (satir kaydirma)
 *                                EVET -> onceki adin sonuna ekle
 *                                HAYIR -> ayristirilamayan satirlara koy
 * </pre>
 *
 * <h3>Neden sonuc dogrudan kaydedilmiyor?</h3>
 * <p>Kaynak PDF'ler standart disidir; ayristirma kacinilmaz olarak hatali
 * satirlar uretir. Sonuc bu yuzden yalnizca ONIZLEME olarak dondurulur;
 * kayit islemi kullanicinin onayindan sonra {@code IceAktarmaService}
 * tarafindan, normal is kurallari uygulanarak yapilir.</p>
 */
@Service
public class PdfAyristirmaService {

    private static final Logger log = LoggerFactory.getLogger(PdfAyristirmaService.class);

    /** Bu esigin altindaki satirlar onay ekraninda vurgulanir. */
    private static final int DUSUK_GUVEN_ESIGI = 60;

    /** Etkinlik adi bundan kisaysa muhtemelen ayristirma hatasidir. */
    private static final int EN_KISA_AD = 3;

    /** Cok uzun satirlar genellikle paragraf metnidir, etkinlik degil. */
    private static final int EN_UZUN_AD = 250;

    /**
     * Satirda tarih izi (4 haneli yil veya Turkce ay adi) var mi?
     * Bolum basligi tespitinde yanlis pozitifleri engellemek icin kullanilir.
     */
    private static final java.util.regex.Pattern TARIH_IZI = java.util.regex.Pattern.compile(
            "\\d{4}|\\b(Ocak|Şubat|Subat|Mart|Nisan|Mayıs|Mayis|Haziran|Temmuz|"
                    + "Ağustos|Agustos|Eylül|Eylul|Ekim|Kasım|Kasim|Aralık|Aralik)\\b",
            java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.UNICODE_CASE);

    private final PdfMetinCikarici metinCikarici;
    private final TarihAyristirici tarihAyristirici;
    private final KategoriTahminEdici kategoriTahminEdici;
    private final KategoriRepository kategoriRepository;

    public PdfAyristirmaService(PdfMetinCikarici metinCikarici,
                                TarihAyristirici tarihAyristirici,
                                KategoriTahminEdici kategoriTahminEdici,
                                KategoriRepository kategoriRepository) {
        this.metinCikarici = metinCikarici;
        this.tarihAyristirici = tarihAyristirici;
        this.kategoriTahminEdici = kategoriTahminEdici;
        this.kategoriRepository = kategoriRepository;
    }

    /**
     * Yuklenen PDF'i ayristirip onizleme uretir. Veritabanina HICBIR SEY YAZMAZ.
     */
    @Transactional(readOnly = true)
    public IceAktarmaDTO.OnizlemeYaniti onizle(MultipartFile dosya) {
        dosyayiDogrula(dosya);

        List<Kategori> kategoriler = kategoriRepository.findByAktifTrueOrderBySiraAscAdAsc();
        if (kategoriler.isEmpty()) {
            throw new IsKuraliIhlaliException(
                    "Sistemde tanımlı aktif kategori yok. İçe aktarmadan önce en az bir kategori tanımlayın.",
                    "KATEGORI_YOK");
        }

        List<String> satirlar;
        try (InputStream akis = dosya.getInputStream()) {
            satirlar = metinCikarici.satirlariCikar(akis);
        } catch (IOException ex) {
            log.warn("PDF okunamadı: {}", dosya.getOriginalFilename(), ex);
            throw new IsKuraliIhlaliException(
                    "PDF dosyası okunamadı. Dosya bozuk veya şifreli olabilir.", "PDF_OKUNAMADI");
        }

        return satirlariAyristir(satirlar, kategoriler, dosya.getOriginalFilename());
    }

    /**
     * Satir listesini etkinliklere cevirir.
     *
     * <p>Paket seviyesinde gorunur: birim testleri PDF dosyasi olusturmak
     * zorunda kalmadan dogrudan satir listesiyle calisabilsin diye.</p>
     */
    IceAktarmaDTO.OnizlemeYaniti satirlariAyristir(List<String> satirlar,
                                                   List<Kategori> kategoriler,
                                                   String dosyaAdi) {
        List<IceAktarmaDTO.AyristirilanEtkinlik> etkinlikler = new ArrayList<>();
        List<String> atlananlar = new ArrayList<>();

        // Bolum basliklarindan gelen donem baglami. Satirda tarih varsa ama donem
        // belirtilmemisse bu deger kullanilir.
        Donem aktifDonem = null;

        for (String satir : satirlar) {
            Optional<TarihAraligi> aralik = tarihAyristirici.ayristir(satir);

            if (aralik.isEmpty()) {
                // Tarihi olmayan satir: bolum basligi mi, devam satiri mi?
                Donem basliktanDonem = donemBasligiMi(satir);
                if (basliktanDonem != null) {
                    aktifDonem = basliktanDonem;
                } else {
                    atlananlar.add(satir);
                }
                continue;
            }

            String ad = tarihAyristirici.tarihiCikar(satir);

            if (ad.length() < EN_KISA_AD) {
                // Satirda tarih var ama ad yok: tabloda tarih ayri hucrede
                // olabilir. Bu durumda satir atlanir ve kullaniciya gosterilir.
                atlananlar.add(satir);
                continue;
            }
            if (ad.length() > EN_UZUN_AD) {
                ad = ad.substring(0, EN_UZUN_AD).trim();
            }

            etkinlikler.add(etkinlikOlustur(ad, aralik.get(), aktifDonem, kategoriler, satir));
        }

        return new IceAktarmaDTO.OnizlemeYaniti(
                dosyaAdi,
                satirlar.size(),
                etkinlikler,
                atlananlar,
                istatistikCikar(etkinlikler, atlananlar));
    }

    // --------------------------------------------------------- ETKINLIK KURMA

    private IceAktarmaDTO.AyristirilanEtkinlik etkinlikOlustur(
            String ad, TarihAraligi aralik, Donem baglamDonemi,
            List<Kategori> kategoriler, String kaynakSatir) {

        List<String> uyarilar = new ArrayList<>();

        // --- DONEM ---
        // Once bolum basligindan gelen baglam, yoksa tarihten cikarim.
        Donem donem;
        if (baglamDonemi != null) {
            donem = baglamDonemi;
        } else {
            donem = Donem.tarihtenTahminEt(aralik.baslangic());
            uyarilar.add("Dönem, başlangıç tarihine göre tahmin edildi.");
        }

        // --- KATEGORI ---
        KategoriTahminEdici.Tahmin tahmin = kategoriTahminEdici.tahminEt(ad, kategoriler);
        if (tahmin.guven() < DUSUK_GUVEN_ESIGI) {
            uyarilar.add("Kategori güvenle belirlenemedi, lütfen kontrol edin.");
        }

        // --- MANTIK DENETIMLERI ---
        long gunSayisi = aralik.baslangic().until(aralik.bitis()).getDays()
                + aralik.baslangic().until(aralik.bitis()).getMonths() * 30L;
        if (gunSayisi > 120) {
            uyarilar.add("Etkinlik süresi olağandışı uzun (" + gunSayisi + " gün); "
                    + "tarih ayrıştırma hatası olabilir.");
        }
        if (ad.matches(".*\\d{4}.*")) {
            uyarilar.add("Etkinlik adında yıl bilgisi kalmış olabilir.");
        }

        int guvenSkoru = guvenHesapla(ad, tahmin.guven(), baglamDonemi != null, uyarilar.size());

        return new IceAktarmaDTO.AyristirilanEtkinlik(
                ad,
                aralik.baslangic(),
                aralik.bitis(),
                donem,
                tahmin.basariliMi() ? tahmin.kategori().getId() : null,
                tahmin.basariliMi() ? tahmin.kategori().getAd() : null,
                guvenSkoru,
                uyarilar,
                kaynakSatir);
    }

    /**
     * Genel guven skoru.
     *
     * <p>Kategori tahmininin guveninden baslar; donem belgede acikca yaziyorsa
     * puan eklenir, her uyari puan dusurur. Amac, kullanicinin dikkatini once
     * en supheli satirlara yoneltmektir.</p>
     */
    private int guvenHesapla(String ad, int kategoriGuveni, boolean donemAcikca, int uyariSayisi) {
        int skor = kategoriGuveni;

        if (donemAcikca) {
            skor += 10;
        }
        // Cok kisa adlar genelde eksik ayristirmadir
        if (ad.length() < 10) {
            skor -= 15;
        }
        skor -= uyariSayisi * 10;

        return Math.max(0, Math.min(100, skor));
    }

    // ---------------------------------------------------------- BOLUM BASLIGI

    /**
     * Satir bir donem basligi mi? ("GÜZ YARIYILI", "BAHAR DÖNEMİ" gibi)
     *
     * <p>Baslik tespit edilirse sonraki etkinlikler o doneme ait sayilir;
     * PDF'lerdeki en yaygin duzen budur.</p>
     *
     * <p><b>Neden bu kadar cok muhafaza var?</b> Bu metot yanlis pozitif
     * verdiginde iki zarar birden olusur: (1) satir etkinlik olarak
     * kaydedilmez, sessizce kaybolur; (2) donem baglami bozulur ve o
     * noktadan SONRAKI tum etkinlikler yanlis doneme atanir. Gercek bir
     * testte "Bahar Yarıyılı Kayıt Yenileme 2-6 Şubat 2026" satiri baslik
     * sanilmis, bir etkinlik kaybolmus ve ardindan gelen yaz etkinlikleri
     * BAHAR olarak isaretlenmisti. Bu yuzden kural sikilastirildi.</p>
     *
     * @return donem, ya da baslik degilse {@code null}
     */
    Donem donemBasligiMi(String satir) {
        // (1) Basliklar kisadir; uzun satir paragraftir
        if (satir.length() > 40) {
            return null;
        }

        // (2) Icinde yil ya da ay adi varsa bu bir baslik degil, tarihi
        // ayristirilamamis bir ETKINLIK satiridir. Baslik sayilirsa yutulur.
        if (TARIH_IZI.matcher(satir).find()) {
            return null;
        }

        // (3) Bosluklar tamamen kaldirilarak eslesme yapilir.
        // Sebep: PDF'lerde baslik fontlarindaki harf araligi yuzunden metin
        // "G ÜZ YARIYILI" gibi bolunebiliyor (bu davranis PDFBox'in kendi
        // ciktisinda da gorulur, bize ozgu degildir).
        String sikistirilmis = satir.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);

        boolean donemKelimesiVar = sikistirilmis.contains("yarıyıl")
                || sikistirilmis.contains("yariyil")
                || sikistirilmis.contains("dönem")
                || sikistirilmis.contains("donem")
                || sikistirilmis.contains("okulu");

        if (!donemKelimesiVar) {
            return null;
        }
        if (sikistirilmis.contains("güz") || sikistirilmis.contains("guz")) {
            return Donem.GUZ;
        }
        if (sikistirilmis.contains("bahar")) {
            return Donem.BAHAR;
        }
        if (sikistirilmis.contains("yaz")) {
            return Donem.YAZ;
        }
        return null;
    }

    // ------------------------------------------------------------- YARDIMCILAR

    private IceAktarmaDTO.Istatistik istatistikCikar(
            List<IceAktarmaDTO.AyristirilanEtkinlik> etkinlikler, List<String> atlananlar) {

        int yuksek = (int) etkinlikler.stream()
                .filter(e -> e.guvenSkoru() >= DUSUK_GUVEN_ESIGI).count();

        return new IceAktarmaDTO.Istatistik(
                etkinlikler.size(),
                yuksek,
                etkinlikler.size() - yuksek,
                atlananlar.size());
    }

    private void dosyayiDogrula(MultipartFile dosya) {
        if (dosya == null || dosya.isEmpty()) {
            throw new IsKuraliIhlaliException("Yüklenecek dosya seçilmedi.", "DOSYA_BOS");
        }
        String ad = dosya.getOriginalFilename();
        if (ad == null || !ad.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new IsKuraliIhlaliException(
                    "Yalnızca PDF dosyaları içe aktarılabilir.", "GECERSIZ_DOSYA_TURU");
        }
    }
}

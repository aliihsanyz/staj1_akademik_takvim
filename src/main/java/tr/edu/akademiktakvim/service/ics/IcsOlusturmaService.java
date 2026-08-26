package tr.edu.akademiktakvim.service.ics;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.stereotype.Service;

import tr.edu.akademiktakvim.domain.Etkinlik;

/**
 * Etkinlikleri RFC 5545 (iCalendar / .ics) bicimine cevirir.
 *
 * <p>Is analizi Bolum 5.3: "Takvime Ekle butonu ile etkinlik .ics formatinda
 * indirilebilecektir. Bu dosya Google Takvim, Apple Takvim veya Outlook gibi
 * uygulamalarda acilabilecektir."</p>
 *
 * <p><b>Neden kutuphane kullanilmadi?</b> Tam gunluk etkinlikler icin gereken
 * iCalendar alt kumesi yaklasik 15 satirlik bir metindir. ical4j gibi bir
 * bagimlilik eklemek, projeye bakim yuku getirmekten baska bir sey yapmazdi.
 * Bicimin inceliklerine (kacislama, satir katlama, DTEND kurali) asagida
 * acikca uyuldu.</p>
 */
@Service
public class IcsOlusturmaService {

    /** iCalendar tarih bicimi: 20250915 */
    private static final DateTimeFormatter TARIH = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** UTC zaman damgasi bicimi: 20250915T103000Z */
    private static final DateTimeFormatter ZAMAN_DAMGASI =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

    /** RFC 5545 satirlarin 75 oktetten uzun olmamasini sart kosar. */
    private static final int SATIR_SINIRI = 75;

    private static final String URUN_KIMLIGI = "-//Akademik Takvim//Dinamik Akademik Takvim Sistemi//TR";
    private static final String ALAN_ADI = "akademiktakvim";

    /** Tek bir etkinligi .ics icerigine cevirir. */
    public byte[] olustur(Etkinlik etkinlik) {
        return olustur(List.of(etkinlik));
    }

    /**
     * Birden fazla etkinligi tek bir takvim dosyasinda toplar.
     * Kullanici filtreledigi tum takvimi tek dosyayla ekleyebilir.
     */
    public byte[] olustur(List<Etkinlik> etkinlikler) {
        StringBuilder sb = new StringBuilder();

        satirEkle(sb, "BEGIN:VCALENDAR");
        satirEkle(sb, "VERSION:2.0");
        satirEkle(sb, "PRODID:" + URUN_KIMLIGI);
        satirEkle(sb, "CALSCALE:GREGORIAN");
        satirEkle(sb, "METHOD:PUBLISH");

        String simdi = LocalDateTime.now(ZoneOffset.UTC).format(ZAMAN_DAMGASI);
        for (Etkinlik e : etkinlikler) {
            etkinlikYaz(sb, e, simdi);
        }

        satirEkle(sb, "END:VCALENDAR");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void etkinlikYaz(StringBuilder sb, Etkinlik e, String simdi) {
        satirEkle(sb, "BEGIN:VEVENT");

        // UID benzersiz ve KARARLI olmalidir: ayni etkinlik tekrar indirilirse
        // takvim uygulamasi yeni bir kayit acmak yerine mevcut olani gunceller.
        satirEkle(sb, "UID:etkinlik-" + e.getId() + "@" + ALAN_ADI);
        satirEkle(sb, "DTSTAMP:" + simdi);

        // Tum gun suren etkinlik: VALUE=DATE (saat bilgisi yok).
        satirEkle(sb, "DTSTART;VALUE=DATE:" + e.getBaslangicTarihi().format(TARIH));

        // DIKKAT - RFC 5545'in en cok hata yapilan kurali:
        // Tum gun etkinliklerde DTEND DISLAYICIDIR (exclusive). Yani 15-20 Eylul
        // arasi suren bir etkinligin DTEND'i 21 Eylul olmalidir. Bitis tarihi
        // dogrudan yazilsaydi etkinlik takvimde bir gun eksik gorunurdu.
        LocalDate dtEnd = e.getBitisTarihi().plusDays(1);
        satirEkle(sb, "DTEND;VALUE=DATE:" + dtEnd.format(TARIH));

        satirEkle(sb, "SUMMARY:" + kacisla(e.getAd()));

        if (e.getAciklama() != null && !e.getAciklama().isBlank()) {
            satirEkle(sb, "DESCRIPTION:" + kacisla(e.getAciklama()));
        }

        // Kategori ve birim, takvim uygulamalarinda filtreleme icin isaretlenir
        satirEkle(sb, "CATEGORIES:" + kacisla(e.getKategori().getAd()));
        satirEkle(sb, "LOCATION:" + kacisla(e.getBirim().getAd()));

        // Akademik takvim etkinlikleri kisiyi mesgul gostermemeli
        satirEkle(sb, "TRANSP:TRANSPARENT");
        satirEkle(sb, "END:VEVENT");
    }

    /**
     * RFC 5545 metin kacislama kurallari.
     *
     * <p>Ters bolu, noktali virgul, virgul ve satir sonu ozel anlamlidir;
     * kacislanmazsa dosya bozulur ve takvim uygulamasi ya satiri atlar ya da
     * tum dosyayi reddeder. Sira onemlidir: ters bolu ONCE kacislanmalidir,
     * aksi halde sonraki adimlarin urettigi ters bolular da kacislanir.</p>
     */
    String kacisla(String metin) {
        if (metin == null) {
            return "";
        }
        return metin
                .replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\r\n", "\\n")
                .replace("\n", "\\n")
                .replace("\r", "\\n");
    }

    /**
     * Satiri 75 okteti asmayacak sekilde katlayarak ekler.
     *
     * <p>Katlama kurali: devam satirlari tek bir bosluk karakteriyle baslar.
     * Olcum KARAKTER degil OKTET uzerinden yapilir; Turkce karakterler UTF-8'de
     * 2 bayt tuttugu icin karakter sayarak bolmek siniri asardi. Ayrica bir
     * karakterin bayt dizisi ORTASINDAN bolunmemelidir, yoksa dosya bozulur.</p>
     */
    void satirEkle(StringBuilder sb, String satir) {
        byte[] baytlar = satir.getBytes(StandardCharsets.UTF_8);

        if (baytlar.length <= SATIR_SINIRI) {
            sb.append(satir).append("\r\n");
            return;
        }

        int konum = 0;
        boolean ilkParca = true;
        while (konum < baytlar.length) {
            // Devam satirlarinda basta bir bosluk oldugu icin bir oktet daha az yer var
            int sinir = ilkParca ? SATIR_SINIRI : SATIR_SINIRI - 1;
            int uzunluk = Math.min(sinir, baytlar.length - konum);

            // UTF-8 devam baytlari 10xxxxxx bicimindedir; boyle bir bayta denk
            // gelirsek karakterin ortasindayiz demektir, geri cekiliriz.
            while (uzunluk > 0 && konum + uzunluk < baytlar.length
                    && (baytlar[konum + uzunluk] & 0xC0) == 0x80) {
                uzunluk--;
            }
            if (uzunluk <= 0) {
                uzunluk = Math.min(sinir, baytlar.length - konum);
            }

            String parca = new String(baytlar, konum, uzunluk, StandardCharsets.UTF_8);
            sb.append(ilkParca ? "" : " ").append(parca).append("\r\n");

            konum += uzunluk;
            ilkParca = false;
        }
    }

    /**
     * Indirilen dosyaya verilecek ad.
     * Turkce karakterler ve bosluklar bazi isletim sistemlerinde sorun cikardigi
     * icin sadelestirilir.
     */
    public String dosyaAdi(String onek) {
        String temiz = onek
                .replace("ı", "i").replace("İ", "I")
                .replace("ş", "s").replace("Ş", "S")
                .replace("ğ", "g").replace("Ğ", "G")
                .replace("ü", "u").replace("Ü", "U")
                .replace("ö", "o").replace("Ö", "O")
                .replace("ç", "c").replace("Ç", "C")
                .replaceAll("[^A-Za-z0-9._-]+", "_");
        return temiz + ".ics";
    }
}

package tr.edu.akademiktakvim.service.ics;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tr.edu.akademiktakvim.TestVeriUretici;
import tr.edu.akademiktakvim.domain.Etkinlik;

/**
 * RFC 5545 (.ics) uretiminin testleri.
 *
 * <p>Kutuphane kullanilmadigi icin bicimin inceliklerini bu testler korur.
 * Ozellikle DTEND kurali, iCalendar'da en sik yapilan hatadir.</p>
 */
@DisplayName("IcsOlusturmaService")
class IcsOlusturmaServiceTest {

    private final IcsOlusturmaService servis = new IcsOlusturmaService();

    private Etkinlik ornekEtkinlik(String ad, LocalDate baslangic, LocalDate bitis) {
        return TestVeriUretici.etkinlik(7L, ad, baslangic, bitis,
                TestVeriUretici.aktifBirim(1L, "Mühendislik Fakültesi"),
                TestVeriUretici.aktifKategori(1L, "Ders ve Sınav Tarihleri"),
                TestVeriUretici.aktifEgitimYili(1L));
    }

    private String uret(Etkinlik e) {
        return new String(servis.olustur(e), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("DTEND bitiş tarihinin BİR GÜN SONRASIDIR (dışlayıcı kural)")
    void dtEndDislayicidir() {
        // RFC 5545'te tüm gün süren etkinliklerde DTEND dışlayıcıdır.
        // Bitiş tarihi doğrudan yazılsaydı, etkinlik takvim uygulamasında
        // BİR GÜN EKSİK görünürdü - sessiz ve fark edilmesi zor bir hata.
        String ics = uret(ornekEtkinlik("Ara Sınavlar",
                LocalDate.of(2025, 11, 10), LocalDate.of(2025, 11, 21)));

        assertThat(ics).contains("DTSTART;VALUE=DATE:20251110");
        assertThat(ics).contains("DTEND;VALUE=DATE:20251122");   // 21 Kasım + 1
    }

    @Test
    @DisplayName("tek günlük etkinlikte DTEND ertesi gündür")
    void tekGunlukEtkinlik() {
        String ics = uret(ornekEtkinlik("Yılbaşı Tatili",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1)));

        assertThat(ics).contains("DTSTART;VALUE=DATE:20260101");
        assertThat(ics).contains("DTEND;VALUE=DATE:20260102");
    }

    @Test
    @DisplayName("ay ve yıl sınırında DTEND doğru hesaplanır")
    void yilSinirindaDtEnd() {
        String ics = uret(ornekEtkinlik("Yıl Sonu Etkinliği",
                LocalDate.of(2025, 12, 30), LocalDate.of(2025, 12, 31)));

        assertThat(ics).contains("DTEND;VALUE=DATE:20260101");
    }

    @Test
    @DisplayName("zorunlu iCalendar alanları bulunur")
    void zorunluAlanlarVar() {
        String ics = uret(ornekEtkinlik("Test", LocalDate.now(), LocalDate.now()));

        assertThat(ics)
                .startsWith("BEGIN:VCALENDAR")
                .contains("VERSION:2.0")
                .contains("PRODID:")
                .contains("BEGIN:VEVENT")
                .contains("UID:etkinlik-7@")
                .contains("DTSTAMP:")
                .contains("END:VEVENT")
                .endsWith("END:VCALENDAR\r\n");
    }

    @Test
    @DisplayName("satırlar CRLF ile biter")
    void crlfKullanilir() {
        // RFC 5545 satır sonu olarak CRLF şart koşar; sadece LF kullanan
        // dosyaları bazı takvim uygulamaları reddeder.
        String ics = uret(ornekEtkinlik("Test", LocalDate.now(), LocalDate.now()));

        assertThat(ics).contains("\r\n");
        assertThat(ics.replace("\r\n", "")).doesNotContain("\n");
    }

    @Test
    @DisplayName("UID kararlıdır: aynı etkinlik aynı UID'yi alır")
    void uidKararlidir() {
        // Kararlı UID sayesinde etkinlik tekrar indirildiğinde takvim
        // uygulaması yeni kayıt açmaz, mevcut olanı günceller.
        Etkinlik e = ornekEtkinlik("Test", LocalDate.now(), LocalDate.now());

        assertThat(uret(e)).contains("UID:etkinlik-7@");
        assertThat(uret(e)).contains("UID:etkinlik-7@");
    }

    @Test
    @DisplayName("Türkçe karakterler UTF-8 olarak korunur")
    void turkceKarakterlerKorunur() {
        String ics = uret(ornekEtkinlik("Güz Yarıyılı Şenliği ĞÜŞİÖÇ",
                LocalDate.now(), LocalDate.now()));

        assertThat(ics).contains("Güz Yarıyılı Şenliği ĞÜŞİÖÇ");
    }

    @Test
    @DisplayName("özel karakterler RFC 5545 kurallarına göre kaçışlanır")
    void kacislamaDogruYapilir() {
        assertThat(servis.kacisla("Sınav, salon A")).isEqualTo("Sınav\\, salon A");
        assertThat(servis.kacisla("Saat: 10;00")).isEqualTo("Saat: 10\\;00");
        assertThat(servis.kacisla("Yol\\dizin")).isEqualTo("Yol\\\\dizin");
        assertThat(servis.kacisla("Satır1\nSatır2")).isEqualTo("Satır1\\nSatır2");
        assertThat(servis.kacisla("Satır1\r\nSatır2")).isEqualTo("Satır1\\nSatır2");
        assertThat(servis.kacisla(null)).isEmpty();
    }

    @Test
    @DisplayName("ters bölü ÖNCE kaçışlanır (sıra bağımlılığı)")
    void tersBoluOnceKacislanir() {
        // Sıra yanlış olsaydı, virgül için üretilen "\," ifadesinin ters bölüsü
        // de kaçışlanır ve "\\," gibi bozuk bir çıktı oluşurdu.
        assertThat(servis.kacisla("a\\b,c")).isEqualTo("a\\\\b\\,c");
    }

    @Test
    @DisplayName("uzun satırlar 75 okteti aşmayacak şekilde katlanır")
    void uzunSatirlarKatlanir() {
        String uzunAd = "Çok Uzun Bir Etkinlik Adı ".repeat(6);
        String ics = uret(ornekEtkinlik(uzunAd, LocalDate.now(), LocalDate.now()));

        for (String satir : ics.split("\r\n")) {
            assertThat(satir.getBytes(StandardCharsets.UTF_8).length)
                    .as("Satır 75 okteti aşmamalı: %s", satir)
                    .isLessThanOrEqualTo(75);
        }
    }

    @Test
    @DisplayName("katlama Türkçe karakteri ortadan bölmez")
    void katlamaKarakteriBolmez() {
        // Ölçüm karakter değil OKTET üzerinden yapılır. Bir karakterin bayt
        // dizisi ortadan bölünürse dosya bozulur ve içerik okunamaz hale gelir.
        String uzunAd = "ğüşıöçĞÜŞİÖÇ".repeat(12);
        String ics = uret(ornekEtkinlik(uzunAd, LocalDate.now(), LocalDate.now()));

        // Katlanan satırlar birleştirilince orijinal metin geri gelmeli
        String birlestirilmis = ics.replace("\r\n ", "");
        assertThat(birlestirilmis).contains(uzunAd);
        assertThat(ics).doesNotContain("�");   // bozuk karakter işareti
    }

    @Test
    @DisplayName("birden fazla etkinlik tek takvim dosyasında toplanır")
    void topluDisaAktarma() {
        List<Etkinlik> etkinlikler = List.of(
                ornekEtkinlik("Birinci", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1)),
                ornekEtkinlik("İkinci", LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 3)));

        String ics = new String(servis.olustur(etkinlikler), StandardCharsets.UTF_8);

        assertThat(ics.split("BEGIN:VEVENT", -1)).hasSize(3);   // 2 etkinlik = 3 parça
        assertThat(ics).containsOnlyOnce("BEGIN:VCALENDAR");
        assertThat(ics).containsOnlyOnce("END:VCALENDAR");
    }

    @Test
    @DisplayName("dosya adındaki Türkçe karakterler sadeleştirilir")
    void dosyaAdiSadelestirilir() {
        assertThat(servis.dosyaAdi("Güz Yarıyılı Sınavları"))
                .isEqualTo("Guz_Yariyili_Sinavlari.ics");
        assertThat(servis.dosyaAdi("İÇ/DIŞ: Değerlendirme"))
                .isEqualTo("IC_DIS_Degerlendirme.ics");
    }
}

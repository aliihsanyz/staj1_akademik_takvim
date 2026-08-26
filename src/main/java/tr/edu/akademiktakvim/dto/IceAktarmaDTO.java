package tr.edu.akademiktakvim.dto;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import tr.edu.akademiktakvim.domain.enums.Donem;

/**
 * PDF ice aktarma akisinin DTO'lari.
 *
 * <p>Akis iki asamalidir ve bu bilincli bir tasarimdir:</p>
 * <ol>
 *   <li><b>Onizleme:</b> PDF yuklenir, ayristirilir, sonuc kullaniciya
 *       GOSTERILIR - ama veritabanina YAZILMAZ.</li>
 *   <li><b>Onay:</b> Kullanici satirlari duzeltir, onaylar, ancak o zaman kaydedilir.</li>
 * </ol>
 *
 * <p>Tek asamali olsaydi, standart olmayan bir PDF'ten cikan hatali onlarca
 * kayit dogrudan takvime girer ve elle temizlenmesi gerekirdi.</p>
 */
public final class IceAktarmaDTO {

    private IceAktarmaDTO() {
    }

    /**
     * PDF'ten cikarilan tek bir aday etkinlik.
     *
     * @param guvenSkoru   0-100 arasi; ayristirmanin ne kadar guvenilir oldugu.
     *                     On yuz dusuk skorlu satirlari vurgulayarak kullanicinin
     *                     dikkatini oraya ceker.
     * @param uyarilar     bu satirla ilgili insan okuyabilir notlar
     * @param kaynakSatir  PDF'teki ham satir; kullanici karsilastirabilsin diye saklanir
     */
    public record AyristirilanEtkinlik(
            String ad,

            @JsonFormat(pattern = "yyyy-MM-dd")
            LocalDate baslangicTarihi,

            @JsonFormat(pattern = "yyyy-MM-dd")
            LocalDate bitisTarihi,

            Donem donem,
            Long kategoriId,
            String kategoriAdi,
            int guvenSkoru,
            List<String> uyarilar,
            String kaynakSatir
    ) {
    }

    /**
     * Onizleme yaniti.
     *
     * @param ayristirilamayanSatirlar tarih bulunamadigi icin atlanan satirlar.
     *                                 Gizlenmez, kullaniciya gosterilir: belki
     *                                 gercekten onemli bir satir kacirilmistir.
     */
    public record OnizlemeYaniti(
            String dosyaAdi,
            int toplamSatir,
            List<AyristirilanEtkinlik> etkinlikler,
            List<String> ayristirilamayanSatirlar,
            Istatistik istatistik
    ) {
    }

    /** Onizleme ozeti - kullaniciya "ne kadari yakalandi" bilgisini verir. */
    public record Istatistik(
            int bulunanEtkinlik,
            int yuksekGuven,
            int dusukGuven,
            int atlananSatir
    ) {
    }

    /**
     * Onay istegi: kullanicinin gozden gecirip duzelttigi liste.
     *
     * <p>Onizleme sonucu SUNUCUDA saklanmaz; kullanici duzelttigi listeyi geri
     * gonderir. Boylece sunucuda oturum durumu tutmak gerekmez ve kullanicinin
     * yaptigi her duzeltme kesin olarak dikkate alinir.</p>
     */
    public record OnayIstegi(
            @NotEmpty(message = "İçe aktarılacak en az bir etkinlik olmalıdır")
            @Valid
            List<OnaylananEtkinlik> etkinlikler,

            @NotNull(message = "Eğitim yılı seçilmelidir")
            Long egitimYiliId,

            @NotNull(message = "Birim seçilmelidir")
            Long birimId,

            String dosyaAdi
    ) {
    }

    /** Kullanicinin onayladigi tek etkinlik. */
    public record OnaylananEtkinlik(
            @NotNull(message = "Etkinlik adı boş bırakılamaz")
            String ad,

            @NotNull(message = "Başlangıç tarihi boş bırakılamaz")
            @JsonFormat(pattern = "yyyy-MM-dd")
            LocalDate baslangicTarihi,

            @NotNull(message = "Bitiş tarihi boş bırakılamaz")
            @JsonFormat(pattern = "yyyy-MM-dd")
            LocalDate bitisTarihi,

            @NotNull(message = "Dönem boş bırakılamaz")
            Donem donem,

            @NotNull(message = "Kategori boş bırakılamaz")
            Long kategoriId
    ) {
    }

    /**
     * Onay sonucu.
     *
     * @param hatalar kaydedilemeyen satirlarin sebepleri. Bir satirin hatasi
     *                digerlerini engellemez; basarililar kaydedilir, hatalar
     *                kullaniciya bildirilir.
     */
    public record OnaySonucu(
            int eklenenAdet,
            int hataliAdet,
            List<String> hatalar
    ) {
    }
}

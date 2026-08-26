package tr.edu.akademiktakvim.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * Etkinligin on yuze donen goruntusu.
 *
 * <p>Entity yerine DTO donmenin iki nedeni var: (1) JPA lazy iliskilerinin
 * serilestirme sirasinda patlamasini onlemek, (2) veritabani yapisini disariya
 * sizdirmamak.</p>
 *
 * @param kalanGunMetni sunucuda hesaplanan geri sayim metni, orn.
 *                      "Baslamasina 10 gun kaldi" / "Devam ediyor" /
 *                      "Uzerinden 5 gun gecti". Sunucuda hesaplanir ki tarayici
 *                      saati yanlis ayarli olan kullanicilar da dogru bilgi gorsun
 *                      (is analizi Bolum 5.2).
 */
public record EtkinlikGorunumDTO(

        Long id,
        String ad,
        String aciklama,

        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate baslangicTarihi,

        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate bitisTarihi,

        boolean tekGun,

        String donem,
        String donemEtiketi,

        Long egitimYiliId,
        String egitimYiliAdi,

        Long kategoriId,
        String kategoriAdi,
        String kategoriRengi,

        Long birimId,
        String birimAdi,
        boolean genelBirim,

        String kalanGunMetni,
        DurumTuru durum
) {

    /** Etkinligin bugune gore konumu. On yuzde renklendirme icin kullanilir. */
    public enum DurumTuru {
        /** Henuz baslamamis. */
        GELECEK,
        /** Bugun devam ediyor. */
        DEVAM_EDIYOR,
        /** Bitmis. */
        GECMIS
    }
}

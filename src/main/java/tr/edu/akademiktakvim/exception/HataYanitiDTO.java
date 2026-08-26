package tr.edu.akademiktakvim.exception;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Tum hata yanitlarinin ortak govdesi.
 *
 * <p>Tek bir hata sozlesmesi olmasi, on yuzun her hatayi ayni sekilde
 * isleyebilmesini saglar; her uc icin ayri hata bicimi tahmin etmesi gerekmez.</p>
 *
 * @param hataKodu     makinenin okuyacagi sabit kod, orn. "IS_KURALI_IHLALI".
 *                     On yuz metne degil bu koda gore dallanmalidir.
 * @param mesaj        kullaniciya gosterilebilecek Turkce aciklama
 * @param alanHatalari form dogrulama hatalarinda alan bazli detaylar
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record HataYanitiDTO(
        LocalDateTime zaman,
        int durum,
        String hataKodu,
        String mesaj,
        String yol,
        List<AlanHatasi> alanHatalari
) {

    /** Tek bir form alanina ait dogrulama hatasi. */
    public record AlanHatasi(String alan, String mesaj) {
    }

    /** Alan hatasi icermeyen basit hata yaniti uretir. */
    public static HataYanitiDTO of(int durum, String hataKodu, String mesaj, String yol) {
        return new HataYanitiDTO(LocalDateTime.now(), durum, hataKodu, mesaj, yol, null);
    }
}

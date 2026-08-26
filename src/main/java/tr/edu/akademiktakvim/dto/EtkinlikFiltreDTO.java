package tr.edu.akademiktakvim.dto;

import java.util.List;
import java.util.Set;

import tr.edu.akademiktakvim.domain.enums.Donem;

/**
 * Takvim listeleme / PDF / ICS uclarinda kullanilan filtre.
 *
 * <p>Tum alanlar istege baglidir; {@code null} verilen alan icin filtre
 * uygulanmaz. Boylece tek bir sorgu metodu hem "her sey" hem de dar filtreler
 * icin calisir.</p>
 *
 * @param birimId      secilen birim. Is analizi Bolum 5.1: kullanici ayni anda
 *                     yalnizca BIR birim secebilir. Bir fakulte secildiginde
 *                     o fakultenin etkinlikleriyle birlikte "Universite Geneli"
 *                     etkinlikleri de listelenir - gercek akademik takvimlerde
 *                     genel tarihler tum fakulteleri baglar.
 * @param kategoriIdler secilen kategoriler. Bolum 5.1: kategoride COKLU secim yapilabilir.
 */
public record EtkinlikFiltreDTO(
        Long egitimYiliId,
        Long birimId,
        Set<Long> kategoriIdler,
        Donem donem,
        String arama
) {

    /** Bos filtre - hicbir kisitlama yok. */
    public static EtkinlikFiltreDTO bos() {
        return new EtkinlikFiltreDTO(null, null, null, null, null);
    }

    public boolean kategoriFiltresiVarMi() {
        return kategoriIdler != null && !kategoriIdler.isEmpty();
    }

    public boolean aramaVarMi() {
        return arama != null && !arama.isBlank();
    }

    /**
     * PDF basliginda "hangi filtrelerle uretildi" satirini yazabilmek icin
     * kullanilan yardimci; okunabilir etiketler disaridan verilir.
     */
    public record Ozet(String egitimYili, String birim, List<String> kategoriler, String donem) {
    }
}

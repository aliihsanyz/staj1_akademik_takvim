package tr.edu.akademiktakvim.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import tr.edu.akademiktakvim.domain.enums.BirimTuru;

/**
 * Sistem tanimlarina (birim / kategori / egitim yili) ait DTO'lar.
 *
 * <p>Ic ice record olarak toplandi; her biri icin ayri dosya acmak paketi
 * gereksiz yere kalabaliklastirirdi. Is analizi Bolum 6.2: bu tanimlar panelden
 * yonetilir, yeni fakulte veya kategori eklemek icin KOD DEGISIKLIGI GEREKMEZ.</p>
 */
public final class TanimDTO {

    private TanimDTO() {
        // Yalnizca ic siniflari barindirir
    }

    // ------------------------------------------------------------------ BIRIM

    /** Birim okuma yaniti. */
    public record BirimGorunum(Long id, String ad, String kod, BirimTuru tur,
                               String turEtiketi, boolean aktif) {
    }

    /** Birim ekleme / guncelleme istegi. */
    public record BirimIstek(
            @NotBlank(message = "Birim adı boş bırakılamaz")
            @Size(max = 150, message = "Birim adı en fazla 150 karakter olabilir")
            String ad,

            @NotBlank(message = "Birim kodu boş bırakılamaz")
            @Size(max = 50, message = "Birim kodu en fazla 50 karakter olabilir")
            String kod,

            @NotNull(message = "Birim türü boş bırakılamaz")
            BirimTuru tur,

            boolean aktif
    ) {
    }

    // --------------------------------------------------------------- KATEGORI

    /** Kategori okuma yaniti. */
    public record KategoriGorunum(Long id, String ad, String kod, String renk,
                                  int sira, boolean aktif) {
    }

    /** Kategori ekleme / guncelleme istegi. */
    public record KategoriIstek(
            @NotBlank(message = "Kategori adı boş bırakılamaz")
            @Size(max = 100, message = "Kategori adı en fazla 100 karakter olabilir")
            String ad,

            @NotBlank(message = "Kategori kodu boş bırakılamaz")
            @Size(max = 50, message = "Kategori kodu en fazla 50 karakter olabilir")
            String kod,

            @NotBlank(message = "Renk boş bırakılamaz")
            @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Renk #RRGGBB biçiminde olmalıdır")
            String renk,

            int sira,
            boolean aktif
    ) {
    }

    // ------------------------------------------------------------ EGITIM YILI

    /** Egitim yili okuma yaniti. */
    public record EgitimYiliGorunum(Long id, String ad,
                                    @JsonFormat(pattern = "yyyy-MM-dd") LocalDate baslangicTarihi,
                                    @JsonFormat(pattern = "yyyy-MM-dd") LocalDate bitisTarihi,
                                    boolean aktif) {
    }

    /** Egitim yili ekleme / guncelleme istegi. */
    public record EgitimYiliIstek(
            @NotBlank(message = "Eğitim yılı adı boş bırakılamaz")
            @Pattern(regexp = "^\\d{4}-\\d{4}$", message = "Eğitim yılı 2025-2026 biçiminde olmalıdır")
            String ad,

            @NotNull(message = "Başlangıç tarihi boş bırakılamaz")
            @JsonFormat(pattern = "yyyy-MM-dd")
            LocalDate baslangicTarihi,

            @NotNull(message = "Bitiş tarihi boş bırakılamaz")
            @JsonFormat(pattern = "yyyy-MM-dd")
            LocalDate bitisTarihi,

            boolean aktif
    ) {
    }
}

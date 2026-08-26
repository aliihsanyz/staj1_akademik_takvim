package tr.edu.akademiktakvim.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tr.edu.akademiktakvim.domain.enums.Donem;

/**
 * Etkinlik ekleme ve guncelleme isteklerinin govdesi.
 *
 * <p>Ekleme ile guncelleme ayni alanlari aldigi icin tek bir record kullanildi;
 * iki ayri sinif tutmak yalnizca kopya kod uretirdi.</p>
 *
 * <p>Buradaki anotasyonlar YALNIZCA alan bazli kontrolleri yapar (bos mu, cok uzun mu).
 * "Bitis tarihi baslangictan once olamaz" gibi alanlar arasi is kurallari
 * {@code EtkinlikDogrulamaService} icinde denetlenir - cunku bunlar bir form
 * dogrulamasi degil, is kuralidir ve PDF ice aktarma yolunda da calismalidir.</p>
 *
 * @param ad             etkinlik adi (IS KURALI 1: bos birakilamaz)
 * @param aciklama       istege bagli uzun aciklama; PDF ciktisina dahil edilmez
 * @param baslangicTarihi etkinligin baslangic gunu
 * @param bitisTarihi    etkinligin bitis gunu (tek gunluk etkinlikte baslangic ile ayni)
 * @param donem          GUZ / BAHAR / YAZ
 * @param egitimYiliId   ilgili egitim yilinin kimligi
 * @param kategoriId     ilgili kategorinin kimligi
 * @param birimId        ilgili birimin kimligi (universite geneli icin GENEL birim)
 */
public record EtkinlikIstekDTO(

        @NotBlank(message = "Etkinlik adı boş bırakılamaz")
        @Size(max = 250, message = "Etkinlik adı en fazla 250 karakter olabilir")
        String ad,

        @Size(max = 5000, message = "Açıklama en fazla 5000 karakter olabilir")
        String aciklama,

        @NotNull(message = "Başlangıç tarihi boş bırakılamaz")
        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate baslangicTarihi,

        @NotNull(message = "Bitiş tarihi boş bırakılamaz")
        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate bitisTarihi,

        @NotNull(message = "Dönem boş bırakılamaz")
        Donem donem,

        @NotNull(message = "Eğitim yılı boş bırakılamaz")
        Long egitimYiliId,

        @NotNull(message = "Kategori boş bırakılamaz")
        Long kategoriId,

        @NotNull(message = "Birim boş bırakılamaz")
        Long birimId
) {
}

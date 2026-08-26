package tr.edu.akademiktakvim.mapper;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;

import tr.edu.akademiktakvim.domain.Etkinlik;
import tr.edu.akademiktakvim.dto.EtkinlikGorunumDTO;
import tr.edu.akademiktakvim.dto.EtkinlikGorunumDTO.DurumTuru;

/**
 * Etkinlik entity'sini on yuz gorunumune cevirir ve geri sayim metnini uretir.
 *
 * <p>MapStruct gibi bir kod ureteci yerine elle yazildi: tek bir donusum var,
 * icinde is mantigi (geri sayim) bulunuyor ve projeye ek bagimlilik
 * getirmemek istiyoruz.</p>
 *
 * <p><b>Neden {@link Clock} enjekte ediliyor?</b> "Baslamasina 10 gun kaldi"
 * metni bugunun tarihine baglidir. {@code LocalDate.now()} dogrudan
 * cagrilsaydi birim testleri takvim gectikce kirilirdi. Clock sayesinde
 * testte sabit bir tarih verilebilir.</p>
 */
@Component
public class EtkinlikMapper {

    private final Clock saat;

    public EtkinlikMapper(Clock saat) {
        this.saat = saat;
    }

    /**
     * Entity'yi gorunum DTO'suna cevirir.
     *
     * <p>Cagirmadan once iliskilerin yuklenmis olmasi gerekir (JOIN FETCH),
     * aksi halde her alan icin ayri sorgu calisir.</p>
     */
    public EtkinlikGorunumDTO gorunumeCevir(Etkinlik e) {
        LocalDate bugun = LocalDate.now(saat);
        DurumTuru durum = durumHesapla(e.getBaslangicTarihi(), e.getBitisTarihi(), bugun);

        return new EtkinlikGorunumDTO(
                e.getId(),
                e.getAd(),
                e.getAciklama(),
                e.getBaslangicTarihi(),
                e.getBitisTarihi(),
                e.tekGunMu(),
                e.getDonem().name(),
                e.getDonem().getEtiket(),
                e.getEgitimYili().getId(),
                e.getEgitimYili().getAd(),
                e.getKategori().getId(),
                e.getKategori().getAd(),
                e.getKategori().getRenk(),
                e.getBirim().getId(),
                e.getBirim().getAd(),
                e.getBirim().genelMi(),
                kalanGunMetni(e.getBaslangicTarihi(), e.getBitisTarihi(), bugun),
                durum);
    }

    /**
     * Etkinligin bugune gore konumunu belirler.
     *
     * <p>Baslangic ve bitis gunleri DAHIL kabul edilir; yani bitis gunu
     * etkinlik hala "devam ediyor" sayilir.</p>
     */
    DurumTuru durumHesapla(LocalDate baslangic, LocalDate bitis, LocalDate bugun) {
        if (bugun.isBefore(baslangic)) {
            return DurumTuru.GELECEK;
        }
        if (bugun.isAfter(bitis)) {
            return DurumTuru.GECMIS;
        }
        return DurumTuru.DEVAM_EDIYOR;
    }

    /**
     * Is analizi Bolum 5.2'deki geri sayim metnini uretir.
     *
     * <p>Ornekler: "Başlamasına 10 gün kaldı", "Yarın başlıyor",
     * "Bugün başlıyor", "Devam ediyor", "Üzerinden 5 gün geçti".</p>
     */
    String kalanGunMetni(LocalDate baslangic, LocalDate bitis, LocalDate bugun) {
        if (bugun.isBefore(baslangic)) {
            long kalan = ChronoUnit.DAYS.between(bugun, baslangic);
            if (kalan == 1) {
                return "Yarın başlıyor";
            }
            return "Başlamasına " + kalan + " gün kaldı";
        }

        if (bugun.isAfter(bitis)) {
            long gecen = ChronoUnit.DAYS.between(bitis, bugun);
            if (gecen == 1) {
                return "Dün sona erdi";
            }
            return "Üzerinden " + gecen + " gün geçti";
        }

        // Bugun etkinlik araliginin icinde
        if (bugun.isEqual(baslangic)) {
            return baslangic.isEqual(bitis) ? "Bugün" : "Bugün başlıyor";
        }
        if (bugun.isEqual(bitis)) {
            return "Bugün sona eriyor";
        }
        long kalanBitise = ChronoUnit.DAYS.between(bugun, bitis);
        return "Devam ediyor (bitmesine " + kalanBitise + " gün)";
    }
}

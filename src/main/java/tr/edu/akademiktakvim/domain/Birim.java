package tr.edu.akademiktakvim.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import tr.edu.akademiktakvim.domain.enums.BirimTuru;

/**
 * Fakulte, enstitu, meslek yuksekokulu veya "Universite Geneli" kaydi.
 *
 * <p>Super Admin panelden yeni birim ekleyebilir; kod degisikligi gerekmez
 * (is analizi Bolum 6.2).</p>
 */
@Entity
@Table(name = "birim")
public class Birim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String ad;

    /** Teknik kod (orn. "MUH_FAK"). Degismez kimlik olarak kullanilir. */
    @Column(nullable = false, length = 50, unique = true)
    private String kod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BirimTuru tur;

    /** IS KURALI 4: pasif birim yeni kayitlarda secilemez. */
    @Column(nullable = false)
    private boolean aktif = true;

    @CreationTimestamp
    @Column(name = "olusturma_zamani", nullable = false, updatable = false)
    private LocalDateTime olusturmaZamani;

    protected Birim() {
        // JPA icin gerekli
    }

    public Birim(String ad, String kod, BirimTuru tur) {
        this.ad = ad;
        this.kod = kod;
        this.tur = tur;
    }

    /** Bu birim "Universite Geneli" mi? Filtreleme mantiginda kullanilir. */
    public boolean genelMi() {
        return tur == BirimTuru.GENEL;
    }

    public Long getId() { return id; }
    public String getAd() { return ad; }
    public void setAd(String ad) { this.ad = ad; }
    public String getKod() { return kod; }
    public void setKod(String kod) { this.kod = kod; }
    public BirimTuru getTur() { return tur; }
    public void setTur(BirimTuru tur) { this.tur = tur; }
    public boolean isAktif() { return aktif; }
    public void setAktif(boolean aktif) { this.aktif = aktif; }
    public LocalDateTime getOlusturmaZamani() { return olusturmaZamani; }
}

package tr.edu.akademiktakvim.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Egitim ogretim yili, orn. "2025-2026". */
@Entity
@Table(name = "egitim_yili")
public class EgitimYili {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20, unique = true)
    private String ad;

    @Column(name = "baslangic_tarihi", nullable = false)
    private LocalDate baslangicTarihi;

    @Column(name = "bitis_tarihi", nullable = false)
    private LocalDate bitisTarihi;

    @Column(nullable = false)
    private boolean aktif = true;

    @CreationTimestamp
    @Column(name = "olusturma_zamani", nullable = false, updatable = false)
    private LocalDateTime olusturmaZamani;

    protected EgitimYili() {
    }

    public EgitimYili(String ad, LocalDate baslangicTarihi, LocalDate bitisTarihi) {
        this.ad = ad;
        this.baslangicTarihi = baslangicTarihi;
        this.bitisTarihi = bitisTarihi;
    }

    public Long getId() { return id; }
    public String getAd() { return ad; }
    public void setAd(String ad) { this.ad = ad; }
    public LocalDate getBaslangicTarihi() { return baslangicTarihi; }
    public void setBaslangicTarihi(LocalDate baslangicTarihi) { this.baslangicTarihi = baslangicTarihi; }
    public LocalDate getBitisTarihi() { return bitisTarihi; }
    public void setBitisTarihi(LocalDate bitisTarihi) { this.bitisTarihi = bitisTarihi; }
    public boolean isAktif() { return aktif; }
    public void setAktif(boolean aktif) { this.aktif = aktif; }
    public LocalDateTime getOlusturmaZamani() { return olusturmaZamani; }
}

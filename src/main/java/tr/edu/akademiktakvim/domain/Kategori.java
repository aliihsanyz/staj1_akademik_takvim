package tr.edu.akademiktakvim.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Etkinlik kategorisi (Ders ve Sinav Tarihleri, Kayit ve Basvuru, Resmi Tatiller,
 * Akademik ve Idari Etkinlikler). Super Admin yenisini ekleyebilir.
 */
@Entity
@Table(name = "kategori")
public class Kategori {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String ad;

    @Column(nullable = false, length = 50, unique = true)
    private String kod;

    /** Arayuzdeki rozet rengi (#RRGGBB). Veritabaninda CHECK ile dogrulanir. */
    @Column(nullable = false, length = 7)
    private String renk = "#64748B";

    /** Listeleme sirasi. Kucuk deger once gosterilir. */
    @Column(nullable = false)
    private int sira = 0;

    @Column(nullable = false)
    private boolean aktif = true;

    @CreationTimestamp
    @Column(name = "olusturma_zamani", nullable = false, updatable = false)
    private LocalDateTime olusturmaZamani;

    protected Kategori() {
    }

    public Kategori(String ad, String kod, String renk, int sira) {
        this.ad = ad;
        this.kod = kod;
        this.renk = renk;
        this.sira = sira;
    }

    public Long getId() { return id; }
    public String getAd() { return ad; }
    public void setAd(String ad) { this.ad = ad; }
    public String getKod() { return kod; }
    public void setKod(String kod) { this.kod = kod; }
    public String getRenk() { return renk; }
    public void setRenk(String renk) { this.renk = renk; }
    public int getSira() { return sira; }
    public void setSira(int sira) { this.sira = sira; }
    public boolean isAktif() { return aktif; }
    public void setAktif(boolean aktif) { this.aktif = aktif; }
    public LocalDateTime getOlusturmaZamani() { return olusturmaZamani; }
}

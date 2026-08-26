package tr.edu.akademiktakvim.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import tr.edu.akademiktakvim.domain.enums.Donem;

/**
 * Akademik takvim etkinligi - sistemin ana varligi.
 *
 * <p>Iliskiler bilerek LAZY tanimlandi; listeleme sorgularinda gereksiz JOIN
 * olusmasin diye. Servis katmani ihtiyac duydugunda JOIN FETCH kullanir.</p>
 */
@Entity
@Table(name = "etkinlik")
public class Etkinlik {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 250)
    private String ad;

    /** Istege bagli uzun aciklama. PDF ciktisina DAHIL EDILMEZ (is analizi Bolum 5.4). */
    @Column(columnDefinition = "TEXT")
    private String aciklama;

    @Column(name = "baslangic_tarihi", nullable = false)
    private LocalDate baslangicTarihi;

    @Column(name = "bitis_tarihi", nullable = false)
    private LocalDate bitisTarihi;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Donem donem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "egitim_yili_id", nullable = false)
    private EgitimYili egitimYili;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "kategori_id", nullable = false)
    private Kategori kategori;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "birim_id", nullable = false)
    private Birim birim;

    @Column(length = 60)
    private String olusturan;

    @Column(length = 60)
    private String guncelleyen;

    @CreationTimestamp
    @Column(name = "olusturma_zamani", nullable = false, updatable = false)
    private LocalDateTime olusturmaZamani;

    @UpdateTimestamp
    @Column(name = "guncelleme_zamani")
    private LocalDateTime guncellemeZamani;

    protected Etkinlik() {
        // JPA icin gerekli
    }

    public Etkinlik(String ad, LocalDate baslangicTarihi, LocalDate bitisTarihi,
                    Donem donem, EgitimYili egitimYili, Kategori kategori, Birim birim) {
        this.ad = ad;
        this.baslangicTarihi = baslangicTarihi;
        this.bitisTarihi = bitisTarihi;
        this.donem = donem;
        this.egitimYili = egitimYili;
        this.kategori = kategori;
        this.birim = birim;
    }

    /** Tek gunluk etkinlik mi? Arayuzde tarih gosterimini kisaltmak icin. */
    public boolean tekGunMu() {
        return baslangicTarihi.equals(bitisTarihi);
    }

    public Long getId() { return id; }
    public String getAd() { return ad; }
    public void setAd(String ad) { this.ad = ad; }
    public String getAciklama() { return aciklama; }
    public void setAciklama(String aciklama) { this.aciklama = aciklama; }
    public LocalDate getBaslangicTarihi() { return baslangicTarihi; }
    public void setBaslangicTarihi(LocalDate baslangicTarihi) { this.baslangicTarihi = baslangicTarihi; }
    public LocalDate getBitisTarihi() { return bitisTarihi; }
    public void setBitisTarihi(LocalDate bitisTarihi) { this.bitisTarihi = bitisTarihi; }
    public Donem getDonem() { return donem; }
    public void setDonem(Donem donem) { this.donem = donem; }
    public EgitimYili getEgitimYili() { return egitimYili; }
    public void setEgitimYili(EgitimYili egitimYili) { this.egitimYili = egitimYili; }
    public Kategori getKategori() { return kategori; }
    public void setKategori(Kategori kategori) { this.kategori = kategori; }
    public Birim getBirim() { return birim; }
    public void setBirim(Birim birim) { this.birim = birim; }
    public String getOlusturan() { return olusturan; }
    public void setOlusturan(String olusturan) { this.olusturan = olusturan; }
    public String getGuncelleyen() { return guncelleyen; }
    public void setGuncelleyen(String guncelleyen) { this.guncelleyen = guncelleyen; }
    public LocalDateTime getOlusturmaZamani() { return olusturmaZamani; }
    public LocalDateTime getGuncellemeZamani() { return guncellemeZamani; }
}

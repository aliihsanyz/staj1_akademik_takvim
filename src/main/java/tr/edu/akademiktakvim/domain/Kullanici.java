package tr.edu.akademiktakvim.domain;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import tr.edu.akademiktakvim.domain.enums.Rol;

/**
 * Yonetici kullanicisi (Super Admin veya Birim Yoneticisi).
 *
 * <p>Ogrenci/Personel icin kullanici kaydi tutulmaz; takvim goruntuleme
 * herkese aciktir.</p>
 */
@Entity
@Table(name = "kullanici")
public class Kullanici {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "kullanici_adi", nullable = false, length = 60, unique = true)
    private String kullaniciAdi;

    /** BCrypt hash. Duz sifre hicbir zaman saklanmaz. */
    @Column(name = "sifre_hash", nullable = false, length = 100)
    private String sifreHash;

    @Column(name = "ad_soyad", nullable = false, length = 120)
    private String adSoyad;

    @Column(length = 150)
    private String eposta;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Rol rol;

    @Column(nullable = false)
    private boolean aktif = true;

    @Column(name = "son_giris")
    private LocalDateTime sonGiris;

    @CreationTimestamp
    @Column(name = "olusturma_zamani", nullable = false, updatable = false)
    private LocalDateTime olusturmaZamani;

    /**
     * Birim Yoneticisinin sorumlu oldugu birimler.
     *
     * <p>EAGER secildi: yetki kontrolu her istekte gerekiyor ve kullanici basina
     * birim sayisi tek haneli. Boylece LazyInitializationException riski de kalmaz.</p>
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "kullanici_birim",
            joinColumns = @JoinColumn(name = "kullanici_id"),
            inverseJoinColumns = @JoinColumn(name = "birim_id")
    )
    private Set<Birim> birimler = new LinkedHashSet<>();

    protected Kullanici() {
    }

    public Kullanici(String kullaniciAdi, String sifreHash, String adSoyad, Rol rol) {
        this.kullaniciAdi = kullaniciAdi;
        this.sifreHash = sifreHash;
        this.adSoyad = adSoyad;
        this.rol = rol;
    }

    /** Super Admin her birime erisir; Birim Yoneticisi yalnizca kendi birimlerine. */
    public boolean superAdminMi() {
        return rol == Rol.SUPER_ADMIN;
    }

    public void birimEkle(Birim birim) {
        birimler.add(birim);
    }

    public Long getId() { return id; }
    public String getKullaniciAdi() { return kullaniciAdi; }
    public void setKullaniciAdi(String kullaniciAdi) { this.kullaniciAdi = kullaniciAdi; }
    public String getSifreHash() { return sifreHash; }
    public void setSifreHash(String sifreHash) { this.sifreHash = sifreHash; }
    public String getAdSoyad() { return adSoyad; }
    public void setAdSoyad(String adSoyad) { this.adSoyad = adSoyad; }
    public String getEposta() { return eposta; }
    public void setEposta(String eposta) { this.eposta = eposta; }
    public Rol getRol() { return rol; }
    public void setRol(Rol rol) { this.rol = rol; }
    public boolean isAktif() { return aktif; }
    public void setAktif(boolean aktif) { this.aktif = aktif; }
    public LocalDateTime getSonGiris() { return sonGiris; }
    public void setSonGiris(LocalDateTime sonGiris) { this.sonGiris = sonGiris; }
    public Set<Birim> getBirimler() { return birimler; }
    public void setBirimler(Set<Birim> birimler) { this.birimler = birimler; }
    public LocalDateTime getOlusturmaZamani() { return olusturmaZamani; }
}

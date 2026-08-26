package tr.edu.akademiktakvim.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import tr.edu.akademiktakvim.domain.enums.IslemTuru;

/**
 * Islem kaydi (Audit Log) - IS KURALI 6.
 *
 * <p><b>Onemli tasarim karari:</b> {@code hedefId} bilerek yabanci anahtar
 * DEGILDIR. Is analizi "kayit veritabanindan kaldirilir, ancak islem
 * gecmisinde silme kaydi tutulur" diyor. FK olsaydi etkinlik silindiginde
 * ya bu kayit da silinirdi ya da silme islemi engellenirdi.</p>
 *
 * <p>Silinen kaydin tum alanlari {@code eskiDeger} icinde JSON olarak saklanir,
 * boylece gerektiginde neyin silindigi gorulebilir.</p>
 */
@Entity
@Table(name = "islem_kaydi")
public class IslemKaydi {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "kullanici_adi", nullable = false, length = 60)
    private String kullaniciAdi;

    @Enumerated(EnumType.STRING)
    @Column(name = "islem_turu", nullable = false, length = 20)
    private IslemTuru islemTuru;

    /** Hangi varlik uzerinde islem yapildi: ETKINLIK, BIRIM, KATEGORI... */
    @Column(name = "hedef_tur", nullable = false, length = 40)
    private String hedefTur;

    /** Yabanci anahtar DEGIL - siniftaki nota bakiniz. */
    @Column(name = "hedef_id")
    private Long hedefId;

    /** Insan okuyabilsin diye kisa ozet, orn. etkinligin adi. */
    @Column(name = "hedef_ozet", length = 250)
    private String hedefOzet;

    /** Hibernate 6 JSONB tipini yerlesik destekler; ek kutuphane gerekmez. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "eski_deger", columnDefinition = "jsonb")
    private String eskiDeger;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "yeni_deger", columnDefinition = "jsonb")
    private String yeniDeger;

    @Column(name = "ip_adresi", length = 45)
    private String ipAdresi;

    @CreationTimestamp
    @Column(name = "islem_zamani", nullable = false, updatable = false)
    private LocalDateTime islemZamani;

    protected IslemKaydi() {
        // JPA icin gerekli
    }

    public IslemKaydi(String kullaniciAdi, IslemTuru islemTuru, String hedefTur,
                      Long hedefId, String hedefOzet) {
        this.kullaniciAdi = kullaniciAdi;
        this.islemTuru = islemTuru;
        this.hedefTur = hedefTur;
        this.hedefId = hedefId;
        this.hedefOzet = hedefOzet;
    }

    public Long getId() { return id; }
    public String getKullaniciAdi() { return kullaniciAdi; }
    public IslemTuru getIslemTuru() { return islemTuru; }
    public String getHedefTur() { return hedefTur; }
    public Long getHedefId() { return hedefId; }
    public String getHedefOzet() { return hedefOzet; }
    public String getEskiDeger() { return eskiDeger; }
    public void setEskiDeger(String eskiDeger) { this.eskiDeger = eskiDeger; }
    public String getYeniDeger() { return yeniDeger; }
    public void setYeniDeger(String yeniDeger) { this.yeniDeger = yeniDeger; }
    public String getIpAdresi() { return ipAdresi; }
    public void setIpAdresi(String ipAdresi) { this.ipAdresi = ipAdresi; }
    public LocalDateTime getIslemZamani() { return islemZamani; }
}

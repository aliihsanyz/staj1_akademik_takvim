package tr.edu.akademiktakvim.domain.enums;

/**
 * Sistemdeki yonetici rolleri.
 *
 * <p>Ogrenci/Personel bu listede yoktur; onlar kimlik dogrulamasi
 * gerektirmeyen anonim kullanicilardir ve yalnizca goruntuleme yapar.</p>
 */
public enum Rol {

    /** Tum universitenin takvimini, sistem tanimlarini ve kullanicilari yonetir. */
    SUPER_ADMIN("Süper Admin"),

    /** Yalnizca sorumlu oldugu fakulte/enstitunun etkinliklerini yonetir. */
    BIRIM_YONETICISI("Birim Yöneticisi");

    private final String etiket;

    Rol(String etiket) {
        this.etiket = etiket;
    }

    public String getEtiket() {
        return etiket;
    }

    /** Spring Security'nin bekledigi "ROLE_" onekli yetki adi. */
    public String yetkiAdi() {
        return "ROLE_" + name();
    }
}

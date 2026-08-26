package tr.edu.akademiktakvim.domain.enums;

/** Islem kaydina (audit log) yazilan islem turleri. */
public enum IslemTuru {

    EKLE("Ekleme"),
    GUNCELLE("Güncelleme"),
    SIL("Silme"),
    ICE_AKTAR("PDF'ten İçe Aktarma");

    private final String etiket;

    IslemTuru(String etiket) {
        this.etiket = etiket;
    }

    public String getEtiket() {
        return etiket;
    }
}

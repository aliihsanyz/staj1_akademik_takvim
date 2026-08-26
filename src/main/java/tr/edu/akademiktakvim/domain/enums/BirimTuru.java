package tr.edu.akademiktakvim.domain.enums;

/** Birim turleri. GENEL, arayuzdeki "Genel Takvim" secenegine karsilik gelir. */
public enum BirimTuru {

    /** Universite geneli. Bu birimin etkinlikleri her birim secildiginde de gorunur. */
    GENEL("Üniversite Geneli"),
    FAKULTE("Fakülte"),
    ENSTITU("Enstitü"),
    MYO("Meslek Yüksekokulu"),
    YUKSEKOKUL("Yüksekokul");

    private final String etiket;

    BirimTuru(String etiket) {
        this.etiket = etiket;
    }

    public String getEtiket() {
        return etiket;
    }
}

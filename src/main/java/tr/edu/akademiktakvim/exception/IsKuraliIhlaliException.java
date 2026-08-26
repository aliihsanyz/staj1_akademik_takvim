package tr.edu.akademiktakvim.exception;

/**
 * Bir is kuralinin ihlal edildigini bildirir (HTTP 422 Unprocessable Entity).
 *
 * <p>Ornek: bitis tarihinin baslangictan once olmasi, pasif bir kategorinin
 * secilmesi. Bunlar sozdizimsel olarak gecerli ama is acisindan kabul
 * edilemez isteklerdir; bu yuzden 400 degil 422 donulur.</p>
 */
public class IsKuraliIhlaliException extends RuntimeException {

    private final String hataKodu;

    public IsKuraliIhlaliException(String mesaj) {
        this(mesaj, "IS_KURALI_IHLALI");
    }

    public IsKuraliIhlaliException(String mesaj, String hataKodu) {
        super(mesaj);
        this.hataKodu = hataKodu;
    }

    public String getHataKodu() {
        return hataKodu;
    }
}

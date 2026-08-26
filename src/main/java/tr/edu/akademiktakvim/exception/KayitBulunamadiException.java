package tr.edu.akademiktakvim.exception;

/** Istenen kaydin veritabaninda bulunmadigini bildirir (HTTP 404). */
public class KayitBulunamadiException extends RuntimeException {

    public KayitBulunamadiException(String mesaj) {
        super(mesaj);
    }

    /** Standart bicimde mesaj uretir, orn. "Etkinlik bulunamadi (id: 42)". */
    public static KayitBulunamadiException of(String varlikAdi, Object kimlik) {
        return new KayitBulunamadiException(varlikAdi + " bulunamadı (id: " + kimlik + ")");
    }
}

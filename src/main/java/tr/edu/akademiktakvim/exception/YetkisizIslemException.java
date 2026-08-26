package tr.edu.akademiktakvim.exception;

/**
 * Kullanicinin kimligi dogrulanmis ancak bu kayit uzerinde yetkisi yok
 * (HTTP 403 Forbidden).
 *
 * <p>IS KURALI 3: Birim Yoneticisi baska birimin kayitlarini degistiremez.</p>
 */
public class YetkisizIslemException extends RuntimeException {

    public YetkisizIslemException(String mesaj) {
        super(mesaj);
    }
}

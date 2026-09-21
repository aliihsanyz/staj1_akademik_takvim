package tr.edu.akademiktakvim.service.pdf;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import org.openpdf.text.BadElementException;
import org.openpdf.text.Image;

/**
 * PDF basliginda kullanilan kurum armasini saglar.
 *
 * <p>Arma, web arayuzuyle ayni dosyadan ({@code static/img/logo.png}) okunur;
 * kurum kimligi degistiginde tek bir gorsel guncellenir.</p>
 *
 * <p><b>Neden PNG?</b> OpenPDF gomulu gorsel olarak PNG/JPEG/GIF okur, WebP
 * okuyamaz. Bu yuzden depoda armanin PNG surumu tutulur.</p>
 *
 * <p><b>Neden fontlardan farkli olarak hata firlatilmiyor?</b> Font eksikse
 * Turkce karakterler bozulur ve cikti kullanilamaz; arma eksikse belge yalnizca
 * sade gorunur. Bu yuzden arma bulunamadigi durumda PDF uretimi durdurulmaz,
 * yalnizca uyari yazilir.</p>
 */
@Component
public class PdfLogoSaglayici {

    private static final Logger log = LoggerFactory.getLogger(PdfLogoSaglayici.class);

    private static final String LOGO_YOLU = "static/img/logo.png";

    /**
     * Gorselin ham baytlari; arma yoksa {@code null}.
     *
     * <p>Image nesnesi degil bayt dizisi onbellege alinir: OpenPDF'te Image
     * olcekleme/hizalama bilgisini kendi uzerinde tutar, dolayisiyla ayni ornegi
     * birden fazla belgede paylasmak yan etki uretir. Her istekte baytlardan
     * yeni bir Image uretmek hem ucuz hem guvenlidir.</p>
     */
    private byte[] logoVerisi;

    @PostConstruct
    public void yukle() {
        ClassPathResource kaynak = new ClassPathResource(LOGO_YOLU);
        if (!kaynak.exists()) {
            log.warn("Kurum armasi bulunamadi ({}). PDF ciktisi armasiz uretilecek.", LOGO_YOLU);
            return;
        }
        try (InputStream akis = kaynak.getInputStream()) {
            this.logoVerisi = akis.readAllBytes();
            log.info("PDF kurum armasi yuklendi.");
        } catch (IOException ex) {
            log.warn("Kurum armasi okunamadi ({}). PDF ciktisi armasiz uretilecek.", LOGO_YOLU, ex);
        }
    }

    /**
     * Verilen kenar uzunluguna sigdirilmis arma dondurur.
     *
     * @param kenarPuan armanin kaplayacagi kare alanin kenari (punto)
     * @return arma; dosya yoksa veya okunamiyorsa bos
     */
    public Optional<Image> arma(float kenarPuan) {
        if (logoVerisi == null) {
            return Optional.empty();
        }
        try {
            Image arma = Image.getInstance(logoVerisi);
            // scaleToFit: en-boy oranini korur, kare olmayan armalar da bozulmaz.
            arma.scaleToFit(kenarPuan, kenarPuan);
            return Optional.of(arma);
        } catch (BadElementException | IOException ex) {
            log.warn("Kurum armasi PDF'e eklenemedi.", ex);
            return Optional.empty();
        }
    }
}

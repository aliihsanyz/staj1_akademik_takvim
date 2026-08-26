package tr.edu.akademiktakvim;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Dinamik Akademik Takvim Sistemi - uygulama giris noktasi.
 *
 * <p>Olusturma / guncelleme zaman damgalari, Hibernate'in
 * {@code @CreationTimestamp} ve {@code @UpdateTimestamp} anotasyonlariyla
 * otomatik doldurulur; ayri bir denetim (auditing) yapilandirmasi gerekmez.</p>
 */
@SpringBootApplication
public class AkademikTakvimApplication {

    public static void main(String[] args) {
        SpringApplication.run(AkademikTakvimApplication.class, args);
    }
}

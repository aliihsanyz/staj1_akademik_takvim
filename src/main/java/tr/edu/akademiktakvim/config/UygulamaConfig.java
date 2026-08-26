package tr.edu.akademiktakvim.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Uygulama genelinde kullanilan basit bean tanimlari.
 */
@Configuration
public class UygulamaConfig {

    /**
     * Sistem saati bean olarak sunulur.
     *
     * <p>Boylece "kac gun kaldi" hesabi yapan siniflar {@code LocalDate.now()}
     * cagirmak yerine bu saati enjekte eder ve testlerde
     * {@code Clock.fixed(...)} ile sabitlenebilir.</p>
     *
     * <p>Saat dilimi acikca Europe/Istanbul secildi; sunucu baska bir bolgede
     * calissa bile akademik takvim gunleri Turkiye saatine gore hesaplanmalidir.</p>
     */
    @Bean
    public Clock saat() {
        return Clock.system(ZoneId.of("Europe/Istanbul"));
    }
}

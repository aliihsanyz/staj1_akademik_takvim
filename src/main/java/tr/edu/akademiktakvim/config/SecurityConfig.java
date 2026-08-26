package tr.edu.akademiktakvim.config;

import java.io.IOException;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Guvenlik yapilandirmasi.
 *
 * <p><b>Neden oturum (session) tabanli, JWT degil?</b> Sistemin tek istemcisi
 * ayni sunucudan servis edilen web arayuzu. Oturum cerezi {@code HttpOnly}
 * oldugu icin JavaScript ona erisemez; bu, XSS durumunda token calinmasini
 * yapisal olarak engeller. JWT ile ayni guvenligi saglamak icin ek kod
 * (saklama, sure dolumu, yenileme) gerekirdi.</p>
 *
 * <p><b>Yetki katmanlari:</b></p>
 * <ul>
 *   <li>Takvim goruntuleme, PDF ve ICS indirme: herkese acik (Ogrenci/Personel)</li>
 *   <li>Etkinlik CRUD ve PDF ice aktarma: Birim Yoneticisi + Super Admin</li>
 *   <li>Sistem tanimlari, kullanicilar, islem kayitlari: yalnizca Super Admin</li>
 * </ul>
 *
 * <p>Buradaki kontrol ROL bazlidir. "Birim Yoneticisi HANGI birime dokunabilir"
 * sorusu veriye baglidir ve {@code YetkiKontrolService} icinde, servis
 * katmaninda cevaplanir.</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filtreZinciri(HttpSecurity http) throws Exception {

        // CSRF jetonu cerezle tasinir; on yuzdeki JavaScript XSRF-TOKEN cerezini
        // okuyup X-XSRF-TOKEN basligina koyar. Bu yuzden cerez HttpOnly OLAMAZ.
        // (Oturum cerezi ise HttpOnly kalir - ikisi farkli cerezlerdir.)
        CsrfTokenRequestAttributeHandler csrfIsleyici = new CsrfTokenRequestAttributeHandler();
        // Spring Security 6'nin ertelenmis jeton davranisini kapatir; aksi halde
        // tek sayfalik arayuzlerde jeton cereze hic yazilmaz.
        csrfIsleyici.setCsrfRequestAttributeName(null);

        http
            .csrf(csrf -> csrf
                    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .csrfTokenRequestHandler(csrfIsleyici))

            .sessionManagement(oturum -> oturum
                    .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                    .maximumSessions(3))   // Ayni hesapla sinirsiz oturum acilmasin

            .authorizeHttpRequests(izin -> izin
                    // --- Statik on yuz dosyalari ---
                    .requestMatchers("/", "/index.html", "/admin.html",
                                     "/css/**", "/js/**", "/favicon.ico").permitAll()

                    // --- Herkese acik okuma uclari (Ogrenci / Personel) ---
                    .requestMatchers(HttpMethod.GET, "/api/saglik").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/etkinlikler/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/tanimlar/**").permitAll()

                    // --- Kimlik islemleri ---
                    .requestMatchers(HttpMethod.POST, "/api/kimlik/giris").permitAll()
                    .requestMatchers("/api/kimlik/**").authenticated()

                    // --- Yalnizca Super Admin ---
                    .requestMatchers("/api/admin/tanimlar/**").hasRole("SUPER_ADMIN")
                    .requestMatchers("/api/admin/islem-kayitlari/**").hasRole("SUPER_ADMIN")
                    .requestMatchers("/api/admin/kullanicilar/**").hasRole("SUPER_ADMIN")

                    // --- Birim Yoneticisi + Super Admin ---
                    .requestMatchers("/api/admin/**").hasAnyRole("BIRIM_YONETICISI", "SUPER_ADMIN")

                    .anyRequest().authenticated())

            // Varsayilan davranis, kimlik dogrulanmamis istekte giris SAYFASINA
            // yonlendirmektir. REST API icin bu yanlistir: on yuzun HTML degil
            // 401 gormesi gerekir. Bu yuzden giris noktasi degistiriliyor.
            .exceptionHandling(hata -> hata
                    .authenticationEntryPoint((istek, yanit, ex) ->
                            jsonHataYaz(yanit, HttpStatus.UNAUTHORIZED,
                                    "KIMLIK_DOGRULANAMADI",
                                    "Bu işlem için oturum açmanız gerekiyor."))
                    .accessDeniedHandler((istek, yanit, ex) ->
                            jsonHataYaz(yanit, HttpStatus.FORBIDDEN,
                                    "YETKISIZ_ISLEM",
                                    "Bu işlem için yetkiniz bulunmuyor.")))

            // Tarayici acilir pencereli temel kimlik dogrulama kutusu istemiyoruz
            .httpBasic(temel -> temel.disable())
            .formLogin(form -> form.disable());

        return http.build();
    }

    /** BCrypt: uyarlanabilir maliyetli, sektor standardi sifre ozetleme algoritmasi. */
    @Bean
    public PasswordEncoder sifreleyici() {
        return new BCryptPasswordEncoder();
    }

    /** Kendi JSON giris ucumuzun kimlik dogrulamayi tetikleyebilmesi icin gerekir. */
    @Bean
    public AuthenticationManager kimlikYoneticisi(AuthenticationConfiguration yapilandirma)
            throws Exception {
        return yapilandirma.getAuthenticationManager();
    }

    /**
     * Guvenlik filtresi seviyesindeki hatalari da {@code HataYanitiDTO} ile ayni
     * bicimde yazar. Bu hatalar {@code GlobalExceptionHandler}'a ulasmaz, cunku
     * filtre zinciri controller'dan oncedir.
     */
    private void jsonHataYaz(HttpServletResponse yanit, HttpStatus durum,
                             String hataKodu, String mesaj) throws IOException {
        yanit.setStatus(durum.value());
        yanit.setContentType(MediaType.APPLICATION_JSON_VALUE);
        yanit.setCharacterEncoding("UTF-8");
        yanit.getWriter().write("""
                {"durum":%d,"hataKodu":"%s","mesaj":"%s"}"""
                .formatted(durum.value(), hataKodu, mesaj));
    }
}

package tr.edu.akademiktakvim.config;

import java.util.List;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tr.edu.akademiktakvim.domain.Kullanici;
import tr.edu.akademiktakvim.repository.KullaniciRepository;

/**
 * Spring Security'nin kullaniciyi veritabanindan okumasini saglar.
 *
 * <p>Kendi {@code Kullanici} entity'mizi Spring Security'nin bekledigi
 * {@code UserDetails} arayuzune cevirir. Entity'nin dogrudan
 * {@code UserDetails} implemente etmesi tercih edilmedi: bu, alan katmanini
 * guvenlik cercevesine baglar ve entity'yi gereksiz metotlarla sisirirdi.</p>
 */
@Service
public class KullaniciDetaylariService implements UserDetailsService {

    private final KullaniciRepository kullaniciRepository;

    public KullaniciDetaylariService(KullaniciRepository kullaniciRepository) {
        this.kullaniciRepository = kullaniciRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String kullaniciAdi) throws UsernameNotFoundException {
        Kullanici kullanici = kullaniciRepository.findByKullaniciAdi(kullaniciAdi)
                // Mesaj bilerek genel tutuldu: "boyle bir kullanici yok" demek,
                // saldirgana hangi kullanici adlarinin gecerli oldugunu sizdirir.
                .orElseThrow(() -> new UsernameNotFoundException("Kullanıcı adı veya şifre hatalı."));

        return User.builder()
                .username(kullanici.getKullaniciAdi())
                .password(kullanici.getSifreHash())
                .authorities(List.of(new SimpleGrantedAuthority(kullanici.getRol().yetkiAdi())))
                .disabled(!kullanici.isAktif())
                .build();
    }
}

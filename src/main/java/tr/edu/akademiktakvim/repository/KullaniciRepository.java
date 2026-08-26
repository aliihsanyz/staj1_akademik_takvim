package tr.edu.akademiktakvim.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import tr.edu.akademiktakvim.domain.Kullanici;

public interface KullaniciRepository extends JpaRepository<Kullanici, Long> {

    Optional<Kullanici> findByKullaniciAdi(String kullaniciAdi);

    boolean existsByKullaniciAdi(String kullaniciAdi);
}

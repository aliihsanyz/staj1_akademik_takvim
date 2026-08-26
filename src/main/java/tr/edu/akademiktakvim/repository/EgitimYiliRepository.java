package tr.edu.akademiktakvim.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import tr.edu.akademiktakvim.domain.EgitimYili;

public interface EgitimYiliRepository extends JpaRepository<EgitimYili, Long> {

    Optional<EgitimYili> findByAd(String ad);

    boolean existsByAd(String ad);

    /** En yeni egitim yili basta gelsin diye ada gore tersten siralanir. */
    List<EgitimYili> findByAktifTrueOrderByAdDesc();

    List<EgitimYili> findAllByOrderByAdDesc();
}

package tr.edu.akademiktakvim.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import tr.edu.akademiktakvim.domain.Birim;
import tr.edu.akademiktakvim.domain.enums.BirimTuru;

public interface BirimRepository extends JpaRepository<Birim, Long> {

    Optional<Birim> findByKod(String kod);

    boolean existsByKod(String kod);

    /** On yuzdeki birim listesi: yalnizca aktif birimler, ada gore sirali. */
    List<Birim> findByAktifTrueOrderByAdAsc();

    List<Birim> findAllByOrderByAdAsc();

    /**
     * "Universite Geneli" birimini bulur. Sistemde bu turden tek kayit olmasi
     * beklenir; filtreleme mantiginin cekirdegidir.
     */
    Optional<Birim> findFirstByTur(BirimTuru tur);
}

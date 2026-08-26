package tr.edu.akademiktakvim.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import tr.edu.akademiktakvim.domain.Kategori;

public interface KategoriRepository extends JpaRepository<Kategori, Long> {

    Optional<Kategori> findByKod(String kod);

    boolean existsByKod(String kod);

    /** IS KURALI 4: yeni kayitta yalnizca aktif kategoriler secilebilir. */
    List<Kategori> findByAktifTrueOrderBySiraAscAdAsc();

    List<Kategori> findAllByOrderBySiraAscAdAsc();
}

package tr.edu.akademiktakvim.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import tr.edu.akademiktakvim.domain.IslemKaydi;

public interface IslemKaydiRepository extends JpaRepository<IslemKaydi, Long> {

    /** Islem kayitlari zamanla cok birikir; bu yuzden her zaman sayfali okunur. */
    Page<IslemKaydi> findAllByOrderByIslemZamaniDesc(Pageable sayfa);

    Page<IslemKaydi> findByHedefTurAndHedefIdOrderByIslemZamaniDesc(
            String hedefTur, Long hedefId, Pageable sayfa);
}

package tr.edu.akademiktakvim.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import tr.edu.akademiktakvim.domain.Etkinlik;

/**
 * Etkinlik veri erisim katmani.
 *
 * <p>{@code JpaSpecificationExecutor} ile geliyor cunku ana ekran filtresi
 * dinamiktir: kullanici birim, egitim yili, coklu kategori ve donem
 * alanlarindan istedigini bos birakabilir. Her kombinasyon icin ayri bir
 * sorgu metodu yazmak yerine {@code EtkinlikSpecification} sartlari calisma
 * aninda birlestirir.</p>
 */
public interface EtkinlikRepository extends JpaRepository<Etkinlik, Long>,
        JpaSpecificationExecutor<Etkinlik> {

    /**
     * Tek bir etkinligi tum iliskileriyle birlikte tek sorguda getirir.
     *
     * <p>Iliskiler LAZY tanimli oldugu icin, DTO'ya cevirirken her alan ayri
     * sorgu tetiklerdi (N+1 problemi). JOIN FETCH bunu tek sorguya indirir.</p>
     */
    @Query("""
            SELECT e FROM Etkinlik e
            JOIN FETCH e.egitimYili
            JOIN FETCH e.kategori
            JOIN FETCH e.birim
            WHERE e.id = :id
            """)
    Optional<Etkinlik> findByIdWithDetay(Long id);

    /** Bir birime bagli etkinlik var mi? Birim silinmeden once kontrol edilir. */
    boolean existsByBirimId(Long birimId);

    boolean existsByKategoriId(Long kategoriId);

    boolean existsByEgitimYiliId(Long egitimYiliId);

    /**
     * Verilen kimliklerdeki etkinlikleri iliskileriyle getirir.
     * PDF ve ICS uretiminde, filtreleme sonucunu tek seferde yuklemek icin kullanilir.
     */
    @Query("""
            SELECT e FROM Etkinlik e
            JOIN FETCH e.egitimYili
            JOIN FETCH e.kategori
            JOIN FETCH e.birim
            WHERE e.id IN :idler
            ORDER BY e.baslangicTarihi ASC, e.ad ASC
            """)
    List<Etkinlik> findAllByIdWithDetay(List<Long> idler);
}

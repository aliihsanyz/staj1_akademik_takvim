package tr.edu.akademiktakvim.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.Predicate;
import tr.edu.akademiktakvim.domain.Etkinlik;
import tr.edu.akademiktakvim.dto.EtkinlikFiltreDTO;

/**
 * Ana ekran filtresini calisma aninda SQL sartlarina cevirir.
 *
 * <p>Filtre alanlarinin hepsi istege bagli oldugu icin (birim, egitim yili,
 * coklu kategori, donem, arama) her kombinasyona ayri sorgu metodu yazmak
 * kombinatorik bir patlama olurdu. Specification, yalnizca dolu olan alanlar
 * icin WHERE sarti ekler.</p>
 */
public final class EtkinlikSpecification {

    private EtkinlikSpecification() {
        // Yardimci sinif
    }

    /**
     * Filtreyi tek bir {@link Specification} nesnesine cevirir.
     *
     * @param filtre        kullanicinin sectigi olcutler
     * @param genelBirimId  "Universite Geneli" biriminin kimligi; bir fakulte
     *                      secildiginde genel etkinliklerin de listeye dahil
     *                      edilebilmesi icin gerekir. {@code null} verilirse
     *                      yalnizca secilen birim filtrelenir.
     */
    public static Specification<Etkinlik> filtrele(EtkinlikFiltreDTO filtre, Long genelBirimId) {
        return (kok, sorgu, kb) -> {
            List<Predicate> sartlar = new ArrayList<>();

            if (filtre.egitimYiliId() != null) {
                sartlar.add(kb.equal(kok.get("egitimYili").get("id"), filtre.egitimYiliId()));
            }

            // --- BIRIM SARTI (is analizi Bolum 5.1) ---
            // Kullanici bir fakulte sectiginde, o fakultenin etkinliklerinin yani sira
            // "Universite Geneli" etkinlikleri de gorunmelidir; cunku genel tarihler
            // (orn. resmi tatiller, kayit haftasi) tum birimleri baglar.
            // Kullanici zaten "Genel Takvim" sectiyse ikinci sarta gerek kalmaz.
            if (filtre.birimId() != null) {
                Predicate secilenBirim = kb.equal(kok.get("birim").get("id"), filtre.birimId());

                boolean genelZatenSecili = genelBirimId != null
                        && genelBirimId.equals(filtre.birimId());

                if (genelBirimId != null && !genelZatenSecili) {
                    Predicate genelBirim = kb.equal(kok.get("birim").get("id"), genelBirimId);
                    sartlar.add(kb.or(secilenBirim, genelBirim));
                } else {
                    sartlar.add(secilenBirim);
                }
            }

            // --- KATEGORI SARTI ---
            // Bolum 5.1: kategoride coklu secim yapilabilir (orn. hem sinavlar hem tatiller).
            if (filtre.kategoriFiltresiVarMi()) {
                sartlar.add(kok.get("kategori").get("id").in(filtre.kategoriIdler()));
            }

            if (filtre.donem() != null) {
                sartlar.add(kb.equal(kok.get("donem"), filtre.donem()));
            }

            // --- SERBEST METIN ARAMASI ---
            // DIKKAT: toLowerCase(Locale.ROOT) bilerek kullanildi. Sistem locale'i
            // tr_TR oldugundan, locale belirtilmezse "I" harfi noktasiz "i" ye
            // donusur ve arama sonuclari sessizce bozulur.
            if (filtre.aramaVarMi()) {
                String kalip = "%" + filtre.arama().trim().toLowerCase(Locale.ROOT) + "%";
                Predicate adda = kb.like(kb.lower(kok.get("ad")), kalip);
                Predicate aciklamada = kb.like(kb.lower(kb.coalesce(kok.get("aciklama"), "")), kalip);
                sartlar.add(kb.or(adda, aciklamada));
            }

            return kb.and(sartlar.toArray(new Predicate[0]));
        };
    }
}

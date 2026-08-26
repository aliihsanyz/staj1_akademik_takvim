package tr.edu.akademiktakvim.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import tr.edu.akademiktakvim.domain.Etkinlik;
import tr.edu.akademiktakvim.domain.IslemKaydi;
import tr.edu.akademiktakvim.domain.enums.IslemTuru;
import tr.edu.akademiktakvim.repository.IslemKaydiRepository;

/**
 * IS KURALI 6: "Silinen veya guncellenen kayitlarin islem gecmisi kullanici adi,
 * islem turu ve zaman damgasiyla tutulur."
 *
 * <p><b>Neden {@code REQUIRES_NEW}?</b> Islem kaydi, asil islemden BAGIMSIZ bir
 * islemde yazilir. Boylece asil islem sonradan geri alinsa (rollback) bile
 * denemenin izi kaybolmaz. Denetim kaydinin amaci tam olarak budur: neyin
 * denendigini de gormek.</p>
 *
 * <p>Ayni sebeple, kayit yazarken olusan bir hata asil islemi COKERTMEZ;
 * yalnizca gunluge yazilir. Denetim kaydi yazilamadi diye kullanicinin
 * etkinlik eklemesi engellenmemelidir.</p>
 */
@Service
public class IslemKaydiService {

    private static final Logger log = LoggerFactory.getLogger(IslemKaydiService.class);

    private final IslemKaydiRepository islemKaydiRepository;
    private final ObjectMapper objectMapper;

    public IslemKaydiService(IslemKaydiRepository islemKaydiRepository, ObjectMapper objectMapper) {
        this.islemKaydiRepository = islemKaydiRepository;
        this.objectMapper = objectMapper;
    }

    /** Yeni kayit olusturuldugunda. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void eklemeKaydet(String kullaniciAdi, Etkinlik etkinlik) {
        IslemKaydi kayit = new IslemKaydi(kullaniciAdi, IslemTuru.EKLE, "ETKINLIK",
                etkinlik.getId(), etkinlik.getAd());
        kayit.setYeniDeger(jsonaCevir(etkinlikOzeti(etkinlik)));
        kaydet(kayit);
    }

    /** Guncellemede hem eski hem yeni deger saklanir; boylece fark gorulebilir. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void guncellemeKaydet(String kullaniciAdi, Map<String, Object> eskiDeger, Etkinlik yeni) {
        IslemKaydi kayit = new IslemKaydi(kullaniciAdi, IslemTuru.GUNCELLE, "ETKINLIK",
                yeni.getId(), yeni.getAd());
        kayit.setEskiDeger(jsonaCevir(eskiDeger));
        kayit.setYeniDeger(jsonaCevir(etkinlikOzeti(yeni)));
        kaydet(kayit);
    }

    /**
     * Silmede kaydin TAMAMI eski_deger icinde saklanir.
     *
     * <p>Is analizi: "Kayit veritabanindan kaldirilir, ancak islem gecmisinde
     * silme kaydi tutulur." Etkinlik satiri gittigi icin, silinen verinin tek
     * kopyasi burasidir.</p>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void silmeKaydet(String kullaniciAdi, Etkinlik silinen) {
        IslemKaydi kayit = new IslemKaydi(kullaniciAdi, IslemTuru.SIL, "ETKINLIK",
                silinen.getId(), silinen.getAd());
        kayit.setEskiDeger(jsonaCevir(etkinlikOzeti(silinen)));
        kaydet(kayit);
    }

    /** PDF ice aktarma ile toplu eklemede tek bir ozet kayit yazilir. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void iceAktarmaKaydet(String kullaniciAdi, String dosyaAdi, int eklenenAdet) {
        IslemKaydi kayit = new IslemKaydi(kullaniciAdi, IslemTuru.ICE_AKTAR, "ETKINLIK",
                null, dosyaAdi + " (" + eklenenAdet + " etkinlik)");
        kayit.setYeniDeger(jsonaCevir(Map.of("dosya", dosyaAdi, "eklenenAdet", eklenenAdet)));
        kaydet(kayit);
    }

    /** Sistem tanimlari (birim / kategori / egitim yili) uzerindeki islemler. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void tanimIslemiKaydet(String kullaniciAdi, IslemTuru tur, String hedefTur,
                                  Long hedefId, String ozet) {
        kaydet(new IslemKaydi(kullaniciAdi, tur, hedefTur, hedefId, ozet));
    }

    /** Islem kayitlarini sayfali listeler (yalnizca Super Admin gorebilir). */
    @Transactional(readOnly = true)
    public Page<IslemKaydi> listele(Pageable sayfa) {
        return islemKaydiRepository.findAllByOrderByIslemZamaniDesc(sayfa);
    }

    // ------------------------------------------------------------- yardimcilar

    private void kaydet(IslemKaydi kayit) {
        try {
            kayit.setIpAdresi(istemciIpAdresi());
            islemKaydiRepository.save(kayit);
        } catch (Exception ex) {
            // Denetim kaydi yazilamadi diye asil islem basarisiz sayilmaz.
            log.error("Islem kaydi yazilamadi: {} / {}", kayit.getIslemTuru(), kayit.getHedefOzet(), ex);
        }
    }

    /**
     * Etkinligin denetime yazilacak duz ozeti.
     *
     * <p>Entity dogrudan JSON'a cevrilmez: LAZY iliskiler serilestirme sirasinda
     * patlar ve gereksiz alanlar kayda girer. Bunun yerine acik bir harita kurulur.</p>
     */
    private Map<String, Object> etkinlikOzeti(Etkinlik e) {
        Map<String, Object> ozet = new LinkedHashMap<>();
        ozet.put("id", e.getId());
        ozet.put("ad", e.getAd());
        ozet.put("aciklama", e.getAciklama());
        ozet.put("baslangicTarihi", String.valueOf(e.getBaslangicTarihi()));
        ozet.put("bitisTarihi", String.valueOf(e.getBitisTarihi()));
        ozet.put("donem", e.getDonem() != null ? e.getDonem().name() : null);
        ozet.put("egitimYili", e.getEgitimYili() != null ? e.getEgitimYili().getAd() : null);
        ozet.put("kategori", e.getKategori() != null ? e.getKategori().getAd() : null);
        ozet.put("birim", e.getBirim() != null ? e.getBirim().getAd() : null);
        return ozet;
    }

    private String jsonaCevir(Map<String, Object> veri) {
        try {
            return objectMapper.writeValueAsString(veri);
        } catch (JsonProcessingException ex) {
            log.warn("Denetim verisi JSON'a cevrilemedi", ex);
            return null;
        }
    }

    /**
     * Istegi yapan istemcinin IP adresi.
     *
     * <p>Ters vekil (reverse proxy) arkasinda calisirken gercek adres
     * {@code X-Forwarded-For} basliginda gelir; once ona bakilir.</p>
     */
    private String istemciIpAdresi() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes nitelikler)) {
            return null; // Zamanlanmis gorev gibi istek disi baglamlar
        }
        HttpServletRequest istek = nitelikler.getRequest();
        String iletilen = istek.getHeader("X-Forwarded-For");
        if (iletilen != null && !iletilen.isBlank()) {
            return iletilen.split(",")[0].trim();
        }
        return istek.getRemoteAddr();
    }
}

package tr.edu.akademiktakvim.service;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tr.edu.akademiktakvim.domain.Kullanici;
import tr.edu.akademiktakvim.dto.EtkinlikIstekDTO;
import tr.edu.akademiktakvim.dto.IceAktarmaDTO;
import tr.edu.akademiktakvim.exception.IsKuraliIhlaliException;
import tr.edu.akademiktakvim.exception.YetkisizIslemException;

/**
 * PDF ice aktarmanin ONAY asamasi: kullanicinin gozden gecirdigi listeyi kaydeder.
 *
 * <p><b>En onemli tasarim karari:</b> bu servis kayitlari dogrudan repository'ye
 * yazmaz; her satiri {@link EtkinlikService#olustur} uzerinden gecirir. Boylece
 * ice aktarilan veri, panelden elle girilen veriyle BIREBIR AYNI is kurallarina
 * tabi olur:</p>
 * <ul>
 *   <li>Bitis tarihi baslangictan once olamaz</li>
 *   <li>Pasif kategori / birim / egitim yili secilemez</li>
 *   <li>Birim Yoneticisi yalnizca kendi birimine aktarabilir</li>
 *   <li>Her kayit icin islem gecmisi tutulur</li>
 * </ul>
 * <p>Repository'ye dogrudan yazilsaydi, ice aktarma bu kurallarin tamamini
 * atlayan bir arka kapi olurdu.</p>
 *
 * <p><b>Hata yonetimi:</b> Bir satirin hatasi digerlerini engellemez. 40 satirlik
 * bir aktarmada 2 satir hatali diye 38 dogru kaydin reddedilmesi kullanici icin
 * bastan basa yeniden calismak demektir. Basarililar kaydedilir, hatalar
 * gerekcesiyle raporlanir.</p>
 */
@Service
public class IceAktarmaService {

    private static final Logger log = LoggerFactory.getLogger(IceAktarmaService.class);

    private final EtkinlikService etkinlikService;
    private final IslemKaydiService islemKaydiService;
    private final YetkiKontrolService yetkiKontrolService;

    public IceAktarmaService(EtkinlikService etkinlikService,
                             IslemKaydiService islemKaydiService,
                             YetkiKontrolService yetkiKontrolService) {
        this.etkinlikService = etkinlikService;
        this.islemKaydiService = islemKaydiService;
        this.yetkiKontrolService = yetkiKontrolService;
    }

    /**
     * Onaylanan etkinlikleri kaydeder.
     *
     * @param istek     kullanicinin duzeltip onayladigi liste
     * @param kullanici islemi yapan yonetici
     * @return kac kaydin eklendigi ve hatalarin dokumu
     */
    @Transactional
    public IceAktarmaDTO.OnaySonucu onayla(IceAktarmaDTO.OnayIstegi istek, Kullanici kullanici) {

        // Hedef birim yetkisi TEK SEFERDE, dongunun disinda kontrol edilir.
        // Iceride kontrol etseydik ayni hata 40 kez tekrarlanan bir liste uretirdi.
        yetkiKontrolService.birimeErisimiDogrula(kullanici, istek.birimId(),
                "PDF içe aktarma");

        List<String> hatalar = new ArrayList<>();
        int eklenen = 0;

        for (IceAktarmaDTO.OnaylananEtkinlik aday : istek.etkinlikler()) {
            EtkinlikIstekDTO etkinlikIstegi = new EtkinlikIstekDTO(
                    aday.ad(),
                    null,                       // Ice aktarmada aciklama alinmaz
                    aday.baslangicTarihi(),
                    aday.bitisTarihi(),
                    aday.donem(),
                    istek.egitimYiliId(),
                    aday.kategoriId(),
                    istek.birimId());

            try {
                // Elle girisle AYNI yol: tum is kurallari ve denetim kaydi burada devreye girer
                etkinlikService.olustur(etkinlikIstegi, kullanici);
                eklenen++;

            } catch (IsKuraliIhlaliException ex) {
                hatalar.add(hataSatiri(aday, ex.getMessage()));

            } catch (YetkisizIslemException ex) {
                // Yetki hatasi satir bazli degil sistemiktir; devam etmenin anlami yok
                throw ex;

            } catch (RuntimeException ex) {
                log.warn("İçe aktarma sırasında beklenmeyen hata: {}", aday.ad(), ex);
                hatalar.add(hataSatiri(aday, "Beklenmeyen hata nedeniyle kaydedilemedi."));
            }
        }

        // Toplu islem icin tek bir ozet denetim kaydi; her satir icin zaten
        // EtkinlikService kendi EKLE kaydini yazdi.
        if (eklenen > 0) {
            islemKaydiService.iceAktarmaKaydet(kullanici.getKullaniciAdi(),
                    istek.dosyaAdi() != null ? istek.dosyaAdi() : "PDF içe aktarma", eklenen);
        }

        return new IceAktarmaDTO.OnaySonucu(eklenen, hatalar.size(), hatalar);
    }

    private String hataSatiri(IceAktarmaDTO.OnaylananEtkinlik aday, String sebep) {
        return "\"" + aday.ad() + "\" (" + aday.baslangicTarihi() + "): " + sebep;
    }
}

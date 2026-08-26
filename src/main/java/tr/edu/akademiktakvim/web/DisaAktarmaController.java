package tr.edu.akademiktakvim.web;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import tr.edu.akademiktakvim.domain.Birim;
import tr.edu.akademiktakvim.domain.Etkinlik;
import tr.edu.akademiktakvim.domain.enums.Donem;
import tr.edu.akademiktakvim.dto.EtkinlikFiltreDTO;
import tr.edu.akademiktakvim.repository.BirimRepository;
import tr.edu.akademiktakvim.repository.EgitimYiliRepository;
import tr.edu.akademiktakvim.repository.KategoriRepository;
import tr.edu.akademiktakvim.service.EtkinlikService;
import tr.edu.akademiktakvim.service.ics.IcsOlusturmaService;
import tr.edu.akademiktakvim.service.pdf.PdfOlusturmaService;

/**
 * PDF ve .ics disa aktarma uclari.
 *
 * <p>Herkese aciktir: is analizi Bolum 5.4'e gore ogrenci ve personel
 * kimlik dogrulamasi yapmadan takvimini indirebilmelidir.</p>
 */
@RestController
public class DisaAktarmaController {

    private final EtkinlikService etkinlikService;
    private final PdfOlusturmaService pdfService;
    private final IcsOlusturmaService icsService;
    private final BirimRepository birimRepository;
    private final KategoriRepository kategoriRepository;
    private final EgitimYiliRepository egitimYiliRepository;

    public DisaAktarmaController(EtkinlikService etkinlikService,
                                 PdfOlusturmaService pdfService,
                                 IcsOlusturmaService icsService,
                                 BirimRepository birimRepository,
                                 KategoriRepository kategoriRepository,
                                 EgitimYiliRepository egitimYiliRepository) {
        this.etkinlikService = etkinlikService;
        this.pdfService = pdfService;
        this.icsService = icsService;
        this.birimRepository = birimRepository;
        this.kategoriRepository = kategoriRepository;
        this.egitimYiliRepository = egitimYiliRepository;
    }

    /**
     * Secili filtrelere uygun takvimi PDF olarak indirir.
     *
     * <p>Filtre parametreleri listeleme ucuyla birebir aynidir; boylece on yuz
     * ekrandaki filtreyi oldugu gibi bu adrese ekleyerek "gordugun neyse onu
     * indir" davranisini saglar.</p>
     */
    @GetMapping("/api/etkinlikler/pdf")
    public ResponseEntity<byte[]> pdfIndir(
            @RequestParam(required = false) Long egitimYiliId,
            @RequestParam(required = false) Long birimId,
            @RequestParam(required = false) Set<Long> kategoriIdler,
            @RequestParam(required = false) Donem donem,
            @RequestParam(required = false) String arama) {

        EtkinlikFiltreDTO filtre =
                new EtkinlikFiltreDTO(egitimYiliId, birimId, kategoriIdler, donem, arama);
        List<Etkinlik> etkinlikler = etkinlikService.entityListele(filtre);

        byte[] pdf = pdfService.olustur(etkinlikler, filtreOzetiCikar(filtre, etkinlikler));
        String dosyaAdi = "akademik-takvim-" + LocalDate.now() + ".pdf";

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, indirmeBasligi(dosyaAdi))
                .body(pdf);
    }

    /** Tek etkinligi takvime eklemek icin .ics dosyasi (Bolum 5.3). */
    @GetMapping("/api/etkinlikler/{id}/ics")
    public ResponseEntity<byte[]> tekEtkinlikIcs(@PathVariable Long id) {
        Etkinlik etkinlik = etkinlikService.entityGetir(id);
        byte[] ics = icsService.olustur(etkinlik);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/calendar; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        indirmeBasligi(icsService.dosyaAdi(etkinlik.getAd())))
                .body(ics);
    }

    /** Filtrelenmis tum takvimi tek .ics dosyasi olarak indirir. */
    @GetMapping("/api/etkinlikler/ics")
    public ResponseEntity<byte[]> topluIcs(
            @RequestParam(required = false) Long egitimYiliId,
            @RequestParam(required = false) Long birimId,
            @RequestParam(required = false) Set<Long> kategoriIdler,
            @RequestParam(required = false) Donem donem) {

        EtkinlikFiltreDTO filtre =
                new EtkinlikFiltreDTO(egitimYiliId, birimId, kategoriIdler, donem, null);
        List<Etkinlik> etkinlikler = etkinlikService.entityListele(filtre);
        byte[] ics = icsService.olustur(etkinlikler);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/calendar; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        indirmeBasligi("akademik-takvim-" + LocalDate.now() + ".ics"))
                .body(ics);
    }

    // ------------------------------------------------------------- yardimcilar

    /**
     * Filtredeki kimlikleri okunabilir adlara cevirir; PDF basliginda
     * "Birim: 3" degil "Birim: Mühendislik Fakültesi" yazmasi icin.
     */
    private PdfOlusturmaService.FiltreOzeti filtreOzetiCikar(EtkinlikFiltreDTO filtre,
                                                             List<Etkinlik> etkinlikler) {
        String egitimYili = filtre.egitimYiliId() == null ? null
                : egitimYiliRepository.findById(filtre.egitimYiliId())
                        .map(y -> y.getAd()).orElse(null);

        String birim = filtre.birimId() == null ? null
                : birimRepository.findById(filtre.birimId())
                        .map(Birim::getAd).orElse(null);

        String kategoriler = null;
        if (filtre.kategoriFiltresiVarMi()) {
            kategoriler = kategoriRepository.findAllById(filtre.kategoriIdler()).stream()
                    .map(k -> k.getAd())
                    .collect(Collectors.joining(", "));
        }

        String donem = filtre.donem() == null ? null : filtre.donem().getEtiket();

        // Birim sutunu yalnizca sonuclarda BIRDEN FAZLA birim varsa gerekli.
        // Not: kullanici tek bir fakulte secse bile, genel etkinlikler de
        // listeye dahil edildigi icin sonucta iki birim bulunabilir; bu durumda
        // sutun gosterilmelidir, aksi halde hangi satirin genel oldugu anlasilmaz.
        long farkliBirimSayisi = etkinlikler.stream()
                .map(e -> e.getBirim().getId())
                .distinct()
                .count();

        return new PdfOlusturmaService.FiltreOzeti(
                egitimYili, birim, kategoriler, donem, farkliBirimSayisi > 1);
    }

    /**
     * {@code Content-Disposition} basligini olusturur.
     *
     * <p>Dosya adi hem ASCII yedegiyle hem de RFC 5987 {@code filename*}
     * bicimiyle verilir; boylece Turkce karakterli adlar modern tarayicilarda
     * dogru gorunur, eski tarayicilar da yedek ada duser.</p>
     */
    private String indirmeBasligi(String dosyaAdi) {
        String asciiYedek = dosyaAdi.replaceAll("[^A-Za-z0-9._-]", "_");
        String kodlanmis = URLEncoder.encode(dosyaAdi, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename=\"" + asciiYedek + "\"; filename*=UTF-8''" + kodlanmis;
    }
}

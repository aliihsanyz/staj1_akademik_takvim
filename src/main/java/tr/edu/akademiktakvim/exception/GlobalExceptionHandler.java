package tr.edu.akademiktakvim.exception;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Tum REST uclarindaki istisnalari tek noktada yakalayip standart
 * {@link HataYanitiDTO} govdesine cevirir.
 *
 * <p>Bu sinif sayesinde controller metotlari try/catch ile kirlenmez;
 * servis katmani anlamli bir istisna firlatir, HTTP karsiligini burasi belirler.</p>
 *
 * <p><b>Guvenlik notu:</b> beklenmeyen hatalarda istisna detayi kullaniciya
 * DONDURULMEZ, yalnizca sunucu gunlugune yazilir. Yigit izi (stack trace)
 * disariya sizarsa saldirgan icin bilgi kaynagi olur.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * IS KURALI ihlali -> 422.
     * Ornek: "Bitis tarihi baslangic tarihinden once olamaz."
     */
    @ExceptionHandler(IsKuraliIhlaliException.class)
    public ResponseEntity<HataYanitiDTO> isKurali(IsKuraliIhlaliException ex, HttpServletRequest istek) {
        log.warn("Is kurali ihlali: {}", ex.getMessage());
        return yanit(HttpStatus.UNPROCESSABLE_ENTITY, ex.getHataKodu(), ex.getMessage(), istek);
    }

    /** Kayit bulunamadi -> 404. */
    @ExceptionHandler(KayitBulunamadiException.class)
    public ResponseEntity<HataYanitiDTO> bulunamadi(KayitBulunamadiException ex, HttpServletRequest istek) {
        return yanit(HttpStatus.NOT_FOUND, "KAYIT_BULUNAMADI", ex.getMessage(), istek);
    }

    /**
     * Yetkisiz islem -> 403.
     * IS KURALI 3: Birim Yoneticisi baska birimin kaydina dokunamaz.
     */
    @ExceptionHandler(YetkisizIslemException.class)
    public ResponseEntity<HataYanitiDTO> yetkisiz(YetkisizIslemException ex, HttpServletRequest istek) {
        log.warn("Yetkisiz islem denemesi: {} -> {}", istek.getRequestURI(), ex.getMessage());
        return yanit(HttpStatus.FORBIDDEN, "YETKISIZ_ISLEM", ex.getMessage(), istek);
    }

    /** Spring Security'nin kendi yetki reddi -> 403. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<HataYanitiDTO> erisimReddedildi(AccessDeniedException ex, HttpServletRequest istek) {
        return yanit(HttpStatus.FORBIDDEN, "YETKISIZ_ISLEM",
                "Bu işlem için yetkiniz bulunmuyor.", istek);
    }

    /** Kimlik dogrulama hatasi -> 401. */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<HataYanitiDTO> kimlikDogrulanamadi(AuthenticationException ex, HttpServletRequest istek) {
        return yanit(HttpStatus.UNAUTHORIZED, "KIMLIK_DOGRULANAMADI",
                "Kullanıcı adı veya şifre hatalı.", istek);
    }

    /**
     * Alan bazli dogrulama hatasi -> 400.
     * DTO uzerindeki @NotBlank, @Size gibi anotasyonlar buraya duser.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<HataYanitiDTO> dogrulama(MethodArgumentNotValidException ex, HttpServletRequest istek) {
        List<HataYanitiDTO.AlanHatasi> alanlar = ex.getBindingResult().getFieldErrors().stream()
                .map(this::alanHatasinaCevir)
                .toList();

        HataYanitiDTO govde = new HataYanitiDTO(
                java.time.LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                "DOGRULAMA_HATASI",
                "Gönderilen bilgilerde eksik veya hatalı alanlar var.",
                istek.getRequestURI(),
                alanlar);

        return ResponseEntity.badRequest().body(govde);
    }

    /**
     * Bozuk JSON, gecersiz kodlama veya cevrilemeyen deger -> 400.
     *
     * <p>Gercek sebep yalnizca sunucu gunlugune yazilir: Jackson'in ic hata
     * metinleri sinif ve alan adlarini icerir, bunlari disariya vermek gereksiz
     * bilgi sizdirmaktir. Kullaniciya en olasi sebep genel bir dille bildirilir.</p>
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<HataYanitiDTO> okunamayanGovde(HttpMessageNotReadableException ex, HttpServletRequest istek) {
        log.warn("İstek gövdesi okunamadı ({}): {}", istek.getRequestURI(),
                ex.getMostSpecificCause().getMessage());
        return yanit(HttpStatus.BAD_REQUEST, "GECERSIZ_ISTEK",
                "İstek gövdesi okunamadı. Gönderilen verinin geçerli JSON olduğundan ve "
                        + "tarih alanlarının yyyy-AA-gg biçiminde yazıldığından emin olun.", istek);
    }

    /** Yuklenen PDF cok buyuk -> 413. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<HataYanitiDTO> dosyaCokBuyuk(MaxUploadSizeExceededException ex, HttpServletRequest istek) {
        return yanit(HttpStatus.PAYLOAD_TOO_LARGE, "DOSYA_COK_BUYUK",
                "Yüklenen dosya izin verilen boyutu aşıyor (en fazla 15 MB).", istek);
    }

    /**
     * Beklenmeyen her sey -> 500.
     * Detay yalnizca sunucu gunlugune yazilir, kullaniciya sizdirilmaz.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<HataYanitiDTO> beklenmeyen(Exception ex, HttpServletRequest istek) {
        log.error("Beklenmeyen hata: {}", istek.getRequestURI(), ex);
        return yanit(HttpStatus.INTERNAL_SERVER_ERROR, "SUNUCU_HATASI",
                "Beklenmeyen bir hata oluştu. Lütfen daha sonra tekrar deneyin.", istek);
    }

    // ------------------------------------------------------------- yardimcilar

    private HataYanitiDTO.AlanHatasi alanHatasinaCevir(FieldError hata) {
        String mesaj = hata.getDefaultMessage() != null
                ? hata.getDefaultMessage()
                : "Geçersiz değer";
        return new HataYanitiDTO.AlanHatasi(hata.getField(), mesaj);
    }

    private ResponseEntity<HataYanitiDTO> yanit(HttpStatus durum, String hataKodu,
                                                String mesaj, HttpServletRequest istek) {
        return ResponseEntity.status(durum)
                .body(HataYanitiDTO.of(durum.value(), hataKodu, mesaj, istek.getRequestURI()));
    }
}

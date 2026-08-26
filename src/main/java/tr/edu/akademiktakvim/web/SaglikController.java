package tr.edu.akademiktakvim.web;

import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sistemin ayakta oldugunu dogrulamak icin kullanilan basit uc.
 * Kurulum dogrulamasi ve izleme (monitoring) icin kullanilir.
 */
@RestController
@RequestMapping("/api/saglik")
public class SaglikController {

    @GetMapping
    public Map<String, Object> saglikDurumu() {
        return Map.of(
                "durum", "AYAKTA",
                "uygulama", "Dinamik Akademik Takvim Sistemi",
                "zaman", LocalDateTime.now()
        );
    }
}

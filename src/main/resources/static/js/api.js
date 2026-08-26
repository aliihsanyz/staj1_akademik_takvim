/**
 * Sunucu ile konusan tek katman.
 *
 * Tum fetch cagrilari buradan gecer; boylece CSRF basligi, hata cevirisi ve
 * oturum yonetimi tek yerde tanimlanir. Sayfa betikleri HTTP ayrintilariyla
 * ugrasmaz.
 */
const Api = (() => {

    /**
     * CSRF jetonunu cerezden okur.
     *
     * Sunucu jetonu XSRF-TOKEN cerezine yazar (HttpOnly DEGIL, cunku
     * JavaScript'in okumasi gerekir). Oturum cerezi ise HttpOnly kalir -
     * ikisi farkli cerezlerdir ve karistirilmamalidir.
     */
    function csrfJetonu() {
        const eslesme = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
        return eslesme ? decodeURIComponent(eslesme[1]) : null;
    }

    /**
     * Sunucunun standart hata govdesini okunabilir bir Error'a cevirir.
     *
     * Alan bazli dogrulama hatalari tek metinde birlestirilir; boylece
     * cagiran kod tek bir mesaj gosterebilir.
     */
    async function hataOlustur(yanit) {
        let govde = null;
        try {
            govde = await yanit.json();
        } catch {
            // Govde JSON degilse (orn. ag hatasi) genel mesaja dusulur
        }

        let mesaj = govde?.mesaj || 'Beklenmeyen bir hata oluştu.';

        if (govde?.alanHatalari?.length) {
            mesaj = govde.alanHatalari.map(a => a.mesaj).join(' ');
        }

        const hata = new Error(mesaj);
        hata.durum = yanit.status;
        hata.hataKodu = govde?.hataKodu;
        return hata;
    }

    async function istek(yol, secenekler = {}) {
        const basliklar = { ...(secenekler.headers || {}) };

        // Guvenli olmayan metotlarda CSRF jetonu zorunludur
        if (secenekler.method && !['GET', 'HEAD'].includes(secenekler.method)) {
            const jeton = csrfJetonu();
            if (jeton) {
                basliklar['X-XSRF-TOKEN'] = jeton;
            }
        }

        const yanit = await fetch(yol, {
            credentials: 'same-origin',   // Oturum cerezi her istekte gitsin
            ...secenekler,
            headers: basliklar
        });

        if (!yanit.ok) {
            throw await hataOlustur(yanit);
        }

        if (yanit.status === 204) {
            return null;
        }
        return yanit.json();
    }

    function jsonIstek(yol, metot, govde) {
        return istek(yol, {
            method: metot,
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(govde)
        });
    }

    /** Filtre nesnesini sorgu dizesine cevirir; bos alanlar atlanir. */
    function sorguDizesi(filtre) {
        const p = new URLSearchParams();

        if (filtre.egitimYiliId) p.set('egitimYiliId', filtre.egitimYiliId);
        if (filtre.birimId) p.set('birimId', filtre.birimId);
        if (filtre.donem) p.set('donem', filtre.donem);
        if (filtre.arama) p.set('arama', filtre.arama);

        // Coklu kategori: Spring Set<Long> icin virgulle ayrilmis tek parametre
        if (filtre.kategoriIdler?.length) {
            p.set('kategoriIdler', filtre.kategoriIdler.join(','));
        }
        return p.toString();
    }

    return {
        csrfJetonu,
        sorguDizesi,

        // -------- Herkese acik --------
        birimler:      () => istek('/api/tanimlar/birimler'),
        kategoriler:   () => istek('/api/tanimlar/kategoriler'),
        egitimYillari: () => istek('/api/tanimlar/egitim-yillari'),
        etkinlikler:   (filtre) => istek('/api/etkinlikler?' + sorguDizesi(filtre)),

        // -------- Kimlik --------
        giris: (kullaniciAdi, sifre) =>
            jsonIstek('/api/kimlik/giris', 'POST', { kullaniciAdi, sifre }),
        cikis: () => istek('/api/kimlik/cikis', { method: 'POST' }),
        ben:   () => istek('/api/kimlik/ben'),

        // -------- Etkinlik yonetimi --------
        etkinlikEkle:     (govde) => jsonIstek('/api/admin/etkinlikler', 'POST', govde),
        etkinlikGuncelle: (id, govde) => jsonIstek('/api/admin/etkinlikler/' + id, 'PUT', govde),
        etkinlikSil:      (id) => istek('/api/admin/etkinlikler/' + id, { method: 'DELETE' }),

        // -------- Sistem tanimlari (Super Admin) --------
        tumBirimler:      () => istek('/api/admin/tanimlar/birimler'),
        tumKategoriler:   () => istek('/api/admin/tanimlar/kategoriler'),
        tumEgitimYillari: () => istek('/api/admin/tanimlar/egitim-yillari'),

        birimEkle:     (g) => jsonIstek('/api/admin/tanimlar/birimler', 'POST', g),
        birimGuncelle: (id, g) => jsonIstek('/api/admin/tanimlar/birimler/' + id, 'PUT', g),
        birimSil:      (id) => istek('/api/admin/tanimlar/birimler/' + id, { method: 'DELETE' }),

        kategoriEkle:     (g) => jsonIstek('/api/admin/tanimlar/kategoriler', 'POST', g),
        kategoriGuncelle: (id, g) => jsonIstek('/api/admin/tanimlar/kategoriler/' + id, 'PUT', g),
        kategoriSil:      (id) => istek('/api/admin/tanimlar/kategoriler/' + id, { method: 'DELETE' }),

        egitimYiliEkle:     (g) => jsonIstek('/api/admin/tanimlar/egitim-yillari', 'POST', g),
        egitimYiliGuncelle: (id, g) => jsonIstek('/api/admin/tanimlar/egitim-yillari/' + id, 'PUT', g),
        egitimYiliSil:      (id) => istek('/api/admin/tanimlar/egitim-yillari/' + id, { method: 'DELETE' }),

        // -------- Yardimci --------
        /**
         * Bugunun icinde bulundugu egitim yilini secer.
         *
         * Akademik yil takvim yiliyla ortusmedigi icin (1 Eylul - 31 Agustos)
         * "listenin ilkini sec" yaklasimi Temmuz-Agustos aylarinda henuz
         * baslamamis, dolayisiyla bos olan bir sonraki yili secer. Kapsayan
         * yil bulunamazsa listedeki en guncel yila dusulur.
         */
        guncelEgitimYili: (yillar) => {
            if (!yillar || yillar.length === 0) return null;
            const bugun = new Date().toISOString().slice(0, 10);
            return yillar.find(y => y.baslangicTarihi <= bugun && bugun <= y.bitisTarihi)
                ?? yillar[0];
        },

        // -------- PDF ice aktarma --------
        iceAktarmaOnizleme: (dosya) => {
            const veri = new FormData();
            veri.append('dosya', dosya);
            // Content-Type BILEREK verilmiyor: tarayici multipart sinirini
            // (boundary) kendisi eklemeli, elle yazilirsa istek bozulur.
            return istek('/api/admin/ice-aktarma/onizleme', { method: 'POST', body: veri });
        },
        iceAktarmaOnayla: (govde) =>
            jsonIstek('/api/admin/ice-aktarma/onayla', 'POST', govde)
    };
})();

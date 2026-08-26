/**
 * Herkese acik takvim ekrani.
 *
 * Is analizi Bolum 5: filtreleme, aylara gore gruplama, geri sayim,
 * etkinlik detayi, .ics ve PDF indirme.
 */
(() => {
    'use strict';

    const AYLAR = ['Ocak', 'Şubat', 'Mart', 'Nisan', 'Mayıs', 'Haziran',
                   'Temmuz', 'Ağustos', 'Eylül', 'Ekim', 'Kasım', 'Aralık'];

    /** Ekranin tum durumu tek nesnede tutulur - tek dogruluk kaynagi. */
    const durum = {
        egitimYillari: [],
        birimler: [],
        kategoriler: [],
        etkinlikler: [],
        filtre: { egitimYiliId: null, birimId: null, kategoriIdler: [] },
        acikEtkinlikId: null
    };

    const el = {
        yilSecici:    document.getElementById('yil-secici'),
        birimListesi: document.getElementById('birim-listesi'),
        enstituBasligi: document.getElementById('enstitu-basligi'),
        enstituListesi: document.getElementById('enstitu-listesi'),
        cipSerisi:    document.getElementById('cip-serisi'),
        icerik:       document.getElementById('icerik'),
        pdfDugmesi:   document.getElementById('pdf-dugmesi'),
        icsDugmesi:   document.getElementById('ics-dugmesi')
    };

    // ---------------------------------------------------------------- ARAÇLAR

    /**
     * Metni HTML'e guvenle gomer.
     *
     * Etkinlik adlari ve aciklamalar yoneticiler tarafindan girilir; dogrudan
     * innerHTML'e yazilirsa saklanmis XSS acigi olusur.
     */
    function kacisla(metin) {
        const d = document.createElement('div');
        d.textContent = metin ?? '';
        return d.innerHTML;
    }

    function tariheCevir(isoMetin) {
        const [y, a, g] = isoMetin.split('-').map(Number);
        return new Date(y, a - 1, g);
    }

    /**
     * Etkinligin tarihini kisa bicimde yazar.
     * Ayni ay icindeki aralik "08–12", ay asan aralik "28 Eki – 3 Kas" olur.
     */
    function tarihMetni(etkinlik) {
        const b = tariheCevir(etkinlik.baslangicTarihi);
        const s = tariheCevir(etkinlik.bitisTarihi);

        if (etkinlik.tekGun) {
            return String(b.getDate()).padStart(2, '0');
        }
        if (b.getMonth() === s.getMonth()) {
            return `${String(b.getDate()).padStart(2, '0')}–${String(s.getDate()).padStart(2, '0')}`;
        }
        return `${b.getDate()} ${AYLAR[b.getMonth()].slice(0, 3)} – ${s.getDate()} ${AYLAR[s.getMonth()].slice(0, 3)}`;
    }

    function uzunTarih(isoMetin) {
        const t = tariheCevir(isoMetin);
        return `${t.getDate()} ${AYLAR[t.getMonth()]} ${t.getFullYear()}`;
    }

    function ayAnahtari(isoMetin) {
        return isoMetin.slice(0, 7);
    }

    function ayBasligi(anahtar) {
        const [yil, ay] = anahtar.split('-').map(Number);
        return `${AYLAR[ay - 1]} ${yil}`;
    }

    function bildir(mesaj, tur = 'bilgi') {
        document.querySelector('.bildirim')?.remove();
        const kutu = document.createElement('div');
        kutu.className = 'bildirim bildirim--' + tur;
        kutu.setAttribute('role', tur === 'hata' ? 'alert' : 'status');
        kutu.textContent = mesaj;
        document.body.appendChild(kutu);
        setTimeout(() => kutu.remove(), 5000);
    }

    // ------------------------------------------------------------- YÜKLEME

    async function baslat() {
        try {
            const [yillar, birimler, kategoriler] = await Promise.all([
                Api.egitimYillari(), Api.birimler(), Api.kategoriler()
            ]);

            durum.egitimYillari = yillar;
            durum.birimler = birimler;
            durum.kategoriler = kategoriler;

            // Varsayilan secim: bugunun icinde bulundugu egitim yili
            durum.filtre.egitimYiliId = Api.guncelEgitimYili(yillar)?.id ?? null;

            // Varsayilan birim: "Genel Takvim"
            durum.filtre.birimId = birimler.find(b => b.tur === 'GENEL')?.id ?? null;

            yilSeciciyiCiz();
            birimleriCiz();
            cipleriCiz();
            await etkinlikleriYukle();

        } catch (hata) {
            hataGoster('Takvim yüklenemedi', hata.message);
        }
    }

    async function etkinlikleriYukle() {
        el.icerik.innerHTML = '<div class="durum">Yükleniyor…</div>';
        try {
            durum.etkinlikler = await Api.etkinlikler(durum.filtre);
            etkinlikleriCiz();
            indirmeBaglantilariniGuncelle();
        } catch (hata) {
            hataGoster('Etkinlikler yüklenemedi', hata.message);
        }
    }

    function hataGoster(baslik, mesaj) {
        el.icerik.innerHTML =
            `<div class="durum durum--hata">
                 <div class="durum__baslik">${kacisla(baslik)}</div>
                 <p>${kacisla(mesaj)}</p>
             </div>`;
    }

    // --------------------------------------------------------------- FİLTRE

    function yilSeciciyiCiz() {
        el.yilSecici.innerHTML = durum.egitimYillari
            .map(y => `<option value="${y.id}">${kacisla(y.ad)}</option>`)
            .join('');
        el.yilSecici.value = durum.filtre.egitimYiliId ?? '';

        el.yilSecici.addEventListener('change', () => {
            durum.filtre.egitimYiliId = Number(el.yilSecici.value);
            etkinlikleriYukle();
        });
    }

    /**
     * Birim listesi. Bolum 5.1: kullanici ayni anda YALNIZCA BIR birim secebilir,
     * bu yuzden dugmeler aria-current ile tekil secim gibi davranir.
     */
    function birimleriCiz() {
        const genel = durum.birimler.filter(b => b.tur === 'GENEL');
        const fakulteler = durum.birimler.filter(b => b.tur === 'FAKULTE');
        const digerleri = durum.birimler.filter(
            b => !['GENEL', 'FAKULTE'].includes(b.tur));

        el.birimListesi.innerHTML = [...genel, ...fakulteler].map(birimSatiri).join('');

        if (digerleri.length) {
            el.enstituBasligi.classList.remove('gizli');
            el.enstituListesi.innerHTML = digerleri.map(birimSatiri).join('');
        }

        document.querySelectorAll('[data-birim-id]').forEach(dugme => {
            dugme.addEventListener('click', () => {
                durum.filtre.birimId = Number(dugme.dataset.birimId);
                secimleriTazele();
                etkinlikleriYukle();
            });
        });
        secimleriTazele();
    }

    function birimSatiri(b) {
        const ad = b.tur === 'GENEL' ? 'Genel Takvim' : b.ad;
        return `<li><button type="button" data-birim-id="${b.id}">${kacisla(ad)}</button></li>`;
    }

    function secimleriTazele() {
        document.querySelectorAll('[data-birim-id]').forEach(d => {
            d.setAttribute('aria-current',
                Number(d.dataset.birimId) === durum.filtre.birimId ? 'true' : 'false');
        });
    }

    /** Kategori cipleri - Bolum 5.1: coklu secim yapilabilir. */
    function cipleriCiz() {
        el.cipSerisi.innerHTML = durum.kategoriler.map(k => `
            <button type="button" class="cip" data-kategori-id="${k.id}"
                    aria-pressed="false" style="--nokta-rengi:${kacisla(k.renk)}">
                <span class="cip__nokta"></span>${kacisla(k.ad)}
            </button>`).join('');

        el.cipSerisi.querySelectorAll('.cip').forEach(cip => {
            cip.addEventListener('click', () => {
                const id = Number(cip.dataset.kategoriId);
                const secili = cip.getAttribute('aria-pressed') === 'true';

                cip.setAttribute('aria-pressed', String(!secili));
                durum.filtre.kategoriIdler = secili
                    ? durum.filtre.kategoriIdler.filter(k => k !== id)
                    : [...durum.filtre.kategoriIdler, id];

                etkinlikleriYukle();
            });
        });
    }

    // ------------------------------------------------------------- LİSTELEME

    function etkinlikleriCiz() {
        if (!durum.etkinlikler.length) {
            el.icerik.innerHTML =
                `<div class="durum">
                     <div class="durum__baslik">Etkinlik bulunamadı</div>
                     <p>Seçtiğiniz filtrelere uygun bir kayıt yok. Kategori seçimlerini
                        kaldırarak daha geniş bir listeye bakabilirsiniz.</p>
                 </div>`;
            return;
        }

        // Bolum 5.2: etkinlikler aylara gore gruplanir. Sunucu zaten
        // kronolojik sirali dondurdugu icin tek gecis yeterli.
        const gruplar = new Map();
        for (const e of durum.etkinlikler) {
            const anahtar = ayAnahtari(e.baslangicTarihi);
            if (!gruplar.has(anahtar)) gruplar.set(anahtar, []);
            gruplar.get(anahtar).push(e);
        }

        // IMZA OGESI: "bugun" cizgisi, gecmisten gelecege gecisin oldugu
        // noktaya yerlestirilir. Boylece kullanici kaydirirken yilin
        // neresinde oldugunu tek bakista gorur.
        let bugunCizgisiEklendi = false;
        const parcalar = [];

        for (const [anahtar, etkinlikler] of gruplar) {
            parcalar.push(ayBasligiHtml(anahtar, etkinlikler.length));

            for (const e of etkinlikler) {
                if (!bugunCizgisiEklendi && e.durum !== 'GECMIS') {
                    parcalar.push(bugunCizgisiHtml());
                    bugunCizgisiEklendi = true;
                }
                parcalar.push(etkinlikHtml(e));
                if (e.id === durum.acikEtkinlikId) {
                    parcalar.push(detayHtml(e));
                }
            }
        }

        // Tum etkinlikler gecmisteyse cizgi en sona eklenir
        if (!bugunCizgisiEklendi) {
            parcalar.push(bugunCizgisiHtml());
        }

        el.icerik.innerHTML = parcalar.join('');
        olaylariBagla();
    }

    function ayBasligiHtml(anahtar, adet) {
        return `
            <div class="ay-basligi">
                <span class="ay-basligi__ad">${kacisla(ayBasligi(anahtar))}</span>
                <span class="ay-basligi__cizgi"></span>
                <span class="ay-basligi__sayi">${adet} etkinlik</span>
            </div>`;
    }

    function bugunCizgisiHtml() {
        const bugun = new Date();
        const metin = `Bugün · ${bugun.getDate()} ${AYLAR[bugun.getMonth()]} ${bugun.getFullYear()}`;
        return `
            <div class="bugun-cizgisi" role="separator" aria-label="${kacisla(metin)}">
                <span class="bugun-cizgisi__etiket">${kacisla(metin)}</span>
                <span class="bugun-cizgisi__yol"></span>
            </div>`;
    }

    function etkinlikHtml(e) {
        const sinif = ['etkinlik'];
        if (e.durum === 'GECMIS') sinif.push('etkinlik--gecmis');
        if (e.durum === 'DEVAM_EDIYOR') sinif.push('etkinlik--devam');

        const acik = e.id === durum.acikEtkinlikId;

        return `
            <button type="button" class="${sinif.join(' ')}" data-etkinlik-id="${e.id}"
                    aria-expanded="${acik}" style="--kategori-rengi:${kacisla(e.kategoriRengi)}">
                <span class="etkinlik__tarih">${kacisla(tarihMetni(e))}</span>
                <span class="etkinlik__ad">
                    <span class="etkinlik__nokta"></span>${kacisla(e.ad)}
                </span>
                <span class="etkinlik__ust-veri">
                    <span class="donem-etiketi">${kacisla(e.donemEtiketi)}</span>
                    <span class="geri-sayim">${kacisla(e.kalanGunMetni)}</span>
                </span>
            </button>`;
    }

    /** Bolum 5.3: etkinlige tiklandiginda acilan detay ve "Takvime Ekle". */
    function detayHtml(e) {
        const tarihSatiri = e.tekGun
            ? uzunTarih(e.baslangicTarihi)
            : `${uzunTarih(e.baslangicTarihi)} – ${uzunTarih(e.bitisTarihi)}`;

        return `
            <div class="detay" style="--kategori-rengi:${kacisla(e.kategoriRengi)}">
                <div class="detay__satir">
                    <span class="detay__etiket">Tarih</span><span>${kacisla(tarihSatiri)}</span>
                </div>
                <div class="detay__satir">
                    <span class="detay__etiket">Birim</span><span>${kacisla(e.birimAdi)}</span>
                </div>
                <div class="detay__satir">
                    <span class="detay__etiket">Kategori</span><span>${kacisla(e.kategoriAdi)}</span>
                </div>
                <div class="detay__satir">
                    <span class="detay__etiket">Dönem</span>
                    <span>${kacisla(e.donemEtiketi)} · ${kacisla(e.egitimYiliAdi)}</span>
                </div>
                ${e.aciklama ? `<p class="detay__aciklama">${kacisla(e.aciklama)}</p>` : ''}
                <div class="detay__eylemler">
                    <a class="dugme dugme--kucuk" href="/api/etkinlikler/${e.id}/ics">
                        Takvime ekle (.ics)
                    </a>
                </div>
            </div>`;
    }

    function olaylariBagla() {
        el.icerik.querySelectorAll('[data-etkinlik-id]').forEach(dugme => {
            dugme.addEventListener('click', () => {
                const id = Number(dugme.dataset.etkinlikId);
                // Ayni satira tekrar tiklamak detayi kapatir
                durum.acikEtkinlikId = durum.acikEtkinlikId === id ? null : id;
                etkinlikleriCiz();
            });
        });
    }

    // -------------------------------------------------------------- İNDİRME

    /**
     * PDF ve .ics baglantilarini ekrandaki filtreyle esitler.
     *
     * Boylece kullanici "gordugum neyse onu indiriyorum" davranisini yasar;
     * indirilen belge ekrandan farkli cikmaz.
     */
    function indirmeBaglantilariniGuncelle() {
        const sorgu = Api.sorguDizesi(durum.filtre);
        el.pdfDugmesi.href = '/api/etkinlikler/pdf?' + sorgu;
        el.icsDugmesi.href = '/api/etkinlikler/ics?' + sorgu;

        const bosMu = durum.etkinlikler.length === 0;
        [el.pdfDugmesi, el.icsDugmesi].forEach(d => {
            d.setAttribute('aria-disabled', String(bosMu));
            d.style.pointerEvents = bosMu ? 'none' : '';
            d.style.opacity = bosMu ? '.5' : '';
        });
    }

    document.addEventListener('DOMContentLoaded', baslat);
})();

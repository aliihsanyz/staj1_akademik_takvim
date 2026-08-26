/**
 * Yonetim paneli.
 *
 * Uc bolumden olusur: etkinlik yonetimi, PDF ice aktarma ve sistem tanimlari.
 * Sistem tanimlari sekmesi yalnizca Super Admin'e gosterilir; sunucu tarafinda
 * da ayrica korunur - arayuzde gizlemek tek basina guvenlik degildir.
 */
(() => {
    'use strict';

    const durum = {
        kullanici: null,
        yillar: [],
        birimler: [],
        kategoriler: [],
        etkinlikler: [],
        onizleme: null,
        secili: new Set(),      // Ice aktarmada onaylanan satirlarin indeksleri
        aktifTanim: 'birimler'
    };

    const $ = (id) => document.getElementById(id);

    // ----------------------------------------------------------------- ARAÇLAR

    function kacisla(metin) {
        const d = document.createElement('div');
        d.textContent = metin ?? '';
        return d.innerHTML;
    }

    function bildir(mesaj, tur = 'bilgi') {
        document.querySelector('.bildirim')?.remove();
        const kutu = document.createElement('div');
        kutu.className = 'bildirim bildirim--' + tur;
        kutu.setAttribute('role', tur === 'hata' ? 'alert' : 'status');
        kutu.textContent = mesaj;
        document.body.appendChild(kutu);
        setTimeout(() => kutu.remove(), 6000);
    }

    function tarihGoster(iso) {
        if (!iso) return '';
        const [y, a, g] = iso.split('-');
        return `${g}.${a}.${y}`;
    }

    function secenekler(liste, seciliId) {
        return liste.map(x =>
            `<option value="${x.id}"${x.id === seciliId ? ' selected' : ''}>${kacisla(x.ad)}</option>`
        ).join('');
    }

    // ------------------------------------------------------------------ GİRİŞ

    async function baslat() {
        try {
            durum.kullanici = await Api.ben();
            await paneliAc();
        } catch {
            // 401 bekleniyor: henuz oturum acilmamis
            $('giris-ekrani').classList.remove('gizli');
        }

        $('giris-formu').addEventListener('submit', async (olay) => {
            olay.preventDefault();
            $('giris-hatasi').textContent = '';
            try {
                durum.kullanici = await Api.giris($('kullanici-adi').value, $('sifre').value);
                $('giris-ekrani').classList.add('gizli');
                await paneliAc();
            } catch (hata) {
                $('giris-hatasi').textContent = hata.message;
            }
        });

        $('cikis-dugmesi').addEventListener('click', async () => {
            await Api.cikis();
            location.reload();
        });
    }

    async function paneliAc() {
        $('panel-ekrani').classList.remove('gizli');
        $('kullanici-bilgisi').textContent =
            `${durum.kullanici.adSoyad} · ${durum.kullanici.rolEtiketi}`;

        // Sistem tanimlari yalnizca Super Admin'e gosterilir
        if (!durum.kullanici.superAdmin) {
            $('tanimlar-sekmesi').classList.add('gizli');
        }

        const [yillar, birimler, kategoriler] = await Promise.all([
            Api.egitimYillari(), Api.birimler(), Api.kategoriler()
        ]);
        durum.yillar = yillar;
        durum.birimler = birimler;
        durum.kategoriler = kategoriler;

        sekmeleriBagla();
        etkinlikBolumunuKur();
        iceAktarmaBolumunuKur();
        await etkinlikleriYukle();
    }

    function sekmeleriBagla() {
        document.querySelectorAll('[data-sekme]').forEach(sekme => {
            sekme.addEventListener('click', () => {
                document.querySelectorAll('[data-sekme]').forEach(s =>
                    s.setAttribute('aria-selected', String(s === sekme)));

                ['etkinlikler', 'ice-aktarma', 'tanimlar'].forEach(ad =>
                    $('bolum-' + ad).classList.toggle('gizli', ad !== sekme.dataset.sekme));

                if (sekme.dataset.sekme === 'tanimlar') {
                    tanimlariYukle();
                }
            });
        });

        document.querySelectorAll('[data-tanim]').forEach(sekme => {
            sekme.addEventListener('click', () => {
                document.querySelectorAll('[data-tanim]').forEach(s =>
                    s.setAttribute('aria-selected', String(s === sekme)));
                durum.aktifTanim = sekme.dataset.tanim;
                tanimlariYukle();
            });
        });
    }

    // ============================================================ ETKİNLİKLER

    function etkinlikBolumunuKur() {
        $('y-yil').innerHTML = secenekler(durum.yillar);
        // Liste bugunun icinde bulundugu egitim yiliyla acilir; aksi hâlde
        // Temmuz-Agustos'ta henuz bos olan bir sonraki yil secili gelir.
        $('y-yil').value = Api.guncelEgitimYili(durum.yillar)?.id ?? '';
        $('y-birim').innerHTML =
            '<option value="">Tüm birimler</option>' + secenekler(durum.birimler);

        $('f-yil').innerHTML = secenekler(durum.yillar);
        $('f-kategori').innerHTML = secenekler(durum.kategoriler);
        $('f-birim').innerHTML = secenekler(yazilabilirBirimler());

        $('y-yil').addEventListener('change', etkinlikleriYukle);
        $('y-birim').addEventListener('change', etkinlikleriYukle);

        $('yeni-etkinlik').addEventListener('click', () => formuAc(null));
        $('form-iptal').addEventListener('click', () => $('etkinlik-formu').classList.add('gizli'));
        $('etkinlik-formu').addEventListener('submit', etkinlikKaydet);
    }

    /**
     * Kullanicinin yazabilecegi birimler.
     *
     * Birim Yoneticisine yalnizca kendi birimleri gosterilir. Bu bir kolaylik
     * onlemidir; asil kontrol sunucuda YetkiKontrolService icinde yapilir.
     */
    function yazilabilirBirimler() {
        if (durum.kullanici.superAdmin) {
            return durum.birimler;
        }
        const izinli = new Set(durum.kullanici.birimler.map(b => b.id));
        return durum.birimler.filter(b => izinli.has(b.id));
    }

    async function etkinlikleriYukle() {
        const filtre = {
            egitimYiliId: Number($('y-yil').value) || null,
            birimId: Number($('y-birim').value) || null
        };
        try {
            durum.etkinlikler = await Api.etkinlikler(filtre);
            etkinlikTablosunuCiz();
        } catch (hata) {
            bildir(hata.message, 'hata');
        }
    }

    function etkinlikTablosunuCiz() {
        if (!durum.etkinlikler.length) {
            $('etkinlik-govdesi').innerHTML =
                '<tr><td colspan="7" style="text-align:center;padding:32px;color:var(--murekkep-soluk)">' +
                'Bu filtreye uygun etkinlik yok.</td></tr>';
            return;
        }

        $('etkinlik-govdesi').innerHTML = durum.etkinlikler.map(e => `
            <tr>
                <td>${kacisla(e.ad)}</td>
                <td>${tarihGoster(e.baslangicTarihi)}</td>
                <td>${tarihGoster(e.bitisTarihi)}</td>
                <td>${kacisla(e.donemEtiketi)}</td>
                <td><span class="cip__nokta" style="--nokta-rengi:${kacisla(e.kategoriRengi)};display:inline-block;margin-left:6px"></span>${kacisla(e.kategoriAdi)}</td>
                <td>${kacisla(e.birimAdi)}</td>
                <td>
                    <div class="tablo__eylemler">
                        <button class="dugme dugme--kucuk" data-duzenle="${e.id}">Düzenle</button>
                        <button class="dugme dugme--kucuk dugme--tehlike" data-sil="${e.id}">Sil</button>
                    </div>
                </td>
            </tr>`).join('');

        $('etkinlik-govdesi').querySelectorAll('[data-duzenle]').forEach(d =>
            d.addEventListener('click', () =>
                formuAc(durum.etkinlikler.find(e => e.id === Number(d.dataset.duzenle)))));

        $('etkinlik-govdesi').querySelectorAll('[data-sil]').forEach(d =>
            d.addEventListener('click', () => etkinlikSil(Number(d.dataset.sil))));
    }

    function formuAc(etkinlik) {
        $('etkinlik-formu').classList.remove('gizli');
        $('f-id').value = etkinlik?.id ?? '';
        $('f-ad').value = etkinlik?.ad ?? '';
        $('f-baslangic').value = etkinlik?.baslangicTarihi ?? '';
        $('f-bitis').value = etkinlik?.bitisTarihi ?? '';
        $('f-donem').value = etkinlik?.donem ?? 'GUZ';
        $('f-yil').value = etkinlik?.egitimYiliId ?? Api.guncelEgitimYili(durum.yillar)?.id ?? '';
        $('f-kategori').value = etkinlik?.kategoriId ?? durum.kategoriler[0]?.id ?? '';
        $('f-birim').value = etkinlik?.birimId ?? yazilabilirBirimler()[0]?.id ?? '';
        $('f-aciklama').value = etkinlik?.aciklama ?? '';
        $('f-ad').focus();
    }

    async function etkinlikKaydet(olay) {
        olay.preventDefault();

        const govde = {
            ad: $('f-ad').value,
            aciklama: $('f-aciklama').value || null,
            baslangicTarihi: $('f-baslangic').value,
            bitisTarihi: $('f-bitis').value,
            donem: $('f-donem').value,
            egitimYiliId: Number($('f-yil').value),
            kategoriId: Number($('f-kategori').value),
            birimId: Number($('f-birim').value)
        };

        const id = $('f-id').value;
        try {
            if (id) {
                await Api.etkinlikGuncelle(Number(id), govde);
                bildir('Etkinlik güncellendi.', 'basari');
            } else {
                await Api.etkinlikEkle(govde);
                bildir('Etkinlik eklendi.', 'basari');
            }
            $('etkinlik-formu').classList.add('gizli');
            await etkinlikleriYukle();
        } catch (hata) {
            // İş kuralı ihlalleri (422) burada görünür: örneğin bitiş tarihi
            // başlangıçtan önceyse sunucunun ürettiği Türkçe mesaj gösterilir.
            bildir(hata.message, 'hata');
        }
    }

    async function etkinlikSil(id) {
        const etkinlik = durum.etkinlikler.find(e => e.id === id);
        if (!confirm(`"${etkinlik.ad}" silinecek. İşlem geçmişinde kaydı korunur. Devam edilsin mi?`)) {
            return;
        }
        try {
            await Api.etkinlikSil(id);
            bildir('Etkinlik silindi.', 'basari');
            await etkinlikleriYukle();
        } catch (hata) {
            bildir(hata.message, 'hata');
        }
    }

    // =========================================================== İÇE AKTARMA

    function iceAktarmaBolumunuKur() {
        const birakAlani = $('birak-alani');
        const dosyaGirisi = $('dosya-girisi');

        $('dosya-sec').addEventListener('click', () => dosyaGirisi.click());
        dosyaGirisi.addEventListener('change', () => {
            if (dosyaGirisi.files[0]) onizlemeAl(dosyaGirisi.files[0]);
        });

        ['dragenter', 'dragover'].forEach(olay =>
            birakAlani.addEventListener(olay, e => {
                e.preventDefault();
                birakAlani.classList.add('birak-alani--uzerinde');
            }));

        ['dragleave', 'drop'].forEach(olay =>
            birakAlani.addEventListener(olay, e => {
                e.preventDefault();
                birakAlani.classList.remove('birak-alani--uzerinde');
            }));

        birakAlani.addEventListener('drop', e => {
            const dosya = e.dataTransfer.files[0];
            if (dosya) onizlemeAl(dosya);
        });

        $('ice-aktarma-iptal').addEventListener('click', iceAktarmayiSifirla);
        $('onayla-dugmesi').addEventListener('click', iceAktarmayiOnayla);
    }

    async function onizlemeAl(dosya) {
        bildir('PDF ayrıştırılıyor…');
        try {
            durum.onizleme = await Api.iceAktarmaOnizleme(dosya);

            // Varsayilan olarak tum satirlar secili gelir; kullanici
            // istemediklerini isaretten cikarir.
            durum.secili = new Set(durum.onizleme.etkinlikler.map((_, i) => i));

            $('yukleme-adimi').classList.add('gizli');
            $('onizleme-adimi').classList.remove('gizli');

            $('i-yil').innerHTML = secenekler(durum.yillar);
            $('i-birim').innerHTML = secenekler(yazilabilirBirimler());

            onizlemeyiCiz();
        } catch (hata) {
            bildir(hata.message, 'hata');
        }
    }

    function onizlemeyiCiz() {
        const o = durum.onizleme;
        const i = o.istatistik;

        $('ozet-serisi').innerHTML = [
            ['Bulunan etkinlik', i.bulunanEtkinlik],
            ['Yüksek güvenli', i.yuksekGuven],
            ['Kontrol gerekli', i.dusukGuven],
            ['Atlanan satır', i.atlananSatir]
        ].map(([etiket, sayi]) => `
            <div>
                <div class="ozet__sayi">${sayi}</div>
                <div class="ozet__etiket">${etiket}</div>
            </div>`).join('');

        $('onizleme-govdesi').innerHTML = o.etkinlikler.map((e, indeks) => {
            const dusuk = e.guvenSkoru < 60;
            return `
            <tr class="${dusuk ? 'dusuk-guven' : ''}">
                <td>
                    <input type="checkbox" data-satir="${indeks}"
                           ${durum.secili.has(indeks) ? 'checked' : ''}
                           aria-label="${kacisla(e.ad)} satırını içe aktar">
                </td>
                <td>
                    <input class="metin-girisi" data-alan="ad" data-satir="${indeks}"
                           value="${kacisla(e.ad)}" style="min-width:220px">
                    ${e.uyarilar.map(u => `<span class="uyari-metni">${kacisla(u)}</span>`).join('')}
                    <span class="kaynak-satir">${kacisla(e.kaynakSatir)}</span>
                </td>
                <td><input class="metin-girisi" type="date" data-alan="baslangicTarihi"
                           data-satir="${indeks}" value="${e.baslangicTarihi}"></td>
                <td><input class="metin-girisi" type="date" data-alan="bitisTarihi"
                           data-satir="${indeks}" value="${e.bitisTarihi}"></td>
                <td>
                    <select class="secici" data-alan="donem" data-satir="${indeks}">
                        <option value="GUZ"${e.donem === 'GUZ' ? ' selected' : ''}>Güz</option>
                        <option value="BAHAR"${e.donem === 'BAHAR' ? ' selected' : ''}>Bahar</option>
                        <option value="YAZ"${e.donem === 'YAZ' ? ' selected' : ''}>Yaz</option>
                    </select>
                </td>
                <td>
                    <select class="secici" data-alan="kategoriId" data-satir="${indeks}">
                        ${secenekler(durum.kategoriler, e.kategoriId)}
                    </select>
                </td>
            </tr>`;
        }).join('');

        // Satir secim kutulari
        $('onizleme-govdesi').querySelectorAll('[data-satir][type="checkbox"]').forEach(kutu =>
            kutu.addEventListener('change', () => {
                const i = Number(kutu.dataset.satir);
                kutu.checked ? durum.secili.add(i) : durum.secili.delete(i);
            }));

        // Ayristirilamayan satirlar gizlenmez: kullanici gercekten onemli
        // bir satirin kacirildigini fark edebilmeli.
        $('atlanan-bolumu').classList.toggle('gizli', o.ayristirilamayanSatirlar.length === 0);
        $('atlanan-listesi').innerHTML =
            o.ayristirilamayanSatirlar.map(s => `<li>${kacisla(s)}</li>`).join('');
    }

    /** Kullanicinin tabloda yaptigi duzeltmeleri okur. */
    function duzenlenmisSatirlar() {
        return durum.onizleme.etkinlikler
            .map((e, indeks) => {
                if (!durum.secili.has(indeks)) return null;

                const oku = (alan) =>
                    $('onizleme-govdesi').querySelector(
                        `[data-alan="${alan}"][data-satir="${indeks}"]`).value;

                return {
                    ad: oku('ad'),
                    baslangicTarihi: oku('baslangicTarihi'),
                    bitisTarihi: oku('bitisTarihi'),
                    donem: oku('donem'),
                    kategoriId: Number(oku('kategoriId'))
                };
            })
            .filter(Boolean);
    }

    async function iceAktarmayiOnayla() {
        const satirlar = duzenlenmisSatirlar();
        if (!satirlar.length) {
            bildir('İçe aktarılacak satır seçilmedi.', 'hata');
            return;
        }

        $('onayla-dugmesi').disabled = true;
        try {
            const sonuc = await Api.iceAktarmaOnayla({
                etkinlikler: satirlar,
                egitimYiliId: Number($('i-yil').value),
                birimId: Number($('i-birim').value),
                dosyaAdi: durum.onizleme.dosyaAdi
            });

            if (sonuc.hataliAdet > 0) {
                // Basarililar kaydedildi; hatalar sebebiyle birlikte bildirilir
                bildir(`${sonuc.eklenenAdet} etkinlik eklendi, ${sonuc.hataliAdet} satır eklenemedi: `
                    + sonuc.hatalar.slice(0, 2).join(' | '), 'hata');
            } else {
                bildir(`${sonuc.eklenenAdet} etkinlik başarıyla içe aktarıldı.`, 'basari');
            }

            iceAktarmayiSifirla();
            await etkinlikleriYukle();
        } catch (hata) {
            bildir(hata.message, 'hata');
        } finally {
            $('onayla-dugmesi').disabled = false;
        }
    }

    function iceAktarmayiSifirla() {
        durum.onizleme = null;
        durum.secili.clear();
        $('dosya-girisi').value = '';
        $('onizleme-adimi').classList.add('gizli');
        $('yukleme-adimi').classList.remove('gizli');
    }

    // ======================================================= SİSTEM TANIMLARI

    async function tanimlariYukle() {
        try {
            if (durum.aktifTanim === 'birimler') {
                birimTablosu(await Api.tumBirimler());
            } else if (durum.aktifTanim === 'kategoriler') {
                kategoriTablosu(await Api.tumKategoriler());
            } else {
                egitimYiliTablosu(await Api.tumEgitimYillari());
            }
        } catch (hata) {
            bildir(hata.message, 'hata');
        }
    }

    function durumRozeti(aktif) {
        return aktif
            ? '<span class="rozet rozet--aktif">Aktif</span>'
            : '<span class="rozet rozet--pasif">Pasif</span>';
    }

    function birimTablosu(birimler) {
        $('tanim-icerigi').innerHTML = `
            <div class="tablo-sarmali">
                <table class="tablo">
                    <thead><tr><th>Ad</th><th>Kod</th><th>Tür</th><th>Durum</th><th></th></tr></thead>
                    <tbody>${birimler.map(b => `
                        <tr>
                            <td>${kacisla(b.ad)}</td>
                            <td>${kacisla(b.kod)}</td>
                            <td>${kacisla(b.turEtiketi)}</td>
                            <td>${durumRozeti(b.aktif)}</td>
                            <td><button class="dugme dugme--kucuk" data-degistir="${b.id}"
                                        data-aktif="${b.aktif}">
                                ${b.aktif ? 'Pasife al' : 'Aktife al'}</button></td>
                        </tr>`).join('')}
                    </tbody>
                </table>
            </div>`;

        $('tanim-icerigi').querySelectorAll('[data-degistir]').forEach(d =>
            d.addEventListener('click', async () => {
                const b = birimler.find(x => x.id === Number(d.dataset.degistir));
                try {
                    await Api.birimGuncelle(b.id, {
                        ad: b.ad, kod: b.kod, tur: b.tur, aktif: !b.aktif
                    });
                    bildir(`"${b.ad}" ${b.aktif ? 'pasife alındı' : 'aktife alındı'}.`, 'basari');
                    tanimlariYukle();
                } catch (hata) {
                    bildir(hata.message, 'hata');
                }
            }));
    }

    function kategoriTablosu(kategoriler) {
        $('tanim-icerigi').innerHTML = `
            <div class="tablo-sarmali">
                <table class="tablo">
                    <thead><tr><th>Ad</th><th>Kod</th><th>Renk</th><th>Durum</th><th></th></tr></thead>
                    <tbody>${kategoriler.map(k => `
                        <tr>
                            <td>${kacisla(k.ad)}</td>
                            <td>${kacisla(k.kod)}</td>
                            <td><span class="cip__nokta" style="--nokta-rengi:${kacisla(k.renk)};display:inline-block"></span></td>
                            <td>${durumRozeti(k.aktif)}</td>
                            <td><button class="dugme dugme--kucuk" data-degistir="${k.id}">
                                ${k.aktif ? 'Pasife al' : 'Aktife al'}</button></td>
                        </tr>`).join('')}
                    </tbody>
                </table>
            </div>`;

        $('tanim-icerigi').querySelectorAll('[data-degistir]').forEach(d =>
            d.addEventListener('click', async () => {
                const k = kategoriler.find(x => x.id === Number(d.dataset.degistir));
                try {
                    await Api.kategoriGuncelle(k.id, {
                        ad: k.ad, kod: k.kod, renk: k.renk, sira: k.sira, aktif: !k.aktif
                    });
                    bildir(`"${k.ad}" ${k.aktif ? 'pasife alındı' : 'aktife alındı'}.`, 'basari');
                    tanimlariYukle();
                } catch (hata) {
                    bildir(hata.message, 'hata');
                }
            }));
    }

    function egitimYiliTablosu(yillar) {
        $('tanim-icerigi').innerHTML = `
            <div class="tablo-sarmali">
                <table class="tablo">
                    <thead><tr><th>Yıl</th><th>Başlangıç</th><th>Bitiş</th><th>Durum</th><th></th></tr></thead>
                    <tbody>${yillar.map(y => `
                        <tr>
                            <td>${kacisla(y.ad)}</td>
                            <td>${tarihGoster(y.baslangicTarihi)}</td>
                            <td>${tarihGoster(y.bitisTarihi)}</td>
                            <td>${durumRozeti(y.aktif)}</td>
                            <td><button class="dugme dugme--kucuk" data-degistir="${y.id}">
                                ${y.aktif ? 'Pasife al' : 'Aktife al'}</button></td>
                        </tr>`).join('')}
                    </tbody>
                </table>
            </div>`;

        $('tanim-icerigi').querySelectorAll('[data-degistir]').forEach(d =>
            d.addEventListener('click', async () => {
                const y = yillar.find(x => x.id === Number(d.dataset.degistir));
                try {
                    await Api.egitimYiliGuncelle(y.id, {
                        ad: y.ad,
                        baslangicTarihi: y.baslangicTarihi,
                        bitisTarihi: y.bitisTarihi,
                        aktif: !y.aktif
                    });
                    bildir(`"${y.ad}" ${y.aktif ? 'pasife alındı' : 'aktife alındı'}.`, 'basari');
                    tanimlariYukle();
                } catch (hata) {
                    bildir(hata.message, 'hata');
                }
            }));
    }

    document.addEventListener('DOMContentLoaded', baslat);
})();

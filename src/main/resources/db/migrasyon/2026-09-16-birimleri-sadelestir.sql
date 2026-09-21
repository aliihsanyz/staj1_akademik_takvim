-- =====================================================================
--  TEK SEFERLIK VERI DUZENLEMESI - 16.09.2026
--
--  Amac: sistemde yalnizca uc birim kalsin
--        (Genel Akademik Takvim, Tip Fakultesi, Dis Hekimligi Fakultesi).
--
--  Bu betik UYGULAMA ACILISINDA CALISMAZ; elle calistirilir:
--      psql -h localhost -U postgres -d akademik_takvim \
--           -f src/main/resources/db/migrasyon/2026-09-16-birimleri-sadelestir.sql
--
--  Neden acilista calismiyor? Kaldirma kurali "listede olmayan her birimi sil"
--  seklinde. Her acilista calissaydi, Super Admin'in panelden sonradan
--  ekledigi birimler ilk yeniden baslatmada sessizce silinirdi.
--
--  Veri politikasi (is sahibi karari):
--    - Kaldirilan birimlerdeki ORNEK veriler ('sistem' tarafindan uretilenler)
--      silinir.
--    - Kullanicinin girdigi / PDF'ten ice aktardigi etkinlikler SILINMEZ,
--      "Genel Akademik Takvim" birimine tasinir. Boylece ogrenci ekraninin
--      varsayilan gorunumunde hemen gorunurler.
-- =====================================================================

BEGIN;

-- 1) GENEL birimin adi arayuzde gorundugu gibi olsun
UPDATE birim SET ad = 'Genel Akademik Takvim' WHERE kod = 'GENEL';

-- 2) Kalacak iki fakulte yoksa olustur, varsa aktife al
INSERT INTO birim (ad, kod, tur)
SELECT 'Tıp Fakültesi', 'TIP_FAK', 'FAKULTE'
WHERE NOT EXISTS (SELECT 1 FROM birim WHERE kod = 'TIP_FAK');

INSERT INTO birim (ad, kod, tur)
SELECT 'Diş Hekimliği Fakültesi', 'DIS_HEK_FAK', 'FAKULTE'
WHERE NOT EXISTS (SELECT 1 FROM birim WHERE kod = 'DIS_HEK_FAK');

UPDATE birim SET aktif = TRUE WHERE kod IN ('GENEL', 'TIP_FAK', 'DIS_HEK_FAK');

-- 3) Kaldirilacak birimlerin ORNEK etkinliklerini sil
DELETE FROM etkinlik
WHERE olusturan = 'sistem'
  AND birim_id IN (SELECT id FROM birim WHERE kod NOT IN ('GENEL', 'TIP_FAK', 'DIS_HEK_FAK'));

-- 4) Gercek etkinlikleri Genel Akademik Takvim'e tasi
UPDATE etkinlik
SET birim_id = (SELECT id FROM birim WHERE kod = 'GENEL'),
    guncelleme_zamani = CURRENT_TIMESTAMP
WHERE birim_id IN (SELECT id FROM birim WHERE kod NOT IN ('GENEL', 'TIP_FAK', 'DIS_HEK_FAK'));

-- 5) Birimleri sil (kullanici_birim baglantilari ON DELETE CASCADE ile temizlenir)
DELETE FROM birim WHERE kod NOT IN ('GENEL', 'TIP_FAK', 'DIS_HEK_FAK');

-- 6) Birimi kalmayan Birim Yoneticilerini Tip Fakultesine bagla.
--    Aksi halde hicbir birime yazamayan, kullanilamaz bir hesap kalirdi.
INSERT INTO kullanici_birim (kullanici_id, birim_id)
SELECT k.id, (SELECT id FROM birim WHERE kod = 'TIP_FAK')
FROM kullanici k
WHERE k.rol = 'BIRIM_YONETICISI'
  AND NOT EXISTS (SELECT 1 FROM kullanici_birim kb WHERE kb.kullanici_id = k.id);

COMMIT;

# Ghost

Gün içinde nerelere gittiğini, her yerde ne kadar kaldığını ve bir yerden bir yere
kaç dakikada gittiğini gösteren kişisel konum günlüğü (Android).

- Konum dururken 5 dakikada, hareket halindeyken 1 dakikada bir kaydedilir (Ayarlar'dan değiştirilebilir).
- Kayıtlar otomatik olarak **ziyaretlere** ve aradaki **yolculuklara** ayrılır.
- Rotalar Strava tarzı çizilir: hareket halindeyken dakikada bir alınan waypoint'lerden geçen turuncu çizgi, yeşil başlangıç ve gri bitiş noktası.
- Yerlere isim verilebilir ("Ev", "İş"); istatistikler yer ve güzergâh bazında toplanır.
- Üç ana ekran widget'ı: günün rotası haritada (detaylı), "şu an" (bekleme ya da yolculuk süresi, canlı sayaç) ve günün özeti.
- Açık / koyu / sistem teması.
- Tüm veriler yalnızca telefonda durur. İnternet yalnızca harita karoları ve adres adı için kullanılır.

## Kurulum

1. Telefonun tarayıcısından bu deponun **Releases** sayfasını aç, en son sürümdeki `ghost-release.apk` dosyasını indir.
2. Dosyayı aç; istenirse "bilinmeyen uygulamaları yükle" iznini ver.
3. Uygulamada **Kaydı başlat**'a dokun, ardından sırasıyla çıkan kartlardaki adımları tamamla:
   konum izni → "Her zaman izin ver" → pil kısıtlamasını kaldır.

Yeni sürümler eskisinin üzerine kurulur; kayıtlar silinmez.

Widget eklemek için ana ekranda boş bir yere basılı tut → Widget'lar → Ghost.

Android arka planda konum alan uygulamalara bildirim göstermeyi zorunlu tutar. Ghost'un bildirimi sessiz ve
küçültülmüştür; tamamen gizlemek için Ayarlar → İzinler → "Kayıt bildirimini gizle" → Kapat. Kayıt etkilenmez.

## Geliştirme

```
./gradlew testReleaseUnitTest assembleRelease
```

- `core/` – ziyaret/yolculuk algoritması (saf Kotlin, birim testli)
- `tracking/` – arka plan konum servisi
- `data/` – SQLite deposu, dışa aktarma
- `ui/` – Jetpack Compose ekranları

`ScreensTest` uygulamayı örnek veriyle JVM üzerinde açar ve her ekranın görüntüsünü
`app/build/outputs/screens` klasörüne kaydeder.

İmza anahtarı (`app/ghost.jks`) bilerek depoda tutuluyor: her derleme aynı imzayı taşır,
böylece güncellemeler veri kaybı olmadan kurulur. Uygulama mağazada yayınlanacaksa ayrı, gizli bir anahtar kullanılmalı.

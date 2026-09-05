# JepretAja — Android (Kotlin + Jetpack Compose)

Rewrite native dari versi Flutter (lihat `jepretaja_app/` untuk versi
sebelumnya) — **backend (Cloud Functions, Firestore Rules, Storage Rules,
integrasi Midtrans, video-processor) SAMA PERSIS, tidak ada yang berubah**.
Project ini hanya menulis ulang client Android-nya dengan Kotlin +
Jetpack Compose, memanggil collection Firestore & Cloud Function callable
yang identik.

## Status Checkpoint Ini (jujur, bukan diklaim lengkap)

**Sudah selesai:**
- Struktur project Gradle (Kotlin DSL) + Hilt DI + Jetpack Compose
- Tema (warna sama persis dengan versi Flutter: `#FF5A3C`)
- Semua model data (User, Creator, ExplorePost, Portfolio, Package, Booking
  dengan state machine 13-status, Chat, Wallet, Payment, Review, Notification)
- Semua repository (Auth, Creator, Explore, Booking, Payment, Availability,
  Chat, Wallet) — memanggil collection Firestore & Cloud Function callable
  yang **identik** dengan backend yang sudah ada, termasuk aturan yang sama
  (booking tidak pernah ditulis langsung, hanya lewat callable, dst)
- Google Sign-In dengan `ActivityResultLauncher` yang benar
- Layar: Splash, Onboarding, Choose Access, Login, Register Customer,
  Register Creator, Home (dasar)
- NavGraph merangkai semua di atas jadi satu alur yang bisa dijalankan

**BELUM dikerjakan** (repository/backend-nya sudah siap, tinggal buat UI-nya):
- Explore (fitur inti — fullscreen feed)
- Search, Nearby, Creator Profile, Package Detail
- Booking Form/Confirmation/Detail, Payment + WebView
- Chat List/Room (repository sudah ada read-receipt & block, UI belum)
- Notifications, My Bookings, My Reviews, Favorites, Profile, Help
- Seluruh Creator Dashboard (Upload, Kelola Paket/Portfolio/Booking,
  Wallet, Withdrawal, Availability, Settings)

Ini **bukan** kelalaian — ini keputusan sadar supaya checkpoint pertama
benar-benar bisa di-build & dijalankan (alur Auth lengkap) daripada
setengah jadi di banyak tempat sekaligus. Lanjutkan sesi berikutnya untuk
melengkapi sisanya, dengan pola yang sama seperti versi Flutter dulu
dikerjakan bertahap.

## Setup

### 1. Firebase — pakai project YANG SAMA dengan versi Flutter (kalau ada)

Kalau Anda sudah pernah `flutterfire configure` untuk versi Flutter,
**pakai project Firebase yang sama** — jangan buat baru, supaya data
Firestore/Storage yang sudah ada tetap kepakai.

Di [Firebase Console](https://console.firebase.google.com) → project Anda
→ **Project Settings** → **Your apps** → cek apakah sudah ada Android app
dengan package `com.jepretaja.app`:
- **Kalau sudah ada** (dari versi Flutter): klik ikon Android app itu →
  download `google-services.json` lagi (sama saja, tidak perlu app baru)
- **Kalau belum ada**: **Add app** → Android → package name
  `com.jepretaja.app` → download `google-services.json`

Letakkan file itu di **`app/google-services.json`** (bukan di root).

### 2. Buka di Android Studio

1. `File > Open` → pilih folder `jepretaja_android_kotlin`
2. Tunggu Gradle sync (pertama kali agak lama, download dependency)
3. Kalau muncul error `Unresolved reference: default_web_client_id` — itu
   tandanya `google-services.json` belum ada/belum di-sync, ulangi langkah 1

### 3. Jalankan

Pilih emulator/device Android di dropdown toolbar → klik ▶ (Shift+F10)

## Kenapa Google Sign-In butuh langkah ekstra

Selain `google-services.json`, Google Sign-In butuh **SHA-1 fingerprint**
project Anda didaftarkan ke Firebase:

```bash
# Windows PowerShell, dari folder project:
.\gradlew signingReport
```

Cari `SHA1` di output (untuk variant debug), copy, lalu di Firebase Console
→ Project Settings → Your apps → Android app → **Add fingerprint** → paste.
Tanpa ini, Google Sign-In akan gagal dengan error `DEVELOPER_ERROR`.

## Backend (tidak berubah)

Semua dokumentasi backend (`SETUP.md`, `SECURITY.md`, `PAYMENT.md`, dst)
dari project Flutter **tetap berlaku 100%** untuk project ini — cukup lihat
`jepretaja_app/docs/` dan `jepretaja_app/backend/`. Tidak ada yang perlu
di-deploy ulang.

## GitHub, Vercel, dan APK

Project ini adalah aplikasi Android, bukan project web. Vercel tidak
menjalankan file APK. Pola deploy yang benar:

1. Deploy backend web yang memiliki endpoint `POST /api/app` ke Vercel.
2. Upload repository Android ini ke GitHub.
3. Di GitHub → `Settings` → `Secrets and variables` → `Actions`, tambahkan:
  - `GOOGLE_SERVICES_JSON`: isi file `app/google-services.json` dalam Base64.
  - `API_BASE_URL`: domain Vercel, misalnya `https://api.jepretaja.vercel.app`.
  - `CLOUDINARY_CLOUD_NAME` dan `CLOUDINARY_UPLOAD_PRESET` bila fitur upload dipakai.
4. Push ke branch `main`. Workflow `Build APK` akan membuat APK di GitHub
  Actions → run terbaru → `Artifacts` → `jepretaja-debug-apk`.

APK debug dapat dipasang langsung pada perangkat Android setelah mengizinkan
instalasi dari sumber tidak dikenal. Untuk Google Play, gunakan APK/AAB rilis
dengan keystore pribadi; jangan memasukkan keystore atau `google-services.json`
ke repository publik.

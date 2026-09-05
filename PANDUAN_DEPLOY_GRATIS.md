# JepretAja — Panduan Deploy Tanpa Modal

Semua langkah di bawah ini bisa dijalankan **tanpa kartu kredit dan tanpa biaya**.

---

## Ringkasan: apa yang gratis dan apa yang tidak

| Kebutuhan | Dipakai | Biaya | Butuh kartu? |
|---|---|---|---|
| Hosting website admin | Vercel Hobby | Rp 0 | Tidak |
| Backend (aksi admin, transaksi) | Vercel Serverless Function | Rp 0 | Tidak |
| Database | Firebase Firestore (Spark) | Rp 0 | Tidak |
| Login user & admin | Firebase Authentication | Rp 0 | Tidak |
| Simpan foto & video | Cloudinary Free | Rp 0 | Tidak |
| Build APK | GitHub Actions | Rp 0 | Tidak |
| Domain | `namaanda.vercel.app` | Rp 0 | Tidak |

**Yang sengaja TIDAK dipakai**, karena sejak 3 Februari 2026 Firebase mewajibkan paket Blaze (harus pasang kartu) untuk keduanya:

- ❌ Cloud Functions → diganti Vercel Serverless Function (`/api/admin`)
- ❌ Cloud Storage → diganti Cloudinary (25 GB gratis, tanpa kartu)

Firestore dan Authentication tetap gratis penuh di paket Spark, jadi tidak ada yang perlu diubah di sana.

---

## Bagian 1 — Firebase (15 menit)

1. Buka [console.firebase.google.com](https://console.firebase.google.com) → **Add project** → beri nama `jepretaja`. **Jangan** upgrade ke Blaze saat ditawari; tetap di Spark.
2. **Build → Firestore Database → Create database** → pilih lokasi `asia-southeast2` (Jakarta) → mode **Production**.
3. **Build → Authentication → Get started** → aktifkan **Email/Password**.
4. **Project Settings (ikon gerigi) → Your apps → Web (`</>`)** → daftarkan app → salin nilai `apiKey`, `authDomain`, dst. Simpan dulu, nanti dipakai di Bagian 2.
5. **Project Settings → Service accounts → Generate new private key** → file JSON terunduh. **Jangan pernah commit file ini ke GitHub.**
6. **Project Settings → Your apps → Android** → `applicationId` harus persis `com.jepretaja.app` → unduh `google-services.json`. Dipakai di Bagian 4.

### Pasang security rules

Tanpa ini database Anda terbuka atau terkunci total. File `firestore.rules` sudah disiapkan.

```bash
npm install -g firebase-tools
firebase login
cd jepretaja_admin
firebase deploy --only firestore:rules --project <project-id-anda>
```

Firebase CLI dan deploy rules gratis, tidak butuh Blaze.

### Buat super admin pertama

Ini satu-satunya langkah manual, karena akun admin pertama belum bisa mengundang dirinya sendiri.

1. **Authentication → Users → Add user** → isi email & password Anda → salin **User UID** yang muncul.
2. **Firestore → Start collection** → Collection ID: `admin_users`.
3. Document ID: tempel **UID** tadi. Isi field:

| Field | Type | Value |
|---|---|---|
| `email` | string | email Anda |
| `name` | string | nama Anda |
| `role` | string | `super_admin` |
| `status` | string | `active` |

Setelah ini, admin berikutnya cukup diundang lewat halaman **Admin Users** di web.

---

## Bagian 2 — Website ke GitHub dan Vercel (10 menit)

### A. Unggah source code website ke GitHub

Gunakan folder website yang berisi `package.json`, `src`, `api`, dan `vercel.json`.
Pada Windows PowerShell, jalankan perintah berikut dari folder tersebut:

```powershell
git init
git branch -M main
git add .
git commit -m "Initial website upload"
git remote add origin https://github.com/USERNAME/NAMA-REPO-WEBSITE.git
git push -u origin main
```

Sebelumnya, buat repository kosong di GitHub (**New repository**). Jangan centang
opsi pembuatan README atau `.gitignore` jika source code lokal sudah memiliki
file-file tersebut. Jika repository lokal sudah memakai Git, lewati `git init`
dan `git branch -M main`, lalu periksa alamat remote dengan `git remote -v`.

Sebelum `git add .`, pastikan file rahasia tidak ikut terunggah. Service account
Firebase, `.env`, dan private key tidak boleh masuk repository. Variabel `VITE_*`
boleh berisi konfigurasi client Firebase, tetapi bukan kredensial server.

### B. Hubungkan repository ke Vercel

1. Buka [vercel.com](https://vercel.com) → **Sign up with GitHub** → **Add New → Project** → pilih repository tadi.
2. Vercel otomatis mendeteksi Vite. Jika repository berisi beberapa proyek, isi **Root Directory** dengan folder yang berisi `package.json` website.
4. Buka **Environment Variables**, isi semuanya (nilai dari Bagian 1 langkah 4):

```
VITE_FIREBASE_API_KEY
VITE_FIREBASE_AUTH_DOMAIN
VITE_FIREBASE_PROJECT_ID
VITE_FIREBASE_STORAGE_BUCKET
VITE_FIREBASE_MESSAGING_SENDER_ID
VITE_FIREBASE_APP_ID
```

Lalu satu lagi yang **paling penting**:

```
FIREBASE_SERVICE_ACCOUNT
```

Isinya seluruh isi file JSON service account dari Bagian 1 langkah 5 — buka filenya dengan Notepad, blok semua (Ctrl+A), salin, tempel ke kotak value.

> Perhatikan: nama variabel ini **tidak** diawali `VITE_`. Itu disengaja. Variabel berawalan `VITE_` ikut terbundel ke dalam file JavaScript yang diunduh browser — kalau service account key ikut ke sana, siapa pun bisa mengambil kendali penuh atas database Anda.

5. Klik **Deploy**. Vercel akan menjalankan build dan memberikan URL publik seperti `https://nama-project-anda.vercel.app`.
6. Setiap `git push origin main` berikutnya memicu deployment baru secara otomatis. Pull request juga dapat memperoleh Preview Deployment.

### Setelah deploy

Kembali ke Firebase Console → **Authentication → Settings → Authorized domains** → **Add domain** → masukkan `nama-project-anda.vercel.app`. Tanpa ini login akan ditolak.

Coba buka URL-nya, login dengan email super admin tadi, lalu buka satu halaman yang punya tombol aksi (misalnya Bookings) untuk memastikan `/api/admin` merespons.

---

## Bagian 3 — Cara kerja backend barunya

Sebelumnya web ini memanggil Cloud Functions lewat `httpsCallable`. Sekarang:

```
Browser admin
   │  POST /api/admin  { action, ...payload }
   │  Header: Authorization: Bearer <ID token Firebase>
   ▼
Vercel Serverless Function (api/admin.js)
   │  1. Verifikasi ID token dengan Firebase Admin SDK
   │  2. Baca koleksi admin_users → cek role & status
   │  3. Cek permission (api/_lib/rbac.js)
   │  4. Jalankan aksi di dalam runTransaction
   ▼
Firestore
```

Enam aksi yang tersedia: `processWithdrawal`, `markWithdrawalManual`, `resolveDispute`, `adminUpdateBookingStatus`, `inviteAdmin`, `writeAdminAuditLog`.

Tingkat keamanannya setara dengan Cloud Functions sebelumnya:

- Browser tidak pernah menulis langsung ke `wallets`, `withdrawals`, `escrow_transactions`, atau `audit_logs` — `firestore.rules` menolak semuanya. Hanya Admin SDK di server yang bisa, dan Admin SDK memang melewati rules.
- `adminId` di audit log selalu diambil dari token yang sudah diverifikasi server, bukan dari body request, jadi admin tidak bisa mengaku sebagai admin lain.
- Semua mutasi saldo dibungkus `runTransaction`. Dua admin yang menekan Approve bersamaan tidak bisa memotong saldo dua kali.
- Ledger `wallet_transactions` bersifat immutable — pembalikan dicatat sebagai baris baru, tidak pernah meng-update baris lama.

---

## Bagian 4 — Cloudinary untuk foto & video (5 menit)

Firebase Storage sudah tidak dipakai lagi. `StorageService.kt` sekarang meng-upload ke Cloudinary.

1. Daftar gratis di [cloudinary.com](https://cloudinary.com) — cukup email, tidak diminta kartu.
2. Di **Dashboard**, catat **Cloud name** (misalnya `dxxxxxxxx`).
3. Buka **Settings (gerigi) → Upload → Upload presets → Add upload preset**:
   - **Signing Mode**: `Unsigned` ← wajib
   - **Preset name**: catat namanya, misalnya `jepretaja_unsigned`
   - Opsional tapi disarankan: batasi **Max file size** (misal 20 MB untuk foto, 100 MB untuk video) supaya kuota tidak habis karena satu orang.
4. Simpan.

Dua nilai itu masuk ke build APK. **Jangan pernah memasukkan API Secret Cloudinary ke dalam APK** — file APK bisa dibongkar siapa saja, dan dengan API Secret orang lain bisa menghapus seluruh media Anda. Unsigned preset memang dirancang untuk dipakai dari sisi klien, jadi cukup itu saja.

Untuk build lokal, isi di `gradle.properties`:

```properties
CLOUDINARY_CLOUD_NAME=dxxxxxxxx
CLOUDINARY_UPLOAD_PRESET=jepretaja_unsigned
```

Untuk build di GitHub Actions, tambahkan dua repository secret dengan nama yang sama (caranya sama seperti `GOOGLE_SERVICES_JSON` di bagian berikutnya).

> Kalau dikosongkan, APK tetap berhasil di-build. Yang terjadi hanya: saat pengguna mencoba upload, muncul pesan yang menyebutkan konfigurasi mana yang belum diisi — bukan crash.

Perubahan lain yang ikut terbawa: upload video tidak lagi butuh Cloud Run video-processor. Cloudinary langsung mengembalikan URL video siap putar beserta thumbnail-nya, jadi post tidak pernah tersangkut di status "processing".

---

## Bagian 5 — APK lewat GitHub Actions (10 menit)

Anda tidak perlu menginstal Android Studio. Build berjalan di server GitHub, gratis.

1. Buat repo GitHub baru, push **isi** folder `jepretaja_android_kotlin` ke root repo (jadi `build.gradle.kts` ada di root, bukan di dalam subfolder).
2. Ubah `google-services.json` jadi base64:

   - Linux/Git Bash: `base64 -w0 google-services.json`
   - macOS: `base64 -i google-services.json`
   - Windows PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("google-services.json"))`

3. Di repo GitHub: **Settings → Secrets and variables → Actions → New repository secret**
   - Name: `GOOGLE_SERVICES_JSON`
   - Secret: tempel hasil base64 tadi
4. Buka tab **Actions → Build APK → Run workflow**.
5. Tunggu 5–10 menit. Unduh APK di bagian **Artifacts** pada halaman run tersebut.
6. Kirim file `app-debug.apk` ke HP, aktifkan "Install dari sumber tidak dikenal", pasang.

APK debug bisa langsung dipasang dan dibagikan ke siapa saja. Untuk masuk Play Store nanti perlu APK/AAB release yang ditandatangani (dan biaya pendaftaran developer Google Play sekali seumur hidup — di luar cakupan gratis ini).

---

## Bagian 6 — Publikasikan APK melalui GitHub Releases

Artifact pada GitHub Actions hanya untuk penyimpanan sementara dan biasanya
memiliki masa retensi. Agar pengguna memiliki tautan unduhan yang stabil,
unggah APK sebagai **asset** pada GitHub Release.

### A. Siapkan APK

1. Jalankan workflow **Actions → Build APK → Run workflow**, atau push ke branch
   `main` dan tunggu sampai job selesai tanpa error.
2. Buka run yang berhasil → **Artifacts** → unduh `jepretaja-debug-apk`.
3. Ekstrak ZIP artifact dan ubah nama file menjadi nama yang jelas, misalnya
   `jepretaja-1.0.0-debug.apk`. Untuk distribusi publik, lebih baik gunakan APK
   release yang sudah ditandatangani keystore pribadi; jangan memakai APK yang
   belum selesai diuji.

### B. Buat Release dan unggah APK

1. Buka halaman repository GitHub → **Releases** → **Draft a new release**.
2. Isi **Choose a tag** dengan tag baru, misalnya `v1.0.0`, lalu pilih **Create new tag**.
3. Isi judul, misalnya `JepretAja Android v1.0.0`, tulis catatan perubahan, dan
   tarik file APK ke area **Attach binaries** atau pilih file tersebut.
4. Klik **Publish release**. Jangan mengunggah APK ke kolom source code atau
   menjadikan file APK sebagai commit biasa; file harus terlihat di bagian
   **Assets** release.

### C. Salin direct link APK

Untuk release tertentu, pola URL langsungnya adalah:

```text
https://github.com/USERNAME/NAMA-REPO/releases/download/v1.0.0/jepretaja-1.0.0-debug.apk
```

Untuk selalu mengarah ke release terbaru, gunakan:

```text
https://github.com/USERNAME/NAMA-REPO/releases/latest/download/jepretaja-1.0.0-debug.apk
```

Nama file pada URL harus sama persis dengan nama asset di GitHub. Jika nama APK
berubah setiap versi, gunakan nama asset tetap seperti `jepretaja.apk` agar URL
`latest` tidak perlu diubah. Uji tautan tersebut di browser mode pribadi atau
salin ke perangkat Android; halaman GitHub tidak boleh muncul dan unduhan harus
langsung dimulai. Jangan gunakan tautan **Actions Artifact**, karena itu bukan
tautan publik permanen dan dapat kedaluwarsa.

### D. Instal APK di Android

1. Buka direct link di Chrome atau browser Android, lalu unduh APK.
2. Jika muncul peringatan, izinkan browser yang digunakan memasang aplikasi dari
   sumber ini melalui **Settings → Install unknown apps**. Menu dapat berbeda
   menurut merek dan versi Android.
3. Buka file dari notifikasi unduhan atau folder **Downloads**, lalu pilih
   **Install**. Jika Android memblokirnya, aktifkan izin tersebut dan ulangi.
4. Setelah instalasi selesai, buka JepretAja dan uji login, upload, serta fitur
   yang membutuhkan backend.

Untuk pembaruan, unggah APK versi baru dengan `versionCode` lebih tinggi pada
Release berikutnya. APK harus ditandatangani dengan keystore yang sama agar dapat
memperbarui instalasi sebelumnya; jangan membagikan keystore atau password-nya.

### E. Checklist sebelum dibagikan

- [ ] GitHub repository tidak memuat service account, `.env`, keystore, atau password.
- [ ] Vercel deployment terakhir berstatus **Ready** dan URL publik dapat dibuka.
- [ ] APK berhasil dipasang pada perangkat uji dan versi aplikasi benar.
- [ ] Release memiliki asset APK di bagian **Assets**, bukan hanya artifact Actions.
- [ ] Direct link dicoba dari perangkat yang tidak login ke GitHub.

---

## Yang masih perlu dikerjakan

Saya sampaikan apa adanya supaya tidak ada kejutan:

**1. APK masih memanggil Cloud Functions untuk booking & pembayaran.**
`BookingRepository`, `PaymentRepository`, dan `AvailabilityRepository` memanggil 13 callable (`createBooking`, `createPaymentOrder`, `startService`, dan seterusnya) yang belum dipindah ke Vercel. Selama belum dipindah: jelajah, profil creator, chat, dan upload media sudah jalan; pembuatan booking dan pembayaran belum. Solusinya sama seperti yang sudah dilakukan untuk web — tambah aksi di `/api/admin` (atau endpoint baru `/api/app`) lalu ganti `getHttpsCallable(...)` dengan panggilan HTTP.

**2. Payment gateway.**
Midtrans/Xendit punya mode sandbox gratis untuk pengembangan, tapi akun produksi butuh dokumen usaha. Untuk awal, alur transfer manual (admin menandai lunas lewat halaman Withdrawal) sudah bisa dipakai — tombolnya sudah berfungsi.

**3. Batas kuota gratis** yang perlu diingat:

- Firestore: 50.000 baca + 20.000 tulis per hari, penyimpanan 1 GB
- Cloudinary: 25 kredit/bulan (kira-kira 25 GB penyimpanan atau 25 GB bandwidth)

Untuk ratusan pengguna pertama ini lega. Kalau Firestore tembus kuota, layanan berhenti sampai hari berikutnya — tidak ada tagihan mendadak, karena Spark memang tidak bisa menagih.

## Kalau ada yang error

| Gejala | Penyebab biasanya |
|---|---|
| Login ditolak terus | Domain Vercel belum ditambahkan di Authentication → Authorized domains |
| Halaman putih setelah refresh di `/users` | `vercel.json` tidak ikut ter-deploy — pastikan ada di root project |
| "FIREBASE_SERVICE_ACCOUNT belum diisi" | Variabel belum diisi di Vercel, atau lupa Redeploy setelah menambahkannya |
| "Akun ini tidak terdaftar sebagai admin" | Document ID di `admin_users` tidak sama persis dengan UID di Authentication |
| Semua data kosong padahal ada isinya | `firestore.rules` belum di-deploy |
| Build APK gagal di langkah pertama | Secret `GOOGLE_SERVICES_JSON` belum diisi |
| "Cloudinary belum dikonfigurasi" saat upload | Secret `CLOUDINARY_CLOUD_NAME` / `CLOUDINARY_UPLOAD_PRESET` kosong saat APK di-build — isi lalu build ulang |
| Upload ditolak "Upload preset must be whitelisted" | Signing Mode preset masih `Signed`, ubah ke `Unsigned` |

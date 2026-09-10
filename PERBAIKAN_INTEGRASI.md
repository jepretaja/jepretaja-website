# Perbaikan Integrasi APK ↔ Panel Admin — 4 September 2026

Enam titik yang membuat kedua proyek belum benar-benar tersambung. Semuanya
sudah diperbaiki di kedua zip ini.

---

## 1. Pendaftaran creator selalu gagal

**Dulu:** `AuthRepository.registerCreator` menulis `users`, `creators`, dan
`wallets` dalam satu `WriteBatch`. `firestore.rules` menutup total penulisan ke
`wallets` (`allow write: if false`). Batch Firestore bersifat atomic, jadi satu
penulisan terlarang membatalkan seluruhnya: dokumen `users` dan `creators` tidak
pernah dibuat, padahal akun Firebase Auth sudah terlanjur ada. Akibatnya setiap
panggilan server membalas "Akun tidak ditemukan".

**Sekarang:** penulisan `wallets` dihapus dari batch. Dokumen wallet dibuat
server saat dana pertama dilepas (`releaseEscrow` memakai `set` + `merge`), dan
layar wallet menampilkan saldo 0 selama dokumennya belum ada.

- `app/src/main/java/com/jepretaja/app/data/repository/AuthRepository.kt`

## 2. Pembayaran selalu error "redirectUrl kosong dari server"

**Dulu:** `PaymentViewModel` menunggu field `redirectUrl` (Midtrans Snap) dan
membuka WebView. Server sudah lama memakai alur transfer manual dan mengirim
`uniqueCode`, `transferAmount`, dan data rekening — tidak pernah `redirectUrl`.

**Sekarang:** layar pembayaran menampilkan instruksi transfer langsung (bank,
nomor rekening, nominal + kode unik, batas waktu, tombol salin), lalu tombol
"Saya Sudah Transfer" membawa ke layar menunggu verifikasi admin. Rute dan layar
WebView Midtrans dihapus.

- `ui/screens/payment/PaymentViewModel.kt`, `PaymentScreen.kt`,
  `PaymentResultScreen.kt`
- `core/navigation/JepretAjaNavGraph.kt`, `core/navigation/Routes.kt`
- dihapus: `ui/screens/payment/PaymentWebViewScreen.kt`

## 3. Query status pembayaran ditolak Firestore

**Dulu:** `payments` di-query hanya dengan filter `bookingId`, sementara aturan
menuntut `resource.data.customerId == uid()`. Firestore menilai query terhadap
kemungkinan hasilnya, bukan dokumen yang benar-benar kembali, jadi seluruh query
ditolak PERMISSION_DENIED.

**Sekarang:** filter `customerId` ikut disertakan (diambil dari sesi login).

- `data/repository/PaymentRepository.kt`

## 4. Add-on paket tidak pernah cocok dengan server

**Dulu:** APK menyimpan add-on di sub-koleksi `packages/{id}/add_ons`; server
membaca array `pkg.addOns` dan mencocokkan `a.id === id`. Selain itu sub-koleksi
`add_ons` tidak pernah disebut di `firestore.rules`, jadi jatuh ke aturan
tolak-semua paling bawah.

**Sekarang:** add-on disimpan sebagai array `addOns` di dokumen paket dengan
bentuk `{ id, name, price }` — persis yang dibaca server. `upsertPackage`
memakai `SetOptions.merge()` supaya menyunting paket tidak menghapus add-onnya.

- `data/repository/CreatorRepository.kt`, `data/model/PackageModel.kt`

> **Data lama:** add-on yang sudah terlanjur tersimpan di sub-koleksi tidak ikut
> berpindah sendiri. Kalau sudah ada isinya, buat ulang dari layar Kelola Paket.

## 5. Pengajuan penarikan saldo tidak pernah tersimpan

**Dulu:** APK menulis langsung ke koleksi `withdrawals` yang ditutup aturan, dan
tidak ada aksi server padanannya.

**Sekarang:** ada aksi server `requestWithdrawal` yang memeriksa nominal
minimum (`settings/general.minWithdrawal`), saldo tersedia, dan menolak
pengajuan ganda. Saldo baru dipotong saat admin menekan Approve, seperti
sebelumnya.

- baru: `api/_lib/actions/appWallet.js`; didaftarkan di `api/app.js`
- `data/repository/WalletRepository.kt`, `core/util/FirestorePaths.kt`

## 6. Pengaturan platform dibaca dari dokumen yang salah

**Dulu:** panel menyimpan ke `settings/general`, server membaca
`settings/platform`. Efeknya fee yang diatur admin diabaikan (selalu 10%) dan
instruksi transfer selalu menampilkan rekening default "BCA / -".

**Sekarang:** server membaca `settings/general`, dan form Pengaturan di panel
punya tiga field baru: Nama Bank, Nomor Rekening, Nama Pemilik Rekening.

- `api/_lib/actions/appBooking.js`, `api/_lib/actions/appPayment.js`
- `src/pages/settings/Settings.jsx`

## Tambahan: indeks Firestore

`firestore.indexes.json` ditambah empat indeks yang dibutuhkan query server:
`bookings(creatorId, dateKey, status)`, `payments(bookingId, customerId)`,
`payments(bookingId, status)`, `withdrawals(creatorId, status)`.

---

# Yang masih harus Anda lakukan sendiri

1. **Deploy rules & indeks:**
   `firebase deploy --only firestore:rules,firestore:indexes`
2. **Isi rekening tujuan** di panel: Settings → Rekening Penerima Transfer.
   Sebelum ini diisi, instruksi transfer di aplikasi masih kosong.
3. **`API_BASE_URL`** di `gradle.properties` masih menunjuk IP laptop
   (`http://10.191.206.86:5173`). Hanya jalan untuk build debug, satu Wi-Fi,
   dengan `npm run dev` menyala. Setelah deploy ke Vercel, ganti ke
   `https://<project>.vercel.app` lalu build ulang.
4. **SHA-1 belum terdaftar** di Firebase — `google-services.json` tidak punya
   OAuth client Android, jadi Google Sign-In kemungkinan besar gagal dengan
   `ApiException: 10`. Tambahkan SHA-1 debug & release di Firebase Console lalu
   unduh ulang `google-services.json`.
5. **Rotasi service account key.** File `.env` berisi private key lengkap dan
   ikut terbawa saat zip dibagikan. Buat key baru di Firebase Console →
   Project Settings → Service accounts, dan hapus yang lama.
6. `node_modules/`, `dist/`, `build/`, dan `.gradle/` tidak disertakan di zip
   ini. Jalankan `npm install` di panel admin dan Gradle Sync di Android Studio.

---

# Tambahan — Badge jumlah di tombol Chat & Notifikasi (4 September 2026)

**Di APK**

- Tombol Chat dan Notifikasi di header Home sekarang memakai `BadgedIconButton`
  (komponen baru di `ui/components/AppComponents.kt`): lingkaran merah
  (`AppColors.Danger`) berisi angka, muncul HANYA saat jumlahnya di atas nol,
  dan dipendekkan jadi "99+" di atas 99. Badge permanen berisi "0" justru
  melatih mata mengabaikannya.
- Dua tombol pintasan yang sama di halaman Profil ikut memakai badge.
- Angkanya memakai snapshot listener, jadi bertambah begitu pesan atau
  notifikasi masuk tanpa perlu membuka ulang layar, dan hilang sendiri setelah
  dibaca.

Sumber angkanya:
- `ChatRepository.streamUnreadCount(uid)` — pesan dengan `readAt == null` yang
  bukan kiriman sendiri. Penyaring pengirim dikerjakan di klien karena Firestore
  tidak mengizinkan `!=` digabung dengan array-contains.
- `NotificationRepository.streamUnreadCount(uid)` — notifikasi dengan
  `readAt == null`. Repository ini baru; query notifikasi yang tadinya menempel
  di dalam `NotificationsViewModel` dipindah ke sini supaya badge dan layar
  Notifikasi memakai sumber yang sama.

**Di server (jepretaja_website)**

Koleksi `notifications` sebelumnya tidak pernah ditulis siapa pun, jadi badge
lonceng akan selamanya 0. Sekarang `api/_lib/notify.js` menulis notifikasi pada
kejadian berikut:

| Kejadian | Yang diberi tahu |
| --- | --- |
| Booking baru dibuat | creator |
| Status booking berpindah | pihak lawan (bukan yang menekan tombol) |
| Pengajuan refund | creator |
| Sengketa dibuka | pihak lawan |
| Pembayaran manual diverifikasi admin | pelanggan + creator |
| Dana escrow dilepas | creator |
| Penarikan disetujui / ditolak / ditransfer / gagal | creator |

`readAt: null` ditulis eksplisit di setiap notifikasi — di Firestore, dokumen
yang tidak punya field itu TIDAK cocok dengan query `where('readAt','==',null)`,
jadi tanpa baris itu badge tetap nol meski notifikasinya masuk.

**Indeks baru** di `firestore.indexes.json`: `notifications(userId, readAt)` dan
`messages(participants CONTAINS, readAt)`. Wajib di-deploy:
`firebase deploy --only firestore:indexes`.

---

# Prioritas 1 — lima kelengkapan ala TikTok (4 September 2026)

## 1. Halaman Pengikut & Mengikuti

Layar baru `ui/screens/follows/FollowListScreen.kt`, dua tab dalam satu halaman,
dibuka dengan mengetuk angka di profil (route `follows/{targetUserId}/{tab}`).

Nama dan foto dibaca dari dokumen `follows` itu sendiri, bukan dari koleksi
`users`. Ini bukan pengulangan data yang malas: `users` memuat email, nomor
telepon, dan token FCM, dan aturan Firestore hanya mengizinkan pemiliknya
membacanya. Menerjemahkan userId lewat `users` akan gagal dibaca untuk semua
orang, dan satu-satunya cara membuatnya berhasil adalah membuka data pribadi
semua pengguna ke publik. Karena itu `toggleFollow` kini menyimpan nama & foto
kedua pihak saat tombol Ikuti ditekan.

Dokumen follow lama belum punya field itu, jadi tampil sebagai "Pengguna".

## 2. Tab "Disukai" di profil

`ExploreRepository.streamLikedPosts(userId)`. Penanda suka sudah lama ditulis
tiap kali tombol hati ditekan, tapi belum pernah ada satu layar pun yang
membacanya kembali. Grid profil sekarang punya tiga tab: Karya, Tersimpan,
Disukai.

Angka statistik profil juga jadi bisa diketuk — Pengikut/Mengikuti membuka
daftar koneksi, Tersimpan pindah tab, Booking membuka daftar booking.

## 3. Inbox bertab + notifikasi suka/komentar/follow

Layar Notifikasi kini bertab: Semua / Suka / Komentar / Pengikut / Sistem,
lengkap dengan hitungan yang belum dibaca per tab. Kategorinya memakai field
`type` yang sudah lama ada di model tapi tidak pernah dipakai.

Aksi server baru `notifyInteraction` (`api/_lib/actions/appNotify.js`) yang
menulis notifikasinya. Ditulis server, bukan aplikasi, karena aturan Firestore
menutup pembuatan `notifications` dari klien sepenuhnya — kalau dibuka, siapa
pun bisa mengarang notifikasi atas nama orang lain. Server memverifikasi dulu
bahwa suka/komentar/follow-nya benar-benar tercatat sebelum menulis.

Dua perilaku yang disengaja:
- Membatalkan suka tidak mengirim apa pun. Kalau tidak, menekan hati bolak-balik
  jadi cara mengirim notifikasi berulang ke orang lain.
- Id notifikasi suka & follow deterministik (`like_{postId}_{uid}`), jadi
  menekan tombol berkali-kali memperbarui satu notifikasi, bukan menumpuk.
- Balasan komentar memberi tahu penulis komentar induknya, bukan pemilik post.

## 4. Urutan komentar: Terbaru / Teratas

Balasan bertingkat sudah ada sebelumnya; yang ditambahkan adalah pemilih urutan.
Diurutkan di memori, bukan lewat query baru: `orderBy("likeCount")` menuntut
indeks komposit yang harus di-deploy dulu, sementara komentar satu post sudah
seluruhnya ada di tangan aplikasi.

## 5. Riwayat pencarian & tab hasil

- Riwayat 8 kata kunci terakhir, tersimpan di perangkat lewat `AppPreferences`
  (bukan di Firestore — riwayat pencarian adalah jejak minat pribadi, dan tidak
  ada fitur yang membutuhkannya di server). Bisa dihapus satu-satu atau semua.
- Tombol Enter di papan ketik ikut menjalankan pencarian.
- Hasil pencarian jadi tiga tab: **Creator**, **Karya**, **Kategori**. Memilih
  kategori menyaring ulang di layar yang sama, tidak menumpuk halaman baru.

Batasnya jujur disebut di kode: Firestore tidak punya pencarian teks penuh, jadi
tab Karya menyaring 120 post terbaru di memori. Pencarian teks sungguhan butuh
layanan indeks terpisah (Algolia/Typesense).

## Yang perlu dijalankan

Tidak ada indeks baru untuk bagian ini — semua query sudah tercakup indeks yang
ada. Tapi kalau indeks dari perubahan sebelumnya belum di-deploy:
`firebase deploy --only firestore:indexes`

---

# Prioritas 2 — bikin terasa hidup (4 September 2026)

## 6. Halaman hashtag & kategori

Ketuk `#wedding` di caption atau chip kategori di feed → halaman berisi grid
karya bertagar itu (`ui/screens/tag/TagFeedScreen.kt`, route `tag/{tag}`).

Tagar dipindai dari caption saat unggah dan saat caption diedit, lalu disimpan
ke array `tags` di dokumen post. Ini bukan duplikasi yang bisa dihindari:
Firestore tidak bisa mencari substring, jadi tagar yang hanya "ada di dalam
teks caption" mustahil dicari — ia harus jadi data yang bisa dicocokkan
`array-contains`. Semua tagar disimpan huruf kecil supaya #Wedding dan #wedding
tidak jadi dua halaman.

Caption di feed kini dirender `CaptionText`, yang membuat tagar dan sebutan bisa
diketuk. Sebutan hanya dijadikan tautan kalau namanya tercatat di field
`mentions` post — supaya tidak ada teks bergaya tautan yang ternyata mati.

## 7. Draft, sampul video, dan sebutan @creator

- **Draft** tersimpan di perangkat (`AppPreferences`) dan dipulihkan otomatis
  saat layar unggah dibuka, dengan tombol "Buang draft". Disimpan lokal karena
  karya setengah jadi tidak semestinya menghuni Firestore, tempat ia ikut
  terbaca aturan, moderasi, dan penghitung.
- **Sampul video**: empat frame diambil dari video (5%, 25%, 50%, 75%) lalu
  creator memilih satu. Sebelumnya sampul ditentukan sepenuhnya oleh layanan
  media, sering jatuh pada frame gelap di detik pertama. Kalau unggah sampul
  gagal, sampul otomatis tetap dipakai — sampul bukan alasan menggagalkan video
  yang sudah terkirim seluruhnya.
- **Sebutan @creator**: mengetik "@" diikuti dua huruf memunculkan saran creator
  aktif; memilihnya menyisipkan nama lengkap dan mencatat pasangan
  nama → creatorId ke field `mentions`.

## 8. Menu per-post

Edit caption dan hapus sudah ada sebelumnya. Yang ditambahkan: **siapa yang
boleh komentar** (semua / hanya pengikut / dimatikan), tersimpan di field
`commentPolicy`.

Ditegakkan di `firestore.rules`, bukan hanya di UI — kalau hanya kolom komentar
yang disembunyikan, siapa pun yang memanggil Firestore langsung tetap bisa
menulis komentar di post yang komentarnya sudah dimatikan. Aturannya membaca
dokumen post (satu `get()` tambahan per komentar baru) dan, untuk mode "hanya
pengikut", memeriksa dokumen follow.

## 9. Bagikan profil & kirim post ke chat

- **QR + tautan profil** di menu setelan profil. Matriks QR dihitung
  `zxing-core` (pustaka Java murni, tanpa Activity dan izin kamera dari
  zxing-android-embedded) lalu digambar sendiri ke bitmap.
  Dependensi baru: `com.google.zxing:core:3.5.3`.
- **Kirim ke chat** dari menu post. Hanya percakapan yang SUDAH ada yang
  ditampilkan — di JepretAja chat tumbuh dari transaksi, dan membiarkan tombol
  bagikan membuka chat baru akan menjadikannya jalur pesan tak diminta ke
  creator mana pun. Yang dikirim tautannya, bukan salinan medianya, supaya
  penghitung tayangan dan tombol booking ikut terbawa.

## 10. Push FCM untuk suka/komentar/follow

`api/_lib/notify.js` kini mengirim push ke `users/{uid}.fcmToken` setiap kali
notifikasi in-app ditulis. Token itu sudah lama ditulis rajin oleh
`JepretAjaMessagingService` tapi belum pernah dipakai sekali pun — notifikasi
hanya terlihat kalau pengguna kebetulan sedang membuka aplikasi.

Dua detail yang disengaja:
- Push dikirim DI LUAR transaksi Firestore. Transaksi bisa diulang saat ada
  tabrakan tulisan, dan efek ke luar ikut terulang — penerima akan mendapat
  notifikasi yang sama dua kali.
- Token yang ditolak FCM (`registration-token-not-registered`) dihapus dari
  dokumen pengguna, supaya perangkat yang sudah lama dicopot tidak terus dicoba
  setiap notifikasi berikutnya.

Channel-nya `jepretaja_default`, sama dengan yang dibuat aplikasi.

## Yang perlu dijalankan

- `firebase deploy --only firestore:rules,firestore:indexes` — ada aturan
  komentar baru dan satu indeks baru `explore_posts(tags CONTAINS, status,
  createdAt)` untuk halaman tagar.
- Gradle Sync di Android Studio: ada dependensi baru (`zxing:core`).

---

# Alur unggah creator — 5 kelengkapan berikutnya (4 September 2026)

## 1. Rekam & jepret langsung dari alur unggah

Dua tombol baru: **Kamera** dan **Rekam**. Sebelumnya media hanya bisa diambil
dari galeri.

Dijalankan lewat aplikasi kamera bawaan (`TakePicture` / `CaptureVideo`), bukan
pratinjau CameraX di dalam aplikasi. Hasil praktisnya sama — creator bisa
merekam tanpa keluar dari alur unggah — tanpa harus mengelola sendiri izin
runtime, siklus hidup kamera, rotasi, dan pemilihan resolusi. Kamera dalam
aplikasi bergaya TikTok (tahan-untuk-merekam, balik kamera, timer, kecepatan)
adalah pekerjaan tersendiri yang jauh lebih besar; ini bukan penggantinya.

Hasil kamera ditulis ke cache lewat **FileProvider** baru
(`${applicationId}.fileprovider` + `res/xml/file_paths.xml`). Wajib: sejak
Android 7, menyerahkan Uri `file://` ke aplikasi lain memicu
FileUriExposedException dan kamera tertutup tanpa penjelasan.

## 2. Pratinjau video sungguhan

Video yang dipilih kini benar-benar diputar di kotak pratinjau. Sebelumnya di
situ hanya ada tulisan "Video dipilih", jadi creator mengirim karya tanpa pernah
bisa menonton ulang apa yang akan tayang.

## 3. Batas & pemeriksaan sebelum unggah

`core/util/MediaInfo.kt` membaca durasi, ukuran, dan resolusi begitu media
dipilih, lalu menampilkan ringkasannya ("2m 14d | 48 MB | 1080x1920"). Kalau
melanggar batas, alasannya muncul merah dan tombol Upload dinonaktifkan.

Batasnya: video maks 3 menit / 200 MB, foto maks 15 MB, lebar minimal 480px.

Pemeriksaan di depan, bukan di belakang: creator di jaringan seluler tidak perlu
menghabiskan kuota mengunggah 200 MB hanya untuk ditolak setelah selesai.
Sekalian mengisi `durationSeconds`, yang sudah lama ada di model post tapi tidak
pernah ditulis satu kali pun.

## 4. Pengaturan post sebelum tayang

Di halaman unggah, bukan setelahnya:

- **Siapa yang boleh komentar** — Semua / Pengikut / Nonaktif.
- **Izinkan orang menyimpan karya ini** (`allowSave`). Kalau dimatikan, tombol
  simpan hilang dari kartu post dan penyimpanan juga ditolak `firestore.rules` —
  menampilkan tombol yang pasti gagal hanya membuat ketukan terasa rusak.

Keduanya ditegakkan di aturan Firestore, bukan hanya di UI.

Visibilitas "hanya pengikut" sengaja TIDAK ditambahkan: query feed menyaring
berdasarkan status, dan menyaring per-hubungan-follow butuh perombakan cara feed
diambil — bukan sesuatu yang bisa dititipkan ke satu tombol.

## 5. Lokasi / venue

Field `location` sudah ada di model post sejak awal tapi selalu ditulis `null`.
Sekarang ada kolomnya di halaman unggah, dan nilainya tampil di kartu post
bersama kategori.

## Yang perlu dijalankan

- `firebase deploy --only firestore:rules` — ada aturan `allowSave` baru.
- Rebuild APK: ada FileProvider baru di AndroidManifest.

---

# Alur unggah creator — kelengkapan penuh (4 September 2026)

## Kamera dalam aplikasi (CameraX)

Layar kamera sendiri (`ui/screens/camera/CameraCaptureScreen.kt`): pratinjau
langsung, mode Foto/Video, balik kamera, penghitung durasi, dan berhenti
otomatis di batas maksimal.

Menggantikan pemanggilan aplikasi kamera bawaan. Bedanya bukan tampilan: dengan
CameraX, aplikasi yang menentukan kualitas rekaman (HD, bukan 4K yang pasti
melewati batas unggah), batas durasi, dan lensa mana yang aktif. Lewat intent,
ketiganya diserahkan ke aplikasi pihak lain yang berbeda di tiap merek ponsel.

Perekaman memakai tekan-mulai / tekan-berhenti, bukan tahan-untuk-merekam:
gestur tahan bertabrakan dengan tombol lain dan mudah terputus tidak sengaja.

Dependensi baru: `androidx.camera:*:1.3.4`. Izin baru: `RECORD_AUDIO`.

## Multi-foto satu post

Pemilih foto kini `GetMultipleContents` (maks 10). Di feed, post berisi beberapa
foto digeser mendatar dengan titik penanda halaman — `mediaUrls` memang sudah
berupa List sejak awal, tapi UI-nya hanya pernah menerima satu berkas.

## Daftar draft + jadwal tayang

- Draft kini **daftar**, bukan satu slot yang diam-diam saling menimpa.
  Disimpan sebagai array JSON di perangkat, maksimal 20, dibuka lewat tombol
  "Draft (n)" di kanan atas.
- **Jadwal tayang** memakai `setInitialDelay` WorkManager, bukan cron server.
  Alasannya: unggahannya sendiri sudah lewat WorkManager, jadi menjadwalkan
  berarti menunda pekerjaan yang sama — tanpa perlu menaruh karya setengah jadi
  di Firestore lebih dulu, dan tanpa bergantung pada frekuensi cron yang di
  Vercel Hobby dibatasi sekali sehari. Konsekuensinya jujur: perangkat harus
  menyala dan punya jaringan saat waktunya tiba.

## Unggahan latar belakang (WorkManager)

`data/work/UploadWorker.kt` + `UploadQueue.kt`. Menutup layar atau berpindah
aplikasi tidak lagi membatalkan unggahan — sebelumnya prosesnya hidup di
viewModelScope layar, dan mati bersama layarnya, paling menyakitkan tepat pada
video besar yang paling lama diunggah.

- Menunggu jaringan lewat `Constraints`, bukan gagal saat sedang offline.
- Gangguan jaringan diulang otomatis 3x dengan backoff eksponensial.
- Kegagalan permanen **mengembalikan isinya ke Draft**, jadi caption, kategori,
  dan pilihan media tidak hilang dan bisa dikirim ulang.
- Antrean tampil di layar unggah dengan kemajuan per item dan tombol Batal.

Worker sengaja tidak memakai Hilt: kedua ketergantungannya bisa dibuat langsung
(StorageService hanya butuh Context, Firestore punya instance global), sehingga
menambah HiltWorkerFactory hanya menambah bagian yang bisa salah.

## Trim video & sampul lewat scrubber

- **Potong**: RangeSlider memilih rentang, lalu `media3-transformer` benar-benar
  meng-encode ulang berkasnya sebelum diunggah. Menyimpan penanda potong tapi
  tetap mengunggah video utuh tidak menyelesaikan masalah kuota yang membuat
  fitur ini dibutuhkan.
- **Sampul**: penggeser bebas menggantikan empat frame tetap. Frame diambil
  dengan `OPTION_CLOSEST` (bukan `CLOSEST_SYNC`) supaya geseran kecil benar-benar
  mengubah gambar, bukan melompat ke keyframe yang sama.

Kalau pemotongan gagal, berkas asli tetap diunggah — gagal memotong bukan alasan
membatalkan unggahan.

## Status moderasi yang terlihat

Layar baru **"Karya Saya"** (profil → menu → Karya Saya) menampilkan SEMUA
unggahan beserta statusnya: sedang ditinjau, tayang, ditolak, disembunyikan.
Sebelumnya grid profil hanya menampilkan yang `published`, jadi karya yang
sedang antre seolah lenyap — dan keluhannya hampir selalu "aplikasinya rusak".

Di panel admin, menolak atau menyembunyikan karya kini **wajib disertai alasan**
(`moderationNote`), dan alasan itulah yang muncul di layar Karya Saya. Menolak
tanpa alasan membuat creator mengulangi kesalahan yang sama.

## Yang perlu dijalankan

- **Gradle Sync**: dependensi baru (CameraX, WorkManager, media3-transformer).
- Rebuild APK: ada izin `RECORD_AUDIO` baru di manifest.
- Tidak ada indeks atau aturan Firestore baru untuk bagian ini.

---

# Profil creator publik — sepuluh kelengkapan (4 September 2026)

## 1. Tombol Booking menempel

CTA di dasar layar: "Lihat Paket & Booking" beserta harga mulai dari
(`minPrice`). Sebelumnya satu-satunya jalan memesan adalah kebetulan membuka tab
"Paket" di urutan ketiga — halaman jualan dengan tombol jual paling tersembunyi.

Saat creator sedang libur, tombolnya TIDAK dimatikan begitu saja: berubah jadi
"Tanya Jadwal" (membuka chat), dengan alasannya tertulis di sebelahnya. Tombol
mati tanpa penjelasan hanya terbaca sebagai kerusakan.

## 2. Tombol Ikuti

`toggleFollow` dan `isFollowing` sudah lama ada dan dipakai di feed Explore, tapi
profil creator tidak pernah memanggilnya sama sekali. Sekarang ada, dengan
jumlah pengikut yang ikut berubah seketika.

## 3. Dua status yang berbeda, dibedakan dengan sengaja

- **Titik hijau "Online" / "Aktif 2 jam lalu"** — dari field `lastActiveAt` yang
  ditulis paling sering sekali per 5 menit saat aplikasi creator dibuka.
  Pembatas itu penting: kehadiran tidak layak dibayar satu tulisan Firestore
  setiap kali layar berganti.
- **Badge "Menerima booking" / "Libur sampai 20 Sep"** — saklar sadar milik
  creator di Creator Studio → Ketersediaan, lengkap dengan tanggal kembali dan
  catatan untuk pelanggan.

Saklar libur **ditegakkan di server** (`createBooking` menolak pesanan baru),
bukan sekadar menyembunyikan tombol. Kalau hanya UI, pesanan tetap bisa masuk
lewat panggilan langsung ke API dan creator yang sedang libur tetap menerima
jadwal yang tidak bisa ia penuhi.

## 4. Tiga angka ala TikTok, semuanya bisa diketuk

Mengikuti | Pengikut | Rating. Sebelumnya hanya rating dan followers, dan
keduanya mati saat disentuh.

## 5. Bagikan profil & menu laporan

Ikon bagikan (tautan profil lewat share sheet) dan menu titik tiga berisi
"Laporkan creator" dengan pilihan alasan. Laporannya masuk koleksi `reports`
dengan `targetType: "creator"` — dibedakan dari laporan satu karya, karena
tindak lanjutnya memang berbeda.

## 6. Waktu balas & tingkat penyelesaian

- **Waktu balas** diukur di perangkat creator saat ia membalas chat: selisih
  antara pesan terakhir lawan bicara dan balasannya, dirata-rata bergerak 1:4
  supaya satu balasan kilat tidak langsung mengubah angka. Batasnya jujur:
  karena ditulis dari perangkat creator sendiri, ini pada dasarnya laporan diri
  — berguna sebagai perkiraan, dan sengaja tidak dipakai untuk apa pun yang
  menentukan uang.
- **Tingkat penyelesaian** dari `completedBookings` / `totalBookings`, keduanya
  ditulis SERVER dan dilindungi `firestore.rules` dari pemiliknya sendiri.
  Angka kepercayaan yang bisa dikarang sendiri tidak berarti apa-apa.
  Disembunyikan sampai ada minimal 3 booking — menampilkan "0% selesai" pada
  creator baru menghukumnya karena belum sempat bekerja.

## 7. Ketersediaan ringkas

Tanggal penuh terdekat dari `availability_blocks`. Datanya sudah lama ada tapi
hanya terlihat oleh creator sendiri, padahal justru calon pelanggan yang perlu
tahu tanggal mana yang sudah terisi.

## 8. Grid rapat 3 kolom

Portfolio dan Explore memakai bentuk yang sama dengan grid profil sendiri: jarak
1dp, rasio 3:4, tanpa bayangan. Ubin kecil berbayang sebelumnya membuat profil
dengan sedikit karya terlihat seperti gagal memuat.

## 9. Karya disematkan

Menu post milik sendiri dapat "Sematkan di profil"; karya itu naik ke urutan
pertama grid dengan label. Penandanya disimpan di dokumen creator
(`pinnedPostId`), bukan di dokumen post — yang disematkan selalu satu, jadi
mustahil ada dua karya yang sama-sama mengaku tersemat.

## 10. Ulasan langsung di profil

Dua ulasan terbaru terbaca tanpa berpindah halaman, dengan "Lihat semua" di
sampingnya. Ulasan adalah alasan orang memilih fotografer; menyembunyikannya di
balik satu ketukan tambahan menghilangkan hal yang paling menentukan.

## Yang perlu dijalankan

`firebase deploy --only firestore:rules` — `totalBookings` dan
`completedBookings` kini masuk daftar field yang dilindungi dari pemilik profil.

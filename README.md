# JepretAja Admin — Website Admin (React)

Dashboard operasional JepretAja: user, creator, moderasi Explore, booking,
payment/escrow, wallet, withdrawal, refund, dispute, review, laporan,
kategori, promosi, notifikasi, analytics, settings, admin users, audit log
— sesuai section 18–22 & 29 dokumen konsep.

## Struktur Proyek

```

## Struktur Monorepo

Repository ini berisi website admin JepretAja. Source aplikasi Android
dipelihara di repository terpisah:

```
root/     <- website admin; deploy ke Vercel
Android   <- https://github.com/MuhammadRycky/jepretaja-apk
```

Vercel hanya memakai root repository ini untuk membangun website. Untuk membuka
APK di Android Studio, clone repository Android tersebut. Tambahkan file
`app/google-services.json` dari Firebase Console secara lokal; file itu sengaja
di-ignore dan tidak boleh diunggah ke repository publik.
src/
  firebase/       config Firebase + daftar nama koleksi Firestore
  auth/           AuthContext (login admin, cek role di admin_users)
  hooks/          useCollection / useDocument (stream Firestore realtime)
  components/     DataTable, StatCard, StatusBadge, SearchInput, dst
  layouts/        Sidebar + AdminLayout + CreatorLayout
  pages/          halaman panel admin, portal creator, dan dokumen publik
  utils/          formatCurrency, formatDate, apiClient (/api/admin & /api/app), dst
```

Panel admin mencakup "Daftar Screen Website Admin" ditambah beberapa layar yang
koleksinya sudah lama ada tapi belum punya tampilan: Katalog Paket, Portfolio
Creator, Moderasi Komentar, Monitoring Chat, dan Akun Saya. Di headernya ada
lonceng antrian (`hooks/useAdminQueue.js`) yang menghitung pekerjaan yang
menunggu ditangani — verifikasi, karya, laporan, sengketa, transfer manual,
penarikan, refund — disaring dengan permission yang sama dengan route-nya.

Portal creator (`/creator`) adalah area terpisah dengan gerbang aksesnya
sendiri — creator hanya melihat datanya sendiri. Selain ringkasan, paket,
portfolio, booking, dan ulasan, di sana ada Unggahan Saya (karya Explore:
unggah, ubah, hapus, plus catatan admin bila karyanya ditolak), Kalender
Ketersediaan, Pesan, Notifikasi, Statistik, dan pengajuan Verifikasi Akun.
Header portalnya memuat badge pesan & notifikasi belum dibaca
(`hooks/useUnreadCreator.js`), memakai penanda `readAt == null` yang sama
dengan badge di APK.

Halaman Saldo & Penarikan menyimpan rekening bank creator di `users/{uid}` —
**bukan** di `creators/{uid}` yang dibaca publik — lalu mengirim pengajuan
pencairan ke `/api/app` (`requestWithdrawal`), karena koleksi `withdrawals`
tertutup total untuk klien.

## Prasyarat

- Node.js 20+
- Project Firebase yang sama dengan APK (lihat repository Android)

## Setup

```bash
npm install
cp .env.example .env
# isi .env dengan config Firebase Web App Anda
npm run dev
```

Build production untuk Vercel:

```bash
npm run build
```

Hasil build ada di folder `dist/`. Proyek produksi ini memakai Vercel sebagai
hosting frontend sekaligus serverless API (`/api/app` dan `/api/admin`).

```bash
# deploy otomatis melalui Git integration Vercel setelah git push
# atau gunakan Vercel CLI jika diperlukan:
npx vercel --prod
```

## Autentikasi & Role Admin

Login memakai Firebase Authentication (Email/Password) yang sama dengan
APK. Setelah login, `AuthContext` mengecek dokumen
`admin_users/{uid}` — hanya user yang punya dokumen di koleksi ini yang
dianggap admin. **Anda perlu membuat dokumen ini secara manual** untuk
akun admin pertama:

```js
// via Firebase Console > Firestore, buat dokumen:
// collection: admin_users, document ID: <uid akun admin>
{ email: "admin@jepretaja.com", role: "super_admin", permissions: [] }
```

## Catatan implementasi penting

- Halaman ini membaca/menulis Firestore langsung dari client memakai
  akun admin yang sudah diverifikasi lewat Firestore Security Rules
  (`isAdminRole()` di `firestore.rules` pada folder website ini).
  **Pastikan rules tersebut sudah di-deploy** sebelum memakai dashboard ini,
  supaya akun non-admin tidak bisa membaca data sensitif meski tahu URL.
  Jalankan `npm run deploy:rules` dari folder website setelah memeriksa
  `.env` lokal, atau gunakan Firebase CLI dengan akun Google Anda.
- Aksi finansial (approve withdrawal, refund, release funds) di UI ini
  diproses oleh Vercel Serverless Function `/api/admin`, yang memverifikasi
  token Firebase, role, permission, dan transaksi Firestore di server.
  Jangan memindahkan aksi ini kembali ke update Firestore langsung dari
  browser.
- Custom claims (role admin di token Auth, bukan hanya dokumen Firestore)
  disarankan ditambahkan lewat Cloud Function `onAdminUserCreated` agar
  Security Rules bisa memvalidasi role tanpa extra `get()` read setiap kali.
- **Kas platform** (`/kas-platform`) — komisi `platformFee` kini ikut masuk ke
  `platform_wallet/main` saat escrow dilepas, dan super admin bisa mencatat
  pencairannya ke rekening pemilik. Hanya `super_admin`: izin
  `manage_platform_payout` tidak diberikan ke peran lain, dan `/api/admin`
  memeriksa nama perannya lagi di luar peta izin. Transfer banknya manual —
  panel mencatat pembukuannya dan menulis audit log berisi nominal, rekening
  tujuan, serta pelakunya. Tombol "Hitung Ulang" menyusun ulang saldo dari
  seluruh dokumen escrow, jadi angkanya selalu bisa dibuktikan dari sumbernya
  (dan itu pula cara memasukkan komisi dari escrow yang dilepas sebelum fitur
  ini ada). Deploy ulang rules setelahnya: `firebase deploy --only firestore:rules`.
- **Unggah foto** (portfolio, foto profil, dokumen verifikasi) memakai
  `components/ImagePicker.jsx`: berkas dipilih langsung dari perangkat, dikecilkan
  di browser, lalu diunggah ke Cloudinary menggunakan unsigned upload preset.
  Cloud name dan preset bersifat publik; API secret tidak boleh dimasukkan ke
  frontend.
  Firebase Storage bukan jalur upload aktif. Berkas `storage.rules` hanya
  dipelihara sebagai konfigurasi opsional; deploy jika Storage memang sengaja
  diaktifkan dengan `npm run deploy:rules:storage`.

## Yang masih perlu dikerjakan sebelum rilis

- Custom claims admin + proteksi rules yang lebih ketat
- Export laporan (CSV/PDF) untuk Analytics & Transactions
- Pagination untuk tabel dengan data besar (saat ini load semua dokumen)

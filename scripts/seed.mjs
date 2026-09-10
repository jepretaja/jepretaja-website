/**
 * Mengisi Firestore dengan data contoh yang lengkap untuk SELURUH halaman
 * panel admin dan aplikasi Android.
 *
 * Aplikasi galeri/booking foto akan selalu terlihat "sederhana" selama belum
 * ada isinya — semua kartu creator, feed Explore, paket, ulasan, transaksi,
 * dan dompet kosong. Script ini mengisi seluruh koleksi tersebut dengan
 * konten yang realistis dan SALING TERHUBUNG: booking menunjuk user dan
 * creator yang benar-benar ada, pembayaran menunjuk booking yang ada, dan
 * seterusnya — supaya setiap halaman panel bisa dicoba sungguhan, termasuk
 * tombol aksinya (verifikasi transfer, setujui penarikan, putuskan sengketa).
 *
 * Nama field & koleksi mengikuti persis model di aplikasi Android
 * (FirestorePaths.kt) dan kolom yang dibaca tiap halaman panel.
 *
 * Cara pakai (dari folder jepretaja_website):
 *   npm run seed          -> tambah/timpa data contoh
 *   npm run seed:hapus    -> hapus lagi semua data contoh
 *
 * SEMUA dokumen contoh memakai ID berawalan "demo_" sehingga mudah dibedakan
 * dari data asli dan bisa dihapus tanpa menyentuh data sungguhan. Dokumen
 * dompet sengaja memakai ID creator (demo_c1 dst.) karena panel memang
 * mengalamatkan wallets/{creatorId} — awalannya tetap "demo_".
 */
import { readFileSync } from 'node:fs';
import { initializeApp, cert } from 'firebase-admin/app';
import { getAuth } from 'firebase-admin/auth';
import { getFirestore, Timestamp } from 'firebase-admin/firestore';

// --- baca kredensial dari .env ---------------------------------------------
function loadServiceAccount() {
  if (process.env.FIREBASE_SERVICE_ACCOUNT) {
    return JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT);
  }
  const env = readFileSync(new URL('../.env', import.meta.url), 'utf8');
  const line = env.split('\n').find((l) => l.startsWith('FIREBASE_SERVICE_ACCOUNT='));
  if (!line) {
    console.error('FIREBASE_SERVICE_ACCOUNT tidak ditemukan di .env');
    process.exit(1);
  }
  return JSON.parse(line.slice('FIREBASE_SERVICE_ACCOUNT='.length));
}

const svc = loadServiceAccount();
initializeApp({ credential: cert(svc), projectId: svc.project_id });
const db = getFirestore();
const auth = getAuth();

// Foto contoh dari Unsplash (gratis dipakai, termasuk komersial).
const img = (id, w = 800) => `https://images.unsplash.com/${id}?auto=format&fit=crop&w=${w}&q=80`;

// Video contoh dari bucket publik Google (Blender Foundation, lisensi bebas).
// Setiap post video WAJIB punya thumbnailUrl berupa gambar: halaman Post
// Detail merender media lewat <img>, jadi tanpa thumbnail ia akan mencoba
// menampilkan berkas .mp4 di dalam tag gambar dan hasilnya kosong.
const vid = (nama) => `https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/${nama}.mp4`;
const VIDEO = {
  bunny: vid('BigBuckBunny'),
  elephant: vid('ElephantsDream'),
  blaze: vid('ForBiggerBlazes'),
  escape: vid('ForBiggerEscapes'),
  fun: vid('ForBiggerFun'),
  joyride: vid('ForBiggerJoyrides'),
  meltdown: vid('ForBiggerMeltdowns'),
  sintel: vid('Sintel'),
  steel: vid('TearsOfSteel'),
  gti: vid('VolkswagenGTIReview'),
};

const hariLalu = (n) => Timestamp.fromMillis(Date.now() - n * 864e5);
const jamLalu = (n) => Timestamp.fromMillis(Date.now() - n * 36e5);
const hariDepan = (n) => Timestamp.fromMillis(Date.now() + n * 864e5);

// ---------------------------------------------------------------- USERS ----
const USERS = [
  { id: 'demo_u1', name: 'Putri Ayuningtyas', email: 'putri.ayu@example.com', phone: '0812-1100-2201', role: 'customer', status: 'active', hari: 210 },
  { id: 'demo_u2', name: 'Ahmad Fauzi', email: 'ahmad.fauzi@example.com', phone: '0813-4455-8890', role: 'customer', status: 'active', hari: 178 },
  { id: 'demo_u3', name: 'Rina Marlina', email: 'rina.marlina@example.com', phone: '0857-2233-9012', role: 'customer', status: 'active', hari: 143 },
  { id: 'demo_u4', name: 'Michelle Tanuwijaya', email: 'michelle.t@example.com', phone: '0811-9090-4455', role: 'customer', status: 'active', hari: 121 },
  { id: 'demo_u5', name: 'Panitia HMTI', email: 'hmti.event@example.com', phone: '0821-7788-1234', role: 'customer', status: 'active', hari: 96 },
  { id: 'demo_u6', name: 'Toko Rasa Nusantara', email: 'rasanusantara@example.com', phone: '0838-6677-3321', role: 'customer', status: 'active', hari: 74 },
  { id: 'demo_u7', name: 'Bagas Prakoso', email: 'bagas.prakoso@example.com', phone: '0852-3344-7788', role: 'customer', status: 'suspended', hari: 45 },
  { id: 'demo_u8', name: 'Sinta Dewanti', email: 'sinta.dewanti@example.com', phone: '0878-1212-6543', role: 'customer', status: 'active', hari: 12 },
  // Akun user milik creator — di aplikasi, creator juga punya dokumen users.
  { id: 'demo_c1', name: 'Rangga Wicaksana', email: 'rangga.wicaksana@example.com', phone: '0812-5566-1010', role: 'creator', status: 'active', hari: 320 },
  { id: 'demo_c2', name: 'Anindya Pramesti', email: 'anindya.pramesti@example.com', phone: '0813-7788-2020', role: 'creator', status: 'active', hari: 295 },
  { id: 'demo_c3', name: 'Studio Kalacitra', email: 'studio.kalacitra@example.com', phone: '0812-3344-5566', role: 'creator', status: 'active', hari: 280 },
  { id: 'demo_c4', name: 'Bimo Hartanto', email: 'bimo.hartanto@example.com', phone: '0857-9900-1122', role: 'creator', status: 'active', hari: 264 },
  { id: 'demo_c5', name: 'Lentera Visual', email: 'lentera.visual@example.com', phone: '0811-2200-8899', role: 'creator', status: 'active', hari: 251 },
  { id: 'demo_c6', name: 'Nadia Kusuma', email: 'nadia.kusuma@example.com', phone: '0838-4455-6677', role: 'creator', status: 'active', hari: 233 },
];

/**
 * Kata sandi seragam untuk SELURUH akun demo.
 *
 * Dokumen Firestore saja tidak cukup untuk bisa masuk: gerbang login mencari
 * dokumen creators/{uid} berdasarkan uid dari Firebase Auth, jadi tanpa akun
 * Auth yang uid-nya sama persis, creator demo tidak akan pernah bisa login.
 * Karena itu akun Auth-nya dibuat di sini dengan uid yang dipaksa sama dengan
 * id dokumennya.
 *
 * INI KREDENSIAL CONTOH, bukan untuk produksi — semuanya memakai domain
 * example.com dan ikut terhapus oleh `npm run seed:hapus`.
 */
const SANDI_DEMO = 'Demo12345!';

// ------------------------------------------------------------- CREATORS ----
const CREATORS = [
  {
    id: 'demo_c1', displayName: 'Rangga Wicaksana', city: 'Jakarta Selatan',
    bio: 'Wedding & prewedding photographer. 8 tahun pengalaman, 300+ pasangan.',
    categories: ['Wedding', 'Prewedding', 'Couple'], rating: 4.9, reviewCount: 128,
    followerCount: 2410, verified: true, minPrice: 3500000,
    equipment: ['Sony A7 IV', 'Sigma 35mm f/1.4', 'Godox AD200'],
    photo: 'photo-1507003211169-0a1dd7228f2d', cover: 'photo-1519741497674-611481863552',
    lat: -6.2607, lng: 106.7816,
  },
  {
    id: 'demo_c2', displayName: 'Anindya Pramesti', city: 'Bandung',
    bio: 'Cerita hangat lewat cahaya alami. Spesialis family & graduation.',
    categories: ['Family', 'Wisuda', 'Couple'], rating: 4.8, reviewCount: 94,
    followerCount: 1680, verified: true, minPrice: 1200000,
    equipment: ['Canon R6', 'RF 50mm f/1.2'],
    photo: 'photo-1438761681033-6461ffad8d80', cover: 'photo-1529626455594-4ff0802cfb7e',
    lat: -6.9175, lng: 107.6191,
  },
  {
    id: 'demo_c3', displayName: 'Studio Kalacitra', city: 'Yogyakarta',
    bio: 'Tim dokumentasi event & konser. Siap liputan multi-kamera.',
    categories: ['Event', 'Video', 'Commercial'], rating: 4.7, reviewCount: 210,
    followerCount: 5320, verified: true, minPrice: 5000000,
    equipment: ['Sony FX3 x3', 'DJI RS4', 'Rode Wireless Pro'],
    photo: 'photo-1492691527719-9d1e07e534b4', cover: 'photo-1511578314322-379afb476865',
    lat: -7.7956, lng: 110.3695,
  },
  {
    id: 'demo_c4', displayName: 'Bimo Hartanto', city: 'Surabaya',
    bio: 'Product & food photography untuk brand dan UMKM.',
    categories: ['Product', 'Commercial'], rating: 4.6, reviewCount: 61,
    followerCount: 890, verified: false, minPrice: 900000,
    equipment: ['Nikon Z6 II', 'Macro 105mm', 'Studio strobe'],
    photo: 'photo-1500648767791-00dcc994a43e', cover: 'photo-1441986300917-64674bd600d8',
    lat: -7.2575, lng: 112.7521,
  },
  {
    id: 'demo_c5', displayName: 'Lentera Visual', city: 'Denpasar',
    bio: 'Destination wedding di Bali. Bahasa Indonesia & English.',
    categories: ['Wedding', 'Prewedding', 'Video'], rating: 4.9, reviewCount: 173,
    followerCount: 7140, verified: true, minPrice: 7500000,
    equipment: ['Sony A7R V', 'DJI Mavic 3', 'Gimbal'],
    photo: 'photo-1524504388940-b1c1722653e1', cover: 'photo-1537633552985-df8429e8048b',
    lat: -8.6705, lng: 115.2126,
  },
  {
    id: 'demo_c6', displayName: 'Nadia Kusuma', city: 'Jakarta Pusat',
    bio: 'Portrait & personal branding. Studio di Menteng.',
    categories: ['Couple', 'Commercial', 'Family'], rating: 4.5, reviewCount: 47,
    followerCount: 1120, verified: false, minPrice: 750000,
    equipment: ['Fujifilm X-T5', 'XF 56mm f/1.2'],
    photo: 'photo-1534528741775-53994a69daeb', cover: 'photo-1554048612-b6a482bc67e5',
    lat: -6.1944, lng: 106.8294,
  },
];

// Pengajuan verifikasi yang MASIH TERTUNDA -> mengisi halaman Creator
// Verification sehingga tombol Approve/Reject bisa benar-benar dicoba.
const VERIFICATIONS = [
  { id: 'demo_ver_1', creatorId: 'demo_c4', docs: ['photo-1554224155-6726b3ff858f', 'photo-1450101499163-c8848c66ca85'], hari: 4 },
  { id: 'demo_ver_2', creatorId: 'demo_c6', docs: ['photo-1568992687947-868a62a9f521'], hari: 2 },
  { id: 'demo_ver_3', creatorId: 'demo_c3', docs: ['photo-1586281380349-632531db7ed4', 'photo-1554224154-26032ffc0d07'], hari: 1 },
];

// -------------------------------------------------------------- PACKAGES ---
const PACKAGES = [
  { creator: 'demo_c1', name: 'Prewedding Half Day', price: 3500000, duration: '4 jam', personnel: '1 fotografer + 1 asisten', output: '80 foto edit, 1 album mini', desc: 'Satu lokasi pilihan di Jabodetabek, konsultasi konsep sebelum hari-H.' },
  { creator: 'demo_c1', name: 'Wedding Full Day', price: 12000000, duration: '10 jam', personnel: '2 fotografer + 1 videografer', output: '400 foto edit, video highlight 3 menit', desc: 'Liputan akad sampai resepsi, termasuk same-day-edit.' },
  { creator: 'demo_c2', name: 'Wisuda Personal', price: 1200000, duration: '2 jam', personnel: '1 fotografer', output: '40 foto edit, cetak 10R (2 lembar)', desc: 'Sesi di area kampus, sudah termasuk foto bersama keluarga.' },
  { creator: 'demo_c2', name: 'Family Session', price: 1800000, duration: '3 jam', personnel: '1 fotografer', output: '60 foto edit', desc: 'Outdoor atau di rumah, cocok untuk keluarga sampai 8 orang.' },
  { creator: 'demo_c3', name: 'Dokumentasi Event 1 Hari', price: 5000000, duration: '8 jam', personnel: '2 fotografer + 1 videografer', output: '250 foto, video after-movie 5 menit', desc: 'Seminar, gathering, atau launching produk.' },
  { creator: 'demo_c4', name: 'Foto Produk 20 Item', price: 900000, duration: '1 hari kerja', personnel: '1 fotografer', output: '20 foto (3 angle per item)', desc: 'Background putih siap unggah ke marketplace.' },
  { creator: 'demo_c5', name: 'Bali Destination Wedding', price: 25000000, duration: '2 hari', personnel: '3 fotografer + 2 videografer + drone', output: '600 foto, film 8 menit', desc: 'Termasuk sesi sunset di pantai dan drone footage.' },
  { creator: 'demo_c6', name: 'Personal Branding Studio', price: 750000, duration: '1,5 jam', personnel: '1 fotografer', output: '25 foto edit', desc: 'Untuk LinkedIn, profil perusahaan, atau portofolio pribadi.' },
];

// -------------------------------------------------------- EXPLORE POSTS ----
// Campuran foto DAN video, serta campuran status moderasi supaya setiap tab
// di halaman Explore Content Moderation ada isinya.
// 20 post: 10 VIDEO + 10 foto. Mayoritas berstatus "published" supaya feed
// Explore di APK benar-benar terisi; sisanya sengaja dibuat pending/rejected/
// hidden agar setiap tab moderasi di panel admin juga ada isinya.
const POSTS = [
  // ---------- 10 VIDEO ----------
  { creator: 'demo_c5', cat: 'Video', loc: 'Ubud, Bali', type: 'video', video: VIDEO.bunny, durasi: 596, caption: 'Drone footage sawah Ubud buat opening film wedding.', media: 'photo-1518548419970-58e3b4079ab2', status: 'published', metrics: { like: 2100, comment: 128, save: 560, share: 91, view: 34800 } },
  { creator: 'demo_c3', cat: 'Event', loc: 'Yogyakarta', type: 'video', video: VIDEO.joyride, durasi: 15, caption: 'Multi-cam coverage buat konser semalam. Tim solid!', media: 'photo-1511578314322-379afb476865', status: 'published', metrics: { like: 870, comment: 51, save: 120, share: 33, view: 12900 } },
  { creator: 'demo_c5', cat: 'Wedding', loc: 'Seminyak, Bali', type: 'video', video: VIDEO.sintel, durasi: 888, caption: 'Same-day-edit resepsi pantai. Diputar malam itu juga.', media: 'photo-1520854221256-17451cc331bf', status: 'published', metrics: { like: 3420, comment: 191, save: 780, share: 142, view: 61200 } },
  { creator: 'demo_c3', cat: 'Commercial', loc: 'Jakarta Selatan', type: 'video', video: VIDEO.gti, durasi: 30, caption: 'Video profil produk untuk klien otomotif.', media: 'photo-1503376780353-7e6692767b70', status: 'published', metrics: { like: 640, comment: 37, save: 210, share: 44, view: 15800 } },
  { creator: 'demo_c1', cat: 'Prewedding', loc: 'Bromo, Jawa Timur', type: 'video', video: VIDEO.steel, durasi: 734, caption: 'Prewedding sunrise di Bromo. Berangkat jam 2 pagi, terbayar.', media: 'photo-1464822759023-fed622ff2c3b', status: 'published', metrics: { like: 2870, comment: 164, save: 690, share: 118, view: 44100 } },
  { creator: 'demo_c2', cat: 'Wisuda', loc: 'Bandung', type: 'video', video: VIDEO.fun, durasi: 60, caption: 'Cuplikan hari wisuda — dari prosesi sampai lempar toga.', media: 'photo-1523050854058-8df90110c9f1', status: 'published', metrics: { like: 910, comment: 72, save: 165, share: 51, view: 19400 } },
  { creator: 'demo_c6', cat: 'Family', loc: 'Menteng, Jakarta', type: 'video', video: VIDEO.elephant, durasi: 653, caption: 'Video keluarga besar, tiga generasi dalam satu frame.', media: 'photo-1511895426328-dc8714191300', status: 'published', metrics: { like: 730, comment: 48, save: 132, share: 29, view: 11600 } },
  { creator: 'demo_c4', cat: 'Product', loc: 'Surabaya', type: 'video', video: VIDEO.blaze, durasi: 15, caption: 'Video produk 360 derajat untuk katalog marketplace.', media: 'photo-1526170375885-4d8ecf77b99f', status: 'published', metrics: { like: 480, comment: 26, save: 195, share: 18, view: 8900 } },
  // Dua video ini sengaja belum/tidak tayang -> mengisi tab moderasi panel
  { creator: 'demo_c3', cat: 'Video', loc: 'Semarang', type: 'video', video: VIDEO.escape, durasi: 15, caption: 'Teaser after-movie gathering perusahaan. Rilis minggu depan.', media: 'photo-1470229722913-7ea0d7ea3d0a', status: 'pending_review', metrics: { like: 12, comment: 1, save: 3, share: 0, view: 210 } },
  { creator: 'demo_c2', cat: 'Family', loc: 'Bandung', type: 'video', video: VIDEO.meltdown, durasi: 15, caption: 'Video lama, disembunyikan sementara atas permintaan klien.', media: 'photo-1476703993599-0035a21b17a9', status: 'hidden', metrics: { like: 88, comment: 4, save: 11, share: 2, view: 1300 } },

  // ---------- 10 FOTO ----------
  { creator: 'demo_c1', cat: 'Wedding', loc: 'Jakarta Selatan', type: 'photo', caption: 'Akad pagi di rumah, cahaya jendela terbaik tahun ini.', media: 'photo-1519741497674-611481863552', status: 'published', metrics: { like: 1240, comment: 86, save: 310, share: 44, view: 18400 } },
  { creator: 'demo_c5', cat: 'Prewedding', loc: 'Uluwatu, Bali', type: 'photo', caption: 'Golden hour di tebing Uluwatu. Sabar nunggu 40 menit, worth it.', media: 'photo-1537633552985-df8429e8048b', status: 'published', metrics: { like: 3180, comment: 204, save: 890, share: 156, view: 52300 } },
  { creator: 'demo_c2', cat: 'Wisuda', loc: 'Bandung', type: 'photo', caption: 'Selamat wisuda! Terima kasih sudah percaya sama kami.', media: 'photo-1529626455594-4ff0802cfb7e', status: 'published', metrics: { like: 640, comment: 38, save: 95, share: 20, view: 8700 } },
  { creator: 'demo_c4', cat: 'Product', loc: 'Surabaya', type: 'photo', caption: 'Setup satu lampu, background putih. Simpel tapi rapi.', media: 'photo-1441986300917-64674bd600d8', status: 'published', metrics: { like: 410, comment: 22, save: 180, share: 12, view: 6200 } },
  { creator: 'demo_c6', cat: 'Family', loc: 'Menteng, Jakarta', type: 'photo', caption: 'Sesi keluarga sore ini. Anak-anak paling seru difoto.', media: 'photo-1554048612-b6a482bc67e5', status: 'published', metrics: { like: 520, comment: 41, save: 88, share: 17, view: 7400 } },
  { creator: 'demo_c1', cat: 'Couple', loc: 'Kota Tua, Jakarta', type: 'photo', caption: 'Jalan-jalan sore sambil motret. Candid selalu menang.', media: 'photo-1522673607200-164d1b6ce486', status: 'published', metrics: { like: 980, comment: 63, save: 240, share: 38, view: 15100 } },
  { creator: 'demo_c3', cat: 'Event', loc: 'Yogyakarta', type: 'photo', caption: 'Panggung utama menjelang gelap. Lampu mulai hidup.', media: 'photo-1493225457124-a3eb161ffa5f', status: 'published', metrics: { like: 1150, comment: 59, save: 205, share: 40, view: 21700 } },
  { creator: 'demo_c6', cat: 'Commercial', loc: 'Jakarta Pusat', type: 'photo', caption: 'Headshot korporat batch pertama bulan ini.', media: 'photo-1560250097-0b93528c311a', status: 'published', metrics: { like: 395, comment: 18, save: 140, share: 11, view: 5800 } },
  { creator: 'demo_c5', cat: 'Wedding', loc: 'Nusa Dua, Bali', type: 'photo', caption: 'Ceremony tepi laut, angin kencang tapi hasilnya juara.', media: 'photo-1465495976277-4387d4b0b4c6', status: 'published', metrics: { like: 2450, comment: 137, save: 615, share: 96, view: 38900 } },
  { creator: 'demo_c4', cat: 'Product', loc: 'Surabaya', type: 'photo', caption: 'Repost hasil klien tanpa izin — contoh konten yang ditolak.', media: 'photo-1523275335684-37898b6baf30', status: 'rejected', metrics: { like: 2, comment: 0, save: 0, share: 0, view: 40 } },
];

// ------------------------------------------------------------ PORTFOLIO ----
const PORTFOLIOS = [
  { creator: 'demo_c1', title: 'Dinda & Reza — Intimate Wedding', cat: 'Wedding', media: ['photo-1519741497674-611481863552', 'photo-1465495976277-4387d4b0b4c6', 'photo-1511285560929-80b456fea0bc'] },
  { creator: 'demo_c2', title: 'Wisuda ITB 2025', cat: 'Wisuda', media: ['photo-1529626455594-4ff0802cfb7e', 'photo-1523050854058-8df90110c9f1'] },
  { creator: 'demo_c3', title: 'Soundrenaline Stage', cat: 'Event', media: ['photo-1511578314322-379afb476865', 'photo-1470229722913-7ea0d7ea3d0a'] },
  { creator: 'demo_c5', title: 'Bali Cliff Ceremony', cat: 'Wedding', media: ['photo-1537633552985-df8429e8048b', 'photo-1520854221256-17451cc331bf'] },
];

// -------------------------------------------------------------- BOOKING ----
// Tulang punggung data: pembayaran, escrow, dompet, refund, dan sengketa
// semuanya menunjuk booking di daftar ini.
// Nilai `status` WAJIB diambil dari object BookingStatus di BookingModel.kt
// pada APK: draft, pending_payment, paid, confirmed, upcoming, in_progress,
// completed, customer_confirmed, funds_released, reviewed, cancelled,
// refund_requested, disputed. Status karangan sendiri akan membuat APK
// menampilkan booking dengan label kosong atau melewatkannya dari hitungan.
const BOOKINGS = [
  { id: 'demo_bk_1', customer: 'demo_u1', creator: 'demo_c1', pkg: 'demo_pkg_1', paket: 'Prewedding Half Day', total: 3500000, status: 'reviewed', lokasi: 'Taman Menteng, Jakarta', jam: '06.00 - 10.00', hari: 62, acara: 55, note: 'Konsep earthy tone, bawa 2 baju ganti.' },
  { id: 'demo_bk_2', customer: 'demo_u2', creator: 'demo_c1', pkg: 'demo_pkg_2', paket: 'Wedding Full Day', total: 12000000, status: 'reviewed', lokasi: 'Gedung Serbaguna Kuningan', jam: '07.00 - 17.00', hari: 48, acara: 40, note: 'Akad pukul 08.00, resepsi 13.00.' },
  { id: 'demo_bk_3', customer: 'demo_u3', creator: 'demo_c2', pkg: 'demo_pkg_4', paket: 'Family Session', total: 1800000, status: 'funds_released', lokasi: 'Rumah klien, Dago', jam: '15.00 - 18.00', hari: 35, acara: 30, note: 'Keluarga 6 orang termasuk balita.' },
  { id: 'demo_bk_4', customer: 'demo_u4', creator: 'demo_c5', pkg: 'demo_pkg_7', paket: 'Bali Destination Wedding', total: 25000000, status: 'in_progress', lokasi: 'Uluwatu, Bali', jam: '10.00 - selesai', hari: 12, acara: 1, note: 'Hari kedua fokus sesi sunset.' },
  { id: 'demo_bk_5', customer: 'demo_u5', creator: 'demo_c3', pkg: 'demo_pkg_5', paket: 'Dokumentasi Event 1 Hari', total: 5000000, status: 'upcoming', lokasi: 'Auditorium Kampus, Yogyakarta', jam: '08.00 - 16.00', hari: 8, acara: -6, note: 'Butuh 2 fotografer stand-by di panggung.' },
  { id: 'demo_bk_6', customer: 'demo_u6', creator: 'demo_c4', pkg: 'demo_pkg_6', paket: 'Foto Produk 20 Item', total: 900000, status: 'pending_payment', lokasi: 'Studio Bimo, Surabaya', jam: '09.00 - 15.00', hari: 3, acara: -4, note: 'Produk dikirim H-1 lewat kurir.' },
  { id: 'demo_bk_7', customer: 'demo_u8', creator: 'demo_c6', pkg: 'demo_pkg_8', paket: 'Personal Branding Studio', total: 750000, status: 'pending_payment', lokasi: 'Studio Menteng', jam: '13.00 - 14.30', hari: 1, acara: -9, note: 'Untuk foto profil LinkedIn.' },
  { id: 'demo_bk_8', customer: 'demo_u7', creator: 'demo_c2', pkg: 'demo_pkg_3', paket: 'Wisuda Personal', total: 1200000, status: 'cancelled', lokasi: 'Kampus ITB', jam: '08.00 - 10.00', hari: 26, acara: 20, note: 'Dibatalkan pelanggan, jadwal bentrok.' },
  { id: 'demo_bk_9', customer: 'demo_u1', creator: 'demo_c5', pkg: 'demo_pkg_7', paket: 'Bali Destination Wedding', total: 25000000, status: 'disputed', lokasi: 'Seminyak, Bali', jam: '14.00 - 20.00', hari: 20, acara: 14, note: 'Hasil dianggap tidak sesuai brief.' },
  { id: 'demo_bk_10', customer: 'demo_u3', creator: 'demo_c3', pkg: 'demo_pkg_5', paket: 'Dokumentasi Event 1 Hari', total: 5000000, status: 'paid', lokasi: 'Hotel Tentrem, Yogyakarta', jam: '09.00 - 17.00', hari: 15, acara: 10, note: 'Gathering tahunan perusahaan.' },
];

// ------------------------------------------------------------- PAYMENTS ----
// Dua di antaranya berstatus awaiting_transfer -> mengisi halaman Verifikasi
// Transfer Manual sehingga tombol Konfirmasi/Tolak bisa dicoba sungguhan.
const PAYMENTS = [
  { id: 'demo_pay_1', booking: 'demo_bk_1', customer: 'demo_u1', provider: 'midtrans', amount: 3500000, status: 'paid', ext: 'MT-8891023', hari: 62 },
  { id: 'demo_pay_2', booking: 'demo_bk_2', customer: 'demo_u2', provider: 'midtrans', amount: 12000000, status: 'paid', ext: 'MT-8891044', hari: 48 },
  { id: 'demo_pay_3', booking: 'demo_bk_3', customer: 'demo_u3', provider: 'midtrans', amount: 1800000, status: 'paid', ext: 'MT-8891077', hari: 35 },
  { id: 'demo_pay_4', booking: 'demo_bk_4', customer: 'demo_u4', provider: 'midtrans', amount: 25000000, status: 'paid', ext: 'MT-8891102', hari: 12 },
  { id: 'demo_pay_5', booking: 'demo_bk_5', customer: 'demo_u5', provider: 'transfer_manual', amount: 5000000, status: 'awaiting_transfer', kode: 317, hari: 0.4 },
  { id: 'demo_pay_6', booking: 'demo_bk_6', customer: 'demo_u6', provider: 'transfer_manual', amount: 900000, status: 'awaiting_transfer', kode: 482, hari: 0.1 },
  { id: 'demo_pay_7', booking: 'demo_bk_8', customer: 'demo_u7', provider: 'midtrans', amount: 1200000, status: 'refunded', ext: 'MT-8891130', hari: 26 },
  { id: 'demo_pay_8', booking: 'demo_bk_9', customer: 'demo_u1', provider: 'midtrans', amount: 25000000, status: 'paid', ext: 'MT-8891166', hari: 20 },
  { id: 'demo_pay_9', booking: 'demo_bk_10', customer: 'demo_u3', provider: 'midtrans', amount: 5000000, status: 'paid', ext: 'MT-8891190', hari: 15 },
  { id: 'demo_pay_10', booking: 'demo_bk_7', customer: 'demo_u8', provider: 'midtrans', amount: 750000, status: 'failed', ext: 'MT-8891204', hari: 1 },
];

// Dana yang masih tertahan di escrow -> mengisi halaman Escrow / Held Funds.
const ESCROW = [
  { id: 'demo_esc_1', booking: 'demo_bk_4', creator: 'demo_c5', amount: 25000000, hold: 'funds_held', release: 'pending', ref: 'ESC-4410' },
  { id: 'demo_esc_2', booking: 'demo_bk_5', creator: 'demo_c3', amount: 5000000, hold: 'funds_held', release: 'pending', ref: 'ESC-4411' },
  { id: 'demo_esc_3', booking: 'demo_bk_9', creator: 'demo_c5', amount: 25000000, hold: 'funds_held', release: 'pending', ref: 'ESC-4412' },
  { id: 'demo_esc_4', booking: 'demo_bk_1', creator: 'demo_c1', amount: 3500000, hold: 'released', release: 'released', ref: 'ESC-4390' },
  { id: 'demo_esc_5', booking: 'demo_bk_2', creator: 'demo_c1', amount: 12000000, hold: 'released', release: 'released', ref: 'ESC-4391' },
];

// --------------------------------------------------------------- WALLET ----
// ID dokumen = ID creator, sesuai alamat wallets/{creatorId} yang dipakai
// panel dan portal creator.
const WALLETS = [
  { creator: 'demo_c1', pending: 0, available: 13950000, total: 15500000, withdrawn: 1550000 },
  { creator: 'demo_c2', pending: 0, available: 1620000, total: 1800000, withdrawn: 180000 },
  { creator: 'demo_c3', pending: 4500000, available: 4050000, total: 9000000, withdrawn: 450000 },
  { creator: 'demo_c4', pending: 810000, available: 0, total: 900000, withdrawn: 0 },
  { creator: 'demo_c5', pending: 45000000, available: 0, total: 50000000, withdrawn: 0 },
  { creator: 'demo_c6', pending: 0, available: 675000, total: 750000, withdrawn: 75000 },
];

const WALLET_TX = [
  { id: 'demo_wtx_1', creator: 'demo_c1', booking: 'demo_bk_1', type: 'credit', amount: 3150000, desc: 'Pelunasan booking Prewedding Half Day (dipotong fee 10%)', hari: 55 },
  { id: 'demo_wtx_2', creator: 'demo_c1', booking: 'demo_bk_2', type: 'credit', amount: 10800000, desc: 'Pelunasan booking Wedding Full Day (dipotong fee 10%)', hari: 40 },
  { id: 'demo_wtx_3', creator: 'demo_c1', booking: null, type: 'debit', amount: 1550000, desc: 'Penarikan ke BCA ****4471', hari: 18 },
  { id: 'demo_wtx_4', creator: 'demo_c2', booking: 'demo_bk_3', type: 'credit', amount: 1620000, desc: 'Pelunasan booking Family Session (dipotong fee 10%)', hari: 30 },
  { id: 'demo_wtx_5', creator: 'demo_c2', booking: null, type: 'debit', amount: 180000, desc: 'Penarikan ke Mandiri ****9982', hari: 9 },
  { id: 'demo_wtx_6', creator: 'demo_c3', booking: 'demo_bk_10', type: 'credit', amount: 4500000, desc: 'Pelunasan booking Dokumentasi Event (dipotong fee 10%)', hari: 10 },
  { id: 'demo_wtx_7', creator: 'demo_c3', booking: 'demo_bk_5', type: 'credit', amount: 4500000, desc: 'Dana tertahan escrow booking Dokumentasi Event', hari: 8 },
  { id: 'demo_wtx_8', creator: 'demo_c4', booking: 'demo_bk_6', type: 'credit', amount: 810000, desc: 'Dana tertahan escrow booking Foto Produk', hari: 3 },
  { id: 'demo_wtx_9', creator: 'demo_c5', booking: 'demo_bk_4', type: 'credit', amount: 22500000, desc: 'Dana tertahan escrow Bali Destination Wedding', hari: 12 },
  { id: 'demo_wtx_10', creator: 'demo_c6', booking: 'demo_bk_7', type: 'credit', amount: 675000, desc: 'Pelunasan booking Personal Branding Studio', hari: 1 },
];

// Dua penarikan berstatus "requested" -> tombol Approve/Reject di halaman
// Withdrawal Detail bisa dicoba; satu "processing" -> tombol tandai transfer
// manual juga bisa dicoba.
const WITHDRAWALS = [
  { id: 'demo_wd_1', creator: 'demo_c1', amount: 5000000, bank: 'BCA', rek: '4471002356', nama: 'Rangga Wicaksana', status: 'requested', hari: 2 },
  { id: 'demo_wd_2', creator: 'demo_c3', amount: 2000000, bank: 'BNI', rek: '0812349987', nama: 'Kalacitra Studio', status: 'requested', hari: 1 },
  { id: 'demo_wd_3', creator: 'demo_c2', amount: 1000000, bank: 'Mandiri', rek: '9982114500', nama: 'Anindya Pramesti', status: 'processing', hari: 4 },
  { id: 'demo_wd_4', creator: 'demo_c1', amount: 1550000, bank: 'BCA', rek: '4471002356', nama: 'Rangga Wicaksana', status: 'completed', ref: 'TRF-9982110', hari: 18 },
  { id: 'demo_wd_5', creator: 'demo_c6', amount: 500000, bank: 'BRI', rek: '3388120045', nama: 'Nadia Kusuma', status: 'failed', gagal: 'Nomor rekening tidak sesuai nama pemilik.', hari: 11 },
];

const REFUNDS = [
  { id: 'demo_rf_1', booking: 'demo_bk_8', customer: 'demo_u7', amount: 1200000, reason: 'Pembatalan oleh pelanggan H-6', status: 'completed', ref: 'RFD-77120' },
  { id: 'demo_rf_2', booking: 'demo_bk_9', customer: 'demo_u1', amount: 12500000, reason: 'Hasil tidak sesuai brief, refund sebagian', status: 'processing', ref: 'RFD-77133' },
  { id: 'demo_rf_3', booking: 'demo_bk_7', customer: 'demo_u8', amount: 750000, reason: 'Pembayaran gagal, dana kembali otomatis', status: 'requested', ref: 'RFD-77140' },
];

const DISPUTES = [
  { id: 'demo_dsp_1', booking: 'demo_bk_9', customer: 'demo_u1', creator: 'demo_c5', by: 'customer', reason: 'Jumlah foto yang diserahkan kurang dari yang dijanjikan.', status: 'open', hari: 6 },
  { id: 'demo_dsp_2', booking: 'demo_bk_4', customer: 'demo_u4', creator: 'demo_c5', by: 'customer', reason: 'Videografer datang terlambat 2 jam di hari pertama.', status: 'reviewing', hari: 3 },
  { id: 'demo_dsp_3', booking: 'demo_bk_5', customer: 'demo_u5', creator: 'demo_c3', by: 'creator', reason: 'Lokasi acara berubah mendadak tanpa pemberitahuan.', status: 'open', hari: 1 },
  { id: 'demo_dsp_4', booking: 'demo_bk_3', customer: 'demo_u3', creator: 'demo_c2', by: 'customer', reason: 'File mentah belum dikirim setelah 14 hari.', status: 'resolved', keputusan: 'release_full', hari: 22 },
];

const REPORTS = [
  { id: 'demo_rep_1', pelapor: 'demo_u2', tipe: 'explore_post', target: 'demo_post_11', reason: 'Konten diunggah tanpa izin pemilik foto', status: 'open', hari: 5 },
  { id: 'demo_rep_2', pelapor: 'demo_u4', tipe: 'review', target: 'demo_rev_3', reason: 'Ulasan mengandung kata kasar', status: 'open', hari: 3 },
  { id: 'demo_rep_3', pelapor: 'demo_u5', tipe: 'creator', target: 'demo_c4', reason: 'Creator tidak merespons chat lebih dari 7 hari', status: 'resolved', hari: 14 },
  { id: 'demo_rep_4', pelapor: 'demo_u8', tipe: 'user', target: 'demo_u7', reason: 'Akun diduga melakukan spam booking', status: 'dismissed', hari: 20 },
];

const REVIEWS = [
  { id: 'demo_rev_1', booking: 'demo_bk_1', customer: 'demo_u1', nama: 'Putri Ayuningtyas', creator: 'demo_c1', rating: 5, text: 'Hasilnya jauh di atas ekspektasi. Komunikatif dari awal sampai file diserahkan.', status: 'published', hari: 54 },
  { id: 'demo_rev_2', booking: 'demo_bk_2', customer: 'demo_u2', nama: 'Ahmad Fauzi', creator: 'demo_c1', rating: 5, text: 'Tepat waktu, arahan posenya enak diikuti. Recommended banget.', status: 'published', hari: 39 },
  { id: 'demo_rev_3', booking: 'demo_bk_3', customer: 'demo_u3', nama: 'Rina Marlina', creator: 'demo_c2', rating: 5, text: 'Anak saya yang pemalu jadi mau difoto. Sabar dan ramah.', status: 'published', hari: 29 },
  { id: 'demo_rev_4', booking: 'demo_bk_10', customer: 'demo_u3', nama: 'Panitia HMTI', creator: 'demo_c3', rating: 4, text: 'Liputan lengkap, after-movie-nya keren. Pengiriman file agak lama sedikit.', status: 'published', hari: 9 },
  { id: 'demo_rev_5', booking: 'demo_bk_4', customer: 'demo_u4', nama: 'Michelle Tanuwijaya', creator: 'demo_c5', rating: 5, text: 'Worth every rupiah. Drone shot-nya bikin video kami terasa sinematik.', status: 'published', hari: 6 },
  { id: 'demo_rev_6', booking: 'demo_bk_6', customer: 'demo_u6', nama: 'Toko Rasa Nusantara', creator: 'demo_c4', rating: 5, text: 'Foto produk kami naik kelas. Penjualan ikut naik.', status: 'published', hari: 2 },
  { id: 'demo_rev_7', booking: 'demo_bk_7', customer: 'demo_u8', nama: 'Sinta Dewanti', creator: 'demo_c6', rating: 3, text: 'Hasil oke tapi sesi terasa terburu-buru, kurang eksplorasi pose.', status: 'published', hari: 1 },
  { id: 'demo_rev_8', booking: 'demo_bk_8', customer: 'demo_u7', nama: 'Bagas Prakoso', creator: 'demo_c2', rating: 1, text: 'Ulasan bermuatan kasar — contoh konten yang perlu disembunyikan moderator.', status: 'hidden', hari: 24 },
];

const NOTIFICATIONS = [
  { id: 'demo_ntf_1', user: 'demo_u1', type: 'booking', title: 'Booking selesai', body: 'Sesi Prewedding Half Day bersama Rangga Wicaksana telah selesai. Beri ulasan yuk!', read: true, hari: 54 },
  { id: 'demo_ntf_2', user: 'demo_u4', type: 'payment', title: 'Pembayaran diterima', body: 'Pembayaran Rp 25.000.000 untuk Bali Destination Wedding sudah kami terima.', read: true, hari: 12 },
  { id: 'demo_ntf_3', user: 'demo_c1', type: 'withdrawal', title: 'Penarikan diproses', body: 'Penarikan Rp 1.550.000 ke BCA ****4471 telah berhasil ditransfer.', read: true, hari: 18 },
  { id: 'demo_ntf_4', user: 'demo_u5', type: 'payment', title: 'Menunggu transfer', body: 'Selesaikan transfer Rp 5.000.317 sebelum 24 jam agar booking tidak dibatalkan.', read: false, hari: 0.4 },
  { id: 'demo_ntf_5', user: 'demo_c5', type: 'dispute', title: 'Sengketa baru', body: 'Pelanggan membuka sengketa pada booking Seminyak. Mohon tanggapi dalam 2x24 jam.', read: false, hari: 6 },
  { id: 'demo_ntf_6', user: 'demo_c3', type: 'booking', title: 'Booking baru dikonfirmasi', body: 'Dokumentasi Event 1 Hari di Yogyakarta telah dikonfirmasi pelanggan.', read: false, hari: 8 },
  { id: 'demo_ntf_7', user: 'demo_u8', type: 'payment', title: 'Pembayaran gagal', body: 'Pembayaran Rp 750.000 gagal diproses. Silakan ulangi dengan metode lain.', read: false, hari: 1 },
  { id: 'demo_ntf_8', user: 'demo_c4', type: 'verification', title: 'Verifikasi sedang ditinjau', body: 'Dokumen verifikasi Anda sedang ditinjau tim kami. Estimasi 2x24 jam.', read: false, hari: 4 },
];

const PROMOTIONS = [
  { id: 'demo_promo_1', code: 'JEPRETBARU', type: 'percentage', value: 15, maxDiscount: 250000, minOrder: 1000000, usageLimit: 500, usageCount: 137, active: true, mulai: 30, selesai: 60 },
  { id: 'demo_promo_2', code: 'WEDDING2JT', type: 'fixed', value: 2000000, maxDiscount: null, minOrder: 10000000, usageLimit: 100, usageCount: 12, active: true, mulai: 14, selesai: 45 },
  { id: 'demo_promo_3', code: 'WISUDAHEMAT', type: 'percentage', value: 20, maxDiscount: 150000, minOrder: 500000, usageLimit: 300, usageCount: 300, active: false, mulai: 120, selesai: -30 },
  { id: 'demo_promo_4', code: 'UMKMPRODUK', type: 'percentage', value: 10, maxDiscount: 100000, minOrder: 500000, usageLimit: null, usageCount: 48, active: true, mulai: 7, selesai: 90 },
];

// Komentar pada post Explore. Nama & foto penulis ikut disimpan di dokumen
// (denormalisasi) mengikuti pola yang sudah dipakai APK — koleksi `users`
// hanya boleh dibaca pemiliknya, jadi layar komentar tidak bisa menerjemahkan
// userId menjadi nama lewat pembacaan users.
const KOMENTAR = [
  { id: 'demo_cmt_1', post: 'demo_post_1', user: 'demo_u1', nama: 'Putri Ayuningtyas', teks: 'Warnanya hangat banget, suka!', suka: 12, jam: 20 },
  { id: 'demo_cmt_2', post: 'demo_post_1', user: 'demo_u2', nama: 'Ahmad Fauzi', teks: 'Ini pakai drone apa ya kak?', suka: 4, jam: 16 },
  { id: 'demo_cmt_3', post: 'demo_post_1', user: 'demo_c5', nama: 'Lentera Visual', teks: 'Mavic 3, terbang pagi biar kabutnya dapat.', suka: 8, jam: 14, induk: 'demo_cmt_2' },
  { id: 'demo_cmt_4', post: 'demo_post_2', user: 'demo_u3', nama: 'Rina Marlina', teks: 'Editingnya rapi, tone-nya konsisten.', suka: 6, jam: 30 },
  { id: 'demo_cmt_5', post: 'demo_post_3', user: 'demo_u4', nama: 'Michelle Tanuwijaya', teks: 'Same-day-edit-nya bikin tamu terharu semua.', suka: 21, jam: 26 },
  { id: 'demo_cmt_6', post: 'demo_post_11', user: 'demo_u5', nama: 'Panitia HMTI', teks: 'Boleh minta kontaknya untuk acara kampus?', suka: 3, jam: 8 },
  { id: 'demo_cmt_7', post: 'demo_post_12', user: 'demo_u8', nama: 'Sinta Dewanti', teks: 'Lokasinya di mana kak? Bagus banget.', suka: 5, jam: 5 },
];

// Like, simpan, dan follow milik demo_u1 — mengisi layar "Tersimpan" dan tab
// "Following" di APK.
const LIKE = ['demo_post_1', 'demo_post_3', 'demo_post_11', 'demo_post_15'];
const SIMPAN = ['demo_post_1', 'demo_post_5', 'demo_post_12', 'demo_post_19'];
const FOLLOW = ['demo_c1', 'demo_c3', 'demo_c5'];

// Percakapan antara pelanggan dan creator. Pesan disimpan sebagai koleksi
// tingkat atas dengan field `chatId` dan array `participants`, persis bentuk
// yang dibaca ChatRepository dan dijaga firestore.rules.
const CHAT = [
  {
    id: 'demo_chat_1', customer: 'demo_u1', creator: 'demo_c1', jam: 3,
    terakhir: 'Baik kak, saya kirim draft konsepnya nanti malam ya.',
    pesan: [
      { id: 'demo_msg_1', dari: 'demo_u1', teks: 'Halo kak, untuk paket prewedding half day masih tersedia tanggal 20?', jam: 9 },
      { id: 'demo_msg_2', dari: 'demo_c1', teks: 'Halo! Tanggal 20 masih kosong kak, jam berapa rencananya?', jam: 8 },
      { id: 'demo_msg_3', dari: 'demo_u1', teks: 'Pagi saja kak, sekalian golden hour.', jam: 6 },
      { id: 'demo_msg_4', dari: 'demo_c1', teks: 'Baik kak, saya kirim draft konsepnya nanti malam ya.', jam: 3 },
    ],
  },
  {
    id: 'demo_chat_2', customer: 'demo_u5', creator: 'demo_c3', jam: 1,
    terakhir: 'Sudah saya catat, tim kami datang 1 jam sebelum acara.',
    pesan: [
      { id: 'demo_msg_5', dari: 'demo_u5', teks: 'Kak, acara kami mulai jam 8 pagi. Tim bisa datang lebih awal?', jam: 4 },
      { id: 'demo_msg_6', dari: 'demo_c3', teks: 'Sudah saya catat, tim kami datang 1 jam sebelum acara.', jam: 1 },
    ],
  },
];

// Tanggal yang ditutup creator — dipakai layar booking untuk menandai tanggal
// yang tidak tersedia.
const TANGGAL_TUTUP = [
  { id: 'demo_blk_1', creator: 'demo_c1', hari: -5, alasan: 'Sudah ada booking lain' },
  { id: 'demo_blk_2', creator: 'demo_c1', hari: -6, alasan: 'Libur keluarga' },
  { id: 'demo_blk_3', creator: 'demo_c5', hari: -12, alasan: 'Destination wedding Bali' },
  { id: 'demo_blk_4', creator: 'demo_c3', hari: -3, alasan: 'Maintenance alat' },
];

const KATEGORI = ['Wedding', 'Prewedding', 'Event', 'Wisuda', 'Couple', 'Family', 'Product', 'Commercial', 'Video'];

// Semua koleksi yang disentuh script ini — dipakai seed() maupun hapus()
// supaya keduanya tidak pernah kehilangan sinkron.
const KOLEKSI = [
  'users', 'creators', 'creator_verifications', 'packages', 'portfolios',
  'explore_posts', 'explore_comments', 'explore_likes', 'explore_saves',
  'follows', 'reviews', 'categories', 'bookings', 'payments',
  'escrow_transactions', 'wallets', 'wallet_transactions', 'withdrawals',
  'refunds', 'disputes', 'reports', 'notifications', 'promotions',
  'chats', 'messages', 'availability_blocks',
];

/**
 * Buat/segarkan akun Firebase Auth untuk setiap akun demo.
 *
 * `uid` DIPAKSA sama dengan id dokumen (demo_c1, demo_u1, ...). Ini syarat
 * mutlak: AuthContext di panel dan repository di APK mencari dokumen
 * creators/{uid} maupun users/{uid} memakai uid dari token, jadi kalau uid
 * Auth berbeda dari id dokumen, akunnya masuk tapi dianggap "tidak punya
 * akses".
 *
 * Akun yang sudah ada diperbarui (bukan dilewati) karena uji coba dengan
 * custom token bisa meninggalkan akun ber-uid sama TANPA email/sandi —
 * akun seperti itu tidak akan pernah bisa dipakai login biasa.
 */
async function pastikanAkunLogin() {
  let dibuat = 0, diperbarui = 0;
  for (const u of USERS) {
    const data = {
      email: u.email,
      emailVerified: true,
      password: SANDI_DEMO,
      displayName: u.name,
      disabled: u.status === 'suspended',
    };
    try {
      await auth.getUser(u.id);
      await auth.updateUser(u.id, data);
      diperbarui++;
    } catch (err) {
      if (err && err.code === 'auth/user-not-found') {
        await auth.createUser({ uid: u.id, ...data });
        dibuat++;
      } else {
        throw err;
      }
    }
  }
  return { dibuat, diperbarui };
}

async function seed() {
  const batch = db.batch();
  const set = (col, id, data) => batch.set(db.collection(col).doc(id), data);

  for (const u of USERS) {
    set('users', u.id, {
      name: u.name, email: u.email, phone: u.phone, role: u.role,
      status: u.status, photoUrl: null, createdAt: hariLalu(u.hari),
    });
  }

  for (const c of CREATORS) {
    set('creators', c.id, {
      userId: c.id, displayName: c.displayName, bio: c.bio, city: c.city,
      categories: c.categories, rating: c.rating, reviewCount: c.reviewCount,
      followerCount: c.followerCount, verified: c.verified,
      status: 'active',                       // wajib: query menyaring status
      photoUrl: img(c.photo, 400), coverUrl: img(c.cover),
      equipment: c.equipment, socialLinks: [],
      serviceLat: c.lat, serviceLng: c.lng, minPrice: c.minPrice,
    });
  }

  for (const v of VERIFICATIONS) {
    set('creator_verifications', v.id, {
      creatorId: v.creatorId,
      documents: v.docs.map((d) => img(d, 600)),
      status: 'pending', submittedAt: hariLalu(v.hari), reviewedAt: null,
    });
  }

  PACKAGES.forEach((p, i) => {
    set('packages', `demo_pkg_${i + 1}`, {
      creatorId: p.creator, name: p.name, price: p.price, duration: p.duration,
      personnel: p.personnel, output: p.output, description: p.desc, active: true,
    });
  });

  POSTS.forEach((p, i) => {
    const c = CREATORS.find((x) => x.id === p.creator);
    set('explore_posts', `demo_post_${i + 1}`, {
      creatorId: p.creator, creatorName: c.displayName,
      creatorPhotoUrl: img(c.photo, 200), creatorVerified: c.verified,
      type: p.type,
      // Untuk video, mediaUrls berisi berkas .mp4 sedangkan thumbnailUrl
      // tetap gambar — panel merender pratinjau lewat <img>.
      mediaUrls: p.type === 'video' ? [p.video] : [img(p.media, 1200)],
      thumbnailUrl: img(p.media, 600),
      caption: p.caption, category: p.cat, location: p.loc,
      metrics: p.metrics,
      status: p.status,                       // wajib: query menyaring status
      moderationStatus: p.status,
      // createdAt dipakai orderBy — dibuat berjenjang agar urutannya masuk akal
      createdAt: jamLalu(i * 7),
    });
  });

  PORTFOLIOS.forEach((p, i) => {
    set('portfolios', `demo_pf_${i + 1}`, {
      creatorId: p.creator, title: p.title, category: p.cat,
      media: p.media.map((m) => img(m, 900)), status: 'active',
    });
  });

  for (const b of BOOKINGS) {
    const u = USERS.find((x) => x.id === b.customer);
    const feePersen = 10;
    const fee = Math.round((b.total * feePersen) / 100);
    set('bookings', b.id, {
      customerId: b.customer, customerName: u.name, creatorId: b.creator,
      packageId: b.pkg, packageName: b.paket,
      location: b.lokasi, time: b.jam, note: b.note,
      total: b.total, status: b.status,
      // Nama field mengikuti BookingModel.kt di APK: tanggal acara disimpan
      // sebagai `date`, BUKAN `eventDate`. Memakai nama lain membuat APK
      // membaca null dan menampilkan tanggal kosong.
      date: hariLalu(b.acara),
      priceBreakdown: {
        packagePrice: b.total, addOnsTotal: 0, travelFee: 0, discount: 0,
        voucherCode: null, platformFeePercent: feePersen, platformFee: fee,
        subtotal: b.total,
      },
      customerConfirmedAt: ['customer_confirmed', 'funds_released', 'reviewed'].includes(b.status)
        ? hariLalu(b.acara - 2) : null,
      fundsReleasedAt: ['funds_released', 'reviewed'].includes(b.status)
        ? hariLalu(b.acara - 3) : null,
      createdAt: hariLalu(b.hari),
    });
  }

  for (const p of PAYMENTS) {
    set('payments', p.id, {
      bookingId: p.booking, customerId: p.customer, provider: p.provider,
      externalId: p.ext || null, amount: p.amount, status: p.status,
      // Transfer manual dibedakan dengan kode unik: nominal yang harus
      // dicocokkan admin adalah transferAmount, bukan amount.
      transferAmount: p.kode ? p.amount + p.kode : p.amount,
      uniqueCode: p.kode || null,
      createdAt: hariLalu(p.hari),
    });
  }

  for (const e of ESCROW) {
    set('escrow_transactions', e.id, {
      bookingId: e.booking, creatorId: e.creator, amount: e.amount,
      holdStatus: e.hold, releaseStatus: e.release, providerReference: e.ref,
      createdAt: hariLalu(20),
    });
  }

  for (const w of WALLETS) {
    set('wallets', w.creator, {
      creatorId: w.creator, pendingBalance: w.pending, availableBalance: w.available,
      totalEarnings: w.total, withdrawn: w.withdrawn, updatedAt: hariLalu(1),
    });
  }

  for (const t of WALLET_TX) {
    set('wallet_transactions', t.id, {
      creatorId: t.creator, bookingId: t.booking, type: t.type,
      amount: t.amount, description: t.desc, createdAt: hariLalu(t.hari),
    });
  }

  for (const w of WITHDRAWALS) {
    set('withdrawals', w.id, {
      creatorId: w.creator, amount: w.amount, bankCode: w.bank,
      bankAccountNumber: w.rek, bankAccountName: w.nama, status: w.status,
      bankReference: w.ref || null, failureReason: w.gagal || null,
      createdAt: hariLalu(w.hari),
    });
  }

  for (const r of REFUNDS) {
    set('refunds', r.id, {
      bookingId: r.booking, customerId: r.customer, amount: r.amount,
      reason: r.reason, status: r.status, providerReference: r.ref,
      createdAt: hariLalu(10),
    });
  }

  for (const d of DISPUTES) {
    set('disputes', d.id, {
      bookingId: d.booking, customerId: d.customer, creatorId: d.creator,
      openedBy: d.by, reason: d.reason, status: d.status,
      decision: d.keputusan || null, createdAt: hariLalu(d.hari),
    });
  }

  for (const r of REPORTS) {
    set('reports', r.id, {
      reporterId: r.pelapor, targetType: r.tipe, targetId: r.target,
      reason: r.reason, status: r.status, createdAt: hariLalu(r.hari),
    });
  }

  for (const r of REVIEWS) {
    set('reviews', r.id, {
      bookingId: r.booking, customerId: r.customer, customerName: r.nama,
      creatorId: r.creator, rating: r.rating, text: r.text,
      status: r.status, creatorReply: null, createdAt: hariLalu(r.hari),
    });
  }

  for (const n of NOTIFICATIONS) {
    set('notifications', n.id, {
      userId: n.user, type: n.type, title: n.title, body: n.body,
      isRead: n.read, readAt: n.read ? hariLalu(n.hari) : null,
      createdAt: hariLalu(n.hari),
    });
  }

  for (const p of PROMOTIONS) {
    set('promotions', p.id, {
      code: p.code, type: p.type, value: p.value, maxDiscount: p.maxDiscount,
      minOrder: p.minOrder, usageLimit: p.usageLimit, usageCount: p.usageCount,
      active: p.active, startAt: hariLalu(p.mulai), endAt: hariDepan(p.selesai),
      createdAt: hariLalu(p.mulai),
    });
  }

  for (const k of KOMENTAR) {
    const penulis = USERS.find((u) => u.id === k.user);
    set('explore_comments', k.id, {
      postId: k.post, userId: k.user, text: k.teks,
      parentId: k.induk || null, likeCount: k.suka,
      authorName: k.nama,
      authorPhotoUrl: penulis && penulis.role === 'creator'
        ? img(CREATORS.find((c) => c.id === k.user)?.photo || 'photo-1507003211169-0a1dd7228f2d', 200)
        : null,
      status: 'published', createdAt: jamLalu(k.jam),
    });
  }

  // ID dokumen mengikuti pola "{postId}_{userId}" / "{creatorId}_{userId}"
  // persis seperti yang dipakai ExploreRepository di APK.
  for (const postId of LIKE) {
    set('explore_likes', `${postId}_demo_u1`, { postId, userId: 'demo_u1', createdAt: hariLalu(2) });
  }
  for (const postId of SIMPAN) {
    set('explore_saves', `${postId}_demo_u1`, { postId, userId: 'demo_u1', createdAt: hariLalu(3) });
  }
  for (const creatorId of FOLLOW) {
    set('follows', `${creatorId}_demo_u1`, { creatorId, userId: 'demo_u1', createdAt: hariLalu(5) });
  }

  for (const c of CHAT) {
    set('chats', c.id, {
      customerId: c.customer, creatorId: c.creator,
      lastMessage: c.terakhir, updatedAt: jamLalu(c.jam), createdAt: hariLalu(2),
    });
    for (const m of c.pesan) {
      set('messages', m.id, {
        chatId: c.id, senderId: m.dari, text: m.teks,
        // participants ikut ditulis di setiap pesan karena aturan Firestore
        // memeriksa keanggotaan dari sini, bukan lewat get() ke dokumen chat.
        participants: [c.customer, c.creator],
        readAt: null, createdAt: jamLalu(m.jam),
      });
    }
  }

  for (const b of TANGGAL_TUTUP) {
    set('availability_blocks', b.id, {
      creatorId: b.creator, date: hariLalu(b.hari), reason: b.alasan, createdAt: hariLalu(7),
    });
  }

  // `order` wajib diisi: kategori tanpa field ini tidak ikut terurut rapi
  // di halaman Categories.
  KATEGORI.forEach((cat, i) => {
    set('categories', `demo_cat_${cat.toLowerCase()}`, { name: cat, active: true, order: i });
  });

  await batch.commit();
  const akun = await pastikanAkunLogin();

  const jumlahVideo = POSTS.filter((p) => p.type === 'video').length;
  console.log('Selesai. Data contoh berhasil dibuat:');
  console.log(`  ${USERS.length} user, ${CREATORS.length} creator, ${VERIFICATIONS.length} pengajuan verifikasi`);
  console.log(`  ${PACKAGES.length} paket, ${PORTFOLIOS.length} portfolio, ${KATEGORI.length} kategori`);
  console.log(`  ${POSTS.length} post Explore (${POSTS.length - jumlahVideo} foto + ${jumlahVideo} video), ${REVIEWS.length} ulasan`);
  console.log(`  ${BOOKINGS.length} booking, ${PAYMENTS.length} pembayaran, ${ESCROW.length} escrow`);
  console.log(`  ${WALLETS.length} dompet, ${WALLET_TX.length} mutasi saldo, ${WITHDRAWALS.length} penarikan`);
  console.log(`  ${REFUNDS.length} refund, ${DISPUTES.length} sengketa, ${REPORTS.length} laporan`);
  console.log(`  ${NOTIFICATIONS.length} notifikasi, ${PROMOTIONS.length} promo`);
  console.log(`  ${KOMENTAR.length} komentar, ${LIKE.length} like, ${SIMPAN.length} simpanan, ${FOLLOW.length} follow`);
  console.log(`  ${CHAT.length} percakapan (${CHAT.reduce((n, c) => n + c.pesan.length, 0)} pesan), ${TANGGAL_TUTUP.length} tanggal ditutup`);
  console.log(`  ${akun.dibuat} akun login dibuat, ${akun.diperbarui} disegarkan`);

  console.log('\n--- Akun demo yang bisa dipakai login (sandi sama semua) ---');
  console.log(`Kata sandi: ${SANDI_DEMO}\n`);
  console.log('  CREATOR (portal creator di web & aplikasi):');
  USERS.filter((u) => u.role === 'creator').forEach((u) => console.log(`    ${u.email.padEnd(34)} ${u.name}`));
  console.log('\n  PELANGGAN (aplikasi Android):');
  USERS.filter((u) => u.role === 'customer').forEach((u) =>
    console.log(`    ${u.email.padEnd(34)} ${u.name}${u.status === 'suspended' ? '  [sengaja dinonaktifkan]' : ''}`)
  );

  console.log('\nSemuanya bisa diedit dan dihapus lewat panel admin.');
  console.log('Untuk menghapus seluruh data contoh: npm run seed:hapus');
}

async function hapus() {
  let total = 0;
  for (const col of KOLEKSI) {
    const snap = await db.collection(col).get();
    const docs = snap.docs.filter((d) => d.id.startsWith('demo_'));
    // Batch Firestore dibatasi 500 operasi, jadi dipotong per 400 dokumen.
    for (let i = 0; i < docs.length; i += 400) {
      const batch = db.batch();
      docs.slice(i, i + 400).forEach((d) => batch.delete(d.ref));
      await batch.commit();
    }
    if (docs.length) console.log(`  ${col}: ${docs.length} dokumen contoh dihapus`);
    total += docs.length;
  }
  // Akun Firebase Auth ikut dibuang. Tanpa ini, "hapus data contoh" menyisakan
  // akun yang masih bisa login dengan sandi demo yang tertulis terbuka di
  // repositori ini — celah yang justru berbahaya di project produksi.
  let akunDihapus = 0;
  for (const u of USERS) {
    try {
      await auth.deleteUser(u.id);
      akunDihapus++;
    } catch (err) {
      if (!err || err.code !== 'auth/user-not-found') throw err;
    }
  }
  if (akunDihapus) console.log(`  akun login: ${akunDihapus} akun demo dihapus`);

  console.log(`\nTotal ${total} dokumen contoh dihapus. Data asli tidak tersentuh.`);
}

const mode = process.argv.includes('--hapus') ? hapus : seed;
mode()
  .then(() => process.exit(0))
  .catch((e) => { console.error('Gagal:', e.message); process.exit(1); });

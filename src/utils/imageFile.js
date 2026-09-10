/**
 * Menyiapkan berkas gambar dari PC/HP sebelum diunggah.
 *
 * Kenapa dikompres di browser dan bukan dikirim apa adanya? Foto dari kamera
 * ponsel sekarang lazim berukuran 4–12 MB dengan sisi 4000 piksel lebih.
 * Mengirimnya apa adanya membuat unggahan lewat data seluler memakan waktu
 * lama dan mudah putus di tengah jalan, sementara yang benar-benar ditampilkan
 * aplikasi hanyalah gambar selebar layar ponsel. Menyusutkannya lebih dulu
 * mengubah unggahan belasan megabita menjadi ratusan kilobita tanpa perbedaan
 * yang terlihat di layar.
 *
 * Semua pekerjaan di sini terjadi di browser — tidak ada berkas yang dikirim
 * ke mana pun oleh modul ini.
 */

/** Berkas mentah yang lebih besar dari ini ditolak sebelum dibaca; membaca
 *  berkas 100 MB ke memori browser ponsel bisa membuat tabnya mati. */
export const UKURAN_BERKAS_MAKS = 15 * 1024 * 1024;
export const UKURAN_VIDEO_MAKS = 100 * 1024 * 1024;

/**
 * Format yang pasti bisa digambar ulang ke canvas oleh semua browser.
 *
 * HEIC/HEIF — format bawaan iPhone — sengaja TIDAK ada di daftar ini. Di luar
 * Safari, browser tidak bisa membacanya sama sekali, dan kegagalannya muncul
 * sebagai gambar kosong tanpa penjelasan. Lebih baik ditolak lebih awal dengan
 * pesan yang menyebutkan cara memperbaikinya.
 */
const TIPE_DIDUKUNG = ['image/jpeg', 'image/png', 'image/webp', 'image/gif', 'image/bmp'];

/** @returns {string|null} pesan kesalahan, atau null kalau berkasnya layak. */
export function periksaBerkasGambar(file) {
  if (!file) return 'Berkas tidak terbaca.';
  if (!file.type.startsWith('image/')) {
    return `"${file.name}" bukan berkas gambar.`;
  }
  if (!TIPE_DIDUKUNG.includes(file.type)) {
    const nama = file.type.replace('image/', '').toUpperCase();
    return `Format ${nama} belum didukung browser. Di iPhone, ubah Pengaturan > Kamera > Format menjadi "Paling Kompatibel", atau kirim ulang fotonya sebagai JPG.`;
  }
  if (file.size > UKURAN_BERKAS_MAKS) {
    return `"${file.name}" berukuran ${formatUkuran(file.size)} — maksimal ${formatUkuran(UKURAN_BERKAS_MAKS)}.`;
  }
  return null;
}

export function periksaBerkasMedia(file, izinkanVideo = false) {
  if (!file) return 'Berkas tidak terbaca.';
  if (file.type.startsWith('video/')) {
    if (!izinkanVideo) return `"${file.name}" adalah video, sedangkan kolom ini hanya menerima foto.`;
    if (!['video/mp4', 'video/webm', 'video/quicktime'].includes(file.type)) {
      return `Format video ${file.type.replace('video/', '').toUpperCase()} belum didukung. Gunakan MP4, WebM, atau MOV.`;
    }
    if (file.size > UKURAN_VIDEO_MAKS) {
      return `"${file.name}" berukuran ${formatUkuran(file.size)} — maksimal ${formatUkuran(UKURAN_VIDEO_MAKS)}.`;
    }
    return null;
  }
  return periksaBerkasGambar(file);
}

export function formatUkuran(byte) {
  if (byte >= 1024 * 1024) return `${(byte / (1024 * 1024)).toFixed(1)} MB`;
  if (byte >= 1024) return `${Math.round(byte / 1024)} KB`;
  return `${byte} B`;
}

/**
 * Membaca berkas menjadi gambar yang siap digambar ke canvas.
 *
 * `imageOrientation: 'from-image'` penting: tanpa itu foto potret dari ponsel
 * ikut terunggah dalam posisi miring, karena arah aslinya hanya tercatat di
 * metadata EXIF dan hilang begitu gambar disalin ke canvas.
 */
async function muatGambar(file) {
  if (typeof createImageBitmap === 'function') {
    try {
      return await createImageBitmap(file, { imageOrientation: 'from-image' });
    } catch {
      // Sebagian browser lama tidak menerima opsi tersebut; jatuh ke cara di bawah.
    }
  }
  const url = URL.createObjectURL(file);
  try {
    return await new Promise((resolve, reject) => {
      const img = new Image();
      img.onload = () => resolve(img);
      img.onerror = () => reject(new Error('Gambar tidak bisa dibaca. Coba berkas lain.'));
      img.src = url;
    });
  } finally {
    // Ditunda satu putaran supaya <img> sempat selesai memakai URL-nya.
    setTimeout(() => URL.revokeObjectURL(url), 0);
  }
}

function keBlob(canvas, mime, mutu) {
  return new Promise((resolve, reject) => {
    canvas.toBlob(
      (blob) => (blob ? resolve(blob) : reject(new Error('Gagal mengubah gambar.'))),
      mime,
      mutu
    );
  });
}

/**
 * Menyusutkan gambar sampai sisi terpanjangnya <= `sisiMaks`, lalu menurunkan
 * mutu JPEG bertahap sampai ukurannya <= `targetByte`.
 *
 * Mutu diturunkan bertahap, bukan langsung ke angka terendah: sebagian besar
 * foto sudah lolos di langkah pertama, dan memaksa semuanya ke mutu terendah
 * berarti merusak gambar yang sebenarnya tidak perlu dirusak. Kalau sampai
 * langkah terakhir masih terlalu besar, hasil terakhir tetap dikembalikan —
 * pemanggilnya yang memutuskan apakah itu masih bisa dipakai.
 *
 * @returns {Promise<{blob: Blob, lebar: number, tinggi: number}>}
 */
export async function kompresGambar(file, { sisiMaks = 1920, targetByte = 400 * 1024, rasio = 16 / 9 } = {}) {
  const sumber = await muatGambar(file);
  const lebarAsli = sumber.width;
  const tinggiAsli = sumber.height;
  if (!lebarAsli || !tinggiAsli) throw new Error('Ukuran gambar tidak terbaca.');

  const rasioAsli = lebarAsli / tinggiAsli;
  const lebarCrop = rasioAsli > rasio ? Math.round(tinggiAsli * rasio) : lebarAsli;
  const tinggiCrop = rasioAsli > rasio ? tinggiAsli : Math.round(lebarAsli / rasio);
  const xCrop = Math.max(0, Math.round((lebarAsli - lebarCrop) / 2));
  const yCrop = Math.max(0, Math.round((tinggiAsli - tinggiCrop) / 2));
  const skala = Math.min(1, sisiMaks / lebarCrop);
  const lebar = Math.max(1, Math.round(lebarCrop * skala));
  const tinggi = Math.max(1, Math.round(tinggiCrop * skala));

  const canvas = document.createElement('canvas');
  canvas.width = lebar;
  canvas.height = tinggi;
  const ctx = canvas.getContext('2d');
  // PNG dan GIF bisa punya bagian transparan. Tanpa dasar putih, bagian itu
  // menjadi HITAM begitu disimpan sebagai JPEG — perubahan yang tampak seperti
  // gambarnya rusak, bukan seperti hasil kompresi.
  ctx.fillStyle = '#FFFFFF';
  ctx.fillRect(0, 0, lebar, tinggi);
  ctx.drawImage(sumber, xCrop, yCrop, lebarCrop, tinggiCrop, 0, 0, lebar, tinggi);
  if (typeof sumber.close === 'function') sumber.close();

  let blob = null;
  for (const mutu of [0.86, 0.78, 0.7, 0.6, 0.5, 0.4]) {
    blob = await keBlob(canvas, 'image/jpeg', mutu);
    if (blob.size <= targetByte) break;
  }
  return { blob, lebar, tinggi };
}

/** Blob -> string data URL, untuk gambar yang disimpan langsung di dokumen. */
export function blobKeDataUrl(blob) {
  return new Promise((resolve, reject) => {
    const pembaca = new FileReader();
    pembaca.onload = () => resolve(pembaca.result);
    pembaca.onerror = () => reject(new Error('Gagal membaca hasil kompresi.'));
    pembaca.readAsDataURL(blob);
  });
}

/** Gambar yang menumpang di dalam dokumen Firestore, bukan di Storage. */
export function adalahDataUrl(nilai) {
  return typeof nilai === 'string' && nilai.startsWith('data:');
}

/** Perkiraan besar sebuah data URL di dalam dokumen Firestore (byte UTF-8). */
export function ukuranDataUrl(nilai) {
  return adalahDataUrl(nilai) ? nilai.length : 0;
}

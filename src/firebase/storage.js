import { getStorage, ref, uploadBytes, getDownloadURL } from 'firebase/storage';
import { app } from './config';

/**
 * Unggahan berkas ke Firebase Storage.
 *
 * KEADAAN YANG PERLU DIKETAHUI: sejak Oktober 2024 Firebase mewajibkan paket
 * Blaze untuk MENYIAPKAN bucket Storage baru, sementara seluruh proyek ini
 * dirancang berjalan di paket gratis (lihat catatan yang sama di
 * utils/adminActions.js). Bucket yang sudah terlanjur ada tetap bisa dipakai,
 * jadi kemampuan ini tidak bisa disimpulkan dari konfigurasi — hanya bisa
 * dibuktikan dengan mencoba mengunggah.
 *
 * Karena itu fungsi di sini TIDAK pernah menyembunyikan kegagalan: pemanggil
 * (components/ImagePicker.jsx) yang memutuskan apa yang terjadi berikutnya,
 * yaitu menyimpan gambarnya langsung di dalam dokumen Firestore. Yang penting
 * bagi pengguna, tombol "pilih foto" tetap bekerja di kedua keadaan.
 */

/** Bucket belum diisi di .env — tidak ada gunanya mencoba menghubunginya. */
export function storageTersedia() {
  return Boolean(import.meta.env.VITE_FIREBASE_STORAGE_BUCKET);
}

/**
 * Kode galat Storage yang berarti "fitur ini memang tidak tersedia untuk
 * proyek ini", bukan "unggahan tadi kebetulan gagal". Hanya untuk kode-kode
 * inilah beralih ke penyimpanan di dalam dokumen masuk akal; masalah jaringan
 * sesaat justru lebih baik ditampilkan apa adanya supaya bisa dicoba ulang.
 */
const GALAT_TIDAK_TERSEDIA = [
  'storage/unauthorized',
  'storage/unauthenticated',
  'storage/project-not-found',
  'storage/bucket-not-found',
  'storage/unknown',
  'storage/invalid-argument',
];

export function storageTidakTersedia(err) {
  return GALAT_TIDAK_TERSEDIA.includes(err?.code);
}

/** Nama berkas yang tidak bisa saling menimpa walau dua orang mengunggah
 *  pada detik yang sama. */
function namaBerkas(ekstensi = 'jpg') {
  const acak = Math.random().toString(36).slice(2, 8);
  return `${Date.now()}-${acak}.${ekstensi}`;
}

/**
 * @param {Blob} blob berkas yang sudah dikompres
 * @param {string} folder mis. 'portfolios/<uid>' — harus cocok dengan storage.rules
 * @returns {Promise<string>} URL unduhan yang bisa dipakai <img src> dan APK
 */
export async function unggahGambar(blob, folder) {
  const storage = getStorage(app);
  const berkasRef = ref(storage, `${folder}/${namaBerkas()}`);
  await uploadBytes(berkasRef, blob, { contentType: blob.type || 'image/jpeg' });
  return getDownloadURL(berkasRef);
}

import { callApi } from './apiClient';

/**
 * Aksi milik pelanggan & creator — dikirim ke /api/app, BUKAN /api/admin.
 *
 * Pemisahan endpoint-nya disengaja dan penting: /api/admin menegakkan
 * keanggotaan admin_users pada setiap aksi, sementara di sini pemanggilnya
 * justru pengguna biasa. Memakai satu endpoint untuk keduanya akan membuat
 * aksi admin bisa dijangkau lewat jalur aplikasi.
 *
 * Nama aksinya sama persis dengan kunci pada HANDLERS di api/app.js dan
 * dengan konstanta CloudFunctions di sisi APK — kalau salah satu diubah,
 * ketiganya harus ikut berubah.
 *
 * Yang dipakai portal creator hanyalah sebagian dari daftar ini; aksi milik
 * pelanggan (createBooking, requestRefund, dan seterusnya) sengaja tidak
 * diekspor karena web ini tidak punya area pelanggan.
 */
const bind = (action) => (payload) => callApi('/api/app', action, payload);

/** Creator menandai sesi dimulai: status booking -> in_progress. */
export const startService = bind('startService');
/** Creator menerima booking yang sudah dibayar: status booking -> confirmed. */
export const confirmBooking = bind('confirmBooking');
/** Creator menandai pekerjaan selesai: status booking -> completed. */
export const markServiceCompleted = bind('markServiceCompleted');
/** Pembatalan oleh salah satu pihak: status booking -> cancelled. */
export const cancelBooking = bind('cancelBooking');

/** Menutup satu tanggal di kalender ketersediaan ({ date: 'YYYY-MM-DD', reason }). */
export const blockAvailabilityDate = bind('blockAvailabilityDate');
/** Membuka kembali tanggal yang sebelumnya ditutup ({ date: 'YYYY-MM-DD' }). */
export const unblockAvailabilityDate = bind('unblockAvailabilityDate');

/** Pengajuan penarikan saldo ({ amount, bankCode, bankAccountNumber, bankAccountName }). */
export const requestWithdrawal = bind('requestWithdrawal');

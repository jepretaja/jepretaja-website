/**
 * Terjemahkan error Firestore ke pesan yang bisa dipahami admin (bukan
 * pesan teknis Firebase mentah) — section 49 & 66.
 */
export function friendlyErrorMessage(error) {
  if (!error) return 'Terjadi kesalahan.';
  switch (error.code) {
    case 'permission-denied':
      return 'Anda tidak punya izin untuk melihat data ini. Role Anda mungkin tidak mencakup akses ke halaman ini.';
    case 'unavailable':
    case 'deadline-exceeded':
      return 'Koneksi ke server bermasalah. Periksa jaringan Anda dan coba lagi.';
    case 'not-found':
      return 'Data tidak ditemukan.';
    case 'resource-exhausted':
      return 'Terlalu banyak permintaan. Coba lagi sebentar lagi.';
    default:
      return `Gagal memuat data (${error.code || 'unknown'}). Coba lagi.`;
  }
}

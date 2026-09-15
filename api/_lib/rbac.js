import { adminAuth, adminDb } from './firebaseAdmin.js';
import { unauthorized, forbidden } from './http.js';

/**
 * Salinan ROLE_PERMISSIONS dari src/auth/permissions.js. Yang di sisi React
 * hanya untuk menyembunyikan tombol; INI yang benar-benar menentukan boleh
 * atau tidak, karena dijalankan di server dan tidak bisa diubah dari browser.
 * Kalau salah satu diubah, ubah keduanya.
 */
export const ROLE_PERMISSIONS = {
  super_admin: [
    'view_users', 'manage_users', 'verify_creator', 'moderate_content',
    'manage_booking', 'manage_payment', 'manage_escrow', 'manage_withdrawal',
    'manage_refund', 'manage_dispute', 'manage_promotion', 'view_analytics',
    'manage_settings', 'manage_admin',
    // Lihat catatan lengkapnya di src/auth/permissions.js. Hanya super_admin.
    'manage_platform_payout',
  ],
  admin: [
    'view_users', 'manage_users', 'verify_creator', 'moderate_content',
    'manage_booking', 'manage_payment', 'manage_escrow', 'manage_withdrawal',
    'manage_refund', 'manage_dispute', 'manage_promotion', 'view_analytics',
    'manage_settings', 'manage_admin',
  ],
  finance_admin: ['view_users', 'manage_payment', 'manage_escrow', 'manage_withdrawal', 'manage_refund', 'view_analytics'],
  moderator: ['view_users', 'verify_creator', 'moderate_content', 'manage_booking'],
  support_admin: ['view_users', 'manage_booking', 'manage_dispute'],
};

export function hasPermission(role, permission) {
  return (ROLE_PERMISSIONS[role] || []).includes(permission);
}

/**
 * Verifikasi ID token Firebase yang dikirim di header Authorization, lalu
 * pastikan pemiliknya terdaftar di koleksi admin_users dengan status aktif
 * dan punya permission yang diminta.
 *
 * @param {string|null} permission  null = cukup admin mana pun.
 */
export async function requireAdmin(req, permission) {
  const header = req.headers.authorization || req.headers.Authorization || '';
  const token = header.startsWith('Bearer ') ? header.slice(7).trim() : '';
  if (!token) throw unauthorized('Anda belum login. Muat ulang halaman lalu login kembali.');

  let decoded;
  try {
    decoded = await adminAuth().verifyIdToken(token);
  } catch {
    throw unauthorized('Sesi login sudah kedaluwarsa. Silakan login ulang.');
  }

  const snap = await adminDb().collection('admin_users').doc(decoded.uid).get();
  if (!snap.exists) throw forbidden('Akun ini tidak terdaftar sebagai admin.');

  const profile = snap.data() || {};
  if (profile.status && profile.status !== 'active') {
    throw forbidden('Akun admin Anda sedang dinonaktifkan.');
  }
  if (permission && !hasPermission(profile.role, permission)) {
    throw forbidden(`Role "${profile.role}" tidak punya izin "${permission}".`);
  }

  return { uid: decoded.uid, email: decoded.email || profile.email || null, role: profile.role, name: profile.name || null };
}

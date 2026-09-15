/**
 * Permission map — HARUS identik dengan backend/functions/src/rbac.js.
 * Ini HANYA dipakai untuk menyembunyikan menu/tombol di UI (kenyamanan).
 * Otorisasi SEBENARNYA selalu ditegakkan ulang di Cloud Functions — UI ini
 * tidak pernah menjadi satu-satunya lapisan keamanan (section 41 & 73).
 */
export const ROLE_PERMISSIONS = {
  super_admin: [
    'view_users', 'manage_users', 'verify_creator', 'moderate_content',
    'manage_booking', 'manage_payment', 'manage_escrow', 'manage_withdrawal',
    'manage_refund', 'manage_dispute', 'manage_promotion', 'view_analytics',
    'manage_settings', 'manage_admin',
    // Mencairkan komisi platform ke rekening pemilik. SENGAJA hanya ada di
    // super_admin dan tidak diberikan ke role lain mana pun: ini satu-satunya
    // izin di sistem ini yang memindahkan uang KELUAR dari platform, bukan
    // memindahkannya antar pihak yang sudah berhak. Finance admin pun tidak
    // punya — ia mengurus dana milik creator dan pelanggan, bukan kas platform.
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

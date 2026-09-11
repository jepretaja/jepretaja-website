import { callApi } from './apiClient';

/**
 * Semua aksi finansial & administratif dikirim ke Serverless Function di
 * /api/admin (satu deployment yang sama dengan web ini di Vercel), BUKAN
 * ditulis langsung ke Firestore dari browser.
 *
 * Kenapa bukan Cloud Functions seperti sebelumnya? Sejak 3 Februari 2026
 * Cloud Functions & Cloud Storage mewajibkan paket Blaze (harus pasang kartu).
 * Firestore + Authentication tetap gratis di paket Spark, jadi logika server
 * dipindah ke Vercel Serverless Function yang gratis dan tanpa kartu.
 *
 * Model keamanannya sama persis: browser mengirim ID token, server yang
 * memverifikasi token itu, mengecek koleksi admin_users, dan menegakkan RBAC.
 * Halaman ini tidak pernah jadi satu-satunya lapisan keamanan.
 *
 * Mekanisme pemanggilannya sendiri (token, penerjemahan error) ada di
 * utils/apiClient.js karena portal creator memakai kontrak yang sama ke
 * /api/app — lihat utils/appActions.js.
 */
const bind = (action) => (payload) => callApi('/api/admin', action, payload);

export const processWithdrawal = bind('processWithdrawal');
export const markWithdrawalManual = bind('markWithdrawalManual');
export const resolveDispute = bind('resolveDispute');
export const adminUpdateBookingStatus = bind('adminUpdateBookingStatus');
export const confirmManualPayment = bind('confirmManualPayment');
export const releaseEscrow = bind('releaseEscrow');
export const processRefund = bind('processRefund');
export const inviteAdmin = bind('inviteAdmin');
export const adminDeleteUser = bind('adminDeleteUser');

// Kas platform — server menolak ketiganya untuk peran selain super_admin.
export const syncPlatformRevenue = bind('syncPlatformRevenue');
export const withdrawPlatformBalance = bind('withdrawPlatformBalance');
export const cancelPlatformPayout = bind('cancelPlatformPayout');

/**
 * Mencatat aksi admin ke audit_logs.
 *
 * Nama aksi yang dicatat dikirim sebagai `auditAction`, bukan `action`, karena
 * `action` sudah dipakai router /api/admin untuk memilih handler. Selama ini
 * keduanya bertabrakan: seluruh 13 pemanggil mengirim `action: 'create_category'`
 * dan sejenisnya, yang menimpa nama handler sehingga setiap pencatatan audit
 * gagal dengan 404 — dan gagalnya tidak terlihat karena semua pemanggil
 * membungkusnya dengan .catch(() => {}).
 */
export const logAdminAction = ({ action, ...rest }) =>
  callApi('/api/admin', 'writeAdminAuditLog', { ...rest, auditAction: action });

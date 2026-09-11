import { HttpError, readBody, requireString } from './_lib/http.js';
import { processWithdrawal, markWithdrawalManual } from './_lib/actions/withdrawals.js';
import { adminUpdateBookingStatus } from './_lib/actions/bookings.js';
import { resolveDispute } from './_lib/actions/disputes.js';
import { inviteAdmin, writeAdminAuditLog, adminDeleteUser } from './_lib/actions/adminUsers.js';
import { confirmManualPayment, releaseEscrow, processRefund } from './_lib/actions/adminPayments.js';
import { syncPlatformRevenue, withdrawPlatformBalance, cancelPlatformPayout } from './_lib/actions/platformPayout.js';

/**
 * Satu Serverless Function untuk semua aksi admin.
 *
 * Digabung jadi satu file (bukan satu file per endpoint) karena Vercel paket
 * Hobby membatasi jumlah Serverless Function per deployment — satu router
 * membuat sisa kuota tetap lega untuk fitur berikutnya.
 *
 * Kontrak: POST /api/admin dengan body { action, ...payload } dan header
 * Authorization: Bearer <Firebase ID token>.
 */
const HANDLERS = {
  processWithdrawal,
  markWithdrawalManual,
  adminUpdateBookingStatus,
  confirmManualPayment,
  releaseEscrow,
  processRefund,
  resolveDispute,
  inviteAdmin,
  adminDeleteUser,
  writeAdminAuditLog,
  // Kas platform — ketiganya hanya untuk super_admin, ditegakkan di dalam
  // handler-nya sendiri (lihat requireSuperAdmin di platformPayout.js).
  syncPlatformRevenue,
  withdrawPlatformBalance,
  cancelPlatformPayout,
};

export default async function handler(req, res) {
  if (req.method !== 'POST') {
    res.setHeader('Allow', 'POST');
    return res.status(405).json({ error: { code: 'method-not-allowed', message: 'Gunakan metode POST.' } });
  }

  try {
    const body = readBody(req);
    const action = requireString(body.action, 'action');
    const fn = HANDLERS[action];
    if (!fn) {
      return res.status(404).json({ error: { code: 'not-found', message: `Aksi "${action}" tidak dikenal.` } });
    }

    const data = await fn(req);
    return res.status(200).json({ data });
  } catch (err) {
    if (err instanceof HttpError) {
      return res.status(err.status).json({ error: { code: err.code, message: err.message } });
    }
    // Error tak terduga: catat lengkap di log Vercel, balas generik ke browser
    // supaya detail internal tidak bocor ke halaman admin.
    console.error('[api/admin] unhandled error:', err);
    const isConfigError = err instanceof Error && err.message.includes('FIREBASE_SERVICE_ACCOUNT');
    return res.status(500).json({
      error: {
        code: 'internal',
        message: isConfigError ? err.message : 'Terjadi kesalahan di server. Coba lagi beberapa saat.',
      },
    });
  }
}

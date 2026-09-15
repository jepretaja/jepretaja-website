import { HttpError, readBody, requireString } from './_lib/http.js';
import {
  createBooking, previewBookingPrice, confirmBooking, startService, markServiceCompleted,
  confirmBookingCompletion, cancelBooking, requestRefund, openDispute,
} from './_lib/actions/appBooking.js';
import { createPaymentOrder } from './_lib/actions/appPayment.js';
import { blockAvailabilityDate, unblockAvailabilityDate } from './_lib/actions/appAvailability.js';
import { requestWithdrawal } from './_lib/actions/appWallet.js';
import { notifyInteraction } from './_lib/actions/appNotify.js';
import { becomeCreator } from './_lib/actions/appCreator.js';

/**
 * Satu Serverless Function untuk semua aksi yang dipanggil aplikasi Android.
 *
 * Terpisah dari /api/admin karena pemanggilnya berbeda: di sini pelanggan
 * dan creator biasa, di sana anggota admin_users. Memisahkannya menjaga
 * agar aksi admin tidak pernah bisa dijangkau lewat jalur aplikasi.
 *
 * Nama aksi sengaja sama persis dengan konstanta di object CloudFunctions
 * pada app/src/main/java/com/jepretaja/app/core/util/FirestorePaths.kt.
 *
 * Kontrak: POST /api/app dengan body { action, ...payload } dan header
 * Authorization: Bearer <Firebase ID token>.
 */
const HANDLERS = {
  createBooking,
  previewBookingPrice,
  confirmBooking,
  startService,
  markServiceCompleted,
  confirmBookingCompletion,
  cancelBooking,
  requestRefund,
  openDispute,
  createPaymentOrder,
  blockAvailabilityDate,
  unblockAvailabilityDate,
  requestWithdrawal,
  notifyInteraction,
  becomeCreator,
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
    console.error('[api/app] unhandled error:', err);
    const isConfigError = err instanceof Error && err.message.includes('FIREBASE_SERVICE_ACCOUNT');
    return res.status(500).json({
      error: {
        code: 'internal',
        message: isConfigError ? err.message : 'Terjadi kesalahan di server. Coba lagi beberapa saat.',
      },
    });
  }
}

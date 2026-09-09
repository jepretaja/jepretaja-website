import { adminDb, FieldValue } from '../firebaseAdmin.js';
import { requireAdmin } from '../rbac.js';
import { writeAudit } from '../audit.js';
import { readBody, requireString, notFound, conflict } from '../http.js';

/**
 * Graf transisi status booking — harus sinkron dengan object BookingStatus di
 * app/src/main/java/com/jepretaja/app/data/model/BookingModel.kt.
 *
 * Admin override tetap divalidasi di sini: tanpa graf ini seorang admin bisa
 * memindahkan booking dari "draft" langsung ke "funds_released" dan melepas
 * dana untuk booking yang belum pernah dibayar.
 */
export const BOOKING_TRANSITIONS = {
  draft: ['pending_payment', 'cancelled'],
  pending_payment: ['paid', 'confirmed', 'rejected', 'cancelled'],
  paid: ['confirmed', 'rejected', 'cancelled', 'refund_requested'],
  confirmed: ['upcoming', 'rejected', 'cancelled', 'refund_requested'],
  upcoming: ['in_progress', 'cancelled', 'refund_requested'],
  in_progress: ['completed', 'disputed'],
  completed: ['customer_confirmed', 'disputed', 'refund_requested'],
  customer_confirmed: ['funds_released', 'disputed'],
  funds_released: ['reviewed'],
  reviewed: [],
  cancelled: [],
  refund_requested: ['cancelled', 'confirmed', 'disputed'],
  disputed: ['funds_released', 'cancelled', 'confirmed'],
  rejected: [],
};

export async function adminUpdateBookingStatus(req) {
  const actor = await requireAdmin(req, 'manage_booking');
  const body = readBody(req);
  const bookingId = requireString(body.bookingId, 'bookingId');
  const toStatus = requireString(body.toStatus, 'toStatus');
  const reason = typeof body.reason === 'string' && body.reason.trim() ? body.reason.trim() : 'manual override by admin';

  if (!Object.prototype.hasOwnProperty.call(BOOKING_TRANSITIONS, toStatus)) {
    throw conflict(`Status "${toStatus}" tidak dikenal.`);
  }

  const db = adminDb();
  const ref = db.collection('bookings').doc(bookingId);

  return db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    if (!snap.exists) throw notFound('Booking tidak ditemukan.');
    const booking = snap.data();
    const fromStatus = booking.status || 'draft';

    if (fromStatus === toStatus) {
      throw conflict(`Booking sudah berstatus "${toStatus}".`);
    }
    const allowed = BOOKING_TRANSITIONS[fromStatus] || [];
    if (!allowed.includes(toStatus)) {
      throw conflict(
        `Transisi "${fromStatus}" ke "${toStatus}" tidak diizinkan. ` +
          (allowed.length ? `Dari "${fromStatus}" hanya bisa ke: ${allowed.join(', ')}.` : `"${fromStatus}" adalah status akhir.`)
      );
    }

    const update = {
      status: toStatus,
      statusUpdatedAt: FieldValue.serverTimestamp(),
      statusUpdatedBy: actor.uid,
    };
    if (toStatus === 'cancelled') update.cancelledAt = FieldValue.serverTimestamp();
    if (toStatus === 'customer_confirmed') update.customerConfirmedAt = FieldValue.serverTimestamp();
    if (toStatus === 'funds_released') update.fundsReleasedAt = FieldValue.serverTimestamp();

    tx.update(ref, update);
    writeAudit(db, actor, {
      action: 'admin_update_booking_status',
      targetType: 'booking',
      targetId: bookingId,
      reason,
      metadata: { fromStatus, toStatus },
    }, tx);

    return { bookingId, fromStatus, toStatus };
  });
}

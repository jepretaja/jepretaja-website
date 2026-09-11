import { adminDb, FieldValue } from '../firebaseAdmin.js';
import { requireAdmin } from '../rbac.js';
import { writeAudit } from '../audit.js';
import { readBody, requireString, requireOneOf, requirePositiveInt, notFound, conflict, badRequest } from '../http.js';

/**
 * Keputusan dispute yang memindahkan uang harus atomik: escrow, wallet,
 * ledger, booking, dan audit log ditulis dalam SATU runTransaction. Kalau
 * salah satu gagal, tidak ada yang berubah — tidak ada kondisi setengah jadi
 * di mana saldo sudah pindah tapi status dispute masih "open".
 */

const DECISIONS = ['release_full', 'refund_full', 'refund_partial', 'reschedule', 'request_evidence', 'close_no_refund'];

/** Keputusan yang tidak menyentuh uang sama sekali. */
const NON_FINANCIAL = { reschedule: 'reschedule_requested', request_evidence: 'awaiting_evidence' };

/**
 * Jumlah yang menjadi hak creator untuk booking ini.
 * Sumber kebenaran utamanya adalah dokumen escrow_transactions (nominal yang
 * benar-benar ditahan). Fallback dipakai hanya untuk booking lama yang belum
 * punya dokumen escrow.
 */
function payoutAmount(booking, escrow) {
  if (escrow && Number(escrow.amount) > 0) return Number(escrow.amount);
  const subtotal = Number(booking?.priceBreakdown?.subtotal);
  if (Number.isFinite(subtotal) && subtotal > 0) return subtotal;
  return Number(booking?.total) || 0;
}

export function calculateDisputeAmounts(held, pendingBefore, toCustomer) {
  const toCreator = Math.max(0, held - toCustomer);
  const pendingDeduct = Math.min(held, Math.max(0, pendingBefore));
  const creatorFromPending = Math.min(toCreator, pendingDeduct);
  const customerFromPending = Math.min(toCustomer, Math.max(0, pendingDeduct - creatorFromPending));
  return {
    toCreator,
    pendingAfter: Math.max(0, pendingBefore) - pendingDeduct,
    creatorFromPending,
    customerFromPending,
  };
}

export async function resolveDispute(req) {
  const actor = await requireAdmin(req, 'manage_dispute');
  const body = readBody(req);
  const disputeId = requireString(body.disputeId, 'disputeId');
  const decision = requireOneOf(body.decision, 'decision', DECISIONS);
  const refundAmount = decision === 'refund_partial' ? requirePositiveInt(body.refundAmount, 'refundAmount') : 0;
  const note = typeof body.note === 'string' ? body.note.trim() : null;

  const db = adminDb();
  const disputeRef = db.collection('disputes').doc(disputeId);

  return db.runTransaction(async (tx) => {
    // ---- Fase baca (Firestore mewajibkan SEMUA read sebelum write) ----
    const disputeSnap = await tx.get(disputeRef);
    if (!disputeSnap.exists) throw notFound('Dispute tidak ditemukan.');
    const dispute = disputeSnap.data();
    if (dispute.status === 'resolved') {
      throw conflict('Dispute ini sudah pernah diselesaikan.');
    }

    // Keputusan non-finansial: cukup update status dispute.
    if (NON_FINANCIAL[decision]) {
      tx.update(disputeRef, {
        status: NON_FINANCIAL[decision],
        decision,
        decisionNote: note,
        resolvedBy: actor.uid,
        updatedAt: FieldValue.serverTimestamp(),
      });
      writeAudit(db, actor, {
        action: `dispute_${decision}`, targetType: 'dispute', targetId: disputeId, reason: note,
      }, tx);
      return { disputeId, decision, moved: 0 };
    }

    const bookingId = dispute.bookingId;
    if (!bookingId) throw badRequest('Dispute ini tidak terhubung ke booking mana pun.');

    const bookingRef = db.collection('bookings').doc(bookingId);
    const bookingSnap = await tx.get(bookingRef);
    if (!bookingSnap.exists) throw notFound('Booking yang disengketakan tidak ditemukan.');
    const booking = bookingSnap.data();

    const creatorId = booking.creatorId || dispute.creatorId;
    if (!creatorId) throw badRequest('Booking ini tidak punya creatorId.');

    const escrowQuery = db.collection('escrow_transactions').where('bookingId', '==', bookingId).limit(1);
    const escrowDocs = await tx.get(escrowQuery);
    const escrowDoc = escrowDocs.empty ? null : escrowDocs.docs[0];
    const escrow = escrowDoc ? escrowDoc.data() : null;

    if (escrow && escrow.releaseStatus && escrow.releaseStatus !== 'pending') {
      throw conflict(`Dana booking ini sudah pernah diproses (release status: "${escrow.releaseStatus}").`);
    }

    const walletRef = db.collection('wallets').doc(creatorId);
    const walletSnap = await tx.get(walletRef);
    if (!walletSnap.exists) throw notFound('Wallet creator tidak ditemukan.');
    const wallet = walletSnap.data();

    // ---- Hitung pergerakan dana ----
    const held = payoutAmount(booking, escrow);
    if (held <= 0) throw badRequest('Nominal dana yang ditahan untuk booking ini tidak valid.');

    let toCustomer = 0;
    if (decision === 'refund_full') toCustomer = held;
    if (decision === 'refund_partial') {
      if (refundAmount >= held) {
        throw badRequest(`Refund sebagian (${refundAmount}) harus lebih kecil dari dana tertahan (${held}). Gunakan "Refund Penuh" jika ingin mengembalikan semuanya.`);
      }
      toCustomer = refundAmount;
    }
    const pendingBefore = Number(wallet.pendingBalance) || 0;
    const availableBefore = Number(wallet.availableBalance) || 0;
    const { toCreator, pendingAfter, creatorFromPending, customerFromPending } =
      calculateDisputeAmounts(held, pendingBefore, toCustomer);
      const availableAfter = availableBefore + creatorFromPending;

    // ---- Fase tulis ----
    tx.update(walletRef, {
      pendingBalance: pendingAfter,
      availableBalance: availableAfter,
      updatedAt: FieldValue.serverTimestamp(),
    });

    if (creatorFromPending > 0) {
      const ledgerRef = db.collection('wallet_transactions').doc();
      tx.set(ledgerRef, {
        creatorId,
        referenceId: bookingId,
        type: 'credit',
          amount: creatorFromPending,
        balanceBefore: availableBefore,
        balanceAfter: availableAfter,
        status: 'success',
        note: `Pelepasan dana hasil keputusan dispute (${decision}).`,
        createdAt: FieldValue.serverTimestamp(),
      });
    }

    if (toCustomer > 0) {
      const refundRef = db.collection('refunds').doc();
      tx.set(refundRef, {
        bookingId,
        customerId: booking.customerId || null,
        creatorId,
        amount: toCustomer,
        reason: note || `Hasil keputusan dispute: ${decision}`,
        status: 'pending',
        disputeId,
        providerReference: null,
        requestedBy: actor.uid,
        createdAt: FieldValue.serverTimestamp(),
      });
      // Ledger debit dicatat hanya jika dana memang pernah masuk pending.
            if (customerFromPending > 0) {
        const ledgerRef = db.collection('wallet_transactions').doc();
        tx.set(ledgerRef, {
          creatorId,
          referenceId: bookingId,
          type: 'refund',
            amount: -customerFromPending,
          balanceBefore: pendingBefore,
          balanceAfter: pendingAfter,
          status: 'success',
          note: `Refund ke customer hasil keputusan dispute (${decision}).`,
          createdAt: FieldValue.serverTimestamp(),
        });
      }
    }

    if (escrowDoc) {
      tx.update(escrowDoc.ref, {
        holdStatus: 'released',
        releaseStatus: toCustomer > 0 ? (toCreator > 0 ? 'partially_refunded' : 'refunded') : 'released',
        releasedAmount: toCreator,
        refundedAmount: toCustomer,
        releasedAt: FieldValue.serverTimestamp(),
        releasedBy: actor.uid,
      });
    }

    tx.update(bookingRef, {
      status: decision === 'refund_full' ? 'cancelled' : 'funds_released',
      fundsReleasedAt: FieldValue.serverTimestamp(),
      statusUpdatedBy: actor.uid,
    });

    tx.update(disputeRef, {
      status: 'resolved',
      decision,
      decisionNote: note,
      refundAmount: toCustomer,
      releasedAmount: toCreator,
      resolvedBy: actor.uid,
      resolvedAt: FieldValue.serverTimestamp(),
    });

    writeAudit(db, actor, {
      action: `dispute_${decision}`,
      targetType: 'dispute',
      targetId: disputeId,
      reason: note,
        metadata: { bookingId, creatorId, held, toCreator, toCustomer, creatorFromPending, customerFromPending, pendingBefore, pendingAfter, availableBefore, availableAfter },
    }, tx);

      return { disputeId, decision, toCreator: creatorFromPending, toCustomer };
  });
}

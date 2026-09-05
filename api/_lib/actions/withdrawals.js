import { adminDb, FieldValue } from '../firebaseAdmin.js';
import { requireAdmin } from '../rbac.js';
import { writeAudit } from '../audit.js';
import { writeNotification } from '../notify.js';
import { readBody, requireString, requireOneOf, notFound, conflict, badRequest } from '../http.js';

/**
 * Semua mutasi saldo dijalankan di dalam runTransaction supaya dua admin yang
 * menekan Approve bersamaan tidak bisa memotong saldo dua kali — Firestore
 * akan membatalkan dan mengulang salah satu transaksi.
 *
 * Ledger wallet_transactions bersifat immutable: tidak pernah di-update,
 * pembalikan dicatat sebagai baris baru.
 */

function ledgerEntry(tx, db, { creatorId, referenceId, type, amount, balanceBefore, balanceAfter, note }) {
  const ref = db.collection('wallet_transactions').doc();
  tx.set(ref, {
    creatorId,
    referenceId,
    type,
    amount,
    balanceBefore,
    balanceAfter,
    status: 'success',
    note,
    createdAt: FieldValue.serverTimestamp(),
  });
}

export async function processWithdrawal(req) {
  const actor = await requireAdmin(req, 'manage_withdrawal');
  const body = readBody(req);
  const withdrawalId = requireString(body.withdrawalId, 'withdrawalId');
  const action = requireOneOf(body.action, 'action', ['approve', 'reject']);
  const reason = typeof body.reason === 'string' ? body.reason.trim() : null;

  const db = adminDb();
  const wRef = db.collection('withdrawals').doc(withdrawalId);

  const result = await db.runTransaction(async (tx) => {
    const wSnap = await tx.get(wRef);
    if (!wSnap.exists) throw notFound('Withdrawal tidak ditemukan.');
    const w = wSnap.data();

    if (w.status !== 'requested') {
      throw conflict(`Withdrawal ini sudah berstatus "${w.status}", tidak bisa diproses lagi.`);
    }

    const amount = Number(w.amount) || 0;
    if (amount <= 0) throw badRequest('Nominal withdrawal tidak valid.');

    if (action === 'reject') {
      // Saldo belum pernah dipotong saat status masih "requested", jadi
      // penolakan tidak menyentuh wallet sama sekali.
      tx.update(wRef, {
        status: 'failed',
        failureReason: reason || 'Ditolak oleh admin.',
        processedBy: actor.uid,
        processedAt: FieldValue.serverTimestamp(),
      });
      writeNotification(db, tx, {
        userId: w.creatorId,
        type: 'withdrawal',
        title: 'Penarikan ditolak',
        body: reason || 'Pengajuan penarikanmu ditolak admin. Saldo tidak berkurang.',
        referenceId: withdrawalId,
      });
      writeAudit(db, actor, {
        action: 'reject_withdrawal',
        targetType: 'withdrawal',
        targetId: withdrawalId,
        reason: reason || null,
        metadata: { amount, creatorId: w.creatorId || null },
      }, tx);
      return { status: 'failed', amount };
    }

    // action === 'approve': potong Available Balance creator sekarang.
    const walletRef = db.collection('wallets').doc(w.creatorId);
    const walletSnap = await tx.get(walletRef);
    if (!walletSnap.exists) throw notFound('Wallet creator tidak ditemukan.');
    const wallet = walletSnap.data();
    const before = Number(wallet.availableBalance) || 0;
    if (before < amount) {
      throw conflict(`Saldo available creator hanya ${before}, tidak cukup untuk withdrawal ${amount}.`);
    }
    const after = before - amount;

    tx.update(walletRef, { availableBalance: after, updatedAt: FieldValue.serverTimestamp() });
    tx.update(wRef, {
      status: 'processing',
      approvedBy: actor.uid,
      approvedAt: FieldValue.serverTimestamp(),
    });
    ledgerEntry(tx, db, {
      creatorId: w.creatorId,
      referenceId: withdrawalId,
      type: 'withdrawal',
      amount: -amount,
      balanceBefore: before,
      balanceAfter: after,
      note: 'Withdrawal disetujui admin, menunggu transfer bank.',
    });
    writeNotification(db, tx, {
      userId: w.creatorId,
      type: 'withdrawal',
      title: 'Penarikan disetujui',
      body: `Rp${amount.toLocaleString('id-ID')} sedang diproses ke rekeningmu.`,
      referenceId: withdrawalId,
    });
    writeAudit(db, actor, {
      action: 'approve_withdrawal',
      targetType: 'withdrawal',
      targetId: withdrawalId,
      reason: reason || null,
      metadata: { amount, creatorId: w.creatorId, balanceBefore: before, balanceAfter: after },
    }, tx);

    return { status: 'processing', amount, balanceAfter: after };
  });

  return result;
}

/**
 * Dipakai kalau disbursement otomatis belum tersedia: admin transfer manual
 * lewat mobile banking, lalu menandai hasilnya di sini.
 */
export async function markWithdrawalManual(req) {
  const actor = await requireAdmin(req, 'manage_withdrawal');
  const body = readBody(req);
  const withdrawalId = requireString(body.withdrawalId, 'withdrawalId');
  const action = requireOneOf(body.action, 'action', ['success', 'failed']);
  const bankReference = typeof body.bankReference === 'string' ? body.bankReference.trim() : '';
  const reason = typeof body.reason === 'string' ? body.reason.trim() : '';

  if (action === 'success' && !bankReference) {
    throw badRequest('Isi nomor referensi transfer bank terlebih dahulu.');
  }
  if (action === 'failed' && !reason) {
    throw badRequest('Isi alasan kegagalan transfer terlebih dahulu.');
  }

  const db = adminDb();
  const wRef = db.collection('withdrawals').doc(withdrawalId);

  return db.runTransaction(async (tx) => {
    const wSnap = await tx.get(wRef);
    if (!wSnap.exists) throw notFound('Withdrawal tidak ditemukan.');
    const w = wSnap.data();
    if (w.status !== 'processing') {
      throw conflict(`Hanya withdrawal berstatus "processing" yang bisa ditandai manual (status sekarang: "${w.status}").`);
    }

    const amount = Number(w.amount) || 0;
    const walletRef = db.collection('wallets').doc(w.creatorId);
    const walletSnap = await tx.get(walletRef);
    if (!walletSnap.exists) throw notFound('Wallet creator tidak ditemukan.');
    const wallet = walletSnap.data();

    if (action === 'success') {
      const withdrawnBefore = Number(wallet.withdrawn) || 0;
      tx.update(walletRef, {
        withdrawn: withdrawnBefore + amount,
        updatedAt: FieldValue.serverTimestamp(),
      });
      tx.update(wRef, {
        status: 'success',
        bankReference,
        completedBy: actor.uid,
        completedAt: FieldValue.serverTimestamp(),
      });
      writeNotification(db, tx, {
        userId: w.creatorId,
        type: 'withdrawal',
        title: 'Dana sudah ditransfer',
        body: `Rp${amount.toLocaleString('id-ID')} sudah dikirim ke rekeningmu. Referensi: ${bankReference}.`,
        referenceId: withdrawalId,
      });
      writeAudit(db, actor, {
        action: 'withdrawal_manual_success',
        targetType: 'withdrawal',
        targetId: withdrawalId,
        reason: bankReference,
        metadata: { amount, creatorId: w.creatorId },
      }, tx);
      return { status: 'success', amount };
    }

    // action === 'failed': kembalikan dana yang sudah dipotong saat approve.
    const before = Number(wallet.availableBalance) || 0;
    const after = before + amount;
    tx.update(walletRef, { availableBalance: after, updatedAt: FieldValue.serverTimestamp() });
    tx.update(wRef, {
      status: 'failed',
      failureReason: reason,
      completedBy: actor.uid,
      completedAt: FieldValue.serverTimestamp(),
    });
    ledgerEntry(tx, db, {
      creatorId: w.creatorId,
      referenceId: withdrawalId,
      type: 'adjustment',
      amount,
      balanceBefore: before,
      balanceAfter: after,
      note: `Pengembalian saldo karena transfer gagal: ${reason}`,
    });
    writeNotification(db, tx, {
      userId: w.creatorId,
      type: 'withdrawal',
      title: 'Transfer gagal, saldo dikembalikan',
      body: reason,
      referenceId: withdrawalId,
    });
    writeAudit(db, actor, {
      action: 'withdrawal_manual_failed',
      targetType: 'withdrawal',
      targetId: withdrawalId,
      reason,
      metadata: { amount, creatorId: w.creatorId, balanceBefore: before, balanceAfter: after },
    }, tx);
    return { status: 'failed', amount, balanceAfter: after };
  });
}

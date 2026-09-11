import { adminDb, FieldValue } from '../firebaseAdmin.js';
import { requireAdmin } from '../rbac.js';
import { writeAudit } from '../audit.js';
import { writeNotification } from '../notify.js';
import { readBody, requireString, requireOneOf, notFound, conflict } from '../http.js';
import { tambahKomisiPlatform } from './platformPayout.js';

/**
 * Verifikasi transfer manual oleh admin.
 *
 * Ini mata rantai yang menyambungkan alur pembayaran: createPaymentOrder
 * (dipanggil aplikasi) menerbitkan instruksi transfer berstatus
 * "awaiting_transfer", dan tanpa aksi ini booking akan tersangkut di
 * "pending_payment" selamanya meskipun uangnya sudah masuk.
 *
 * Dana TIDAK langsung masuk ke saldo creator. Uang ditahan lebih dulu
 * (escrow) dan baru dilepas setelah pekerjaan selesai dan dikonfirmasi
 * pelanggan — kalau langsung dikreditkan, creator bisa menarik dana lalu
 * tidak mengerjakan pesanan.
 */
export async function confirmManualPayment(req) {
  const actor = await requireAdmin(req, 'manage_payment');
  const body = readBody(req);
  const paymentId = requireString(body.paymentId, 'paymentId');
  const action = requireOneOf(body.action, 'action', ['confirm', 'reject']);
  const reason = typeof body.reason === 'string' ? body.reason.trim() : null;

  const db = adminDb();
  const payRef = db.collection('payments').doc(paymentId);

  const hasil = await db.runTransaction(async (tx) => {
    const paySnap = await tx.get(payRef);
    if (!paySnap.exists) throw notFound('Data pembayaran tidak ditemukan.');
    const pay = paySnap.data();

    // Dijalankan dalam transaksi supaya dua admin yang menekan Konfirmasi
    // bersamaan tidak bisa mengkredit escrow dua kali untuk satu transfer.
    if (pay.status !== 'awaiting_transfer') {
      throw conflict(`Pembayaran ini sudah berstatus "${pay.status}", tidak bisa diproses lagi.`);
    }

    const bookingRef = db.collection('bookings').doc(pay.bookingId);
    const bookingSnap = await tx.get(bookingRef);
    if (!bookingSnap.exists) throw notFound('Booking terkait tidak ditemukan.');
    const booking = bookingSnap.data();

    if (action === 'reject') {
      tx.update(payRef, {
        status: 'rejected',
        rejectedReason: reason,
        rejectedAt: FieldValue.serverTimestamp(),
        processedBy: actor.uid,
      });
      // Booking tetap pending_payment supaya pelanggan bisa mencoba lagi.
      return { paymentId, action, bookingId: pay.bookingId, status: 'rejected' };
    }

    if (booking.status !== 'pending_payment') {
      throw conflict(`Booking berstatus "${booking.status}", tidak menunggu pembayaran.`);
    }

    const amount = Number(pay.amount) || 0;
    const feePercent = Number(booking.priceBreakdown?.platformFeePercent) || 0;
    const platformFee = Number(booking.priceBreakdown?.platformFee) || Math.floor((amount * feePercent) / 100);
    const creatorShare = Math.max(0, amount - platformFee);
    const walletRef = db.collection('wallets').doc(booking.creatorId);
    const walletSnap = await tx.get(walletRef);
    const wallet = walletSnap.data() || {};
    const pendingBefore = Number(wallet.pendingBalance) || 0;
    const pendingAfter = pendingBefore + creatorShare;

    tx.update(payRef, {
      status: 'paid',
      paidAt: FieldValue.serverTimestamp(),
      processedBy: actor.uid,
      verifiedNote: reason,
    });

    tx.update(bookingRef, {
      status: 'paid',
      paidAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    });

    // Dana creator dicatat sebagai pending sejak pembayaran diverifikasi.
    // Sebelumnya escrow hanya dibuat tanpa menaikkan pendingBalance, sehingga
    // dispute/release berikutnya bisa mengurangi saldo yang sebenarnya nol.
    tx.set(walletRef, {
      creatorId: booking.creatorId,
      pendingBalance: pendingAfter,
      availableBalance: Number(wallet.availableBalance) || 0,
      totalEarnings: Number(wallet.totalEarnings) || 0,
      updatedAt: FieldValue.serverTimestamp(),
    }, { merge: true });
    tx.set(db.collection('wallet_transactions').doc(), {
      creatorId: booking.creatorId,
      referenceId: pay.bookingId,
      type: 'escrow_hold',
      amount: creatorShare,
      balanceBefore: pendingBefore,
      balanceAfter: pendingAfter,
      status: 'held',
      note: 'Dana ditahan setelah pembayaran diverifikasi.',
      createdAt: FieldValue.serverTimestamp(),
    });

    // Escrow: dana ditahan atas nama creator, belum bisa ditarik.
    // Nama koleksi dan field mengikuti yang sudah dibaca halaman
    // HeldFunds.jsx (holdStatus/releaseStatus) serta FirestorePaths.kt di
    // aplikasi — memakai nama sendiri akan membuat dana tertahan tidak
    // muncul di mana pun.
    const escrowRef = db.collection('escrow_transactions').doc(pay.bookingId);
    tx.set(escrowRef, {
      bookingId: pay.bookingId,
      paymentId,
      customerId: booking.customerId,
      creatorId: booking.creatorId,
      amount,
      platformFee,
      creatorShare,
      holdStatus: 'held',
      releaseStatus: 'pending',
      providerReference: pay.uniqueCode ? `MANUAL-${pay.uniqueCode}` : null,
      heldAt: FieldValue.serverTimestamp(),
    });

    tx.set(db.collection('booking_status_history').doc(), {
      bookingId: pay.bookingId,
      fromStatus: 'pending_payment',
      toStatus: 'paid',
      actorId: actor.uid,
      actorRole: 'admin',
      reason: reason || 'transfer manual diverifikasi admin',
      createdAt: FieldValue.serverTimestamp(),
    });

    // Kedua pihak diberi tahu: pelanggan supaya tahu transfernya sudah masuk,
    // creator supaya bisa mulai menyiapkan sesinya.
    writeNotification(db, tx, {
      userId: booking.customerId,
      type: 'payment',
      title: 'Pembayaran terverifikasi',
      body: 'Transfer kamu sudah dicek admin. Booking berlanjut ke tahap berikutnya.',
      referenceId: pay.bookingId,
    });
    writeNotification(db, tx, {
      userId: booking.creatorId,
      type: 'payment',
      title: 'Pesanan sudah dibayar',
      body: 'Pembayaran pelanggan terverifikasi. Danamu ditahan sampai sesi selesai.',
      referenceId: pay.bookingId,
    });

    writeAudit(db, actor, {
      action: 'confirm_manual_payment',
      targetType: 'payment',
      targetId: paymentId,
      reason,
      metadata: { bookingId: pay.bookingId, amount, platformFee, creatorShare },
    }, tx);

    return { paymentId, action, bookingId: pay.bookingId, status: 'paid', amount, creatorShare };
  });

  if (action === 'reject') {
    writeAudit(db, actor, {
      action: 'reject_manual_payment',
      targetType: 'payment',
      targetId: paymentId,
      reason,
      metadata: { bookingId: hasil.bookingId },
    });
  }

  return hasil;
}

/**
 * Melepas dana escrow ke saldo creator setelah pekerjaan dikonfirmasi.
 *
 * Dipisahkan dari confirmManualPayment karena terjadi di titik waktu yang
 * berbeda: uang masuk saat pembayaran, tapi baru menjadi hak creator
 * setelah pelanggan mengonfirmasi hasil.
 */
export async function releaseEscrow(req) {
  const actor = await requireAdmin(req, 'manage_escrow');
  const body = readBody(req);
  const bookingId = requireString(body.bookingId, 'bookingId');
  const reason = typeof body.reason === 'string' ? body.reason.trim() : null;

  const db = adminDb();
  const escrowRef = db.collection('escrow_transactions').doc(bookingId);
  const bookingRef = db.collection('bookings').doc(bookingId);

  return db.runTransaction(async (tx) => {
    const eSnap = await tx.get(escrowRef);
    if (!eSnap.exists) throw notFound('Data escrow tidak ditemukan.');
    const escrow = eSnap.data();
    if (escrow.releaseStatus !== 'pending') {
      throw conflict(`Escrow sudah berstatus "${escrow.releaseStatus}".`);
    }

    const bSnap = await tx.get(bookingRef);
    if (!bSnap.exists) throw notFound('Booking tidak ditemukan.');
    const booking = bSnap.data();

    // Hanya boleh dilepas setelah pelanggan mengonfirmasi, atau setelah
    // sengketa diputuskan. Tanpa syarat ini dana bisa lepas untuk pekerjaan
    // yang belum pernah dikerjakan.
    const bolehLepas = ['customer_confirmed', 'disputed'];
    if (!bolehLepas.includes(booking.status)) {
      throw conflict(
        `Dana hanya bisa dilepas saat booking berstatus ${bolehLepas.join(' atau ')}, ` +
        `sekarang "${booking.status}".`
      );
    }

    const walletRef = db.collection('wallets').doc(escrow.creatorId);
    const wSnap = await tx.get(walletRef);
    const saldoSebelum = Number(wSnap.data()?.availableBalance) || 0;
    const totalSebelum = Number(wSnap.data()?.totalEarnings) || 0;
    const nominal = Number(escrow.creatorShare) || 0;
    const saldoSesudah = saldoSebelum + nominal;

    tx.set(walletRef, {
      creatorId: escrow.creatorId,
      availableBalance: saldoSesudah,
      totalEarnings: totalSebelum + nominal,
      updatedAt: FieldValue.serverTimestamp(),
    }, { merge: true });

    // Ledger bersifat immutable — setiap mutasi dicatat sebagai baris baru.
    tx.set(db.collection('wallet_transactions').doc(), {
      creatorId: escrow.creatorId,
      referenceId: bookingId,
      type: 'escrow_release',
      amount: nominal,
      balanceBefore: saldoSebelum,
      balanceAfter: saldoSesudah,
      status: 'success',
      note: reason || 'pelepasan dana setelah pekerjaan dikonfirmasi',
      createdAt: FieldValue.serverTimestamp(),
    });

    tx.update(escrowRef, {
      holdStatus: 'released',
      releaseStatus: 'released',
      releasedAt: FieldValue.serverTimestamp(),
      releasedBy: actor.uid,
    });

    // Bagian platform dari booking ini masuk ke kas platform pada saat yang
    // sama dengan bagian creator. Sebelumnya `platformFee` hanya tercatat di
    // dokumen escrow dan tidak pernah dikumpulkan ke mana pun, sehingga
    // pendapatan platform tidak punya saldo maupun riwayat.
    //
    // Memakai increment, jadi tidak menambah pembacaan pada transaksi ini dan
    // tidak menggagalkan pelepasan dana kalau dokumen kasnya belum pernah ada.
    tambahKomisiPlatform(db, tx, { platformFee: escrow.platformFee, bookingId });

    tx.update(bookingRef, {
      status: 'funds_released',
      fundsReleasedAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    });

    tx.set(db.collection('booking_status_history').doc(), {
      bookingId,
      fromStatus: booking.status,
      toStatus: 'funds_released',
      actorId: actor.uid,
      actorRole: 'admin',
      reason: reason || 'dana dilepas ke creator',
      createdAt: FieldValue.serverTimestamp(),
    });

    writeNotification(db, tx, {
      userId: escrow.creatorId,
      type: 'wallet',
      title: 'Dana dilepas ke saldo',
      body: `Rp${(Number(nominal) || 0).toLocaleString('id-ID')} sudah masuk ke saldo tersedia dan bisa ditarik.`,
      referenceId: bookingId,
    });

    writeAudit(db, actor, {
      action: 'release_escrow',
      targetType: 'booking',
      targetId: bookingId,
      reason,
      metadata: { creatorId: escrow.creatorId, amount: nominal },
    }, tx);

    return { bookingId, creatorId: escrow.creatorId, amount: nominal, status: 'released' };
  });
}

/** Menyelesaikan permintaan refund secara idempotent dari panel admin. */
export async function processRefund(req) {
  const actor = await requireAdmin(req, 'manage_refund');
  const body = readBody(req);
  const refundId = requireString(body.refundId, 'refundId');
  const action = requireOneOf(body.action, 'action', ['approve', 'reject']);
  const reason = typeof body.reason === 'string' ? body.reason.trim() : null;
  const db = adminDb();
  const refundRef = db.collection('refunds').doc(refundId);
  const refundSnap = await refundRef.get();
  if (!refundSnap.exists) throw notFound('Permintaan refund tidak ditemukan.');
  const refund = refundSnap.data();
  if (refund.status !== 'pending') throw conflict(`Refund sudah berstatus "${refund.status}".`);

  if (action === 'reject') {
    await db.runTransaction(async (tx) => {
      const current = await tx.get(refundRef);
      if (!current.exists || current.data().status !== 'pending') throw conflict('Refund sudah diproses admin lain.');
      tx.update(refundRef, {
        status: 'rejected', rejectedReason: reason, processedBy: actor.uid,
        processedAt: FieldValue.serverTimestamp(),
      });
      tx.update(db.collection('bookings').doc(refund.bookingId), {
        status: 'confirmed', updatedAt: FieldValue.serverTimestamp(),
      });
    });
    return { refundId, status: 'rejected' };
  }

  const paymentSnap = await db.collection('payments').where('bookingId', '==', refund.bookingId).limit(1).get();
  const payment = paymentSnap.docs[0]?.data();
  let providerStatus = 'approved_manual';
  if (payment?.provider === 'midtrans' && payment.orderId) {
    const endpoint = process.env.MIDTRANS_ENV === 'production'
      ? `https://app.midtrans.com/v2/${encodeURIComponent(payment.orderId)}/refund`
      : `https://app.sandbox.midtrans.com/v2/${encodeURIComponent(payment.orderId)}/refund`;
    const response = await fetch(endpoint, {
      method: 'POST',
      headers: {
        Authorization: `Basic ${Buffer.from(`${process.env.MIDTRANS_SERVER_KEY || ''}:`).toString('base64')}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ refund_key: refundId, amount: Number(refund.amount) || 0, reason: reason || refund.reason || 'refund admin' }),
    });
    if (!response.ok) throw new HttpError(502, 'refund-provider-error', 'Gateway pembayaran menolak refund. Coba lagi atau proses manual.');
    providerStatus = 'refunded';
  }

  await db.runTransaction(async (tx) => {
    const current = await tx.get(refundRef);
    if (!current.exists || current.data().status !== 'pending') throw conflict('Refund sudah diproses admin lain.');
    tx.update(refundRef, {
      status: providerStatus, processedBy: actor.uid, processedAt: FieldValue.serverTimestamp(),
      providerReference: payment?.orderId || null, adminNote: reason,
    });
    tx.update(db.collection('bookings').doc(refund.bookingId), {
      status: 'cancelled', updatedAt: FieldValue.serverTimestamp(),
    });
    if (paymentSnap.docs[0]) {
      tx.update(paymentSnap.docs[0].ref, { status: 'refunded', refundedAt: FieldValue.serverTimestamp() });
    }
    writeNotification(db, tx, {
      userId: refund.customerId, type: 'refund', title: 'Refund diproses',
      body: providerStatus === 'refunded' ? 'Refund berhasil dikirim ke gateway pembayaran.' : 'Refund disetujui dan perlu diproses manual oleh admin.',
      referenceId: refund.bookingId,
    });
  });
  return { refundId, status: providerStatus };
}

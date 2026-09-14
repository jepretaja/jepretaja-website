import { adminDb, FieldValue } from '../firebaseAdmin.js';
import { requireUser } from '../authUser.js';
import { readBody, requireString, notFound, conflict, forbidden } from '../http.js';

const DEFAULT_BANK = {
  bankName: 'BCA',
  bankAccountNumber: '1234567890',
  bankAccountName: 'PT JepretAja Indonesia',
};

export function computeUniqueCode(bookingId) {
  if (!bookingId || !bookingId.trim()) return 111;
  const seed = [...bookingId].reduce((sum, ch) => sum + ch.charCodeAt(0), 0);
  return ((seed % 900) + 100) % 1000;
}

export function buildTransferInstruction({
  bookingId,
  amount,
  bankName,
  bankAccountNumber,
  bankAccountName,
  expiresAtMs,
}) {
  const uniqueCode = computeUniqueCode(bookingId);
  const transferAmount = Number(amount) + uniqueCode;
  const expiresAt = Number(expiresAtMs || Date.now() + 24 * 60 * 60 * 1000);

  return {
    status: 'awaiting_transfer',
    provider: 'transfer_manual',
    method: 'bank_transfer',
    uniqueCode,
    transferAmount,
    amount: Number(amount),
    bankName: bankName || DEFAULT_BANK.bankName,
    bankAccountNumber: bankAccountNumber || DEFAULT_BANK.bankAccountNumber,
    bankAccountName: bankAccountName || DEFAULT_BANK.bankAccountName,
    expiresAt,
    instruction: `Transfer sebesar Rp ${transferAmount.toLocaleString('id-ID')} ke rekening ${bankName || DEFAULT_BANK.bankName} ${bankAccountNumber || DEFAULT_BANK.bankAccountNumber} a.n. ${bankAccountName || DEFAULT_BANK.bankAccountName}. Masukkan kode unik ${uniqueCode} agar pembayaran bisa dicocokkan.`,
  };
}

/**
 * Membuat instruksi transfer manual untuk booking.
 *
 * Server tidak lagi membuat transaksi Midtrans di backend ini. Flow yang
 * dipakai aplikasi dan admin web adalah transfer manual: pelanggan membayar
 * ke rekening yang ditetapkan di settings/general, lalu admin memverifikasi
 * di panel web dengan aksi confirmManualPayment.
 */
export async function createPaymentOrder(req) {
  const actor = await requireUser(req);
  const body = readBody(req);
  const bookingId = requireString(body.bookingId, 'bookingId');

  const db = adminDb();
  const bookingRef = db.collection('bookings').doc(bookingId);
  const bookingSnap = await bookingRef.get();
  if (!bookingSnap.exists) throw notFound('Booking tidak ditemukan.');

  const booking = bookingSnap.data();
  if (booking.customerId !== actor.uid) throw forbidden('Booking ini bukan milik Anda.');
  if (booking.status !== 'pending_payment') {
    throw conflict(`Booking berstatus "${booking.status}", tidak menunggu pembayaran.`);
  }

  if (booking.paymentId) {
    const paymentRef = db.collection('payments').doc(booking.paymentId);
    const existing = await paymentRef.get();
    const old = existing.data();
    if (old?.provider === 'transfer_manual' && ['awaiting_transfer', 'paid', 'rejected'].includes(old.status)) {
      return buildTransferInstruction({
        bookingId,
        amount: Number(old.amount) || Number(booking.total) || 0,
        bankName: old.bankName || DEFAULT_BANK.bankName,
        bankAccountNumber: old.bankAccountNumber || DEFAULT_BANK.bankAccountNumber,
        bankAccountName: old.bankAccountName || DEFAULT_BANK.bankAccountName,
        expiresAtMs: old.expiresAt?.toMillis?.() ?? Date.now() + 24 * 60 * 60 * 1000,
      });
    }
    throw conflict('Pembayaran untuk booking ini sedang diproses. Tunggu sebentar lalu coba lagi.');
  }

  const amount = Number(booking.total) || 0;
  if (amount <= 0) throw conflict('Total booking tidak valid untuk pembayaran.');

  const settingsSnap = await db.collection('settings').doc('general').get();
  const settings = settingsSnap.data() || {};
  const bankName = (settings.payoutBankName || '').trim() || DEFAULT_BANK.bankName;
  const bankAccountNumber = (settings.payoutAccountNumber || '').trim() || DEFAULT_BANK.bankAccountNumber;
  const bankAccountName = (settings.payoutAccountName || '').trim() || DEFAULT_BANK.bankAccountName;

  if (!bankAccountNumber || bankAccountNumber === '1234567890' || !bankAccountName || bankAccountName === 'PT JepretAja Indonesia') {
    throw conflict('Rekening tujuan transfer manual belum dikonfigurasi. Hubungi admin untuk mengisi pengaturan pembayaran.');
  }

  const paymentRef = db.collection('payments').doc(`booking_${bookingId}`);
  const expiresAt = Date.now() + 24 * 60 * 60 * 1000;
  const instruction = buildTransferInstruction({
    bookingId,
    amount,
    bankName,
    bankAccountNumber,
    bankAccountName,
    expiresAtMs: expiresAt,
  });

  await db.runTransaction(async (tx) => {
    const latestBooking = await tx.get(bookingRef);
    const latest = latestBooking.data();
    if (!latest || latest.customerId !== actor.uid) throw forbidden('Booking ini bukan milik Anda.');
    if (latest.status !== 'pending_payment') throw conflict(`Booking berstatus "${latest.status}", tidak menunggu pembayaran.`);

    tx.set(paymentRef, {
      paymentId: paymentRef.id,
      bookingId,
      customerId: actor.uid,
      creatorId: latest.creatorId,
      provider: 'transfer_manual',
      method: 'bank_transfer',
      amount,
      uniqueCode: instruction.uniqueCode,
      transferAmount: instruction.transferAmount,
      bankName,
      bankAccountNumber,
      bankAccountName,
      status: 'awaiting_transfer',
      expiresAt: instruction.expiresAt,
      createdAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    });

    tx.update(bookingRef, {
      paymentId: paymentRef.id,
      updatedAt: FieldValue.serverTimestamp(),
    });
  });

  return {
    paymentId: paymentRef.id,
    bookingId,
    amount,
    uniqueCode: instruction.uniqueCode,
    transferAmount: instruction.transferAmount,
    bankName,
    bankAccountNumber,
    bankAccountName,
    expiresAt: instruction.expiresAt,
    instruction: instruction.instruction,
    provider: 'transfer_manual',
    status: 'awaiting_transfer',
  };
}

import { adminDb, FieldValue } from '../firebaseAdmin.js';
import { requireUser } from '../authUser.js';
import { readBody, requireString, notFound, conflict, forbidden } from '../http.js';

/**
 * Membuat order pembayaran untuk sebuah booking.
 *
 * Saat ini memakai alur TRANSFER MANUAL: server menerbitkan instruksi
 * transfer beserta kode unik, lalu admin memverifikasi bukti transfer di
 * panel web. Payment gateway (Midtrans/Xendit) belum tersambung.
 *
 * Nilai yang ditagih diambil dari dokumen booking di Firestore, tidak
 * pernah dari body request — kalau tidak, nominal bisa diubah dari sisi
 * aplikasi menjadi berapa pun.
 */
export async function createPaymentOrder(req) {
  const actor = await requireUser(req);
  const body = readBody(req);
  const bookingId = requireString(body.bookingId, 'bookingId');

  const db = adminDb();
  const bookingRef = db.collection('bookings').doc(bookingId);
  const paymentRef = db.collection('payments').doc();

  const hasil = await db.runTransaction(async (tx) => {
    const snap = await tx.get(bookingRef);
    if (!snap.exists) throw notFound('Booking tidak ditemukan.');
    const booking = snap.data();

    if (booking.customerId !== actor.uid) throw forbidden('Booking ini bukan milik Anda.');
    if (booking.status !== 'pending_payment') {
      throw conflict(`Booking berstatus "${booking.status}", tidak menunggu pembayaran.`);
    }

    // Kalau sudah pernah dibuat dan masih berlaku, kembalikan yang lama
    // supaya pengguna tidak mendapat kode unik berbeda setiap kali menekan
    // tombol bayar — itu membuat nominal transfer jadi tidak cocok.
    const adaQ = db.collection('payments')
      .where('bookingId', '==', bookingId)
      .where('status', '==', 'awaiting_transfer');
    const ada = await tx.get(adaQ);
    if (!ada.empty) {
      const paymentDoc = ada.docs[0];
      const lama = paymentDoc.data();
      const expiresAtMillis = lama.expiresAt?.toMillis?.() ?? new Date(lama.expiresAt || 0).getTime();
      if (expiresAtMillis > Date.now()) {
        return { paymentId: paymentDoc.id, ...lama, reused: true };
      }

      // Jangan mengembalikan instruksi transfer yang sudah kedaluwarsa.
      // Tandai order lama agar riwayat pembayaran tetap audit-able, lalu
      // lanjutkan membuat instruksi baru dalam transaksi yang sama.
      tx.update(paymentDoc.ref, {
        status: 'expired',
        expiredAt: FieldValue.serverTimestamp(),
      });
    }

    // settings/general — dokumen yang sama dengan form Pengaturan di panel
    // admin. Dulu membaca settings/platform, jadi nomor rekening tujuan
    // transfer selalu kosong dan instruksinya tampil sebagai "BCA / -".
    const settings = await tx.get(db.collection('settings').doc('general'));
    const s = settings.data() || {};

    // Kode unik 3 digit untuk mencocokkan transfer masuk secara otomatis.
    const kodeUnik = Math.floor(Math.random() * 900) + 100;
    const jumlahTransfer = (Number(booking.total) || 0) + kodeUnik;
    const kedaluwarsa = new Date(Date.now() + 24 * 60 * 60 * 1000);

    const payment = {
      paymentId: paymentRef.id,
      bookingId,
      customerId: actor.uid,
      creatorId: booking.creatorId,
      method: 'manual_transfer',
      amount: Number(booking.total) || 0,
      uniqueCode: kodeUnik,
      transferAmount: jumlahTransfer,
      bankName: s.payoutBankName || 'BCA',
      bankAccountNumber: s.payoutAccountNumber || '-',
      bankAccountName: s.payoutAccountName || 'JepretAja',
      status: 'awaiting_transfer',
      expiresAt: kedaluwarsa,
      createdAt: FieldValue.serverTimestamp(),
    };

    tx.set(paymentRef, payment);
    tx.update(bookingRef, { paymentId: paymentRef.id, updatedAt: FieldValue.serverTimestamp() });
    return { ...payment, reused: false };
  });

  return {
    paymentId: hasil.paymentId,
    method: 'manual_transfer',
    amount: hasil.amount,
    uniqueCode: hasil.uniqueCode,
    transferAmount: hasil.transferAmount,
    bankName: hasil.bankName,
    bankAccountNumber: hasil.bankAccountNumber,
    bankAccountName: hasil.bankAccountName,
    expiresAt: hasil.expiresAt?.toMillis ? hasil.expiresAt.toMillis() : new Date(hasil.expiresAt).getTime(),
    instruction: `Transfer tepat Rp${hasil.transferAmount.toLocaleString('id-ID')} ` +
      `(termasuk kode unik ${hasil.uniqueCode}) agar pembayaran terverifikasi otomatis.`,
  };
}

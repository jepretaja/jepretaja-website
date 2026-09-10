import { adminDb, FieldValue } from '../firebaseAdmin.js';
import { requireUser } from '../authUser.js';
import { readBody, requireString, notFound, conflict, forbidden, HttpError } from '../http.js';

const MIDTRANS_ENDPOINT = process.env.MIDTRANS_ENV === 'production'
  ? 'https://app.midtrans.com/snap/v1/transactions'
  : 'https://app.sandbox.midtrans.com/snap/v1/transactions';

function midtransAuth() {
  const key = process.env.MIDTRANS_SERVER_KEY;
  if (!key) throw new HttpError(500, 'midtrans-not-configured', 'Pembayaran sedang dikonfigurasi. Coba lagi nanti.');
  return `Basic ${Buffer.from(`${key}:`).toString('base64')}`;
}

/**
 * Membuat order pembayaran untuk sebuah booking.
 *
 * Membuat transaksi Midtrans Snap. Server Key hanya berada di Vercel; aplikasi
 * menerima snapToken/redirectUrl dan tidak pernah menerima kredensial rahasia.
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
  const snap = await bookingRef.get();
  if (!snap.exists) throw notFound('Booking tidak ditemukan.');
  const booking = snap.data();
  if (booking.customerId !== actor.uid) throw forbidden('Booking ini bukan milik Anda.');
  if (booking.status !== 'pending_payment') throw conflict(`Booking berstatus "${booking.status}", tidak menunggu pembayaran.`);

  if (booking.paymentId) {
    const existing = await db.collection('payments').doc(booking.paymentId).get();
    const old = existing.data();
    if (old?.provider === 'midtrans' && old.snapToken && ['pending', 'settlement', 'capture'].includes(old.status)) {
      return { paymentId: existing.id, orderId: old.orderId, amount: old.amount, snapToken: old.snapToken, redirectUrl: old.redirectUrl, clientKey: process.env.MIDTRANS_CLIENT_KEY || '' };
    }
  }

  const paymentRef = db.collection('payments').doc();
  const orderId = `JEP-${bookingId}-${paymentRef.id.slice(0, 8)}`;
  const amount = Number(booking.total) || 0;
  if (amount <= 0) throw conflict('Total booking tidak valid untuk pembayaran.');

  const response = await fetch(MIDTRANS_ENDPOINT, {
    method: 'POST',
    headers: { Authorization: midtransAuth(), 'Content-Type': 'application/json', Accept: 'application/json' },
    body: JSON.stringify({
      transaction_details: { order_id: orderId, gross_amount: amount },
      item_details: [{ id: booking.packageId || bookingId, price: amount, quantity: 1, name: booking.packageName || 'Booking JepretAja' }],
      customer_details: { first_name: actor.email?.split('@')[0] || 'Pelanggan', email: actor.email || undefined },
      callbacks: { finish: `${process.env.APP_PUBLIC_URL || ''}/payment/${bookingId}/result` },
    }),
  });
  const result = await response.json().catch(() => ({}));
  if (!response.ok || !result.token) {
    throw new HttpError(502, 'midtrans-error', result.error_messages?.join(' ') || 'Midtrans gagal membuat transaksi.');
  }

  const payment = {
    paymentId: paymentRef.id, bookingId, customerId: actor.uid, creatorId: booking.creatorId,
    provider: 'midtrans', method: 'snap', orderId, amount,
    snapToken: result.token, redirectUrl: result.redirect_url || null,
    status: 'pending', expiresAt: new Date(Date.now() + 24 * 60 * 60 * 1000),
    createdAt: FieldValue.serverTimestamp(),
  };
  await paymentRef.set(payment);
  await bookingRef.update({ paymentId: paymentRef.id, updatedAt: FieldValue.serverTimestamp() });

  return { paymentId: paymentRef.id, orderId, amount, snapToken: result.token, redirectUrl: result.redirect_url || null, clientKey: process.env.MIDTRANS_CLIENT_KEY || '' };
}

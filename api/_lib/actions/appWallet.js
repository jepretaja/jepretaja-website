import { adminDb, FieldValue } from '../firebaseAdmin.js';
import { requireCreator } from '../authUser.js';
import { readBody, requireString, badRequest, conflict, notFound } from '../http.js';

const MIN_WITHDRAWAL_DEFAULT = 100000;

/**
 * Creator mengajukan penarikan saldo.
 *
 * Aksi ini ada di server, bukan di APK, karena firestore.rules menutup total
 * penulisan ke koleksi `withdrawals` (`allow write: if false`) — sama seperti
 * seluruh koleksi finansial lain. Sebelumnya aplikasi memanggil
 * `withdrawals.add(...)` langsung dan SELALU ditolak PERMISSION_DENIED, jadi
 * tombol "Ajukan Penarikan" tidak pernah menghasilkan apa pun.
 *
 * Yang diperiksa di sini dan tidak bisa diperiksa di security rules:
 * - nominal minimum dari settings/general,
 * - saldo available yang benar-benar dimiliki creator,
 * - tidak ada pengajuan lain yang masih berjalan.
 *
 * Saldo TIDAK dipotong di tahap ini. Pemotongan terjadi saat admin menekan
 * Approve (processWithdrawal), supaya penolakan tidak perlu mengembalikan
 * apa pun.
 */
export async function requestWithdrawal(req) {
  const actor = await requireCreator(req);
  const body = readBody(req);

  const amount = Math.floor(Number(body.amount) || 0);
  if (amount <= 0) throw badRequest('Nominal penarikan tidak valid.');

  const bankCode = requireString(body.bankCode, 'bankCode');
  const bankAccountNumber = requireString(body.bankAccountNumber, 'bankAccountNumber');
  const bankAccountName = requireString(body.bankAccountName, 'bankAccountName');

  const db = adminDb();
  const wRef = db.collection('withdrawals').doc();

  await db.runTransaction(async (tx) => {
    const settings = await tx.get(db.collection('settings').doc('general'));
    const minWithdrawal = Number(settings.data()?.minWithdrawal) || MIN_WITHDRAWAL_DEFAULT;
    if (amount < minWithdrawal) {
      throw badRequest(`Penarikan minimal Rp${minWithdrawal.toLocaleString('id-ID')}.`);
    }

    const walletSnap = await tx.get(db.collection('wallets').doc(actor.uid));
    if (!walletSnap.exists) {
      throw notFound('Saldo Anda masih kosong, belum ada dana yang bisa ditarik.');
    }
    const tersedia = Number(walletSnap.data().availableBalance) || 0;
    if (tersedia < amount) {
      throw conflict(`Saldo tersedia hanya Rp${tersedia.toLocaleString('id-ID')}.`);
    }

    // Satu pengajuan berjalan pada satu waktu. Tanpa ini creator bisa
    // mengajukan tiga penarikan sebesar seluruh saldonya sekaligus; masing
    // masing lolos pemeriksaan karena saldo baru dipotong saat approve.
    const berjalan = await tx.get(
      db.collection('withdrawals')
        .where('creatorId', '==', actor.uid)
        .where('status', 'in', ['requested', 'processing'])
        .limit(1)
    );
    if (!berjalan.empty) {
      throw conflict('Masih ada pengajuan penarikan yang sedang diproses.');
    }

    tx.set(wRef, {
      withdrawalId: wRef.id,
      creatorId: actor.uid,
      amount,
      bankCode,
      bankAccountNumber,
      bankAccountName,
      status: 'requested',
      createdAt: FieldValue.serverTimestamp(),
    });
  });

  return { withdrawalId: wRef.id, status: 'requested', amount };
}

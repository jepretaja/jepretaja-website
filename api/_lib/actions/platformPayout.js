import { adminDb, FieldValue } from '../firebaseAdmin.js';
import { requireAdmin } from '../rbac.js';
import { writeAudit } from '../audit.js';
import { readBody, requireString, badRequest, conflict, notFound, forbidden } from '../http.js';

/**
 * Kas platform: komisi yang terkumpul dari setiap booking, dan pencairannya
 * ke rekening pemilik.
 *
 * LATAR BELAKANGNYA. `platformFee` sudah lama dihitung per booking dan
 * dicatat di dokumen escrow, tapi saat dana dilepas hanya `creatorShare` yang
 * berpindah ke dompet creator — komisinya tidak pernah dikumpulkan ke mana pun.
 * Akibatnya pendapatan platform hanya berupa angka yang tersebar di ratusan
 * dokumen escrow, tanpa saldo, tanpa riwayat, dan tanpa satu pun cara
 * mencairkannya.
 *
 * DUA LAPIS ANGKA, DISENGAJA:
 *
 *  1. `platform_wallet/main` — saldo berjalan, dinaikkan langsung oleh
 *     releaseEscrow setiap kali dana dilepas. Ini yang dibaca halaman admin
 *     supaya angkanya hidup tanpa menghitung ulang apa pun.
 *  2. `syncPlatformRevenue` — menghitung ULANG dari sumber aslinya (seluruh
 *     escrow yang sudah released dikurangi seluruh pencairan yang sah).
 *
 * Yang kedua ada karena yang pertama bisa meleset: escrow yang sudah dilepas
 * SEBELUM fitur ini dibuat tidak pernah menaikkan saldo mana pun, dan setiap
 * penambahan berjalan selalu punya risiko terlewat. Angka uang yang tidak bisa
 * dibuktikan ulang dari catatan aslinya bukan angka yang layak dipercaya —
 * jadi hitungan ulang itu tersedia sebagai tombol, bukan sebagai pekerjaan
 * yang harus dilakukan lewat database.
 *
 * SIAPA YANG BOLEH. Hanya `super_admin`, lewat izin `manage_platform_payout`
 * yang tidak diberikan ke role lain mana pun. Ini satu-satunya aksi di sistem
 * ini yang memindahkan uang KELUAR dari platform, bukan memindahkannya antar
 * pihak yang memang sudah berhak.
 */

const WALLET_COLLECTION = 'platform_wallet';
const WALLET_DOC = 'main';
const PAYOUT_COLLECTION = 'platform_payouts';

/** Pencairan yang masih dianggap "uang sudah keluar". Yang dibatalkan tidak
 *  ikut, karena nominalnya sudah dikembalikan ke saldo. */
const PAYOUT_TERPAKAI = ['paid', 'pending'];

/**
 * Gerbang bersama untuk seluruh aksi di berkas ini.
 *
 * Peran diperiksa DUA KALI: lewat peta izin, dan lewat nama peran secara
 * langsung. Pemeriksaan kedua terlihat berlebihan dan memang disengaja —
 * kalau suatu hari `manage_platform_payout` tidak sengaja ikut tersalin ke
 * role lain saat menyunting peta izin, kas platform tidak langsung ikut
 * terbuka bersamanya.
 */
async function requireSuperAdmin(req) {
  const actor = await requireAdmin(req, 'manage_platform_payout');
  if (actor.role !== 'super_admin') {
    throw forbidden('Hanya super admin yang boleh mengakses kas platform.');
  }
  return actor;
}

function walletRef(db) {
  return db.collection(WALLET_COLLECTION).doc(WALLET_DOC);
}

/**
 * Dipanggil dari dalam transaksi releaseEscrow.
 *
 * Sengaja memakai FieldValue.increment: nilainya tidak perlu dibaca lebih dulu,
 * sehingga menambahkannya ke transaksi yang sudah ada tidak menambah satu pun
 * pembacaan — dan tidak mengubah perilaku pelepasan dana kalau dokumen kas
 * platform belum pernah ada.
 */
export function tambahKomisiPlatform(db, tx, { platformFee, bookingId }) {
  const nominal = Math.max(0, Math.floor(Number(platformFee) || 0));
  if (!nominal) return 0;

  tx.set(walletRef(db), {
    totalRevenue: FieldValue.increment(nominal),
    availableBalance: FieldValue.increment(nominal),
    lastBookingId: bookingId || null,
    updatedAt: FieldValue.serverTimestamp(),
  }, { merge: true });

  return nominal;
}

/**
 * Simpan rekening pencairan milik super admin yang sedang login.
 *
 * Disimpan di `admin_users/{uid}.payoutAccount` — bukan di `settings`, karena
 * ini rekening PRIBADI orangnya, bukan setelan platform. Bedanya penting:
 * dokumen settings dibaca publik (`allow read: if true`, dipakai aplikasi
 * untuk menampilkan instruksi transfer), sementara admin_users hanya bisa
 * dibaca pemiliknya sendiri dan sesama admin.
 *
 * Harus lewat server karena firestore.rules menutup total penulisan ke
 * admin_users (`allow write: if false`) — peran dan status admin tidak boleh
 * bisa disunting dari browser, dan pengecualian untuk satu field akan
 * membuka pintu yang sama.
 */
export async function savePlatformPayoutAccount(req) {
  const actor = await requireSuperAdmin(req);
  const body = readBody(req);

  const bankCode = requireString(body.bankCode, 'bankCode');
  const bankAccountName = requireString(body.bankAccountName, 'bankAccountName');
  const nomor = requireString(body.bankAccountNumber, 'bankAccountNumber').replace(/[\s-]/g, '');
  if (!/^\d{6,20}$/.test(nomor)) {
    throw badRequest('Nomor rekening harus 6–20 angka, tanpa huruf.');
  }

  const db = adminDb();
  await db.collection('admin_users').doc(actor.uid).set({
    payoutAccount: {
      bankCode,
      bankAccountNumber: nomor,
      bankAccountName: bankAccountName.trim(),
      updatedAt: FieldValue.serverTimestamp(),
    },
  }, { merge: true });

  // Ikut dicatat: perubahan rekening tujuan uang keluar sama pentingnya
  // dengan pencairannya sendiri. Tanpa jejak ini, rekening bisa diganti
  // sesaat sebelum pencairan tanpa meninggalkan bekas apa pun.
  await writeAudit(db, actor, {
    action: 'update_platform_payout_account',
    targetType: 'admin_user',
    targetId: actor.uid,
    metadata: { bankCode, bankAccountNumber: nomor, bankAccountName: bankAccountName.trim() },
  });

  return { bankCode, bankAccountNumber: nomor, bankAccountName: bankAccountName.trim() };
}

/**
 * Hitung ulang kas platform dari catatan aslinya.
 *
 * Membaca SELURUH escrow yang sudah dilepas dan seluruh pencairan — mahal,
 * tapi hanya berjalan saat tombolnya ditekan, dan justru ketelitiannya yang
 * jadi alasan aksi ini ada.
 */
export async function syncPlatformRevenue(req) {
  const actor = await requireSuperAdmin(req);
  const db = adminDb();

  const escrowSnap = await db.collection('escrow_transactions')
    .where('releaseStatus', '==', 'released')
    .get();
  const totalRevenue = escrowSnap.docs.reduce(
    (jumlah, d) => jumlah + (Math.floor(Number(d.data().platformFee) || 0)), 0
  );

  const payoutSnap = await db.collection(PAYOUT_COLLECTION).get();
  const totalPaidOut = payoutSnap.docs
    .filter((d) => PAYOUT_TERPAKAI.includes(d.data().status))
    .reduce((jumlah, d) => jumlah + (Math.floor(Number(d.data().amount) || 0)), 0);

  const availableBalance = totalRevenue - totalPaidOut;

  await walletRef(db).set({
    totalRevenue,
    totalPaidOut,
    availableBalance,
    escrowDihitung: escrowSnap.size,
    pencairanDihitung: payoutSnap.size,
    lastSyncAt: FieldValue.serverTimestamp(),
    lastSyncBy: actor.uid,
    updatedAt: FieldValue.serverTimestamp(),
  }, { merge: true });

  await writeAudit(db, actor, {
    action: 'sync_platform_revenue',
    targetType: 'platform_wallet',
    targetId: WALLET_DOC,
    metadata: { totalRevenue, totalPaidOut, availableBalance },
  });

  return { totalRevenue, totalPaidOut, availableBalance, escrowDihitung: escrowSnap.size };
}

/**
 * Catat pencairan kas platform ke rekening pemilik.
 *
 * Transfer banknya sendiri dilakukan manual di luar sistem — platform ini
 * tidak tersambung ke gerbang pembayaran keluar. Yang dikerjakan di sini
 * adalah pembukuannya: saldo dipotong, satu baris riwayat dibuat, dan audit
 * log ditulis dengan nominal serta rekening tujuannya.
 *
 * CATATAN KEJUJURAN SOAL PENGAMANNYA. Karena penerimanya adalah orang yang
 * sama dengan yang menyetujui, satu-satunya pengaman di sini adalah jejak
 * yang tidak bisa dihapus dari panel: setiap pencairan tercatat di audit log
 * beserta pelakunya, dan koleksi audit tertutup untuk penulisan dari klien.
 * Kalau kelak super admin lebih dari satu, langkah berikutnya yang wajar
 * adalah mensyaratkan persetujuan super admin kedua sebelum status menjadi
 * 'paid'.
 */
export async function withdrawPlatformBalance(req) {
  const actor = await requireSuperAdmin(req);
  const body = readBody(req);

  const amount = Math.floor(Number(body.amount) || 0);
  if (amount <= 0) throw badRequest('Nominal pencairan tidak valid.');

  const bankCode = requireString(body.bankCode, 'bankCode');
  const bankAccountNumber = requireString(body.bankAccountNumber, 'bankAccountNumber');
  const bankAccountName = requireString(body.bankAccountName, 'bankAccountName');
  const note = typeof body.note === 'string' ? body.note.trim().slice(0, 300) : '';

  const db = adminDb();
  const payoutRef = db.collection(PAYOUT_COLLECTION).doc();

  const hasil = await db.runTransaction(async (tx) => {
    const snap = await tx.get(walletRef(db));
    if (!snap.exists) {
      throw notFound('Kas platform belum pernah dihitung. Tekan "Hitung Ulang" lebih dulu.');
    }

    const tersedia = Math.floor(Number(snap.data().availableBalance) || 0);
    if (tersedia <= 0) throw conflict('Tidak ada saldo platform yang bisa dicairkan.');
    if (amount > tersedia) {
      throw conflict(`Saldo platform hanya Rp${tersedia.toLocaleString('id-ID')}.`);
    }

    tx.set(payoutRef, {
      payoutId: payoutRef.id,
      amount,
      bankCode,
      bankAccountNumber,
      bankAccountName,
      note: note || null,
      // Tidak ada tahap "menunggu" karena tidak ada pihak lain yang menyetujui:
      // begitu dicatat, uangnya dianggap sudah keluar. Kekeliruan dibereskan
      // dengan cancelPlatformPayout, yang meninggalkan jejaknya sendiri.
      status: 'paid',
      requestedBy: actor.uid,
      requestedByEmail: actor.email || null,
      saldoSebelum: tersedia,
      saldoSesudah: tersedia - amount,
      createdAt: FieldValue.serverTimestamp(),
    });

    tx.set(walletRef(db), {
      availableBalance: FieldValue.increment(-amount),
      totalPaidOut: FieldValue.increment(amount),
      updatedAt: FieldValue.serverTimestamp(),
    }, { merge: true });

    return { saldoSesudah: tersedia - amount };
  });

  await writeAudit(db, actor, {
    action: 'withdraw_platform_balance',
    targetType: 'platform_payout',
    targetId: payoutRef.id,
    reason: note || null,
    metadata: { amount, bankCode, bankAccountNumber, bankAccountName, saldoSesudah: hasil.saldoSesudah },
  });

  return { payoutId: payoutRef.id, amount, status: 'paid', availableBalance: hasil.saldoSesudah };
}

/** Membatalkan pencairan yang telanjur dicatat — nominalnya dikembalikan ke
 *  saldo. Barisnya TIDAK dihapus, hanya ditandai, supaya riwayatnya tetap utuh. */
export async function cancelPlatformPayout(req) {
  const actor = await requireSuperAdmin(req);
  const body = readBody(req);
  const payoutId = requireString(body.payoutId, 'payoutId');
  const reason = typeof body.reason === 'string' ? body.reason.trim().slice(0, 300) : '';

  const db = adminDb();
  const payoutRef = db.collection(PAYOUT_COLLECTION).doc(payoutId);

  const nominal = await db.runTransaction(async (tx) => {
    const snap = await tx.get(payoutRef);
    if (!snap.exists) throw notFound('Catatan pencairan tidak ditemukan.');
    const data = snap.data();
    if (!PAYOUT_TERPAKAI.includes(data.status)) {
      throw conflict(`Pencairan ini sudah berstatus "${data.status}".`);
    }
    const amount = Math.floor(Number(data.amount) || 0);

    tx.update(payoutRef, {
      status: 'cancelled',
      cancelReason: reason || null,
      cancelledBy: actor.uid,
      cancelledAt: FieldValue.serverTimestamp(),
    });

    tx.set(walletRef(db), {
      availableBalance: FieldValue.increment(amount),
      totalPaidOut: FieldValue.increment(-amount),
      updatedAt: FieldValue.serverTimestamp(),
    }, { merge: true });

    return amount;
  });

  await writeAudit(db, actor, {
    action: 'cancel_platform_payout',
    targetType: 'platform_payout',
    targetId: payoutId,
    reason: reason || null,
    metadata: { amount: nominal },
  });

  return { payoutId, status: 'cancelled', amount: nominal };
}

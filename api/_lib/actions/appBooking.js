import { adminDb, FieldValue, Timestamp } from '../firebaseAdmin.js';
import { requireUser } from '../authUser.js';
import { readBody, requireString, badRequest, notFound, conflict, forbidden } from '../http.js';
import { BOOKING_TRANSITIONS } from './bookings.js';
import { writeNotification, JUDUL_STATUS } from '../notify.js';

const PLATFORM_FEE_PERCENT_DEFAULT = 10;

/**
 * Menghitung rincian harga DI SERVER.
 *
 * Perhitungan tidak boleh dilakukan di aplikasi: APK bisa dibongkar dan
 * angkanya diubah, sehingga seseorang bisa memesan paket Rp12 juta dengan
 * total Rp0. Karena itu harga paket, add-on, dan voucher semuanya dibaca
 * ulang dari Firestore di sini — nilai yang dikirim aplikasi diabaikan
 * kecuali biaya perjalanan yang memang ditentukan kesepakatan.
 */
async function hitungHarga(db, { packageId, addOnIds = [], travelFee = 0, voucherCode = null }) {
  const pkgSnap = await db.collection('packages').doc(packageId).get();
  if (!pkgSnap.exists) throw notFound('Paket tidak ditemukan.');
  const pkg = pkgSnap.data();
  if (pkg.active === false) throw conflict('Paket ini sedang tidak aktif.');

  const packagePrice = Number(pkg.price) || 0;

  let addOnsTotal = 0;
  const addOnDetail = [];
  if (Array.isArray(addOnIds) && addOnIds.length) {
    const addOns = pkg.addOns || [];
    for (const id of addOnIds) {
      const found = addOns.find((a) => a.id === id);
      if (!found) throw badRequest(`Add-on "${id}" tidak tersedia pada paket ini.`);
      addOnsTotal += Number(found.price) || 0;
      addOnDetail.push({ id, name: found.name || '', price: Number(found.price) || 0 });
    }
  }

  const travel = Math.max(0, Number(travelFee) || 0);

  // Voucher divalidasi di server: masa berlaku, kuota, dan nilai potongan
  // semuanya dari Firestore.
  let discount = 0;
  let voucherApplied = null;
  if (voucherCode) {
    const vSnap = await db.collection('promotions').doc(String(voucherCode)).get();
    if (!vSnap.exists) throw badRequest('Kode voucher tidak ditemukan.');
    const v = vSnap.data();
    const now = Date.now();
    const mulai = v.startsAt?.toMillis?.() ?? 0;
    const selesai = v.endsAt?.toMillis?.() ?? Number.MAX_SAFE_INTEGER;
    if (v.active === false) throw badRequest('Voucher sudah tidak aktif.');
    if (now < mulai || now > selesai) throw badRequest('Voucher di luar masa berlaku.');
    if (typeof v.quota === 'number' && (v.usedCount || 0) >= v.quota) {
      throw badRequest('Kuota voucher sudah habis.');
    }
    const dasar = packagePrice + addOnsTotal;
    discount = v.type === 'percent'
      ? Math.floor((dasar * (Number(v.value) || 0)) / 100)
      : Math.min(Number(v.value) || 0, dasar);
    if (typeof v.maxDiscount === 'number') discount = Math.min(discount, v.maxDiscount);
    voucherApplied = String(voucherCode);
  }

  const subtotal = Math.max(0, packagePrice + addOnsTotal + travel - discount);

  // Dokumennya 'general', SAMA dengan yang ditulis panel admin di
  // src/pages/settings/Settings.jsx. Sebelumnya server membaca
  // settings/platform yang tidak pernah ditulis siapa pun, sehingga fee yang
  // diatur admin diam-diam diabaikan dan selalu jatuh ke default 10%.
  const settings = await db.collection('settings').doc('general').get();
  const platformFeePercent = Number(settings.data()?.platformFeePercent) || PLATFORM_FEE_PERCENT_DEFAULT;
  const platformFee = Math.floor((subtotal * platformFeePercent) / 100);

  return {
    breakdown: {
      packagePrice,
      addOnsTotal,
      travelFee: travel,
      discount,
      voucherCode: voucherApplied,
      platformFeePercent,
      platformFee,
      subtotal,
    },
    addOnDetail,
    // Yang dibayar pelanggan adalah subtotal; platformFee adalah potongan
    // untuk platform yang diambil dari bagian creator, bukan tambahan biaya.
    total: subtotal,
    pkg,
  };
}

export async function previewBookingPrice(req) {
  await requireUser(req);
  const body = readBody(req);
  const db = adminDb();
  const hasil = await hitungHarga(db, {
    packageId: requireString(body.packageId, 'packageId'),
    addOnIds: body.addOnIds,
    travelFee: body.travelFee,
    voucherCode: body.voucherCode || null,
  });
  return { total: hasil.total, priceBreakdown: hasil.breakdown, addOns: hasil.addOnDetail };
}

export async function createBooking(req) {
  const actor = await requireUser(req);
  const body = readBody(req);
  const db = adminDb();

  const packageId = requireString(body.packageId, 'packageId');
  const tanggalIso = requireString(body.date, 'date');
  const time = requireString(body.time, 'time');
  const location = requireString(body.location, 'location');
  const latitude = body.latitude == null ? null : Number(body.latitude);
  const longitude = body.longitude == null ? null : Number(body.longitude);
  if ((latitude != null && !Number.isFinite(latitude)) || (longitude != null && !Number.isFinite(longitude))) {
    throw badRequest('Koordinat lokasi tidak valid.');
  }
  const note = typeof body.note === 'string' ? body.note.trim() : null;

  const tanggal = new Date(tanggalIso);
  if (Number.isNaN(tanggal.getTime())) throw badRequest('Format tanggal tidak valid.');
  // Batas hari ini, bukan jam sekarang — memesan untuk hari ini masih wajar.
  const awalHariIni = new Date();
  awalHariIni.setHours(0, 0, 0, 0);
  if (tanggal < awalHariIni) throw badRequest('Tanggal acara sudah lewat.');

  const hasil = await hitungHarga(db, {
    packageId,
    addOnIds: body.addOnIds,
    travelFee: body.travelFee,
    voucherCode: body.voucherCode || null,
  });

  const creatorId = hasil.pkg.creatorId;
  if (!creatorId) throw conflict('Paket ini tidak terhubung ke creator mana pun.');
  if (creatorId === actor.uid) throw forbidden('Anda tidak bisa memesan paket milik sendiri.');

  const creatorSnap = await db.collection('creators').doc(creatorId).get();
  if (!creatorSnap.exists || creatorSnap.data().status !== 'active') {
    throw conflict('Creator sedang tidak menerima pesanan.');
  }

  // Saklar "menerima booking" milik creator ditegakkan di sini, bukan cuma
  // dengan menyembunyikan tombol di aplikasi. Kalau hanya tombolnya yang
  // hilang, pesanan tetap bisa masuk lewat panggilan langsung ke API, dan
  // creator yang sedang libur tetap menerima jadwal yang tidak bisa ia penuhi.
  const dataCreator = creatorSnap.data();
  if (dataCreator.acceptingBookings === false) {
    const sampai = dataCreator.awayUntil?.toDate?.();
    throw conflict(
      sampai
        ? `Creator sedang tidak menerima booking sampai ${sampai.toLocaleDateString('id-ID')}.`
        : 'Creator sedang tidak menerima booking baru.'
    );
  }

  const kunciTanggal = tanggalIso.slice(0, 10);
  const bookingRef = db.collection('bookings').doc();

  // Seluruh pemeriksaan ketersediaan dan penulisan dilakukan dalam satu
  // transaksi. Tanpa ini, dua pelanggan yang menekan "Booking" pada detik
  // yang sama bisa sama-sama lolos pemeriksaan dan memesan tanggal yang
  // sama pada creator yang sama.
  await db.runTransaction(async (tx) => {
    const blockRef = db.collection('availability_blocks').doc(`${creatorId}_${kunciTanggal}`);
    const blockSnap = await tx.get(blockRef);
    if (blockSnap.exists && blockSnap.data().blocked === true) {
      throw conflict('Creator tidak tersedia pada tanggal tersebut.');
    }

    const bentrokQ = db.collection('bookings')
      .where('creatorId', '==', creatorId)
      .where('dateKey', '==', kunciTanggal)
      .where('status', 'in', ['pending_payment', 'paid', 'confirmed', 'upcoming', 'in_progress']);
    const bentrok = await tx.get(bentrokQ);
    if (!bentrok.empty) throw conflict('Tanggal tersebut sudah dipesan.');

    tx.set(bookingRef, {
      bookingId: bookingRef.id,
      customerId: actor.uid,
      customerName: actor.profile?.name || actor.email || 'Pengguna',
      creatorId,
      packageId,
      packageName: hasil.pkg.name || '',
      date: Timestamp.fromDate(tanggal),
      dateKey: kunciTanggal,
      time,
      location,
      latitude,
      longitude,
      note,
      addOns: hasil.addOnDetail,
      total: hasil.total,
      priceBreakdown: hasil.breakdown,
      status: 'pending_payment',
      createdAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    });

    tx.set(db.collection('booking_status_history').doc(), {
      bookingId: bookingRef.id,
      fromStatus: null,
      toStatus: 'pending_payment',
      actorId: actor.uid,
      actorRole: 'customer',
      reason: 'booking dibuat',
      createdAt: FieldValue.serverTimestamp(),
    });

    // Penghitung dipakai profil publik untuk menampilkan tingkat penyelesaian.
    tx.set(
      db.collection('creators').doc(creatorId),
      { totalBookings: FieldValue.increment(1) },
      { merge: true },
    );

    writeNotification(db, tx, {
      userId: creatorId,
      type: 'booking',
      title: 'Booking baru masuk',
      body: `${actor.profile?.name || 'Seorang pelanggan'} memesan ${hasil.pkg.name || 'paketmu'} untuk ${kunciTanggal}.`,
      referenceId: bookingRef.id,
    });
  });

  return { bookingId: bookingRef.id, total: hasil.total, priceBreakdown: hasil.breakdown, status: 'pending_payment' };
}

/**
 * Pindah status booking oleh pelanggan/creator.
 *
 * Graf transisi yang sama dengan sisi admin dipakai ulang, ditambah
 * pemeriksaan siapa yang berhak melakukan tiap perpindahan. Tanpa itu,
 * seorang pelanggan bisa memanggil markServiceCompleted untuk melepas dana
 * padahal pekerjaannya belum dikerjakan.
 */
async function pindahStatus(req, { toStatus, pelaku, alasanDefault }) {
  const actor = await requireUser(req);
  const body = readBody(req);
  const bookingId = requireString(body.bookingId, 'bookingId');
  const reason = typeof body.reason === 'string' && body.reason.trim() ? body.reason.trim() : alasanDefault;

  const db = adminDb();
  const ref = db.collection('bookings').doc(bookingId);

  return db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    if (!snap.exists) throw notFound('Booking tidak ditemukan.');
    const booking = snap.data();

    const adalahCustomer = booking.customerId === actor.uid;
    const adalahCreator = booking.creatorId === actor.uid;
    if (!adalahCustomer && !adalahCreator) throw forbidden('Booking ini bukan milik Anda.');
    if (pelaku === 'customer' && !adalahCustomer) throw forbidden('Hanya pelanggan yang bisa melakukan ini.');
    if (pelaku === 'creator' && !adalahCreator) throw forbidden('Hanya creator yang bisa melakukan ini.');

    const fromStatus = booking.status || 'draft';
    const boleh = BOOKING_TRANSITIONS[fromStatus] || [];
    if (!boleh.includes(toStatus)) {
      throw conflict(`Tidak bisa berpindah dari "${fromStatus}" ke "${toStatus}".`);
    }

    const update = { status: toStatus, updatedAt: FieldValue.serverTimestamp() };
    if (toStatus === 'customer_confirmed') update.customerConfirmedAt = FieldValue.serverTimestamp();
    if (toStatus === 'cancelled') update.cancelledAt = FieldValue.serverTimestamp();
    tx.update(ref, update);

    tx.set(db.collection('booking_status_history').doc(), {
      bookingId, fromStatus, toStatus,
      actorId: actor.uid, actorRole: adalahCreator ? 'creator' : 'customer',
      reason, createdAt: FieldValue.serverTimestamp(),
    });

    // Tingkat penyelesaian di profil publik dihitung dari dua penghitung ini.
    // HANYA pada 'completed': satu booking melewati completed lalu
    // customer_confirmed lalu funds_released, jadi menghitung lebih dari satu
    // di antaranya membuat angkanya melebihi jumlah booking yang ada.
    if (toStatus === 'completed') {
      tx.set(
        db.collection('creators').doc(booking.creatorId),
        { completedBookings: FieldValue.increment(1) },
        { merge: true },
      );
    }

    // Yang diberi tahu adalah pihak LAWAN — orang yang menekan tombolnya sudah
    // tahu apa yang baru saja ia lakukan.
    writeNotification(db, tx, {
      userId: adalahCreator ? booking.customerId : booking.creatorId,
      type: 'booking',
      title: JUDUL_STATUS[toStatus] || 'Status booking berubah',
      body: `Booking ${booking.packageName || ''} kini berstatus "${toStatus}".`.replace('  ', ' '),
      referenceId: bookingId,
    });

    return { bookingId, fromStatus, toStatus };
  });
}

export const startService = (req) =>
  pindahStatus(req, { toStatus: 'in_progress', pelaku: 'creator', alasanDefault: 'creator memulai sesi' });

export const confirmBooking = (req) =>
  pindahStatus(req, { toStatus: 'confirmed', pelaku: 'creator', alasanDefault: 'creator menerima booking' });

export const markServiceCompleted = (req) =>
  pindahStatus(req, { toStatus: 'completed', pelaku: 'creator', alasanDefault: 'creator menandai sesi selesai' });

export const confirmBookingCompletion = (req) =>
  pindahStatus(req, { toStatus: 'customer_confirmed', pelaku: 'customer', alasanDefault: 'pelanggan mengonfirmasi hasil' });

export const cancelBooking = (req) =>
  pindahStatus(req, { toStatus: 'cancelled', pelaku: 'both', alasanDefault: 'dibatalkan' });

export async function requestRefund(req) {
  const actor = await requireUser(req);
  const body = readBody(req);
  const bookingId = requireString(body.bookingId, 'bookingId');
  const reason = requireString(body.reason, 'reason');

  const db = adminDb();
  const ref = db.collection('bookings').doc(bookingId);
  const refundRef = db.collection('refunds').doc();

  await db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    if (!snap.exists) throw notFound('Booking tidak ditemukan.');
    const booking = snap.data();
    if (booking.customerId !== actor.uid) throw forbidden('Booking ini bukan milik Anda.');

    const fromStatus = booking.status || 'draft';
    if (!(BOOKING_TRANSITIONS[fromStatus] || []).includes('refund_requested')) {
      throw conflict(`Refund tidak bisa diajukan saat status "${fromStatus}".`);
    }

    tx.update(ref, { status: 'refund_requested', updatedAt: FieldValue.serverTimestamp() });
    tx.set(refundRef, {
      refundId: refundRef.id, bookingId, customerId: actor.uid, creatorId: booking.creatorId,
      amount: booking.total || 0, reason, status: 'pending',
      createdAt: FieldValue.serverTimestamp(),
    });
    tx.set(db.collection('booking_status_history').doc(), {
      bookingId, fromStatus, toStatus: 'refund_requested', actorId: actor.uid,
      actorRole: 'customer', reason, createdAt: FieldValue.serverTimestamp(),
    });
    writeNotification(db, tx, {
      userId: booking.creatorId,
      type: 'refund',
      title: 'Pengajuan refund',
      body: `Pelanggan mengajukan pengembalian dana: ${reason}`,
      referenceId: bookingId,
    });
  });

  return { refundId: refundRef.id, status: 'pending' };
}

export async function openDispute(req) {
  const actor = await requireUser(req);
  const body = readBody(req);
  const bookingId = requireString(body.bookingId, 'bookingId');
  const reason = requireString(body.reason, 'reason');

  const db = adminDb();
  const ref = db.collection('bookings').doc(bookingId);
  const disputeRef = db.collection('disputes').doc();

  await db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    if (!snap.exists) throw notFound('Booking tidak ditemukan.');
    const booking = snap.data();
    const adalahPihak = booking.customerId === actor.uid || booking.creatorId === actor.uid;
    if (!adalahPihak) throw forbidden('Booking ini bukan milik Anda.');

    const fromStatus = booking.status || 'draft';
    if (!(BOOKING_TRANSITIONS[fromStatus] || []).includes('disputed')) {
      throw conflict(`Sengketa tidak bisa dibuka saat status "${fromStatus}".`);
    }

    tx.update(ref, { status: 'disputed', updatedAt: FieldValue.serverTimestamp() });
    tx.set(disputeRef, {
      disputeId: disputeRef.id, bookingId, openedBy: actor.uid,
      openedByRole: booking.creatorId === actor.uid ? 'creator' : 'customer',
      customerId: booking.customerId, creatorId: booking.creatorId,
      amount: booking.total || 0, reason, status: 'open',
      createdAt: FieldValue.serverTimestamp(),
    });
    tx.set(db.collection('booking_status_history').doc(), {
      bookingId, fromStatus, toStatus: 'disputed', actorId: actor.uid,
      actorRole: booking.creatorId === actor.uid ? 'creator' : 'customer',
      reason, createdAt: FieldValue.serverTimestamp(),
    });
    writeNotification(db, tx, {
      userId: booking.creatorId === actor.uid ? booking.customerId : booking.creatorId,
      type: 'dispute',
      title: 'Sengketa dibuka',
      body: `Booking ini masuk sengketa: ${reason}`,
      referenceId: bookingId,
    });
  });

  return { disputeId: disputeRef.id, status: 'open' };
}

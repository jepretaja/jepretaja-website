import { auth } from '../firebase/config';

/**
 * Pemanggil bersama untuk KEDUA Serverless Function yang dipakai web ini:
 *
 *   /api/admin  -> aksi anggota admin_users (lihat utils/adminActions.js)
 *   /api/app    -> aksi pelanggan & creator (lihat utils/appActions.js)
 *
 * Keduanya memakai kontrak yang sama persis — POST { action, ...payload }
 * dengan header Authorization: Bearer <ID token> — dan membalas
 * { data } atau { error: { code, message } }. Sebelumnya logika ini hanya ada
 * di adminActions.js, sehingga portal creator tidak punya jalan sama sekali
 * untuk memanggil /api/app: setiap aksi creator (menutup tanggal, mengajukan
 * penarikan, menandai sesi selesai) tidak pernah bisa dijalankan dari web.
 *
 * Penerjemahan error-nya sengaja disatukan di sini: pesan "tidak bisa
 * menghubungi server" dan "format tak terduga" harus sama di kedua area,
 * karena keduanya menggambarkan kegagalan yang sama bagi penggunanya.
 */

// Kosong = pakai origin yang sama (default di Vercel). Diisi hanya kalau API
// sengaja dideploy terpisah dari web-nya.
const API_BASE = import.meta.env.VITE_ADMIN_API_BASE || '';

export class ApiError extends Error {
  constructor(code, message) {
    super(message);
    this.code = code;
  }
}

export async function callApi(endpoint, action, payload = {}) {
  const user = auth.currentUser;
  if (!user) throw new ApiError('unauthenticated', 'Anda belum login. Muat ulang halaman lalu login kembali.');

  const token = await user.getIdToken();

  let res;
  try {
    res = await fetch(`${API_BASE}${endpoint}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      // `action` sengaja ditulis SETELAH sebaran payload supaya payload yang
      // kebetulan punya field bernama `action` tidak menimpa nama aksi router.
      body: JSON.stringify({ ...payload, action }),
    });
  } catch {
    throw new ApiError('unavailable', 'Tidak bisa menghubungi server. Periksa koneksi internet Anda lalu coba lagi.');
  }

  let body = null;
  try {
    body = await res.json();
  } catch {
    throw new ApiError('internal', `Server membalas dengan format tak terduga (HTTP ${res.status}).`);
  }

  if (!res.ok || body?.error) {
    const err = body?.error || {};
    throw new ApiError(err.code || 'internal', err.message || `Permintaan gagal (HTTP ${res.status}).`);
  }

  return body.data;
}

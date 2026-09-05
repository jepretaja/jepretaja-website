/**
 * Error yang aman dikirim ke browser. Semua error lain di-log ke server dan
 * dibalas dengan pesan generik supaya detail internal (path Firestore, stack
 * trace) tidak bocor ke halaman admin.
 */
export class HttpError extends Error {
  constructor(status, code, message) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

export const badRequest = (msg) => new HttpError(400, 'invalid-argument', msg);
export const unauthorized = (msg) => new HttpError(401, 'unauthenticated', msg);
export const forbidden = (msg) => new HttpError(403, 'permission-denied', msg);
export const notFound = (msg) => new HttpError(404, 'not-found', msg);
export const conflict = (msg) => new HttpError(409, 'failed-precondition', msg);

/** Vercel sudah mem-parse JSON body, tapi jaga-jaga kalau body datang string. */
export function readBody(req) {
  const body = req.body;
  if (!body) return {};
  if (typeof body === 'string') {
    try {
      return JSON.parse(body);
    } catch {
      throw badRequest('Body request bukan JSON yang valid.');
    }
  }
  return body;
}

export function requireString(value, field) {
  if (typeof value !== 'string' || !value.trim()) {
    throw badRequest(`Field "${field}" wajib diisi.`);
  }
  return value.trim();
}

export function requireOneOf(value, field, allowed) {
  const v = requireString(value, field);
  if (!allowed.includes(v)) {
    throw badRequest(`Field "${field}" harus salah satu dari: ${allowed.join(', ')}.`);
  }
  return v;
}

export function requirePositiveInt(value, field) {
  const n = Number(value);
  if (!Number.isFinite(n) || n <= 0 || Math.floor(n) !== n) {
    throw badRequest(`Field "${field}" harus berupa angka bulat lebih besar dari 0.`);
  }
  return n;
}

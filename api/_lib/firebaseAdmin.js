import { initializeApp, getApps, cert } from 'firebase-admin/app';
import { getAuth } from 'firebase-admin/auth';
import { getFirestore, FieldValue, Timestamp } from 'firebase-admin/firestore';

/**
 * Inisialisasi Firebase Admin SDK di Vercel Serverless Function.
 *
 * Kenapa di sini dan bukan di Cloud Functions? Sejak 3 Februari 2026 Firebase
 * mewajibkan paket Blaze (harus pasang kartu) untuk Cloud Functions dan Cloud
 * Storage. Firestore + Authentication TETAP gratis di paket Spark tanpa kartu.
 * Jadi semua logika server dipindah ke Vercel Serverless Function (Hobby plan,
 * gratis, tanpa kartu) yang mengakses Firestore lewat Admin SDK.
 *
 * Kredensial diambil dari environment variable FIREBASE_SERVICE_ACCOUNT.
 * Isinya boleh JSON mentah atau JSON yang sudah di-base64.
 */

let cached = null;

function loadServiceAccount() {
  const raw = process.env.FIREBASE_SERVICE_ACCOUNT;
  if (!raw || !raw.trim()) {
    throw new Error(
      'FIREBASE_SERVICE_ACCOUNT belum diisi. Buka Vercel > Project > Settings > ' +
        'Environment Variables, lalu tempel isi file service account JSON dari ' +
        'Firebase Console > Project Settings > Service accounts > Generate new private key.'
    );
  }
  const text = raw.trim().startsWith('{') ? raw : Buffer.from(raw, 'base64').toString('utf8');
  const parsed = JSON.parse(text);
  // Private key sering tersimpan dengan "\n" literal saat ditempel manual ke
  // dashboard Vercel — kembalikan jadi newline asli supaya tidak error signing.
  if (typeof parsed.private_key === 'string') {
    parsed.private_key = parsed.private_key.replace(/\\n/g, '\n');
  }
  return parsed;
}

function getApp() {
  if (cached) return cached;
  cached = getApps().length ? getApps()[0] : initializeApp({ credential: cert(loadServiceAccount()) });
  return cached;
}

export function adminAuth() {
  return getAuth(getApp());
}

export function adminDb() {
  return getFirestore(getApp());
}

export { FieldValue, Timestamp };

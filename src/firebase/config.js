import { initializeApp } from 'firebase/app';
import { getAuth } from 'firebase/auth';
import { getFirestore } from 'firebase/firestore';
import { initializeAppCheck, ReCaptchaV3Provider } from 'firebase/app-check';

// Isi lewat file .env (lihat .env.example) — JANGAN hardcode key produksi.
const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY,
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN,
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID,
  storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET,
  messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID,
  appId: import.meta.env.VITE_FIREBASE_APP_ID,
};

export const app = initializeApp(firebaseConfig);

// App Check (section 45) — OPSIONAL sekarang. Aksi finansial tidak lagi lewat
// Cloud Functions callable melainkan lewat /api/admin (Vercel Serverless
// Function) yang memverifikasi ID token sendiri, jadi web ini tetap berfungsi
// penuh tanpa reCAPTCHA. Isi VITE_RECAPTCHA_SITE_KEY hanya kalau App Check
// sudah diaktifkan untuk Firestore di Firebase Console > App Check.
if (import.meta.env.VITE_RECAPTCHA_SITE_KEY) {
  initializeAppCheck(app, {
    provider: new ReCaptchaV3Provider(import.meta.env.VITE_RECAPTCHA_SITE_KEY),
    isTokenAutoRefreshEnabled: true,
  });
}

export const auth = getAuth(app);
export const db = getFirestore(app);

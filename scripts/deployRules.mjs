/**
 * Deploy aturan keamanan (firestore.rules / storage.rules) memakai service
 * account yang sudah ada di .env.
 *
 * KENAPA TIDAK `firebase deploy` SAJA?
 *
 * `firebase deploy --only firestore:rules` lebih dulu memeriksa apakah API
 * firestore.googleapis.com aktif, dan pemeriksaan itu memanggil
 * serviceusage.googleapis.com — yang butuh izin di LUAR yang dimiliki service
 * account Admin SDK bawaan Firebase. Hasilnya deploy berhenti di
 * "Permission denied to get service" padahal kredensialnya sebenarnya berhak
 * menerbitkan aturan.
 *
 * Skrip ini melewati pemeriksaan itu dan memanggil Firebase Rules API
 * langsung — API yang sama yang dipakai CLI setelah pemeriksaannya lewat.
 *
 * Dua cara pakai:
 *   npm run deploy:rules            -> firestore.rules
 *   npm run deploy:rules -- storage -> storage.rules
 *
 * Alternatif resminya tetap ada dan lebih disarankan kalau bisa: jalankan
 * `firebase login` sekali di komputer ini, lalu `firebase deploy --only
 * firestore:rules` bekerja seperti biasa dengan akun Google Anda sendiri.
 */
import { readFileSync } from 'node:fs';
import { GoogleAuth } from 'google-auth-library';

const TARGET = {
  firestore: { berkas: 'firestore.rules', release: 'cloud.firestore' },
  storage: { berkas: 'storage.rules', release: null }, // release diisi nama bucket
};

function bacaServiceAccount() {
  const raw = readFileSync('.env', 'utf8');
  const m = raw.match(/^FIREBASE_SERVICE_ACCOUNT=(.*)$/m);
  if (!m) throw new Error('FIREBASE_SERVICE_ACCOUNT tidak ditemukan di .env');
  let nilai = m[1].trim().replace(/^['"]|['"]$/g, '');
  // Nilainya boleh JSON apa adanya atau versi base64-nya.
  if (!nilai.startsWith('{')) nilai = Buffer.from(nilai, 'base64').toString('utf8');
  return JSON.parse(nilai);
}

function bacaEnv(kunci) {
  const raw = readFileSync('.env', 'utf8');
  const m = raw.match(new RegExp(`^${kunci}=(.*)$`, 'm'));
  return m ? m[1].trim().replace(/^['"]|['"]$/g, '') : '';
}

async function main() {
  const argumen = (process.argv[2] || 'firestore').toLowerCase();
  if (argumen === '--help' || argumen === '-h') {
    console.log('Usage: npm run deploy:rules [storage]');
    console.log('Default target: firestore.rules');
    console.log('Storage target: storage.rules');
    return;
  }

  const jenis = argumen;
  const target = TARGET[jenis];
  if (!target) throw new Error(`Target "${jenis}" tidak dikenal. Pilih: firestore atau storage.`);

  const kredensial = bacaServiceAccount();
  const project = kredensial.project_id;

  // Aturan Storage diterbitkan per bucket, jadi nama rilisnya ikut nama bucket.
  const releaseId = jenis === 'storage'
    ? `firebase.storage/${bacaEnv('VITE_FIREBASE_STORAGE_BUCKET') || `${project}.appspot.com`}`
    : target.release;

  const auth = new GoogleAuth({
    credentials: kredensial,
    scopes: ['https://www.googleapis.com/auth/cloud-platform'],
  });
  const client = await auth.getClient();
  const { token } = await client.getAccessToken();

  const base = `https://firebaserules.googleapis.com/v1/projects/${project}`;
  const panggil = async (url, opsi) => {
    const res = await fetch(url, {
      ...opsi,
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    });
    const teks = await res.text();
    if (!res.ok) {
      const err = new Error(`HTTP ${res.status}\n${teks}`);
      err.status = res.status;
      throw err;
    }
    return teks ? JSON.parse(teks) : {};
  };

  const source = readFileSync(target.berkas, 'utf8');
  console.log(`Deploy ${target.berkas} ke proyek ${project}`);

  console.log('  1/2 mengunggah ruleset...');
  const ruleset = await panggil(`${base}/rulesets`, {
    method: 'POST',
    body: JSON.stringify({ source: { files: [{ name: target.berkas, content: source }] } }),
  });
  console.log('      ', ruleset.name);

  console.log(`  2/2 menerbitkan ke ${releaseId}...`);
  const releaseName = `projects/${project}/releases/${releaseId}`;
  try {
    await panggil(`${base}/releases`, {
      method: 'POST',
      body: JSON.stringify({ name: releaseName, rulesetName: ruleset.name }),
    });
    console.log('       release baru dibuat');
  } catch (err) {
    // Rilis dengan nama itu biasanya sudah ada — perbarui, jangan buat baru.
    if (err.status !== 409) throw err;
    await panggil(`https://firebaserules.googleapis.com/v1/${releaseName}`, {
      method: 'PATCH',
      body: JSON.stringify({ release: { name: releaseName, rulesetName: ruleset.name } }),
    });
    console.log('       release yang ada diperbarui');
  }

  console.log(`SELESAI — ${target.berkas} sudah aktif.`);
}

main().catch((err) => {
  console.error('GAGAL:', err.message);
  process.exit(1);
});

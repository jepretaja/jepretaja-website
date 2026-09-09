import { readFileSync } from 'node:fs';
import { initializeApp, cert } from 'firebase-admin/app';
import { getAuth } from 'firebase-admin/auth';
import { getFirestore } from 'firebase-admin/firestore';
import { getStorage } from 'firebase-admin/storage';

if (process.env.CLEANUP_PRODUCTION_CONFIRM !== 'DELETE') {
  console.error('Dibatalkan. Set CLEANUP_PRODUCTION_CONFIRM=DELETE untuk menjalankan cleanup produksi.');
  process.exit(1);
}

function loadServiceAccount() {
  if (process.env.FIREBASE_SERVICE_ACCOUNT) return JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT);
  const env = readFileSync(new URL('../.env', import.meta.url), 'utf8');
  const line = env.split('\n').find((item) => item.startsWith('FIREBASE_SERVICE_ACCOUNT='));
  if (!line) throw new Error('FIREBASE_SERVICE_ACCOUNT tidak ditemukan di .env');
  return JSON.parse(line.slice('FIREBASE_SERVICE_ACCOUNT='.length));
}

const serviceAccount = loadServiceAccount();
initializeApp({
  credential: cert(serviceAccount),
  projectId: serviceAccount.project_id,
  storageBucket: serviceAccount.storage_bucket || `${serviceAccount.project_id}.firebasestorage.app`,
});

const db = getFirestore();
const auth = getAuth();
const bucket = getStorage().bucket();

const collections = [
  'users', 'creators', 'packages', 'portfolios', 'explore_posts', 'explore_comments',
  'explore_likes', 'explore_saves', 'explore_comment_likes', 'follows', 'reviews',
  'categories', 'bookings', 'payments', 'escrow_transactions', 'wallets',
  'wallet_transactions', 'withdrawals', 'refunds', 'disputes', 'reports',
  'notifications', 'promotions', 'chats', 'messages', 'availability_blocks',
];

async function deleteDocs(refs) {
  for (let index = 0; index < refs.length; index += 400) {
    const batch = db.batch();
    refs.slice(index, index + 400).forEach((ref) => batch.delete(ref));
    await batch.commit();
  }
}

async function deleteDemoDocuments() {
  let total = 0;
  for (const name of collections) {
    const snapshot = await db.collection(name).get();
    const refs = snapshot.docs
      .filter((item) => item.id.startsWith('demo_'))
      .map((item) => item.ref);
    await deleteDocs(refs);
    if (refs.length) console.log(`${name}: ${refs.length} dokumen demo dihapus`);
    total += refs.length;
  }
  return total;
}

async function deleteVerificationDocuments() {
  const snapshot = await db.collection('creator_verifications').get();
  await deleteDocs(snapshot.docs.map((item) => item.ref));
  console.log(`creator_verifications: ${snapshot.size} dokumen KTP/verifikasi dihapus`);
  return snapshot.size;
}

async function deleteStorageFiles() {
  const demoPrefixes = ['portfolios/demo_', 'profiles/demo_', 'explore/demo_'];
  let groups;
  try {
    groups = await Promise.all([
      bucket.getFiles({ prefix: 'verifications/' }),
      ...demoPrefixes.map((prefix) => bucket.getFiles({ prefix })),
    ]);
  } catch (error) {
    if (error?.code === 404) {
      console.log('Storage: bucket Firebase tidak tersedia, dilewati.');
      return 0;
    }
    throw error;
  }
  const targets = groups.flatMap(([files]) => files);
  await Promise.all(targets.map((file) => file.delete()));
  console.log(`Storage: ${targets.length} file KTP/demo dihapus`);
  return targets.length;
}

async function deleteDemoAuthUsers() {
  let deleted = 0;
  let pageToken;
  do {
    const page = await auth.listUsers(1000, pageToken);
    const demoUsers = page.users.filter((user) => user.uid.startsWith('demo_') || user.email?.endsWith('@example.com'));
    for (const user of demoUsers) {
      await auth.deleteUser(user.uid);
      deleted += 1;
    }
    pageToken = page.pageToken;
  } while (pageToken);
  console.log(`Auth: ${deleted} akun demo dihapus`);
  return deleted;
}

const verificationDocs = await deleteVerificationDocuments();
const demoDocs = await deleteDemoDocuments();
const storageFiles = await deleteStorageFiles();
const authUsers = await deleteDemoAuthUsers();
console.log(`Selesai: ${verificationDocs} dokumen verifikasi, ${demoDocs} dokumen demo, ${storageFiles} file Storage, ${authUsers} akun Auth.`);

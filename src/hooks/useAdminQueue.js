import { collection, where } from 'firebase/firestore';
import { db } from '../firebase/config';
import { PATHS } from '../firebase/paths';
import { useCollection } from './useCollection';
import { usePermission } from './usePermission';

/** Laporan yang sudah ditutup admin — sisanya berarti masih menunggu. */
const LAPORAN_SELESAI = ['resolved', 'dismissed'];

/**
 * Antrian pekerjaan yang menunggu tindakan admin.
 *
 * Ini padanan "notifikasi" bagi sisi admin. Admin tidak punya kotak masuk
 * pribadi — yang berarti "ada yang baru" baginya adalah pekerjaan yang masuk
 * antrian: verifikasi creator, karya yang menunggu ditinjau, sengketa yang
 * dibuka, penarikan yang diajukan, transfer manual yang perlu dicocokkan.
 * Sebelumnya semua itu hanya bisa diketahui dengan membuka halamannya satu
 * per satu dan menghitung sendiri, sehingga antrian yang sepi ditengok terus
 * dan yang menumpuk justru terlewat.
 *
 * Setiap baris disaring dengan permission yang SAMA dengan route dan menu
 * sidebar-nya (lihat App.jsx dan layouts/Sidebar.jsx). Menampilkan angka untuk
 * halaman yang tidak bisa dibuka role tersebut hanya melahirkan tugas yang
 * tidak bisa dikerjakan siapa pun yang melihatnya.
 *
 * Catatan soal biaya: hook ini dipasang sekali di AdminLayout, jadi jumlah
 * langganan Firestore-nya tetap — tidak bertambah setiap pindah halaman.
 */
export function useAdminQueue() {
  const { can } = usePermission();

  // Setiap query disaring di server supaya yang mengalir hanya dokumen yang
  // benar-benar menunggu, bukan seluruh koleksi.
  const { data: sengketa } = useCollection(
    collection(db, PATHS.disputes), [where('status', 'in', ['open', 'reviewing'])]
  );
  const { data: penarikan } = useCollection(
    collection(db, PATHS.withdrawals), [where('status', 'in', ['requested', 'processing'])]
  );
  const { data: transfer } = useCollection(
    collection(db, PATHS.payments), [where('status', '==', 'awaiting_transfer')]
  );
  const { data: refund } = useCollection(
    collection(db, PATHS.refunds), [where('status', '==', 'pending')]
  );
  // Laporan disaring di klien, bukan di server: nilai status untuk laporan
  // yang masih terbuka ditulis aplikasi dan tidak dijamin satu kata tertentu,
  // sementara `not-in` di Firestore ikut MEMBUANG dokumen yang tidak punya
  // field itu sama sekali — persis laporan lama yang justru paling lama
  // menunggu.
  const { data: laporan } = useCollection(collection(db, PATHS.reports));

  const baris = [
    {
      id: 'laporan',
      label: 'Laporan pengguna',
      to: '/reports',
      permission: 'manage_booking',
      jumlah: laporan.filter((l) => !LAPORAN_SELESAI.includes(l.status)).length,
    },
    {
      id: 'sengketa',
      label: 'Sengketa terbuka',
      to: '/disputes',
      permission: 'manage_dispute',
      jumlah: sengketa.length,
    },
    {
      id: 'transfer',
      label: 'Transfer manual perlu dicek',
      to: '/payments/manual',
      permission: 'manage_payment',
      jumlah: transfer.length,
    },
    {
      id: 'penarikan',
      label: 'Penarikan perlu diproses',
      to: '/withdrawals',
      permission: 'manage_withdrawal',
      jumlah: penarikan.length,
    },
    {
      id: 'refund',
      label: 'Refund menunggu',
      to: '/refunds',
      permission: 'manage_refund',
      jumlah: refund.length,
    },
  ].filter((b) => can(b.permission));

  return {
    baris,
    total: baris.reduce((t, b) => t + b.jumlah, 0),
  };
}

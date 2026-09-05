import { useEffect, useState } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import { collection, doc, updateDoc, where } from 'firebase/firestore';
import { db } from '../../firebase/config';
import { PATHS } from '../../firebase/paths';
import { useDocument } from '../../hooks/useDocument';
import { useCollection } from '../../hooks/useCollection';
import { usePermission } from '../../hooks/usePermission';
import StatusBadge from '../../components/StatusBadge';
import DataTable from '../../components/DataTable';
import ErrorState from '../../components/ErrorState';
import { useConfirm } from '../../components/ConfirmDialog';
import { logAdminAction, adminDeleteUser } from '../../utils/adminActions';
import { formatCurrency, formatDate } from '../../utils/format';
import { byNewest } from '../../utils/sort';

const ROLE = ['customer', 'creator'];

/** User Detail (section 29): lihat, ubah, suspend, dan hapus akun pengguna. */
export default function UserDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { data: user, loading, error } = useDocument(doc(db, PATHS.users, id));
  const { can } = usePermission();
  const { confirm, dialog } = useConfirm();

  // Data terkait milik pengguna ini. Pengurutan dilakukan di klien karena
  // where + orderBy pada field berbeda menuntut composite index (lihat
  // utils/sort.js).
  const { data: bookingRaw, error: bookingError } = useCollection(
    collection(db, PATHS.bookings), [where('customerId', '==', id)]
  );
  const { data: ulasanRaw, error: ulasanError } = useCollection(
    collection(db, PATHS.reviews), [where('customerId', '==', id)]
  );
  const bookings = byNewest(bookingRaw);
  const ulasan = byNewest(ulasanRaw);

  const bolehUbah = can('manage_users');
  const [form, setForm] = useState(null);
  const [menyimpan, setMenyimpan] = useState(false);
  const [pesan, setPesan] = useState(null);

  // Form diisi sekali saat dokumen selesai dimuat. Tanpa penjaga `form`,
  // setiap perubahan dokumen dari sisi server akan menimpa apa yang sedang
  // diketik admin di tengah penyuntingan.
  useEffect(() => {
    if (user && !form) {
      setForm({
        name: user.name || '',
        email: user.email || '',
        phone: user.phone || '',
        role: user.role || 'customer',
      });
    }
  }, [user, form]);

  const simpan = async (e) => {
    e.preventDefault();
    if (!form.name.trim()) { setPesan({ tipe: 'gagal', teks: 'Nama wajib diisi.' }); return; }
    setMenyimpan(true);
    setPesan(null);
    try {
      await updateDoc(doc(db, PATHS.users, id), {
        name: form.name.trim(),
        email: form.email.trim(),
        phone: form.phone.trim(),
        role: form.role,
      });
      await logAdminAction({
        action: 'update_user', targetType: 'user', targetId: id, reason: form.name.trim(),
      }).catch(() => {});
      setPesan({ tipe: 'sukses', teks: 'Perubahan tersimpan.' });
    } catch (err) {
      setPesan({ tipe: 'gagal', teks: err.message || 'Gagal menyimpan perubahan.' });
    } finally {
      setMenyimpan(false);
    }
  };

  const toggleSuspend = async () => {
    const suspending = user.status === 'active';
    const ok = await confirm({
      title: suspending ? 'Suspend akun ini?' : 'Aktifkan kembali akun ini?',
      message: suspending
        ? 'Pengguna tidak akan bisa login/bertransaksi sampai diaktifkan kembali.'
        : 'Pengguna akan bisa login & bertransaksi kembali.',
      danger: suspending,
    });
    if (!ok) return;
    await updateDoc(doc(db, PATHS.users, id), { status: suspending ? 'suspended' : 'active' });
    await logAdminAction({
      action: suspending ? 'suspend_user' : 'activate_user', targetType: 'user', targetId: id,
    }).catch(() => {});
  };

  const hapus = async () => {
    const ok = await confirm({
      title: `Hapus akun ${user.name}?`,
      message:
        'Akun login dinonaktifkan dan profil creator (bila ada) dihapus. Dokumen pengguna ' +
        'ditandai "deleted", bukan dibuang, karena booking dan pembayaran lama menunjuk ' +
        'ke akun ini — menghapusnya akan merusak riwayat transaksi. Penghapusan ditolak ' +
        'bila masih ada booking berjalan atau saldo tersisa.',
      danger: true,
      confirmLabel: 'Ya, Hapus Akun',
    });
    if (!ok) return;
    setMenyimpan(true);
    setPesan(null);
    try {
      await adminDeleteUser({ userId: id, reason: 'dihapus lewat panel admin' });
      navigate('/users');
    } catch (err) {
      setPesan({ tipe: 'gagal', teks: err.message || 'Gagal menghapus akun.' });
    } finally {
      setMenyimpan(false);
    }
  };

  if (loading) return <div className="loading">Memuat...</div>;
  if (error) return <ErrorState error={error} onRetry={() => window.location.reload()} />;
  if (!user) return <div className="empty-state">Pengguna tidak ditemukan</div>;

  return (
    <div>
      {dialog}
      <div className="breadcrumb"><Link to="/users">Users</Link> / {user.name}</div>

      {pesan && (
        <div className={`card ${pesan.tipe === 'sukses' ? 'banner-success' : 'banner-danger'}`}>
          <div className="msg">{pesan.teks}</div>
        </div>
      )}

      <div className="grid grid-2 mb-lg">
        <form className="card" onSubmit={simpan}>
          <div className="section-title">Ubah Profil</div>
          <div className="mb-md">
            <label className="field-label">Nama</label>
            <input className="input" value={form?.name ?? ''} disabled={!bolehUbah}
              onChange={(e) => setForm({ ...form, name: e.target.value })} />
          </div>
          <div className="mb-md">
            <label className="field-label">Email</label>
            <input className="input" type="email" value={form?.email ?? ''} disabled={!bolehUbah}
              onChange={(e) => setForm({ ...form, email: e.target.value })} />
            <p className="text-meta" style={{ margin: '4px 0 0' }}>
              Mengubah email di sini hanya memperbarui profil, bukan alamat login akun.
            </p>
          </div>
          <div className="mb-md">
            <label className="field-label">No. HP</label>
            <input className="input" value={form?.phone ?? ''} disabled={!bolehUbah}
              onChange={(e) => setForm({ ...form, phone: e.target.value })} />
          </div>
          <div className="mb-md">
            <label className="field-label">Role</label>
            <select className="input" value={form?.role ?? 'customer'} disabled={!bolehUbah}
              onChange={(e) => setForm({ ...form, role: e.target.value })}>
              {ROLE.map((r) => <option key={r} value={r}>{r}</option>)}
            </select>
          </div>
          {!bolehUbah && (
            <p className="text-danger-sm">Role Anda tidak memiliki izin <code>manage_users</code>.</p>
          )}
          <button className="btn btn-primary" type="submit" disabled={!bolehUbah || menyimpan}>
            {menyimpan ? 'Menyimpan...' : 'Simpan Perubahan'}
          </button>
        </form>

        <div>
          <div className="card mb-lg">
            <div className="section-title">Informasi Akun</div>
            <div className="detail-row"><span className="k">User ID</span><span className="v">{id}</span></div>
            <div className="detail-row"><span className="k">Status</span><span className="v"><StatusBadge status={user.status} /></span></div>
            <div className="detail-row"><span className="k">Role</span><span className="v"><StatusBadge status={user.role} /></span></div>
            <div className="detail-row"><span className="k">Bergabung</span><span className="v">{formatDate(user.createdAt)}</span></div>
            <div className="detail-row"><span className="k">Total Booking</span><span className="v num">{bookings.length}</span></div>
            <div className="detail-row"><span className="k">Total Ulasan</span><span className="v num">{ulasan.length}</span></div>
          </div>

          <div className="card">
            <div className="section-title">Aksi</div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              <button
                className={`btn ${user.status === 'active' ? 'btn-danger' : 'btn-success'}`}
                disabled={!bolehUbah || menyimpan}
                onClick={toggleSuspend}
              >
                {user.status === 'active' ? 'Suspend Akun' : 'Aktifkan Kembali'}
              </button>
              <button className="btn btn-danger" disabled={!bolehUbah || menyimpan || user.status === 'deleted'} onClick={hapus}>
                {user.status === 'deleted' ? 'Akun Sudah Dihapus' : 'Hapus Akun'}
              </button>
            </div>
          </div>
        </div>
      </div>

      <div className="table-wrap mb-lg">
        <div className="table-toolbar"><div className="section-title" style={{ marginBottom: 0 }}>Riwayat Booking</div></div>
        <DataTable
          error={bookingError}
          onRetry={() => window.location.reload()}
          emptyTitle="Belum ada booking"
          onRowClick={(row) => navigate(`/bookings/${row.id}`)}
          columns={[
            { key: 'packageName', label: 'Paket' },
            { key: 'total', label: 'Total', render: (r) => formatCurrency(r.total) },
            { key: 'status', label: 'Status', render: (r) => <StatusBadge status={r.status} /> },
            { key: 'createdAt', label: 'Tanggal', render: (r) => formatDate(r.createdAt) },
          ]}
          rows={bookings}
        />
      </div>

      <div className="table-wrap">
        <div className="table-toolbar"><div className="section-title" style={{ marginBottom: 0 }}>Ulasan Ditulis</div></div>
        <DataTable
          error={ulasanError}
          onRetry={() => window.location.reload()}
          emptyTitle="Belum menulis ulasan"
          columns={[
            { key: 'creatorId', label: 'Creator' },
            { key: 'rating', label: 'Rating', render: (r) => '★'.repeat(r.rating || 0) },
            { key: 'text', label: 'Ulasan', render: (r) => (r.text || '').slice(0, 60) },
            { key: 'status', label: 'Status', render: (r) => <StatusBadge status={r.status} /> },
          ]}
          rows={ulasan}
        />
      </div>
    </div>
  );
}

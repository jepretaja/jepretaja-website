import { useState } from 'react';
import { addDoc, collection, deleteDoc, doc, updateDoc } from 'firebase/firestore';
import { db } from '../../firebase/config';
import { PATHS } from '../../firebase/paths';
import { useCollection } from '../../hooks/useCollection';
import DataTable from '../../components/DataTable';
import StatusBadge from '../../components/StatusBadge';
import { useConfirm } from '../../components/ConfirmDialog';
import { logAdminAction } from '../../utils/adminActions';
import { byNumberAsc } from '../../utils/sort';

/** Categories (section 29): CRUD kategori jasa. */
export default function Categories() {
  // Diurutkan di klien, BUKAN dengan orderBy('order'): orderBy Firestore
  // diam-diam membuang dokumen yang tidak punya field tersebut, dan kategori
  // yang dibuat dari luar halaman ini (mis. lewat seed atau langsung di
  // Console) sering tidak menyimpan `order` — akibatnya seluruh daftar
  // tampak kosong padahal datanya ada.
  const { data: rows, loading, error } = useCollection(collection(db, PATHS.categories));
  const data = byNumberAsc(rows, 'order');
  const [name, setName] = useState('');
  const { confirm, dialog } = useConfirm();

  const addCategory = async (e) => {
    e.preventDefault();
    if (!name.trim()) return;
    const ref = await addDoc(collection(db, PATHS.categories), { name: name.trim(), active: true, order: data.length });
    await logAdminAction({ action: 'create_category', targetType: 'category', targetId: ref.id, reason: name.trim() }).catch(() => {});
    setName('');
  };

  const toggleActive = async (cat) => {
    await updateDoc(doc(db, PATHS.categories, cat.id), { active: !cat.active });
    await logAdminAction({ action: cat.active ? 'deactivate_category' : 'activate_category', targetType: 'category', targetId: cat.id }).catch(() => {});
  };

  const remove = async (cat) => {
    const ok = await confirm({ title: `Hapus kategori "${cat.name}"?`, message: 'Kategori yang sudah dipakai di paket/booking lama tidak akan terpengaruh.', danger: true, confirmLabel: 'Ya, Hapus' });
    if (!ok) return;
    await deleteDoc(doc(db, PATHS.categories, cat.id));
    await logAdminAction({ action: 'delete_category', targetType: 'category', targetId: cat.id, reason: cat.name }).catch(() => {});
  };

  return (
    <div>
      {dialog}
      <h1 className="page-title">Categories</h1>
      <form onSubmit={addCategory} style={{ display: 'flex', gap: 10, marginBottom: 16 }}>
        <input className="input" style={{ maxWidth: 280 }} placeholder="Nama kategori baru" value={name} onChange={(e) => setName(e.target.value)} />
        <button className="btn btn-primary" type="submit">Tambah</button>
      </form>
      <div className="table-wrap">
        <DataTable
          loading={loading}
          error={error}
          onRetry={() => window.location.reload()}
          columns={[
            { key: 'name', label: 'Nama' },
            { key: 'active', label: 'Status', render: (r) => <StatusBadge status={r.active ? 'active' : 'suspended'} /> },
            {
              key: 'actions',
              label: 'Aksi',
              render: (r) => (
                <div style={{ display: 'flex', gap: 8 }}>
                  <button className="btn btn-outline btn-sm" onClick={() => toggleActive(r)}>{r.active ? 'Nonaktifkan' : 'Aktifkan'}</button>
                  <button className="btn btn-danger btn-sm" onClick={() => remove(r)}>Hapus</button>
                </div>
              ),
            },
          ]}
          rows={data}
        />
      </div>
    </div>
  );
}

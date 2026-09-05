import { useState } from 'react';
import { addDoc, collection, deleteDoc, doc, orderBy, Timestamp, updateDoc } from 'firebase/firestore';
import { db } from '../../firebase/config';
import { PATHS } from '../../firebase/paths';
import { useCollection } from '../../hooks/useCollection';
import { usePermission } from '../../hooks/usePermission';
import { useConfirm } from '../../components/ConfirmDialog';
import { logAdminAction } from '../../utils/adminActions';
import DataTable from '../../components/DataTable';
import StatusBadge from '../../components/StatusBadge';
import { formatCurrency, formatDate } from '../../utils/format';

/**
 * Promotions / Voucher (section 36 & 29). SKEMA HARUS PERSIS SAMA dengan
 * yang divalidasi server di backend/functions/src/pricing.js
 * (validateAndComputeVoucherDiscount) — field `code`, `type`, `value`,
 * `maxDiscount`, `minOrder`, `usageLimit`, `active`, `startAt`, `endAt`.
 * Sebelumnya form CRUD ini tidak ada sama sekali (halaman read-only) dan
 * kolom tabel memakai field `title` yang bahkan tidak pernah dibaca server.
 */
export default function Promotions() {
  const { data, loading, error } = useCollection(collection(db, PATHS.promotions), [orderBy('createdAt', 'desc')]);
  const { can } = usePermission();
  const { confirm, dialog } = useConfirm();
  const [form, setForm] = useState(null); // null = tertutup, {} = form baru, {...promo} = edit
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState(null);

  const canManage = can('manage_promotion');

  const openCreate = () => setForm({ code: '', type: 'percentage', value: '', maxDiscount: '', minOrder: '', usageLimit: '', active: true, startAt: '', endAt: '' });
  const openEdit = (promo) => setForm({
    id: promo.id,
    code: promo.code || '',
    type: promo.type || 'percentage',
    value: promo.value ?? '',
    maxDiscount: promo.maxDiscount ?? '',
    minOrder: promo.minOrder ?? '',
    usageLimit: promo.usageLimit ?? '',
    active: promo.active !== false,
    startAt: promo.startAt ? promo.startAt.toDate().toISOString().slice(0, 10) : '',
    endAt: promo.endAt ? promo.endAt.toDate().toISOString().slice(0, 10) : '',
  });

  const save = async (e) => {
    e.preventDefault();
    if (!form.code.trim()) { setFormError('Kode voucher wajib diisi.'); return; }
    if (!form.value || Number(form.value) <= 0) { setFormError('Value wajib diisi dan lebih dari 0.'); return; }
    setSaving(true);
    setFormError(null);
    try {
      const payload = {
        code: form.code.trim().toUpperCase(),
        type: form.type,
        value: Number(form.value),
        maxDiscount: form.maxDiscount ? Number(form.maxDiscount) : null,
        minOrder: form.minOrder ? Number(form.minOrder) : null,
        usageLimit: form.usageLimit ? Number(form.usageLimit) : null,
        usageCount: form.usageCount || 0,
        active: form.active,
        startAt: form.startAt ? Timestamp.fromDate(new Date(form.startAt)) : null,
        endAt: form.endAt ? Timestamp.fromDate(new Date(form.endAt)) : null,
      };
      if (form.id) {
        await updateDoc(doc(db, PATHS.promotions, form.id), payload);
        await logAdminAction({ action: 'update_promotion', targetType: 'promotion', targetId: form.id, reason: payload.code }).catch(() => {});
      } else {
        const ref = await addDoc(collection(db, PATHS.promotions), { ...payload, createdAt: new Date() });
        await logAdminAction({ action: 'create_promotion', targetType: 'promotion', targetId: ref.id, reason: payload.code }).catch(() => {});
      }
      setForm(null);
    } catch (err) {
      setFormError(err.message || 'Gagal menyimpan voucher.');
    } finally {
      setSaving(false);
    }
  };

  const toggleActive = async (promo) => {
    await updateDoc(doc(db, PATHS.promotions, promo.id), { active: !promo.active });
    await logAdminAction({ action: promo.active ? 'deactivate_promotion' : 'activate_promotion', targetType: 'promotion', targetId: promo.id }).catch(() => {});
  };

  const remove = async (promo) => {
    const ok = await confirm({ title: `Hapus voucher "${promo.code}"?`, message: 'Voucher yang sudah pernah dipakai tetap tercatat di booking lama.', danger: true, confirmLabel: 'Ya, Hapus' });
    if (!ok) return;
    await deleteDoc(doc(db, PATHS.promotions, promo.id));
    await logAdminAction({ action: 'delete_promotion', targetType: 'promotion', targetId: promo.id, reason: promo.code }).catch(() => {});
  };

  return (
    <div>
      {dialog}
      <div className="table-toolbar" style={{ padding: 0, marginBottom: 12 }}>
        <h1 className="page-title mb-0">Promotions / Voucher</h1>
        {canManage && <button className="btn btn-primary btn-sm" onClick={openCreate}>+ Voucher Baru</button>}
      </div>

      {form && (
        <form onSubmit={save} className="card mb-lg" style={{ maxWidth: 480 }}>
          <div className="section-title">{form.id ? 'Edit Voucher' : 'Voucher Baru'}</div>
          <div className="form-fields">
            <input className="input" placeholder="Kode voucher (mis. HEMAT50)" value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value })} />
            <select className="input" value={form.type} onChange={(e) => setForm({ ...form, type: e.target.value })}>
              <option value="percentage">Percentage (%)</option>
              <option value="fixed">Fixed (Rp)</option>
            </select>
            <input className="input" type="number" placeholder={form.type === 'percentage' ? 'Value (%), mis. 20' : 'Value (Rp)'} value={form.value} onChange={(e) => setForm({ ...form, value: e.target.value })} />
            {form.type === 'percentage' && (
              <input className="input" type="number" placeholder="Maksimal diskon (Rp) — opsional" value={form.maxDiscount} onChange={(e) => setForm({ ...form, maxDiscount: e.target.value })} />
            )}
            <input className="input" type="number" placeholder="Minimum order (Rp) — opsional" value={form.minOrder} onChange={(e) => setForm({ ...form, minOrder: e.target.value })} />
            <input className="input" type="number" placeholder="Batas pemakaian (kali) — opsional" value={form.usageLimit} onChange={(e) => setForm({ ...form, usageLimit: e.target.value })} />
            <div className="form-row">
              <label className="field-label">Mulai
                <input className="input" type="date" value={form.startAt} onChange={(e) => setForm({ ...form, startAt: e.target.value })} />
              </label>
              <label className="field-label">Selesai
                <input className="input" type="date" value={form.endAt} onChange={(e) => setForm({ ...form, endAt: e.target.value })} />
              </label>
            </div>
            <label style={{ fontSize: 13 }}>
              <input type="checkbox" checked={form.active} onChange={(e) => setForm({ ...form, active: e.target.checked })} style={{ marginRight: 6 }} />
              Aktif
            </label>
            {formError && <span className="text-danger-sm">{formError}</span>}
            <div className="flex-row">
              <button className="btn btn-primary" type="submit" disabled={saving}>{saving ? 'Menyimpan...' : 'Simpan'}</button>
              <button className="btn btn-outline" type="button" onClick={() => setForm(null)}>Batal</button>
            </div>
          </div>
        </form>
      )}

      <div className="table-wrap">
        <DataTable
          loading={loading}
          error={error}
          onRetry={() => window.location.reload()}
          emptyTitle="Belum ada voucher"
          columns={[
            { key: 'code', label: 'Kode', render: (r) => <strong>{r.code}</strong> },
            { key: 'type', label: 'Tipe' },
            { key: 'value', label: 'Value', render: (r) => (r.type === 'percentage' ? `${r.value}%` : formatCurrency(r.value)) },
            { key: 'maxDiscount', label: 'Maks Diskon', render: (r) => (r.maxDiscount ? formatCurrency(r.maxDiscount) : '-') },
            { key: 'usage', label: 'Pemakaian', render: (r) => `${r.usageCount || 0}${r.usageLimit ? ` / ${r.usageLimit}` : ''}` },
            { key: 'endAt', label: 'Berakhir', render: (r) => (r.endAt ? formatDate(r.endAt) : '-') },
            { key: 'active', label: 'Status', render: (r) => <StatusBadge status={r.active ? 'active' : 'suspended'} /> },
            ...(canManage ? [{
              key: 'actions', label: 'Aksi',
              render: (r) => (
                <div style={{ display: 'flex', gap: 6 }}>
                  <button className="btn btn-outline btn-sm" onClick={() => openEdit(r)}>Edit</button>
                  <button className="btn btn-outline btn-sm" onClick={() => toggleActive(r)}>{r.active ? 'Nonaktifkan' : 'Aktifkan'}</button>
                  <button className="btn btn-danger btn-sm" onClick={() => remove(r)}>Hapus</button>
                </div>
              ),
            }] : []),
          ]}
          rows={data}
        />
      </div>
    </div>
  );
}

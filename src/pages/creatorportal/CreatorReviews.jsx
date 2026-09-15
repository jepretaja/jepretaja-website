import { collection, doc, updateDoc, where } from 'firebase/firestore';
import { useState } from 'react';
import { db } from '../../firebase/config';
import { PATHS } from '../../firebase/paths';
import { useCollection } from '../../hooks/useCollection';
import { useAuth } from '../../auth/AuthContext';
import DataTable from '../../components/DataTable';
import { formatDateTime } from '../../utils/format';
import { byNewest } from '../../utils/sort';

/** Ulasan yang diterima creator ini. */
export default function CreatorReviews() {
  const { user } = useAuth();
  const uid = user?.uid;
  // Urutan terbaru dihitung di klien (lihat utils/sort.js) — where + orderBy
  // di field berbeda menuntut composite index, dan tanpa index itu query
  // gagal total sehingga halaman ini hanya menampilkan pesan error.
  const { data: rows, loading, error } = useCollection(
    collection(db, PATHS.reviews),
    uid ? [where('creatorId', '==', uid)] : []
  );
  const data = byNewest(rows);
  const [balasan, setBalasan] = useState({});
  const [menyimpanId, setMenyimpanId] = useState(null);
  const [pesan, setPesan] = useState(null);

  const rataRata = data.length
    ? (data.reduce((a, r) => a + (r.rating || 0), 0) / data.length).toFixed(1)
    : '-';

  const simpanBalasan = async (review) => {
    const teks = (balasan[review.id] ?? review.creatorReply ?? '').trim();
    if (!teks) {
      setPesan('Balasan tidak boleh kosong.');
      return;
    }
    setMenyimpanId(review.id);
    setPesan(null);
    try {
      await updateDoc(doc(db, PATHS.reviews, review.id), { creatorReply: teks });
      setBalasan((s) => ({ ...s, [review.id]: teks }));
      setPesan('Balasan tersimpan.');
    } catch (err) {
      setPesan(err.message || 'Gagal menyimpan balasan.');
    } finally {
      setMenyimpanId(null);
    }
  };

  return (
    <div>
      <h1 className="page-title">Ulasan</h1>
      <p className="text-meta" style={{ marginBottom: 16 }}>
        {data.length} ulasan &middot; rata-rata {rataRata}
      </p>
      {pesan && <div className="card banner-success"><div className="msg">{pesan}</div></div>}
      <div className="table-wrap">
        <DataTable
          loading={loading}
          error={error}
          onRetry={() => window.location.reload()}
          columns={[
            { key: 'customerName', label: 'Pelanggan', render: (r) => r.customerName || '-' },
            { key: 'rating', label: 'Rating', render: (r) => `${r.rating || 0} / 5` },
            { key: 'text', label: 'Ulasan', render: (r) => r.text || '-' },
            {
              key: 'creatorReply', label: 'Balasan Anda', render: (r) => (
                <div style={{ minWidth: 240 }}>
                  <textarea
                    className="input"
                    rows={2}
                    value={balasan[r.id] ?? r.creatorReply ?? ''}
                    placeholder="Tulis balasan untuk pelanggan"
                    onChange={(e) => setBalasan((s) => ({ ...s, [r.id]: e.target.value }))}
                  />
                  <button className="btn btn-outline btn-sm" type="button" disabled={menyimpanId === r.id} onClick={() => simpanBalasan(r)}>
                    {menyimpanId === r.id ? 'Menyimpan...' : 'Simpan Balasan'}
                  </button>
                </div>
              ),
            },
            { key: 'createdAt', label: 'Waktu', render: (r) => formatDateTime(r.createdAt) },
          ]}
          rows={data}
          emptyTitle="Belum ada ulasan"
        />
      </div>
    </div>
  );
}

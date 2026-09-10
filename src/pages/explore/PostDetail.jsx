import { useParams, Link } from 'react-router-dom';
import { doc, updateDoc } from 'firebase/firestore';
import { db } from '../../firebase/config';
import { PATHS } from '../../firebase/paths';
import { useDocument } from '../../hooks/useDocument';
import StatusBadge from '../../components/StatusBadge';
import ErrorState from '../../components/ErrorState';
import { useConfirm } from '../../components/ConfirmDialog';
import { logAdminAction } from '../../utils/adminActions';
import { formatDateTime } from '../../utils/format';

/** Post Detail (section 21 & 29): approve/reject/hide/delete/feature. */
export default function PostDetail() {
  const { id } = useParams();
  const { data: post, loading, error } = useDocument(doc(db, PATHS.explorePosts, id));
  const { confirm, dialog } = useConfirm();

  const setStatus = async (status, label) => {
    const ok = await confirm({
      title: `${label}?`,
      message: 'Aksi ini akan langsung diterapkan dan tercatat di audit log.',
      danger: status === 'rejected' || status === 'deleted',
    });
    if (!ok) return;

    // Menolak atau menyembunyikan WAJIB disertai alasan. Tanpa alasan, creator
    // hanya melihat karyanya hilang dan mengulangi kesalahan yang sama pada
    // unggahan berikutnya; catatan ini yang ditampilkan di layar "Karya Saya".
    let moderationNote = null;
    if (status === 'rejected' || status === 'hidden') {
      moderationNote = window.prompt('Alasan singkat untuk creator (wajib):', '');
      if (!moderationNote || !moderationNote.trim()) {
        window.alert('Alasan wajib diisi supaya creator tahu apa yang harus diperbaiki.');
        return;
      }
      moderationNote = moderationNote.trim().slice(0, 300);
    }

    await updateDoc(doc(db, PATHS.explorePosts, id), {
      status,
      // Disetujui kembali? Catatan lama dihapus supaya tidak menempel di karya
      // yang sudah tayang.
      moderationNote: moderationNote ?? null,
    });
    await logAdminAction({
      action: `explore_${status}`, targetType: 'explore_post', targetId: id,
      metadata: moderationNote ? { moderationNote } : undefined,
    }).catch(() => {});
  };

  if (loading) return <div className="loading">Memuat...</div>;
  if (error) return <ErrorState error={error} onRetry={() => window.location.reload()} />;
  if (!post) return <div className="empty-state">Konten tidak ditemukan</div>;

  return (
    <div>
      {dialog}
      <div className="breadcrumb"><Link to="/explore">Explore Content</Link> / Post Detail</div>
      <div className="grid grid-2">
        <div className="card">
          {post.mediaUrls?.[0] && (post.type === 'video' ? (
            <video controls preload="metadata" src={post.mediaUrls[0]} poster={post.thumbnailUrl || undefined}
              style={{ width: '100%', borderRadius: 10, marginBottom: 14, maxHeight: 360, objectFit: 'cover' }} />
          ) : (
            <img src={post.thumbnailUrl || post.mediaUrls[0]} alt="" style={{ width: '100%', borderRadius: 10, marginBottom: 14, maxHeight: 360, objectFit: 'cover' }} />
          ))}
          <div className="detail-row"><span className="k">Creator</span><span className="v">{post.creatorName}</span></div>
          <div className="detail-row"><span className="k">Kategori</span><span className="v">{post.category}</span></div>
          <div className="detail-row"><span className="k">Lokasi</span><span className="v">{post.location || '-'}</span></div>
          <div className="detail-row"><span className="k">Caption</span><span className="v">{post.caption}</span></div>
          <div className="detail-row"><span className="k">Status</span><span className="v"><StatusBadge status={post.status} /></span></div>
          <div className="detail-row"><span className="k">Dibuat</span><span className="v">{formatDateTime(post.createdAt)}</span></div>
        </div>
        <div className="card">
          <div className="section-title">Moderasi</div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            <button className="btn btn-success" onClick={() => setStatus('published', 'Approve (Publish)')}>Approve (Publish)</button>
            <button className="btn btn-danger" onClick={() => setStatus('rejected', 'Reject')}>Reject</button>
            <button className="btn btn-outline" onClick={() => setStatus('hidden', 'Hide')}>Hide</button>
            <button className="btn btn-outline" onClick={() => setStatus('deleted', 'Delete')}>Delete</button>
          </div>
          <div className="section-title" style={{ marginTop: 20 }}>Engagement</div>
          <div className="detail-row"><span className="k">Like</span><span className="v num">{post.metrics?.like ?? 0}</span></div>
          <div className="detail-row"><span className="k">Comment</span><span className="v num">{post.metrics?.comment ?? 0}</span></div>
          <div className="detail-row"><span className="k">Save</span><span className="v num">{post.metrics?.save ?? 0}</span></div>
          <div className="detail-row"><span className="k">View</span><span className="v num">{post.metrics?.view ?? 0}</span></div>
        </div>
      </div>
    </div>
  );
}

import { useEffect, useMemo, useRef, useState } from 'react';
import { addDoc, collection, doc, serverTimestamp, updateDoc, where } from 'firebase/firestore';
import { db } from '../../firebase/config';
import { PATHS } from '../../firebase/paths';
import { useCollection } from '../../hooks/useCollection';
import { useAuth } from '../../auth/AuthContext';
import EmptyState from '../../components/EmptyState';
import ErrorState from '../../components/ErrorState';
import { formatDateTime } from '../../utils/format';
import { toMillis } from '../../utils/sort';

/**
 * Isi satu pesan bisa datang dengan nama field berbeda tergantung versi APK
 * yang menulisnya. Dibaca lewat daftar cadangan supaya percakapan lama tidak
 * tampil sebagai deretan baris kosong — kegagalan yang sangat mudah disalah
 * artikan sebagai "pesannya hilang".
 */
function isiPesan(m) {
  return m.text ?? m.message ?? m.content ?? m.body ?? '';
}

/**
 * Ruang percakapan creator dengan pelanggannya.
 *
 * Dua hal yang menentukan bentuk halaman ini, keduanya berasal dari
 * firestore.rules dan bukan pilihan gaya:
 *
 *  1. Pesan disimpan sebagai koleksi TINGKAT ATAS `messages` dengan field
 *     `chatId`, bukan sub-koleksi di bawah /chats/{chatId}.
 *  2. Aturan membaca pesan berbunyi `uid() in resource.data.participants`.
 *     Firestore hanya mengabulkan sebuah query kalau batasannya MEMBUKTIKAN
 *     seluruh hasilnya lolos aturan, jadi query di sini wajib memakai
 *     `array-contains` pada `participants` — menyaring dengan `chatId` saja
 *     akan ditolak seluruhnya, bukan sekadar mengembalikan sedikit baris.
 *
 * Karena itu seluruh pesan milik creator ini diambil sekali lalu dikelompokkan
 * per percakapan di browser. Untuk jumlah pesan seorang creator, itu jauh lebih
 * murah daripada satu langganan Firestore per percakapan yang dibuka.
 */
export default function CreatorChats() {
  const { user, creatorProfile } = useAuth();
  const uid = user?.uid;

  const [dipilih, setDipilih] = useState(null);
  const [teks, setTeks] = useState('');
  const [mengirim, setMengirim] = useState(false);
  const [pesanGagal, setPesanGagal] = useState(null);
  const akhirRef = useRef(null);

  const { data: chats, loading, error } = useCollection(
    collection(db, PATHS.chats),
    uid ? [where('creatorId', '==', uid)] : []
  );
  const { data: semuaPesan, error: errorPesan } = useCollection(
    collection(db, PATHS.messages),
    uid ? [where('participants', 'array-contains', uid)] : []
  );

  const perChat = useMemo(() => {
    const peta = new Map();
    semuaPesan.forEach((m) => {
      const daftar = peta.get(m.chatId) || [];
      daftar.push(m);
      peta.set(m.chatId, daftar);
    });
    // Di dalam percakapan urutannya justru terlama di atas — itu urutan baca
    // yang wajar untuk percakapan, kebalikan dari daftar tabel di panel lain.
    peta.forEach((daftar) => daftar.sort((a, b) => toMillis(a.createdAt) - toMillis(b.createdAt)));
    return peta;
  }, [semuaPesan]);

  const daftarChat = useMemo(() => {
    return [...chats].sort((a, b) => toMillis(b.updatedAt) - toMillis(a.updatedAt));
  }, [chats]);

  const chatAktif = daftarChat.find((c) => c.id === dipilih) || null;
  const pesanAktif = chatAktif ? (perChat.get(chatAktif.id) || []) : [];

  // Percakapan pertama dibuka sendiri supaya halaman tidak mendarat di panel
  // kanan yang kosong.
  useEffect(() => {
    if (!dipilih && daftarChat.length > 0) setDipilih(daftarChat[0].id);
  }, [daftarChat, dipilih]);

  useEffect(() => {
    akhirRef.current?.scrollIntoView({ block: 'end' });
  }, [pesanAktif.length, dipilih]);

  const belumDibaca = (chatId) =>
    (perChat.get(chatId) || []).filter((m) => !m.readAt && m.senderId !== uid).length;

  const kirim = async (e) => {
    e.preventDefault();
    const isi = teks.trim();
    if (!isi || !chatAktif) return;

    setMengirim(true);
    setPesanGagal(null);
    try {
      await addDoc(collection(db, PATHS.messages), {
        chatId: chatAktif.id,
        senderId: uid,
        senderName: creatorProfile?.displayName || null,
        // Aturan menuntut daftar ini berisi TEPAT kedua peserta chat —
        // menambah atau mengurangi satu id membuat pesannya ditolak.
        participants: [chatAktif.customerId, chatAktif.creatorId],
        text: isi,
        readAt: null,
        createdAt: serverTimestamp(),
      });
      // Ringkasan di dokumen chat: satu-satunya perubahan yang diizinkan
      // aturan bagi peserta percakapan.
      await updateDoc(doc(db, PATHS.chats, chatAktif.id), {
        lastMessage: isi,
        updatedAt: serverTimestamp(),
      });
      setTeks('');
    } catch (err) {
      setPesanGagal(err.message || 'Pesan gagal dikirim.');
    } finally {
      setMengirim(false);
    }
  };

  const tandaiDibaca = async () => {
    if (!chatAktif) return;
    const perlu = (perChat.get(chatAktif.id) || []).filter((m) => !m.readAt && m.senderId !== uid);
    if (perlu.length === 0) return;
    // Hanya lawan bicara yang boleh menandai sudah dibaca, dan hanya field
    // `readAt` yang boleh disentuh — keduanya ditegakkan di firestore.rules.
    await Promise.allSettled(
      perlu.map((m) => updateDoc(doc(db, PATHS.messages, m.id), { readAt: serverTimestamp() }))
    );
  };

  if (loading) return <div className="loading">Memuat percakapan...</div>;
  if (error) return <ErrorState error={error} onRetry={() => window.location.reload()} />;

  return (
    <div>
      <h1 className="page-title">Pesan</h1>
      <p className="text-meta" style={{ marginBottom: 16 }}>
        Percakapan tumbuh dari transaksi — daftar di bawah hanya berisi pelanggan yang sudah menghubungi Anda.
      </p>

      {errorPesan && (
        <div className="card banner-danger">
          <div className="msg">Isi percakapan gagal dimuat: {errorPesan.code || errorPesan.message}</div>
        </div>
      )}
      {pesanGagal && <div className="card banner-danger"><div className="msg">{pesanGagal}</div></div>}

      {daftarChat.length === 0 ? (
        <EmptyState glyph="✉" title="Belum ada percakapan" hint="Pesan dari pelanggan akan muncul di sini." />
      ) : (
        <div className="chat-shell">
          <div className="chat-daftar">
            {daftarChat.map((c) => {
              const jumlah = belumDibaca(c.id);
              return (
                <button
                  key={c.id}
                  type="button"
                  className={`chat-daftar-item${c.id === dipilih ? ' active' : ''}`}
                  onClick={() => setDipilih(c.id)}
                >
                  <div className="flex-between">
                    <strong>{c.customerName || c.customerId || 'Pelanggan'}</strong>
                    {jumlah > 0 && <span className="badge badge-danger">{jumlah > 99 ? '99+' : jumlah}</span>}
                  </div>
                  <div className="chat-daftar-cuplikan">{c.lastMessage || 'Belum ada pesan'}</div>
                  <div className="text-meta">{formatDateTime(c.updatedAt)}</div>
                </button>
              );
            })}
          </div>

          <div className="chat-ruang">
            {!chatAktif ? (
              <EmptyState title="Pilih satu percakapan" />
            ) : (
              <>
                <div className="chat-kepala flex-between">
                  <strong>{chatAktif.customerName || chatAktif.customerId || 'Pelanggan'}</strong>
                  {belumDibaca(chatAktif.id) > 0 && (
                    <button className="btn btn-outline btn-sm" onClick={tandaiDibaca}>Tandai dibaca</button>
                  )}
                </div>

                <div className="chat-gulung">
                  {pesanAktif.length === 0 ? (
                    <EmptyState title="Belum ada pesan di percakapan ini" />
                  ) : (
                    pesanAktif.map((m) => (
                      <div key={m.id} className={`chat-balon${m.senderId === uid ? ' milik-saya' : ''}`}>
                        <div>{isiPesan(m) || <em className="text-meta">(pesan tanpa teks)</em>}</div>
                        <div className="chat-waktu">{formatDateTime(m.createdAt)}</div>
                      </div>
                    ))
                  )}
                  <div ref={akhirRef} />
                </div>

                <form className="chat-kirim" onSubmit={kirim}>
                  <input
                    className="input"
                    value={teks}
                    placeholder="Tulis pesan..."
                    onChange={(e) => setTeks(e.target.value)}
                  />
                  <button className="btn btn-primary" type="submit" disabled={mengirim || !teks.trim()}>
                    {mengirim ? 'Mengirim...' : 'Kirim'}
                  </button>
                </form>
              </>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

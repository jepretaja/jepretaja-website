import { useId, useRef, useState } from 'react';
import { storageTersedia, storageTidakTersedia, unggahBerkas } from '../firebase/storage';
import {
  adalahDataUrl, blobKeDataUrl, formatUkuran, kompresGambar, periksaBerkasMedia, ukuranDataUrl,
} from '../utils/imageFile';

/**
 * Pemilih gambar: klik, tarik-lepas, tempel, atau ambil langsung dari kamera.
 *
 * Menggantikan kolom "tempel URL gambar" yang sebelumnya dipakai di seluruh
 * panel. Kolom URL itu bukan sekadar kurang nyaman — ia membuat fiturnya tidak
 * bisa dipakai oleh orang yang justru menjadi sasarannya: seorang creator yang
 * baru memotret dengan ponselnya tidak punya URL untuk ditempel. Kolom URL
 * tetap disediakan sebagai pelengkap (berguna untuk memindahkan gambar yang
 * sudah telanjur ada di tempat lain), tapi tidak lagi menjadi satu-satunya cara.
 *
 * DUA TEMPAT PENYIMPANAN, satu perilaku:
 *
 *  1. Firebase Storage, kalau bucketnya bisa dipakai. Ini jalur yang
 *     diutamakan — dokumen Firestore hanya menyimpan URL pendek.
 *  2. Kalau Storage menolak (paket gratis tidak selalu punya bucket, lihat
 *     firebase/storage.js), gambarnya dikompres lebih kecil lagi lalu disimpan
 *     langsung di dalam dokumen sebagai data URL.
 *
 * Cara kedua ada batasnya dan batas itu ditegakkan, bukan didiamkan: satu
 * dokumen Firestore tidak boleh lebih dari 1 MB, dan dokumen yang melampauinya
 * GAGAL DISIMPAN SEUTUHNYA — bukan gagal sebagian. Karena itu total gambar
 * tertanam dijaga jauh di bawah batas tersebut, dan penambahan yang akan
 * melewatinya ditolak di sini dengan penjelasan, bukan dibiarkan meledak saat
 * tombol Simpan ditekan.
 */
export default function ImagePicker({
  value,
  onChange,
  multiple = false,
  max = 8,
  folder = 'uploads',
  label = 'Foto',
  hint,
  allowUrl = true,
  // Dipakai halaman admin saat role-nya tidak berizin menyunting: gambarnya
  // tetap terlihat, hanya alat pengubahnya yang hilang. Menyembunyikan
  // seluruh bagian ini akan membuat admin mengira creator itu memang belum
  // punya foto.
  disabled = false,
  sisiMaks = 1600,
  targetByte = 400 * 1024,
  // Ambang aman untuk gambar tertanam. Sisa ~300 KB dari batas 1 MB
  // disediakan untuk isi dokumen yang lain.
  batasTertanam = 700 * 1024,
  izinkanVideo = false,
  onMediaTypeChange,
}) {
  const idBerkas = useId();
  const inputBerkas = useRef(null);
  const inputKamera = useRef(null);

  const [sibuk, setSibuk] = useState(false);
  const [progres, setProgres] = useState(null); // { selesai, total }
  const [galat, setGalat] = useState(null);
  const [seret, setSeret] = useState(false);
  const [urlManual, setUrlManual] = useState('');
  const [bukaUrl, setBukaUrl] = useState(false);

  // Satu bentuk data di dalam komponen (array), apa pun bentuk di luarnya.
  const daftar = multiple ? (Array.isArray(value) ? value : []) : (value ? [value] : []);
  const penuh = daftar.length >= (multiple ? max : 1);

  const kirimKeluar = (berikutnya) => {
    onChange(multiple ? berikutnya : (berikutnya[0] || ''));
  };

  const totalTertanam = daftar.reduce((t, g) => t + ukuranDataUrl(g), 0);

  const prosesBerkas = async (fileList) => {
    const files = [...fileList];
    if (files.length === 0) return;

    setGalat(null);

    const sisaSlot = (multiple ? max : 1) - daftar.length;
    if (sisaSlot <= 0) {
      setGalat(multiple ? `Sudah mencapai batas ${max} foto.` : 'Hapus foto lama dulu sebelum menambah yang baru.');
      return;
    }
    const antre = files.slice(0, sisaSlot);
    const dilewati = files.length - antre.length;

    setSibuk(true);
    setProgres({ selesai: 0, total: antre.length });

    const hasil = [];
    const masalah = [];
    // Sekali Storage terbukti tidak tersedia, sisa berkas dalam satu batch
    // langsung ditempuh lewat jalur tertanam — mencoba lagi hanya menambah
    // penantian yang sudah pasti berujung sama.
    let pakaiTertanam = !storageTersedia();
    let besarTertanamBaru = 0;

    for (const file of antre) {
      const salah = periksaBerkasMedia(file, izinkanVideo);
      if (salah) {
        masalah.push(salah);
        setProgres((p) => ({ ...p, selesai: p.selesai + 1 }));
        continue;
      }

      try {
        if (file.type.startsWith('video/')) {
          if (!storageTersedia()) {
            throw new Error('Video membutuhkan Firebase Storage. Aktifkan VITE_USE_FIREBASE_STORAGE=true dan deploy storage.rules.');
          }
          hasil.push(await unggahBerkas(file, folder));
          onMediaTypeChange?.('video');
          setProgres((p) => ({ ...p, selesai: p.selesai + 1 }));
          continue;
        }
        if (!pakaiTertanam) {
          const { blob } = await kompresGambar(file, { sisiMaks, targetByte });
          try {
            hasil.push(await unggahBerkas(blob, folder));
            setProgres((p) => ({ ...p, selesai: p.selesai + 1 }));
            continue;
          } catch (err) {
            if (!storageTidakTersedia(err)) throw err;
            pakaiTertanam = true;
          }
        }

        // Jalur tertanam: dikompres jauh lebih kecil, karena hasilnya ikut
        // menumpang di dalam dokumen dan dibaca ulang setiap kali halaman
        // yang memuatnya dibuka.
        const { blob } = await kompresGambar(file, { sisiMaks: 1000, targetByte: 150 * 1024 });
        const dataUrl = await blobKeDataUrl(blob);
        if (totalTertanam + besarTertanamBaru + dataUrl.length > batasTertanam) {
          masalah.push(
            `"${file.name}" tidak muat. Penyimpanan gambar di dalam dokumen dibatasi ${formatUkuran(batasTertanam)}; hapus foto lain dulu atau aktifkan Firebase Storage.`
          );
        } else {
          besarTertanamBaru += dataUrl.length;
          hasil.push(dataUrl);
        }
      } catch (err) {
        masalah.push(`"${file.name}" gagal diproses: ${err.message}`);
      }
      setProgres((p) => ({ ...p, selesai: p.selesai + 1 }));
    }

    if (hasil.length) kirimKeluar([...daftar, ...hasil]);
    if (dilewati > 0) {
      masalah.push(`${dilewati} berkas tidak ikut ditambahkan karena batas ${max} foto.`);
    }
    if (masalah.length) setGalat(masalah.join(' '));

    setSibuk(false);
    setProgres(null);
    // Berkas yang sama harus bisa dipilih lagi setelah dihapus; tanpa ini
    // event `change` tidak menyala karena nilainya dianggap tidak berubah.
    if (inputBerkas.current) inputBerkas.current.value = '';
    if (inputKamera.current) inputKamera.current.value = '';
  };

  const hapus = (i) => {
    setGalat(null);
    kirimKeluar(daftar.filter((_, idx) => idx !== i));
  };

  const jadikanSampul = (i) => {
    const berikutnya = [...daftar];
    const [dipindah] = berikutnya.splice(i, 1);
    kirimKeluar([dipindah, ...berikutnya]);
  };

  const tambahUrl = () => {
    const url = urlManual.trim();
    if (!url) return;
    if (!/^https?:\/\//i.test(url)) {
      setGalat('Tautan harus diawali http:// atau https://');
      return;
    }
    setGalat(null);
    setUrlManual('');
    kirimKeluar(multiple ? [...daftar, url] : [url]);
  };

  return (
    <div className="pemilih-gambar">
      <label className="field-label" htmlFor={idBerkas}>{label}</label>

      {!penuh && !disabled && (
        <div
          className={`pemilih-zona${seret ? ' seret' : ''}${sibuk ? ' sibuk' : ''}`}
          onDragOver={(e) => { e.preventDefault(); setSeret(true); }}
          onDragLeave={() => setSeret(false)}
          onDrop={(e) => {
            e.preventDefault();
            setSeret(false);
            if (!sibuk) prosesBerkas(e.dataTransfer.files);
          }}
          // Tempel tangkapan layar langsung dari papan klip — jalan tercepat
          // untuk gambar yang belum pernah tersimpan sebagai berkas.
          onPaste={(e) => {
            const berkas = [...(e.clipboardData?.files || [])];
            if (berkas.length && !sibuk) prosesBerkas(berkas);
          }}
        >
          <input
            id={idBerkas}
            ref={inputBerkas}
            type="file"
            accept={izinkanVideo ? 'image/*,video/mp4,video/webm,video/quicktime' : 'image/*'}
            multiple={multiple}
            hidden
            onChange={(e) => prosesBerkas(e.target.files)}
          />
          {/* Input kedua khusus kamera. `capture` hanya berpengaruh di ponsel;
              di komputer tombolnya membuka pemilih berkas biasa, jadi tidak
              ada tombol yang mati di salah satu perangkat. */}
          <input
            ref={inputKamera}
            type="file"
            accept="image/*"
            capture="environment"
            hidden
            onChange={(e) => prosesBerkas(e.target.files)}
          />

          <div className="pemilih-glif">🖼</div>
          <div className="pemilih-ajakan">
            {sibuk
              ? `Memproses ${progres?.selesai ?? 0} dari ${progres?.total ?? 0} media...`
              : `Tarik ${izinkanVideo ? 'foto atau video' : 'foto'} ke sini, tempel, atau`}
          </div>
          {!sibuk && (
            <div className="pemilih-tombol">
              <button type="button" className="btn btn-primary btn-sm" onClick={() => inputBerkas.current?.click()}>
                Pilih dari Perangkat
              </button>
              <button type="button" className="btn btn-outline btn-sm" onClick={() => inputKamera.current?.click()}>
                Ambil Foto
              </button>
            </div>
          )}
          <div className="text-meta" style={{ marginTop: 8 }}>
            {hint || `${izinkanVideo ? 'JPG, PNG, WEBP, MP4, WebM, atau MOV' : 'JPG, PNG, atau WEBP'}. Foto besar otomatis dikecilkan sebelum diunggah${multiple ? `, maksimal ${max} media` : ''}.`}
          </div>
        </div>
      )}

      {galat && <div className="text-danger-sm" style={{ marginTop: 8 }}>{galat}</div>}

      {daftar.length > 0 && (
        <div className="pemilih-daftar">
          {daftar.map((gambar, i) => (
            <div className="pemilih-item" key={`${gambar.slice(0, 40)}-${i}`}>
              {izinkanVideo && /\.(mp4|webm|mov)(\?|$)/i.test(gambar)
                ? <video src={gambar} controls preload="metadata" />
                : <img src={gambar} alt="" onError={(e) => { e.target.classList.add('gagal'); }} />}
              <div className="pemilih-item-aksi" hidden={disabled}>
                {multiple && i > 0 && (
                  <button type="button" className="btn btn-outline btn-sm" onClick={() => jadikanSampul(i)}>
                    Jadikan Sampul
                  </button>
                )}
                <button type="button" className="btn btn-danger btn-sm" onClick={() => hapus(i)}>Hapus</button>
              </div>
              {multiple && i === 0 && <span className="pemilih-tanda">Sampul</span>}
              {adalahDataUrl(gambar) && (
                <span className="pemilih-tanda pemilih-tanda-kanan" title="Gambar disimpan di dalam dokumen, bukan di Storage">
                  tertanam
                </span>
              )}
            </div>
          ))}
        </div>
      )}

      {totalTertanam > 0 && (
        <div className="text-meta" style={{ marginTop: 8 }}>
          {formatUkuran(totalTertanam)} dari {formatUkuran(batasTertanam)} penyimpanan tertanam terpakai.
        </div>
      )}

      {daftar.length === 0 && disabled && (
        <div className="text-meta">Belum ada foto.</div>
      )}

      {allowUrl && !penuh && !disabled && (
        <div style={{ marginTop: 10 }}>
          {bukaUrl ? (
            <div className="flex-row">
              <input
                className="input"
                value={urlManual}
                placeholder="https://... tautan gambar"
                onChange={(e) => setUrlManual(e.target.value)}
                onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); tambahUrl(); } }}
              />
              <button type="button" className="btn btn-outline btn-sm" onClick={tambahUrl}>Tambah</button>
              <button type="button" className="btn btn-outline btn-sm" onClick={() => setBukaUrl(false)}>Tutup</button>
            </div>
          ) : (
            <button type="button" className="pemilih-tautan" onClick={() => setBukaUrl(true)}>
              atau tempel tautan gambar
            </button>
          )}
        </div>
      )}
    </div>
  );
}

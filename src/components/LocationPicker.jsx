import { useEffect, useRef, useState } from 'react';

/**
 * Pemilih lokasi layanan: otomatis, dicari, atau diisi manual.
 *
 * Sebelumnya titik layanan creator diisi lewat dua kolom angka mentah
 * (latitude & longitude). Praktis tidak ada orang yang hafal koordinat
 * tempatnya sendiri, jadi kolom itu hampir pasti dibiarkan kosong — dan fitur
 * "Nearby" di aplikasi, yang menghitung jarak dari koordinat tersebut, jadi
 * tidak pernah menemukan creator mana pun.
 *
 * Tersedia tiga jalur, semuanya mengisi kota DAN koordinat sekaligus:
 *   1. tombol "Gunakan Lokasi Saya" — dari GPS/perangkat, lalu dibalik jadi alamat;
 *   2. kotak pencarian nama tempat — dengan saran alamat;
 *   3. isian manual — tetap dipertahankan sebagai jalan keluar bila layanan peta
 *      sedang tidak dapat dihubungi atau titiknya perlu digeser sedikit.
 *
 * Layanan peta memakai Nominatim (OpenStreetMap): gratis, tanpa kunci API, dan
 * tidak menuntut kartu kredit — sejalan dengan pilihan lain di proyek ini yang
 * menghindari layanan berbayar. Batas wajarnya 1 permintaan/detik, karena itu
 * pencarian ditunda 700 ms setelah ketikan terakhir, bukan dikirim tiap huruf.
 */

const NOMINATIM = 'https://nominatim.openstreetmap.org';
const JEDA_KETIK = 700;

/** Ambil nama kota dari struktur alamat Nominatim yang bisa berbeda-beda. */
function ambilKota(alamat = {}) {
  return (
    alamat.city || alamat.town || alamat.municipality || alamat.county ||
    alamat.city_district || alamat.village || alamat.state || ''
  );
}

export default function LocationPicker({ nilai, onChange, disabled = false }) {
  const { city = '', lat = '', lng = '', address = '' } = nilai || {};

  const [kueri, setKueri] = useState('');
  const [saran, setSaran] = useState([]);
  const [mencari, setMencari] = useState(false);
  const [pesan, setPesan] = useState(null);
  const [manualTerbuka, setManualTerbuka] = useState(false);
  const abortRef = useRef(null);

  const perbarui = (bagian) => onChange({ ...nilai, ...bagian });

  // Pencarian ditunda; permintaan sebelumnya dibatalkan agar hasil lama yang
  // datang terlambat tidak menimpa hasil ketikan terbaru.
  useEffect(() => {
    const teks = kueri.trim();
    if (teks.length < 3) { setSaran([]); return; }

    const timer = setTimeout(async () => {
      abortRef.current?.abort();
      const kendali = new AbortController();
      abortRef.current = kendali;
      setMencari(true);
      setPesan(null);
      try {
        const res = await fetch(
          `${NOMINATIM}/search?format=jsonv2&addressdetails=1&limit=6&countrycodes=id&q=${encodeURIComponent(teks)}`,
          { signal: kendali.signal, headers: { 'Accept-Language': 'id' } }
        );
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const data = await res.json();
        setSaran(data);
        if (data.length === 0) setPesan({ tipe: 'info', teks: 'Tidak ada tempat yang cocok. Coba kata kunci lain, atau isi manual.' });
      } catch (err) {
        if (err.name !== 'AbortError') {
          setPesan({ tipe: 'gagal', teks: 'Layanan peta tidak dapat dihubungi. Anda tetap bisa mengisi manual di bawah.' });
          setManualTerbuka(true);
        }
      } finally {
        setMencari(false);
      }
    }, JEDA_KETIK);

    return () => clearTimeout(timer);
  }, [kueri]);

  const pilihSaran = (item) => {
    perbarui({
      address: item.display_name,
      city: ambilKota(item.address) || city,
      lat: Number(item.lat).toFixed(6),
      lng: Number(item.lon).toFixed(6),
    });
    setKueri('');
    setSaran([]);
    setPesan({ tipe: 'sukses', teks: 'Lokasi terpasang.' });
  };

  const gunakanLokasiSaya = () => {
    if (!navigator.geolocation) {
      setPesan({ tipe: 'gagal', teks: 'Peramban ini tidak mendukung deteksi lokasi. Silakan cari atau isi manual.' });
      setManualTerbuka(true);
      return;
    }
    setPesan({ tipe: 'info', teks: 'Meminta izin lokasi…' });
    navigator.geolocation.getCurrentPosition(
      async ({ coords }) => {
        const la = coords.latitude.toFixed(6);
        const lo = coords.longitude.toFixed(6);
        // Koordinat langsung dipasang lebih dulu: kalaupun pembalikan alamat
        // gagal, titiknya sudah benar dan itu yang dipakai fitur Nearby.
        perbarui({ lat: la, lng: lo });
        setPesan({ tipe: 'info', teks: 'Lokasi didapat, mencari nama alamatnya…' });
        try {
          const res = await fetch(
            `${NOMINATIM}/reverse?format=jsonv2&addressdetails=1&lat=${la}&lon=${lo}`,
            { headers: { 'Accept-Language': 'id' } }
          );
          const data = await res.json();
          perbarui({
            lat: la, lng: lo,
            address: data.display_name || '',
            city: ambilKota(data.address) || city,
          });
          setPesan({ tipe: 'sukses', teks: 'Lokasi Anda terpasang.' });
        } catch {
          setPesan({ tipe: 'info', teks: 'Koordinat terpasang, tetapi nama alamat gagal diambil. Isi kota secara manual bila perlu.' });
          setManualTerbuka(true);
        }
      },
      (err) => {
        const alasan = {
          1: 'Izin lokasi ditolak. Aktifkan izin lokasi di peramban, atau gunakan pencarian di bawah.',
          2: 'Lokasi tidak dapat ditentukan saat ini. Coba lagi atau gunakan pencarian.',
          3: 'Permintaan lokasi kehabisan waktu. Coba lagi atau gunakan pencarian.',
        }[err.code] || 'Gagal mengambil lokasi.';
        setPesan({ tipe: 'gagal', teks: alasan });
        setManualTerbuka(true);
      },
      { enableHighAccuracy: true, timeout: 12000, maximumAge: 60000 }
    );
  };

  const hapusLokasi = () => {
    perbarui({ address: '', lat: '', lng: '' });
    setPesan(null);
  };

  const adaTitik = lat !== '' && lng !== '' && lat !== null && lng !== null;

  return (
    <div className="lokasi">
      <div className="lokasi-aksi">
        <button type="button" className="btn btn-primary btn-sm" disabled={disabled} onClick={gunakanLokasiSaya}>
          Gunakan Lokasi Saya
        </button>
        <span className="text-meta">atau cari nama tempat</span>
      </div>

      <div className="lokasi-cari">
        <input
          className="input"
          placeholder="Contoh: Menteng Jakarta Pusat, atau Jalan Braga Bandung"
          value={kueri}
          disabled={disabled}
          onChange={(e) => setKueri(e.target.value)}
        />
        {mencari && <span className="lokasi-status">mencari…</span>}
        {saran.length > 0 && (
          <ul className="lokasi-saran">
            {saran.map((s) => (
              <li key={s.place_id}>
                <button type="button" onClick={() => pilihSaran(s)}>
                  <span className="lokasi-saran-nama">{s.name || s.display_name.split(',')[0]}</span>
                  <span className="lokasi-saran-alamat">{s.display_name}</span>
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>

      {pesan && (
        <p className={`lokasi-pesan lokasi-pesan-${pesan.tipe}`}>{pesan.teks}</p>
      )}

      {adaTitik && (
        <div className="lokasi-terpilih">
          <div className="lokasi-terpilih-teks">
            <strong>{city || 'Kota belum terisi'}</strong>
            <span className="text-meta">{address || 'Alamat tidak tercatat'}</span>
            <span className="text-meta num">{lat}, {lng}</span>
          </div>
          <div className="lokasi-terpilih-aksi">
            <a
              className="btn btn-outline btn-sm"
              href={`https://www.openstreetmap.org/?mlat=${lat}&mlon=${lng}#map=16/${lat}/${lng}`}
              target="_blank"
              rel="noreferrer"
            >
              Lihat Peta
            </a>
            <button type="button" className="btn btn-outline btn-sm" disabled={disabled} onClick={hapusLokasi}>
              Hapus
            </button>
          </div>
        </div>
      )}

      <button type="button" className="lokasi-toggle" onClick={() => setManualTerbuka((v) => !v)}>
        {manualTerbuka ? '− Sembunyikan isian manual' : '+ Isi manual (kota & koordinat)'}
      </button>

      {manualTerbuka && (
        <div className="lokasi-manual">
          <div>
            <label className="field-label">Kota</label>
            <input className="input" value={city} disabled={disabled}
              onChange={(e) => perbarui({ city: e.target.value })} />
          </div>
          <div>
            <label className="field-label">Latitude</label>
            <input className="input" type="number" step="any" value={lat} disabled={disabled}
              placeholder="-6.2607" onChange={(e) => perbarui({ lat: e.target.value })} />
          </div>
          <div>
            <label className="field-label">Longitude</label>
            <input className="input" type="number" step="any" value={lng} disabled={disabled}
              placeholder="106.7816" onChange={(e) => perbarui({ lng: e.target.value })} />
          </div>
        </div>
      )}
    </div>
  );
}

import LegalLayout from './LegalLayout';

/**
 * Kebijakan Privasi — WAJIB ada dan bisa diakses publik sebelum aplikasi
 * boleh terbit di Google Play.
 *
 * Isinya sengaja disusun dari apa yang benar-benar dilakukan kode, bukan dari
 * templat umum: izin yang diminta AndroidManifest.xml, field yang disimpan
 * UserModel.kt, serta layanan pihak ketiga yang nyata dipanggil (Firebase,
 * Cloudinary di StorageService.kt, Midtrans di PaymentRepository.kt).
 * Kebijakan yang tidak cocok dengan perilaku aplikasi lebih berbahaya daripada
 * tidak punya kebijakan sama sekali — Google memeriksanya terhadap formulir
 * Keamanan Data, dan ketidakcocokan berujung penangguhan.
 */
export default function Privacy() {
  return (
    <LegalLayout
      judul="Kebijakan Privasi"
      ringkas="Menjelaskan data apa yang JepretAja kumpulkan, untuk apa dipakai, dengan siapa dibagikan, dan bagaimana Anda mengendalikannya."
      diperbarui="6 September 2026"
    >
      <h2>1. Siapa kami</h2>
      <p>
        JepretAja adalah layanan pemesanan jasa fotografi dan videografi yang mempertemukan
        pelanggan dengan creator (fotografer/videografer). Kebijakan ini berlaku untuk aplikasi
        Android <strong>JepretAja</strong> (paket <code>com.jepretaja.app</code>) beserta panel
        web pengelolanya.
      </p>

      <h2>2. Data yang kami kumpulkan</h2>
      <h3>a. Data yang Anda berikan sendiri</h3>
      <ul>
        <li><strong>Identitas akun</strong> — nama, alamat email, dan nomor telepon saat mendaftar.</li>
        <li><strong>Foto profil</strong> — bila Anda mengunggahnya.</li>
        <li><strong>Data creator</strong> — bio, kota layanan, kategori jasa, peralatan, daftar harga, dan tautan media sosial, bila Anda mendaftar sebagai creator.</li>
        <li><strong>Dokumen verifikasi</strong> — berkas identitas yang Anda unggah untuk pengajuan verifikasi creator.</li>
        <li><strong>Foto dan video</strong> — karya yang Anda unggah ke Explore dan portfolio.</li>
        <li><strong>Isi percakapan</strong> — pesan antara pelanggan dan creator di dalam aplikasi.</li>
        <li><strong>Ulasan dan laporan</strong> — penilaian yang Anda tulis dan laporan yang Anda ajukan.</li>
      </ul>

      <h3>b. Data yang terkumpul otomatis</h3>
      <ul>
        <li><strong>Lokasi perkiraan dan presisi</strong> — hanya saat Anda memakai fitur pencarian creator terdekat (“Nearby”), dan hanya setelah Anda memberi izin. Lokasi dipakai untuk menghitung jarak; kami tidak menyimpan riwayat perjalanan Anda.</li>
        <li><strong>Token notifikasi (FCM)</strong> — penanda perangkat agar kami dapat mengirim pemberitahuan pesanan.</li>
        <li><strong>Data penggunaan dan laporan kerusakan</strong> — melalui Firebase Analytics dan Firebase Crashlytics, untuk memahami fitur mana yang dipakai dan memperbaiki aplikasi yang macet.</li>
        <li><strong>Riwayat transaksi</strong> — pesanan, pembayaran, penarikan dana, refund, dan sengketa.</li>
      </ul>

      <h3>c. Izin perangkat yang kami minta</h3>
      <ul>
        <li><strong>Kamera</strong> — mengambil foto/video untuk diunggah.</li>
        <li><strong>Foto dan video di perangkat</strong> — memilih berkas yang akan diunggah.</li>
        <li><strong>Lokasi</strong> — fitur creator terdekat.</li>
        <li><strong>Notifikasi</strong> — pemberitahuan status pesanan dan pesan masuk.</li>
        <li><strong>Internet dan status jaringan</strong> — menghubungkan aplikasi ke layanan kami.</li>
      </ul>
      <p>
        Semua izin di atas bersifat opsional kecuali internet. Menolaknya hanya menonaktifkan
        fitur terkait, tidak memblokir penggunaan aplikasi secara keseluruhan.
      </p>

      <h2>3. Untuk apa data dipakai</h2>
      <ul>
        <li>Membuat dan mengamankan akun Anda.</li>
        <li>Menampilkan profil creator dan karyanya kepada calon pelanggan.</li>
        <li>Memproses pemesanan, pembayaran, pencairan dana, refund, dan sengketa.</li>
        <li>Mengirim pemberitahuan terkait pesanan dan pesan.</li>
        <li>Memoderasi konten serta menangani laporan penyalahgunaan.</li>
        <li>Memperbaiki keandalan aplikasi lewat statistik penggunaan dan laporan kerusakan.</li>
      </ul>
      <p>
        Kami <strong>tidak menjual data pribadi Anda</strong> dan tidak menggunakannya untuk
        iklan bertarget dari pihak ketiga.
      </p>

      <h2>4. Pihak ketiga yang memproses data</h2>
      <p>Kami memakai penyedia layanan berikut, masing-masing dengan kebijakan privasinya sendiri:</p>
      <ul>
        <li><strong>Google Firebase</strong> (Authentication, Firestore, Cloud Messaging, Analytics, Crashlytics, App Check) — autentikasi, penyimpanan data, notifikasi, dan diagnostik.</li>
        <li><strong>Cloudinary</strong> — penyimpanan dan pengiriman foto serta video yang Anda unggah.</li>
        <li><strong>Penyedia bank</strong> — transfer manual dilakukan melalui bank yang Anda pilih sendiri. JepretAja tidak menerima atau menyimpan nomor kartu, PIN, password, atau OTP perbankan Anda.</li>
        <li><strong>Vercel</strong> — tempat berjalannya layanan server dan panel web kami.</li>
      </ul>
      <p>
        Kami juga dapat mengungkapkan data bila diwajibkan hukum yang berlaku atau untuk
        menyelidiki dugaan penipuan dan pelanggaran serius terhadap syarat layanan.
      </p>

      <h2>5. Data yang terlihat publik</h2>
      <p>
        Profil creator, foto dan video di Explore, portfolio, serta ulasan yang sudah tayang
        dapat dilihat oleh pengguna lain — memang itulah fungsinya sebagai etalase. Nomor
        telepon, alamat email, riwayat transaksi, dan isi percakapan Anda <strong>tidak</strong>
        {' '}pernah ditampilkan secara publik.
      </p>

      <h2>6. Berapa lama data disimpan</h2>
      <ul>
        <li><strong>Data akun</strong> — selama akun aktif.</li>
        <li><strong>Catatan transaksi</strong> — tetap disimpan setelah akun dihapus, sepanjang diwajibkan untuk pembukuan, perpajakan, dan penyelesaian sengketa.</li>
        <li><strong>Konten yang Anda unggah</strong> — dihapus bersama penghapusan akun, kecuali yang sudah menjadi bagian dari catatan pesanan.</li>
      </ul>

      <h2>7. Pembayaran, refund, dan sengketa</h2>
      <p>
        Pada versi layanan saat ini, pembayaran dilakukan melalui transfer manual ke rekening
        yang ditampilkan oleh sistem. Bukti dan status pembayaran dapat dicatat sebagai riwayat
        transaksi dan ditinjau admin. Pengajuan refund atau sengketa juga disimpan bersama
        alasan dan bukti yang Anda berikan agar keputusan dapat diaudit. Kami tidak meminta
        kredensial perbankan dan tidak dapat melihat PIN, password, atau OTP Anda.
      </p>

      <h2>8. Hak Anda</h2>
      <ul>
        <li><strong>Mengakses dan memperbaiki</strong> data melalui menu Profil di aplikasi.</li>
        <li><strong>Menghapus akun</strong> — lihat halaman <a href="/hapus-akun">Penghapusan Akun</a>.</li>
        <li><strong>Menarik izin</strong> lokasi, kamera, media, atau notifikasi kapan saja lewat Pengaturan Android.</li>
        <li><strong>Mengajukan keberatan</strong> atas pemrosesan data dengan menghubungi kami.</li>
      </ul>

      <h2>9. Keamanan</h2>
      <p>
        Seluruh komunikasi aplikasi terenkripsi lewat HTTPS. Akses ke basis data dibatasi
        aturan keamanan per pengguna, dan seluruh operasi keuangan dijalankan di sisi server
        setelah token login diverifikasi — bukan dipercayakan pada aplikasi. Meski demikian,
        tidak ada sistem yang sepenuhnya kebal; segera hubungi kami bila Anda menduga ada
        akses tidak sah pada akun Anda.
      </p>

      <h2>10. Anak-anak</h2>
      <p>
        JepretAja tidak ditujukan untuk pengguna di bawah 17 tahun dan kami tidak dengan
        sengaja mengumpulkan data anak. Bila Anda mengetahui adanya data semacam itu, beri
        tahu kami dan akan segera kami hapus.
      </p>

      <h2>11. Perubahan kebijakan</h2>
      <p>
        Bila kebijakan ini berubah secara berarti, kami akan memberitahukannya di dalam
        aplikasi dan memperbarui tanggal di bagian atas halaman ini.
      </p>

      <h2>12. Hubungi kami</h2>
      <p>
        Pertanyaan atau permintaan terkait data pribadi dapat dikirim ke{' '}
        <a href="mailto:support.soxvo@gmail.com">support.soxvo@gmail.com</a>. Kami berupaya
        menjawab dalam 7 hari kerja.
      </p>
    </LegalLayout>
  );
}

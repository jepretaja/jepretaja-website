import LegalLayout from './LegalLayout';

/**
 * Halaman penghapusan akun — WAJIB ada sejak kebijakan Google Play 2023 untuk
 * setiap aplikasi yang memungkinkan pembuatan akun.
 *
 * Syarat yang sering terlewat: URL-nya harus bisa dibuka TANPA memasang
 * aplikasi dan TANPA login, serta harus menyebutkan dengan jelas data apa yang
 * dihapus dan data apa yang tetap disimpan beserta alasannya. Karena itu
 * halaman ini berada di rute publik dan tidak memuat form yang menuntut sesi.
 */
export default function DeleteAccount() {
  const subjek = encodeURIComponent('Permintaan Penghapusan Akun JepretAja');
  const isi = encodeURIComponent(
    'Halo Tim JepretAja,\n\n'
    + 'Saya ingin menghapus akun saya beserta data pribadinya.\n\n'
    + 'Email akun    : \n'
    + 'Nomor telepon : \n'
    + 'Nama          : \n\n'
    + 'Terima kasih.'
  );

  return (
    <LegalLayout
      judul="Penghapusan Akun dan Data"
      ringkas="Dua cara menghapus akun JepretAja beserta rincian data yang dihapus dan yang tetap kami simpan."
      diperbarui="4 September 2026"
    >
      <h2>Cara 1 — Langsung dari aplikasi (paling cepat)</h2>
      <ol>
        <li>Buka aplikasi <strong>JepretAja</strong>.</li>
        <li>Masuk ke tab <strong>Profil</strong>.</li>
        <li>Pilih <strong>Hapus Akun</strong>.</li>
        <li>Konfirmasi permintaan.</li>
      </ol>
      <p className="legal-note">
        Demi keamanan, Firebase menolak penghapusan akun yang sesi loginnya sudah lama. Bila
        muncul pesan tersebut, keluar lalu masuk kembali, kemudian ulangi langkahnya.
      </p>

      <h2>Cara 2 — Lewat email (bila aplikasi tidak dapat diakses)</h2>
      <p>
        Kirim permintaan dari <strong>alamat email yang terdaftar pada akun Anda</strong> ke{' '}
        <a href="mailto:support.soxvo@gmail.com">support.soxvo@gmail.com</a> dengan subjek
        “Permintaan Penghapusan Akun”. Kami memverifikasi kepemilikan akun lebih dahulu, lalu
        memprosesnya dalam <strong>maksimal 30 hari kalender</strong>.
      </p>
      <p>
        <a className="btn btn-primary" href={`mailto:support.soxvo@gmail.com?subject=${subjek}&body=${isi}`}>
          Kirim Permintaan Penghapusan
        </a>
      </p>

      <h2>Data yang dihapus</h2>
      <ul>
        <li>Akun login (email dan kata sandi) beserta akses masuk Anda.</li>
        <li>Nama, nomor telepon, dan foto profil.</li>
        <li>Profil creator: bio, kota, kategori, peralatan, dan tautan sosial.</li>
        <li>Foto dan video yang Anda unggah ke Explore serta portfolio.</li>
        <li>Komentar, tanda suka, simpanan, dan daftar creator yang Anda ikuti.</li>
        <li>Token notifikasi perangkat Anda.</li>
      </ul>

      <h2>Data yang tetap kami simpan, dan mengapa</h2>
      <ul>
        <li>
          <strong>Catatan pesanan dan pembayaran</strong> — disimpan sepanjang diwajibkan
          peraturan pembukuan dan perpajakan, serta untuk menyelesaikan sengketa yang mungkin
          timbul. Catatan ini dipisahkan dari identitas Anda sebisa mungkin.
        </li>
        <li>
          <strong>Ulasan yang sudah tayang</strong> — tetap ditampilkan tanpa identitas Anda,
          karena menghapusnya akan mengubah rating creator lain secara tidak adil.
        </li>
        <li>
          <strong>Catatan audit tindakan admin</strong> — diperlukan untuk pertanggungjawaban
          internal atas keputusan keuangan.
        </li>
      </ul>
      <p>
        Data yang disimpan itu tidak lagi dipakai untuk menghubungi Anda maupun untuk keperluan
        lain di luar yang disebutkan.
      </p>

      <h2>Bila Anda punya saldo atau pesanan berjalan</h2>
      <p>
        Penghapusan akan <strong>ditolak sementara</strong> apabila akun Anda masih memiliki
        pesanan yang sedang berjalan atau saldo dompet yang belum dicairkan. Selesaikan atau
        batalkan pesanan tersebut dan cairkan saldo terlebih dahulu — ini untuk memastikan tidak
        ada uang yang tertinggal tanpa pemilik.
      </p>

      <h2>Pertanyaan</h2>
      <p>
        Hubungi <a href="mailto:support.soxvo@gmail.com">support.soxvo@gmail.com</a> bila proses
        penghapusan tidak berjalan atau Anda membutuhkan penjelasan lebih lanjut.
      </p>
    </LegalLayout>
  );
}

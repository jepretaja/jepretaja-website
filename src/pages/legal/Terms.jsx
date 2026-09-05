import LegalLayout from './LegalLayout';

/**
 * Syarat Layanan.
 *
 * Bukan syarat mutlak Google Play seperti kebijakan privasi, tetapi praktis
 * wajib untuk aplikasi yang memfasilitasi transaksi uang antar pengguna:
 * peninjau Play menanyakan siapa yang bertanggung jawab atas dana, bagaimana
 * pembatalan dan refund bekerja, dan bagaimana sengketa diselesaikan.
 */
export default function Terms() {
  return (
    <LegalLayout
      judul="Syarat Layanan"
      ringkas="Aturan penggunaan JepretAja bagi pelanggan maupun creator, termasuk soal pembayaran, pembatalan, dan sengketa."
      diperbarui="4 September 2026"
    >
      <h2>1. Penerimaan syarat</h2>
      <p>
        Dengan membuat akun atau menggunakan JepretAja, Anda menyatakan setuju pada syarat ini
        dan pada <a href="/privasi">Kebijakan Privasi</a> kami. Bila tidak setuju, mohon jangan
        menggunakan layanan ini.
      </p>

      <h2>2. Peran JepretAja</h2>
      <p>
        JepretAja adalah <strong>perantara</strong> yang mempertemukan pelanggan dengan creator
        serta menyediakan sarana pemesanan dan pembayaran. Jasa fotografi/videografi itu sendiri
        diberikan oleh creator, bukan oleh JepretAja. Kami tidak menjamin hasil karya tertentu,
        namun kami menyediakan mekanisme sengketa dan refund bila jasa tidak sesuai kesepakatan.
      </p>

      <h2>3. Akun</h2>
      <ul>
        <li>Anda wajib berusia minimal 17 tahun.</li>
        <li>Data yang Anda daftarkan harus benar dan mutakhir.</li>
        <li>Anda bertanggung jawab menjaga kerahasiaan kata sandi dan seluruh aktivitas di akun Anda.</li>
        <li>Satu orang tidak boleh membuat banyak akun untuk mengakali promo atau menghindari sanksi.</li>
      </ul>

      <h2>4. Kewajiban creator</h2>
      <ul>
        <li>Memberikan jasa sesuai paket yang dipesan: durasi, jumlah personel, dan hasil akhir.</li>
        <li>Hanya mengunggah karya milik sendiri atau yang Anda punya izin untuk menayangkannya.</li>
        <li>Menghormati privasi pelanggan — tidak menayangkan foto pelanggan tanpa persetujuan.</li>
        <li>Menyerahkan hasil sesuai tenggat yang dijanjikan.</li>
      </ul>

      <h2>5. Pembayaran dan dana ditahan (escrow)</h2>
      <ul>
        <li>Pembayaran diproses melalui penyedia pembayaran pihak ketiga (Midtrans) atau transfer manual yang diverifikasi admin.</li>
        <li>
          Dana pelanggan <strong>ditahan lebih dahulu</strong> dan baru diteruskan ke dompet
          creator setelah pekerjaan dinyatakan selesai. Ini melindungi kedua belah pihak.
        </li>
        <li>JepretAja memungut biaya layanan dari nilai transaksi; besarannya ditampilkan sebelum Anda membayar.</li>
        <li>Creator dapat mengajukan pencairan dana dari saldo tersedia, dengan batas minimum yang berlaku.</li>
      </ul>

      <h2>6. Pembatalan dan refund</h2>
      <ul>
        <li>Pembatalan oleh pelanggan sebelum pekerjaan dimulai dapat dikenai potongan sesuai kedekatan dengan tanggal acara.</li>
        <li>Pembatalan sepihak oleh creator setelah dikonfirmasi menghasilkan refund penuh bagi pelanggan.</li>
        <li>Permintaan refund diajukan lewat aplikasi dan ditinjau tim kami.</li>
      </ul>

      <h2>7. Sengketa</h2>
      <p>
        Bila hasil pekerjaan dianggap tidak sesuai, pelanggan maupun creator dapat membuka
        sengketa melalui aplikasi. Tim kami meninjau bukti dari kedua pihak dan dapat memutuskan
        untuk meneruskan dana ke creator, mengembalikan sebagian atau seluruhnya kepada
        pelanggan, atau menjadwalkan ulang pekerjaan. Keputusan disampaikan beserta alasannya.
      </p>

      <h2>8. Konten yang dilarang</h2>
      <ul>
        <li>Konten seksual eksplisit, kekerasan, atau yang melanggar hukum Indonesia.</li>
        <li>Karya milik orang lain yang diunggah tanpa izin.</li>
        <li>Ujaran kebencian, pelecehan, atau penipuan.</li>
        <li>Upaya memindahkan transaksi ke luar platform untuk menghindari biaya layanan.</li>
      </ul>
      <p>
        Konten yang melanggar dapat kami sembunyikan atau hapus, dan akun pelakunya dapat
        ditangguhkan.
      </p>

      <h2>9. Hak atas karya</h2>
      <p>
        Hak cipta atas foto dan video tetap milik creator yang membuatnya. Dengan mengunggahnya
        ke JepretAja, creator memberi kami izin terbatas untuk menampilkan karya tersebut di
        dalam aplikasi dan materi promosi layanan. Pelanggan memperoleh hak pakai atas hasil
        pesanannya sesuai kesepakatan pada paket.
      </p>

      <h2>10. Penangguhan akun</h2>
      <p>
        Kami dapat menangguhkan atau menghapus akun yang melanggar syarat ini, melakukan
        penipuan, atau merugikan pengguna lain. Bila akun ditangguhkan sementara ada dana
        tertahan, dana tersebut tetap diselesaikan sesuai mekanisme sengketa.
      </p>

      <h2>11. Batasan tanggung jawab</h2>
      <p>
        Sejauh diizinkan hukum yang berlaku, tanggung jawab JepretAja atas suatu pesanan
        terbatas pada nilai transaksi pesanan tersebut. Kami tidak bertanggung jawab atas
        kerugian tidak langsung seperti kehilangan keuntungan atau peluang.
      </p>

      <h2>12. Perubahan</h2>
      <p>
        Syarat ini dapat diperbarui. Perubahan yang berarti akan diberitahukan di dalam
        aplikasi, dan penggunaan setelah pemberitahuan berarti Anda menerima versi terbaru.
      </p>

      <h2>13. Hukum yang berlaku</h2>
      <p>
        Syarat ini tunduk pada hukum Republik Indonesia. Sengketa yang tidak dapat diselesaikan
        secara musyawarah akan diselesaikan di pengadilan yang berwenang di Indonesia.
      </p>

      <h2>14. Kontak</h2>
      <p>
        Pertanyaan mengenai syarat ini dapat dikirim ke{' '}
        <a href="mailto:support.soxvo@gmail.com">support.soxvo@gmail.com</a>.
      </p>
    </LegalLayout>
  );
}

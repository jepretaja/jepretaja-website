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
      diperbarui="6 September 2026"
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

      <h2>5. Pembayaran dan dana transaksi</h2>
      <ul>
        <li>Pada versi layanan saat ini, pembayaran dilakukan melalui transfer manual ke rekening yang ditampilkan di instruksi pembayaran dan diverifikasi admin.</li>
        <li>
          Dana yang sudah terverifikasi dicatat dalam sistem dan diproses sesuai status booking,
          penyelesaian layanan, refund, atau sengketa yang berlaku.
        </li>
        <li>JepretAja memungut biaya layanan dari nilai transaksi; besarannya ditampilkan sebelum Anda membayar.</li>
        <li>Creator dapat mengajukan pencairan dana dari saldo tersedia, dengan batas minimum yang berlaku.</li>
      </ul>

      <h2>6. Transfer manual dan batas tanggung jawab pembayaran</h2>
      <ul>
        <li>Transfer hanya boleh dilakukan ke rekening dan nominal yang ditampilkan di instruksi pembayaran resmi dalam aplikasi.</li>
        <li>Jangan mengirim PIN, password, OTP, atau kredensial perbankan kepada JepretAja, creator, atau siapa pun melalui chat.</li>
        <li>JepretAja tidak bertanggung jawab atas transfer ke rekening yang salah, nominal yang salah, biaya bank, keterlambatan bank, atau transfer yang dilakukan di luar instruksi resmi.</li>
        <li>Status pembayaran baru dianggap diterima setelah diverifikasi dan tercatat pada booking. Bukti transfer bukan jaminan pembayaran telah berhasil.</li>
        <li>Kami dapat meminta bukti tambahan dan menahan perubahan status sampai verifikasi selesai.</li>
      </ul>

      <h2>7. Pembatalan dan refund</h2>
      <ul>
        <li>Pembatalan hanya tersedia pada status booking yang diizinkan sistem. Hak pembatalan dapat berbeda menurut tahap layanan.</li>
        <li>Permintaan refund diajukan lewat aplikasi dengan alasan dan bukti yang relevan.</li>
        <li>Refund tidak otomatis disetujui. Admin meninjau status booking, bukti pembayaran, alasan, dan kontribusi masing-masing pihak.</li>
        <li>Keputusan dapat berupa refund penuh, refund sebagian, melanjutkan dana kepada creator, atau penutupan tanpa refund sesuai bukti dan keadaan kasus.</li>
        <li>Waktu dana kembali juga bergantung pada proses bank. JepretAja tidak menjanjikan waktu penyelesaian bank tertentu.</li>
      </ul>

      <h2>8. Sengketa</h2>
      <p>
        Bila hasil pekerjaan dianggap tidak sesuai, pelanggan maupun creator dapat membuka
        sengketa melalui aplikasi. Tim kami meninjau bukti dari kedua pihak dan dapat memutuskan
        untuk meneruskan dana ke creator, mengembalikan sebagian atau seluruhnya kepada
        pelanggan, atau menutup sengketa tanpa refund. Keputusan disampaikan beserta alasannya
        dan dicatat dalam riwayat kasus. Selama sengketa berlangsung, dana terkait dapat ditahan
        dari proses pencairan sampai keputusan dibuat.
      </p>

      <h2>9. Konten yang dilarang</h2>
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

      <h2>10. Hak atas karya</h2>
      <p>
        Hak cipta atas foto dan video tetap milik creator yang membuatnya. Dengan mengunggahnya
        ke JepretAja, creator memberi kami izin terbatas untuk menampilkan karya tersebut di
        dalam aplikasi dan materi promosi layanan. Pelanggan memperoleh hak pakai atas hasil
        pesanannya sesuai kesepakatan pada paket.
      </p>

      <h2>11. Penangguhan akun</h2>
      <p>
        Kami dapat menangguhkan atau menghapus akun yang melanggar syarat ini, melakukan
        penipuan, atau merugikan pengguna lain. Bila akun ditangguhkan sementara ada dana
        tertahan, dana tersebut tetap diselesaikan sesuai mekanisme sengketa.
      </p>

      <h2>12. Batasan tanggung jawab</h2>
      <p>
        Sejauh diizinkan hukum yang berlaku, tanggung jawab JepretAja atas suatu pesanan
        terbatas pada nilai transaksi pesanan tersebut, kecuali hukum yang berlaku menentukan
        lain. Kami tidak bertanggung jawab atas kerugian tidak langsung, kehilangan keuntungan,
        gangguan bank, kegagalan jaringan, atau tindakan creator/pelanggan di luar kendali kami.
      </p>

      <h2>13. Perubahan</h2>
      <p>
        Syarat ini dapat diperbarui. Perubahan yang berarti akan diberitahukan di dalam
        aplikasi, dan penggunaan setelah pemberitahuan berarti Anda menerima versi terbaru.
      </p>

      <h2>14. Hukum yang berlaku</h2>
      <p>
        Syarat ini tunduk pada hukum Republik Indonesia. Sengketa yang tidak dapat diselesaikan
        secara musyawarah akan diselesaikan di pengadilan yang berwenang di Indonesia.
      </p>

      <h2>15. Kontak</h2>
      <p>
        Pertanyaan mengenai syarat ini dapat dikirim ke{' '}
        <a href="mailto:support.soxvo@gmail.com">support.soxvo@gmail.com</a>.
      </p>
    </LegalLayout>
  );
}

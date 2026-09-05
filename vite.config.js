import { readFileSync, existsSync } from 'node:fs';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

/**
 * Menjalankan Serverless Function di folder api/ saat `npm run dev`.
 *
 * Di Vercel, file api/admin.js dan api/app.js otomatis dilayani pada
 * /api/admin dan /api/app. Vite dev server tidak tahu apa-apa soal itu:
 * tanpa plugin ini /api/admin membalas 404, sehingga SETIAP tombol aksi di
 * panel (konfirmasi pembayaran, setujui penarikan, undang admin, moderasi
 * konten) gagal saat dijalankan lokal — padahal kodenya benar dan jalan
 * normal setelah dideploy. Plugin ini menutup selisih itu supaya perilaku
 * lokal sama dengan produksi.
 *
 * Hanya aktif pada mode `serve` (dev), jadi tidak ikut ke hasil build.
 */
function vercelApiDevPlugin() {
  return {
    name: 'jepretaja-vercel-api-dev',
    apply: 'serve',
    configureServer(server) {
      // Akar proyek diambil dari Vite, BUKAN dari import.meta.url.
      //
      // Vite mem-bundle berkas konfigurasi ini ke lokasi sementara sebelum
      // menjalankannya, sehingga import.meta.url menunjuk ke folder temp —
      // `new URL('./api/...', import.meta.url)` lalu teresolusi ke berkas yang
      // tidak ada, middleware ini diam-diam melepas setiap permintaan, dan
      // /api/admin membalas 404 seolah plugin tidak terpasang sama sekali.
      const akar = server.config.root;
      const jalur = (...bagian) => join(akar, ...bagian);

      // Handler di api/ membaca kredensial dari process.env, sementara Vite
      // hanya memuat variabel berawalan VITE_ ke sisi browser. Variabel
      // server (FIREBASE_SERVICE_ACCOUNT) karena itu dimuat manual di sini.
      const envPath = jalur('.env');
      if (existsSync(envPath)) {
        for (const baris of readFileSync(envPath, 'utf8').split('\n')) {
          const teks = baris.trim();
          if (!teks || teks.startsWith('#')) continue;
          const pisah = teks.indexOf('=');
          if (pisah === -1) continue;
          const nama = teks.slice(0, pisah).trim();
          if (!process.env[nama]) process.env[nama] = teks.slice(pisah + 1).trim();
        }
      }

      // Handler di-import sekali lalu dipakai ulang: modul Node tidak bisa
      // di-hot-reload seperti modul di src/. Konsekuensinya, perubahan pada
      // file di folder api/ baru berlaku setelah dev server di-restart.
      //
      // Itu jebakan yang mudah menipu — server tetap membalas 200 dengan kode
      // LAMA seolah perubahan sudah berlaku. Karena itu folder api/ diawasi
      // dan perubahannya diberi peringatan mencolok, bukan didiamkan.
      const cacheHandler = new Map();

      server.watcher.add(jalur('api'));
      server.watcher.on('change', (berkas) => {
        if (!berkas.replace(/\\/g, '/').includes('/api/')) return;
        server.config.logger.warn(
          `\n  [api] ${berkas} berubah, tapi handler yang sedang dimuat masih versi LAMA.` +
            '\n  [api] Restart dev server (Ctrl+C lalu `npm run dev`) supaya perubahan berlaku.\n'
        );
      });

      server.middlewares.use(async (req, res, next) => {
        const path = (req.url || '').split('?')[0];
        if (!path.startsWith('/api/')) return next();

        const nama = path.slice('/api/'.length).replace(/\/$/, '');
        if (!/^[a-z0-9_-]+$/i.test(nama)) return next();

        const berkas = jalur('api', `${nama}.js`);
        if (!existsSync(berkas)) return next();

        // Vercel menyediakan res.status().json(); res bawaan Node tidak punya.
        res.status = (kode) => {
          res.statusCode = kode;
          return res;
        };
        res.json = (isi) => {
          res.setHeader('Content-Type', 'application/json');
          res.end(JSON.stringify(isi));
          return res;
        };

        try {
          // Vercel juga sudah mem-parse body JSON sebelum handler dipanggil.
          const potongan = [];
          for await (const c of req) potongan.push(c);
          const mentah = Buffer.concat(potongan).toString('utf8');
          req.body = mentah ? JSON.parse(mentah) : {};
        } catch {
          return res.status(400).json({
            error: { code: 'invalid-argument', message: 'Body request bukan JSON yang valid.' },
          });
        }

        try {
          if (!cacheHandler.has(nama)) {
            // Wajib file URL, bukan path Windows mentah: `import('C:\\...')`
            // ditolak loader ESM karena "C:" terbaca sebagai skema protokol.
            cacheHandler.set(nama, (await import(pathToFileURL(berkas).href)).default);
          }
          await cacheHandler.get(nama)(req, res);
        } catch (err) {
          server.config.logger.error(`[api/${nama}] ${err?.stack || err}`);
          if (!res.writableEnded) {
            res.status(500).json({
              error: { code: 'internal', message: String(err?.message || err) },
            });
          }
        }
      });
    },
  };
}

const pkg = JSON.parse(readFileSync(new URL('./package.json', import.meta.url), 'utf8'));

export default defineConfig({
  plugins: [react(), vercelApiDevPlugin()],
  // Versi aplikasi ditampilkan di footer halaman depan. Diambil dari
  // package.json saat build supaya tidak pernah lupa diperbarui manual.
  define: { __APP_VERSION__: JSON.stringify(pkg.version) },
  // host: true membuat dev server mendengarkan di SEMUA alamat jaringan, bukan
  // hanya localhost. Tanpa ini, APK di HP (atau emulator) tidak akan pernah
  // bisa menghubungi http://<ip-laptop>:5173 — koneksinya ditolak seolah
  // servernya mati, padahal di browser laptop terlihat jalan normal.
  server: { port: 5173, host: true },
  build: {
    rollupOptions: {
      output: {
        // Pisahkan vendor besar (Firebase SDK, Recharts) dari kode aplikasi
        // supaya browser bisa cache terpisah dan chunk awal lebih kecil.
        manualChunks: {
          'vendor-firebase': ['firebase/app', 'firebase/auth', 'firebase/firestore', 'firebase/app-check'],
          'vendor-recharts': ['recharts'],
          'vendor-react': ['react', 'react-dom', 'react-router-dom'],
        },
      },
    },
  },
});

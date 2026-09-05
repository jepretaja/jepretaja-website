import { createContext, useContext, useEffect, useState } from 'react';
import { onAuthStateChanged, signInWithEmailAndPassword, signOut } from 'firebase/auth';
import { doc, getDoc } from 'firebase/firestore';
import { auth, db } from '../firebase/config';
import { PATHS } from '../firebase/paths';

/**
 * Auth panel web. Ada DUA jenis akun yang boleh masuk, dengan area berbeda:
 *
 *  1. Admin / super_admin (koleksi admin_users)  -> dashboard operasional,
 *     melihat SELURUH pengguna, pembayaran, sengketa.
 *  2. Creator (koleksi creators)                 -> portal creator, HANYA
 *     melihat data miliknya sendiri.
 *
 * Pemisahan ini disengaja dan penting: halaman admin menampilkan data
 * pelanggan dan keuangan seluruh platform. Creator tidak boleh masuk ke
 * sana, jadi mereka diarahkan ke portal terpisah, bukan diberi akses
 * dengan menu yang disembunyikan.
 */
const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [adminProfile, setAdminProfile] = useState(null);
  const [creatorProfile, setCreatorProfile] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    return onAuthStateChanged(auth, async (firebaseUser) => {
      setUser(firebaseUser);
      if (firebaseUser) {
        // Wajib: pembacaan peran di bawah ini asinkron. Tanpa menandai
        // loading, sesaat setelah login `user` sudah terisi tapi
        // adminProfile/creatorProfile masih null — gerbang layout akan
        // menyimpulkan "tidak punya akses" dan melempar balik ke /login.
        setLoading(true);
        // Paksa refresh ID token supaya custom claims (role) yang baru
        // di-set oleh syncAdminClaims (trigger admin_users) langsung
        // terbaca, bukan menunggu refresh otomatis ~1 jam kemudian.
        await firebaseUser.getIdToken(true);

        // Cek admin dulu. Kalau seseorang kebetulan terdaftar sebagai admin
        // sekaligus creator, hak admin yang dipakai.
        const adminSnap = await getDoc(doc(db, PATHS.adminUsers, firebaseUser.uid))
          .catch(() => null);
        const isAdmin = adminSnap?.exists();
        setAdminProfile(isAdmin ? { id: adminSnap.id, ...adminSnap.data() } : null);

        if (isAdmin) {
          setCreatorProfile(null);
        } else {
          const creatorSnap = await getDoc(doc(db, PATHS.creators, firebaseUser.uid))
            .catch(() => null);
          setCreatorProfile(
            creatorSnap?.exists() ? { id: creatorSnap.id, ...creatorSnap.data() } : null
          );
        }
      } else {
        setAdminProfile(null);
        setCreatorProfile(null);
      }
      setLoading(false);
    });
  }, []);

  const login = (email, password) => signInWithEmailAndPassword(auth, email, password);
  const logout = () => signOut(auth);

  const role = adminProfile ? 'admin' : creatorProfile ? 'creator' : null;

  return (
    <AuthContext.Provider
      value={{ user, adminProfile, creatorProfile, role, loading, login, logout }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  return useContext(AuthContext);
}

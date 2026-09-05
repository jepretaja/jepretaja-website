import { Suspense, lazy } from 'react';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { AuthProvider } from './auth/AuthContext';
import AdminLayout from './layouts/AdminLayout';
import CreatorLayout from './layouts/CreatorLayout';
import RequirePermission from './components/RequirePermission';
import Login from './pages/login/Login';
import Landing from './pages/landing/Landing';
import Dashboard from './pages/dashboard/Dashboard';

// Code-splitting per halaman (section 47) — sebelumnya seluruh Admin Web
// jadi satu chunk ~1.1MB. Setiap halaman sekarang di-load on-demand saat
// route-nya dibuka, bukan semua sekaligus di awal.
const UserList = lazy(() => import('./pages/users/UserList'));
const UserDetail = lazy(() => import('./pages/users/UserDetail'));
const CreatorList = lazy(() => import('./pages/creators/CreatorList'));
const CreatorDetail = lazy(() => import('./pages/creators/CreatorDetail'));
const CreatorVerification = lazy(() => import('./pages/creators/CreatorVerification'));
const ExploreModeration = lazy(() => import('./pages/explore/ExploreModeration'));
const PostDetail = lazy(() => import('./pages/explore/PostDetail'));
const BookingList = lazy(() => import('./pages/bookings/BookingList'));
const BookingDetail = lazy(() => import('./pages/bookings/BookingDetail'));
const TransactionList = lazy(() => import('./pages/payments/TransactionList'));
const TransactionDetail = lazy(() => import('./pages/payments/TransactionDetail'));
const HeldFunds = lazy(() => import('./pages/payments/HeldFunds'));
const ManualTransfers = lazy(() => import('./pages/payments/ManualTransfers'));
const Wallets = lazy(() => import('./pages/wallets/Wallets'));
const WithdrawalList = lazy(() => import('./pages/withdrawals/WithdrawalList'));
const WithdrawalDetail = lazy(() => import('./pages/withdrawals/WithdrawalDetail'));
const Refunds = lazy(() => import('./pages/refunds/Refunds'));
const Disputes = lazy(() => import('./pages/disputes/Disputes'));
const DisputeDetail = lazy(() => import('./pages/disputes/DisputeDetail'));
const ReviewModeration = lazy(() => import('./pages/reviews/ReviewModeration'));
const Reports = lazy(() => import('./pages/reports/Reports'));
const Categories = lazy(() => import('./pages/categories/Categories'));
const Promotions = lazy(() => import('./pages/promotions/Promotions'));
const Notifications = lazy(() => import('./pages/notifications/Notifications'));
const Analytics = lazy(() => import('./pages/analytics/Analytics'));
const Settings = lazy(() => import('./pages/settings/Settings'));
const AdminUsers = lazy(() => import('./pages/adminusers/AdminUsers'));
const AuditLogs = lazy(() => import('./pages/auditlogs/AuditLogs'));
const CommentModeration = lazy(() => import('./pages/explore/CommentModeration'));
const PackageCatalog = lazy(() => import('./pages/packages/PackageCatalog'));
const PortfolioModeration = lazy(() => import('./pages/portfolios/PortfolioModeration'));
const ChatMonitor = lazy(() => import('./pages/chats/ChatMonitor'));
const MyAccount = lazy(() => import('./pages/account/MyAccount'));
const PlatformRevenue = lazy(() => import('./pages/platform/PlatformRevenue'));

// Route penampung untuk alamat yang tidak dikenal. Tanpa ini URL salah ketik
// berakhir di layar putih tanpa pesan apa pun.
const NotFound = lazy(() => import('./pages/notfound/NotFound'));

// Portal creator — area terpisah dari dashboard admin. Semua halaman di
// bawah /creator hanya menampilkan data milik creator yang sedang login.
// Dokumen publik untuk syarat penerbitan di Google Play.
const Privacy = lazy(() => import('./pages/legal/Privacy'));
const Terms = lazy(() => import('./pages/legal/Terms'));
const DeleteAccount = lazy(() => import('./pages/legal/DeleteAccount'));

const CreatorHome = lazy(() => import('./pages/creatorportal/CreatorHome'));
const CreatorProfile = lazy(() => import('./pages/creatorportal/CreatorProfile'));
const CreatorPortfolio = lazy(() => import('./pages/creatorportal/CreatorPortfolio'));
const CreatorBookings = lazy(() => import('./pages/creatorportal/CreatorBookings'));
const CreatorPackages = lazy(() => import('./pages/creatorportal/CreatorPackages'));
const CreatorWallet = lazy(() => import('./pages/creatorportal/CreatorWallet'));
const CreatorReviews = lazy(() => import('./pages/creatorportal/CreatorReviews'));
const CreatorBookingDetail = lazy(() => import('./pages/creatorportal/CreatorBookingDetail'));
const CreatorAvailability = lazy(() => import('./pages/creatorportal/CreatorAvailability'));
const CreatorChats = lazy(() => import('./pages/creatorportal/CreatorChats'));
const CreatorNotifications = lazy(() => import('./pages/creatorportal/CreatorNotifications'));
const CreatorStatistics = lazy(() => import('./pages/creatorportal/CreatorStatistics'));
const CreatorPosts = lazy(() => import('./pages/creatorportal/CreatorPosts'));
// Namanya sengaja dibedakan dari CreatorVerification milik admin di atas:
// yang itu ANTRIAN peninjauan, yang ini FORMULIR pengajuan.
const CreatorVerificationRequest = lazy(() => import('./pages/creatorportal/CreatorVerification'));

function PageFallback() {
  return <div className="loading">Memuat halaman...</div>;
}

function Lazy({ children }) {
  return <Suspense fallback={<PageFallback />}>{children}</Suspense>;
}

/** Semua route Admin Web (section 29), dibungkus RequirePermission untuk
 * route sensitif — lapisan kedua selain sidebar filtering (section 73). */
export default function App() {
  return (
    <BrowserRouter
      // Mengaktifkan perilaku React Router v7 lebih awal. Selain menyiapkan
      // migrasi, ini juga menghilangkan dua peringatan "Future Flag Warning"
      // yang sebelumnya muncul di console pada SETIAP halaman dan menenggelamkan
      // pesan error yang benar-benar perlu dilihat.
      future={{ v7_startTransition: true, v7_relativeSplatPath: true }}
    >
      <AuthProvider>
        <Routes>
          {/* Halaman depan publik: pusat informasi sebelum masuk. Sengaja
              berada DI LUAR AdminLayout supaya bisa dibuka tanpa sesi login —
              status sistem justru paling dibutuhkan saat terjadi gangguan. */}
          <Route path="/" element={<Landing />} />
          {/* Dokumen publik. Google Play mewajibkan URL kebijakan privasi dan
              URL penghapusan akun dapat dibuka tanpa memasang aplikasi dan
              tanpa login — karena itu ketiganya berada di luar AdminLayout. */}
          <Route path="/privasi" element={<Lazy><Privacy /></Lazy>} />
          <Route path="/syarat" element={<Lazy><Terms /></Lazy>} />
          <Route path="/hapus-akun" element={<Lazy><DeleteAccount /></Lazy>} />
          <Route path="/login" element={<Login />} />
          <Route path="/creator" element={<CreatorLayout />}>
            <Route index element={<Lazy><CreatorHome /></Lazy>} />
            <Route path="profile" element={<Lazy><CreatorProfile /></Lazy>} />
            <Route path="portfolio" element={<Lazy><CreatorPortfolio /></Lazy>} />
            <Route path="posts" element={<Lazy><CreatorPosts /></Lazy>} />
            <Route path="bookings" element={<Lazy><CreatorBookings /></Lazy>} />
            <Route path="bookings/:id" element={<Lazy><CreatorBookingDetail /></Lazy>} />
            <Route path="packages" element={<Lazy><CreatorPackages /></Lazy>} />
            <Route path="availability" element={<Lazy><CreatorAvailability /></Lazy>} />
            <Route path="wallet" element={<Lazy><CreatorWallet /></Lazy>} />
            <Route path="reviews" element={<Lazy><CreatorReviews /></Lazy>} />
            <Route path="chats" element={<Lazy><CreatorChats /></Lazy>} />
            <Route path="notifications" element={<Lazy><CreatorNotifications /></Lazy>} />
            <Route path="statistics" element={<Lazy><CreatorStatistics /></Lazy>} />
            <Route path="verification" element={<Lazy><CreatorVerificationRequest /></Lazy>} />
          </Route>
          <Route element={<AdminLayout />}>
            <Route path="/dashboard" element={<Dashboard />} />
            <Route path="/users" element={<Lazy><RequirePermission permission="view_users"><UserList /></RequirePermission></Lazy>} />
            <Route path="/users/:id" element={<Lazy><RequirePermission permission="view_users"><UserDetail /></RequirePermission></Lazy>} />
            <Route path="/creators" element={<Lazy><RequirePermission permission="view_users"><CreatorList /></RequirePermission></Lazy>} />
            <Route path="/creators/:id" element={<Lazy><RequirePermission permission="view_users"><CreatorDetail /></RequirePermission></Lazy>} />
            <Route path="/creator-verification" element={<Lazy><RequirePermission permission="verify_creator"><CreatorVerification /></RequirePermission></Lazy>} />
            <Route path="/explore" element={<Lazy><RequirePermission permission="moderate_content"><ExploreModeration /></RequirePermission></Lazy>} />
            <Route path="/explore/:id" element={<Lazy><RequirePermission permission="moderate_content"><PostDetail /></RequirePermission></Lazy>} />
            <Route path="/bookings" element={<Lazy><RequirePermission permission="manage_booking"><BookingList /></RequirePermission></Lazy>} />
            <Route path="/bookings/:id" element={<Lazy><RequirePermission permission="manage_booking"><BookingDetail /></RequirePermission></Lazy>} />
            <Route path="/payments" element={<Lazy><RequirePermission permission="manage_payment"><TransactionList /></RequirePermission></Lazy>} />
            <Route path="/payments/manual" element={<Lazy><RequirePermission permission="manage_payment"><ManualTransfers /></RequirePermission></Lazy>} />
            <Route path="/payments/:id" element={<Lazy><RequirePermission permission="manage_payment"><TransactionDetail /></RequirePermission></Lazy>} />
            <Route path="/escrow" element={<Lazy><RequirePermission permission="manage_escrow"><HeldFunds /></RequirePermission></Lazy>} />
            <Route path="/wallets" element={<Lazy><RequirePermission permission="manage_escrow"><Wallets /></RequirePermission></Lazy>} />
            <Route path="/withdrawals" element={<Lazy><RequirePermission permission="manage_withdrawal"><WithdrawalList /></RequirePermission></Lazy>} />
            <Route path="/withdrawals/:id" element={<Lazy><RequirePermission permission="manage_withdrawal"><WithdrawalDetail /></RequirePermission></Lazy>} />
            <Route path="/refunds" element={<Lazy><RequirePermission permission="manage_refund"><Refunds /></RequirePermission></Lazy>} />
            <Route path="/disputes" element={<Lazy><RequirePermission permission="manage_dispute"><Disputes /></RequirePermission></Lazy>} />
            <Route path="/disputes/:id" element={<Lazy><RequirePermission permission="manage_dispute"><DisputeDetail /></RequirePermission></Lazy>} />
            <Route path="/reviews" element={<Lazy><RequirePermission permission="moderate_content"><ReviewModeration /></RequirePermission></Lazy>} />
            <Route path="/comments" element={<Lazy><RequirePermission permission="moderate_content"><CommentModeration /></RequirePermission></Lazy>} />
            <Route path="/packages" element={<Lazy><RequirePermission permission="moderate_content"><PackageCatalog /></RequirePermission></Lazy>} />
            <Route path="/portfolios" element={<Lazy><RequirePermission permission="moderate_content"><PortfolioModeration /></RequirePermission></Lazy>} />
            <Route path="/chats" element={<Lazy><RequirePermission permission="manage_dispute"><ChatMonitor /></RequirePermission></Lazy>} />
            <Route path="/reports" element={<Lazy><RequirePermission permission="manage_booking"><Reports /></RequirePermission></Lazy>} />
            <Route path="/categories" element={<Lazy><RequirePermission permission="manage_promotion"><Categories /></RequirePermission></Lazy>} />
            <Route path="/promotions" element={<Lazy><RequirePermission permission="manage_promotion"><Promotions /></RequirePermission></Lazy>} />
            <Route path="/notifications" element={<Lazy><RequirePermission permission="manage_promotion"><Notifications /></RequirePermission></Lazy>} />
            <Route path="/analytics" element={<Lazy><RequirePermission permission="view_analytics"><Analytics /></RequirePermission></Lazy>} />
            <Route path="/settings" element={<Lazy><RequirePermission permission="manage_settings"><Settings /></RequirePermission></Lazy>} />
            <Route path="/admin-users" element={<Lazy><RequirePermission permission="manage_admin"><AdminUsers /></RequirePermission></Lazy>} />
            <Route path="/audit-logs" element={<Lazy><RequirePermission permission="manage_admin"><AuditLogs /></RequirePermission></Lazy>} />
            {/* Kas platform: hanya super_admin yang punya izin ini, dan server
                memeriksa perannya lagi pada setiap aksinya. */}
            <Route path="/kas-platform" element={<Lazy><RequirePermission permission="manage_platform_payout"><PlatformRevenue /></RequirePermission></Lazy>} />
            {/* Akun sendiri — tanpa RequirePermission: setiap admin, apa pun
                perannya, berhak melihat identitas dan mengganti sandinya. */}
            <Route path="/akun" element={<Lazy><MyAccount /></Lazy>} />
          </Route>
          {/* Harus paling akhir: route ini menangkap semua alamat yang tidak
              cocok dengan satu pun di atasnya. */}
          <Route path="*" element={<Lazy><NotFound /></Lazy>} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  );
}

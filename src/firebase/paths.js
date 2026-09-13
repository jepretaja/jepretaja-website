// Nama koleksi Firestore — harus sama persis dengan object FirestorePaths di
// sisi APK: app/src/main/java/com/jepretaja/app/core/util/FirestorePaths.kt
//
// (Rujukan lama di komentar ini menyebut lib/core/constants/firestore_paths.dart
// milik versi Flutter; berkas itu sudah tidak dipakai sejak APK ditulis ulang
// dengan Kotlin.)
//
// Beberapa koleksi di bawah tidak dibaca panel admin sama sekali — chats,
// messages, follows, explore_likes, dan seterusnya hanya dipakai aplikasi.
// Nama-nama itu tetap didaftarkan di sini supaya kedua sisi punya SATU daftar
// istilah yang sama: saat aturan Firestore atau alat admin nanti perlu
// menyentuhnya, nama koleksinya tidak perlu ditebak ulang dan tidak ada risiko
// salah eja yang baru ketahuan setelah rilis.
export const PATHS = {
  users: 'users',
  creators: 'creators',

  explorePosts: 'explore_posts',
  exploreComments: 'explore_comments',
  exploreLikes: 'explore_likes',
  exploreSaves: 'explore_saves',
  exploreCommentLikes: 'explore_comment_likes',
  follows: 'follows',

  portfolios: 'portfolios',
  packages: 'packages',
  bookings: 'bookings',
  availabilityBlocks: 'availability_blocks',

  chats: 'chats',
  messages: 'messages',
  blockedUsers: 'blocked_users',
  liveLocations: 'live_locations',
  locationTracks: 'location_tracks',
  supportAlerts: 'support_alerts',

  payments: 'payments',
  escrowTransactions: 'escrow_transactions',
  wallets: 'wallets',
  walletTransactions: 'wallet_transactions',
  withdrawals: 'withdrawals',
  refunds: 'refunds',
  disputes: 'disputes',

  reviews: 'reviews',
  notifications: 'notifications',
  reports: 'reports',
  categories: 'categories',
  promotions: 'promotions',
  settings: 'settings',
  adminUsers: 'admin_users',
  auditLogs: 'audit_logs',

  // Kas platform: saldo komisi yang terkumpul dan riwayat pencairannya ke
  // rekening pemilik. Keduanya hanya ditulis Admin SDK di /api/admin.
  platformWallet: 'platform_wallet',
  platformPayouts: 'platform_payouts',
};

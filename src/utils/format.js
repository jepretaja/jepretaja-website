export function formatCurrency(value) {
  const n = Number(value) || 0;
  return new Intl.NumberFormat('id-ID', { style: 'currency', currency: 'IDR', maximumFractionDigits: 0 }).format(n);
}

export function formatDate(value) {
  if (!value) return '-';
  const date = value?.toDate ? value.toDate() : new Date(value);
  return new Intl.DateTimeFormat('id-ID', { day: 'numeric', month: 'short', year: 'numeric' }).format(date);
}

export function formatDateTime(value) {
  if (!value) return '-';
  const date = value?.toDate ? value.toDate() : new Date(value);
  return new Intl.DateTimeFormat('id-ID', { day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' }).format(date);
}

export function compactNumber(value) {
  return new Intl.NumberFormat('id-ID', { notation: 'compact' }).format(Number(value) || 0);
}

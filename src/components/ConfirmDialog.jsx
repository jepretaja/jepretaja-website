import { useState } from 'react';

/**
 * Modal konfirmasi generik — dipakai sebelum aksi berbahaya/finansial
 * (suspend, reject, approve withdrawal, resolve dispute, dst). Sebelumnya
 * aksi-aksi ini langsung jalan begitu tombol diklik tanpa konfirmasi sama
 * sekali.
 */
export function useConfirm() {
  const [state, setState] = useState(null); // { title, message, danger, onConfirm }

  function confirm({ title, message, danger = false, confirmLabel = 'Ya, lanjutkan' }) {
    return new Promise((resolve) => {
      setState({ title, message, danger, confirmLabel, resolve });
    });
  }

  function handle(result) {
    state?.resolve(result);
    setState(null);
  }

  const dialog = state ? (
    <div style={overlayStyle}>
      <div style={cardStyle}>
        <h3 style={{ marginBottom: 8 }}>{state.title}</h3>
        <p style={{ color: 'var(--text-secondary)', fontSize: 13.5, marginBottom: 20 }}>{state.message}</p>
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10 }}>
          <button className="btn btn-outline btn-sm" onClick={() => handle(false)}>Batal</button>
          <button className={`btn btn-sm ${state.danger ? 'btn-danger' : 'btn-primary'}`} onClick={() => handle(true)}>
            {state.confirmLabel}
          </button>
        </div>
      </div>
    </div>
  ) : null;

  return { confirm, dialog };
}

const overlayStyle = {
  position: 'fixed', inset: 0, background: 'rgba(20,23,31,0.45)',
  display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000,
};
const cardStyle = {
  width: 360, background: 'var(--surface)', borderRadius: 14, padding: 22,
  boxShadow: '0 20px 60px rgba(0,0,0,0.25)',
};

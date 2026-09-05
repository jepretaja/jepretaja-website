export default function SearchInput({ value, onChange, placeholder = 'Cari...' }) {
  return (
    <div className="search-input">
      <span className="icon">⌕</span>
      <input className="input" value={value} onChange={(e) => onChange(e.target.value)} placeholder={placeholder} />
    </div>
  );
}

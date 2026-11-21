export function AttributesCase() {
  return (
    <main>
      <label htmlFor="field" className="label wide" data-kind="primary" aria-label="Field label">Field</label>
      <input id="field" readOnly value="fixed" tabIndex={3} />
      <button disabled>Disabled</button>
      <button disabled={false}>Enabled</button>
      <p data-testid="styled" style={{ color: 'rgb(255, 0, 0)', opacity: 0.5 }}>styled</p>
    </main>
  );
}

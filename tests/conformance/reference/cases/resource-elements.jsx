export function ResourceElements() {
  const css = '.bench-styled { color: rgb(0, 128, 0); }';
  return (
    <main>
      <style href="bench-style" precedence="default">{css}</style>
      <style href="bench-style" precedence="default">{css}</style>
      <p className="bench-styled" data-testid="styled">styled</p>
    </main>
  );
}

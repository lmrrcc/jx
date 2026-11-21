function Badge({ label = 'default label', tone }) {
  return <p data-testid={'badge-' + label.replace(' ', '-')} data-tone={tone}>{label}</p>;
}

export function DefaultProps() {
  return (
    <main>
      <Badge />
      <Badge label="given" tone="warm" />
    </main>
  );
}

function Label({ text, tone }) {
  return <p data-testid="label" data-tone={tone}>{text}</p>;
}

export function SpreadProps() {
  const attributes = { 'data-kind': 'spread', title: 'from spread' };
  const props = { text: 'spread text', tone: 'calm' };
  return (
    <main>
      <button {...attributes} title="explicit wins">Later wins</button>
      <a title="explicit loses" {...attributes}>Earlier loses</a>
      <Label {...props} />
    </main>
  );
}

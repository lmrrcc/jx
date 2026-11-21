export function RawHtml() {
  return (
    <main>
      <div data-testid="raw" dangerouslySetInnerHTML={{ __html: '<b>bold</b> and <i>italic</i>' }} />
    </main>
  );
}

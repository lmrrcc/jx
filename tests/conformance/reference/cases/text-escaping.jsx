export function TextEscaping() {
  const markup = '<b>bold</b><img src=x onerror=alert(1)>';
  return (
    <main>
      <p data-testid="string">{markup}</p>
      <p data-testid="entities">&amp; &lt; &gt; &quot; &apos; &#65; &#x42;</p>
      <p data-testid="attribute" title="a &amp; b">attribute</p>
    </main>
  );
}

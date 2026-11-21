import { useState } from 'react';

export function PreventDefault() {
  const [clicks, setClicks] = useState(0);
  const [submitted, setSubmitted] = useState(false);
  return (
    <main>
      <a href="#moved" onClick={event => {
        event.preventDefault();
        setClicks(previous => previous + 1);
      }}>Stay</a>
      <a href="#moved">Go</a>
      <form onSubmit={event => {
        event.preventDefault();
        setSubmitted(true);
      }}>
        <button type="submit">Submit</button>
      </form>
      <p role="status">{clicks} {submitted ? 'submitted' : 'idle'}</p>
    </main>
  );
}

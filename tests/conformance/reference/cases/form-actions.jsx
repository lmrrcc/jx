import { useState } from 'react';

export function FormActions() {
  const [result, setResult] = useState('none');
  return (
    <main>
      <form action={formData => setResult('form ' + formData.get('name'))}>
        <input name="name" defaultValue="Ada" aria-label="Name" />
        <button type="submit">Save</button>
        <button type="submit" formAction={formData => setResult('alternative ' + formData.get('name'))}>Save differently</button>
      </form>
      <p role="status">{result}</p>
    </main>
  );
}

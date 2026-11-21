import { useState } from 'react';

export function ChoiceInputs() {
  const [size, setSize] = useState('small');
  const [color, setColor] = useState('red');
  const [notes, setNotes] = useState('first');
  return (
    <main>
      <label><input type="radio" name="size" value="small" checked={size === 'small'} onChange={event => setSize(event.target.value)} />Small</label>
      <label><input type="radio" name="size" value="large" checked={size === 'large'} onChange={event => setSize(event.target.value)} />Large</label>
      <select aria-label="Color" value={color} onChange={event => setColor(event.target.value)}>
        <option value="red">Red</option>
        <option value="green">Green</option>
        <option value="blue">Blue</option>
      </select>
      <textarea aria-label="Notes" value={notes} onChange={event => setNotes(event.target.value)} />
      <p role="status">{size}|{color}|{notes}</p>
      <button onClick={() => {
        setSize('small');
        setColor('blue');
        setNotes('reset');
      }}>Reset</button>
    </main>
  );
}

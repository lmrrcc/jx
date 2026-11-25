'use client';
import {createElement, useState} from 'react';
export function Counter({initial, label}) {
  const [count, setCount] = useState(initial);
  return createElement('button', {onClick: () => setCount(value => value + 1)}, label, ': ', count);
}

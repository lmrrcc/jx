import { useEffect, useInsertionEffect, useLayoutEffect } from 'react';
import { log } from '../bench.js';

export function InsertionEffect() {
  useEffect(() => { log('effect'); }, []);
  useLayoutEffect(() => { log('layout'); }, []);
  useInsertionEffect(() => { log('insertion'); }, []);
  return <main><p role="status">mounted</p></main>;
}

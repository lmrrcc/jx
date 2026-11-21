import { useEffect, useLayoutEffect } from 'react';
import { log } from '../bench.js';

export function LayoutEffectOrder() {
  useEffect(() => {
    log('effect');
    return () => log('cleanup effect');
  }, []);
  useLayoutEffect(() => {
    log('layout');
    return () => log('cleanup layout');
  }, []);
  return <main><p role="status">mounted</p></main>;
}

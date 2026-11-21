import { preconnect, prefetchDNS, preinit, preinitModule, preload, preloadModule } from 'react-dom';

export function ResourceHints() {
  preconnect('https://connect.example.test');
  prefetchDNS('https://dns.example.test');
  preload('data:text/css,.preloaded{}', { as: 'style' });
  preinit('data:text/javascript,globalThis.__benchPreinit=1', { as: 'script' });
  preloadModule('data:text/javascript,export default 1');
  preinitModule('data:text/javascript,globalThis.__benchPreinitModule=1');
  return <main><p role="status">hinted</p></main>;
}

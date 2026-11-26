// Framework/interop bootstrap only. Application behavior lives in Java and .jx.
import initialize, {compress, decompress} from '../../node_modules/brotli-wasm/pkg.web/brotli_wasm.js';

try {
  await initialize(new URL('./brotli_wasm_bg.wasm', import.meta.url));
  globalThis.__JX_BROTLI__ = Object.freeze({compress, decompress});
  await import('./application.js');
  document.getElementById('startup').remove();
} catch (error) {
  document.getElementById('startup').textContent = 'WASM initialization failed';
  throw error;
}

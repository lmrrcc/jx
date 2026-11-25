// Shared by generated development bootstraps and independent-generation browser probes.
export const hotRuntimeVersion = 2;
export function assertHotRuntime() {
  const active = globalThis.__JX_HOT__;
  if (active?.version && active.version !== hotRuntimeVersion)
    throw new Error('Incompatible JX hot runtime; load a fresh development page');
}

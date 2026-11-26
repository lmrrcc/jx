// Shared by generated development bootstraps and independent-generation browser probes.
export function compiledHotRuntimeVersion(runtime) {
  // Calling an exported Java method can initialize TeaVM's application entry class.
  // Read the protocol marker emitted by that compiled runtime without invoking it.
  if (typeof runtime !== 'function') throw new Error('Missing compiled JX hot runtime attestation');
  const markers = Object.getOwnPropertyNames(runtime).filter(name => /^protocol[0-9]+$/.test(name));
  if (markers.length !== 1 || !/^protocol[1-9][0-9]*$/.test(markers[0])
      || typeof Object.getOwnPropertyDescriptor(runtime, markers[0]).value !== 'function')
    throw new Error('Invalid compiled JX hot runtime attestation');
  const version = Number(markers[0].slice('protocol'.length));
  if (!Number.isSafeInteger(version)) throw new Error('Invalid compiled JX hot runtime attestation');
  return version;
}

export function assertHotRuntime(hotRuntimeVersion) {
  if (!Number.isSafeInteger(hotRuntimeVersion) || hotRuntimeVersion < 1)
    throw new Error('Missing compiled JX hot runtime attestation');
  const active = globalThis.__JX_HOT__;
  if (active?.version && active.version !== hotRuntimeVersion)
    throw new Error(`Incompatible JX hot runtime (active ${active.version}, candidate ${hotRuntimeVersion}); load a fresh development page`);
  if (active?.version === 2 && (!(active.classOwners instanceof Map)
      || !(active.incomingClasses instanceof Map) || !active.classRootContext))
    throw new Error('Incomplete JX hot runtime; load a fresh development page');
}

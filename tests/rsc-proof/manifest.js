// Build-owned Flight references for this proof. No request selects a module path.
export function validateManifest(manifest, modules) {
  if (manifest === null || typeof manifest !== 'object' || Array.isArray(manifest) ||
      modules.length !== 2 || Object.keys(manifest).length !== modules.length) {
    throw new Error('Invalid proof client manifest');
  }
  for (const module of modules) {
    const entry = manifest[module];
    if (!Object.hasOwn(manifest, module) || entry === null || typeof entry !== 'object' ||
        entry.id !== './' + module.slice(module.lastIndexOf('/') + 1) || entry.name !== '*' ||
        !Array.isArray(entry.chunks) || entry.chunks.length < 2 || entry.chunks.length > 16 || entry.chunks.length % 2) {
      throw new Error('Invalid proof client reference');
    }
    for (let index = 0; index < entry.chunks.length; index += 2) {
      if (typeof entry.chunks[index] !== 'string' || !/^[a-zA-Z0-9_-]{1,100}$/.test(entry.chunks[index]) ||
          typeof entry.chunks[index + 1] !== 'string' || !/^[a-zA-Z0-9_.-]{1,150}\.js$/.test(entry.chunks[index + 1])) {
        throw new Error('Invalid proof client chunk');
      }
    }
  }
  return manifest;
}

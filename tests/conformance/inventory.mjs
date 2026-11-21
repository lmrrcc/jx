// Framework test fixture: lists what the installed React packages export.
// `node tests/conformance/inventory.mjs --write` regenerates react-exports.txt.
import {readFile, writeFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';

export const MODULES = ['react', 'react/jsx-runtime', 'react-dom', 'react-dom/client', 'react-dom/server', 'react-dom/static'];
export const FILE = fileURLToPath(new URL('./react-exports.txt', import.meta.url));

/** Returns sorted `module:name` entries for every named export, without bundler internals. */
export async function installedExports() {
  const entries = [];
  for (const name of MODULES) {
    const module = await import(name);
    for (const key of Object.keys(module)) {
      if (key !== 'default' && !key.startsWith('__')) entries.push(name + ':' + key);
    }
  }
  return entries.sort();
}

export async function recordedExports() {
  return (await readFile(FILE, 'utf8')).split('\n').filter(Boolean);
}

if (process.argv.includes('--write')) {
  const entries = await installedExports();
  await writeFile(FILE, entries.join('\n') + '\n');
  console.log('Wrote ' + entries.length + ' exports to ' + FILE);
}

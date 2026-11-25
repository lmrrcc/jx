import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFile, readdir} from 'node:fs/promises';
import {fileURLToPath, pathToFileURL} from 'node:url';
import {dirname, resolve} from 'node:path';
import {validateManifest} from './manifest.js';

const here = dirname(fileURLToPath(import.meta.url));
const modules = ['jx', 'reference'].map(side => pathToFileURL(resolve(here, side + '-client.js')).href);
const manifest = JSON.parse(await readFile(resolve(here, 'target/site/react-client-manifest.json'), 'utf8'));

test('generated client references are bounded and every advertised chunk exists', async () => {
  validateManifest(manifest, modules);
  for (const entry of Object.values(manifest)) {
    for (let index = 1; index < entry.chunks.length; index += 2) {
      assert.ok((await readFile(resolve(here, 'target/site', entry.chunks[index]))).length > 0);
    }
  }
  for (const invalid of [null, [], {}, {...manifest, unknown: manifest[modules[0]]}]) {
    assert.throws(() => validateManifest(invalid, modules), /Invalid proof/);
  }
  const swapped = structuredClone(manifest);
  swapped[modules[0]].id = manifest[modules[1]].id;
  assert.throws(() => validateManifest(swapped, modules), /Invalid proof/);
  for (const chunks of [[], ['id'], ['id', '../server.cjs'], ['id', 'https://remote/chunk.js'], [7, 'chunk.js'],
    Array(18).fill('chunk.js')]) {
    const invalid = structuredClone(manifest);
    invalid[modules[0]].chunks = chunks;
    assert.throws(() => validateManifest(invalid, modules), /Invalid proof/);
  }
});

test('server application code and sources are absent from all browser chunks and source maps', async () => {
  const server = await readFile(resolve(here, 'target/server.cjs'), 'utf8');
  assert.ok(server.includes('ServerPage'), 'positive control: the server application really was built');
  const files = (await readdir(resolve(here, 'target/site'))).filter(name => /\.(js|map)$/.test(name));
  assert.ok(files.length >= 4, 'inspect both client chunks and maps, not only the entry point');
  let javaClient = false;
  for (const name of files) {
    const source = await readFile(resolve(here, 'target/site', name), 'utf8');
    if (source.includes('__jxRscClient')) javaClient = true;
    for (const forbidden of ['ServerPage', 'ServerEntry', 'jx.fixture.rsc.server', '__jxRscProof', '/server/src/']) {
      assert.ok(!source.includes(forbidden), name + ' contains server code/source: ' + forbidden);
    }
  }
  assert.ok(javaClient, 'the compiled Java client must be included in the checked artifacts');
});

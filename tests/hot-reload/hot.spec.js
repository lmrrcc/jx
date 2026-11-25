import { test, expect } from '@playwright/test';
import { cp, mkdir, mkdtemp, readFile, writeFile } from 'node:fs/promises';
import { createWriteStream } from 'node:fs';
import { spawn } from 'node:child_process';
import { once } from 'node:events';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { compiledHotRuntimeVersion } from '../../jx-maven-plugin/src/main/resources/jx-hot-protocol.js';

const root = fileURLToPath(new URL('../..', import.meta.url));

async function development(example, port, run, { sourceRuntime = false } = {}) {
  await mkdir(path.join(root, '.cache/hot-reload'), { recursive: true });
  const project = await mkdtemp(path.join(root, '.cache/hot-reload', example + '-'));
  await cp(path.join(root, 'examples', example, 'src'), path.join(project, 'src'), { recursive: true });
  let pom = (await readFile(path.join(root, 'examples', example, 'pom.xml'), 'utf8'))
    .replace('<relativePath>../../pom.xml</relativePath>', '<relativePath>../../../pom.xml</relativePath>');
  if (sourceRuntime) {
    // Compile the actual runtime in this owned copy, so protocol drift never changes a shared JAR.
    await cp(path.join(root, 'jx-react/src/main/java'), path.join(project, 'src/main/java'), { recursive: true });
    const runtimeDependency = '<dependency><groupId>dev.jx</groupId><artifactId>jx-react</artifactId><version>${project.version}</version></dependency>';
    if (!pom.includes(runtimeDependency)) throw new Error('Runtime fixture dependency no longer matches the example POM');
    pom = pom.replace(runtimeDependency, '<dependency><groupId>dev.jx</groupId><artifactId>jx-core</artifactId><version>${project.version}</version></dependency>\n'
      + '<dependency><groupId>org.teavm</groupId><artifactId>teavm-jso</artifactId><version>${teavm.version}</version></dependency>\n'
      + '<dependency><groupId>org.teavm</groupId><artifactId>teavm-jso-apis</artifactId><version>${teavm.version}</version></dependency>');
  }
  await writeFile(path.join(project, 'pom.xml'), pom);
  // A development session must not overwrite assets served by a separate production/backend process.
  await mkdir(path.join(project, 'target/site'), { recursive: true });
  await writeFile(path.join(project, 'target/site/application.js'), 'production artifact sentinel');
  const logPath = path.join(project, 'development.log');
  const log = createWriteStream(logPath);
  const server = spawn(path.join(root, 'mvnw'), ['-B', '-ntp', '-Dmaven.repo.local=' + path.join(root, '.cache/maven-repository'),
    '-f', path.join(project, 'pom.xml'), 'dev.jx:jx-maven-plugin:0.1.0-SNAPSHOT:dev', '-Djx.port=' + port],
    { cwd: root, stdio: ['ignore', 'pipe', 'pipe'] });
  server.stdout.pipe(log); server.stderr.pipe(log);
  const url = 'http://127.0.0.1:' + port;
  try {
    await expect.poll(async () => {
      if (server.exitCode != null) throw new Error('Maven dev exited: ' + await readFile(logPath, 'utf8'));
      try { return (await (await fetch(url + '/__jx_dev/version')).json()).state; } catch { return 'starting'; }
    }, { timeout: 180_000, intervals: [500] }).toBe('ready');
    expect(await readFile(path.join(project, 'target/site/application.js'), 'utf8')).toBe('production artifact sentinel');
    await run({ project, url });
  } catch (failure) {
    await test.info().attach('development.log', { path: logPath, contentType: 'text/plain' });
    throw failure;
  } finally {
    if (server.exitCode == null) {
      const closed = once(server, 'exit'); server.kill('SIGTERM');
      let timeout;
      try { await Promise.race([closed, new Promise(resolve => { timeout = setTimeout(() => { server.kill('SIGKILL'); resolve(); }, 10_000); })]); }
      finally { clearTimeout(timeout); }
    }
    log.end();
  }
}

async function edit(file, transform) {
  await writeFile(file, transform(await readFile(file, 'utf8')));
}

test('runtime attestation accepts one version marker without invoking exports or getters', () => {
  function compiled() {}
  compiled.protocol2 = () => { throw new Error('entry initializer must not run'); };
  compiled.teavmMetadata = {};
  expect(compiledHotRuntimeVersion(compiled)).toBe(2);
  compiled.protocol1 = () => {};
  expect(() => compiledHotRuntimeVersion(compiled)).toThrow('Invalid compiled JX hot runtime attestation');
  expect(() => compiledHotRuntimeVersion(function missing() {})).toThrow('Invalid compiled JX hot runtime attestation');
  function accessor() {}
  Object.defineProperty(accessor, 'protocol2', {get() { throw new Error('getter must not run'); }});
  expect(() => compiledHotRuntimeVersion(accessor)).toThrow('Invalid compiled JX hot runtime attestation');
});

test('real .jx saves preserve counter state, apply new code/CSS, survive errors and reset changed hooks', async ({ page }) => {
  await development('counter-app', 4673, async ({ project, url }) => {
    const errors = []; page.on('pageerror', error => errors.push(error.message));
    await page.addInitScript(() => {
      globalThis.rootListenerRegistrations = 0;
      const add = EventTarget.prototype.addEventListener;
      EventTarget.prototype.addEventListener = function(...args) {
        if (this === document || this.id === 'root') globalThis.rootListenerRegistrations++;
        return add.apply(this, args);
      };
    });
    await page.goto(url);
    await page.evaluate(() => { globalThis.documentSentinel = 'same browser document'; });
    const first = page.getByRole('region', { name: 'First counter' });
    const second = page.getByRole('region', { name: 'Second counter' });
    await first.getByRole('button', { name: 'Add three to First counter' }).click();
    await second.getByRole('button', { name: 'Increment Second counter', exact: true }).click();
    await expect(first.getByRole('status')).toHaveText('3');
    const listeners = await page.evaluate(() => {
      globalThis.vendorSentinel = globalThis.__JX_VENDOR__.React;
      return globalThis.rootListenerRegistrations;
    });
    const counter = path.join(project, 'src/main/jx/jx/example/counter/Counter.jx');
    const app = path.join(project, 'src/main/jx/jx/example/counter/App.jx');
    await edit(counter, text => text.replace('previous -> previous + 1', 'previous -> previous + 2'));
    await edit(app, text => text.replace('Two counters. One Java application.', 'Updated Java application.'));
    await edit(path.join(project, 'src/main/web/style.css'), text => text + '\n.counter { border-top-width: 7px; }\n');
    // Input remains live while Maven builds; the checkpoint must include this later click too.
    await first.getByRole('button', { name: 'Increment First counter', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Updated Java application.' })).toBeVisible();
    await expect(first.getByRole('status')).toHaveText('4');
    await expect(second.getByRole('status')).toHaveText('11');
    await expect(first).toHaveCSS('border-top-width', '7px');
    await first.getByRole('button', { name: 'Increment First counter', exact: true }).click();
    await expect(first.getByRole('status')).toHaveText('6');

    const valid = await readFile(counter, 'utf8');
    await writeFile(counter, valid.replace('Hooks.useState(props.initial())', 'Hooks.useState(unknownSymbol)'));
    await expect(page.locator('#__jx_dev_notice')).toContainText('JX build failed');
    await first.getByRole('button', { name: 'Increment First counter', exact: true }).click();
    await expect(first.getByRole('status')).toHaveText('8');
    await writeFile(counter, valid.replace('Increment {props.label()}', 'Increase {props.label()}'));
    await expect(first.getByRole('button', { name: 'Increase First counter', exact: true })).toBeVisible();
    await expect(first.getByRole('status')).toHaveText('8');
    await expect(second.getByRole('status')).toHaveText('11');
    await expect(page.locator('#__jx_dev_notice')).toHaveCount(0);

    const recovered = await readFile(counter, 'utf8');
    await writeFile(counter, recovered.replace('return (', 'if (props.initial() >= 0) throw new IllegalStateException("intentional hot fixture");\n        return ('));
    await expect(page.locator('#__jx_dev_notice')).toContainText('JX hot update failed');
    await writeFile(counter, recovered.replace('Increase {props.label()}', 'Recovered {props.label()}'));
    await expect(first.getByRole('button', { name: 'Recovered First counter', exact: true })).toBeVisible();
    await expect(first.getByRole('status')).toHaveText('8');
    await expect(second.getByRole('status')).toHaveText('11');

    await edit(counter, text => text.replace('State<Integer> count =', 'State<String> added = Hooks.useState("new hook");\n        State<Integer> count ='));
    await expect(first.getByRole('status')).toHaveText('0');
    await expect(second.getByRole('status')).toHaveText('10');
    expect(await page.evaluate(() => globalThis.documentSentinel)).toBe('same browser document');
    expect(await page.evaluate(() => globalThis.__JX_VENDOR__.React === globalThis.vendorSentinel)).toBe(true);
    expect(await page.evaluate(() => globalThis.rootListenerRegistrations)).toBe(listeners);
    expect(errors.length).toBeGreaterThan(0);
    expect(errors.filter(message => !message.includes('intentional hot fixture'))).toEqual([]);
  });
});

test('compiled runtime drift and a missing attestation reject replacement before teardown', async ({ page }) => {
  await development('counter-app', 4673, async ({ project, url }) => {
    const errors = []; page.on('pageerror', error => errors.push(error.message));
    await page.goto(url);
    const first = page.getByRole('region', { name: 'First counter' });
    await first.getByRole('button', { name: 'Increment First counter', exact: true }).click();
    await expect(first.getByRole('status')).toHaveText('1');
    const originalNode = await first.getByRole('status').elementHandle();
    await page.evaluate(() => {
      globalThis.hotProtocolCalls = [];
      const hot = globalThis.__JX_HOT__;
      for (const name of ['capture', 'dispose']) {
        const operation = hot[name];
        hot[name] = (...args) => { globalThis.hotProtocolCalls.push(name); return operation(...args); };
      }
    });
    const runtime = path.join(project, 'src/main/java/jx/react/HotRuntime.java');
    const validRuntime = await readFile(runtime, 'utf8');
    expect(validRuntime).toContain('VERSION = 2');
    // Maven/javac/TeaVM rebuild the real JX runtime with its preceding protocol identifier.
    await writeFile(runtime, validRuntime.replace('VERSION = 2', 'VERSION = 1'));
    await expect(page.locator('#__jx_dev_notice')).toContainText('JX hot update failed');
    expect(errors).toEqual([expect.stringContaining('active 2, candidate 1')]);
    expect(await page.evaluate(() => globalThis.hotProtocolCalls)).toEqual([]);
    expect(await first.getByRole('status').evaluate((current, old) => current === old, originalNode)).toBe(true);
    await originalNode.dispose();
    await first.getByRole('button', { name: 'Increment First counter', exact: true }).click();
    await expect(first.getByRole('status')).toHaveText('2');
    await writeFile(runtime, validRuntime);
    const app = path.join(project, 'src/main/jx/jx/example/counter/App.jx');
    await edit(app, text => text.replace('Two counters. One Java application.', 'Attested Java application.'));
    await expect(page.getByRole('heading', { name: 'Attested Java application.' })).toBeVisible();
    await expect(first.getByRole('status')).toHaveText('2');
    await expect(page.locator('#__jx_dev_notice')).toHaveCount(0);
    await page.evaluate(() => { globalThis.hotProtocolCalls = []; });
    const node = await first.getByRole('status').elementHandle();
    const published = path.join(project, 'target/jx/dev-site/application.js');
    const previousBundle = await readFile(published, 'utf8');
    const entry = path.join(project, 'src/main/java/jx/example/counter/Main.java');
    const valid = await readFile(entry, 'utf8');
    // Compile a real main-only TeaVM module, as produced before runtime attestation existed.
    await writeFile(entry, 'package jx.example.counter;\npublic final class Main { public static void main(String[] args) {} }\n');
    await expect(page.locator('#__jx_dev_notice')).toContainText('JX build failed');
    expect(await readFile(path.join(project, 'development.log'), 'utf8')).toMatch(/No matching export.*JxHotRuntime/s);
    expect(await readFile(published, 'utf8')).toBe(previousBundle);
    expect(await page.evaluate(() => globalThis.hotProtocolCalls)).toEqual([]);
    expect(await first.getByRole('status').evaluate((current, old) => current === old, node)).toBe(true);
    await node.dispose();
    await first.getByRole('button', { name: 'Increment First counter', exact: true }).click();
    await expect(first.getByRole('status')).toHaveText('3');

    await writeFile(entry, valid);
    await edit(app, text => text.replace('Attested Java application.', 'Recovered Java application.'));
    await expect(page.getByRole('heading', { name: 'Recovered Java application.' })).toBeVisible();
    await expect(page.locator('#__jx_dev_notice')).toHaveCount(0);
    await expect(first.getByRole('status')).toHaveText('3');
    await first.getByRole('button', { name: 'Increment First counter', exact: true }).click();
    await expect(first.getByRole('status')).toHaveText('4');
    expect(errors).toEqual([expect.stringContaining('active 2, candidate 1')]);
  }, { sourceRuntime: true });
});

test('real Todo .jx save keeps unsaved form/focus, URL and typed Elm model without replaying HTTP', async ({ page }) => {
  await development('todo-app', 4773, async ({ project, url }) => {
    const errors = []; page.on('pageerror', error => errors.push(error.message));
    let reads = 0;
    const todos = [{ id: 'hot-fixture', title: 'Preserved Java record', completed: false, version: 0 }];
    await page.route(url + '/api/todos', async route => {
      if (route.request().method() === 'GET') { reads++; await route.fulfill({ json: todos }); }
      else {
        const created = { id: 'new-fixture', title: route.request().postDataJSON().title, completed: false, version: 0 };
        todos.push(created); await route.fulfill({ status: 201, json: created });
      }
    });
    await page.goto(url + '/todos?filter=active');
    await expect(page.getByText('Preserved Java record')).toBeVisible();
    const input = page.getByRole('textbox', { name: 'What needs doing?' });
    await input.fill('Created before update');
    await page.getByRole('button', { name: 'Add task', exact: true }).click();
    await expect(page.getByText('Created before update')).toBeVisible();
    await expect(input).toHaveValue('');
    await input.fill('Unsaved draft – Java');
    await input.evaluate(element => element.setSelectionRange(3, 8));
    const before = reads;
    await page.evaluate(() => { globalThis.documentSentinel = 'same Todo document'; });
    await edit(path.join(project, 'src/main/jx/jx/example/todo/Page.jx'), text => text.replace('One thing at a time.', 'Java hot update complete.'));
    await expect(page.getByRole('heading', { name: 'Java hot update complete.' })).toBeVisible();
    await expect(page.getByText('Preserved Java record')).toBeVisible();
    await expect(page.getByText('Created before update')).toBeVisible();
    await expect(input).toHaveValue('Unsaved draft – Java');
    await expect(input).toBeFocused();
    expect(await input.evaluate(element => [element.selectionStart, element.selectionEnd])).toEqual([3, 8]);
    expect(page.url()).toBe(url + '/todos?filter=active');
    expect(reads).toBe(before);
    expect(await page.evaluate(() => globalThis.documentSentinel)).toBe('same Todo document');
    await page.getByRole('button', { name: 'Add task', exact: true }).click();
    await expect(page.getByText('Unsaved draft – Java', { exact: true })).toBeVisible();
    expect(errors).toEqual([]);
  });
});

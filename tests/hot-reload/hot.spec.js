import { test, expect } from '@playwright/test';
import { cp, mkdir, mkdtemp, readFile, writeFile } from 'node:fs/promises';
import { createWriteStream } from 'node:fs';
import { spawn } from 'node:child_process';
import { once } from 'node:events';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('../..', import.meta.url));

async function development(example, port, run) {
  await mkdir(path.join(root, '.cache/hot-reload'), { recursive: true });
  const project = await mkdtemp(path.join(root, '.cache/hot-reload', example + '-'));
  await cp(path.join(root, 'examples', example, 'src'), path.join(project, 'src'), { recursive: true });
  const pom = (await readFile(path.join(root, 'examples', example, 'pom.xml'), 'utf8'))
    .replace('<relativePath>../../pom.xml</relativePath>', '<relativePath>../../../pom.xml</relativePath>');
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

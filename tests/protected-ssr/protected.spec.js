import {test, expect, request as requests} from '@playwright/test';
import {get as httpGet} from 'node:http';
import {readFile} from 'node:fs/promises';

const backend = 'http://127.0.0.1:4885';
const host = 'http://127.0.0.1:4884';
const proofHeaders = () => ({'x-jx-proof': process.env.PROTECTED_PROOF_TOKEN});

async function authenticate(username) {
  const login = await requests.newContext({baseURL: backend});
  try {
    const anonymous = await (await login.get('/api/session')).json();
    const response = await login.post('/api/session/login', {
      form: {username, password: 'test-only-password', _csrf: anonymous.csrfToken}, maxRedirects: 0,
    });
    expect(response.status()).toBe(303);
    const session = await (await login.get('/api/session')).json();
    expect(session.username).toBe(username);
    const storageState = await login.storageState();
    const api = await requests.newContext({baseURL: backend, storageState,
      extraHTTPHeaders: {'X-CSRF-TOKEN': session.csrfToken}});
    return {api, storageState, csrfToken: session.csrfToken};
  } finally { await login.dispose(); }
}

async function seed(api, title) {
  const current = await (await api.get('/api/todos')).json();
  for (const todo of current) {
    expect((await api.delete(`/api/todos/${todo.id}?version=${todo.version}`)).status()).toBe(204);
  }
  const created = await api.post('/api/todos', {data: {title}});
  expect(created.status()).toBe(201);
  return created.json();
}

async function metrics(control, id) {
  const response = await control.get('/__proof/state');
  expect(response.status()).toBe(200);
  return (await response.json()).requests.find(item => item.id === id);
}

function observe(page) {
  const errors = [], network = [];
  page.on('pageerror', error => errors.push(error.message));
  page.on('console', message => { if (message.type() === 'error') errors.push(message.text()); });
  page.on('request', request => network.push(request.url()));
  return {errors, network};
}

async function holdApplication(page) {
  let release;
  const released = new Promise(resolve => { release = resolve; });
  await page.route('**/assets/application.js', async route => { await released; await route.continue(); });
  return release;
}

test('concurrent authenticated SSR retains per-request immutable data and hydrates without a second load', async ({browser}) => {
  const alice = await authenticate('demo'), bob = await authenticate('other');
  const control = await requests.newContext({baseURL: host, extraHTTPHeaders: proofHeaders()});
  const contexts = [];
  const releases = [];
  try {
    const injection = '</script><script>globalThis.ssrInjected=1</script> & \u2028 \u2029';
    const aliceTodo = await seed(alice.api, `Private alpha ${injection}`);
    const bobTodo = await seed(bob.api, 'Private beta — independent owner');
    for (const identity of [alice, bob]) contexts.push(await browser.newContext({
      storageState: identity.storageState, extraHTTPHeaders: {...proofHeaders(), 'x-user': 'untrusted-actor'},
    }));
    const pages = await Promise.all(contexts.map(context => context.newPage()));
    const observations = pages.map(observe);
    for (const page of pages) releases.push(await holdApplication(page));
    const ids = ['concurrent-alpha', 'concurrent-beta'];
    const pending = pages.map((page, index) => page.goto(`${host}/ssr/todos?id=${ids[index]}&hold=1&username=forged`, {waitUntil: 'commit'}));
    // Observe failures promptly while both requests wait on their actual React Suspense boundary.
    for (const navigation of pending) navigation.catch(() => {});
    for (const id of ids) await expect.poll(async () => (await metrics(control, id))?.ready).toBe(true);

    const changed = await alice.api.put(`/api/todos/${aliceTodo.id}`, {data: {
      title: 'Changed after SSR captured its snapshot', completed: false, version: aliceTodo.version,
    }});
    expect(changed.status()).toBe(200);
    expect((await changed.json()).title).toBe('Changed after SSR captured its snapshot');

    // Complete the second request first; neither result may borrow the other's model.
    expect((await control.post(`/__proof/release?id=${ids[1]}`)).status()).toBe(204);
    const second = await pending[1];
    expect((await control.post(`/__proof/release?id=${ids[0]}`)).status()).toBe(204);
    const first = await pending[0];
    const responses = [first, second];
    const todos = [aliceTodo, bobTodo], names = ['demo', 'other'];
    for (let index = 0; index < pages.length; index++) {
      const page = pages[index], response = responses[index], todo = todos[index];
      expect(response.status()).toBe(200);
      expect(response.headers()['cache-control']).toBe('private, no-store');
      expect(response.headers().vary).toBe('Cookie');
      await expect(page.locator('#todos li')).toHaveText([todo.title]);
      await expect(page.locator('#owner')).toHaveText(names[index]);
      expect(await page.locator('html').getAttribute('data-jx-hydrated')).toBeNull();
      const snapshotText = await page.locator('#jx-snapshot').textContent();
      const snapshot = JSON.parse(snapshotText);
      expect(snapshot).toEqual({schema: 1, username: names[index], todos: [todo]});
      expect(snapshotText).not.toContain('<');
      const html = await response.text();
      for (const secret of [todos[1 - index].id, todos[1 - index].title, alice.csrfToken, bob.csrfToken,
        ...alice.storageState.cookies.map(cookie => cookie.value), ...bob.storageState.cookies.map(cookie => cookie.value),
        'csrfToken', 'test-only-password', process.env.PROTECTED_PROOF_TOKEN]) expect(html).not.toContain(secret);
      expect(await page.evaluate(() => globalThis.ssrInjected)).toBeUndefined();
      await page.evaluate(() => { globalThis.ssrNode = document.querySelector('#todos li'); });
    }
    for (const release of releases) release();
    for (let index = 0; index < pages.length; index++) {
      const page = pages[index];
      await expect(page.locator('html')).toHaveAttribute('data-jx-hydrated', 'true');
      expect(await page.evaluate(() => globalThis.ssrNode === document.querySelector('#todos li'))).toBe(true);
      await expect(page.locator('#todos li')).toHaveText([todos[index].title]);
      expect(observations[index].network.filter(url => new URL(url).pathname.startsWith('/api/'))).toEqual([]);
      await expect.poll(async () => (await metrics(control, ids[index]))?.finished).toBe(true);
      expect(await metrics(control, ids[index])).toMatchObject({sessionReads: 1, dataReads: 1, aborted: false, timedOut: false});
      await contexts[index].setOffline(true);
      await page.getByRole('button', {name: 'Local updates: 0'}).click();
      await expect(page.getByRole('button', {name: 'Local updates: 1'})).toBeVisible();
      expect(observations[index].errors).toEqual([]);
    }
  } finally {
    for (const release of releases) release();
    await Promise.all(contexts.map(context => context.close()));
    await Promise.all([alice.api.dispose(), bob.api.dispose(), control.dispose()]);
  }
});

test('disconnect cancels a suspended request; late release and forged identity cannot cross sessions', async ({browser, request}) => {
  const denied = await request.get('/ssr/todos?id=anonymous&username=demo', {headers: {'x-user': 'demo'}});
  expect(denied.status()).toBe(401);
  expect((await request.get('/__proof/state')).status()).toBe(403);
  const alice = await authenticate('demo'), bob = await authenticate('other');
  const control = await requests.newContext({baseURL: host, extraHTTPHeaders: proofHeaders()});
  let transport, context;
  try {
    const aliceTodo = await seed(alice.api, 'Must never appear in another request');
    const bobTodo = await seed(bob.api, 'Still works after another session disconnects');
    const cookie = alice.storageState.cookies.filter(value => value.name === 'JXSESSION');
    expect(cookie).toHaveLength(1);
    const stopped = new Promise(resolve => {
      transport = httpGet(`${host}/ssr/todos?id=cancelled-alpha&hold=1`, {
        headers: {...proofHeaders(), Cookie: `JXSESSION=${cookie[0].value}`},
      }, response => { response.resume(); resolve('unexpected response'); });
      transport.once('error', () => resolve('disconnected'));
    });
    await expect.poll(async () => (await metrics(control, 'cancelled-alpha'))?.ready).toBe(true);
    transport.destroy(new Error('Deliberate client disconnect'));
    expect(await stopped).toBe('disconnected');
    await expect.poll(async () => (await (await control.get('/__proof/state')).json()).active,
      {timeout: 5000}).toBe(0);
    expect(await metrics(control, 'cancelled-alpha')).toMatchObject({aborted: true, timedOut: false, finished: false});
    expect((await control.post('/__proof/release?id=cancelled-alpha')).status()).toBe(409);
    context = await browser.newContext({storageState: bob.storageState});
    const page = await context.newPage();
    const observation = observe(page);
    const response = await page.goto(`${host}/ssr/todos?id=surviving-beta`);
    expect(response.status()).toBe(200);
    await expect(page.locator('html')).toHaveAttribute('data-jx-hydrated', 'true');
    await expect(page.locator('#todos li')).toHaveText([bobTodo.title]);
    expect(await response.text()).not.toContain(aliceTodo.id);
    expect(observation.errors).toEqual([]);
  } finally {
    transport?.destroy();
    await context?.close();
    await Promise.all([alice.api.dispose(), bob.api.dispose(), control.dispose()]);
  }
});

test('the client artifact contains neither server renderer nor Spring classes', async () => {
  const metadata = JSON.parse(await readFile(new URL('./target/site/metafile.json', import.meta.url), 'utf8'));
  const inputs = Object.keys(metadata.inputs);
  expect(inputs.some(input => input.includes('client/target/browser/application.js'))).toBe(true);
  expect(inputs.some(input => /server[/.]|react-dom-server|springframework/.test(input))).toBe(false);
  const bundle = await readFile(new URL('./target/site/application.js', import.meta.url), 'utf8');
  for (const privateName of ['__jxProtectedSSR', 'test-only-password', 'JX_DEMO_PASSWORD_HASH', 'PROTECTED_PROOF_TOKEN']) {
    expect(bundle).not.toContain(privateName);
  }
});

import {test, expect, log} from './bench.js';

test('controlled-input: state drives the value and may transform typed text', async ({page, open}) => {
  await open('controlled-input');
  const field = page.getByLabel('Name');
  await field.pressSequentially('abc');
  await expect(field).toHaveValue('ABC');
  await expect(page.getByRole('status')).toHaveText('ABC');
  await page.getByRole('button', {name: 'Clear'}).click();
  await expect(field).toHaveValue('');
});

test('checkbox: a controlled checkbox reports its checked flag', async ({page, open}) => {
  await open('checkbox');
  const box = page.getByLabel('Accept');
  await expect(box).not.toBeChecked();
  await box.click();
  await expect(box).toBeChecked();
  await expect(page.getByRole('status')).toHaveText('accepted');
  await box.click();
  await expect(page.getByRole('status')).toHaveText('pending');
});

test('key-event: the handler reads the key name and acts on Enter only', async ({page, open}) => {
  await open('key-event');
  const field = page.getByLabel('Draft');
  await field.pressSequentially('note');
  await field.press('Escape');
  await expect(page.getByRole('status')).toHaveText('');
  await field.press('Enter');
  await expect(page.getByRole('status')).toHaveText('note');
  await expect(field).toHaveValue('');
});

test('prevent-default: stops link navigation and form submission', async ({page, open}) => {
  await open('prevent-default');
  await page.getByRole('link', {name: 'Stay'}).click();
  await expect(page.getByRole('status')).toHaveText('1 idle');
  expect(new URL(page.url()).hash).toEqual('');
  await page.getByRole('button', {name: 'Submit'}).click();
  await expect(page.getByRole('status')).toHaveText('1 submitted');
  // The form was not submitted: the page still has its own query.
  expect(new URL(page.url()).searchParams.get('case')).toEqual('prevent-default');
  await page.getByRole('link', {name: 'Go'}).click();
  await expect.poll(() => new URL(page.url()).hash).toEqual('#moved');
});

test('stop-propagation: a stopped event does not reach the ancestor handler', async ({page, open}) => {
  await open('stop-propagation');
  await page.getByRole('button', {name: 'Bubble'}).click();
  await expect(page.getByRole('status')).toHaveText('1/1');
  await page.getByRole('button', {name: 'Stop'}).click();
  await expect(page.getByRole('status')).toHaveText('1/2');
});

test('event-payload: handlers read type, buttons, modifiers, targets and coordinates', async ({page, open}) => {
  await open('event-payload');
  await page.getByTestId('inner').click({modifiers: ['Shift']});
  await page.getByLabel('Keys').press('Control+k');
  const entries = await log(page);
  expect(entries[0]).toEqual('click 0 true false DIV SPAN true 1');
  expect(entries.at(-1)).toEqual('keydown k KeyK true false false');
});

test('pointer-events: focus, pointer, wheel, touch and mouse-enter carry their fields', async ({page, open}) => {
  await open('pointer-events');
  const last = async () => (await log(page)).at(-1);
  await page.getByLabel('First').click();
  await expect.poll(last).toEqual('focus INPUT none');
  await page.getByLabel('Second').click();
  await expect.poll(last).toEqual('blur second');
  const pad = page.getByTestId('pad');
  await pad.hover();
  await expect.poll(last).toEqual('mouseenter DIV');
  await page.mouse.down();
  await expect.poll(last).toEqual('pointerdown mouse true 0');
  await page.mouse.up();
  await page.mouse.wheel(0, 120);
  await expect.poll(last).toEqual('wheel 1 0');
  await pad.evaluate(node => {
    const touch = new Touch({identifier: 7, target: node, clientX: 5, clientY: 6});
    node.dispatchEvent(new TouchEvent('touchstart', {touches: [touch], changedTouches: [touch], bubbles: true}));
  });
  await expect.poll(last).toEqual('touchstart 1 7');
});

test('transfer-events: composition text, clipboard data and drag data', async ({page, open}) => {
  await open('transfer-events');
  const field = page.getByLabel('Compose');
  await field.evaluate(node => {
    node.dispatchEvent(new CompositionEvent('compositionstart', {data: '', bubbles: true}));
    node.dispatchEvent(new CompositionEvent('compositionupdate', {data: 'ni', bubbles: true}));
    node.dispatchEvent(new CompositionEvent('compositionend', {data: 'ni', bubbles: true}));
  });
  expect(await log(page)).toEqual(['compositionstart []', 'compositionupdate [ni]', 'compositionend [ni]']);
  const copied = await field.evaluate(node => {
    const incoming = new DataTransfer();
    incoming.setData('text/plain', 'pasted text');
    node.dispatchEvent(new ClipboardEvent('paste', {clipboardData: incoming, bubbles: true, cancelable: true}));
    const outgoing = new DataTransfer();
    node.dispatchEvent(new ClipboardEvent('copy', {clipboardData: outgoing, bubbles: true, cancelable: true}));
    return outgoing.getData('text/plain');
  });
  expect(copied).toEqual('copied by handler');
  expect((await log(page)).slice(3)).toEqual(['paste pasted text', 'copy']);
  await page.evaluate(() => {
    const transfer = new DataTransfer();
    const event = (type, options) => new DragEvent(type, {dataTransfer: transfer, bubbles: true, ...options});
    document.querySelector('[data-testid="source"]').dispatchEvent(event('dragstart'));
    document.querySelector('[data-testid="target"]').dispatchEvent(event('drop', {cancelable: true}));
  });
  expect((await log(page)).slice(5)).toEqual(['dragstart', 'drop dragged item']);
});

test('media-events: animation, transition, media and load events reach their handlers', async ({page, open}) => {
  await open('media-events');
  await expect.poll(() => log(page)).toEqual(['load IMG']);
  await page.getByTestId('box').evaluate(node => {
    node.dispatchEvent(new AnimationEvent('animationend', {animationName: 'pulse', elapsedTime: 0.5, bubbles: true}));
    node.dispatchEvent(new TransitionEvent('transitionend', {propertyName: 'opacity', bubbles: true}));
  });
  await page.getByTestId('audio').evaluate(node => {
    node.dispatchEvent(new Event('play'));
    node.dispatchEvent(new Event('timeupdate'));
    node.dispatchEvent(new Event('pause'));
  });
  expect((await log(page)).slice(1)).toEqual(
    ['animationend pulse 0.5', 'transitionend opacity', 'play AUDIO true', 'timeupdate 0', 'pause AUDIO']);
});

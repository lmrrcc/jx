(() => {
  const initial = __JX_INITIAL_STATUS__;
  let version = initial.version;
  let session = initial.session;
  let applying = false;
  let checkpoint;
  let failedUpdate = false;
  const hot = { enabled: true, defer: false, initialized: false, boot };
  globalThis.__JX_HOT__ = hot;

  function notice(message) {
    let element = document.getElementById('__jx_dev_notice');
    if (!message) { if (element) element.remove(); return; }
    if (!document.body) { document.addEventListener('DOMContentLoaded', () => notice(message), { once: true }); return; }
    if (!element) {
      element = document.createElement('div'); element.id = '__jx_dev_notice'; element.setAttribute('role', 'alert');
      element.style.cssText = 'position:fixed;inset:0 0 auto;background:#8b1020;color:white;padding:12px;z-index:2147483647;font:15px system-ui;white-space:pre-wrap';
      document.body.appendChild(element);
    }
    element.textContent = message;
  }

  function focused() {
    const element = document.activeElement;
    if (!element || element === document.body) return null;
    const peers = Array.from(document.querySelectorAll(element.tagName));
    return { id: element.id, name: element.getAttribute('name'), tag: element.tagName,
      index: peers.indexOf(element), start: element.selectionStart, end: element.selectionEnd,
      direction: element.selectionDirection, scrollX, scrollY };
  }

  function restoreFocus(previous) {
    if (!previous) return;
    let element = previous.id ? document.getElementById(previous.id) : null;
    if (!element && previous.name) element = Array.from(document.getElementsByName(previous.name)).find(item => item.tagName === previous.tag);
    if (!element) element = document.querySelectorAll(previous.tag)[previous.index];
    if (element && typeof element.focus === 'function') {
      element.focus({ preventScroll: true });
      if (previous.start != null && typeof element.setSelectionRange === 'function') {
        try { element.setSelectionRange(previous.start, previous.end, previous.direction); } catch (_) { /* Non-text input. */ }
      }
    }
    scrollTo(previous.scrollX, previous.scrollY);
  }

  async function boot(main, next = { version, session }) {
    if (applying) throw new Error('A JX update is already being applied');
    applying = true;
    hot.initialized = true;
    let teardownStarted = false;
    const previousFocus = focused();
    try {
      hot.notices = [];
      if (!failedUpdate && typeof hot.capture === 'function') checkpoint = hot.capture();
      if (typeof hot.dispose === 'function') { teardownStarted = true; hot.dispose(); }
      if (checkpoint != null && typeof hot.restore === 'function') hot.restore(checkpoint);
      main([]);
      if (typeof hot.ready === 'function') {
        let timeout;
        try {
          await Promise.race([hot.ready(), new Promise((_, reject) => { timeout = setTimeout(() => reject(new Error('JX render did not commit after update')), 10_000); })]);
        } finally { clearTimeout(timeout); }
      }
      await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)));
      restoreFocus(previousFocus);
      failedUpdate = false;
      checkpoint = undefined;
      notice(hot.notices?.length ? 'JX update: ' + hot.notices.join('\n') : '');
      window.dispatchEvent(new CustomEvent('jx:hot-update', { detail: next }));
    } catch (failure) {
      failedUpdate = failedUpdate || teardownStarted;
      notice('JX hot update failed. Your previous state checkpoint is retained. Fix the code and save.\n' + String(failure));
      window.dispatchEvent(new CustomEvent('jx:hot-error', { detail: String(failure) }));
      throw failure;
    } finally { applying = false; }
  }

  async function update(next) {
    hot.defer = true;
    let module;
    try {
      globalThis.__JX_BUNDLE__ = undefined;
      // A removable classic script avoids the permanent native ESM module cache for each Java generation.
      await new Promise((resolve, reject) => {
        const script = document.createElement('script');
        const timeout = setTimeout(() => { script.remove(); reject(new Error('JX bundle download timed out')); }, 30_000);
        script.src = '/application.js?jx-session=' + encodeURIComponent(next.session) + '&jx-version=' + next.version;
        script.onload = () => { clearTimeout(timeout); script.remove(); resolve(); };
        script.onerror = () => { clearTimeout(timeout); script.remove(); reject(new Error('Cannot load rebuilt Java browser bundle')); };
        document.head.appendChild(script);
      });
      module = globalThis.__JX_BUNDLE__;
      if (typeof module?.main !== 'function') throw new Error('Development bundle does not export main; restart the JX dev goal');
      if (module.hotRuntimeVersion !== hot.version) throw new Error('Incompatible JX hot runtime; load a fresh development page');
    } finally { hot.defer = false; }
    await boot(module.main, next);
    for (const link of document.querySelectorAll('link[rel="stylesheet"][href]')) {
      const url = new URL(link.href);
      if (url.origin !== location.origin) continue;
      url.searchParams.set('jx-version', next.session + ':' + next.version);
      link.href = url.href;
    }
    version = next.version; session = next.session;
  }

  async function check() {
    try {
      const response = await fetch('/__jx_dev/version', { cache: 'no-store', signal: AbortSignal.timeout(3_000) });
      if (!response.ok) throw new Error('Development server unavailable');
      const next = await response.json();
      if (next.state === 'ready' && document.body?.hasAttribute('data-jx-waiting')) {
        // The initial waiting page has no application or state; load its real HTML/assets once ready.
        location.replace(location.href); return;
      }
      if (next.state === 'failed') notice('JX build failed. The current application is still running. See .jx diagnostics in the IDE or Maven console.');
      else if (next.state === 'ready' && !applying && (next.version !== version || next.session !== session)) {
        try { await update(next); }
        catch (failure) {
          // Retry only a newer build, while retaining the most recent committed checkpoint.
          version = next.version; session = next.session;
          notice('JX hot update failed. Fix the code and save to retry.\n' + String(failure));
        }
      }
    } catch (_) { /* Keep the application usable while offline or while the dev server restarts. */ }
    setTimeout(check, 500);
  }
  check();
})();

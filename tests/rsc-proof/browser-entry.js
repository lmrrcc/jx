// Framework browser host: the application components on the JX side are Java.
import {createElement, use, Suspense} from 'react';
import {createRoot} from 'react-dom/client';
import {createFromFetch} from 'react-server-dom-webpack/client.browser';
const parameters = new URLSearchParams(location.search);
const query = new URLSearchParams({side: parameters.get('side') || 'jx', request: parameters.get('request') || 'alpha'});
const model = createFromFetch(fetch('/flight?' + query));
function Page() { return use(model); }
createRoot(document.getElementById('app')).render(createElement(Suspense, {fallback: 'Loading Flight'}, createElement(Page)));

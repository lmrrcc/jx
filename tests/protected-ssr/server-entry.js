// Framework host boundary. Only this entry imports the Node-compiled renderer.
import {main} from './server/target/browser/application.js';
main([]);
export const renderer = globalThis.__jxProtectedSSR;

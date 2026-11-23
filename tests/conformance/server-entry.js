// Framework test fixture: starts the JX bench application in Node, where it registers its
// server functions instead of mounting a page.
import { main } from './target/browser/application.js';

main([]);
export const server = globalThis.__jxBenchServer;

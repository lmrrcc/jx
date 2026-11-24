// Framework test fixture: starts the host application. The lazily loaded application is the
// second entry point of the same esbuild run, so both share one copy of React.
import { main } from './host/target/browser/application.js';

main([]);

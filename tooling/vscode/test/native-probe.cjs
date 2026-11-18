const path = require('node:path');
const fs = require('node:fs/promises');
const { runTests } = require('@vscode/test-electron');

(async () => {
  const base = path.resolve(__dirname, '..');
  const root = path.join(base, '.vscode-test/native-java');
  const workspace = path.join(root, 'workspace');
  const extension = path.join(root, 'probe');
  const extensions = path.join(root, 'extensions');
  await fs.mkdir(path.join(workspace, 'src/main/java/demo'), { recursive: true });
  await fs.mkdir(path.join(workspace, 'src/main/jx/demo'), { recursive: true });
  await fs.mkdir(path.join(workspace, 'src/test/java/demo'), { recursive: true });
  await fs.mkdir(path.join(workspace, 'src/test/jx/demo'), { recursive: true });
  await fs.mkdir(path.join(workspace, '.vscode'), { recursive: true });
  await fs.mkdir(extension, { recursive: true });
  await fs.mkdir(extensions, { recursive: true });
  await fs.writeFile(path.join(workspace, 'pom.xml'), '<project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>jx-native-java-test</artifactId><version>1</version><properties><maven.compiler.release>25</maven.compiler.release></properties></project>');
  await fs.writeFile(path.join(workspace, 'src/main/java/demo/Helper.java'), 'package demo; public class Helper { public static String greet(String name) { return name; } }');
  await fs.writeFile(path.join(workspace, 'src/main/jx/demo/Counter.jx'), 'package demo; public class Counter {}');
  await fs.writeFile(path.join(workspace, 'src/test/jx/demo/TestOnly.jx'), 'package demo; public class TestOnly {}');
  await fs.writeFile(path.join(workspace, '.vscode/settings.json'), JSON.stringify({ 'java.server.launchMode': 'Standard', 'java.import.gradle.enabled': false, 'java.configuration.updateBuildConfiguration': 'automatic' }));
  await fs.writeFile(path.join(extension, 'package.json'), JSON.stringify({ name: 'jx-native-probe', publisher: 'lmrrcc', version: '0.0.1', engines: { vscode: '^1.138.0' }, main: './main.cjs', activationEvents: ['*'], extensionDependencies: ['redhat.java'], contributes: { javaExtensions: [path.join(base, 'server/jx-java-bridge.jar')] } }));
  await fs.writeFile(path.join(extension, 'main.cjs'), 'exports.activate = () => {};');
  const installed = path.join(require('node:os').homedir(), '.vscode/extensions');
  const javaExtension = process.env.JX_JAVA_EXTENSION || path.join(installed,
    (await fs.readdir(installed)).find(name => name.startsWith('redhat.java-1.58.0')) ?? 'missing-redhat.java-1.58.0');
  const link = path.join(extensions, path.basename(javaExtension));
  try { await fs.symlink(javaExtension, link, 'dir'); } catch (error) { if (error.code !== 'EEXIST') throw error; }
  await runTests({ vscodeExecutablePath: process.env.VSCODE_EXECUTABLE_PATH,
    extensionDevelopmentPath: extension, extensionTestsPath: path.join(__dirname, 'native-probe-suite.cjs'),
    launchArgs: [workspace, '--extensions-dir', extensions, '--user-data-dir', path.join(root, 'user-data'), '--disable-workspace-trust', '--skip-welcome', '--skip-release-notes'],
    extensionTestsEnv: { JX_NATIVE_PROBE: '1' } });
})().catch(failure => { console.error(failure); process.exitCode = 1; });

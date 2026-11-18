const path = require('node:path');
const fs = require('node:fs/promises');
const { runTests, resolveCliPathFromVSCodeExecutablePath } = require('@vscode/test-electron');
const { promisify } = require('node:util');
const execute = promisify(require('node:child_process').execFile);
const net = require('node:net');

async function availablePort() {
  const server = net.createServer();
  await new Promise((resolve, reject) => { server.once('error', reject); server.listen(0, '127.0.0.1', resolve); });
  const port = server.address().port;
  await new Promise(resolve => server.close(resolve));
  return port;
}

(async () => {
  const base = path.resolve(__dirname, '..');
  const testRoot = path.join(base, '.vscode-test');
  const workspace = path.join(testRoot, 'workspace with spaces');
  const extensions = path.join(testRoot, 'extensions');
  const userData = path.join(testRoot, 'user-data');
  const repository = path.resolve(base, '../..');
  const javaProperties = await execute('java', ['-XshowSettings:properties', '-version']);
  const javaHome = /^\s*java\.home\s*=\s*(.+)$/m.exec(javaProperties.stderr)?.[1]?.trim();
  if (!javaHome) throw new Error('Tests require a full Java 25 JDK');
  await fs.mkdir(path.join(userData, 'User'), { recursive: true });
  await fs.writeFile(path.join(userData, 'User/settings.json'), JSON.stringify({ 'jx.javaPath': path.join(javaHome, 'bin', process.platform === 'win32' ? 'java.exe' : 'java') }));
  // This is an isolated copy of the real sample. No application source in the checkout is edited.
  await fs.rm(workspace, { recursive: true, force: true });
  await fs.mkdir(path.join(workspace, 'src/main/jx'), { recursive: true });
  const module = path.join(workspace, 'apps/counter-app');
  await fs.mkdir(module, { recursive: true });
  await fs.cp(path.join(repository, 'examples/counter-app/src'), path.join(module, 'src'), { recursive: true });
  // m2e does not infer source roots registered by every third-party generator. Explicit Maven
  // source metadata exercises coexistence with an existing, imported generated-source root.
  const modulePom = (await fs.readFile(path.join(repository, 'examples/counter-app/pom.xml'), 'utf8'))
    .replace('<build><plugins>', `<build><plugins>
    <plugin><groupId>org.codehaus.mojo</groupId><artifactId>build-helper-maven-plugin</artifactId><version>3.6.1</version>
      <executions><execution><id>editor-generated-source</id><phase>generate-sources</phase><goals><goal>add-source</goal></goals>
        <configuration><sources><source>target/generated-sources/jx</source></sources></configuration>
      </execution></executions>
    </plugin>`);
  await fs.writeFile(path.join(module, 'pom.xml'), modulePom);
  await fs.mkdir(path.join(module, 'src/main/java/jx/example/counter'), { recursive: true });
  await fs.writeFile(path.join(module, 'src/main/java/jx/example/counter/NativeHelper.java'), 'package jx.example.counter; public final class NativeHelper { public static String label(String value) { return NativeEditing.greeting(value); } }');
  await fs.writeFile(path.join(module, 'src/main/jx/jx/example/counter/NativeEditing.jx'), `package jx.example.counter;
import jx.Component;
import jx.View;
@Component
public final class NativeEditing {
  public static String greeting(String value) { return value; }
  public static View render() {
    java.util.List<String> items = new java.util.ArrayList<>();
    String label = NativeHelper.label(greeting("Editor"));
    int length = label.length();
    return (<button title={label.substring(1)} onClick={event -> event.preventDefault()}>{label.toUpperCase()}{length}</button>);
  }
}
`);
  const parent = (await fs.readFile(path.join(repository, 'pom.xml'), 'utf8'))
    .replace(/<modules>[\s\S]*?<\/modules>/, '<modules><module>apps/counter-app</module></modules>');
  await fs.writeFile(path.join(workspace, 'pom.xml'), parent);
  for (const wrapper of ['mvnw', 'mvnw.cmd']) await fs.copyFile(path.join(repository, wrapper), path.join(workspace, wrapper));
  await fs.chmod(path.join(workspace, 'mvnw'), 0o755);
  await fs.cp(path.join(repository, '.mvn'), path.join(workspace, '.mvn'), { recursive: true });
  await fs.mkdir(path.join(workspace, '.cache'), { recursive: true });
  await fs.symlink(path.join(repository, '.cache/maven-repository'), path.join(workspace, '.cache/maven-repository'), 'dir');
  await fs.symlink(path.join(repository, '.cache/maven-repository'), path.join(workspace, '.cache/editor-repository'), 'dir');
  await fs.symlink(path.join(repository, 'node_modules'), path.join(workspace, 'node_modules'), 'dir');
  await fs.copyFile(path.join(repository, 'package.json'), path.join(workspace, 'package.json'));
  await fs.copyFile(path.join(repository, 'package-lock.json'), path.join(workspace, 'package-lock.json'));
  const devPort = await availablePort();
  await fs.mkdir(path.join(workspace, '.vscode'), { recursive: true });
  const javaSettings = path.join(workspace, '.vscode/java-maven-settings.xml');
  await fs.writeFile(javaSettings, '<settings><localRepository>' + path.join(repository, '.cache/maven-repository').replaceAll('&', '&amp;').replaceAll('<', '&lt;') + '</localRepository></settings>');
  await fs.writeFile(path.join(workspace, '.vscode/settings.json'), JSON.stringify({
    'jx.devArguments': ['dev.jx:jx-maven-plugin:0.1.0-SNAPSHOT:dev', '-Djx.port=' + devPort],
    'task.saveBeforeRun': 'never', 'java.server.launchMode': 'Standard',
    'java.configuration.maven.userSettings': javaSettings,
    'java.configuration.updateBuildConfiguration': 'automatic', 'java.import.gradle.enabled': false
  }));
  await fs.writeFile(path.join(workspace, 'src/main/jx/Test.jx'), 'import jx.Component;\nimport jx.View;\n@Component\npublic final class Test { public static View render() { return (<div>Hello</div>); } }\n');
  await fs.writeFile(path.join(workspace, 'src/main/jx/Counter.jx'), 'import jx.Component;\nimport jx.View;\n@Component\npublic final class Counter {\n public record Props(int initial, String label) {}\n public static View render(Props props) { return (<div>{props.label()}</div>); }\n}\n');
  await fs.writeFile(path.join(workspace, 'src/main/jx/App.jx'), 'import jx.Component;\nimport jx.View;\n@Component\npublic final class App {\n public static View render() { return (<Counter initial={0} label="Editor" />); }\n}\n');
  await execute(path.join(workspace, process.platform === 'win32' ? 'mvnw.cmd' : 'mvnw'),
    ['-B', '-ntp', '-f', path.join(module, 'pom.xml'), '-Dmaven.repo.local=' + path.join(repository, '.cache/maven-repository'), 'package'],
    { cwd: workspace, timeout: 180_000, maxBuffer: 16 * 1024 * 1024 });
  await fs.writeFile(path.join(module, 'target/generated-sources/jx/jx/example/counter/Foreign.java'), 'package jx.example.counter; public final class Foreign {}');
  const executable = process.env.VSCODE_EXECUTABLE_PATH;
  if (!executable) throw new Error('Set VSCODE_EXECUTABLE_PATH to the VS Code executable to test; tests do not silently download or change the target IDE.');
  const cli = resolveCliPathFromVSCodeExecutablePath(executable);
  const installedExtensions = path.join(require('node:os').homedir(), '.vscode/extensions');
  const javaExtension = process.env.JX_JAVA_EXTENSION || path.join(installedExtensions,
    (await fs.readdir(installedExtensions)).find(name => name.startsWith('redhat.java-1.58.0')) ?? 'missing-redhat.java-1.58.0');
  await fs.mkdir(extensions, { recursive: true });
  try { await fs.symlink(javaExtension, path.join(extensions, path.basename(javaExtension)), 'dir'); }
  catch (error) { if (error.code !== 'EEXIST') throw error; }
  await execute(cli, ['--install-extension', path.join(base, 'jx-language-0.2.0.vsix'), '--force', '--extensions-dir', extensions, '--user-data-dir', userData], { timeout: 60_000 });
  const installed = path.join(extensions, 'lmrrcc.jx-language-0.2.0');
  await runTests({ vscodeExecutablePath: executable, extensionDevelopmentPath: installed,
    extensionTestsPath: path.join(base, 'out/test/suite.cjs'),
    launchArgs: [workspace, '--extensions-dir', extensions, '--user-data-dir', userData, '--disable-workspace-trust', '--skip-welcome', '--skip-release-notes', '--disable-updates'],
    extensionTestsEnv: { JX_EDITOR_TEST: '1', JX_DEV_PORT: String(devPort) } });
  const restricted = path.join(testRoot, 'restricted-user-data');
  await fs.mkdir(path.join(restricted, 'User'), { recursive: true });
  await fs.writeFile(path.join(restricted, 'User/settings.json'), JSON.stringify({
    'security.workspace.trust.enabled': true,
    'security.workspace.trust.startupPrompt': 'never',
    'security.workspace.trust.emptyWindow': false
  }));
  // test-electron always adds --disable-workspace-trust. Launch the same IDE
  // directly for this check so the real trust service remains enabled.
  await execute(executable, [workspace, '--extensions-dir', extensions, '--user-data-dir', restricted,
    `--extensionDevelopmentPath=${installed}`, `--extensionTestsPath=${path.join(base, 'out/test/suite.cjs')}`,
    '--skip-welcome', '--skip-release-notes', '--disable-updates'], {
    env: { ...process.env, JX_EDITOR_TEST: '1', JX_EXPECT_UNTRUSTED: '1' }, timeout: 60_000, maxBuffer: 8 * 1024 * 1024
  });
  console.log('PASS: packaged extension leaves Java stopped in an untrusted workspace.');
})().catch(failure => { console.error(failure); process.exitCode = 1; });

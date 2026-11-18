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
  // This is an isolated copy of the real sample. No application source in the checkout is edited.
  await fs.rm(workspace, { recursive: true, force: true });
  await fs.mkdir(path.join(workspace, 'src/main/jx'), { recursive: true });
  const module = path.join(workspace, 'apps/counter-app');
  await fs.mkdir(module, { recursive: true });
  await fs.cp(path.join(repository, 'examples/counter-app/src'), path.join(module, 'src'), { recursive: true });
  await fs.copyFile(path.join(repository, 'examples/counter-app/pom.xml'), path.join(module, 'pom.xml'));
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
  await fs.writeFile(path.join(workspace, '.vscode/settings.json'), JSON.stringify({
    'jx.devArguments': ['dev.jx:jx-maven-plugin:0.1.0-SNAPSHOT:dev', '-Djx.port=' + devPort],
    'task.saveBeforeRun': 'never'
  }));
  await fs.writeFile(path.join(workspace, 'src/main/jx/Test.jx'), 'import jx.JxComponent;\nimport jx.View;\n@JxComponent\npublic final class Test { public static View render() { return (<div>Hello</div>); } }\n');
  await fs.writeFile(path.join(workspace, 'src/main/jx/Counter.jx'), 'import jx.JxComponent;\nimport jx.View;\n@JxComponent\npublic final class Counter {\n public record Props(int initial, String label) {}\n public static View render(Props props) { return (<div>{props.label()}</div>); }\n}\n');
  await fs.writeFile(path.join(workspace, 'src/main/jx/App.jx'), 'import jx.JxComponent;\nimport jx.View;\n@JxComponent\npublic final class App {\n public static View render() { return (<Counter initial={0} label="Editor" />); }\n}\n');
  const executable = process.env.VSCODE_EXECUTABLE_PATH;
  if (!executable) throw new Error('Set VSCODE_EXECUTABLE_PATH to the VS Code executable to test; tests do not silently download or change the target IDE.');
  const cli = resolveCliPathFromVSCodeExecutablePath(executable);
  await execute(cli, ['--install-extension', path.join(base, 'jx-language-0.1.0.vsix'), '--force', '--extensions-dir', extensions, '--user-data-dir', userData], { timeout: 60_000 });
  const installed = path.join(extensions, 'lmrrcc.jx-language-0.1.0');
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

import * as vscode from 'vscode';
import * as path from 'node:path';
import { access } from 'node:fs/promises';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { LanguageClient, LanguageClientOptions, ServerOptions } from 'vscode-languageclient/node';

const execute = promisify(execFile);
let client: LanguageClient | undefined;
let output: vscode.LogOutputChannel;
let watcher: vscode.FileSystemWatcher;
let pending: Promise<void> = Promise.resolve();

async function start(context: vscode.ExtensionContext): Promise<void> {
  if (!vscode.workspace.isTrusted) return;
  const java = vscode.workspace.getConfiguration('jx').get<string>('javaPath', 'java').trim();
  if (!java) throw new Error('Set jx.javaPath to a Java 25 executable.');
  const version = await execute(java, ['-version'], { timeout: 10_000, maxBuffer: 16_384 });
  const match = /version\s+"(\d+)/.exec(version.stderr + version.stdout);
  if (!match || Number(match[1]) < 25) throw new Error('The JX language server requires Java 25 or newer. Set jx.javaPath.');
  const jar = context.asAbsolutePath('server/jx-language-server.jar');
  await access(jar);
  const classpath = (vscode.workspace.workspaceFolders ?? []).flatMap(folder =>
    vscode.workspace.getConfiguration('jx', folder.uri).get<string[]>('classpath', []).map(entry =>
      path.isAbsolute(entry) ? entry : path.resolve(folder.uri.fsPath, entry)));
  const server: ServerOptions = { command: java, args: ['-jar', jar], options: { env: process.env } };
  const options: LanguageClientOptions = {
    documentSelector: [{ scheme: 'file', language: 'jx' }],
    outputChannel: output,
    initializationOptions: { classpath },
    synchronize: { fileEvents: watcher },
    connectionOptions: { maxRestartCount: 3 }
  };
  const next = new LanguageClient('jx', 'JX Language Server', server, options);
  client = next;
  try { await next.start(); }
  catch (failure) { await next.dispose(); client = undefined; throw failure; }
}

function restart(context: vscode.ExtensionContext): Promise<void> {
  pending = pending.catch(() => undefined).then(async () => {
    if (client) { await client.dispose(); client = undefined; }
    await start(context);
  });
  return pending;
}

function report(failure: unknown): void {
  const message = failure instanceof Error ? failure.message : String(failure);
  output.appendLine(message);
  void vscode.window.showErrorMessage(`JX: ${message}`, 'Show Output').then(choice => {
    if (choice) output.show();
  });
}

async function folder(): Promise<vscode.WorkspaceFolder | undefined> {
  const folders = vscode.workspace.workspaceFolders ?? [];
  return folders.length === 1 ? folders[0] : vscode.window.showWorkspaceFolderPick();
}

async function runMaven(development: boolean): Promise<void> {
  if (!vscode.workspace.isTrusted) throw new Error('Trust this workspace before running its Maven Wrapper.');
  const selected = await folder();
  if (!selected) return;
  const wrapper = path.join(selected.uri.fsPath, process.platform === 'win32' ? 'mvnw.cmd' : 'mvnw');
  await access(wrapper);
  const args = development ? vscode.workspace.getConfiguration('jx', selected.uri).get<string[]>('devArguments', []) : ['package'];
  if (args.length === 0) throw new Error('Set jx.devArguments to the development goal for this project.');
  const task = new vscode.Task({ type: 'jx', mode: development ? 'dev' : 'build' }, selected,
    development ? 'JX Development Server' : 'JX Build', 'JX',
    new vscode.ProcessExecution(wrapper, args, { cwd: selected.uri.fsPath }), []);
  task.presentationOptions = { reveal: vscode.TaskRevealKind.Always, panel: vscode.TaskPanelKind.Dedicated };
  await vscode.tasks.executeTask(task);
}

async function createComponent(): Promise<void> {
  if (!vscode.workspace.isTrusted) throw new Error('Trust this workspace before creating a component.');
  const name = await vscode.window.showInputBox({ prompt: 'Java component class name', value: 'NewComponent',
    validateInput: value => /^[A-Z][A-Za-z0-9_]*$/.test(value) ? undefined : 'Use a Java class name beginning with an uppercase letter.' });
  if (!name) return;
  const selected = await folder();
  const target = await vscode.window.showSaveDialog({ filters: { JX: ['jx'] },
    defaultUri: selected ? vscode.Uri.joinPath(selected.uri, 'src/main/jx', `${name}.jx`) : undefined });
  if (!target) return;
  const source = `import jx.JxComponent;\nimport jx.View;\n\n@JxComponent\npublic final class ${name} {\n    public static View render() {\n        return (<div>Hello from ${name}</div>);\n    }\n}\n`;
  const edit = new vscode.WorkspaceEdit();
  edit.createFile(target, { overwrite: false });
  edit.insert(target, new vscode.Position(0, 0), source);
  if (!await vscode.workspace.applyEdit(edit)) throw new Error('Component could not be created.');
  await vscode.window.showTextDocument(target);
}

export async function activate(context: vscode.ExtensionContext): Promise<{ isRunning(): boolean }> {
  output = vscode.window.createOutputChannel('JX Language Server', { log: true });
  watcher = vscode.workspace.createFileSystemWatcher('**/*.{jx,java}');
  context.subscriptions.push(output, watcher);
  const command = (id: string, action: () => Promise<unknown> | void) =>
    vscode.commands.registerCommand(id, async () => { try { await action(); } catch (failure) { report(failure); } });
  context.subscriptions.push(
    command('jx.restartServer', () => restart(context)),
    command('jx.showOutput', () => output.show()),
    command('jx.createComponent', createComponent),
    command('jx.build', () => runMaven(false)),
    command('jx.dev', () => runMaven(true)),
    vscode.workspace.onDidGrantWorkspaceTrust(() => { void restart(context).catch(report); }),
    vscode.workspace.onDidChangeConfiguration(event => {
      if (event.affectsConfiguration('jx.javaPath') || event.affectsConfiguration('jx.classpath')) void restart(context).catch(report);
    })
  );
  try { await restart(context); } catch (failure) { report(failure); }
  return { isRunning: () => client?.isRunning() ?? false };
}

export async function deactivate(): Promise<void> {
  await pending.catch(() => undefined);
  if (client) { await client.dispose(); client = undefined; }
}

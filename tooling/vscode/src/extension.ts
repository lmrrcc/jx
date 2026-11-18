import * as vscode from 'vscode';
import * as path from 'node:path';
import { access } from 'node:fs/promises';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { LanguageClient, LanguageClientOptions, ServerOptions } from 'vscode-languageclient/node';
import { DevelopmentTasks } from './development';
import { JavaEditing } from './javaEditing';

const execute = promisify(execFile);
let client: LanguageClient | undefined;
let output: vscode.LogOutputChannel;
let watcher: vscode.FileSystemWatcher;
let pending: Promise<void> = Promise.resolve();
let development: DevelopmentTasks | undefined;
let javaEditing: JavaEditing | undefined;
const javaExecutables = new Map<string, string>();

async function start(context: vscode.ExtensionContext): Promise<void> {
  if (!vscode.workspace.isTrusted) return;
  const configuredJava = vscode.workspace.getConfiguration('jx').get<string>('javaPath', 'java').trim();
  if (!configuredJava) throw new Error('Set jx.javaPath to a Java 25 executable.');
  let java = javaExecutables.get(configuredJava) ?? configuredJava;
  const version = await execute(java, ['-XshowSettings:properties', '-version'], { timeout: 10_000, maxBuffer: 32_768 });
  const match = /version\s+"(\d+)/.exec(version.stderr + version.stdout);
  if (!match || Number(match[1]) < 25) throw new Error('The JX language server requires Java 25 or newer. Set jx.javaPath.');
  const javaHome = /^\s*java\.home\s*=\s*(.+)$/m.exec(version.stderr + version.stdout)?.[1]?.trim();
  if (!javaHome) throw new Error('Cannot locate the Java installation. Set jx.javaPath to a full Java 25 JDK executable.');
  try { await access(path.join(javaHome, 'lib', 'ct.sym')); }
  catch { throw new Error('The JX compiler requires a full Java 25 JDK, including lib/ct.sym. Set jx.javaPath; the Java extension’s stripped runtime is insufficient.'); }
  java = path.join(javaHome, 'bin', process.platform === 'win32' ? 'java.exe' : 'java');
  javaExecutables.set(configuredJava, java);
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

async function createComponent(): Promise<void> {
  if (!vscode.workspace.isTrusted) throw new Error('Trust this workspace before creating a component.');
  const name = await vscode.window.showInputBox({ prompt: 'Java component class name', value: 'NewComponent',
    validateInput: value => /^[A-Z][A-Za-z0-9_]*$/.test(value) ? undefined : 'Use a Java class name beginning with an uppercase letter.' });
  if (!name) return;
  const selected = await folder();
  const target = await vscode.window.showSaveDialog({ filters: { JX: ['jx'] },
    defaultUri: selected ? vscode.Uri.joinPath(selected.uri, 'src/main/jx', `${name}.jx`) : undefined });
  if (!target) return;
  const source = `import jx.Component;\nimport jx.View;\n\n@Component\npublic final class ${name} {\n    public static View render() {\n        return (<div>Hello from ${name}</div>);\n    }\n}\n`;
  const edit = new vscode.WorkspaceEdit();
  edit.createFile(target, { overwrite: false });
  edit.insert(target, new vscode.Position(0, 0), source);
  if (!await vscode.workspace.applyEdit(edit)) throw new Error('Component could not be created.');
  await vscode.window.showTextDocument(target);
}

export async function activate(context: vscode.ExtensionContext): Promise<{ isRunning(): boolean }> {
  output = vscode.window.createOutputChannel('JX Language Server', { log: true });
  development = new DevelopmentTasks(output);
  javaEditing = new JavaEditing(() => client, output);
  watcher = vscode.workspace.createFileSystemWatcher('**/*.{jx,java}');
  context.subscriptions.push(output, watcher, development, javaEditing);
  const command = (id: string, action: () => Promise<unknown> | void) =>
    vscode.commands.registerCommand(id, async () => { try { await action(); } catch (failure) { report(failure); } });
  context.subscriptions.push(
    command('jx.restartServer', () => restart(context)),
    command('jx.showOutput', () => output.show()),
    command('jx.createComponent', createComponent),
    command('jx.build', () => development?.build()),
    command('jx.dev', () => development?.start()),
    command('jx.stopDev', () => development?.stop()),
    command('jx.goToDefinition', () => javaEditing?.goToDefinition()),
    vscode.workspace.onDidGrantWorkspaceTrust(() => { void restart(context).catch(report); }),
    vscode.workspace.onDidChangeConfiguration(event => {
      if (event.affectsConfiguration('jx.javaPath') || event.affectsConfiguration('jx.classpath')) void restart(context).catch(report);
    })
  );
  try { await restart(context); } catch (failure) { report(failure); }
  return { isRunning: () => client?.isRunning() ?? false };
}

export async function deactivate(): Promise<void> {
  const shutdown = await Promise.allSettled([javaEditing?.shutdown(), development?.shutdown()]);
  await pending.catch(() => undefined);
  if (client) { await client.dispose(); client = undefined; }
  const failures = shutdown.filter((result): result is PromiseRejectedResult => result.status === 'rejected');
  if (failures.length) throw new AggregateError(failures.map(result => result.reason), 'JX shutdown failed');
}

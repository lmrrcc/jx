import * as assert from 'node:assert/strict';
import * as vscode from 'vscode';
import * as path from 'node:path';
import { readFile, realpath } from 'node:fs/promises';
import { verifyHighlighting } from './highlighting';
import { verifyJavaEditing } from './javaEditing';

async function eventually(check: () => boolean | Promise<boolean>, message: string, timeout = 20_000): Promise<void> {
  const until = Date.now() + timeout;
  while (Date.now() < until) {
    if (await check()) return;
    await new Promise(resolve => setTimeout(resolve, 100));
  }
  assert.fail(message);
}

export async function run(): Promise<void> {
  const extension = vscode.extensions.getExtension('lmrrcc.jx-language');
  assert.ok(extension, 'Packaged JX extension must be installed');
  await verifyHighlighting(extension.extensionPath, vscode.env.appRoot);
  const api = await extension.activate();
  if (process.env.JX_EXPECT_UNTRUSTED === '1') {
    assert.equal(vscode.workspace.isTrusted, false, 'Trust fixture must remain restricted');
    assert.equal(api.isRunning(), false, 'An untrusted workspace must not start Java');
    await vscode.commands.executeCommand('jx.restartServer');
    assert.equal(api.isRunning(), false, 'Restart must preserve the workspace trust boundary');
    let started = 0;
    const listener = vscode.tasks.onDidStartTask(() => started++);
    try {
      await vscode.commands.executeCommand('jx.dev');
      await vscode.commands.executeCommand('jx.build');
      await vscode.commands.executeCommand('jx.stopDev');
      assert.equal(started, 0, 'Restricted workspaces must not execute Maven tasks');
      assert.equal(vscode.tasks.taskExecutions.filter(task => task.task.definition.type === 'jx').length, 0);
    } finally { listener.dispose(); }
    return;
  }
  assert.equal(api.isRunning(), true, 'Bundled Java language server must initialize');
  assert.equal(vscode.tasks.taskExecutions.filter(task => task.task.definition.type === 'jx').length, 0,
    'Opening the extension must not implicitly run the project');
  const root = vscode.workspace.workspaceFolders?.[0];
  assert.ok(root);
  const uri = vscode.Uri.joinPath(root.uri, 'src/main/jx/Test.jx');
  const document = await vscode.workspace.openTextDocument(uri);
  await vscode.window.showTextDocument(document);
  assert.equal(document.languageId, 'jx');
  const position = document.positionAt(document.getText().indexOf('<div') + 1);
  let completions: vscode.CompletionList | undefined;
  for (let attempt = 0; attempt < 30; attempt++) {
    completions = await vscode.commands.executeCommand<vscode.CompletionList>('vscode.executeCompletionItemProvider', uri, position);
    if (completions?.items.length) break;
    await new Promise(resolve => setTimeout(resolve, 200));
  }
  assert.ok(completions?.items.length, 'Completion must cross the actual editor/LSP boundary');
  const appUri = vscode.Uri.joinPath(root.uri, 'src/main/jx/App.jx');
  const app = await vscode.workspace.openTextDocument(appUri);
  await vscode.window.showTextDocument(app);
  const component = app.positionAt(app.getText().indexOf('<Counter') + 2);
  const hovers = await vscode.commands.executeCommand<vscode.Hover[]>('vscode.executeHoverProvider', appUri, component);
  assert.ok(hovers?.length, 'Hover must come from the shared language server');
  const definitions = await vscode.commands.executeCommand<(vscode.Location | vscode.LocationLink)[]>('vscode.executeDefinitionProvider', appUri, component);
  assert.ok(definitions?.some(item => ('targetUri' in item ? item.targetUri : item.uri).path.endsWith('/Counter.jx')), 'Definition must navigate to the original component source');
  const symbols = await vscode.commands.executeCommand<vscode.DocumentSymbol[]>('vscode.executeDocumentSymbolProvider', appUri);
  assert.ok(symbols?.some(symbol => symbol.name === 'App'), 'Document symbols must expose the Java component');
  const value = app.getText().indexOf('initial={0}') + 'initial={'.length;
  const edit = new vscode.WorkspaceEdit();
  edit.replace(appUri, new vscode.Range(app.positionAt(value), app.positionAt(value + 1)), '"bad"');
  assert.equal(await vscode.workspace.applyEdit(edit), true);
  let diagnostics: readonly vscode.Diagnostic[] = [];
  for (let attempt = 0; attempt < 50; attempt++) {
    diagnostics = vscode.languages.getDiagnostics(appUri);
    if (diagnostics.some(item => item.severity === vscode.DiagnosticSeverity.Error)) break;
    await new Promise(resolve => setTimeout(resolve, 200));
  }
  assert.ok(diagnostics.some(item => item.severity === vscode.DiagnosticSeverity.Error && item.range.start.line === 4), 'Invalid typed props must publish an error on original .jx source');
  await vscode.commands.executeCommand('jx.restartServer');
  assert.equal(api.isRunning(), true, 'Language server must restart');

  // Restore the deliberate semantic error before a real Maven build; Start must save both documents.
  const restore = new vscode.WorkspaceEdit();
  restore.replace(appUri, new vscode.Range(app.positionAt(value), app.positionAt(value + '"bad"'.length)), '0');
  assert.equal(await vscode.workspace.applyEdit(restore), true);
  await verifyJavaEditing(root.uri);
  const counterAppUri = vscode.Uri.joinPath(root.uri, 'apps/counter-app/src/main/jx/jx/example/counter/App.jx');
  const counterApp = await vscode.workspace.openTextDocument(counterAppUri);
  await vscode.window.showTextDocument(counterApp);
  const heading = counterApp.getText().indexOf('Two counters.');
  assert.ok(heading >= 0);
  const change = new vscode.WorkspaceEdit();
  change.replace(counterAppUri, new vscode.Range(counterApp.positionAt(heading), counterApp.positionAt(heading + 'Two counters.'.length)), 'Editor counters.');
  assert.equal(await vscode.workspace.applyEdit(change), true);
  assert.equal(counterApp.isDirty, true, 'The module source is unsaved before Start');
  const starts: vscode.TaskExecution[] = [];
  const listener = vscode.tasks.onDidStartTask(event => {
    if (event.execution.task.definition.type === 'jx' && event.execution.task.definition.mode === 'dev') starts.push(event.execution);
  });
  const devTasks = () => vscode.tasks.taskExecutions.filter(execution => execution.task.definition.type === 'jx' && execution.task.definition.mode === 'dev');
  try {
    await Promise.all([vscode.commands.executeCommand('jx.dev'), vscode.commands.executeCommand('jx.dev')]);
    await eventually(() => starts.length > 0, 'Start must launch a real VS Code process task');
    assert.equal(starts.length, 1, 'Concurrent Start invocations must launch one server');
    assert.equal(counterApp.isDirty, false, 'Start must save the active module source');
    assert.ok((await readFile(counterAppUri.fsPath, 'utf8')).includes('Editor counters.'));
    const execution = starts[0];
    assert.ok(execution.task.execution instanceof vscode.ProcessExecution);
    const taskProcess = execution.task.execution;
    const module = path.join(root.uri.fsPath, 'apps/counter-app');
    assert.equal(execution.task.definition.project, await realpath(module));
    assert.equal(taskProcess.process, path.join(root.uri.fsPath, process.platform === 'win32' ? 'mvnw.cmd' : 'mvnw'));
    assert.equal(taskProcess.options?.cwd, root.uri.fsPath, 'The ancestor wrapper owns the Maven working directory');
    assert.equal(taskProcess.args[taskProcess.args.indexOf('-f') + 1], path.join(module, 'pom.xml'), 'The selected .jx module must override the aggregator POM');
    assert.ok(taskProcess.args.includes('-Dmaven.repo.local=' + path.join(root.uri.fsPath, '.cache/maven-repository')));
    assert.ok(taskProcess.args.includes('-Djx.port=' + process.env.JX_DEV_PORT));
    await eventually(async () => {
      try {
        const response = await fetch('http://127.0.0.1:' + process.env.JX_DEV_PORT + '/__jx_dev/version');
        const status = await response.json() as { state?: string };
        return response.ok && status.state === 'ready';
      } catch { return false; }
    }, 'The real Maven development goal must package and serve the isolated application', 180_000);
    await vscode.commands.executeCommand('jx.dev');
    assert.equal(starts.length, 1, 'Start while running must retain the original server');
    await vscode.commands.executeCommand('jx.stopDev');
    await eventually(() => devTasks().length === 0, 'Stop must terminate the module task');
    await eventually(async () => {
      try { await fetch('http://127.0.0.1:' + process.env.JX_DEV_PORT + '/__jx_dev/version'); return false; }
      catch { return true; }
    }, 'Stop must release the development HTTP listener');
    await vscode.workspace.getConfiguration('jx', counterAppUri).update('mavenRepository', '.cache/editor-repository', vscode.ConfigurationTarget.WorkspaceFolder);
    await vscode.commands.executeCommand('jx.dev');
    await eventually(() => starts.length === 2, 'An ended task must release its module slot for restart');
    const restarted = starts[1].task.execution;
    assert.ok(restarted instanceof vscode.ProcessExecution);
    assert.ok(restarted.args.includes('-Dmaven.repo.local=' + path.join(root.uri.fsPath, '.cache/editor-repository')),
      'An explicit workspace-relative Maven repository must override automatic cache discovery');
    await vscode.commands.executeCommand('jx.stopDev');
    await eventually(() => devTasks().length === 0, 'The restarted task must also stop');
  } finally {
    listener.dispose();
    await vscode.commands.executeCommand('jx.stopDev');
  }
}

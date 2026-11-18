import * as assert from 'node:assert/strict';
import * as vscode from 'vscode';

export async function run(): Promise<void> {
  const extension = vscode.extensions.getExtension('lmrrcc.jx-language');
  assert.ok(extension, 'Packaged JX extension must be installed');
  const api = await extension.activate();
  if (process.env.JX_EXPECT_UNTRUSTED === '1') {
    assert.equal(vscode.workspace.isTrusted, false, 'Trust fixture must remain restricted');
    assert.equal(api.isRunning(), false, 'An untrusted workspace must not start Java');
    await vscode.commands.executeCommand('jx.restartServer');
    assert.equal(api.isRunning(), false, 'Restart must preserve the workspace trust boundary');
    return;
  }
  assert.equal(api.isRunning(), true, 'Bundled Java language server must initialize');
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
}

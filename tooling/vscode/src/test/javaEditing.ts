import * as assert from 'node:assert/strict';
import * as vscode from 'vscode';
import { access, readFile } from 'node:fs/promises';
import { MappedFile } from '../javaEditing';

async function eventually<T>(read: () => PromiseLike<T>, accepts: (value: T) => boolean, message: string): Promise<T> {
  const until = Date.now() + 90_000;
  let value: T;
  do {
    value = await read();
    if (accepts(value)) return value;
    await new Promise(resolve => setTimeout(resolve, 200));
  } while (Date.now() < until);
  assert.fail(message + ': ' + JSON.stringify(value));
}

export async function verifyJavaEditing(root: vscode.Uri): Promise<void> {
  const java = vscode.extensions.getExtension('redhat.java');
  assert.ok(java, 'The official Java extension is installed in the isolated editor');
  await (await java.activate()).serverReady();
  const uri = vscode.Uri.joinPath(root, 'apps/counter-app/src/main/jx/jx/example/counter/NativeEditing.jx');
  const document = await vscode.workspace.openTextDocument(uri);
  await vscode.window.showTextDocument(document);
  const generatedUri = vscode.Uri.joinPath(root, 'apps/counter-app/target/generated-sources/jx/jx/example/counter/NativeEditing.java');
  const generatedBefore = await readFile(generatedUri.fsPath, 'utf8');
  const at = (text: string, advance = 0) => {
    const index = document.getText().indexOf(text);
    assert.ok(index >= 0, 'Fixture contains ' + text);
    return document.positionAt(index + advance);
  };
  const completion = (text: string, advance: number) => vscode.commands.executeCommand<vscode.CompletionList>('vscode.executeCompletionItemProvider', uri, at(text, advance));
  const label = (item: vscode.CompletionItem) => typeof item.label === 'string' ? item.label : item.label.label;
  const removeQualifier = new vscode.WorkspaceEdit();
  const qualifier = at('new java.util.ArrayList', 'new '.length);
  removeQualifier.delete(uri, new vscode.Range(qualifier, qualifier.translate(0, 'java.util.'.length)));
  await vscode.workspace.applyEdit(removeQualifier);
  const imports = await eventually(() => vscode.commands.executeCommand<vscode.CompletionList>('vscode.executeCompletionItemProvider', uri, at('new ArrayList', 'new ArrayL'.length), undefined, 50),
    result => !!result?.items.some(item => label(item).startsWith('ArrayList') && item.additionalTextEdits?.some(edit => edit.newText.includes('import java.util.ArrayList;'))), 'Native completion resolves an auto-import into .jx');
  const imported = imports.items.find(item => label(item).startsWith('ArrayList') && item.additionalTextEdits?.some(edit => edit.newText.includes('import java.util.ArrayList;')))!;
  const addImport = new vscode.WorkspaceEdit();
  addImport.set(uri, imported.additionalTextEdits!);
  await vscode.workspace.applyEdit(addImport);
  assert.ok(document.getText().includes('import java.util.ArrayList;'));
  await eventually(() => completion('label.length', 'label.len'.length), result => !!result?.items.some(item => label(item) === 'length' && item.kind === vscode.CompletionItemKind.Method), 'Native Java completion outside markup');
  await eventually(() => completion('label.substring', 'label.sub'.length), result => !!result?.items.some(item => label(item) === 'substring' && item.kind === vscode.CompletionItemKind.Method), 'Native Java completion inside a markup attribute');
  await eventually(() => completion('event.preventDefault', 'event.pre'.length), result => !!result?.items.some(item => label(item) === 'preventDefault' && item.kind === vscode.CompletionItemKind.Method), 'Native event lambda parameter type is jx.Event');
  const methodStart = at('event.preventDefault()', 'event.'.length);
  const incomplete = new vscode.WorkspaceEdit();
  incomplete.delete(uri, new vscode.Range(methodStart, methodStart.translate(0, 'preventDefault()'.length)));
  await vscode.workspace.applyEdit(incomplete);
  await eventually(() => completion('event.', 'event.'.length), result => !!result?.items.some(item => label(item) === 'preventDefault' && item.kind === vscode.CompletionItemKind.Method), 'Native Java completion while typing an incomplete event expression');
  const finish = new vscode.WorkspaceEdit();
  finish.insert(uri, methodStart, 'preventDefault()');
  await vscode.workspace.applyEdit(finish);
  const hovers = await vscode.commands.executeCommand<vscode.Hover[]>('vscode.executeHoverProvider', uri, at('label.substring', 1));
  assert.ok(hovers.some(hover => hover.contents.some(content => (typeof content === 'string' ? content : content.value).includes('String'))), 'Native Java local-variable hover inside markup');
  const signatures = await vscode.commands.executeCommand<vscode.SignatureHelp>('vscode.executeSignatureHelpProvider', uri, at('substring(1)', 'substring('.length));
  assert.ok(signatures?.signatures.some(signature => signature.label.includes('substring')), 'Native overload signature help');
  const definitions = await vscode.commands.executeCommand<(vscode.Location | vscode.LocationLink)[]>('vscode.executeDefinitionProvider', uri, at('NativeHelper.label', 'NativeHelper.'.length));
  assert.ok(definitions?.some(location => ('targetUri' in location ? location.targetUri : location.uri).path.endsWith('/NativeHelper.java')), 'Native cross-file definition reaches ordinary Java');
  const references = await vscode.commands.executeCommand<vscode.Location[]>('vscode.executeReferenceProvider', uri, at('String label', 'String '.length));
  assert.ok(references?.filter(location => location.uri.toString() === uri.toString()).length! >= 3, 'Native Java references map back from markup to .jx');
  const renamed = await vscode.commands.executeCommand<vscode.WorkspaceEdit>('vscode.executeDocumentRenameProvider', uri, at('String label', 'String '.length), 'caption');
  assert.ok(renamed?.size, 'Native rename returns source edits');
  assert.ok(renamed.entries().every(([target]) => target.toString() === uri.toString()), 'Rename never edits generated Java');
  assert.equal(await vscode.workspace.applyEdit(renamed), true);
  assert.ok(document.getText().includes('String caption = NativeHelper.label'));
  assert.ok(document.getText().includes('title={caption.substring(1)}'));
  assert.ok(document.getText().includes('{caption.toUpperCase()}'));
  const problem = at('caption.length');
  const breakSymbol = new vscode.WorkspaceEdit();
  breakSymbol.replace(uri, new vscode.Range(problem, problem.translate(0, 'caption'.length)), 'missingValue');
  await vscode.workspace.applyEdit(breakSymbol);
  await eventually(async () => vscode.languages.getDiagnostics(uri), diagnostics => diagnostics.some(diagnostic => diagnostic.source === 'Java' && diagnostic.message.includes('missingValue') && diagnostic.range.start.line === problem.line), 'Native Java diagnostic maps to original source');
  const restore = new vscode.WorkspaceEdit();
  restore.replace(uri, new vscode.Range(problem, problem.translate(0, 'missingValue'.length)), 'caption');
  await vscode.workspace.applyEdit(restore);
  const helperUri = vscode.Uri.joinPath(root, 'apps/counter-app/src/main/java/jx/example/counter/NativeHelper.java');
  const helper = await vscode.workspace.openTextDocument(helperUri);
  const helperEditor = await vscode.window.showTextDocument(helper);
  helperEditor.selection = new vscode.Selection(helper.positionAt(helper.getText().indexOf('greeting')), helper.positionAt(helper.getText().indexOf('greeting')));
  const originalDefinitions = await vscode.commands.executeCommand<(vscode.Location | vscode.LocationLink)[]>('vscode.executeDefinitionProvider', helperUri, helperEditor.selection.active);
  assert.ok(originalDefinitions?.some(location => ('targetUri' in location ? location.targetUri : location.uri).toString() === generatedUri.toString()), 'The native project owns the existing Maven generated-source root: ' + JSON.stringify(originalDefinitions));
  await vscode.commands.executeCommand('jx.goToDefinition');
  assert.equal(vscode.window.activeTextEditor?.document.uri.toString(), uri.toString(), 'Java-to-JX navigation opens authored source instead of a phantom Java mirror');
  assert.equal(vscode.window.activeTextEditor?.selection.active.line, at('String greeting').line);
  const methodRename = await vscode.commands.executeCommand<vscode.WorkspaceEdit>('vscode.executeDocumentRenameProvider', uri, at('String greeting', 'String '.length), 'welcome');
  assert.ok(methodRename?.entries().some(([target]) => target.toString() === helperUri.toString()), 'Native method rename finds references in an open ordinary Java file');
  assert.ok(methodRename.entries().some(([target]) => target.toString() === uri.toString()), 'Native method rename maps its JX declaration and references');
  assert.equal(await vscode.workspace.applyEdit(methodRename), true);
  assert.ok(document.getText().includes('String welcome(String value)'));
  assert.ok(document.getText().includes('NativeHelper.label(welcome("Editor"))'));
  assert.ok(helper.getText().includes('NativeEditing.welcome(value)'));
  const beforeRejectedRename = document.getText(), helperBeforeRejectedRename = helper.getText();
  await assert.rejects(Promise.resolve(vscode.commands.executeCommand('vscode.executeDocumentRenameProvider', uri, at('class NativeEditing', 'class '.length), 'RenamedComponent')),
    /generated markup|file moves|rename/i, 'Class rename crossing synthetic component metadata fails atomically');
  assert.equal(document.getText(), beforeRejectedRename);
  assert.equal(helper.getText(), helperBeforeRejectedRename);
  await helper.save();
  await document.save();
  assert.equal(await readFile(generatedUri.fsPath, 'utf8'), generatedBefore, 'Unsaved and saved editor projections never modify existing generated Java');
  const foreignUri = vscode.Uri.joinPath(root, 'apps/counter-app/target/generated-sources/jx/jx/example/counter/Foreign.java');
  const foreignBefore = await readFile(foreignUri.fsPath, 'utf8');
  await assert.rejects(Promise.resolve(vscode.commands.executeCommand('java.execute.workspaceCommand', 'jx.java.sync', {
    session: 'unowned-output-test', projectUri: vscode.Uri.joinPath(root, 'apps/counter-app').toString(),
    files: [{ sourceUri: uri.toString(), relativeJavaPath: 'jx/example/counter/Foreign.java', javaText: 'package jx.example.counter; public class Foreign {}' }]
  })), /hide authored or unowned Java/, 'A generated-directory Java file without matching output ownership is protected');
  await vscode.commands.executeCommand('java.execute.workspaceCommand', 'jx.java.close', 'unowned-output-test');
  assert.equal(await readFile(foreignUri.fsPath, 'utf8'), foreignBefore);
  const binding = vscode.extensions.getExtension('lmrrcc.jx-language')!.packageJSON.contributes.keybindings.find((value: { command: string }) => value.command === 'jx.goToDefinition');
  assert.ok(binding.when.includes('jx.javaNavigation') && binding.when.includes('isWorkspaceTrusted'), 'F12 is scoped to trusted JX Maven projects');
  const phantom = vscode.Uri.joinPath(root, 'apps/counter-app/src/main/java/jx/example/counter/NativeEditing.java');
  await assert.rejects(access(phantom.fsPath), 'Native editing must not create Java mirror files');
  assert.ok(!vscode.workspace.textDocuments.some(value => value.uri.toString() === phantom.toString()), 'No hidden dirty Java editor');

  const map = new MappedFile({ sourceUri: uri.toString(), version: 1, sourceText: 'a😀b', relativeJavaPath: 'T.java', javaText: 'a😀GENERATEDb', sourceLength: 4,
    topLevelTypes: [], segments: [{ generatedStart: 0, length: 3, sourceStart: 0, linear: true }, { generatedStart: 3, length: 9, sourceStart: 3, linear: false }, { generatedStart: 12, length: 1, sourceStart: 3, linear: true }] }, phantom.toString());
  assert.deepEqual(map.generatedPosition(new vscode.Position(0, 3)), { line: 0, character: 12 }, 'Positions use UTF-16 code units');
  assert.equal(map.sourceRange({ start: { line: 0, character: 2 }, end: { line: 0, character: 13 } }), undefined, 'Edits crossing generated markup are rejected');
  assert.deepEqual(map.sourceRange({ start: { line: 0, character: 3 }, end: { line: 0, character: 3 } }), { start: { line: 0, character: 3 }, end: { line: 0, character: 3 } }, 'Completion insertion at a copied Java slice end uses source affinity');
  assert.deepEqual(map.sourceRange({ start: { line: 0, character: 12 }, end: { line: 0, character: 13 } }), { start: { line: 0, character: 3 }, end: { line: 0, character: 4 } });
  console.log('PASS: packaged native Java completion, markup/event types, hover, signatures, definitions, references, rename, diagnostics, UTF-16 and projection isolation.');
}

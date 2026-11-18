const vscode = require('vscode');
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');

exports.run = async () => {
  const api = await vscode.extensions.getExtension('redhat.java').activate();
  await api.serverReady();
  const root = vscode.workspace.workspaceFolders[0].uri;
  const sourceUri = vscode.Uri.joinPath(root, 'src/main/jx/demo/Counter.jx').toString();
  const javaText = 'package demo; public class Counter { public static java.util.List<String> values() { return new ArrayList<>(); } public static String value() { String label = Helper.greet("Hello"); return label.substring(1); } }';
  const invoke = (name, input) => vscode.commands.executeCommand('java.execute.workspaceCommand', 'jx.java.' + name, input);
  const files = await invoke('sync', { session: 'native-probe', projectUri: root.toString(), files: [{ sourceUri, relativeJavaPath: 'demo/Counter.java', javaText }] });
  console.log('NATIVE FILES', JSON.stringify(files));
  assert.equal(files.length, 1);
  const javaUri = files[0].javaUri;
  for (const cast of [false, true]) {
    const expression = cast ? '(java.util.function.Consumer<String>)(value -> value.)' : 'value -> value.';
    const code = 'package demo; public class Counter { void consume(java.util.function.Consumer<String> fn) {} void render(){ consume(' + expression + '); } }';
    await invoke('sync', { session: 'native-probe', projectUri: root.toString(), files: [{ sourceUri, relativeJavaPath: 'demo/Counter.java', javaText: code }] });
    const items = await invoke('completion', { textDocument: { uri: javaUri }, position: { line: 0, character: code.indexOf('value.') + 'value.'.length } });
    console.log('NATIVE INCOMPLETE LAMBDA', cast, JSON.stringify(items.items.map(item => item.label)));
    if (cast) assert.ok(items.items.some(item => item.label === 'substring'), 'Explicit target type retains incomplete lambda completion');
  }
  await invoke('sync', { session: 'native-probe', projectUri: root.toString(), files: [{ sourceUri, relativeJavaPath: 'demo/Counter.java', javaText }] });
  const importItems = await invoke('completion', { textDocument: { uri: javaUri }, position: { line: 0, character: javaText.indexOf('ArrayList') + 'ArrayL'.length } });
  const importItem = importItems.items.find(item => item.label === 'ArrayList');
  const resolvedImport = await invoke('resolveCompletion', importItem);
  console.log('NATIVE IMPORT', JSON.stringify(resolvedImport));
  assert.ok(resolvedImport.additionalTextEdits?.some(edit => edit.newText.includes('import java.util.ArrayList')), 'Native completion import resolution');
  await assert.rejects(fs.stat(vscode.Uri.parse(javaUri).fsPath), { code: 'ENOENT' });
  const offset = javaText.indexOf('substring') + 3;
  const result = await invoke('completion', { textDocument: { uri: javaUri }, position: { line: 0, character: offset } });
  console.log('NATIVE COMPLETION', JSON.stringify(result).slice(0, 1000));
  assert.ok(result.items.some(item => item.label.includes('substring')), 'Expected native JDT substring completion');
  const hover = await invoke('hover', { textDocument: { uri: javaUri }, position: { line: 0, character: javaText.lastIndexOf('label') + 1 } });
  console.log('NATIVE HOVER', JSON.stringify(hover));
  assert.ok(JSON.stringify(hover).includes('String'));
  const declaration = javaText.indexOf('label');
  const reference = javaText.lastIndexOf('label');
  const references = await invoke('references', { textDocument: { uri: javaUri }, position: { line: 0, character: declaration + 1 }, context: { includeDeclaration: true } });
  assert.ok(references.some(location => location.range.start.character === reference), 'Native local-variable reference');
  const rename = await invoke('rename', { textDocument: { uri: javaUri }, position: { line: 0, character: declaration + 1 }, newName: 'title' });
  console.log('NATIVE RENAME', JSON.stringify(rename));
  assert.ok(JSON.stringify(rename).includes('title'), 'Native local-variable rename edits');
  const definition = await invoke('definition', { textDocument: { uri: javaUri }, position: { line: 0, character: javaText.indexOf('greet') + 1 } });
  assert.ok(definition.some(location => location.uri.endsWith('/Helper.java')), 'Native cross-file Java definition');
  const signature = await invoke('signatureHelp', { textDocument: { uri: javaUri }, position: { line: 0, character: javaText.indexOf('substring(') + 'substring('.length } });
  assert.ok(signature.signatures.some(item => item.label.includes('substring')), 'Native overload signatures');
  await assert.rejects(invoke('sync', { session: 'collision-probe', projectUri: root.toString(), files: [{ sourceUri, relativeJavaPath: 'demo/Helper.java', javaText }] }), /hide authored or unowned Java/);
  await invoke('close', 'collision-probe');
  await invoke('sync', { session: 'native-probe', projectUri: root.toString(), files: [{ sourceUri, relativeJavaPath: 'demo/Counter.java', javaText: javaText.replace('Helper.greet("Hello")', 'missingName') }] });
  const diagnostics = await invoke('diagnostics', 'native-probe');
  console.log('NATIVE DIAGNOSTICS', JSON.stringify(diagnostics));
  assert.ok(diagnostics.some(file => file.diagnostics.some(diagnostic => diagnostic.message.includes('missingName'))), 'Native unresolved Java symbol diagnostic');
  await invoke('close', 'native-probe');
  await invoke('sync', { session: 'after-close', projectUri: root.toString(), files: [{ sourceUri, relativeJavaPath: 'demo/Counter.java', javaText }] });
  await invoke('close', 'after-close');
  const testSource = vscode.Uri.joinPath(root, 'src/test/jx/demo/TestOnly.jx').toString();
  const scoped = await invoke('sync', { session: 'source-scopes', projectUri: root.toString(), files: [
    { sourceUri, relativeJavaPath: 'demo/Counter.java', javaText: 'package demo; public class Counter { TestOnly forbidden; }' },
    { sourceUri: testSource, relativeJavaPath: 'demo/TestOnly.java', javaText: 'package demo; public class TestOnly { Counter allowed; }' }
  ] });
  assert.ok(scoped.find(file => file.sourceUri === testSource).javaUri.includes('/src/test/java/'), 'Test projections retain the Maven test source root');
  const scopeDiagnostics = await invoke('diagnostics', 'source-scopes');
  assert.ok(scopeDiagnostics.find(file => file.uri.includes('/src/main/java/')).diagnostics.some(diagnostic => diagnostic.message.includes('TestOnly')), 'Main Java cannot resolve a test-only projected type');
  assert.ok(!scopeDiagnostics.find(file => file.uri.includes('/src/test/java/')).diagnostics.some(diagnostic => diagnostic.severity === 1), 'Test Java can resolve main projected types');
  await invoke('close', 'source-scopes');
  assert.ok(!vscode.workspace.textDocuments.some(document => document.uri.toString() === javaUri), 'No hidden dirty Java editor');
  console.log('PASS: native JDT completion, hover, signatures, definitions, references, rename and diagnostics; authored-source collision rejected; no disk projection or hidden dirty editor.');
};

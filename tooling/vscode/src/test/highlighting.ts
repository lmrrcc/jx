import * as assert from 'node:assert/strict';
import * as path from 'node:path';
import { readFile } from 'node:fs/promises';
import { INITIAL, Registry, parseRawGrammar, IToken } from 'vscode-textmate';
import { loadWASM, OnigScanner, OnigString } from 'vscode-oniguruma';

/** The real TextMate/Oniguruma tokenizer used by the verified editor, not string checks on grammar JSON. */
export async function verifyHighlighting(extensionPath: string, appRoot: string): Promise<void> {
  const wasm = await readFile(require.resolve('vscode-oniguruma/release/onig.wasm'));
  await loadWASM(Uint8Array.from(wasm).buffer);
  const registry = new Registry({
    onigLib: Promise.resolve({
      createOnigScanner: patterns => new OnigScanner(patterns),
      createOnigString: value => new OnigString(value)
    }),
    loadGrammar: async scope => {
      const file = scope === 'source.jx' ? path.join(extensionPath, 'syntaxes/jx.tmLanguage.json')
        : scope === 'source.java' ? path.join(appRoot, 'extensions/java/syntaxes/java.tmLanguage.json') : undefined;
      return file ? parseRawGrammar(await readFile(file, 'utf8'), file) : null;
    }
  });
  try {
    const grammar = await registry.loadGrammar('source.jx');
    assert.ok(grammar);
    const lines = [
      'public class ColorExample {',
      '  public static View render() {',
      '    String marker = "<fake>";',
      '    List<String> names = List.of("Ada");',
      '    return (',
      '      <main class="panel" aria-label=\'Demo\'>',
      '        <button disabled onClick={event -> { int next = 1; log("}"); }}>Hello <strong>{names.get(0)}</strong></button>',
      '        {names.stream().map(name -> <span title={name}>{name}</span>).toList()}',
      '        <><input value="ready" /><p>Ready &amp; waiting</p></>',
      '      </main>',
      '    );',
      '  }',
      '  public static int after() { return 2; }',
      '}'
    ];
    const tokens: IToken[][] = [];
    let stack = INITIAL;
    for (const line of lines) {
      const result = grammar.tokenizeLine(line, stack);
      tokens.push(result.tokens); stack = result.ruleStack;
    }
    const scopes = (line: number, text: string, offset = 0) => {
      const at = lines[line].indexOf(text) + offset;
      assert.ok(at >= 0, text + ' fixture must exist');
      const token = tokens[line].find(value => value.startIndex <= at && value.endIndex > at);
      assert.ok(token, 'Expected token for ' + text);
      return token.scopes;
    };
    const has = (line: number, text: string, expected: string, offset = 0) =>
      assert.ok(scopes(line, text, offset).some(scope => scope.startsWith(expected)),
        text + ' must have ' + expected + '; actual: ' + scopes(line, text, offset).join(', '));
    has(5, 'main', 'entity.name.tag');
    has(5, 'class', 'entity.other.attribute-name');
    has(5, 'aria-label', 'entity.other.attribute-name');
    has(5, 'panel', 'string.quoted.double');
    has(5, 'Demo', 'string.quoted.single');
    has(6, 'button', 'entity.name.tag');
    has(6, 'disabled', 'entity.other.attribute-name');
    has(6, 'onClick', 'entity.other.attribute-name');
    has(6, 'int next', 'storage.type');
    has(6, 'int next', 'source.java');
    has(6, '"}"', 'string.quoted', 1);
    has(6, 'Hello', 'text.html');
    has(6, 'strong', 'entity.name.tag');
    has(7, 'span', 'entity.name.tag');
    has(7, 'title', 'entity.other.attribute-name');
    has(7, '{name}', 'source.java', 1);
    has(8, 'input', 'entity.name.tag');
    has(8, 'value', 'entity.other.attribute-name');
    has(8, 'ready', 'string.quoted.double');
    has(8, '&amp;', 'constant.character.entity');
    has(9, 'main', 'entity.name.tag');
    has(12, 'int after', 'storage.type');
    assert.ok(!scopes(2, 'fake').some(scope => scope.startsWith('entity.name.tag')), 'Java strings must not become HTML tags');
    assert.ok(!scopes(3, 'String').some(scope => scope.startsWith('entity.name.tag')), 'Java generic types must not become HTML tags');
    assert.ok(!scopes(12, 'int after').some(scope => scope.startsWith('text.html')), 'Closing markup must restore the surrounding Java scope');
  } finally { registry.dispose(); }
}


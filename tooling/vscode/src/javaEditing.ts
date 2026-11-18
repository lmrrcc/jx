import * as vscode from 'vscode';
import { randomUUID } from 'node:crypto';
import * as path from 'node:path';
import { access } from 'node:fs/promises';
import { LanguageClient } from 'vscode-languageclient/node';
import type * as protocol from 'vscode-languageclient/node';

interface Segment { generatedStart: number; length: number; sourceStart: number; linear: boolean }
interface ProjectionFile {
  sourceUri: string; version: number | null; sourceText: string; relativeJavaPath: string; javaText: string;
  segments: Segment[]; sourceLength: number; topLevelTypes: string[];
}
interface Projection { protocolVersion: number; projectUri: string; files: ProjectionFile[]; unavailable?: string[] }
interface NativeFile { sourceUri: string; javaUri: string }
interface JavaApi { serverReady(): Promise<boolean>; serverMode: string }
interface Prepared { file: MappedFile; position: protocol.Position; client: LanguageClient; version: number; document: vscode.TextDocument }

function key(uri: string): string { return vscode.Uri.parse(uri).toString(); }
function offset(text: string, position: protocol.Position): number {
  let start = 0;
  for (let line = 0; line < position.line; line++) {
    const next = text.indexOf('\n', start);
    if (next < 0) return text.length;
    start = next + 1;
  }
  return Math.min(text.length, start + position.character);
}
function position(text: string, value: number): protocol.Position {
  const end = Math.max(0, Math.min(text.length, value));
  let line = 0, start = 0;
  for (let index = 0; index < end; index++) if (text.charCodeAt(index) === 10) { line++; start = index + 1; }
  return { line, character: end - start };
}

/** Copied source spans are editable. Generated scaffolding is never treated as authored Java. */
export class MappedFile {
  constructor(readonly projection: ProjectionFile, readonly javaUri: string, readonly projectUri?: string) {}
  generatedPosition(sourcePosition: vscode.Position): protocol.Position | undefined {
    const sourceOffset = offset(this.projection.sourceText, sourcePosition);
    const segment = this.projection.segments.find(value => value.linear && sourceOffset >= value.sourceStart && sourceOffset < value.sourceStart + value.length)
      ?? this.projection.segments.find(value => value.linear && sourceOffset === value.sourceStart + value.length);
    return segment ? position(this.projection.javaText, segment.generatedStart + sourceOffset - segment.sourceStart) : undefined;
  }
  sourceRange(range: protocol.Range, anchorSynthetic = false): protocol.Range | undefined {
    const start = offset(this.projection.javaText, range.start), end = offset(this.projection.javaText, range.end);
    const segments = this.projection.segments;
    const boundary = start === end ? segments.find(segment => segment.linear && start === segment.generatedStart + segment.length) : undefined;
    const first = boundary ?? segments.find(segment => start >= segment.generatedStart && start < segment.generatedStart + segment.length)
      ?? segments.find(segment => start === segment.generatedStart + segment.length && segment.linear);
    if (!first) return undefined;
    if (!first.linear) {
      return anchorSynthetic ? { start: position(this.projection.sourceText, first.sourceStart), end: position(this.projection.sourceText, Math.min(this.projection.sourceLength, first.sourceStart + 1)) } : undefined;
    }
    const sourceStart = first.sourceStart + start - first.generatedStart;
    let cursor = start;
    while (cursor < end) {
      const segment = segments.find(value => cursor >= value.generatedStart && cursor < value.generatedStart + value.length);
      if (!segment || !segment.linear || segment.sourceStart + cursor - segment.generatedStart !== sourceStart + cursor - start) return undefined;
      cursor = Math.min(end, segment.generatedStart + segment.length);
    }
    return { start: position(this.projection.sourceText, sourceStart), end: position(this.projection.sourceText, sourceStart + end - start) };
  }
  edit(edit: protocol.TextEdit): protocol.TextEdit | undefined {
    const range = this.sourceRange(edit.range);
    return range ? { ...edit, range } : undefined;
  }
  renameEdits(edit: protocol.TextEdit, oldName: string, newName: string): protocol.TextEdit[] | undefined {
    const direct = this.edit(edit);
    if (direct) return [direct];
    // JDT may coalesce several replacements into one edit spanning unchanged generated markup.
    // Split only proven identifier substitutions and verify every other token is unchanged.
    const start = offset(this.projection.javaText, edit.range.start);
    const original = this.projection.javaText.slice(start, offset(this.projection.javaText, edit.range.end));
    const tokens = (text: string) => [...text.matchAll(/[\p{ID_Start}_$][\p{ID_Continue}$]*|[^\p{ID_Start}_$]+/gu)];
    const before = tokens(original), after = tokens(edit.newText);
    if (before.length !== after.length) return undefined;
    const result: protocol.TextEdit[] = [];
    for (let index = 0; index < before.length; index++) {
      if (before[index][0] === after[index][0]) continue;
      if (before[index][0] !== oldName || after[index][0] !== newName) return undefined;
      const begin = start + before[index].index!;
      const mapped = this.edit({ range: { start: position(this.projection.javaText, begin), end: position(this.projection.javaText, begin + oldName.length) }, newText: newName });
      if (!mapped) return undefined;
      result.push(mapped);
    }
    return result;
  }
}

/** Owns projected JDT working copies and delegates semantic work to the official Java extension. */
export class JavaEditing implements vscode.Disposable {
  private readonly instance = randomUUID();
  private readonly resources: vscode.Disposable[] = [];
  private readonly diagnostics = vscode.languages.createDiagnosticCollection('jx-java');
  private readonly sessions = new Map<string, string>();
  private readonly projectSources = new Map<string, Set<string>>();
  private readonly files = new Map<string, MappedFile>();
  private readonly sources = new Map<string, MappedFile>();
  private readonly completions = new WeakMap<vscode.CompletionItem, { prepared: Prepared; raw: protocol.CompletionItem }>();
  private initialized: Promise<void> | undefined;
  private serial: Promise<unknown> = Promise.resolve();
  private timer: ReturnType<typeof setTimeout> | undefined;
  private disposed = false;

  constructor(private readonly getClient: () => LanguageClient | undefined, private readonly output: vscode.LogOutputChannel) {
    const selector: vscode.DocumentSelector = { language: 'jx', scheme: 'file' };
    this.resources.push(this.diagnostics,
      vscode.languages.registerCompletionItemProvider(selector, {
        provideCompletionItems: (document, at, token, context) => this.safe(() => this.complete(document, at, token, context)),
        resolveCompletionItem: (item, token) => this.safe(() => this.resolve(item, token))
      }, '.', '@'),
      vscode.languages.registerHoverProvider(selector, { provideHover: (document, at, token) => this.safe(async () => {
        const prepared = await this.prepare(document, at, token); if (!prepared) return undefined;
        const hover = await this.invoke<protocol.Hover | null>('hover', this.params(prepared), token);
        if (!this.current(prepared, token) || !hover) return undefined;
        const range = hover.range ? prepared.file.sourceRange(hover.range) : undefined;
        const contents = Array.isArray(hover.contents) ? hover.contents.map(value => typeof value === 'string' ? this.documentation(value) : value)
          : typeof hover.contents === 'string' ? this.documentation(hover.contents)
          : 'kind' in hover.contents ? { ...hover.contents, value: this.documentation(hover.contents.value) } : hover.contents;
        return prepared.client.protocol2CodeConverter.asHover({ ...hover, contents, range });
      }) }),
      vscode.languages.registerDefinitionProvider(selector, { provideDefinition: (document, at, token) => this.safe(async () => {
        const prepared = await this.prepare(document, at, token); if (!prepared) return undefined;
        const locations = await this.invoke<protocol.Location[]>('definition', this.params(prepared), token);
        return this.current(prepared, token) ? this.locations(locations) : undefined;
      }) }),
      vscode.languages.registerReferenceProvider(selector, { provideReferences: (document, at, context, token) => this.safe(async () => {
        const prepared = await this.prepare(document, at, token); if (!prepared) return undefined;
        const locations = await this.invoke<protocol.Location[]>('references', { ...this.params(prepared), context }, token);
        return this.current(prepared, token) ? this.locations(locations) : undefined;
      }) }),
      vscode.languages.registerSignatureHelpProvider(selector, { provideSignatureHelp: (document, at, token) => this.safe(async () => {
        const prepared = await this.prepare(document, at, token); if (!prepared) return undefined;
        const help = await this.invoke<protocol.SignatureHelp | null>('signatureHelp', this.params(prepared), token);
        return this.current(prepared, token) ? prepared.client.protocol2CodeConverter.asSignatureHelp(help, token) : undefined;
      }) }, '(', ','),
      vscode.languages.registerRenameProvider(selector, {
        prepareRename: (document, at, token) => this.safe(async () => {
          const prepared = await this.prepare(document, at, token); if (!prepared) return undefined;
          const range = await this.invoke<protocol.Range | null>('prepareRename', this.params(prepared), token);
          const mapped = range ? prepared.file.sourceRange(range) : undefined;
          return mapped && this.current(prepared, token) ? new vscode.Range(mapped.start.line, mapped.start.character, mapped.end.line, mapped.end.character) : undefined;
        }),
        provideRenameEdits: (document, at, newName, token) => this.queue(() => this.rename(document, at, newName, token))
      }),
      vscode.workspace.onDidOpenTextDocument(document => { if (document.languageId === 'jx') this.schedule(); }),
      vscode.workspace.onDidGrantWorkspaceTrust(() => this.schedule()),
      vscode.window.onDidChangeActiveTextEditor(() => { void this.updateNavigationContext(); }),
      vscode.workspace.onDidChangeTextDocument(event => { if (event.document.languageId === 'jx') this.schedule(); }),
      vscode.workspace.onDidCloseTextDocument(document => {
        if (document.languageId === 'jx') { this.diagnostics.delete(document.uri); void this.releaseIfUnused().catch(error => this.log(error)); }
      })
    );
    this.schedule();
    void this.updateNavigationContext();
  }

  private async initialize(): Promise<void> {
    if (!vscode.workspace.isTrusted || this.disposed) throw new Error('Native Java editing requires a trusted workspace');
    this.initialized ??= (async () => {
      const extension = vscode.extensions.getExtension<JavaApi>('redhat.java');
      if (!extension) throw new Error('Install Language Support for Java by Red Hat 1.58.0 for native Java editing');
      if (extension.packageJSON.version !== '1.58.0') throw new Error(`JX native Java editing currently requires Red Hat Java 1.58.0; found ${extension.packageJSON.version}`);
      const api = await extension.activate();
      await api.serverReady();
      if (api.serverMode === 'LightWeight') throw new Error('Switch the Java language server to Standard mode for native JX Java editing');
      try {
        const info = await this.invoke<{ protocolVersion: number; javaVersion: number }>('info', {});
        if (info.protocolVersion !== 1 || info.javaVersion < 25) throw new Error('Incompatible Java bridge');
      } catch (error) {
        throw new Error('JX requires Red Hat Java 1.58.0 running on Java 25 or newer. Check java.jdt.ls.java.home and restart Java. ' + String(error));
      }
    })().catch(error => { this.initialized = undefined; throw error; });
    await this.initialized;
  }
  private invoke<T>(name: string, value: unknown, token?: vscode.CancellationToken): Promise<T> {
    return Promise.resolve(vscode.commands.executeCommand<T>('java.execute.workspaceCommand', `jx.java.${name}`, value, ...(token ? [token] : [])));
  }
  private async sync(document: vscode.TextDocument, token?: vscode.CancellationToken): Promise<LanguageClient | undefined> {
    await this.initialize();
    const client = this.getClient();
    if (!client?.isRunning() || token?.isCancellationRequested) return undefined;
    const version = document.version;
    const params = { uri: document.uri.toString(), version };
    const projection = token
      ? await client.sendRequest<Projection>('jx/editorProjection', params, token)
      : await client.sendRequest<Projection>('jx/editorProjection', params);
    if (this.disposed || document.version !== version || token?.isCancellationRequested) return undefined;
    if (projection.protocolVersion !== 1) throw new Error('Unsupported JX editor projection protocol');
    const session = this.sessions.get(projection.projectUri) ?? `${this.instance}:${randomUUID()}`;
    const created = !this.sessions.has(projection.projectUri);
    this.sessions.set(projection.projectUri, session);
    let native: NativeFile[];
    try { native = await this.invoke<NativeFile[]>('sync', { session, projectUri: projection.projectUri, files: projection.files }, token); }
    catch (error) {
      if (created) try { await this.invoke('close', session); this.sessions.delete(projection.projectUri); }
      catch (cleanup) { this.log(cleanup); }
      throw error;
    }
    for (const uri of this.projectSources.get(projection.projectUri) ?? []) {
      const previous = this.sources.get(uri);
      if (previous) this.files.delete(key(previous.javaUri));
      this.sources.delete(uri);
      if (!projection.files.some(file => key(file.sourceUri) === uri)) this.diagnostics.delete(vscode.Uri.parse(uri));
    }
    this.projectSources.set(projection.projectUri, new Set(projection.files.map(file => key(file.sourceUri))));
    for (const uri of projection.unavailable ?? []) this.diagnostics.delete(vscode.Uri.parse(uri));
    for (const input of projection.files) {
      const destination = native.find(value => key(value.sourceUri) === key(input.sourceUri));
      if (!destination) throw new Error('Native Java projection omitted a source');
      const file = new MappedFile(input, destination.javaUri, projection.projectUri);
      this.files.set(key(destination.javaUri), file);
      this.sources.set(key(input.sourceUri), file);
    }
    return client;
  }
  private queue<T>(work: () => Promise<T>): Promise<T> {
    const next = this.serial.catch(() => undefined).then(work);
    this.serial = next;
    return next;
  }
  private prepare(document: vscode.TextDocument, at: vscode.Position, token: vscode.CancellationToken): Promise<Prepared | undefined> {
    return (async () => {
      const client = await this.sync(document, token);
      const file = this.sources.get(document.uri.toString());
      if (!client || !file || file.projection.version !== document.version) return undefined;
      const generated = file.generatedPosition(at);
      return generated ? { client, file, position: generated, version: document.version, document } : undefined;
    })();
  }
  private params(prepared: Prepared): protocol.TextDocumentPositionParams { return { textDocument: { uri: prepared.file.javaUri }, position: prepared.position }; }
  private current(prepared: Prepared, token?: vscode.CancellationToken): boolean {
    return !this.disposed && !token?.isCancellationRequested && prepared.document.version === prepared.version
      && this.sources.get(prepared.document.uri.toString())?.projection.javaText === prepared.file.projection.javaText
      && [...(this.projectSources.get(prepared.file.projectUri ?? '') ?? [])].every(uri => {
        const open = vscode.workspace.textDocuments.find(document => document.uri.toString() === uri);
        return !open || open.version === this.sources.get(uri)?.projection.version;
      });
  }
  private locations(locations: protocol.Location[]): vscode.Location[] {
    return locations.flatMap(location => {
      const file = this.files.get(key(location.uri));
      const range = file ? file.sourceRange(location.range) : location.range;
      return range ? [new vscode.Location(vscode.Uri.parse(file?.projection.sourceUri ?? location.uri), new vscode.Range(range.start.line, range.start.character, range.end.line, range.end.character))] : [];
    });
  }
  private documentation(markdown: string): string {
    return markdown.replace(/\]\((file:[^)\s]+)\)/g, (match, target: string) => {
      const uri = vscode.Uri.parse(target);
      const file = this.files.get(uri.with({ fragment: '' }).toString());
      if (!file) return match;
      const line = /^(?:L)?(\d+)/.exec(uri.fragment);
      const at = { line: Math.max(0, Number(line?.[1] ?? 1) - 1), character: 0 };
      const range = file.sourceRange({ start: at, end: at }, true);
      return `](${vscode.Uri.parse(file.projection.sourceUri).with({ fragment: range ? String(range.start.line + 1) : '' }).toString()})`;
    });
  }
  private mappedCompletion(raw: protocol.CompletionItem, prepared: Prepared): vscode.CompletionItem | undefined {
    const mapped = { ...raw, command: undefined };
    if (raw.textEdit) {
      if ('range' in raw.textEdit) { const edit = prepared.file.edit(raw.textEdit); if (!edit) return undefined; mapped.textEdit = edit; }
      else {
        const insert = prepared.file.sourceRange(raw.textEdit.insert), replace = prepared.file.sourceRange(raw.textEdit.replace);
        if (!insert || !replace) return undefined;
        mapped.textEdit = { newText: raw.textEdit.newText, insert, replace };
      }
    }
    if (raw.additionalTextEdits) {
      const edits = raw.additionalTextEdits.map(edit => prepared.file.edit(edit));
      if (edits.some(edit => !edit)) return undefined;
      mapped.additionalTextEdits = edits as protocol.TextEdit[];
    }
    const item = prepared.client.protocol2CodeConverter.asCompletionItem(mapped);
    this.completions.set(item, { prepared, raw });
    return item;
  }
  private async complete(document: vscode.TextDocument, at: vscode.Position, token: vscode.CancellationToken, context: vscode.CompletionContext): Promise<vscode.CompletionList | undefined> {
    const prepared = await this.prepare(document, at, token); if (!prepared) return undefined;
    const result = await this.invoke<protocol.CompletionList>('completion', { ...this.params(prepared), context: { triggerKind: context.triggerKind, triggerCharacter: context.triggerCharacter } }, token);
    if (!this.current(prepared, token)) return undefined;
    const items: vscode.CompletionItem[] = [];
    for (const original of result.items) {
      const raw = { ...original };
      const defaults = result.itemDefaults;
      if (!raw.textEdit && defaults?.editRange) {
        const newText = raw.textEditText ?? raw.insertText ?? raw.label;
        raw.textEdit = 'start' in defaults.editRange ? { range: defaults.editRange, newText } : { ...defaults.editRange, newText };
      }
      raw.insertTextFormat ??= defaults?.insertTextFormat;
      raw.data ??= defaults?.data;
      const mapped = this.mappedCompletion(raw, prepared);
      if (mapped) items.push(mapped);
    }
    return new vscode.CompletionList(items, result.isIncomplete);
  }
  private async resolve(item: vscode.CompletionItem, token: vscode.CancellationToken): Promise<vscode.CompletionItem | undefined> {
    const entry = this.completions.get(item);
    if (!entry || !this.current(entry.prepared, token)) return item;
    const raw = await this.invoke<protocol.CompletionItem>('resolveCompletion', entry.raw, token);
    return this.current(entry.prepared, token) ? this.mappedCompletion(raw, entry.prepared) ?? item : item;
  }
  private async rename(document: vscode.TextDocument, at: vscode.Position, newName: string, token: vscode.CancellationToken): Promise<vscode.WorkspaceEdit | undefined> {
    const prepared = await this.prepare(document, at, token); if (!prepared) return undefined;
    const ordinaryVersions = new Map(vscode.workspace.textDocuments.filter(value => value.languageId === 'java')
      .map(value => [value.uri.toString(), { version: value.version, text: value.getText() }]));
    const raw = await this.invoke<protocol.WorkspaceEdit>('rename', { ...this.params(prepared), newName }, token);
    if (!this.current(prepared, token)) throw new Error('The JX document changed during rename; try again');
    const changes: Record<string, protocol.TextEdit[]> = {};
    const oldName = document.getText(document.getWordRangeAtPosition(at));
    const add = async (uri: string, edits: protocol.TextEdit[], nativeVersion?: number | null) => {
      const file = this.files.get(key(uri));
      const chunks = file ? edits.map(edit => file.renameEdits(edit, oldName, newName)) : edits.map(edit => [edit]);
      if (chunks.some(chunk => !chunk)) throw new Error('This Java rename changes generated markup; rename the component or prop in JX source instead');
      const mapped = chunks.flatMap(chunk => chunk ?? []);
      const target = file?.projection.sourceUri ?? uri;
      if (file) {
        const open = await vscode.workspace.openTextDocument(vscode.Uri.parse(target));
        if (open.getText() !== file.projection.sourceText || file.projection.version !== null && open.version !== file.projection.version)
          throw new Error('A JX document changed during rename; try again');
      } else {
        const captured = ordinaryVersions.get(key(target));
        const open = vscode.workspace.textDocuments.find(value => value.uri.toString() === key(target));
        if (!captured || !open) throw new Error('Rename affects a closed Java file. Open it and retry.');
        if (open.version !== captured.version || open.getText() !== captured.text || nativeVersion != null && nativeVersion !== open.version)
          throw new Error('A Java document changed during rename; try again');
      }
      changes[target] = [...(changes[target] ?? []), ...mapped];
    };
    for (const [uri, edits] of Object.entries(raw.changes ?? {})) await add(uri, edits);
    for (const change of raw.documentChanges ?? []) {
      if (!('textDocument' in change)) throw new Error('Java file moves are not supported for JX source');
      if (change.edits.some(edit => !('newText' in edit))) throw new Error('Snippet edits are not supported during JX rename');
      await add(change.textDocument.uri, change.edits.filter((edit): edit is protocol.TextEdit => 'newText' in edit), change.textDocument.version);
    }
    return prepared.client.protocol2CodeConverter.asWorkspaceEdit({ changes }, token);
  }
  private schedule(): void {
    if (this.timer) clearTimeout(this.timer);
    if (this.disposed || !vscode.workspace.isTrusted) return;
    this.timer = setTimeout(() => { void this.queue(() => this.validate()).catch(error => this.log(error)); }, 400);
  }
  private async validate(): Promise<void> {
    const seen = new Set<string>();
    for (const document of vscode.workspace.textDocuments.filter(value => value.languageId === 'jx' && value.uri.scheme === 'file')) {
      let client: LanguageClient | undefined;
      try { client = await this.sync(document); }
      catch (error) { this.log(error); continue; }
      if (!client) continue;
      const source = this.sources.get(document.uri.toString());
      if (!source || source.projection.version !== document.version) continue;
      const session = this.sessions.get(source.projectUri ?? '');
      if (!session || seen.has(session)) continue;
      seen.add(session);
      const results = await this.invoke<{ uri: string; diagnostics: protocol.Diagnostic[] }[]>('diagnostics', session);
      for (const result of results) {
        const file = this.files.get(key(result.uri)); if (!file) continue;
        const open = vscode.workspace.textDocuments.find(value => value.uri.toString() === key(file.projection.sourceUri));
        if (!open || open.version !== file.projection.version) continue;
        const mapped = result.diagnostics.flatMap(diagnostic => {
          const range = file.sourceRange(diagnostic.range, true); return range ? [{ ...diagnostic, range }] : [];
        });
        this.diagnostics.set(open.uri, await client.protocol2CodeConverter.asDiagnostics(mapped));
      }
    }
  }
  private async releaseIfUnused(): Promise<void> {
    await this.queue(async () => {
      for (const [project, session] of this.sessions) {
        const owned = this.projectSources.get(project) ?? new Set<string>();
        if (vscode.workspace.textDocuments.some(document => document.languageId === 'jx' && owned.has(document.uri.toString()))) continue;
        await this.invoke('close', session);
        this.sessions.delete(project); this.projectSources.delete(project);
        for (const uri of owned) {
          const file = this.sources.get(uri);
          if (file) this.files.delete(key(file.javaUri));
          this.sources.delete(uri);
        }
      }
    });
  }
  private async javaProjectSource(document: vscode.TextDocument): Promise<vscode.Uri | undefined> {
    if (!vscode.workspace.isTrusted || document.languageId !== 'java' || document.uri.scheme !== 'file') return undefined;
    const workspace = vscode.workspace.getWorkspaceFolder(document.uri);
    if (!workspace) return undefined;
    for (let directory = path.dirname(document.uri.fsPath); ; directory = path.dirname(directory)) {
      const relative = path.relative(workspace.uri.fsPath, directory);
      if (relative.startsWith('..') || path.isAbsolute(relative)) return undefined;
      try {
        await access(path.join(directory, 'pom.xml'));
        const files = await vscode.workspace.findFiles(new vscode.RelativePattern(directory, 'src/**/*.jx'), '**/target/**', 1);
        return files[0];
      } catch (error) {
        if ((error as NodeJS.ErrnoException).code !== 'ENOENT') throw error;
      }
      if (directory === workspace.uri.fsPath || path.dirname(directory) === directory) return undefined;
    }
  }
  private async updateNavigationContext(): Promise<void> {
    const editor = vscode.window.activeTextEditor;
    try {
      const enabled = !!editor && !!await this.javaProjectSource(editor.document);
      if (!this.disposed && vscode.window.activeTextEditor === editor) await vscode.commands.executeCommand('setContext', 'jx.javaNavigation', enabled);
    } catch (error) { this.log(error); }
  }
  /** F12 for Java files in a JX Maven module, mapping native JDT locations back to authored source. */
  async goToDefinition(): Promise<void> {
    const editor = vscode.window.activeTextEditor;
    if (!editor || !vscode.workspace.isTrusted) return;
    const at = editor.selection.active, version = editor.document.version;
    let locations: vscode.Location[];
    if (editor.document.languageId === 'jx') {
      const definitions = await vscode.commands.executeCommand<(vscode.Location | vscode.LocationLink)[]>('vscode.executeDefinitionProvider', editor.document.uri, at);
      locations = (definitions ?? []).map(value => 'targetUri' in value ? new vscode.Location(value.targetUri, value.targetSelectionRange ?? value.targetRange) : value);
    } else {
      const source = await this.javaProjectSource(editor.document);
      if (!source) throw new Error('Open a Java file in a Maven module containing JX source');
      locations = await this.queue(async () => {
        const projected = await vscode.workspace.openTextDocument(source);
        await this.sync(projected);
        const result = await this.invoke<protocol.Location[]>('definition', { textDocument: { uri: editor.document.uri.toString() }, position: at });
        return this.locations(result);
      });
    }
    if (editor.document.version !== version || vscode.window.activeTextEditor !== editor) return;
    if (locations.length === 1) await vscode.window.showTextDocument(locations[0].uri, { selection: locations[0].range });
    else await vscode.commands.executeCommand('editor.action.goToLocations', editor.document.uri, at, locations, 'peek', 'No definition found');
  }
  private async shutdownCopies(): Promise<void> {
    await this.serial.catch(() => undefined);
    const failures: unknown[] = [];
    for (const session of this.sessions.values()) try { await this.invoke('close', session); } catch (error) { failures.push(error); }
    this.sessions.clear(); this.projectSources.clear(); this.files.clear(); this.sources.clear();
    if (failures.length) throw new AggregateError(failures, 'Could not release every native Java working copy');
  }
  async shutdown(): Promise<void> { this.dispose(); await this.shutdownCopies(); }
  dispose(): void { this.disposed = true; if (this.timer) clearTimeout(this.timer); for (const resource of this.resources.splice(0)) resource.dispose(); void vscode.commands.executeCommand('setContext', 'jx.javaNavigation', false); }
  private log(error: unknown): void { this.output.appendLine(`Native Java: ${error instanceof Error ? error.message : String(error)}`); }
  private async safe<T>(work: () => Promise<T>): Promise<T | undefined> { try { return await this.queue(work); } catch (error) { this.log(error); return undefined; } }
}

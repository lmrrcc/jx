import * as vscode from 'vscode';
import * as path from 'node:path';
import { readFile, realpath, stat } from 'node:fs/promises';

const DEV_GOAL = 'dev.jx:jx-maven-plugin:0.1.0-SNAPSHOT:dev';

interface MavenModule {
  folder: vscode.WorkspaceFolder;
  directory: string;
  identity: string;
  wrapper: string;
  wrapperRoot: string;
  resource: vscode.Uri;
}

interface DevelopmentSession {
  module: MavenModule;
  execution?: vscode.TaskExecution;
  stopping: boolean;
  starting: Promise<void>;
}

async function isFile(filename: string): Promise<boolean> {
  try { return (await stat(filename)).isFile(); }
  catch (failure) {
    if ((failure as NodeJS.ErrnoException).code === 'ENOENT') return false;
    throw failure;
  }
}

async function ancestorWith(start: string, filename: string): Promise<string | undefined> {
  for (let current = start; ; current = path.dirname(current)) {
    if (await isFile(path.join(current, filename))) return current;
    if (path.dirname(current) === current) return undefined;
  }
}

/** Explicit Maven task lifecycle. No wrapper or project command runs during discovery or activation. */
export class DevelopmentTasks implements vscode.Disposable {
  private readonly sessions = new Map<string, DevelopmentSession>();
  private readonly ended: vscode.Disposable;
  private readonly editorChanged: vscode.Disposable;
  private lastResource: vscode.Uri | undefined = vscode.window.activeTextEditor?.document.uri;
  private closed = false;
  private stopEpoch = 0;

  constructor(private readonly log: vscode.LogOutputChannel) {
    this.editorChanged = vscode.window.onDidChangeActiveTextEditor(editor => {
      if (editor?.document.uri.scheme === 'file') this.lastResource = editor.document.uri;
    });
    this.ended = vscode.tasks.onDidEndTask(event => {
      for (const [key, session] of this.sessions) {
        if (session.execution === event.execution || !session.execution && event.execution.task.definition.project === key)
          this.sessions.delete(key);
      }
    });
  }

  async build(): Promise<void> {
    this.requireTrust();
    const module = await this.module(false);
    if (!module) return;
    if (!await vscode.workspace.saveAll(false)) throw new Error('Save was cancelled. The JX build was not started.');
    this.requireTrust();
    await vscode.tasks.executeTask(await this.task(module, false));
  }

  async start(): Promise<void> {
    this.requireTrust();
    const requestedAt = this.stopEpoch;
    const module = await this.module(true);
    if (!module || requestedAt !== this.stopEpoch) return;
    const active = this.sessions.get(module.identity);
    const existing = this.running(module.identity);
    if (active || existing) {
      this.log.info('JX development is already running or starting for ' + module.directory + '.');
      return;
    }
    // Reserve before saving or creating the task: simultaneous commands share this module slot.
    const session: DevelopmentSession = { module, stopping: false, starting: Promise.resolve() };
    this.sessions.set(module.identity, session);
    session.starting = this.launch(session);
    try { await session.starting; }
    catch (failure) {
      if (this.sessions.get(module.identity) === session) this.sessions.delete(module.identity);
      throw failure;
    }
  }

  private async launch(session: DevelopmentSession): Promise<void> {
    if (!await vscode.workspace.saveAll(false)) throw new Error('Save was cancelled. The JX development server was not started.');
    if (session.stopping || this.closed) { this.sessions.delete(session.module.identity); return; }
    this.requireTrust();
    const task = await this.task(session.module, true);
    if (session.stopping || this.closed) { this.sessions.delete(session.module.identity); return; }
    session.execution = await vscode.tasks.executeTask(task);
    this.log.info('Started JX development for ' + session.module.directory + '.');
    if (session.stopping || this.closed) await this.terminate(session.execution);
  }

  async stop(): Promise<void> {
    this.stopEpoch++;
    const executions = vscode.tasks.taskExecutions.filter(execution => this.isDevelopment(execution));
    const candidates = new Map<string, { directory: string; session?: DevelopmentSession; execution?: vscode.TaskExecution }>();
    for (const [key, session] of this.sessions) candidates.set(key, { directory: session.module.directory, session, execution: session.execution });
    for (const execution of executions) {
      const key = String(execution.task.definition.project);
      if (!candidates.has(key)) candidates.set(key, { directory: key, execution });
    }
    if (candidates.size === 0) { this.log.info('No JX development server is running.'); return; }
    let selected: string | undefined;
    const active = vscode.window.activeTextEditor?.document.uri ?? this.lastResource;
    if (active?.scheme === 'file') {
      const directory = await ancestorWith(path.dirname(active.fsPath), 'pom.xml');
      if (directory) {
        const key = await realpath(directory);
        if (candidates.has(key)) selected = key;
      }
    }
    if (!selected && candidates.size === 1) selected = candidates.keys().next().value;
    if (!selected) {
      const choice = await vscode.window.showQuickPick([...candidates].map(([key, candidate]) => ({
        label: path.basename(candidate.directory), description: candidate.directory, key
      })), { placeHolder: 'Stop a JX development server' });
      selected = choice?.key;
    }
    if (!selected) return;
    const candidate = candidates.get(selected)!;
    if (candidate.session) {
      candidate.session.stopping = true;
      await candidate.session.starting;
    }
    const execution = candidate.session?.execution ?? candidate.execution;
    if (execution && vscode.tasks.taskExecutions.includes(execution)) await this.terminate(execution);
    this.sessions.delete(selected);
    this.log.info('Stopped JX development for ' + candidate.directory + '.');
  }

  async shutdown(): Promise<void> {
    this.closed = true;
    const sessions = [...this.sessions.values()];
    sessions.forEach(session => { session.stopping = true; });
    await Promise.allSettled(sessions.map(session => session.starting));
    const executions = vscode.tasks.taskExecutions.filter(execution => this.isDevelopment(execution));
    await Promise.allSettled(executions.map(execution => this.terminate(execution)));
    this.sessions.clear();
  }

  dispose(): void {
    this.closed = true;
    for (const session of this.sessions.values()) {
      session.stopping = true;
      session.execution?.terminate();
    }
    this.ended.dispose();
    this.editorChanged.dispose();
  }

  private async terminate(execution: vscode.TaskExecution): Promise<void> {
    if (!vscode.tasks.taskExecutions.includes(execution)) return;
    await new Promise<void>((resolve, reject) => {
      const timeout = setTimeout(() => {
        listener.dispose();
        reject(new Error('The JX task did not stop within 15 seconds. Use VS Code Tasks: Terminate Task and inspect its terminal.'));
      }, 15_000);
      const listener = vscode.tasks.onDidEndTask(event => {
        if (event.execution !== execution) return;
        clearTimeout(timeout); listener.dispose(); resolve();
      });
      try { execution.terminate(); }
      catch (failure) { clearTimeout(timeout); listener.dispose(); reject(failure); }
    });
  }

  private isDevelopment(execution: vscode.TaskExecution): boolean {
    return execution.task.definition.type === 'jx' && execution.task.definition.mode === 'dev'
      && typeof execution.task.definition.project === 'string';
  }

  private running(identity: string): vscode.TaskExecution | undefined {
    return vscode.tasks.taskExecutions.find(execution => this.isDevelopment(execution) && execution.task.definition.project === identity);
  }

  private requireTrust(): void {
    if (this.closed) throw new Error('JX task management has stopped.');
    if (!vscode.workspace.isTrusted) throw new Error('Trust this workspace before running its Maven Wrapper.');
  }

  private async module(development: boolean): Promise<MavenModule | undefined> {
    const active = vscode.window.activeTextEditor?.document.uri ?? this.lastResource;
    let folder = active?.scheme === 'file' ? vscode.workspace.getWorkspaceFolder(active) : undefined;
    let start = folder && active ? path.dirname(active.fsPath) : undefined;
    if (!folder) {
      const folders = vscode.workspace.workspaceFolders ?? [];
      folder = folders.length === 1 ? folders[0] : await vscode.window.showWorkspaceFolderPick();
      start = folder?.uri.fsPath;
    }
    if (!folder || !start) return undefined;
    if (folder.uri.scheme !== 'file') throw new Error('JX Maven commands require a filesystem workspace.');
    const directory = await ancestorWith(start, 'pom.xml');
    if (!directory) throw new Error('No pom.xml was found for the active file. Open a .jx file in its Maven application module.');
    if (development) {
      const pom = await readFile(path.join(directory, 'pom.xml'), 'utf8');
      if (/<packaging>\s*pom\s*<\/packaging>/.test(pom.replace(/<!--[\s\S]*?-->/g, '')))
        throw new Error('Open a .jx file in the application module before starting JX development; the selected POM is an aggregator.');
    }
    const wrapperName = process.platform === 'win32' ? 'mvnw.cmd' : 'mvnw';
    const wrapperRoot = await ancestorWith(directory, wrapperName);
    if (!wrapperRoot) throw new Error('No ' + wrapperName + ' was found in the Maven module or its ancestors.');
    return { folder, directory, identity: await realpath(directory), wrapper: path.join(wrapperRoot, wrapperName),
      wrapperRoot, resource: active ?? vscode.Uri.file(directory) };
  }

  private async task(module: MavenModule, development: boolean): Promise<vscode.Task> {
    const configuration = vscode.workspace.getConfiguration('jx', module.resource);
    const configured = development ? configuration.get<unknown>('devArguments', [DEV_GOAL]) : ['package'];
    if (!Array.isArray(configured) || configured.length === 0 || configured.some(value => typeof value !== 'string' || value.length === 0 || value.includes('\0')))
      throw new Error('jx.devArguments must be a nonempty list of Maven argument strings.');
    const arguments_: string[] = [...configured];
    if (arguments_.some(argument => argument === '-f' || argument === '--file' || argument.startsWith('--file=')
      || /^-f[^-]/.test(argument) && !['-fae', '-ff', '-fn'].includes(argument)))
      throw new Error('Do not set -f or --file in jx.devArguments; JX selects the Maven module from the active file.');
    const args = ['-B', '-ntp', '-f', path.join(module.directory, 'pom.xml')];
    if (!arguments_.some(argument => argument.startsWith('-Dmaven.repo.local=') || argument.startsWith('maven.repo.local='))) {
      const repositoryValue = configuration.get<unknown>('mavenRepository', '');
      if (typeof repositoryValue !== 'string' || repositoryValue.includes('\0'))
        throw new Error('jx.mavenRepository must be a filesystem path string.');
      const configuredRepository = repositoryValue.trim();
      if (configuredRepository) {
        const repository = path.isAbsolute(configuredRepository) ? configuredRepository : path.resolve(module.folder.uri.fsPath, configuredRepository);
        args.push('-Dmaven.repo.local=' + repository);
      } else {
        const repository = path.join(module.wrapperRoot, '.cache', 'maven-repository');
        try { if ((await stat(repository)).isDirectory()) args.push('-Dmaven.repo.local=' + repository); }
        catch (failure) { if ((failure as NodeJS.ErrnoException).code !== 'ENOENT') throw failure; }
      }
    }
    args.push(...arguments_);
    const task = new vscode.Task({ type: 'jx', mode: development ? 'dev' : 'build', project: module.identity }, module.folder,
      (development ? 'JX Dev: ' : 'JX Build: ') + path.basename(module.directory), 'JX',
      new vscode.ProcessExecution(module.wrapper, args, { cwd: module.wrapperRoot }), []);
    task.detail = module.directory;
    task.presentationOptions = { reveal: vscode.TaskRevealKind.Always, panel: vscode.TaskPanelKind.Dedicated, clear: false };
    return task;
  }
}


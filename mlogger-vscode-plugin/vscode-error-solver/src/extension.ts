import * as vscode from 'vscode';
import * as fs from 'fs/promises';
import * as fsSync from 'fs';
import axios from 'axios';
import chokidar, { FSWatcher } from 'chokidar';

let watcher: FSWatcher | null = null;
let lastSize = 0;
const processedLines = new Set<string>();
let debounceTimeout: NodeJS.Timeout | null = null;

export function activate(context: vscode.ExtensionContext) {
    context.subscriptions.push(
        vscode.commands.registerCommand('errorsolver.openPanel', () => {
            ErrorSolverPanel.createOrShow(context.extensionUri);
        })
    );

    context.subscriptions.push(
        vscode.commands.registerCommand('errorsolver.toggleWatch', async () => {
            if (watcher) {
                await watcher.close();
                watcher = null;
                processedLines.clear();
                lastSize = 0;
                vscode.window.showInformationMessage("⏹️ Watcher fermato.");
                return;
            }

            const uri = await vscode.window.showOpenDialog({
                canSelectFiles: true,
                openLabel: "Seleziona file di log da monitorare"
            });
            if (!uri || uri.length === 0) return;

            const filePath = uri[0].fsPath;
            vscode.window.showInformationMessage(`📜 Monitoraggio file: ${filePath}`);
            ErrorSolverPanel.createOrShow(context.extensionUri);

            lastSize = 0;
            processedLines.clear();

            watcher = chokidar.watch(filePath, { persistent: true });

            const scheduleProcess = () => {
                if (debounceTimeout) clearTimeout(debounceTimeout);
                debounceTimeout = setTimeout(() => processNewLines(filePath), 100);
            };

            watcher.on('change', scheduleProcess);

            watcher.on('error', (error) => {
                vscode.window.showErrorMessage(error instanceof Error ? `Errore watcher: ${error.message}` : "Errore watcher sconosciuto");
            });
        })
    );
}

async function processNewLines(filePath: string) {
    try {
        const stats = await fs.stat(filePath);

        if (stats.size < lastSize) {
            lastSize = 0;
            processedLines.clear();
        }

        if (stats.size > lastSize) {
            const fd = await fs.open(filePath, 'r');
            const buffer = Buffer.alloc(stats.size - lastSize);
            await fd.read(buffer, 0, buffer.length, lastSize);
            await fd.close();

            const newText = buffer.toString('utf8');
            lastSize = stats.size;

            const errorLines = newText.split(/\r?\n/)
                .filter(l => /error|exception|failed/i.test(l))
                .filter(l => !processedLines.has(l));

            for (const line of errorLines) {
                processedLines.add(line);
                const timestamp = new Date().toLocaleTimeString();
                ErrorSolverPanel.currentPanel?.appendLog(`⚡ [${timestamp}] Log: ${line}`);
                const response = await queryAI(line);
                ErrorSolverPanel.currentPanel?.appendLog(`📘 [${timestamp}] AI: ${response}\n`);
            }
        }
    } catch (err: any) {
        vscode.window.showErrorMessage("Errore leggendo il file: " + err.message);
    }
}

async function queryAI(log: string): Promise<string> {
    try {
        const res = await axios.post("http://localhost:1234/v1/chat/completions", {
            model: "lmstudio-community/llama-3.2-3b-instruct",
            temperature: 0.3,
            max_tokens: 150,
            messages: [
                { role: "system", content: "Sei un assistente tecnico per errori di codice." },
                { role: "user", content: log }
            ]
        }, { headers: { "Content-Type": "application/json" } });

        return res.data.choices?.[0]?.message?.content?.trim() ?? "Nessuna risposta.";
    } catch (e: any) {
        return "Errore chiamando l’AI locale: " + e.message;
    }
}

// 🔹 Pannello WebView
class ErrorSolverPanel {
    public static currentPanel: ErrorSolverPanel | undefined;
    private readonly _panel: vscode.WebviewPanel;
    private _disposables: vscode.Disposable[] = [];
    private _isReady = false;
    private _buffer: string[] = [];

    public static createOrShow(extensionUri: vscode.Uri) {
        const column = vscode.window.activeTextEditor?.viewColumn;
        if (ErrorSolverPanel.currentPanel) {
            ErrorSolverPanel.currentPanel._panel.reveal(column);
            return;
        }
        const panel = vscode.window.createWebviewPanel(
            'errorSolver',
            'Error Solver',
            column || vscode.ViewColumn.One,
            { enableScripts: true }
        );
        ErrorSolverPanel.currentPanel = new ErrorSolverPanel(panel);
    }

    private constructor(panel: vscode.WebviewPanel) {
        this._panel = panel;
        this._panel.webview.html = this._getHtml();

        this._panel.webview.onDidReceiveMessage(message => {
            if (message.command === 'ready') {
                this._isReady = true;
                this._buffer.forEach(text => this._panel.webview.postMessage({ command: 'log', text }));
                this._buffer = [];
            }
        });

        this._panel.onDidDispose(() => this.dispose(), null, this._disposables);
    }

    public dispose() {
        ErrorSolverPanel.currentPanel = undefined;
        this._panel.dispose();
        while (this._disposables.length) {
            const d = this._disposables.pop();
            if (d) d.dispose();
        }
    }

    private _getHtml(): string {
        return `
        <!DOCTYPE html>
        <html lang="it">
        <head>
            <meta charset="UTF-8">
            <title>Error Solver</title>
            <style>
                body { margin:0; font-family:sans-serif; display:flex; flex-direction:column; height:100vh; }
                #toolbar { padding:5px; background:#eee; display:flex; justify-content:flex-end; }
                #log { flex:1; padding:5px; font-family:monospace; white-space:pre-wrap; overflow-y:auto; border-top:1px solid #ccc; }
                button { padding:5px 10px; }
            </style>
        </head>
        <body>
            <div id="toolbar">
                <button id="clearButton">Clear</button>
            </div>
            <div id="log"></div>
            <script>
                const vscode = acquireVsCodeApi();
                const logDiv = document.getElementById('log');

                window.addLog = text => {
                    logDiv.textContent += text + '\\n';
                    logDiv.scrollTop = logDiv.scrollHeight;
                };

                document.getElementById('clearButton').addEventListener('click', () => {
                    logDiv.textContent = '';
                });

                vscode.postMessage({ command: 'ready' });

                window.addEventListener('message', event => {
                    const message = event.data;
                    if(message.command === 'log'){
                        window.addLog(message.text);
                    }
                });
            </script>
        </body>
        </html>`;
    }

    public appendLog(text: string) {
        if (this._isReady) {
            this._panel.webview.postMessage({ command: 'log', text });
        } else {
            this._buffer.push(text);
        }
    }
}

export function deactivate() {
    if (watcher) {
        watcher.close();
        watcher = null;
    }
    processedLines.clear();
    lastSize = 0;
}

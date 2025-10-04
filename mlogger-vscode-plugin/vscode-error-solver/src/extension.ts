import * as vscode from 'vscode';
import axios from 'axios';

let activeTerminal: vscode.Terminal | null = null;
let writeEmitter: vscode.EventEmitter<string> | null = null;
const processedLines = new Set<string>();
let outputBuffer = '';

export function activate(context: vscode.ExtensionContext) {

    // 🔹 Apri il pannello manualmente
    context.subscriptions.push(
        vscode.commands.registerCommand('errorsolver.openPanel', () => {
            ErrorSolverPanel.createOrShow(context.extensionUri);
        })
    );

    // 🔹 Avvia terminale con pseudoterminal per intercettare output
    context.subscriptions.push(
        vscode.commands.registerCommand('errorsolver.startTerminalWatch', async () => {

            if (activeTerminal) {
                vscode.window.showInformationMessage("⏹️ Terminal watcher già attivo.");
                return;
            }

            ErrorSolverPanel.createOrShow(context.extensionUri);

            // 🔹 Crea un pseudoterminal personalizzato che intercetta tutto
            writeEmitter = new vscode.EventEmitter<string>();

            const pty: vscode.Pseudoterminal = {
                onDidWrite: writeEmitter.event,

                open: () => {
                    writeEmitter?.fire('\x1b[1;32mError Solver Terminal attivo\x1b[0m\r\n');
                    writeEmitter?.fire('Digita i comandi. Gli errori saranno analizzati automaticamente.\r\n\r\n$ ');
                },

                close: () => { },

                handleInput: async (data: string) => {
                    // Gestisci backspace
                    if (data === '\x7f') {
                        if (outputBuffer.length > 0) {
                            outputBuffer = outputBuffer.slice(0, -1);
                            writeEmitter?.fire('\x1b[2K\r$ ' + outputBuffer);
                        }
                        return;
                    }

                    // Gestisci Ctrl+C
                    if (data === '\x03') {
                        writeEmitter?.fire('^C\r\n$ ');
                        outputBuffer = '';
                        return;
                    }

                    // Echo del carattere
                    if (data !== '\r') {
                        writeEmitter?.fire(data);
                        outputBuffer += data;
                        return;
                    }

                    // Invio premuto - esegui comando
                    if (data === '\r' && outputBuffer.trim()) {
                        writeEmitter?.fire('\r\n');
                        const command = outputBuffer.trim();
                        outputBuffer = '';

                        // Esegui il comando reale
                        await executeCommand(command);

                        writeEmitter?.fire('$ ');
                    } else if (data === '\r') {
                        writeEmitter?.fire('\r\n$ ');
                    }
                }
            };

            activeTerminal = vscode.window.createTerminal({
                name: 'Error Solver Terminal',
                pty: pty
            });

            activeTerminal.show();
        })
    );

    // 🔹 Esegui comando e cattura output
    async function executeCommand(command: string) {
        return new Promise<void>((resolve) => {
            const { spawn } = require('child_process');

            // Ottieni la shell dell'utente
            const userShell = vscode.env.shell || process.env.SHELL || process.env.COMSPEC || '/bin/bash';

            // Determina i parametri della shell - NON interactive per evitare errori PTY
            const isWindows = process.platform === 'win32';
            let shellArgs: string[];

            if (isWindows) {
                shellArgs = ['/c', command];
            } else {
                // Per bash/zsh: source il file rc prima del comando
                const rcFile = userShell.includes('zsh') ? '~/.zshrc' :
                    userShell.includes('bash') ? '~/.bashrc' : '';
                const fullCommand = rcFile ? `source ${rcFile} 2>/dev/null; ${command}` : command;
                shellArgs = ['-c', fullCommand];
            }

            writeEmitter?.fire(`\x1b[90m> ${command}\x1b[0m\r\n`);

            const child = spawn(userShell, shellArgs, {
                cwd: vscode.workspace.workspaceFolders?.[0]?.uri.fsPath || process.cwd(),
                env: { ...process.env }
            });

            // Cattura stdout
            child.stdout.on('data', (data: Buffer) => {
                const text = data.toString();
                const lines = text.split('\n');
                for (const line of lines) {
                    if (line.trim()) {
                        writeEmitter?.fire(line + '\r\n');
                        processLine(line);
                    }
                }
            });

            // Cattura stderr
            child.stderr.on('data', (data: Buffer) => {
                const text = data.toString();
                const lines = text.split('\n');
                for (const line of lines) {
                    if (line.trim()) {
                        writeEmitter?.fire(`\x1b[31m${line}\x1b[0m\r\n`);
                        processLine(line);
                    }
                }
            });

            // Gestisci errori
            child.on('error', (error: Error) => {
                writeEmitter?.fire(`\x1b[31mErrore esecuzione: ${error.message}\x1b[0m\r\n`);
                resolve();
            });

            // Gestisci chiusura
            child.on('close', (code: number) => {
                if (code !== 0 && code !== null) {
                    writeEmitter?.fire(`\x1b[33mProcesso terminato con codice: ${code}\x1b[0m\r\n`);
                }
                resolve();
            });
        });
    }

    // 🔹 Processa una linea per rilevare errori
    async function processLine(line: string) {
        if (/error|exception|failed|traceback|fatal/i.test(line)) {
            if (!processedLines.has(line)) {
                processedLines.add(line);
                const timestamp = new Date().toLocaleTimeString();
                ErrorSolverPanel.currentPanel?.appendLog(`⚡ [${timestamp}] Error: ${line}`);

                // Chiamata AI asincrona in background
                queryAI(line).then(response => {
                    ErrorSolverPanel.currentPanel?.appendLog(`📘 [${timestamp}] AI: ${response}\n`);
                });
            }
        }
    }

    // 🔹 Comando per fermare il watcher
    context.subscriptions.push(
        vscode.commands.registerCommand('errorsolver.stopTerminalWatch', () => {
            if (activeTerminal) {
                activeTerminal.dispose();
                activeTerminal = null;
                writeEmitter = null;
                processedLines.clear();
                outputBuffer = '';
                vscode.window.showInformationMessage("⏹️ Terminal watcher fermato.");
            }
        })
    );
}

// 🔹 Funzione per chiamare AI locale
async function queryAI(log: string): Promise<string> {
    try {
        const res = await axios.post("http://localhost:1234/v1/chat/completions", {
            model: "lmstudio-community/llama-3.2-3b-instruct",
            temperature: 0.3,
            max_tokens: 150,
            messages: [
                { role: "system", content: "Sei un assistente tecnico per errori di codice. Rispondi in modo conciso." },
                { role: "user", content: `Analizza questo errore e suggerisci una soluzione: ${log}` }
            ]
        }, { headers: { "Content-Type": "application/json" } });

        return res.data.choices?.[0]?.message?.content?.trim() ?? "Nessuna risposta.";
    } catch (e: any) {
        return "⚠️ Errore AI: " + e.message;
    }
}

// 🔹 WebView Panel
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
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Error Solver</title>
            <style>
                * {
                    margin: 0;
                    padding: 0;
                    box-sizing: border-box;
                }
                body { 
                    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; 
                    display: flex; 
                    flex-direction: column; 
                    height: 100vh;
                    background: #1e1e1e;
                    color: #d4d4d4;
                    overflow: hidden;
                }
                #toolbar { 
                    padding: 12px 16px; 
                    background: #252526; 
                    display: flex; 
                    justify-content: space-between;
                    align-items: center;
                    border-bottom: 1px solid #3e3e42;
                    flex-shrink: 0;
                }
                .title {
                    font-weight: 600;
                    font-size: 14px;
                    display: flex;
                    align-items: center;
                    gap: 8px;
                }
                .status {
                    display: inline-block;
                    width: 8px;
                    height: 8px;
                    border-radius: 50%;
                    background: #4ec9b0;
                    animation: pulse 2s infinite;
                }
                @keyframes pulse {
                    0%, 100% { opacity: 1; }
                    50% { opacity: 0.5; }
                }
                button { 
                    padding: 6px 14px; 
                    background: #0e639c;
                    color: white;
                    border: none;
                    border-radius: 4px;
                    cursor: pointer;
                    font-size: 12px;
                    font-weight: 500;
                    transition: background 0.2s;
                }
                button:hover {
                    background: #1177bb;
                }
                button:active {
                    background: #0d5a8f;
                }
                #log { 
                    flex: 1;
                    padding: 16px; 
                    font-family: 'Consolas', 'Courier New', monospace; 
                    font-size: 13px;
                    overflow-y: auto; 
                    line-height: 1.6;
                }
                #log:empty::before {
                    content: 'In attesa di errori da analizzare...';
                    color: #6e6e6e;
                    font-style: italic;
                }
                .log-entry {
                    margin-bottom: 16px;
                    padding: 12px;
                    border-left: 3px solid transparent;
                    border-radius: 4px;
                    background: #2d2d30;
                    animation: slideIn 0.3s ease-out;
                }
                @keyframes slideIn {
                    from {
                        opacity: 0;
                        transform: translateX(-10px);
                    }
                    to {
                        opacity: 1;
                        transform: translateX(0);
                    }
                }
                .error-line {
                    border-left-color: #f48771;
                    color: #f48771;
                    font-weight: 500;
                }
                .ai-response {
                    border-left-color: #4ec9b0;
                    color: #d4d4d4;
                    margin-top: 8px;
                    padding-left: 16px;
                }
                .ai-response::before {
                    content: '💡 ';
                    color: #4ec9b0;
                }
                .timestamp {
                    color: #858585;
                    font-size: 11px;
                    margin-right: 8px;
                }
                .terminal-name {
                    color: #569cd6;
                    font-size: 11px;
                    margin-right: 8px;
                }
                #log::-webkit-scrollbar {
                    width: 10px;
                }
                #log::-webkit-scrollbar-track {
                    background: #1e1e1e;
                }
                #log::-webkit-scrollbar-thumb {
                    background: #424242;
                    border-radius: 5px;
                }
                #log::-webkit-scrollbar-thumb:hover {
                    background: #4e4e4e;
                }
            </style>
        </head>
        <body>
            <div id="toolbar">
                <div class="title">
                    <span class="status"></span>
                    <span>🔍 Error Solver</span>
                </div>
                <button id="clearButton">Clear Log</button>
            </div>
            <div id="log"></div>
            <script>
                const vscode = acquireVsCodeApi();
                const logDiv = document.getElementById('log');

                window.addLog = text => {
                    const entry = document.createElement('div');
                    entry.className = 'log-entry';
                    
                    if (text.includes('⚡')) {
                        entry.className += ' error-line';
                    } else if (text.includes('📘')) {
                        entry.className += ' ai-response';
                        text = text.replace('📘', '');
                    }
                    
                    entry.textContent = text;
                    logDiv.appendChild(entry);
                    logDiv.scrollTop = logDiv.scrollHeight;
                };

                document.getElementById('clearButton').addEventListener('click', () => {
                    logDiv.innerHTML = '';
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
    if (activeTerminal) {
        activeTerminal.dispose();
        activeTerminal = null;
    }
    if (writeEmitter) {
        writeEmitter.dispose();
        writeEmitter = null;
    }
    processedLines.clear();
    outputBuffer = '';
}
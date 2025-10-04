"use strict";
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.activate = activate;
exports.deactivate = deactivate;
const vscode = __importStar(require("vscode"));
const fs = __importStar(require("fs"));
const axios_1 = __importDefault(require("axios"));
const chokidar_1 = __importDefault(require("chokidar"));
let watcher = null;
let lastSize = 0;
const processedLines = new Set();
let debounceTimeout = null;
function activate(context) {
    // 🔹 Comando per aprire il pannello manualmente
    context.subscriptions.push(vscode.commands.registerCommand('errorsolver.openPanel', () => {
        ErrorSolverPanel.createOrShow(context.extensionUri);
    }));
    // 🔹 Comando per avviare/fermare il watcher
    context.subscriptions.push(vscode.commands.registerCommand('errorsolver.toggleWatch', async () => {
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
        if (!uri || uri.length === 0)
            return;
        const filePath = uri[0].fsPath;
        vscode.window.showInformationMessage(`📜 Monitoraggio file: ${filePath}`);
        // 🔹 Apri automaticamente il pannello se non esiste
        ErrorSolverPanel.createOrShow(context.extensionUri);
        lastSize = 0;
        processedLines.clear();
        watcher = chokidar_1.default.watch(filePath, { persistent: true });
        // Debounce per evitare più trigger rapidi
        const scheduleProcess = () => {
            if (debounceTimeout)
                clearTimeout(debounceTimeout);
            debounceTimeout = setTimeout(() => processNewLines(filePath), 100);
        };
        watcher.on('change', scheduleProcess);
        watcher.on('error', (error) => {
            if (error instanceof Error) {
                vscode.window.showErrorMessage("Errore watcher: " + error.message);
            }
            else {
                vscode.window.showErrorMessage("Errore watcher sconosciuto");
            }
        });
    }));
}
// 🔹 Funzione che legge solo le nuove righe e chiama l’AI
async function processNewLines(filePath) {
    // fs.stat(filePath, async (err, stats) => {
    //     if (err) return;
    //     if (stats.size < lastSize) lastSize = 0; // reset se file ricreato
    //     if (stats.size > lastSize) {
    //         const fd = fs.openSync(filePath, 'r');
    //         const buffer = Buffer.alloc(stats.size - lastSize);
    //         fs.readSync(fd, buffer, 0, buffer.length, lastSize);
    //         fs.closeSync(fd);
    //         const newText = buffer.toString('utf8');
    //         lastSize = stats.size;
    //         // 🔹 Filtra solo righe d'errore e non duplicate
    //         const errorLines = newText.split(/\r?\n/)
    //             .filter(l => /error|exception|failed/i.test(l))
    //             .filter(l => !processedLines.has(l));
    //         for (const line of errorLines) {
    //             processedLines.add(line);
    //             ErrorSolverPanel.currentPanel?.appendLog("⚡ Log: " + line);
    //             const response = await queryAI(line);
    //             ErrorSolverPanel.currentPanel?.appendLog("📘 AI: " + response + "\n");
    //         }
    //     }
    // });
    fs.stat(filePath, async (err, stats) => {
        var _a, _b;
        if (err)
            return;
        // 🔹 Se il file è più piccolo di lastSize, significa che è stato ricreato o troncato
        if (stats.size < lastSize) {
            lastSize = 0;
            processedLines.clear(); // opzionale: resetta anche le righe già processate
        }
        if (stats.size > lastSize) {
            const fd = fs.openSync(filePath, 'r');
            const buffer = Buffer.alloc(stats.size - lastSize);
            fs.readSync(fd, buffer, 0, buffer.length, lastSize);
            fs.closeSync(fd);
            const newText = buffer.toString('utf8');
            lastSize = stats.size;
            // 🔹 Filtra solo righe d'errore e non duplicate
            const errorLines = newText.split(/\r?\n/)
                .filter(l => /error|exception|failed/i.test(l))
                .filter(l => !processedLines.has(l));
            for (const line of errorLines) {
                processedLines.add(line);
                (_a = ErrorSolverPanel.currentPanel) === null || _a === void 0 ? void 0 : _a.appendLog("⚡ Log: " + line);
                const response = await queryAI(line);
                (_b = ErrorSolverPanel.currentPanel) === null || _b === void 0 ? void 0 : _b.appendLog("📘 AI: " + response + "\n");
            }
        }
    });
}
// 🔹 Funzione per chiamare AI locale
async function queryAI(log) {
    var _a, _b, _c, _d, _e;
    try {
        const res = await axios_1.default.post("http://localhost:1234/v1/chat/completions", {
            model: "lmstudio-community/llama-3.2-3b-instruct",
            temperature: 0.3,
            max_tokens: 150,
            messages: [
                { role: "system", content: "Sei un assistente tecnico per errori di codice." },
                { role: "user", content: log }
            ]
        }, { headers: { "Content-Type": "application/json" } });
        return (_e = (_d = (_c = (_b = (_a = res.data.choices) === null || _a === void 0 ? void 0 : _a[0]) === null || _b === void 0 ? void 0 : _b.message) === null || _c === void 0 ? void 0 : _c.content) === null || _d === void 0 ? void 0 : _d.trim()) !== null && _e !== void 0 ? _e : "Nessuna risposta.";
    }
    catch (e) {
        return "Errore chiamando l’AI locale: " + e.message;
    }
}
// 🔹 Pannello WebView
class ErrorSolverPanel {
    static createOrShow(extensionUri) {
        var _a;
        const column = (_a = vscode.window.activeTextEditor) === null || _a === void 0 ? void 0 : _a.viewColumn;
        if (ErrorSolverPanel.currentPanel) {
            ErrorSolverPanel.currentPanel._panel.reveal(column);
            return;
        }
        const panel = vscode.window.createWebviewPanel('errorSolver', 'Error Solver', column || vscode.ViewColumn.One, { enableScripts: true });
        ErrorSolverPanel.currentPanel = new ErrorSolverPanel(panel);
    }
    constructor(panel) {
        this._disposables = [];
        this._isReady = false;
        this._buffer = [];
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
    dispose() {
        ErrorSolverPanel.currentPanel = undefined;
        this._panel.dispose();
        while (this._disposables.length) {
            const d = this._disposables.pop();
            if (d)
                d.dispose();
        }
    }
    _getHtml() {
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

                // Notifica all'estensione che il WebView è pronto
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
    appendLog(text) {
        if (this._isReady) {
            this._panel.webview.postMessage({ command: 'log', text });
        }
        else {
            this._buffer.push(text);
        }
    }
}
function deactivate() {
    if (watcher) {
        watcher.close();
        watcher = null;
    }
    processedLines.clear();
    lastSize = 0;
}

## 📊 Come Funziona

Quando si verifica un errore:

1. **Intercettazione**: L'estensione cattura l'errore
2. **Toast Notification**: Appare un toast elegante in alto a destra 🎨
3. **Invio Backend**: Payload JSON inviato al backend configurato
4. **Logging**: Il backend registra l'errore (database/file/console)
5. **Analisi AI**: Se configurata, l'AI analizza l'errore
6. **Aggiornamento Toast**: Il toast viene aggiornato con l'analisi AI
7. **Suggerimenti Console**: Log dettagliati nella console del browser

### 🎨 Toast Notifications

I toast appaiono automaticamente quando viene rilevato un errore:
- **Design moderno** con gradiente viola
- **Icone distintive** per ogni tipo di errore
- **Auto-dismiss** dopo 8 secondi
- **Click per dettagli** - clicca per vedere i log completi
- **Pulsante chiudi** manuale
- **Aggiornamento live** con analisi AI

### Esempio Output Console

```javascript
[Error Monitor] Backend response: {
  choices: [{
    message: {
      content: "Analisi dell'errore..."
    }
  }]
}

🤖 AI Analysis
Questo errore 404 indica che la risorsa '/api/users' non è stata trovata.
Possibili cause:
1. L'endpoint non è stato implementato
2. URL errato nella chiamata
3. Il server non è in esecuzione

Soluzione: Verifica...## 📊 Come Funziona

Quando si verifica un errore:

1. **Intercettazione**: L'estensione cattura l'errore
2. **Invio Backend**: Payload JSON inviato al backend configurato
3. **Logging**: Il backend registra l'errore (database/file/console)
4. **Analisi AI**: Se configurata, l'AI analizza l'errore
5. **Suggerimenti**: L'AI suggerisce soluzioni in console browser

### Esempio Output Console

```javascript
[Error Monitor] Backend response: {
  success: true,
  errorId: "1728912345abc"
}

🤖 AI Analysis
Questo errore 404 indica che la risorsa '/api/users' non è stata trovata.
Possibili cause:
1. L'endpoint non è stato implementato
2. URL errato nella chiamata
3. Il server non è in esecuzione

Soluzione: Ver# Error Monitor - Chrome Extension

Estensione Chrome che intercetta errori console.log, console.error e chiamate network, inviando i dati a un backend configurabile.

## 📁 Struttura File

```
error-monitor/
├── manifest.json
├── content.js
├── background.js
├── popup.html
├── popup.js
├── icon16.png
├── icon48.png
└── icon128.png
```

## 🚀 Installazione

### 1. Crea le icone

Crea tre file immagine PNG per le icone (16x16, 48x48, 128x128 pixel). Puoi usare qualsiasi immagine temporanea o generare icone online.

### 2. Carica l'estensione in Chrome

1. Apri Chrome e vai su `chrome://extensions/`
2. Attiva la **Modalità sviluppatore** (interruttore in alto a destra)
3. Clicca su **Carica estensione non pacchettizzata**
4. Seleziona la cartella contenente i file dell'estensione
5. L'estensione verrà caricata e apparirà nella toolbar

### 3. Configurazione Rapida (Popup)

1. Clicca sull'icona dell'estensione nella toolbar
2. Inserisci l'URL del backend
3. Attiva/disattiva il monitoring
4. Clicca **Salva Impostazioni**

### 4. Configurazione Avanzata (AI Debugger)

1. Clicca su **"Apri impostazioni avanzate"** nel popup
   OPPURE
   Vai su `chrome://extensions/` → Dettagli estensione → Opzioni
2. Configura:
   - **Backend URL**: dove inviare gli errori
   - **Model**: nome del modello AI (es. llama-3.2-3b-instruct)
   - **Base URL**: URL API AI (es. http://localhost:1234/v1)
   - **API Key**: chiave autenticazione
   - **Temperature**: creatività risposte (0.0-1.0)
   - **Max Tokens**: lunghezza massima risposta
   - **Base Prompt**: istruzioni per l'AI
   - **Output Language**: lingua risposte
3. Clicca **Test Connessione** per verificare
4. Clicca **Salva Impostazioni**

## 🖥️ Setup Backend e AI

### Backend Node.js (per logging)

```bash
# Crea cartella backend
mkdir error-monitor-backend
cd error-monitor-backend

# Inizializza progetto
npm init -y
npm install express cors

# Crea server.js (usa il codice fornito)
# Avvia server
node server.js
```

Backend disponibile su `http://localhost:3000`

### LM Studio (per AI Debugger)

1. Scarica [LM Studio](https://lmstudio.ai/)
2. Scarica un modello (es. Llama 3.2 3B Instruct)
3. Avvia il server locale (porta 1234 di default)
4. Nelle opzioni estensione usa:
   - Base URL: `http://localhost:1234/v1`
   - API Key: `lm-studio`
   - Model: nome del modello scaricato

### OpenAI API (alternativa)

Nelle opzioni usa:
- Base URL: `https://api.openai.com/v1`
- API Key: la tua chiave OpenAI
- Model: `gpt-4` o `gpt-3.5-turbo`

## 📊 Tipi di errori intercettati

L'estensione monitora:

- **console.error** - Errori loggati manualmente
- **console.warn** - Warning della console
- **JavaScript errors** - Errori runtime JavaScript
- **Unhandled promise rejections** - Promise non gestite
- **Network errors** - Chiamate HTTP con status 4xx/5xx
- **Fetch API errors** - Errori nelle chiamate fetch
- **XMLHttpRequest errors** - Errori XHR

## 💾 Formato Payload

Ogni errore inviato al backend contiene:

```json
{
  "type": "network_error",
  "message": "Descrizione errore",
  "url": "URL della risorsa",
  "status": 404,
  "timestamp": "2025-10-14T10:30:00.000Z",
  "pageUrl": "https://example.com",
  "userAgent": "Mozilla/5.0..."
}
```

## ⚙️ Configurazione avanzata

Puoi modificare `content.js` per:
- Filtrare specifici tipi di errori
- Aggiungere metadati personalizzati
- Modificare la logica di invio
- Aggiungere buffer o batching

## 🔒 Note di sicurezza

- L'estensione invia dati solo se il monitoring è attivo
- Non invia i propri log per evitare loop
- Gestisce errori di invio senza bloccare la pagina
- Usa HTTPS per il backend in produzione

## 🐛 Debug

Per verificare il funzionamento:

1. Apri la console di Chrome (F12)
2. Vedrai il messaggio `[Error Monitor] Extension loaded`
3. Testa con: `console.error('Test errore')`
4. Controlla i log del backend per verificare la ricezione

## 📝 Personalizzazione URL Backend

Puoi configurare qualsiasi endpoint:
- `http://localhost:3000/errors` (locale)
- `https://api.tuodominio.com/logs` (remoto)
- `https://tuoserver.com/webhook/errors` (webhook)

## 🔄 Aggiornamenti

Dopo aver modificato i file:
1. Vai su `chrome://extensions/`
2. Clicca il pulsante di reload sull'estensione
3. Ricarica le pagine web per applicare le modifiche
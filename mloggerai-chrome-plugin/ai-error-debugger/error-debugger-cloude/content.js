// Intercetta console.log e console.error
(function() {
  const originalLog = console.log;
  const originalError = console.error;
  const originalWarn = console.warn;

  // Variabile per tracciare se il container è stato creato
  let toastContainerCreated = false;

  // Crea il contenitore per i toast
  function createToastContainer() {
    if (toastContainerCreated || document.getElementById('error-monitor-toast-container')) {
      return;
    }
    
    const container = document.createElement('div');
    container.id = 'error-monitor-toast-container';
    container.style.cssText = `
      position: fixed;
      top: 20px;
      right: 20px;
      z-index: 2147483647;
      display: flex;
      flex-direction: column;
      gap: 10px;
      max-width: 400px;
      pointer-events: none;
    `;
    
    // Assicurati che il body esista
    if (document.body) {
      document.body.appendChild(container);
      toastContainerCreated = true;
      originalLog('[Error Monitor] Toast container creato');
    } else {
      // Se il body non esiste ancora, aspetta il DOMContentLoaded
      document.addEventListener('DOMContentLoaded', function() {
        if (!document.getElementById('error-monitor-toast-container')) {
          document.body.appendChild(container);
          toastContainerCreated = true;
          originalLog('[Error Monitor] Toast container creato (dopo DOMContentLoaded)');
        }
      });
    }
  }

  // Mostra toast di errore
  function showErrorToast(errorType, message, withAI = false) {
    originalLog('[Error Monitor] Tentativo di mostrare toast:', errorType);
    
    // Assicurati che il container esista
    createToastContainer();
    
    const container = document.getElementById('error-monitor-toast-container');
    if (!container) {
      originalError('[Error Monitor] Container toast non trovato!');
      return null;
    }
    
    const toast = document.createElement('div');
    toast.style.cssText = `
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      color: white;
      padding: 16px 20px;
      border-radius: 12px;
      box-shadow: 0 10px 40px rgba(0,0,0,0.3);
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
      font-size: 14px;
      line-height: 1.5;
      pointer-events: auto;
      cursor: pointer;
      transform: translateX(450px);
      transition: all 0.3s cubic-bezier(0.68, -0.55, 0.265, 1.55);
      opacity: 0;
      max-height: 300px;
      overflow: hidden;
    `;
    
    const typeEmoji = {
      'console_error': '❌',
      'console_warning': '⚠️',
      'javascript_error': '🐛',
      'network_error': '🌐',
      'unhandled_rejection': '💥'
    };
    
    const typeLabel = {
      'console_error': 'Console Error',
      'console_warning': 'Warning',
      'javascript_error': 'JavaScript Error',
      'network_error': 'Network Error',
      'unhandled_rejection': 'Unhandled Promise'
    };
    
    const truncatedMessage = message.length > 150 ? message.substring(0, 150) + '...' : message;
    
    toast.innerHTML = `
      <div style="display: flex; align-items: start; gap: 12px;">
        <div style="font-size: 24px; line-height: 1;">${typeEmoji[errorType] || '🔴'}</div>
        <div style="flex: 1; min-width: 0;">
          <div style="font-weight: 600; margin-bottom: 4px; font-size: 15px;">
            ${typeLabel[errorType] || 'Error'}
          </div>
          <div style="opacity: 0.9; font-size: 13px; word-wrap: break-word; max-height: 60px; overflow: hidden;">
            ${truncatedMessage}
          </div>
          ${withAI ? `
            <div id="ai-status-${Date.now()}" style="margin-top: 8px; padding-top: 8px; border-top: 1px solid rgba(255,255,255,0.2);">
              <div style="display: flex; align-items: center; gap: 6px; font-size: 12px; opacity: 0.8;">
                <span>🤖</span>
                <span>AI sta analizzando...</span>
              </div>
            </div>
          ` : ''}
        </div>
        <button class="close-toast-btn" style="
          background: rgba(255,255,255,0.2);
          border: none;
          color: white;
          width: 24px;
          height: 24px;
          border-radius: 50%;
          cursor: pointer;
          display: flex;
          align-items: center;
          justify-content: center;
          font-size: 16px;
          line-height: 1;
          transition: background 0.2s;
          flex-shrink: 0;
        ">×</button>
      </div>
    `;
    
    container.appendChild(toast);
    originalLog('[Error Monitor] Toast aggiunto al DOM');
    
    // Animazione di entrata
    setTimeout(() => {
      toast.style.transform = 'translateX(0)';
      toast.style.opacity = '1';
      originalLog('[Error Monitor] Toast animato');
    }, 50);
    
    // Gestione hover pulsante chiudi
    const closeBtn = toast.querySelector('.close-toast-btn');
    closeBtn.addEventListener('mouseenter', function() {
      this.style.background = 'rgba(255,255,255,0.3)';
    });
    closeBtn.addEventListener('mouseleave', function() {
      this.style.background = 'rgba(255,255,255,0.2)';
    });
    
    // Click per aprire console (tranne sul pulsante)
    toast.addEventListener('click', (e) => {
      if (!e.target.classList.contains('close-toast-btn')) {
        originalLog('%c👉 Apri DevTools per vedere i dettagli completi', 'color: #667eea; font-weight: bold; font-size: 14px;');
      }
    });
    
    // Pulsante chiudi
    closeBtn.addEventListener('click', (e) => {
      e.stopPropagation();
      removeToast(toast);
    });
    
    // Auto-remove dopo 8 secondi
    setTimeout(() => {
      if (toast.parentElement) {
        removeToast(toast);
      }
    }, 8000);
    
    return toast;
  }
  
  // Aggiorna toast con analisi AI
  function updateToastWithAI(toast, analysis) {
    if (!toast) return;
    
    const aiSection = toast.querySelector('[id^="ai-status-"]');
    if (aiSection) {
      const truncatedAnalysis = analysis.length > 120 ? analysis.substring(0, 120) + '...' : analysis;
      aiSection.innerHTML = `
        <div style="display: flex; align-items: start; gap: 6px; font-size: 12px;">
          <span style="font-size: 14px;">🤖</span>
          <div style="flex: 1; opacity: 0.9; line-height: 1.4;">
            ${truncatedAnalysis}
          </div>
        </div>
      `;
      originalLog('[Error Monitor] Toast aggiornato con AI');
    }
  }
  
  // Rimuovi toast con animazione
  function removeToast(toast) {
    toast.style.transform = 'translateX(450px)';
    toast.style.opacity = '0';
    setTimeout(() => {
      if (toast.parentElement) {
        toast.remove();
        originalLog('[Error Monitor] Toast rimosso');
      }
    }, 300);
  }

  // Funzione per inviare errori al backend e ottenere analisi AI
  async function sendToBackend(payload) {
    let currentToast = null;
    
    try {
      const syncResult = await chrome.storage.sync.get(['backendUrl', 'isEnabled']);
      const localResult = await chrome.storage.local.get([
        'model',
        'base_url',
        'api_Key',
        'temperature',
        'max_tokens',
        'base_prompt',
        'outputLanguage'
      ]);

      if (!syncResult.isEnabled) {
        originalLog('[Error Monitor] Monitoring disabilitato, ma mostro comunque il toast');
      }

      // Mostra toast di errore SEMPRE
      currentToast = showErrorToast(payload.type, payload.message, syncResult.isEnabled);
      
      if (!syncResult.isEnabled) return;

      const backendUrl = syncResult.backendUrl || 'http://localhost:3000/errors';
      
      // Formatta il messaggio per l'AI
      const errorMessage = `Tipo errore: ${payload.type}
Messaggio: ${payload.message}
${payload.url ? `URL: ${payload.url}` : ''}
${payload.pageUrl ? `Page URL: ${payload.pageUrl}` : ''}
${payload.status ? `Status HTTP: ${payload.status}` : ''}
${payload.statusText ? `Status Text: ${payload.statusText}` : ''}
${payload.filename ? `File: ${payload.filename}:${payload.lineno}:${payload.colno}` : ''}
${payload.stack ? `Stack trace:\n${payload.stack}` : ''}
${payload.duration ? `Durata: ${payload.duration}ms` : ''}
Timestamp: ${payload.timestamp}

Rispondi in ${localResult.outputLanguage || 'italiano'}.`;

      // Invia errore al backend con formato AI
      const response = await fetch(backendUrl, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          model: localResult.model || 'lmstudio-community/llama-3.2-3b-instruct',
          messages: [
            {
              role: 'system',
              content: localResult.base_prompt || 'Sei un esperto debugger che analizza errori e propone soluzioni concrete fornendo anche esempi in javascript.'
            },
            {
              role: 'user',
              content: errorMessage
            }
          ],
          temperature: localResult.temperature ?? 0.3,
          max_tokens: localResult.max_tokens ?? 150
        })
      });

      const responseData = await response.json();
      originalLog('[Error Monitor] Backend response:', responseData);

      // Se il backend ha restituito una risposta AI, mostrala
      if (responseData.choices && responseData.choices[0]) {
        const aiAnalysis = responseData.choices[0].message?.content || responseData.choices[0].text;
        if (aiAnalysis) {
          originalLog('%c[🤖 AI Analysis]', 'color: #4CAF50; font-weight: bold; font-size: 14px;');
          originalLog(aiAnalysis);
          
          // Aggiorna il toast con l'analisi AI
          if (currentToast) {
            updateToastWithAI(currentToast, aiAnalysis);
          }
        }
      }
      
    } catch (err) {
      originalError('[Error Monitor] Errore invio al backend:', err.message);
      
      // Aggiorna toast in caso di errore
      if (currentToast) {
        const aiSection = currentToast.querySelector('[id^="ai-status-"]');
        if (aiSection) {
          aiSection.innerHTML = `
            <div style="display: flex; align-items: center; gap: 6px; font-size: 12px; opacity: 0.8; color: #ffcccc;">
              <span>⚠️</span>
              <span>AI non disponibile</span>
            </div>
          `;
        }
      }
    }
  }

  // Override console.log
  console.log = function(...args) {
    originalLog.apply(console, args);
    
    // Non inviare i log del monitor stesso
    if (args[0] && typeof args[0] === 'string' && args[0].includes('[Error Monitor]')) {
      return;
    }
  };

  // Override console.error
  console.error = function(...args) {
    originalError.apply(console, args);
    
    const payload = {
      type: 'console_error',
      message: args.map(arg => 
        typeof arg === 'object' ? JSON.stringify(arg) : String(arg)
      ).join(' '),
      timestamp: new Date().toISOString(),
      url: window.location.href,
      userAgent: navigator.userAgent
    };
    
    sendToBackend(payload);
  };

  // Override console.warn
  console.warn = function(...args) {
    originalWarn.apply(console, args);
    
    const payload = {
      type: 'console_warning',
      message: args.map(arg => 
        typeof arg === 'object' ? JSON.stringify(arg) : String(arg)
      ).join(' '),
      timestamp: new Date().toISOString(),
      url: window.location.href,
      userAgent: navigator.userAgent
    };
    
    sendToBackend(payload);
  };

  // Intercetta errori JavaScript globali
  window.addEventListener('error', function(event) {
    const payload = {
      type: 'javascript_error',
      message: event.message,
      filename: event.filename,
      lineno: event.lineno,
      colno: event.colno,
      stack: event.error ? event.error.stack : '',
      timestamp: new Date().toISOString(),
      url: window.location.href,
      userAgent: navigator.userAgent
    };
    
    sendToBackend(payload);
  });

  // Intercetta promise rejection non gestite
  window.addEventListener('unhandledrejection', function(event) {
    const payload = {
      type: 'unhandled_rejection',
      message: event.reason ? String(event.reason) : 'Promise rejected',
      stack: event.reason && event.reason.stack ? event.reason.stack : '',
      timestamp: new Date().toISOString(),
      url: window.location.href,
      userAgent: navigator.userAgent
    };
    
    sendToBackend(payload);
  });

  // Intercetta chiamate fetch con errori
  const originalFetch = window.fetch;
  window.fetch = async function(...args) {
    const url = args[0];
    const startTime = Date.now();
    
    try {
      const response = await originalFetch.apply(this, args);
      
      // Intercetta errori HTTP (4xx, 5xx)
      if (!response.ok) {
        const duration = Date.now() - startTime;
        const payload = {
          type: 'network_error',
          method: args[1]?.method || 'GET',
          url: url,
          status: response.status,
          statusText: response.statusText,
          duration: duration,
          timestamp: new Date().toISOString(),
          pageUrl: window.location.href,
          userAgent: navigator.userAgent
        };
        
        sendToBackend(payload);
      }
      
      return response;
    } catch (error) {
      const duration = Date.now() - startTime;
      const payload = {
        type: 'network_error',
        method: args[1]?.method || 'GET',
        url: url,
        error: error.message,
        duration: duration,
        timestamp: new Date().toISOString(),
        pageUrl: window.location.href,
        userAgent: navigator.userAgent
      };
      
      sendToBackend(payload);
      throw error;
    }
  };

  // Intercetta XMLHttpRequest
  const originalXHROpen = XMLHttpRequest.prototype.open;
  const originalXHRSend = XMLHttpRequest.prototype.send;

  XMLHttpRequest.prototype.open = function(method, url) {
    this._method = method;
    this._url = url;
    this._startTime = Date.now();
    return originalXHROpen.apply(this, arguments);
  };

  XMLHttpRequest.prototype.send = function() {
    const xhr = this;
    
    const originalOnLoad = xhr.onload;
    xhr.onload = function() {
      if (xhr.status >= 400) {
        const duration = Date.now() - xhr._startTime;
        const payload = {
          type: 'network_error',
          method: xhr._method,
          url: xhr._url,
          status: xhr.status,
          statusText: xhr.statusText,
          duration: duration,
          timestamp: new Date().toISOString(),
          pageUrl: window.location.href,
          userAgent: navigator.userAgent
        };
        
        sendToBackend(payload);
      }
      
      if (originalOnLoad) {
        originalOnLoad.apply(this, arguments);
      }
    };
    
    const originalOnError = xhr.onerror;
    xhr.onerror = function() {
      const duration = Date.now() - xhr._startTime;
      const payload = {
        type: 'network_error',
        method: xhr._method,
        url: xhr._url,
        error: 'Network request failed',
        duration: duration,
        timestamp: new Date().toISOString(),
        pageUrl: window.location.href,
        userAgent: navigator.userAgent
      };
      
      sendToBackend(payload);
      
      if (originalOnError) {
        originalOnError.apply(this, arguments);
      }
    };
    
    return originalXHRSend.apply(this, arguments);
  };

  // Inizializza subito il container se il DOM è già pronto
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', createToastContainer);
  } else {
    createToastContainer();
  }

  console.log('[Error Monitor] Extension loaded');
})();
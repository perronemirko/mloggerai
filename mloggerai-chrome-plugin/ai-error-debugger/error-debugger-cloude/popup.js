// Carica le impostazioni salvate
document.addEventListener('DOMContentLoaded', () => {
  chrome.storage.sync.get(['backendUrl', 'isEnabled'], (result) => {
    document.getElementById('backendUrl').value = result.backendUrl || 'http://localhost:1234/v1/chat/completion';
    document.getElementById('enabledToggle').checked = result.isEnabled !== false;
  });
});

// Salva le impostazioni
document.getElementById('saveBtn').addEventListener('click', () => {
  const backendUrl = document.getElementById('backendUrl').value;
  const isEnabled = document.getElementById('enabledToggle').checked;
  
  if (!backendUrl.trim()) {
    showStatus('Inserisci un URL valido', 'error');
    return;
  }
  
  chrome.storage.sync.set({
    backendUrl: backendUrl,
    isEnabled: isEnabled
  }, () => {
    showStatus('Impostazioni salvate con successo!', 'success');
    
    // Ricarica tutte le tab per applicare le nuove impostazioni
    chrome.tabs.query({}, (tabs) => {
      tabs.forEach(tab => {
        if (tab.url && !tab.url.startsWith('chrome://')) {
          chrome.tabs.reload(tab.id);
        }
      });
    });
  });
});

function showStatus(message, type) {
  const statusEl = document.getElementById('status');
  statusEl.textContent = message;
  statusEl.className = `status ${type}`;
  
  setTimeout(() => {
    statusEl.className = 'status';
  }, 3000);
}

// Link per aprire la pagina delle opzioni
document.getElementById('openOptions').addEventListener('click', (e) => {
  e.preventDefault();
  chrome.runtime.openOptionsPage();
});
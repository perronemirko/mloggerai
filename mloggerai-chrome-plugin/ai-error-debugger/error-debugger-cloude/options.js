document.addEventListener("DOMContentLoaded", () => {
  // Selettori Backend/Monitoring
  const backendUrl = document.getElementById("backendUrl");
  const enabledToggle = document.getElementById("enabledToggle");
  
  // Selettori AI
  const model = document.getElementById("model");
  const base_url = document.getElementById("base_url");
  const api_Key = document.getElementById("api_Key");
  const temperature = document.getElementById("temperature");
  const max_tokens = document.getElementById("max_tokens");
  const base_prompt = document.getElementById("base_prompt");
  const outputLanguage = document.getElementById("outputLanguage");
  
  // Pulsanti
  const saveBtn = document.getElementById("saveBtn");
  const testBtn = document.getElementById("testBtn");
  const status = document.getElementById("status");

  if (!model || !base_url || !saveBtn) {
    console.error("Alcuni elementi del DOM non sono presenti!");
    return;
  }

  // Ripristina le impostazioni salvate
  function restoreOptions() {
    // Carica impostazioni backend/monitoring
    chrome.storage.sync.get(['backendUrl', 'isEnabled'], (syncItems) => {
      backendUrl.value = syncItems.backendUrl || "http://localhost:1234/v1/chat/completion";
      enabledToggle.checked = syncItems.isEnabled !== false;
    });

    // Carica impostazioni AI
    chrome.storage.local.get(
      ["model", "base_url", "api_Key", "temperature", "max_tokens", "base_prompt", "outputLanguage"],
      (items) => {
        model.value = items.model || "lmstudio-community/llama-3.2-3b-instruct";
        base_url.value = items.base_url || "http://localhost:1234/v1";
        api_Key.value = items.api_Key || "lm-studio";
        temperature.value = items.temperature ?? 0.3;
        max_tokens.value = items.max_tokens ?? 150;
        base_prompt.value = items.base_prompt || "Trova il bug e proponi la soluzione";
        outputLanguage.value = items.outputLanguage || "italiano";
      }
    );
  }

  // Salva le impostazioni
  function saveOptions() {
    // Valida input
    if (!backendUrl.value.trim() || !base_url.value.trim()) {
      showStatus("Compila tutti i campi obbligatori", "error");
      return;
    }

    // Salva impostazioni backend/monitoring
    const syncSettings = {
      backendUrl: backendUrl.value.trim(),
      isEnabled: enabledToggle.checked
    };

    chrome.storage.sync.set(syncSettings);

    // Salva impostazioni AI
    const localSettings = {
      model: model.value.trim(),
      base_url: base_url.value.trim(),
      api_Key: api_Key.value.trim(),
      temperature: parseFloat(temperature.value || "0.3"),
      max_tokens: parseInt(max_tokens.value || "150"),
      base_prompt: base_prompt.value.trim(),
      outputLanguage: outputLanguage.value.trim() || "italiano"
    };

    chrome.storage.local.set(localSettings, () => {
      showStatus("✅ Impostazioni salvate con successo!", "success");
      
      // Ricarica tutte le tab per applicare le nuove impostazioni
      chrome.tabs.query({}, (tabs) => {
        tabs.forEach(tab => {
          if (tab.url && !tab.url.startsWith('chrome://')) {
            chrome.tabs.reload(tab.id);
          }
        });
      });
    });
  }

  // Test connessione AI
  async function testConnection() {
    showStatus("🔄 Test connessione in corso...", "info");
    
    try {
      const settings = {
        base_url: base_url.value.trim(),
        api_Key: api_Key.value.trim(),
        model: model.value.trim(),
        temperature: parseFloat(temperature.value || "0.3"),
        max_tokens: parseInt(max_tokens.value || "150")
      };

      const response = await fetch(`${settings.base_url}/chat/completions`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${settings.api_Key}`
        },
        body: JSON.stringify({
          model: settings.model,
          messages: [
            {
              role: 'user',
              content: 'Test connection. Reply with "OK"'
            }
          ],
          temperature: settings.temperature,
          max_tokens: 50
        })
      });

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}: ${response.statusText}`);
      }

      const data = await response.json();
      
      if (data.choices && data.choices[0]) {
        showStatus("✅ Connessione AI riuscita! Modello risponde correttamente.", "success");
      } else {
        showStatus("⚠️ Connessione stabilita ma risposta inaspettata", "error");
      }
      
    } catch (error) {
      showStatus(`❌ Errore connessione: ${error.message}`, "error");
    }
  }

  // Mostra messaggio di stato
  function showStatus(message, type) {
    status.textContent = message;
    status.className = `${type}`;
    
    if (type === "success") {
      setTimeout(() => {
        status.className = "";
        status.textContent = "";
      }, 5000);
    }
  }

  // Event listeners
  saveBtn.addEventListener("click", saveOptions);
  testBtn.addEventListener("click", testConnection);

  // Salvataggio con Enter nei campi input
  document.querySelectorAll('input').forEach(input => {
    input.addEventListener('keypress', (e) => {
      if (e.key === 'Enter') {
        saveOptions();
      }
    });
  });

  // Ripristina subito le impostazioni all'apertura
  restoreOptions();
});
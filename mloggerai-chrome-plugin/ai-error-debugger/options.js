document.addEventListener("DOMContentLoaded", () => {
  // Selettori
  const model = document.getElementById("model");
  const base_url = document.getElementById("base_url");
  const api_Key = document.getElementById("api_Key");
  const temperature = document.getElementById("temperature");
  const max_tokens = document.getElementById("max_tokens");
  const base_prompt = document.getElementById("base_prompt");
  const outputLanguage = document.getElementById("outputLanguage");
  const saveBtn = document.getElementById("saveBtn");
  const status = document.getElementById("status");

  if (!model || !base_url || !saveBtn) {
    console.error("Alcuni elementi del DOM non sono presenti!");
    return;
  }

  // Ripristina le impostazioni salvate
  function restoreOptions() {
    chrome.storage.local.get(
      ["model","base_url","api_Key","temperature","max_tokens","base_prompt","outputLanguage"],
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
    const settings = {
      model: model.value.trim(),
      base_url: base_url.value.trim(),
      api_Key: api_Key.value.trim(),
      temperature: parseFloat(temperature.value || "0.3"),
      max_tokens: parseInt(max_tokens.value || "150"),
      base_prompt: base_prompt.value.trim(),
      outputLanguage: outputLanguage.value.trim() || "italiano"
    };

    chrome.storage.local.set(settings, () => {
      status.textContent = "Impostazioni salvate!";
      setTimeout(() => { status.textContent = ""; }, 3000);
    });
  }

  // Event listener pulsante Salva
  saveBtn.addEventListener("click", saveOptions);

  // Ripristina subito le impostazioni all'apertura
  restoreOptions();
});

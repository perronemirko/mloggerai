document.addEventListener("DOMContentLoaded", () => {
  const simulateBtn = document.getElementById("simulateBtn");
  const popupStatus = document.getElementById("popupStatus");

  simulateBtn.addEventListener("click", async () => {
    popupStatus.textContent = "Invio richiesta al backend...";

    try {
      // Leggi impostazioni dal storage
      const items = await chrome.storage.local.get(
        ["model","base_url","api_Key","temperature","max_tokens","base_prompt","outputLanguage"]
      );

      // --- PAYLOAD PER IL MODELLO ---
      const payload = {
        model: items.model || "lmstudio-community/llama-3.2-3b-instruct",
        messages: [
          {
            role: "system",
            content: items.base_prompt || "Always answer in rhymes. Today is Thursday"
          },
          {
            role: "user",
            content: "What day is it today?"
          }
        ],
        temperature: items.temperature ?? 0.7,
        max_tokens: items.max_tokens ?? 200,
        stream: false
      };

      // Fetch diretto al backend
      const res = await fetch(`${items.base_url}/chat/completions`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${items.api_Key || ""}`
        },
        body: JSON.stringify(payload)
      });

      const data = await res.json();

      // Estrazione della risposta del modello
      const solution = data.choices?.[0]?.message?.content || "Nessuna risposta dal modello";

      popupStatus.textContent = "✅ Soluzione ricevuta!";

      // Mostra overlay nella pagina attiva
      const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
      if (tab && tab.id) {
        chrome.scripting.executeScript({
          target: { tabId: tab.id },
          func: (solution) => {
            const existing = document.getElementById("aiOverlay");
            if (existing) existing.remove();

            const overlay = document.createElement("div");
            overlay.id = "aiOverlay";
            Object.assign(overlay.style, {
              position: "fixed",
              bottom: "10px",
              right: "10px",
              background: "#fff",
              border: "1px solid #ccc",
              padding: "8px",
              zIndex: 999999,
              maxWidth: "320px",
              boxShadow: "0 2px 10px rgba(0,0,0,.2)",
              fontSize: "0.9em",
              whiteSpace: "pre-wrap"
            });
            overlay.innerHTML = `<strong>AI:</strong> ${solution}`;
            document.body.appendChild(overlay);

            setTimeout(() => overlay.remove(), 15000);
          },
          args: [solution]
        });
      }

    } catch (err) {
      popupStatus.textContent = "❌ Errore fetch: " + err.message;
      console.error(err);
    }
  });
});

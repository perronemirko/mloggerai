// content.js - verbose debug + fallback to background fetch
(async () => {
  try {
    console.log("AI Debugger content script: start");

    // leggi configurazione dal storage (async)
    const items = await chrome.storage.local.get([
      "model", "base_url", "api_Key",
      "temperature", "max_tokens", "base_prompt", "outputLanguage"
    ]);

    const model = items.model || "lmstudio-community/llama-3.2-3b-instruct";
    const baseURL = items.base_url || "http://localhost:1234";
    const apiKey = items.api_Key || "";
    const basePrompt = items.base_prompt || "Trova l'errore e proponi la soluzione";
    const language = items.outputLanguage || "italiano";

    console.log("AI Debugger config:", { baseURL, model, language });

    // semplice helper per overlay
    function showOverlay(text) {
      console.log("AI Overlay:", text);
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
        maxWidth: "420px",
        maxHeight: "260px",
        overflowY: "auto",
        boxShadow: "0 2px 10px rgba(0,0,0,.2)",
        fontSize: "0.9em",
        whiteSpace: "pre-wrap"
      });
      overlay.innerHTML = `<strong>💡 AI Suggestion:</strong><br>${text}`;
      document.body.appendChild(overlay);
      setTimeout(() => overlay.remove(), 30000);
    }

    async function callBackendDirect(payload) {
      console.log("Attempting direct fetch to backend:", baseURL + "/v1/chat/completions");
      try {
        const res = await fetch(`${baseURL}/v1/chat/completions`, {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "Authorization": `Bearer ${apiKey}`
          },
          body: JSON.stringify(payload),
          // mode/commentare se vuoi forzare cors behaviour:
          // mode: 'cors'
        });

        console.log("Direct fetch response status:", res.status, res.statusText);
        if (!res.ok) {
          const text = await res.text().catch(()=>"<no-body>");
          throw new Error(`HTTP ${res.status}: ${text}`);
        }
        const data = await res.json();
        console.log("Direct fetch response body:", data);
        return data;
      } catch (err) {
        console.error("Direct fetch failed:", err);
        throw err;
      }
    }

    async function callBackendViaBackground(payload) {
      console.log("Falling back to background fetch");
      return new Promise((resolve) => {
        chrome.runtime.sendMessage(
          { type: "FETCH_TO_BACKEND", payload },
          (response) => {
            if (chrome.runtime.lastError) {
              console.error("chrome.runtime.lastError:", chrome.runtime.lastError);
              resolve({ error: chrome.runtime.lastError.message });
            } else {
              console.log("Background fetch response:", response);
              resolve(response);
            }
          }
        );
      });
    }

    async function sendToAI(logMessage) {
      const payload = {
        model,
        messages: [
          { role: "system", content: `${basePrompt}. Rispondi in ${language}` },
          { role: "user", content: logMessage }
        ],
        temperature: items.temperature ?? 0.3,
        max_tokens: items.max_tokens ?? 256,
        stream: false
      };

      // Primo tentativo: fetch diretto dal content script
      try {
        const data = await callBackendDirect(payload);
        const solution = data?.choices?.[0]?.message?.content || data?.choices?.[0]?.text || JSON.stringify(data);
        showOverlay(solution);
        return;
      } catch (err) {
        // Direct fetch failed -> proviamo il background
      }

      // Fallback: chiedi al background di fare il fetch (richiede host_permissions)
      try {
        const bgResp = await callBackendViaBackground(payload);
        if (bgResp?.error) {
          showOverlay("Errore background fetch: " + bgResp.error);
        } else {
          const solution = bgResp?.choices?.[0]?.message?.content || bgResp?.choices?.[0]?.text || JSON.stringify(bgResp);
          showOverlay(solution);
        }
      } catch (err) {
        showOverlay("Errore fetch fallback: " + err.message);
      }
    }

    // Hook console.error/warn and global errors
    ["error", "warn"].forEach((m) => {
      const orig = console[m];
      console[m] = function (...args) {
        try { orig.apply(console, args); } catch(e){ /* ignore */ }
        const msg = `[console.${m}] ${args.map(a => typeof a === "object" ? JSON.stringify(a, null, 2) : a).join(" ")}`;
        console.log("Captured log ->", msg);
        sendToAI(msg);
      };
    });

    window.addEventListener("error", (e) => {
      const msg = `[window.error] ${e.message} at ${e.filename}:${e.lineno}:${e.colno}`;
      console.log("Captured window.error ->", msg);
      sendToAI(msg);
    });

    window.addEventListener("unhandledrejection", (e) => {
      const msg = `[unhandledrejection] ${typeof e.reason === "object" ? JSON.stringify(e.reason) : e.reason}`;
      console.log("Captured unhandledrejection ->", msg);
      sendToAI(msg);
    });

    console.log("AI Debugger content script: hooks installed");
  } catch (topErr) {
    console.error("AI Debugger content script failed to initialize:", topErr);
  }
})();

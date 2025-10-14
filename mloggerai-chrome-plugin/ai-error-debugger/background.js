// background.js
console.log("AI Debugger background worker started");

chrome.runtime.onMessage.addListener((msg, sender, sendResponse) => {
  if (msg?.type !== "FETCH_TO_BACKEND") return;

  (async () => {
    let respObj;
    try {
      const cfg = await chrome.storage.local.get(["base_url", "api_Key"]);
      const baseURL = cfg.base_url || "http://localhost:1234";
      const apiKey = cfg.api_Key || "";

      console.log("background: fetching to", baseURL + "/v1/chat/completions");
      const res = await fetch(`${baseURL}/v1/chat/completions`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Authorization": apiKey ? `Bearer ${apiKey}` : ""
        },
        body: JSON.stringify(msg.payload)
      });

      if (!res.ok) {
        const t = await res.text().catch(()=>"<no-body>");
        respObj = { error: `HTTP ${res.status}: ${t}` };
      } else {
        respObj = await res.json();
      }
    } catch (err) {
      console.error("background fetch error:", err);
      respObj = { error: err.message || String(err) };
    } finally {
      try {
        sendResponse(respObj);
      } catch (e) {
        console.warn("sendResponse failed:", e);
      }
    }
  })();

  return true; // keep message channel open for async sendResponse
});

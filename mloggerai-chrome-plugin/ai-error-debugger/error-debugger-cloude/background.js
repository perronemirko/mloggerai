// Inizializza le impostazioni di default
chrome.runtime.onInstalled.addListener(() => {
  chrome.storage.sync.set({
    backendUrl: 'http://localhost:1234/v1/chat/completions',
    isEnabled: true
  });
  
  console.log('Error Monitor Extension installed');
});

// Listener per messaggi dal content script o popup
chrome.runtime.onMessage.addListener((request, sender, sendResponse) => {
  if (request.action === 'getSettings') {
    chrome.storage.sync.get(['backendUrl', 'isEnabled'], (result) => {
      sendResponse(result);
    });
    return true;
  }
  
  if (request.action === 'saveSettings') {
    chrome.storage.sync.set(request.settings, () => {
      sendResponse({ success: true });
    });
    return true;
  }
});
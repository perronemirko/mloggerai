// Un piccolo script Node.js che genera un errore di proposito
console.log("🚀 Avvio test Error Solver...");

// Funzione che lancerà un errore
function crashApp() {
    console.log("Eseguo calcolo errato...");
    const result = 10 / undefinedVariable; // <-- ReferenceError
    console.log("Risultato:", result);
}
setTimeout(() => {
    try {
        crashApp();
    } catch (e) {
        console.error("❌ Errore catturato:", e.message);
        throw e; // rilancia per farlo apparire nel terminale
    }
}, 1000)
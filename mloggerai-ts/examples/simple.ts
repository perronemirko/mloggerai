import { ErrorSolver } from "../src/ErrorSolver"; // NO .js extension needed

async function main(): Promise<void> {
  const solver = new ErrorSolver({
    model: "gpt-4o-mini",
    outputLanguage: "italiano",
  });

  const logText = `
TypeError: Cannot read properties of undefined (reading 'map')
at /app/src/components/UserList.tsx:23:18
`;

  console.log("🧩 Analizzando errore...");

  const solution = await solver.solveFromLog(logText);

  console.log("\n✅ SOLUZIONE AI:");
  console.log(solution);
}

main().catch(console.error);

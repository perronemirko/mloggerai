#!/bin/bash
set -e

# ----------------------------
# Pulizia cartelle precedenti
# ----------------------------
rm -rf build
rm -rf install

# ----------------------------
# Percorsi
# ----------------------------
ROOT_DIR="$(pwd)"
BUILD_DIR="$ROOT_DIR/build"
INSTALL_DIR="$ROOT_DIR/install"
ENV_FILE="$ROOT_DIR/.env"

# ----------------------------
# 1️⃣ Crea la cartella build
# ----------------------------
mkdir -p "$BUILD_DIR"
cd "$BUILD_DIR"

# ----------------------------
# 2️⃣ Configura CMake
# ----------------------------
cmake .. -DCMAKE_BUILD_TYPE=Release -DCMAKE_INSTALL_PREFIX="$INSTALL_DIR"

# ----------------------------
# 3️⃣ Compila tutto
# ----------------------------
cmake --build . --parallel

# ----------------------------
# 4️⃣ Installa la libreria e gli header
# ----------------------------
cmake --install .

# ----------------------------
# 5️⃣ Esegui tutti gli esempi
# ----------------------------
EXAMPLES=("ErrorSolver_example" "ErrorSolver_example_flexible" "ErrorSolverBuilder_example")

for example in "${EXAMPLES[@]}"; do
    if [ -f "$example" ]; then
        if [ -f "$ENV_FILE" ]; then
            echo "✅ Running $example with .env..."
            ./$example "$ENV_FILE"
        else
            echo "⚠️ .env not found. Running $example without env..."
            ./$example
        fi
    else
        echo "❌ Example $example not found, skipping."
    fi
done

echo "🎉 Build and examples completed successfully!"

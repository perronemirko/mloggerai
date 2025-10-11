#!/bin/bash
set -e
rm build -rf
rm install -rf
# Percorsi
ROOT_DIR="$(pwd)"
BUILD_DIR="$ROOT_DIR/build"
INSTALL_DIR="$ROOT_DIR/install"
ENV_FILE="$ROOT_DIR/.env"

# 1️⃣ Crea la cartella build
mkdir -p "$BUILD_DIR"
cd "$BUILD_DIR"

# 2️⃣ Configura CMake
cmake .. -DCMAKE_BUILD_TYPE=Release -DCMAKE_INSTALL_PREFIX="$INSTALL_DIR"

# 3️⃣ Compila tutto
cmake --build . --parallel -j$(nproc)

# 4️⃣ Installa la libreria e gli header
cmake --install .

# 5️⃣ Esegui l'esempio con il file .env
if [ -f "$ENV_FILE" ]; then
    echo "Running mloggerai_example with .env..."
    ./ErrorSolver_example "$ENV_FILE"
else
    echo ".env file not found. Running example without env..."
    ./ErrorSolver_example
fi

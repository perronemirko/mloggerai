#!/bin/bash
set -e

# ----------------------------
# Color helper
# ----------------------------
GREEN="\033[0;32m"
YELLOW="\033[1;33m"
RED="\033[0;31m"
NC="\033[0m" # No Color

# ----------------------------
# Pulizia cartelle precedenti
# ----------------------------
echo -e "${YELLOW}🧹 Cleaning previous build and install directories...${NC}"
rm -rf build install

# ----------------------------
# Percorsi
# ----------------------------
ROOT_DIR="$(pwd)"
BUILD_DIR="$ROOT_DIR/build"
INSTALL_DIR="$ROOT_DIR/install"
ENV_FILE="$ROOT_DIR/.env"
BIN_DIR="$BUILD_DIR/bin"

# ----------------------------
# 1️⃣ Crea la cartella build
# ----------------------------
echo -e "${GREEN}📂 Creating build directory...${NC}"
mkdir -p "$BUILD_DIR"
cd "$BUILD_DIR"

# ----------------------------
# 2️⃣ Configura CMake
# ----------------------------
echo -e "${GREEN}⚙️  Configuring CMake...${NC}"
cmake .. -DCMAKE_BUILD_TYPE=Release -DCMAKE_INSTALL_PREFIX="$INSTALL_DIR"

# ----------------------------
# 3️⃣ Compila tutto
# ----------------------------
echo -e "${GREEN}🚀 Building project...${NC}"
cmake --build . --parallel

# ----------------------------
# 4️⃣ Installa librerie e header
# ----------------------------
echo -e "${GREEN}📦 Installing libraries and headers...${NC}"
cmake --install .

# ----------------------------
# 5️⃣ Esegui tutti gli esempi
# ----------------------------
EXAMPLES=("ErrorSolver_example" "ErrorSolverBuilder_example" "ErrorSolverFacade_example")

echo -e "${GREEN}🎯 Running all examples...${NC}"
for example in "${EXAMPLES[@]}"; do
    EXE_PATH="$BIN_DIR/$example"
    if [ -f "$EXE_PATH" ]; then
        if [ -f "$ENV_FILE" ]; then
            echo -e "${GREEN}✅ Running $example with .env...${NC}"
            "$EXE_PATH" "$ENV_FILE"
        else
            echo -e "${YELLOW}⚠️ .env not found. Running $example without env...${NC}"
            "$EXE_PATH"
        fi
    else
        echo -e "${RED}❌ Example $example not found in $BIN_DIR, skipping.${NC}"
    fi
done

echo -e "${GREEN}🎉 Build, install, and example runs completed successfully!${NC}"

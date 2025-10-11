cmake_minimum_required(VERSION 3.16)
project(ErrorSolverCPP VERSION 1.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include(FetchContent)

# ----------------------------
# nlohmann/json
# ----------------------------
FetchContent_Declare(
    json
    GIT_REPOSITORY https://github.com/nlohmann/json.git
    GIT_TAG v3.11.2
)
FetchContent_MakeAvailable(json)

# ----------------------------
# cpr
# ----------------------------
FetchContent_Declare(
    cpr
    GIT_REPOSITORY https://github.com/libcpr/cpr.git
    GIT_TAG 1.10.1
)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
FetchContent_MakeAvailable(cpr)

# ----------------------------
# dotenv-cpp (header-only manual setup)
# ----------------------------
FetchContent_Declare(
    dotenv
    GIT_REPOSITORY https://github.com/laserpants/dotenv-cpp.git
    GIT_TAG master
)
FetchContent_MakeAvailable(dotenv)

# Create the library manually since dotenv-cpp is header-only
add_library(dotenv-cpp INTERFACE)
target_include_directories(dotenv-cpp INTERFACE ${dotenv_SOURCE_DIR}/include)

# Create alias
add_library(dotenv::dotenv ALIAS dotenv-cpp)

# ----------------------------
# ErrorSolver library
# ----------------------------
add_library(ErrorSolver STATIC src/ErrorSolver.cpp)
target_include_directories(ErrorSolver PUBLIC include)
target_link_libraries(ErrorSolver PUBLIC cpr nlohmann_json::nlohmann_json)

# ----------------------------
# Example executable
# ----------------------------
add_executable(ErrorSolver_example examples/main.cpp)
target_include_directories(ErrorSolver_example PRIVATE include)

# Link libraries AFTER executable is created
target_link_libraries(ErrorSolver_example PRIVATE ErrorSolver dotenv::dotenv)
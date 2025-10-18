# cmake_minimum_required(VERSION 3.16)
# project(ErrorSolverCPP VERSION 1.1 LANGUAGES CXX)

# set(CMAKE_CXX_STANDARD 17)
# set(CMAKE_CXX_STANDARD_REQUIRED ON)
# set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# include(FetchContent)

# # ==========================================================
# # 📦 Dependencies
# # ==========================================================

# # ----------------------------
# # nlohmann/json
# # ----------------------------
# FetchContent_Declare(
#     json
#     GIT_REPOSITORY https://github.com/nlohmann/json.git
#     GIT_TAG v3.11.2
# )
# FetchContent_MakeAvailable(json)

# # ----------------------------
# # cpr (HTTP client)
# # ----------------------------
# FetchContent_Declare(
#     cpr
#     GIT_REPOSITORY https://github.com/libcpr/cpr.git
#     GIT_TAG 1.10.1
# )
# FetchContent_MakeAvailable(cpr)

# # ----------------------------
# # dotenv-cpp (header-only)
# # ----------------------------
# FetchContent_Declare(
#     dotenv
#     GIT_REPOSITORY https://github.com/laserpants/dotenv-cpp.git
#     GIT_TAG master
# )
# FetchContent_MakeAvailable(dotenv)

# add_library(dotenv-cpp INTERFACE)
# target_include_directories(dotenv-cpp INTERFACE ${dotenv_SOURCE_DIR}/include)
# add_library(dotenv::dotenv ALIAS dotenv-cpp)

# # ==========================================================
# # 🧠 Core Library: ErrorSolver
# # ==========================================================
# add_library(ErrorSolver STATIC
#     src/ErrorSolver.cpp
# )
# target_include_directories(ErrorSolver PUBLIC include)
# target_link_libraries(ErrorSolver
#     PUBLIC
#         cpr
#         nlohmann_json::nlohmann_json
# )

# # ==========================================================
# # 🏗️ Builder Library: ErrorSolverBuilder
# # ==========================================================
# add_library(ErrorSolverBuilder STATIC
#     src/ErrorSolverBuilder.cpp
# )
# target_include_directories(ErrorSolverBuilder PUBLIC include)
# target_link_libraries(ErrorSolverBuilder
#     PUBLIC
#         ErrorSolver
#         cpr
#         nlohmann_json::nlohmann_json
# )

# # ==========================================================
# # 🎛️ Facade Library: ErrorSolverFacade
# # ==========================================================
# add_library(ErrorSolverFacade STATIC
#     src/ErrorSolverFacade.cpp
# )
# target_include_directories(ErrorSolverFacade PUBLIC include)
# target_link_libraries(ErrorSolverFacade
#     PUBLIC
#         ErrorSolver
# )

# # ==========================================================
# # 🧩 Example Executables
# # ==========================================================

# # --- Example 1: classico ---
# add_executable(ErrorSolver_example examples/main.cpp)
# target_include_directories(ErrorSolver_example PRIVATE include)
# target_link_libraries(ErrorSolver_example PRIVATE
#     ErrorSolver
#     dotenv::dotenv
# )

# # --- Example 2: builder pattern ---
# add_executable(ErrorSolverBuilder_example examples/main_builder_example.cpp)
# target_include_directories(ErrorSolverBuilder_example PRIVATE include)
# target_link_libraries(ErrorSolverBuilder_example PRIVATE
#     ErrorSolverBuilder
#     ErrorSolverFacade       # ← aggiunto
#     ErrorSolver             # ← serve anche il core
#     dotenv::dotenv
# )
# # --- Example 3: facade pattern (builder + facade) ---
# add_executable(ErrorSolverFacade_example examples/main_facade_example.cpp)
# target_include_directories(ErrorSolverFacade_example PRIVATE include)
# target_link_libraries(ErrorSolverFacade_example PRIVATE
#     ErrorSolverFacade
#     ErrorSolverBuilder
#     dotenv::dotenv
# )

# # ==========================================================
# # ✅ Summary
# # ==========================================================
# message(STATUS "")
# message(STATUS "=========================================")
# message(STATUS "   ✅ ErrorSolverCPP Build Configuration  ")
# message(STATUS "-----------------------------------------")
# message(STATUS "   Core Library:       ErrorSolver")
# message(STATUS "   Builder Library:    ErrorSolverBuilder")
# message(STATUS "   Facade Library:     ErrorSolverFacade")
# message(STATUS "-----------------------------------------")
# message(STATUS "   Example executables:")
# message(STATUS "     • ErrorSolver_example")
# message(STATUS "     • ErrorSolverBuilder_example")
# message(STATUS "     • ErrorSolverFacade_example")
# message(STATUS "=========================================")
# message(STATUS "")
cmake_minimum_required(VERSION 3.16)
project(ErrorSolverCPP VERSION 1.1 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

include(FetchContent)

# ==========================================================
# 📦 Dependencies
# ==========================================================
# nlohmann/json
FetchContent_Declare(
    json
    GIT_REPOSITORY https://github.com/nlohmann/json.git
    GIT_TAG v3.11.2
)
FetchContent_MakeAvailable(json)

# cpr (HTTP client)
FetchContent_Declare(
    cpr
    GIT_REPOSITORY https://github.com/libcpr/cpr.git
    GIT_TAG 1.10.1
)
FetchContent_MakeAvailable(cpr)

# dotenv-cpp (header-only)
FetchContent_Declare(
    dotenv
    GIT_REPOSITORY https://github.com/laserpants/dotenv-cpp.git
    GIT_TAG master
)
FetchContent_MakeAvailable(dotenv)

add_library(dotenv-cpp INTERFACE)
target_include_directories(dotenv-cpp INTERFACE ${dotenv_SOURCE_DIR}/include)
add_library(dotenv::dotenv ALIAS dotenv-cpp)

# ==========================================================
# 🧠 Core Library: ErrorSolver
# ==========================================================
add_library(ErrorSolver STATIC src/ErrorSolver.cpp)
target_include_directories(ErrorSolver PUBLIC include)
target_link_libraries(ErrorSolver PUBLIC
    cpr
    nlohmann_json::nlohmann_json
)

# ==========================================================
# 🏗️ Builder Library: ErrorSolverBuilder
# ==========================================================
add_library(ErrorSolverBuilder STATIC src/ErrorSolverBuilder.cpp)
target_include_directories(ErrorSolverBuilder PUBLIC include)
target_link_libraries(ErrorSolverBuilder PUBLIC
    ErrorSolver
    cpr
    nlohmann_json::nlohmann_json
)

# ==========================================================
# 🎛️ Facade Library: ErrorSolverFacade
# ==========================================================
add_library(ErrorSolverFacade STATIC src/ErrorSolverFacade.cpp)
target_include_directories(ErrorSolverFacade PUBLIC include)
target_link_libraries(ErrorSolverFacade PUBLIC
    ErrorSolver
)

# ==========================================================
# 🧩 Example Executables
# ==========================================================
set(EXECUTABLE_OUTPUT_PATH ${CMAKE_BINARY_DIR}/bin)

# Example 1: classico
add_executable(ErrorSolver_example examples/main.cpp)
target_include_directories(ErrorSolver_example PRIVATE include)
target_link_libraries(ErrorSolver_example PRIVATE
    ErrorSolver
    dotenv::dotenv
)

# Example 2: builder pattern
add_executable(ErrorSolverBuilder_example examples/main_builder_example.cpp)
target_include_directories(ErrorSolverBuilder_example PRIVATE include)
target_link_libraries(ErrorSolverBuilder_example PRIVATE
    ErrorSolverBuilder
    ErrorSolverFacade
    ErrorSolver
    dotenv::dotenv
)

# Example 3: facade pattern
add_executable(ErrorSolverFacade_example examples/main_facade_example.cpp)
target_include_directories(ErrorSolverFacade_example PRIVATE include)
target_link_libraries(ErrorSolverFacade_example PRIVATE
    ErrorSolverFacade
    ErrorSolverBuilder
    ErrorSolver
    dotenv::dotenv
)

# ==========================================================
# 🏗️ Install targets (per integrazione esterna)
# ==========================================================
install(TARGETS ErrorSolver ErrorSolverBuilder ErrorSolverFacade
        EXPORT ErrorSolverTargets
        ARCHIVE DESTINATION lib
        LIBRARY DESTINATION lib
        INCLUDES DESTINATION include)

install(DIRECTORY include/ DESTINATION include)

install(EXPORT ErrorSolverTargets
        FILE ErrorSolverTargets.cmake
        NAMESPACE ErrorSolver::
        DESTINATION lib/cmake/ErrorSolver)

# ==========================================================
# ✅ Summary
# ==========================================================
message(STATUS "")
message(STATUS "=========================================")
message(STATUS "   ✅ ErrorSolverCPP Build Configuration  ")
message(STATUS "-----------------------------------------")
message(STATUS "   Core Library:       ErrorSolver")
message(STATUS "   Builder Library:    ErrorSolverBuilder")
message(STATUS "   Facade Library:     ErrorSolverFacade")
message(STATUS "-----------------------------------------")
message(STATUS "   Example executables (bin/):")
message(STATUS "     • ErrorSolver_example")
message(STATUS "     • ErrorSolverBuilder_example")
message(STATUS "     • ErrorSolverFacade_example")
message(STATUS "=========================================")
message(STATUS "")

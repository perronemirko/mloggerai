# cmake_minimum_required(VERSION 3.16)
# project(ErrorSolverCPP VERSION 1.0 LANGUAGES CXX)

# set(CMAKE_CXX_STANDARD 17)
# set(CMAKE_CXX_STANDARD_REQUIRED ON)

# include(FetchContent)

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
# # cpr
# # ----------------------------
# FetchContent_Declare(
#     cpr
#     GIT_REPOSITORY https://github.com/libcpr/cpr.git
#     GIT_TAG 1.10.1
# )
# set(CMAKE_POSITION_INDEPENDENT_CODE ON)
# FetchContent_MakeAvailable(cpr)

# # ----------------------------
# # dotenv-cpp (header-only manual setup)
# # ----------------------------
# FetchContent_Declare(
#     dotenv
#     GIT_REPOSITORY https://github.com/laserpants/dotenv-cpp.git
#     GIT_TAG master
# )
# FetchContent_MakeAvailable(dotenv)

# # Create the library manually since dotenv-cpp is header-only
# add_library(dotenv-cpp INTERFACE)
# target_include_directories(dotenv-cpp INTERFACE ${dotenv_SOURCE_DIR}/include)

# # Create alias
# add_library(dotenv::dotenv ALIAS dotenv-cpp)

# # ----------------------------
# # ErrorSolver library
# # ----------------------------
# add_library(ErrorSolver STATIC src/ErrorSolver.cpp)
# target_include_directories(ErrorSolver PUBLIC include)
# target_link_libraries(ErrorSolver PUBLIC cpr nlohmann_json::nlohmann_json)

# # ----------------------------
# # Example executable
# # ----------------------------
# add_executable(ErrorSolver_example examples/main.cpp)
# target_include_directories(ErrorSolver_example PRIVATE include)

# # ----------------------------
# # Example executable flexible
# # ----------------------------
# add_executable(ErrorSolver_example examples/flexible_main_example.cpp)
# target_include_directories(ErrorSolver_example PRIVATE include)

# # Link libraries AFTER executable is created
# target_link_libraries(ErrorSolver_example PRIVATE ErrorSolver dotenv::dotenv)


# # ----------------------------
# # ErrorSolverBuilder library
# # ----------------------------
# add_library(ErrorSolverBuilder STATIC src/ErrorSolverBuilder.cpp)
# target_include_directories(ErrorSolverBuilder PUBLIC include)
# target_link_libraries(ErrorSolverBuilder PUBLIC cpr nlohmann_json::nlohmann_json)

# # ----------------------------
# # Example executable builder
# # ----------------------------
# add_executable(ErrorSolverBuilder_example examples/main_builder_example.cpp)
# target_include_directories(ErrorSolverBuilder_example PRIVATE include)

# # Link libraries AFTER executable is created
# target_link_libraries(ErrorSolverBuilder_example PRIVATE ErrorSolverBuilder dotenv::dotenv)


cmake_minimum_required(VERSION 3.16)
project(ErrorSolverCPP VERSION 1.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

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
# cpr (HTTP client)
# ----------------------------
FetchContent_Declare(
    cpr
    GIT_REPOSITORY https://github.com/libcpr/cpr.git
    GIT_TAG 1.10.1
)
FetchContent_MakeAvailable(cpr)

# ----------------------------
# dotenv-cpp (header-only)
# ----------------------------
FetchContent_Declare(
    dotenv
    GIT_REPOSITORY https://github.com/laserpants/dotenv-cpp.git
    GIT_TAG master
)
FetchContent_MakeAvailable(dotenv)

# Create header-only interface library
add_library(dotenv-cpp INTERFACE)
target_include_directories(dotenv-cpp INTERFACE ${dotenv_SOURCE_DIR}/include)
add_library(dotenv::dotenv ALIAS dotenv-cpp)

# ----------------------------
# ErrorSolver library
# ----------------------------
add_library(ErrorSolver STATIC src/ErrorSolver.cpp)
target_include_directories(ErrorSolver PUBLIC include)
target_link_libraries(ErrorSolver PUBLIC cpr nlohmann_json::nlohmann_json)

# ----------------------------
# Example executable (classic)
# ----------------------------
add_executable(ErrorSolver_example examples/main.cpp)
target_include_directories(ErrorSolver_example PRIVATE include)
target_link_libraries(ErrorSolver_example PRIVATE ErrorSolver dotenv::dotenv)

# ----------------------------
# Example executable (flexible / fluent builder)
# ----------------------------
add_executable(ErrorSolver_example_flexible examples/flexible_main_example.cpp)
target_include_directories(ErrorSolver_example_flexible PRIVATE include)
target_link_libraries(ErrorSolver_example_flexible PRIVATE ErrorSolver dotenv::dotenv)

# ----------------------------
# ErrorSolverBuilder library
# ----------------------------
add_library(ErrorSolverBuilder STATIC src/ErrorSolverBuilder.cpp)
target_include_directories(ErrorSolverBuilder PUBLIC include)
target_link_libraries(ErrorSolverBuilder PUBLIC cpr nlohmann_json::nlohmann_json)

# ----------------------------
# Example executable (builder)
# ----------------------------
add_executable(ErrorSolverBuilder_example examples/main_builder_example.cpp)
target_include_directories(ErrorSolverBuilder_example PRIVATE include)
target_link_libraries(ErrorSolverBuilder_example PRIVATE ErrorSolverBuilder dotenv::dotenv)

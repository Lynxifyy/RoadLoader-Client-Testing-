cmake_minimum_required(VERSION 3.22.1)
project(roadclient LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(IMGUI_DIR ${CMAKE_CURRENT_SOURCE_DIR}/third_party/imgui)

add_library(roadclient SHARED
        JniBridge.cpp
        RoadOverlay.cpp
        OverlayUi.cpp
        ${IMGUI_DIR}/imgui.cpp
        ${IMGUI_DIR}/imgui_draw.cpp
        ${IMGUI_DIR}/imgui_tables.cpp
        ${IMGUI_DIR}/imgui_widgets.cpp
        ${IMGUI_DIR}/backends/imgui_impl_opengl3.cpp)

target_include_directories(roadclient PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}
        ${IMGUI_DIR}
        ${IMGUI_DIR}/backends)

target_compile_definitions(roadclient PRIVATE
        IMGUI_IMPL_OPENGL_ES3
        IMGUI_DEFINE_MATH_OPERATORS)

target_compile_options(roadclient PRIVATE
        -Wall
        -Wextra
        -Wno-unused-parameter
        -Wno-missing-field-initializers
        -fno-exceptions
        -fno-rtti)

find_library(GL_LIB GLESv3)
find_library(ANDROID_LIB android)
find_library(LOG_LIB log)

target_link_libraries(roadclient
        ${GL_LIB}
        ${ANDROID_LIB}
        ${LOG_LIB}
        EGL
        m)

# Keep the overlay small: the 32-bit APK carries both ABIs.
set_target_properties(roadclient PROPERTIES
        CXX_VISIBILITY_PRESET hidden
        VISIBILITY_INLINES_HIDDEN ON)

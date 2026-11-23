#include "RoadOverlay.h"

#include <android/asset_manager.h>
#include <android/log.h>

#include "backends/imgui_impl_opengl3.h"

#include <algorithm>
#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <cstring>

#define LOG_TAG "RoadOverlay"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

// The backend keeps its state on the ImGui context. A surface that goes away
// without a shutdown (context loss, view reuse) would otherwise trip the
// backend assertion on the next init, so the leftover context is retired here.
bool g_imguiBackendLive = false;

const char* kSceneVertex = R"GLSL(#version 300 es
precision highp float;
uniform vec4 uRect;
uniform vec4 uUv;
out vec2 vUv;
const vec2 corners[4] = vec2[4](vec2(0.0, 0.0), vec2(1.0, 0.0), vec2(0.0, 1.0), vec2(1.0, 1.0));
void main() {
    vec2 c = corners[gl_VertexID];
    vUv = uUv.xy + c * uUv.zw;
    gl_Position = vec4(mix(uRect.xy, uRect.zw, c), 0.0, 1.0);
}
)GLSL";

const char* kSceneFragment = R"GLSL(#version 300 es
precision highp float;
in vec2 vUv;
out vec4 fragColor;
uniform sampler2D uTex;
uniform int uMode;
uniform vec2 uResolution;
uniform float uTime;
uniform vec3 uAccentA;
uniform vec3 uAccentB;

void main() {
    if (uMode == 1) {
        fragColor = texture(uTex, vUv);
        return;
    }
    vec2 uv = vUv;
    float t = uTime;
    vec3 col = mix(vec3(0.020, 0.028, 0.070), vec3(0.055, 0.080, 0.170), uv.y);
    float g1 = smoothstep(0.95, 0.0, length(uv - vec2(0.24 + 0.06 * sin(t * 0.31), 0.18 + 0.05 * cos(t * 0.43))));
    float g2 = smoothstep(0.85, 0.0, length(uv - vec2(0.86 + 0.04 * cos(t * 0.27), 0.34 + 0.06 * sin(t * 0.37))));
    float g3 = smoothstep(0.80, 0.0, length(uv - vec2(0.55 + 0.05 * sin(t * 0.21), 0.92 + 0.04 * cos(t * 0.29))));
    col += uAccentA * g1 * 0.42;
    col += uAccentB * g2 * 0.30;
    col += vec3(0.09, 0.20, 0.17) * g3 * 0.30;
    float sheen = sin((uv.x * 1.3 + uv.y) * 5.0 - t * 0.7) * 0.5 + 0.5;
    col += vec3(0.020, 0.028, 0.055) * sheen;
    vec2 v = uv - 0.5;
    col *= 1.0 - dot(v, v) * 0.55;
    fragColor = vec4(col, 1.0);
}
)GLSL";

const char* kBlurFragment = R"GLSL(#version 300 es
precision highp float;
in vec2 vUv;
out vec4 fragColor;
uniform sampler2D uTex;
uniform vec2 uDir;

void main() {
    float w[4] = float[4](0.216092, 0.190394, 0.131402, 0.070270);
    vec4 sum = texture(uTex, vUv) * w[0];
    for (int i = 1; i < 4; ++i) {
        sum += (texture(uTex, vUv + uDir * float(i)) + texture(uTex, vUv - uDir * float(i))) * w[i];
    }
    fragColor = sum;
}
)GLSL";

const RoadOverlay::Accent kAccents[] = {
    {"Violet", 0xFF7C5CFF, 0xFF22D3EE},
    {"Aurora", 0xFF34D399, 0xFF22D3EE},
    {"Sunset", 0xFFFF7A59, 0xFFFBBF24},
    {"Rose", 0xFFFB7185, 0xFF7C5CFF},
    {"Ice", 0xFF22D3EE, 0xFF8AB4FF},
};
constexpr int kAccentCount = 5;

/** Palette entries are written as 0xAARRGGBB; ImGui wants IM_COL32(r, g, b, a). */
inline ImU32 rgba(uint32_t value) {
    return IM_COL32((value >> 16) & 0xFF, (value >> 8) & 0xFF, value & 0xFF, (value >> 24) & 0xFF);
}

inline ImU32 alpha(ImU32 color, float factor) {
    int a = (int)(((color >> IM_COL32_A_SHIFT) & 0xFF) * factor);
    a = std::min(255, std::max(0, a));
    return (color & ~IM_COL32_A_MASK) | ((ImU32)a << IM_COL32_A_SHIFT);
}

inline ImU32 lerpColor(ImU32 a, ImU32 b, float t) {
    int ar = (a >> IM_COL32_R_SHIFT) & 0xFF, ag = (a >> IM_COL32_G_SHIFT) & 0xFF;
    int ab = (a >> IM_COL32_B_SHIFT) & 0xFF, aa = (a >> IM_COL32_A_SHIFT) & 0xFF;
    int br = (b >> IM_COL32_R_SHIFT) & 0xFF, bg = (b >> IM_COL32_G_SHIFT) & 0xFF;
    int bb = (b >> IM_COL32_B_SHIFT) & 0xFF, ba = (b >> IM_COL32_A_SHIFT) & 0xFF;
    return IM_COL32(
        (int)(ar + (br - ar) * t), (int)(ag + (bg - ag) * t),
        (int)(ab + (bb - ab) * t), (int)(aa + (ba - aa) * t));
}

bool readAsset(AAssetManager* assets, const char* name, void** data, int* size) {
    if (assets == nullptr) return false;
    AAsset* asset = AAssetManager_open(assets, name, AASSET_MODE_BUFFER);
    if (asset == nullptr) return false;
    const int length = (int)AAsset_getLength(asset);
    if (length <= 0) {
        AAsset_close(asset);
        return false;
    }
    void* buffer = std::malloc((size_t)length);
    if (buffer == nullptr) {
        AAsset_close(asset);
        return false;
    }
    const int read = (int)AAsset_read(asset, buffer, (size_t)length);
    AAsset_close(asset);
    if (read != length) {
        std::free(buffer);
        return false;
    }
    *data = buffer;
    *size = length;
    return true;
}

long long jsonInt(const std::string& text, const char* key, long long fallback) {
    const std::string needle = std::string("\"") + key + "\"";
    const size_t at = text.find(needle);
    if (at == std::string::npos) return fallback;
    const char* start = text.c_str() + at + needle.size();
    while (*start != '\0' && (*start == ':' || *start == ' ')) ++start;
    return std::strtoll(start, nullptr, 10);
}

float jsonFloat(const std::string& text, const char* key, float fallback) {
    const std::string needle = std::string("\"") + key + "\"";
    const size_t at = text.find(needle);
    if (at == std::string::npos) return fallback;
    const char* start = text.c_str() + at + needle.size();
    while (*start != '\0' && (*start == ':' || *start == ' ')) ++start;
    return (float)std::strtod(start, nullptr);
}

}  // namespace

const RoadOverlay::Accent& RoadOverlay::accentAt(int index) {
    return kAccents[std::min(std::max(index, 0), kAccentCount - 1)];
}

int RoadOverlay::accentCount() { return kAccentCount; }

// ---------------------------------------------------------------- lifecycle

bool RoadOverlay::init(AAssetManager* assets, const std::string& configPath) {
    assets_ = assets;
    configPath_ = configPath;
    loadConfig();
    if (!initGl()) {
        LOGE("GL initialisation failed");
        return false;
    }
    initImGui();
    return glReady_ && imguiReady_;
}

void RoadOverlay::shutdown() {
    saveConfig();
    if (imguiReady_) {
        ImGui_ImplOpenGL3_Shutdown();
        ImGui::DestroyContext();
        imguiReady_ = false;
    }
    g_imguiBackendLive = false;
    destroyTargets();
    if (frameTexture_ != 0) {
        glDeleteTextures(1, &frameTexture_);
        frameTexture_ = 0;
    }
    if (sceneProgram_ != 0) {
        glDeleteProgram(sceneProgram_);
        sceneProgram_ = 0;
    }
    if (blurProgram_ != 0) {
        glDeleteProgram(blurProgram_);
        blurProgram_ = 0;
    }
    for (void*& data : fontData_) {
        if (data != nullptr) {
            std::free(data);
            data = nullptr;
        }
    }
    glReady_ = false;
}

void RoadOverlay::surfaceChanged(int width, int height) {
    surfaceWidth_ = std::max(1, width);
    surfaceHeight_ = std::max(1, height);
    if (!glReady_) return;
    createTargets(surfaceWidth_, surfaceHeight_);
    menuButtonPlaced_ = false;
    menuPosPlaced_ = false;
    if (imguiReady_) {
        ImGuiIO& io = ImGui::GetIO();
        io.DisplaySize = ImVec2((float)surfaceWidth_, (float)surfaceHeight_);
        io.DisplayFramebufferScale = ImVec2(1.f, 1.f);
    }
}

// --------------------------------------------------------------------- GL

GLuint RoadOverlay::compile(GLenum type, const char* source) {
    GLuint shader = glCreateShader(type);
    glShaderSource(shader, 1, &source, nullptr);
    glCompileShader(shader);
    GLint ok = GL_FALSE;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &ok);
    if (ok != GL_TRUE) {
        char log[1024] = {0};
        glGetShaderInfoLog(shader, sizeof(log), nullptr, log);
        LOGE("shader compile failed: %s", log);
        glDeleteShader(shader);
        return 0;
    }
    return shader;
}

GLuint RoadOverlay::link(const char* vertex, const char* fragment) {
    GLuint vs = compile(GL_VERTEX_SHADER, vertex);
    GLuint fs = compile(GL_FRAGMENT_SHADER, fragment);
    if (vs == 0 || fs == 0) {
        if (vs != 0) glDeleteShader(vs);
        if (fs != 0) glDeleteShader(fs);
        return 0;
    }
    GLuint program = glCreateProgram();
    glAttachShader(program, vs);
    glAttachShader(program, fs);
    glLinkProgram(program);
    glDeleteShader(vs);
    glDeleteShader(fs);
    GLint ok = GL_FALSE;
    glGetProgramiv(program, GL_LINK_STATUS, &ok);
    if (ok != GL_TRUE) {
        char log[1024] = {0};
        glGetProgramInfoLog(program, sizeof(log), nullptr, log);
        LOGE("program link failed: %s", log);
        glDeleteProgram(program);
        return 0;
    }
    return program;
}

bool RoadOverlay::initGl() {
    sceneProgram_ = link(kSceneVertex, kSceneFragment);
    blurProgram_ = link(kSceneVertex, kBlurFragment);
    if (sceneProgram_ == 0 || blurProgram_ == 0) return false;
    glGenTextures(1, &frameTexture_);
    glBindTexture(GL_TEXTURE_2D, frameTexture_);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glBindTexture(GL_TEXTURE_2D, 0);
    glDisable(GL_DEPTH_TEST);
    glReady_ = true;
    return true;
}

void RoadOverlay::destroyTargets() {
    for (int i = 0; i < 3; ++i) {
        if (targetFbo_[i] != 0) {
            glDeleteFramebuffers(1, &targetFbo_[i]);
            targetFbo_[i] = 0;
        }
        if (targetTex_[i] != 0) {
            glDeleteTextures(1, &targetTex_[i]);
            targetTex_[i] = 0;
        }
    }
    targetWidth_ = 0;
    targetHeight_ = 0;
}

void RoadOverlay::createTargets(int width, int height) {
    destroyTargets();
    targetWidth_ = std::max(1, width / 4);
    targetHeight_ = std::max(1, height / 4);
    for (int i = 0; i < 3; ++i) {
        glGenTextures(1, &targetTex_[i]);
        glBindTexture(GL_TEXTURE_2D, targetTex_[i]);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, targetWidth_, targetHeight_, 0, GL_RGBA,
                     GL_UNSIGNED_BYTE, nullptr);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glGenFramebuffers(1, &targetFbo_[i]);
        glBindFramebuffer(GL_FRAMEBUFFER, targetFbo_[i]);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, targetTex_[i], 0);
        const GLenum status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            LOGE("blur target %d incomplete: 0x%x", i, status);
        }
    }
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glBindTexture(GL_TEXTURE_2D, 0);
}

void RoadOverlay::drawBackdrop(float time) {
    const Accent& accent = accentAt(config_.accentIndex);
    const float a[3] = {((accent.start >> 16) & 0xFF) / 255.f, ((accent.start >> 8) & 0xFF) / 255.f,
                        (accent.start & 0xFF) / 255.f};
    const float b[3] = {((accent.end >> 16) & 0xFF) / 255.f, ((accent.end >> 8) & 0xFF) / 255.f,
                        (accent.end & 0xFF) / 255.f};
    glUseProgram(sceneProgram_);
    glUniform1i(glGetUniformLocation(sceneProgram_, "uMode"), 0);
    glUniform1f(glGetUniformLocation(sceneProgram_, "uTime"), time);
    glUniform2f(glGetUniformLocation(sceneProgram_, "uResolution"), (float)surfaceWidth_,
                (float)surfaceHeight_);
    glUniform3f(glGetUniformLocation(sceneProgram_, "uAccentA"), a[0], a[1], a[2]);
    glUniform3f(glGetUniformLocation(sceneProgram_, "uAccentB"), b[0], b[1], b[2]);
    glUniform4f(glGetUniformLocation(sceneProgram_, "uRect"), -1.f, -1.f, 1.f, 1.f);
    glUniform4f(glGetUniformLocation(sceneProgram_, "uUv"), 0.f, 1.f, 1.f, -1.f);
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
}

void RoadOverlay::drawFrameQuad() {
    if (frameTexture_ == 0 || frameWidth_ <= 0 || frameHeight_ <= 0) return;
    const float viewAspect = (float)surfaceWidth_ / (float)surfaceHeight_;
    const float frameAspect = (float)frameWidth_ / (float)frameHeight_;
    float sx = 1.f;
    float sy = 1.f;
    // Centre-crop: fill the surface and let the frame overflow instead of
    // showing letterbox bars next to the aurora.
    if (frameAspect > viewAspect) {
        sy = frameAspect / viewAspect;
    } else {
        sx = viewAspect / frameAspect;
    }
    glUseProgram(sceneProgram_);
    glUniform1i(glGetUniformLocation(sceneProgram_, "uMode"), 1);
    glUniform1i(glGetUniformLocation(sceneProgram_, "uTex"), 0);
    glUniform4f(glGetUniformLocation(sceneProgram_, "uRect"), -sx, -sy, sx, sy);
    glUniform4f(glGetUniformLocation(sceneProgram_, "uUv"), 0.f, 1.f, 1.f, -1.f);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, frameTexture_);
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
}

void RoadOverlay::drawScene() {
    glDisable(GL_BLEND);
    drawBackdrop(time_);
    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    drawFrameQuad();
    glDisable(GL_BLEND);
}

void RoadOverlay::blurPass() {
    if (targetFbo_[0] == 0) return;
    glBindFramebuffer(GL_FRAMEBUFFER, targetFbo_[0]);
    glViewport(0, 0, targetWidth_, targetHeight_);
    drawScene();

    glDisable(GL_BLEND);
    glUseProgram(blurProgram_);
    glUniform1i(glGetUniformLocation(blurProgram_, "uTex"), 0);
    glUniform4f(glGetUniformLocation(blurProgram_, "uRect"), -1.f, -1.f, 1.f, 1.f);
    glUniform4f(glGetUniformLocation(blurProgram_, "uUv"), 0.f, 1.f, 1.f, -1.f);

    glBindFramebuffer(GL_FRAMEBUFFER, targetFbo_[1]);
    glViewport(0, 0, targetWidth_, targetHeight_);
    glUniform2f(glGetUniformLocation(blurProgram_, "uDir"), 1.0f / (float)targetWidth_, 0.f);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, targetTex_[0]);
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);

    glBindFramebuffer(GL_FRAMEBUFFER, targetFbo_[2]);
    glViewport(0, 0, targetWidth_, targetHeight_);
    glUniform2f(glGetUniformLocation(blurProgram_, "uDir"), 0.f, 1.0f / (float)targetHeight_);
    glBindTexture(GL_TEXTURE_2D, targetTex_[1]);
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);

    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glBindTexture(GL_TEXTURE_2D, 0);
}

// ------------------------------------------------------------------ render

void RoadOverlay::render(const uint8_t* pixels, int frameWidth, int frameHeight, int stride,
                         float dt, const EngineStats& stats) {
    if (!glReady_ || surfaceWidth_ == 0 || surfaceHeight_ == 0) return;
    stats_ = stats;
    dt = std::min(std::max(dt, 0.f), 0.1f);
    time_ += dt;

    if (pixels != nullptr && frameWidth > 0 && frameHeight > 0 && stride >= frameWidth * 4) {
        glBindTexture(GL_TEXTURE_2D, frameTexture_);
        if (frameWidth != frameWidth_ || frameHeight != frameHeight_) {
            frameWidth_ = frameWidth;
            frameHeight_ = frameHeight;
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, frameWidth_, frameHeight_, 0, GL_RGBA,
                         GL_UNSIGNED_BYTE, nullptr);
        }
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        glPixelStorei(GL_UNPACK_ROW_LENGTH, stride / 4);
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, frameWidth_, frameHeight_, GL_RGBA,
                        GL_UNSIGNED_BYTE, pixels);
        glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    smoothFps_ += ((float)stats.fps - smoothFps_) * std::min(1.f, dt * 4.f);
    cpsWindow_ += dt;
    if (cpsWindow_ >= 0.5f) {
        const int64_t delta = stats.inputPushed - lastInputPushed_;
        cps_ = (float)delta / cpsWindow_;
        lastInputPushed_ = stats.inputPushed;
        cpsWindow_ = 0.f;
    }

    if (config_.blur && targetFbo_[0] != 0) {
        blurPass();
    }

    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glViewport(0, 0, surfaceWidth_, surfaceHeight_);
    drawScene();

    if (imguiReady_) {
        const float s = scaleForSurface();
        beginFrame(dt);
        drawHud(s);
        drawMenuButton(s);
        drawMenu(s);
        drawToasts(s);
        ImGui::Render();
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glViewport(0, 0, surfaceWidth_, surfaceHeight_);
        ImGui_ImplOpenGL3_RenderDrawData(ImGui::GetDrawData());
    }
    glUseProgram(0);
}

float RoadOverlay::scaleForSurface() const {
    // One design unit is one dp, so the HUD keeps its physical size on any
    // panel instead of shrinking with the frame resolution.
    return std::min(std::max(density_ * config_.hudScale, 1.f), 5.f);
}

// -------------------------------------------------------------- ImGui setup

void RoadOverlay::initImGui() {
    IMGUI_CHECKVERSION();
    if (ImGui::GetCurrentContext() != nullptr) {
        if (g_imguiBackendLive) {
            ImGui_ImplOpenGL3_Shutdown();
            g_imguiBackendLive = false;
        }
        ImGui::DestroyContext();
    }
    ImGui::CreateContext();
    ImGuiIO& io = ImGui::GetIO();
    io.IniFilename = nullptr;
    io.LogFilename = nullptr;
    io.ConfigFlags |= ImGuiConfigFlags_NoMouseCursorChange;
    io.ConfigWindowsMoveFromTitleBarOnly = true;
    io.DisplaySize = ImVec2((float)surfaceWidth_, (float)surfaceHeight_);

    ImFontConfig config;
    config.FontDataOwnedByAtlas = false;
    config.OversampleH = 2;
    config.OversampleV = 1;
    config.PixelSnapH = true;

    if (readAsset(assets_, "fonts/Poppins-Regular.ttf", &fontData_[0], &fontDataSize_[0]) &&
        readAsset(assets_, "fonts/Poppins-SemiBold.ttf", &fontData_[1], &fontDataSize_[1])) {
        fontBody_ = io.Fonts->AddFontFromMemoryTTF(fontData_[0], fontDataSize_[0], 17.f, &config);
        fontSemi_ = io.Fonts->AddFontFromMemoryTTF(fontData_[1], fontDataSize_[1], 17.f, &config);
        fontTitle_ = io.Fonts->AddFontFromMemoryTTF(fontData_[1], fontDataSize_[1], 27.f, &config);
    }
    if (fontBody_ == nullptr) fontBody_ = io.Fonts->AddFontDefault();
    if (fontSemi_ == nullptr) fontSemi_ = fontBody_;
    if (fontTitle_ == nullptr) fontTitle_ = fontBody_;
    io.FontDefault = fontBody_;

    styleImGui();
    imguiReady_ = ImGui_ImplOpenGL3_Init("#version 300 es");
    g_imguiBackendLive = imguiReady_;
    if (!imguiReady_) {
        LOGE("ImGui OpenGL3 backend failed to initialise");
        ImGui::DestroyContext();
    }
}

void RoadOverlay::styleImGui() {
    ImGuiStyle& style = ImGui::GetStyle();
    style.WindowPadding = ImVec2(0, 0);
    style.FramePadding = ImVec2(10, 6);
    style.ItemSpacing = ImVec2(10, 8);
    style.WindowRounding = 26.f;
    style.ChildRounding = 18.f;
    style.FrameRounding = 12.f;
    style.GrabRounding = 12.f;
    style.ScrollbarRounding = 12.f;
    style.WindowBorderSize = 0.f;
    style.ChildBorderSize = 0.f;
    style.FrameBorderSize = 0.f;
    style.ScrollbarSize = 6.f;
    style.Colors[ImGuiCol_Text] = ImVec4(0.918f, 0.941f, 1.0f, 1.0f);
    style.Colors[ImGuiCol_WindowBg] = ImVec4(0.f, 0.f, 0.f, 0.f);
    style.Colors[ImGuiCol_ChildBg] = ImVec4(0.f, 0.f, 0.f, 0.f);
    style.Colors[ImGuiCol_PopupBg] = ImVec4(0.06f, 0.08f, 0.16f, 0.96f);
    style.Colors[ImGuiCol_Border] = ImVec4(1.f, 1.f, 1.f, 0.10f);
    style.Colors[ImGuiCol_ScrollbarBg] = ImVec4(0.f, 0.f, 0.f, 0.f);
    style.Colors[ImGuiCol_ScrollbarGrab] = ImVec4(1.f, 1.f, 1.f, 0.18f);
    style.Colors[ImGuiCol_ScrollbarGrabHovered] = ImVec4(1.f, 1.f, 1.f, 0.28f);
    style.Colors[ImGuiCol_ScrollbarGrabActive] = ImVec4(1.f, 1.f, 1.f, 0.38f);
    style.Colors[ImGuiCol_SliderGrab] = ImVec4(1.f, 1.f, 1.f, 0.9f);
    style.Colors[ImGuiCol_Button] = ImVec4(1.f, 1.f, 1.f, 0.08f);
    style.Colors[ImGuiCol_ButtonHovered] = ImVec4(1.f, 1.f, 1.f, 0.14f);
    style.Colors[ImGuiCol_ButtonActive] = ImVec4(1.f, 1.f, 1.f, 0.20f);
    style.Colors[ImGuiCol_TextDisabled] = ImVec4(0.55f, 0.60f, 0.70f, 1.0f);
}

void RoadOverlay::beginFrame(float dt) {
    if (!imguiReady_) return;
    ImGui_ImplOpenGL3_NewFrame();
    ImGui::GetIO().DeltaTime = dt > 0.f ? dt : 1.f / 60.f;
    ImGui::NewFrame();
}

// ------------------------------------------------------------------- input

bool RoadOverlay::touch(int action, float x, float y) {
    if (!imguiReady_) return false;
    const float s = scaleForSurface();
    const float buttonSize = 52.f * s;
    const bool onButton = menuButtonPlaced_ && x >= menuButton_.x && x <= menuButton_.x + buttonSize &&
        y >= menuButton_.y && y <= menuButton_.y + buttonSize;

    // The floating button is handled before ImGui sees anything: a fast tap
    // otherwise arrives as press+release in one frame and the freshly opened
    // window would eat its own click.
    if (action == 0 && onButton) {
        menuButtonPressed_ = true;
        menuButtonDragged_ = false;
        menuButtonPress_ = ImVec2(x, y);
        return true;
    }
    if (menuButtonPressed_ && action == 2) {
        const float dx = x - menuButtonPress_.x;
        const float dy = y - menuButtonPress_.y;
        if (std::fabs(dx) + std::fabs(dy) > 4.f * s) {
            menuButtonDragged_ = true;
            menuButton_.x += dx;
            menuButton_.y += dy;
            menuButtonPress_ = ImVec2(x, y);
        }
        return true;
    }
    if (menuButtonPressed_ && (action == 1 || action == 3)) {
        menuButtonPressed_ = false;
        if (!menuButtonDragged_) {
            config_.menuOpen = !config_.menuOpen;
            if (config_.menuOpen) {
                pushToast("Mod menu opened");
            }
        }
        return true;
    }

    ImGuiIO& io = ImGui::GetIO();
    io.AddMouseSourceEvent(ImGuiMouseSource_TouchScreen);
    switch (action) {
        case 0:  // down
            lastMoveY_ = y;
            io.AddMousePosEvent(x, y);
            io.AddMouseButtonEvent(0, true);
            break;
        case 2:  // move
            // ImGui only scrolls on a wheel; a vertical drag over an open menu
            // becomes a wheel tick so long tab lists stay reachable by touch.
            if (config_.menuOpen && !ImGui::IsAnyItemActive()) {
                const float dy = y - lastMoveY_;
                if (std::fabs(dy) > 2.f) {
                    io.AddMouseWheelEvent(0.f, -dy / (44.f * s));
                }
            }
            lastMoveY_ = y;
            io.AddMousePosEvent(x, y);
            break;
        case 1:  // up
        case 3:  // cancel
            io.AddMousePosEvent(x, y);
            io.AddMouseButtonEvent(0, false);
            break;
        default:
            break;
    }
    const bool insideMenu = x >= menuMin_.x && x <= menuMax_.x && y >= menuMin_.y && y <= menuMax_.y;
    if (action == 0 && config_.menuOpen && !insideMenu) {
        config_.menuOpen = false;
        enqueue(CMD_CLOSE_MENU, 0);
        return true;
    }
    return config_.menuOpen || io.WantCaptureMouse;
}

// -------------------------------------------------------- config & commands

void RoadOverlay::loadConfig() {
    std::FILE* file = std::fopen(configPath_.c_str(), "rb");
    if (file == nullptr) return;
    std::string text;
    char buffer[512];
    size_t read = 0;
    while ((read = std::fread(buffer, 1, sizeof(buffer), file)) > 0) {
        text.append(buffer, read);
    }
    std::fclose(file);
    config_.accentIndex = (int)jsonInt(text, "accent", config_.accentIndex);
    config_.hudScale = jsonFloat(text, "scale", config_.hudScale);
    config_.panelOpacity = jsonFloat(text, "opacity", config_.panelOpacity);
    config_.blur = jsonInt(text, "blur", config_.blur ? 1 : 0) != 0;
    config_.modules = (uint32_t)jsonInt(text, "modules", (long long)config_.modules);
    config_.shaders = jsonInt(text, "shaders", config_.shaders ? 1 : 0) != 0;
    config_.safeMode = jsonInt(text, "safe", config_.safeMode ? 1 : 0) != 0;
    config_.volume = jsonFloat(text, "volume", config_.volume);
    config_.renderDistance = (int)jsonInt(text, "rd", config_.renderDistance);
    config_.accentIndex = std::min(std::max(config_.accentIndex, 0), kAccentCount - 1);
    config_.hudScale = std::min(std::max(config_.hudScale, 0.7f), 1.6f);
    config_.panelOpacity = std::min(std::max(config_.panelOpacity, 0.35f), 1.f);
}

void RoadOverlay::saveConfig() {
    if (configPath_.empty()) return;
    std::FILE* file = std::fopen(configPath_.c_str(), "wb");
    if (file == nullptr) return;
    std::fprintf(
        file,
        "{\"accent\":%d,\"scale\":%.3f,\"opacity\":%.3f,\"blur\":%d,\"modules\":%u,"
        "\"shaders\":%d,\"safe\":%d,\"volume\":%.3f,\"rd\":%d}\n",
        config_.accentIndex, config_.hudScale, config_.panelOpacity, config_.blur ? 1 : 0,
        config_.modules, config_.shaders ? 1 : 0, config_.safeMode ? 1 : 0, config_.volume,
        config_.renderDistance);
    std::fclose(file);
}

void RoadOverlay::enqueue(int command, int64_t value) {
    const uint64_t packed = ((uint64_t)(uint32_t)command << 32) | (uint32_t)(int32_t)value;
    commands_.push_back(packed);
    if (commands_.size() > 32) commands_.erase(commands_.begin());
}

int64_t RoadOverlay::pollCommand() {
    if (commands_.empty()) return 0;
    const uint64_t packed = commands_.front();
    commands_.erase(commands_.begin());
    return (int64_t)packed;
}

void RoadOverlay::configure(const OverlayConfig& config) {
    const bool menu = config_.menuOpen;
    config_ = config;
    config_.menuOpen = menu;
    config_.accentIndex = std::min(std::max(config_.accentIndex, 0), kAccentCount - 1);
    config_.hudScale = std::min(std::max(config_.hudScale, 0.7f), 1.6f);
    config_.panelOpacity = std::min(std::max(config_.panelOpacity, 0.35f), 1.f);
    saveConfig();
}

void RoadOverlay::setLabels(const std::string& instance, const std::string& version,
                            const std::string& engineVersion) {
    instanceLabel_ = instance.empty() ? "No instance" : instance;
    versionLabel_ = version.empty() ? "1.0.0" : version;
    engineLabel_ = engineVersion.empty() ? "core 2.1" : engineVersion;
}

void RoadOverlay::setMenuOpen(bool open) {
    config_.menuOpen = open;
}

void RoadOverlay::setDensity(float density) {
    density_ = std::min(std::max(density, 0.75f), 4.f);
}

void RoadOverlay::pushToast(const std::string& text) {
    toasts_.push_back({text, 1.9f});
    if (toasts_.size() > 3) toasts_.erase(toasts_.begin());
}

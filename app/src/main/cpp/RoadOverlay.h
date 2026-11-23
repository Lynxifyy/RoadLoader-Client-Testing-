#pragma once

#include <GLES3/gl3.h>
#include <android/asset_manager.h>

#include "imgui.h"

#include <cstdint>
#include <string>
#include <unordered_map>
#include <utility>
#include <vector>

/** Counters mirrored from the Kotlin host once per frame. */
struct EngineStats {
    int fps = 0;
    int64_t frameCount = 0;
    int64_t pingMs = 0;
    int64_t heapUsedMb = 0;
    int64_t heapLimitMb = 0;
    int64_t renderAvgUs = 0;
    int64_t inputPushed = 0;
    int64_t inputConsumed = 0;
    int64_t inputDropped = 0;
    int pid = 0;
    bool alive = false;
    int64_t elapsedMs = 0;
    int64_t audioPushed = 0;
    int status = 0;
    int64_t selfTest = 0;
};

/** HUD modules the player can switch on from the menu. */
enum ModuleBit : uint32_t {
    MOD_WATERMARK = 1u << 0,
    MOD_FPS = 1u << 1,
    MOD_PING = 1u << 2,
    MOD_MEMORY = 1u << 3,
    MOD_CPS = 1u << 4,
    MOD_SESSION = 1u << 5,
    MOD_RENDER = 1u << 6,
    MOD_INPUT = 1u << 7,
    MOD_ENGINE = 1u << 8,
    MOD_ALL = (1u << 9) - 1u,
};

/** Commands the overlay raises for the Kotlin host to apply. */
enum OverlayCommand : int {
    CMD_NONE = 0,
    CMD_SET_SHADERS = 1,
    CMD_SET_SAFE_MODE = 2,
    CMD_SET_RENDER_DISTANCE = 3,
    CMD_REQUEST_SELF_TEST = 4,
    CMD_SET_VOLUME = 5,
    CMD_SET_ACCENT = 6,
    CMD_SET_MODULES = 7,
    CMD_SET_HUD_SCALE = 8,
    CMD_SET_PANEL_OPACITY = 9,
    CMD_SET_BLUR = 10,
    CMD_CLOSE_MENU = 11,
    CMD_STOP_ENGINE = 12,
};

struct OverlayConfig {
    int accentIndex = 0;
    float hudScale = 1.0f;
    float panelOpacity = 1.0f;
    bool blur = true;
    uint32_t modules = MOD_WATERMARK | MOD_FPS | MOD_PING | MOD_MEMORY | MOD_CPS | MOD_SESSION | MOD_ENGINE;
    bool shaders = true;
    bool safeMode = false;
    float volume = 0.8f;
    int renderDistance = 12;
    bool menuOpen = false;
};

/**
 * Draws the streamed 32-bit frame, the frosted-glass HUD and the Dear ImGui
 * mod menu on one GLES 3 surface.
 *
 * Everything here runs on the GL thread; the Kotlin renderer feeds frames and
 * counters in, and drains [pollCommand] for anything the menu changed.
 */
class RoadOverlay {
public:
    struct Accent {
        const char* name;
        uint32_t start;
        uint32_t end;
    };

    bool init(AAssetManager* assets, const std::string& configPath);
    void shutdown();
    void surfaceChanged(int width, int height);
    void render(const uint8_t* pixels, int frameWidth, int frameHeight, int stride, float dt,
                const EngineStats& stats);
    bool touch(int action, float x, float y);

    void configure(const OverlayConfig& config);
    void setLabels(const std::string& instance, const std::string& version,
                   const std::string& engineVersion);
    void setMenuOpen(bool open);
    void setDensity(float density);
    bool menuOpen() const { return config_.menuOpen; }
    void pushToast(const std::string& text);

    int64_t pollCommand();

    static const Accent& accentAt(int index);
    static int accentCount();

private:

    // ---- GL
    bool initGl();
    void createTargets(int width, int height);
    void destroyTargets();
    void drawScene();
    void drawBackdrop(float time);
    void drawFrameQuad();
    void blurPass();
    GLuint compile(GLenum type, const char* source);
    GLuint link(const char* vertex, const char* fragment);

    // ---- ImGui lifecycle
    void initImGui();
    void styleImGui();
    void beginFrame(float dt);

    // ---- UI
    void drawHud(float s);
    void drawMenu(float s);
    void drawMenuButton(float s);
    void drawToasts(float s);

    bool pillRow(const char* label, const char* subtitle, bool* value, float s, uint32_t accent);
    bool floatSlider(const char* label, float* value, float min, float max, const char* fmt,
                     float s, uint32_t accent);
    bool intSlider(const char* label, int* value, int min, int max, float s, uint32_t accent);
    void glassPanel(ImDrawList* list, ImVec2 from, ImVec2 to, float rounding, uint32_t tint,
                    float opacity);
    void textGlow(ImDrawList* list, ImFont* font, float size, ImVec2 pos, uint32_t color,
                  const char* text);
    void moduleRow(ImDrawList* list, ImVec2 origin, float width, const char* label,
                   const char* value, uint32_t accent);
    float animate(ImGuiID id, float target, float dt);
    void hudValues(std::vector<std::pair<std::string, std::string>>& out) const;
    float scaleForSurface() const;

    // ---- config + commands
    void loadConfig();
    void saveConfig();
    void enqueue(int command, int64_t value);

    AAssetManager* assets_ = nullptr;
    std::string configPath_;
    OverlayConfig config_;
    EngineStats stats_;
    std::string instanceLabel_ = "No instance";
    std::string versionLabel_ = "1.0.0";
    std::string engineLabel_ = "core 2.1";

    // GL objects
    GLuint sceneProgram_ = 0;
    GLuint blurProgram_ = 0;
    GLuint frameTexture_ = 0;
    GLuint targetTex_[3] = {0, 0, 0};
    GLuint targetFbo_[3] = {0, 0, 0};
    int surfaceWidth_ = 0;
    int surfaceHeight_ = 0;
    int frameWidth_ = 0;
    int frameHeight_ = 0;
    int targetWidth_ = 0;
    int targetHeight_ = 0;

    bool glReady_ = false;
    bool imguiReady_ = false;
    void* fontData_[2] = {nullptr, nullptr};
    int fontDataSize_[2] = {0, 0};
    ImFont* fontBody_ = nullptr;
    ImFont* fontSemi_ = nullptr;
    ImFont* fontTitle_ = nullptr;

    float density_ = 2.5f;
    float lastMoveY_ = 0.f;
    float time_ = 0.f;
    int tab_ = 0;
    float fpsHistory_[96] = {0.f};
    int fpsCursor_ = 0;
    float smoothFps_ = 0.f;
    float cps_ = 0.f;
    float cpsWindow_ = 0.f;
    int64_t lastInputPushed_ = 0;
    float uploadMs_ = 0.f;

    std::vector<uint64_t> commands_;
    std::vector<std::pair<std::string, float>> toasts_;
    std::unordered_map<ImGuiID, float> anim_;

    ImVec2 menuButton_{0.f, 0.f};
    bool menuButtonPlaced_ = false;
    bool menuButtonPressed_ = false;
    bool menuButtonDragged_ = false;
    ImVec2 menuButtonPress_{0.f, 0.f};
    bool menuHovered_ = false;
    bool menuButtonHovered_ = false;
    bool menuDragging_ = false;
    ImVec2 menuDragOffset_{0.f, 0.f};
    ImVec2 menuPos_{0.f, 0.f};
    ImVec2 menuMin_{0.f, 0.f};
    ImVec2 menuMax_{0.f, 0.f};
    bool menuPosPlaced_ = false;
};

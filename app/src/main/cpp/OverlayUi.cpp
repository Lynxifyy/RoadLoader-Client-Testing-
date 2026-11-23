// HUD and mod-menu drawing. Split from RoadOverlay.cpp so the GL/render code
// and the interface code can be read (and reviewed) separately.

#include "RoadOverlay.h"

#include "imgui.h"
#include "imgui_internal.h"

#include <algorithm>
#include <cfloat>
#include <cmath>
#include <cstdio>
#include <string>
#include <vector>

namespace {

/** Palette entries are written as 0xAARRGGBB; ImGui wants IM_COL32(r, g, b, a). */
inline ImU32 rgba(uint32_t value) {
    return IM_COL32((value >> 16) & 0xFF, (value >> 8) & 0xFF, value & 0xFF, (value >> 24) & 0xFF);
}

inline ImU32 withAlpha(ImU32 color, float factor) {
    int a = (int)(((color >> IM_COL32_A_SHIFT) & 0xFF) * factor);
    a = std::min(255, std::max(0, a));
    return (color & ~IM_COL32_A_MASK) | ((ImU32)a << IM_COL32_A_SHIFT);
}

inline ImU32 lerpColor(ImU32 a, ImU32 b, float t) {
    const int ar = (a >> IM_COL32_R_SHIFT) & 0xFF, ag = (a >> IM_COL32_G_SHIFT) & 0xFF;
    const int ab = (a >> IM_COL32_B_SHIFT) & 0xFF, aa = (a >> IM_COL32_A_SHIFT) & 0xFF;
    const int br = (b >> IM_COL32_R_SHIFT) & 0xFF, bg = (b >> IM_COL32_G_SHIFT) & 0xFF;
    const int bb = (b >> IM_COL32_B_SHIFT) & 0xFF, ba = (b >> IM_COL32_A_SHIFT) & 0xFF;
    return IM_COL32((int)(ar + (br - ar) * t), (int)(ag + (bg - ag) * t),
                    (int)(ab + (bb - ab) * t), (int)(aa + (ba - aa) * t));
}

const ImU32 kText = IM_COL32(234, 240, 255, 255);
const ImU32 kTextDim = IM_COL32(154, 168, 199, 255);
const ImU32 kTextMuted = IM_COL32(107, 120, 151, 255);
const ImU32 kWhite = IM_COL32(255, 255, 255, 255);

const char* statusLabel(int status) {
    switch (status) {
        case 1: return "starting";
        case 2: return "running";
        case 3: return "stopped";
        case 4: return "crashed";
        case 5: return "safe mode";
        default: return "idle";
    }
}

/** Rounded rect with a vertical gradient: ImGui 1.91 has no rounded variant. */
void gradientRect(ImDrawList* list, ImVec2 from, ImVec2 to, float rounding, ImU32 top,
                  ImU32 bottom) {
    constexpr int kBands = 10;
    const float height = (to.y - from.y) / kBands;
    for (int i = 0; i < kBands; ++i) {
        const ImU32 color = lerpColor(top, bottom, ((float)i + 0.5f) / kBands);
        const ImVec2 a(from.x, from.y + height * (float)i);
        const ImVec2 b(to.x, from.y + height * (float)(i + 1));
        ImDrawFlags flags = 0;
        float radius = 0.f;
        if (i == 0) {
            flags = ImDrawFlags_RoundCornersTop;
            radius = rounding;
        } else if (i == kBands - 1) {
            flags = ImDrawFlags_RoundCornersBottom;
            radius = rounding;
        }
        list->AddRectFilled(a, b, color, radius, flags);
    }
}

std::string formatSession(int64_t millis) {
    const int64_t total = millis / 1000;
    const int64_t minutes = total / 60;
    const int64_t seconds = total % 60;
    char buffer[32];
    std::snprintf(buffer, sizeof(buffer), "%02lld:%02lld", (long long)minutes, (long long)seconds);
    return buffer;
}

}  // namespace

// ------------------------------------------------------------------ helpers

float RoadOverlay::animate(ImGuiID id, float target, float dt) {
    float& value = anim_[id];
    value += (target - value) * std::min(1.f, dt * 16.f);
    if (std::fabs(target - value) < 0.001f) value = target;
    return value;
}

void RoadOverlay::glassPanel(ImDrawList* list, ImVec2 from, ImVec2 to, float rounding,
                             uint32_t tint, float opacity) {
    const float o = opacity * config_.panelOpacity;
    if (config_.blur && targetTex_[2] != 0 && surfaceWidth_ > 0 && surfaceHeight_ > 0) {
        const ImVec2 uv0(from.x / (float)surfaceWidth_, from.y / (float)surfaceHeight_);
        const ImVec2 uv1(to.x / (float)surfaceWidth_, to.y / (float)surfaceHeight_);
        list->AddImageRounded((ImTextureID)(intptr_t)targetTex_[2], from, to, uv0, uv1,
                              IM_COL32(255, 255, 255, (int)(54 * o)), rounding);
    }
    const ImU32 top = withAlpha(rgba(tint), std::min(1.f, o * 0.90f));
    const ImU32 bottom = withAlpha(rgba(tint), std::min(1.f, o * 1.0f));
    gradientRect(list, from, to, rounding, top, bottom);
    list->AddRect(from, to, IM_COL32(255, 255, 255, (int)(44 * o)), rounding, 0, 1.f);
    list->AddRectFilled(
        ImVec2(from.x + rounding * 0.5f, from.y),
        ImVec2(to.x - rounding * 0.5f, from.y + 1.f),
        IM_COL32(255, 255, 255, (int)(60 * o)), rounding);
}

void RoadOverlay::textGlow(ImDrawList* list, ImFont* font, float size, ImVec2 pos, uint32_t color,
                           const char* text) {
    const ImU32 glow = withAlpha(rgba(color), 0.22f);
    list->AddText(font, size, ImVec2(pos.x - 1.2f, pos.y), glow, text);
    list->AddText(font, size, ImVec2(pos.x + 1.2f, pos.y), glow, text);
    list->AddText(font, size, ImVec2(pos.x, pos.y - 1.2f), glow, text);
    list->AddText(font, size, ImVec2(pos.x, pos.y + 1.2f), glow, text);
    list->AddText(font, size, pos, rgba(color), text);
}

void RoadOverlay::moduleRow(ImDrawList* list, ImVec2 origin, float width, const char* label,
                            const char* value, uint32_t accent) {
    const float s = scaleForSurface();
    const float height = 26.f * s;
    const ImVec2 to(origin.x + width, origin.y + height);
    glassPanel(list, origin, to, 9.f * s, 0xFF0C1224, 0.85f);
    list->AddRectFilled(ImVec2(origin.x, origin.y + 5.f * s), ImVec2(origin.x + 3.f * s, to.y - 5.f * s),
                        rgba(accent), 2.f * s);
    list->AddText(fontBody_, 12.5f * s, ImVec2(origin.x + 12.f * s, origin.y + 5.f * s), kTextDim, label);
    const float valueWidth = fontSemi_->CalcTextSizeA(12.5f * s, FLT_MAX, 0.f, value).x;
    list->AddText(fontSemi_, 12.5f * s,
                  ImVec2(to.x - valueWidth - 10.f * s, origin.y + 5.f * s), rgba(accent), value);
}

void RoadOverlay::hudValues(std::vector<std::pair<std::string, std::string>>& out) const {
    char buffer[64];
    if (config_.modules & MOD_FPS) {
        std::snprintf(buffer, sizeof(buffer), "%d", (int)(smoothFps_ + 0.5f));
        out.emplace_back("FPS", buffer);
    }
    if (config_.modules & MOD_PING) {
        std::snprintf(buffer, sizeof(buffer), "%lld ms", (long long)stats_.pingMs);
        out.emplace_back("PING", buffer);
    }
    if (config_.modules & MOD_MEMORY) {
        std::snprintf(buffer, sizeof(buffer), "%lld/%lld MB", (long long)stats_.heapUsedMb,
                      (long long)stats_.heapLimitMb);
        out.emplace_back("HEAP", buffer);
    }
    if (config_.modules & MOD_CPS) {
        std::snprintf(buffer, sizeof(buffer), "%d", (int)(cps_ + 0.5f));
        out.emplace_back("CPS", buffer);
    }
    if (config_.modules & MOD_SESSION) {
        out.emplace_back("TIME", formatSession(stats_.elapsedMs));
    }
    if (config_.modules & MOD_RENDER) {
        std::snprintf(buffer, sizeof(buffer), "%.2f ms", (double)stats_.renderAvgUs / 1000.0);
        out.emplace_back("RENDER", buffer);
    }
    if (config_.modules & MOD_INPUT) {
        std::snprintf(buffer, sizeof(buffer), "%lld/%lld", (long long)stats_.inputPushed,
                      (long long)stats_.inputConsumed);
        out.emplace_back("INPUT", buffer);
    }
    if (config_.modules & MOD_ENGINE) {
        out.emplace_back("ENGINE", statusLabel(stats_.status));
    }
}

// --------------------------------------------------------------------- HUD

void RoadOverlay::drawHud(float s) {
    // The console owns the screen while it is open; the HUD would only fight
    // it for the same corners.
    if (config_.menuOpen) return;
    ImDrawList* list = ImGui::GetForegroundDrawList();
    const ImVec2 size = ImGui::GetIO().DisplaySize;
    const Accent& accent = accentAt(config_.accentIndex);
    const float margin = 18.f * s;

    float cursorY = margin;
    if (config_.modules & MOD_WATERMARK) {
        const float height = 68.f * s;
        const ImVec2 from(margin, cursorY);
        const ImVec2 to(margin + 258.f * s, cursorY + height);
        glassPanel(list, from, to, 18.f * s, 0xFF0A1020, 1.f);

        const ImVec2 center(from.x + 36.f * s, from.y + height * 0.5f);
        list->AddCircleFilled(center, 20.f * s, rgba(accent.start), 32);
        list->AddCircleFilled(ImVec2(center.x + 8.f * s, center.y + 8.f * s), 13.f * s,
                              rgba(accent.end), 32);
        list->AddCircleFilled(center, 15.f * s, IM_COL32(9, 13, 28, 255), 32);
        const char* mark = "R";
        const ImVec2 markSize = fontTitle_->CalcTextSizeA(19.f * s, FLT_MAX, 0.f, mark);
        list->AddText(fontTitle_, 19.f * s,
                      ImVec2(center.x - markSize.x * 0.5f, center.y - markSize.y * 0.5f),
                      rgba(accent.start), mark);

        textGlow(list, fontSemi_, 17.f * s, ImVec2(from.x + 66.f * s, from.y + 12.f * s),
                 accent.start, "RoadClient");
        list->AddText(fontBody_, 11.f * s, ImVec2(from.x + 66.f * s, from.y + 34.f * s), kTextDim,
                      instanceLabel_.c_str());
        list->AddText(fontBody_, 10.f * s, ImVec2(from.x + 66.f * s, from.y + 48.f * s), kTextMuted,
                      versionLabel_.c_str());
        list->AddRectFilled(ImVec2(from.x + 18.f * s, to.y - 2.f * s),
                            ImVec2(to.x - 18.f * s, to.y), rgba(accent.start), 1.f * s);
        cursorY = to.y + 10.f * s;
    }

    std::vector<std::pair<std::string, std::string>> values;
    hudValues(values);
    const float rowWidth = 168.f * s;
    const float rowHeight = 26.f * s;
    // Keep the first row clear of the floating menu button in the corner.
    const float startY = margin + 66.f * s;
    for (size_t i = 0; i < values.size(); ++i) {
        const ImVec2 origin(size.x - margin - rowWidth, startY + (rowHeight + 5.f * s) * (float)i);
        moduleRow(list, origin, rowWidth, values[i].first.c_str(), values[i].second.c_str(),
                  accent.start);
    }

    // Engine status line, bottom left.
    char status[128];
    std::snprintf(status, sizeof(status), "32-bit companion  pid %d  heap %lld MB  %s",
                  stats_.pid, (long long)stats_.heapUsedMb, engineLabel_.c_str());
    list->AddText(fontBody_, 10.5f * s, ImVec2(margin, size.y - margin - 16.f * s), kTextMuted,
                  status);

    // FPS sparkline, bottom right.
    if (config_.modules & MOD_FPS) {
        const float width = 196.f * s;
        const float height = 62.f * s;
        const ImVec2 from(size.x - margin - width, size.y - margin - height);
        const ImVec2 to(size.x - margin, size.y - margin);
        glassPanel(list, from, to, 14.f * s, 0xFF0A1020, 0.9f);
        list->AddText(fontBody_, 9.5f * s, ImVec2(from.x + 12.f * s, from.y + 7.f * s), kTextMuted,
                      "FRAME RATE");
        char fps[16];
        std::snprintf(fps, sizeof(fps), "%d", (int)(smoothFps_ + 0.5f));
        list->AddText(fontSemi_, 15.f * s, ImVec2(to.x - 34.f * s, from.y + 5.f * s),
                      rgba(accent.end), fps);

        const float graphTop = from.y + 26.f * s;
        const float graphBottom = to.y - 8.f * s;
        const float graphLeft = from.x + 10.f * s;
        const float graphRight = to.x - 10.f * s;
        const float step = (graphRight - graphLeft) / 95.f;
        const float peak = 120.f;
        ImVec2 points[96];
        for (int i = 0; i < 96; ++i) {
            const int index = (fpsCursor_ + i) % 96;
            const float value = std::min(fpsHistory_[index], peak) / peak;
            points[i] = ImVec2(graphLeft + step * (float)i,
                               graphBottom - (graphBottom - graphTop) * value);
        }
        for (int i = 0; i < 95; ++i) {
            list->AddLine(points[i], points[i + 1], withAlpha(rgba(accent.end), 0.85f), 1.6f * s);
        }
        for (int i = 0; i < 95; ++i) {
            list->AddQuadFilled(
                points[i], points[i + 1], ImVec2(points[i + 1].x, graphBottom),
                ImVec2(points[i].x, graphBottom), withAlpha(rgba(accent.start), 0.10f));
        }
    }

    if (stats_.frameCount == 0) {
        const ImVec2 center(size.x * 0.5f, size.y * 0.5f);
        const float radius = 26.f * s;
        for (int i = 0; i < 40; ++i) {
            const float a0 = time_ * 3.f + (float)i * 0.16f;
            const float a1 = a0 + 0.10f;
            const ImU32 color = withAlpha(rgba(accent.end), 0.9f - (float)i / 40.f * 0.85f);
            list->PathArcTo(center, radius, a0, a1, 4);
            list->PathStroke(color, 0, 3.f * s);
        }
        const char* label = "Starting the 32-bit engine";
        const ImVec2 textSize = fontSemi_->CalcTextSizeA(14.f * s, FLT_MAX, 0.f, label);
        list->AddText(fontSemi_, 14.f * s,
                      ImVec2(center.x - textSize.x * 0.5f, center.y + radius + 18.f * s), kTextDim,
                      label);
    }
}

// ------------------------------------------------------------- menu button

void RoadOverlay::drawMenuButton(float s) {
    ImDrawList* list = ImGui::GetForegroundDrawList();
    const ImVec2 size = ImGui::GetIO().DisplaySize;
    const float radius = 26.f * s;
    if (!menuButtonPlaced_) {
        menuButton_ = ImVec2(size.x - 18.f * s - radius * 2.f, 18.f * s);
        menuButtonPlaced_ = true;
    }
    const ImVec2 center(menuButton_.x + radius, menuButton_.y + radius);
    menuButtonHovered_ = false;

    const Accent& accent = accentAt(config_.accentIndex);
    list->AddCircleFilled(center, radius + 3.f * s, withAlpha(rgba(accent.start), 0.18f), 40);
    list->AddCircleFilled(center, radius, IM_COL32(12, 18, 36, 215), 40);
    list->AddCircle(center, radius, IM_COL32(255, 255, 255, 60), 40, 1.f);

    if (config_.menuOpen) {
        const float d = radius * 0.38f;
        list->AddLine(ImVec2(center.x - d, center.y - d), ImVec2(center.x + d, center.y + d), kWhite,
                      2.2f * s);
        list->AddLine(ImVec2(center.x + d, center.y - d), ImVec2(center.x - d, center.y + d), kWhite,
                      2.2f * s);
    } else {
        const float half = radius * 0.44f;
        for (int i = -1; i <= 1; ++i) {
            const float y = center.y + (float)i * radius * 0.28f;
            list->AddLine(ImVec2(center.x - half, y), ImVec2(center.x + half, y),
                          i == 0 ? rgba(accent.end) : kWhite, 2.2f * s);
        }
    }
}

// -------------------------------------------------------------------- menu

bool RoadOverlay::pillRow(const char* label, const char* subtitle, bool* value, float s,
                          uint32_t accent) {
    ImGui::PushID(label);
    const float width = ImGui::GetContentRegionAvail().x;
    const float height = (subtitle != nullptr ? 54.f : 42.f) * s;
    const ImVec2 start = ImGui::GetCursorScreenPos();
    ImGui::InvisibleButton("row", ImVec2(width, height));
    const bool clicked = ImGui::IsItemClicked();
    const bool hovered = ImGui::IsItemHovered();
    if (clicked) {
        *value = !*value;
        saveConfig();
    }

    ImDrawList* list = ImGui::GetWindowDrawList();
    if (hovered) {
        list->AddRectFilled(start, ImVec2(start.x + width, start.y + height),
                            IM_COL32(255, 255, 255, 12), 14.f * s);
    }
    list->AddText(fontSemi_, 14.f * s, ImVec2(start.x + 6.f * s, start.y + 8.f * s), kText, label);
    if (subtitle != nullptr) {
        list->AddText(fontBody_, 11.f * s, ImVec2(start.x + 6.f * s, start.y + 30.f * s), kTextMuted,
                      subtitle);
    }

    const ImGuiID id = ImGui::GetID("toggle");
    const float progress = animate(id, *value ? 1.f : 0.f, ImGui::GetIO().DeltaTime);
    const float trackW = 46.f * s;
    const float trackH = 26.f * s;
    const ImVec2 track(start.x + width - trackW - 4.f * s, start.y + (height - trackH) * 0.5f);
    const ImU32 off = IM_COL32(10, 16, 32, 235);
    const ImU32 on = rgba(accent);
    const ImU32 trackColor = lerpColor(off, on, progress);
    list->AddRectFilled(track, ImVec2(track.x + trackW, track.y + trackH), trackColor, trackH * 0.5f);
    list->AddRect(track, ImVec2(track.x + trackW, track.y + trackH),
                  lerpColor(IM_COL32(255, 255, 255, 70), IM_COL32(255, 255, 255, 150), progress),
                  trackH * 0.5f, 0, 1.2f * s);
    const float knobRadius = trackH * 0.5f - 3.f * s;
    const float knobX = track.x + trackH * 0.5f +
                        progress * (trackW - trackH);
    list->AddCircleFilled(ImVec2(knobX, track.y + trackH * 0.5f), knobRadius,
                          progress > 0.5f ? kWhite : IM_COL32(214, 224, 245, 255), 24);

    ImGui::PopID();
    return clicked;
}

bool RoadOverlay::floatSlider(const char* label, float* value, float min, float max, const char* fmt,
                              float s, uint32_t accent) {
    ImGui::PushID(label);
    const float width = ImGui::GetContentRegionAvail().x;
    const ImVec2 start = ImGui::GetCursorScreenPos();
    ImGui::InvisibleButton("slider", ImVec2(width, 46.f * s));
    ImDrawList* list = ImGui::GetWindowDrawList();
    const float trackY = start.y + 32.f * s;
    const float trackLeft = start.x + 6.f * s;
    const float trackRight = start.x + width - 6.f * s;
    const float trackWidth = trackRight - trackLeft;

    bool changed = false;
    if (ImGui::IsItemActive()) {
        const float t = std::min(1.f, std::max(0.f, (ImGui::GetIO().MousePos.x - trackLeft) / trackWidth));
        const float next = min + (max - min) * t;
        if (std::fabs(next - *value) > 0.0001f) {
            *value = next;
            changed = true;
        }
    }

    char display[64];
    std::snprintf(display, sizeof(display), fmt, (double)*value);
    list->AddText(fontSemi_, 13.f * s, ImVec2(start.x + 6.f * s, start.y + 2.f * s), kText, label);
    const ImVec2 valueSize = fontSemi_->CalcTextSizeA(13.f * s, FLT_MAX, 0.f, display);
    list->AddText(fontSemi_, 13.f * s, ImVec2(start.x + width - valueSize.x - 6.f * s, start.y + 2.f * s),
                  rgba(accent), display);

    const float t = (max > min) ? (*value - min) / (max - min) : 0.f;
    list->AddRectFilled(ImVec2(trackLeft, trackY), ImVec2(trackRight, trackY + 5.f * s),
                        IM_COL32(255, 255, 255, 26), 3.f * s);
    list->AddRectFilled(ImVec2(trackLeft, trackY),
                        ImVec2(trackLeft + trackWidth * t, trackY + 5.f * s), rgba(accent), 3.f * s);
    list->AddCircleFilled(ImVec2(trackLeft + trackWidth * t, trackY + 2.5f * s), 7.f * s, kWhite, 20);
    ImGui::PopID();
    return changed;
}

bool RoadOverlay::intSlider(const char* label, int* value, int min, int max, float s,
                            uint32_t accent) {
    float asFloat = (float)*value;
    const bool changed = floatSlider(label, &asFloat, (float)min, (float)max, "%.0f", s, accent);
    if (changed) *value = (int)(asFloat + 0.5f);
    return changed;
}

void RoadOverlay::drawMenu(float s) {
    menuHovered_ = false;
    if (!config_.menuOpen) return;

    const ImVec2 display = ImGui::GetIO().DisplaySize;
    const float width = std::min(470.f * s, display.x - 28.f * s);
    const float height = std::min(486.f * s, display.y - 28.f * s);
    if (!menuPosPlaced_) {
        menuPos_ = ImVec2(22.f * s, (display.y - height) * 0.5f);
        menuPosPlaced_ = true;
    }
    menuPos_.x = std::min(std::max(menuPos_.x, 8.f * s), display.x - width - 8.f * s);
    menuPos_.y = std::min(std::max(menuPos_.y, 8.f * s), display.y - height - 8.f * s);
    menuMin_ = menuPos_;
    menuMax_ = ImVec2(menuPos_.x + width, menuPos_.y + height);

    ImGui::SetNextWindowPos(menuPos_);
    ImGui::SetNextWindowSize(ImVec2(width, height));
    const ImGuiWindowFlags flags = ImGuiWindowFlags_NoTitleBar | ImGuiWindowFlags_NoResize |
        ImGuiWindowFlags_NoScrollbar | ImGuiWindowFlags_NoScrollWithMouse |
        ImGuiWindowFlags_NoCollapse | ImGuiWindowFlags_NoSavedSettings |
        ImGuiWindowFlags_NoBringToFrontOnFocus;
    ImGui::PushStyleVar(ImGuiStyleVar_WindowPadding, ImVec2(0, 0));
    if (ImGui::Begin("##roadclient-menu", nullptr, flags)) {
        menuHovered_ = ImGui::IsWindowHovered(ImGuiHoveredFlags_ChildWindows);
        ImDrawList* list = ImGui::GetWindowDrawList();
        const ImVec2 origin = ImGui::GetWindowPos();
        const ImVec2 far = ImVec2(origin.x + width, origin.y + height);
        const Accent& accent = accentAt(config_.accentIndex);

        glassPanel(list, origin, far, 26.f * s, 0xFF0A1020, 1.15f);
        // Accent hairline along the top edge.
        list->AddRectFilledMultiColor(ImVec2(origin.x + 26.f * s, origin.y + 1.f * s),
                                      ImVec2(far.x - 26.f * s, origin.y + 3.f * s),
                                      rgba(accent.start), rgba(accent.end), rgba(accent.end),
                                      rgba(accent.start));

        // Header, draggable.
        const float headerHeight = 64.f * s;
        ImGui::SetCursorScreenPos(ImVec2(origin.x + 18.f * s, origin.y + 8.f * s));
        ImGui::InvisibleButton("##header-drag", ImVec2(width - 96.f * s, headerHeight - 8.f * s));
        if (ImGui::IsItemActive() && ImGui::IsMouseDragging(0)) {
            menuPos_.x += ImGui::GetIO().MouseDelta.x;
            menuPos_.y += ImGui::GetIO().MouseDelta.y;
            ImGui::SetWindowPos(menuPos_);
        }

        const ImVec2 logoCenter(origin.x + 44.f * s, origin.y + 36.f * s);
        list->AddCircleFilled(logoCenter, 16.f * s, rgba(accent.start), 28);
        list->AddCircleFilled(ImVec2(logoCenter.x + 6.f * s, logoCenter.y + 6.f * s), 10.f * s,
                              rgba(accent.end), 28);
        list->AddCircleFilled(logoCenter, 12.f * s, IM_COL32(11, 17, 34, 255), 28);
        const ImVec2 markSize = fontTitle_->CalcTextSizeA(15.f * s, FLT_MAX, 0.f, "R");
        list->AddText(fontTitle_, 15.f * s,
                      ImVec2(logoCenter.x - markSize.x * 0.5f, logoCenter.y - markSize.y * 0.5f),
                      rgba(accent.start), "R");
        list->AddText(fontTitle_, 21.f * s, ImVec2(origin.x + 68.f * s, origin.y + 18.f * s), kText,
                      "RoadClient");
        list->AddText(fontBody_, 10.5f * s, ImVec2(origin.x + 68.f * s, origin.y + 44.f * s),
                      kTextMuted, "32-bit companion console");

        // Tabs.
        static const char* kTabs[] = {"Modules", "HUD", "Look", "Engine", "About"};
        const float tabsY = origin.y + 72.f * s;
        const float tabGap = 6.f * s;
        const float tabWidth = (width - 36.f * s - tabGap * 4.f) / 5.f;
        for (int i = 0; i < 5; ++i) {
            const ImVec2 tabMin(origin.x + 18.f * s + (tabWidth + tabGap) * (float)i, tabsY);
            const ImVec2 tabMax(tabMin.x + tabWidth, tabMin.y + 34.f * s);
            ImGui::SetCursorScreenPos(tabMin);
            ImGui::PushID(i);
            ImGui::InvisibleButton("##tab", ImVec2(tabWidth, 34.f * s));
            const bool hovered = ImGui::IsItemHovered();
            if (ImGui::IsItemClicked()) tab_ = i;
            ImGui::PopID();
            const bool selected = tab_ == i;
            if (selected) {
                gradientRect(list, tabMin, tabMax, 17.f * s, rgba(accent.start),
                             rgba(accent.end));
            } else if (hovered) {
                list->AddRectFilled(tabMin, tabMax, IM_COL32(255, 255, 255, 14), 17.f * s);
            }
            const ImVec2 textSize = fontSemi_->CalcTextSizeA(12.5f * s, FLT_MAX, 0.f, kTabs[i]);
            list->AddText(fontSemi_, 12.5f * s,
                          ImVec2(tabMin.x + (tabWidth - textSize.x) * 0.5f, tabMin.y + 9.f * s),
                          selected ? IM_COL32(255, 255, 255, 255) : kTextDim, kTabs[i]);
        }

        // Content region.
        const float contentTop = tabsY + 44.f * s;
        const float contentHeight = height - (contentTop - origin.y) - 40.f * s;
        ImGui::SetCursorScreenPos(ImVec2(origin.x + 18.f * s, contentTop));
        ImGui::PushStyleVar(ImGuiStyleVar_WindowPadding, ImVec2(0, 0));
        if (ImGui::BeginChild("##content", ImVec2(width - 36.f * s, contentHeight), false,
                              ImGuiWindowFlags_NoBackground)) {
            if (tab_ == 0) {
                if (pillRow("BetterRenderDragon", "Post-process shaders over the 32-bit pipeline",
                            &config_.shaders, s, accent.start)) {
                    enqueue(CMD_SET_SHADERS, config_.shaders ? 1 : 0);
                    pushToast(config_.shaders ? "Shaders enabled" : "Shaders disabled");
                }
                if (pillRow("Safe Mode", "Skip Rust fast paths, run the vanilla path",
                            &config_.safeMode, s, accent.start)) {
                    enqueue(CMD_SET_SAFE_MODE, config_.safeMode ? 1 : 0);
                    pushToast(config_.safeMode ? "Safe mode on" : "Safe mode off");
                }
                if (pillRow("Glass backdrop", "Blur the frame behind panels", &config_.blur, s,
                            accent.start)) {
                    enqueue(CMD_SET_BLUR, config_.blur ? 1 : 0);
                }
                float volumePercent = config_.volume * 100.f;
                if (floatSlider("Audio volume", &volumePercent, 0.f, 100.f, "%.0f%%", s,
                                accent.start)) {
                    config_.volume = volumePercent / 100.f;
                    enqueue(CMD_SET_VOLUME, (int64_t)(config_.volume * 100.f));
                }
            } else if (tab_ == 1) {
                struct ModuleRow {
                    const char* label;
                    const char* subtitle;
                    uint32_t bit;
                };
                static const ModuleRow kModules[] = {
                    {"Watermark", "Instance name and version", MOD_WATERMARK},
                    {"FPS", "Frame rate and sparkline", MOD_FPS},
                    {"Ping", "Companion heartbeat age", MOD_PING},
                    {"Heap", "32-bit heap usage", MOD_MEMORY},
                    {"CPS", "Input events per second", MOD_CPS},
                    {"Session", "Time in the world", MOD_SESSION},
                    {"Render", "Average render cost", MOD_RENDER},
                    {"Input", "Pushed and consumed events", MOD_INPUT},
                    {"Engine", "Companion state", MOD_ENGINE},
                };
                for (const ModuleRow& row : kModules) {
                    bool enabled = (config_.modules & row.bit) != 0;
                    if (pillRow(row.label, row.subtitle, &enabled, s, accent.start)) {
                        if (enabled) {
                            config_.modules |= row.bit;
                        } else {
                            config_.modules &= ~row.bit;
                        }
                        enqueue(CMD_SET_MODULES, (int64_t)config_.modules);
                    }
                }
                if (floatSlider("HUD scale", &config_.hudScale, 0.75f, 1.55f, "%.2fx", s,
                                accent.start)) {
                    enqueue(CMD_SET_HUD_SCALE, (int64_t)(config_.hudScale * 100.f));
                }
            } else if (tab_ == 2) {
                ImGui::Dummy(ImVec2(0, 2.f * s));
                ImDrawList* contentList = ImGui::GetWindowDrawList();
                const ImVec2 rowStart = ImGui::GetCursorScreenPos();
                contentList->AddText(fontSemi_, 13.f * s, rowStart, kText, "Accent");
                const float swatchY = rowStart.y + 26.f * s;
                for (int i = 0; i < 5; ++i) {
                    ImGui::PushID(100 + i);
                    const ImVec2 at(rowStart.x + 6.f * s + (float)i * 62.f * s, swatchY);
                    ImGui::SetCursorScreenPos(at);
                    ImGui::InvisibleButton("##swatch", ImVec2(52.f * s, 56.f * s));
                    const bool hovered = ImGui::IsItemHovered();
                    if (ImGui::IsItemClicked()) {
                        config_.accentIndex = i;
                        enqueue(CMD_SET_ACCENT, i);
                        saveConfig();
                    }
                    ImGui::PopID();
                    const ImVec2 center(at.x + 26.f * s, at.y + 20.f * s);
                    const Accent& swatch = accentAt(i);
                    contentList->AddCircleFilled(center, 18.f * s, rgba(swatch.start), 28);
                    contentList->AddCircleFilled(ImVec2(center.x + 6.f * s, center.y + 6.f * s),
                                                 12.f * s, rgba(swatch.end), 28);
                    if (config_.accentIndex == i) {
                        contentList->AddCircle(center, 22.f * s, IM_COL32(255, 255, 255, 200), 32,
                                               2.f * s);
                    } else if (hovered) {
                        contentList->AddCircle(center, 22.f * s, IM_COL32(255, 255, 255, 90), 32,
                                               1.5f * s);
                    }
                    const ImVec2 nameSize =
                        fontBody_->CalcTextSizeA(10.5f * s, FLT_MAX, 0.f, swatch.name);
                    contentList->AddText(fontBody_, 10.5f * s,
                                         ImVec2(center.x - nameSize.x * 0.5f, at.y + 40.f * s),
                                         config_.accentIndex == i ? kText : kTextMuted,
                                         swatch.name);
                }
                ImGui::SetCursorScreenPos(ImVec2(rowStart.x, swatchY + 62.f * s));
                float opacityPercent = config_.panelOpacity * 100.f;
                if (floatSlider("Panel opacity", &opacityPercent, 35.f, 100.f, "%.0f%%", s,
                                accent.start)) {
                    config_.panelOpacity = opacityPercent / 100.f;
                    enqueue(CMD_SET_PANEL_OPACITY, (int64_t)(config_.panelOpacity * 100.f));
                }
            } else if (tab_ == 3) {
                if (intSlider("Render distance", &config_.renderDistance, 2, 32, s, accent.start)) {
                    enqueue(CMD_SET_RENDER_DISTANCE, config_.renderDistance);
                }
                ImDrawList* contentList = ImGui::GetWindowDrawList();
                const ImVec2 at = ImGui::GetCursorScreenPos();
                const float barWidth = ImGui::GetContentRegionAvail().x - 12.f * s;
                const float fraction = stats_.heapLimitMb > 0
                    ? std::min(1.f, (float)stats_.heapUsedMb / (float)stats_.heapLimitMb)
                    : 0.f;
                contentList->AddText(fontBody_, 11.f * s, ImVec2(at.x + 6.f * s, at.y + 4.f * s),
                                     kTextMuted, "32-bit heap");
                contentList->AddRectFilled(ImVec2(at.x + 6.f * s, at.y + 22.f * s),
                                           ImVec2(at.x + 6.f * s + barWidth, at.y + 28.f * s),
                                           IM_COL32(255, 255, 255, 24), 3.f * s);
                contentList->AddRectFilled(
                    ImVec2(at.x + 6.f * s, at.y + 22.f * s),
                    ImVec2(at.x + 6.f * s + barWidth * fraction, at.y + 28.f * s),
                    rgba(fraction > 0.8f ? 0xFFF87171 : accent.start), 3.f * s);
                char heap[64];
                std::snprintf(heap, sizeof(heap), "%lld / %lld MB", (long long)stats_.heapUsedMb,
                              (long long)stats_.heapLimitMb);
                contentList->AddText(fontBody_, 10.5f * s, ImVec2(at.x + 6.f * s, at.y + 32.f * s),
                                     kTextDim, heap);
                ImGui::Dummy(ImVec2(0, 46.f * s));

                const ImVec2 grid = ImGui::GetCursorScreenPos();
                static const char* kLabels[] = {"FPS", "PING", "FRAMES", "RENDER", "INPUT",
                                                "AUDIO"};
                char values[6][32];
                std::snprintf(values[0], 32, "%d", stats_.fps);
                std::snprintf(values[1], 32, "%lld ms", (long long)stats_.pingMs);
                std::snprintf(values[2], 32, "%lld", (long long)stats_.frameCount);
                std::snprintf(values[3], 32, "%.2f ms", (double)stats_.renderAvgUs / 1000.0);
                std::snprintf(values[4], 32, "%lld/%lld", (long long)stats_.inputPushed,
                              (long long)stats_.inputConsumed);
                std::snprintf(values[5], 32, "%lld", (long long)stats_.audioPushed);
                const float cellW = (ImGui::GetContentRegionAvail().x - 8.f * s) * 0.5f;
                for (int i = 0; i < 6; ++i) {
                    const float x = grid.x + (float)(i % 2) * (cellW + 8.f * s);
                    const float y = grid.y + (float)(i / 2) * 40.f * s;
                    contentList->AddText(fontBody_, 9.5f * s, ImVec2(x + 2.f * s, y), kTextMuted,
                                         kLabels[i]);
                    contentList->AddText(fontSemi_, 13.f * s, ImVec2(x + 2.f * s, y + 13.f * s),
                                         kText, values[i]);
                }
                ImGui::Dummy(ImVec2(0, 128.f * s));

                const ImVec2 buttons = ImGui::GetCursorScreenPos();
                const float buttonW = (ImGui::GetContentRegionAvail().x - 10.f * s) * 0.5f;
                for (int i = 0; i < 2; ++i) {
                    ImGui::PushID(200 + i);
                    ImGui::SetCursorScreenPos(ImVec2(buttons.x + (float)i * (buttonW + 10.f * s),
                                                     buttons.y));
                    ImGui::InvisibleButton("##action", ImVec2(buttonW, 38.f * s));
                    const bool hovered = ImGui::IsItemHovered();
                    if (ImGui::IsItemClicked()) {
                        if (i == 0) {
                            enqueue(CMD_REQUEST_SELF_TEST, 0);
                            pushToast("Self-test requested");
                        } else {
                            enqueue(CMD_STOP_ENGINE, 0);
                            pushToast("Stopping the 32-bit engine");
                        }
                    }
                    ImGui::PopID();
                    const ImVec2 min(buttons.x + (float)i * (buttonW + 10.f * s), buttons.y);
                    const ImVec2 max(min.x + buttonW, min.y + 38.f * s);
                    contentList->AddRectFilled(min, max,
                                               i == 0
                                                   ? (hovered ? withAlpha(rgba(accent.start), 0.42f)
                                                              : withAlpha(rgba(accent.start), 0.26f))
                                                   : IM_COL32(255, 255, 255, hovered ? 26 : 14),
                                               14.f * s);
                    const char* text = i == 0 ? "Run self-test" : "Stop engine";
                    const ImVec2 size = fontSemi_->CalcTextSizeA(12.5f * s, FLT_MAX, 0.f, text);
                    contentList->AddText(fontSemi_, 12.5f * s,
                                         ImVec2(min.x + (buttonW - size.x) * 0.5f, min.y + 11.f * s),
                                         kText, text);
                }
                ImGui::Dummy(ImVec2(0, 44.f * s));
            } else {
                ImDrawList* contentList = ImGui::GetWindowDrawList();
                const ImVec2 at = ImGui::GetCursorScreenPos();
                contentList->AddText(fontSemi_, 13.f * s, at, kText, "RoadClient");
                contentList->AddText(fontBody_, 11.f * s, ImVec2(at.x, at.y + 20.f * s), kTextDim,
                                     (std::string("Version ") + versionLabel_).c_str());
                contentList->AddText(fontBody_, 11.f * s, ImVec2(at.x, at.y + 38.f * s), kTextDim,
                                     (std::string("Engine ") + engineLabel_).c_str());
                contentList->AddText(fontBody_, 11.f * s, ImVec2(at.x, at.y + 56.f * s), kTextDim,
                                     (std::string("Instance ") + instanceLabel_).c_str());
                contentList->AddText(fontBody_, 11.f * s, ImVec2(at.x, at.y + 74.f * s), kTextDim,
                                     (std::string("Companion ") + statusLabel(stats_.status) +
                                      (stats_.pid > 0 ? " · pid " + std::to_string(stats_.pid) : ""))
                                         .c_str());
                contentList->AddText(fontBody_, 11.f * s, ImVec2(at.x, at.y + 100.f * s), kTextMuted,
                                     "HUD and menu rendered with Dear ImGui 1.91.5");
                contentList->AddText(fontBody_, 11.f * s, ImVec2(at.x, at.y + 118.f * s), kTextMuted,
                                     "Frames streamed from the armeabi-v7a companion");
                contentList->AddText(fontBody_, 11.f * s, ImVec2(at.x, at.y + 136.f * s), kTextMuted,
                                     "over shared memory by RoadLoader.");
                ImGui::Dummy(ImVec2(0, 168.f * s));
            }
        }
        ImGui::EndChild();
        ImGui::PopStyleVar();

        // Footer.
        char footer[96];
        std::snprintf(footer, sizeof(footer), "v%s  ·  %s", versionLabel_.c_str(),
                      statusLabel(stats_.status));
        list->AddText(fontBody_, 9.5f * s, ImVec2(origin.x + 22.f * s, far.y - 24.f * s),
                      kTextMuted, footer);
        const char* hint = "tap outside to close";
        const ImVec2 hintSize = fontBody_->CalcTextSizeA(9.5f * s, FLT_MAX, 0.f, hint);
        list->AddText(fontBody_, 9.5f * s, ImVec2(far.x - hintSize.x - 22.f * s, far.y - 24.f * s),
                      kTextMuted, hint);
    }
    ImGui::End();
    ImGui::PopStyleVar();
}

// ------------------------------------------------------------------ toasts

void RoadOverlay::drawToasts(float s) {
    ImDrawList* list = ImGui::GetForegroundDrawList();
    const ImVec2 display = ImGui::GetIO().DisplaySize;
    const Accent& accent = accentAt(config_.accentIndex);
    float y = display.y - 92.f * s;
    for (int i = (int)toasts_.size() - 1; i >= 0; --i) {
        auto& toast = toasts_[(size_t)i];
        toast.second -= ImGui::GetIO().DeltaTime;
        if (toast.second <= 0.f) {
            toasts_.erase(toasts_.begin() + i);
            continue;
        }
        const float fade = std::min(1.f, toast.second / 0.4f);
        const ImVec2 size = fontSemi_->CalcTextSizeA(12.5f * s, FLT_MAX, 0.f, toast.first.c_str());
        const float width = size.x + 44.f * s;
        const ImVec2 from((display.x - width) * 0.5f, y);
        const ImVec2 to(from.x + width, from.y + 34.f * s);
        glassPanel(list, from, to, 17.f * s, 0xFF0B1122, fade);
        list->AddCircleFilled(ImVec2(from.x + 18.f * s, from.y + 17.f * s), 4.f * s,
                              withAlpha(rgba(accent.end), fade), 12);
        list->AddText(fontSemi_, 12.5f * s, ImVec2(from.x + 30.f * s, from.y + 9.f * s),
                      withAlpha(kText, fade), toast.first.c_str());
        y -= 42.f * s;
    }
}

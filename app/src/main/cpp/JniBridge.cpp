// JNI surface for the overlay renderer. All entry points are called from the
// GL thread inside GameRenderer.

#include <android/asset_manager_jni.h>
#include <android/log.h>
#include <jni.h>

#include "RoadOverlay.h"

#include <memory>
#include <string>

#define LOG_TAG "RoadOverlayJni"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

constexpr int kStatsLength = 16;

std::string toString(JNIEnv* env, jstring value) {
    if (value == nullptr) return {};
    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) return {};
    std::string text(chars);
    env->ReleaseStringUTFChars(value, chars);
    return text;
}

RoadOverlay* fromHandle(jlong handle) {
    return reinterpret_cast<RoadOverlay*>(handle);
}

void applyStats(JNIEnv* env, jlongArray array, EngineStats* stats) {
    if (array == nullptr || env->GetArrayLength(array) < kStatsLength) return;
    jlong values[kStatsLength] = {0};
    env->GetLongArrayRegion(array, 0, kStatsLength, values);
    stats->fps = (int)values[0];
    stats->frameCount = values[1];
    stats->pingMs = values[2];
    stats->heapUsedMb = values[3];
    stats->heapLimitMb = values[4];
    stats->renderAvgUs = values[5];
    stats->inputPushed = values[6];
    stats->inputConsumed = values[7];
    stats->inputDropped = values[8];
    stats->pid = (int)values[9];
    stats->alive = values[10] != 0;
    stats->elapsedMs = values[11];
    stats->audioPushed = values[12];
    stats->status = (int)values[13];
    stats->selfTest = values[14];
}

}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_dev_roadloader_client_session_NativeOverlay_nativeCreate(JNIEnv* env, jobject /*thiz*/,
                                                              jobject assetManager,
                                                              jstring configPath) {
    auto* overlay = new RoadOverlay();
    AAssetManager* assets = assetManager != nullptr ? AAssetManager_fromJava(env, assetManager) : nullptr;
    if (!overlay->init(assets, toString(env, configPath))) {
        LOGE("overlay init failed");
        delete overlay;
        return 0;
    }
    return reinterpret_cast<jlong>(overlay);
}

JNIEXPORT void JNICALL
Java_dev_roadloader_client_session_NativeOverlay_nativeDestroy(JNIEnv*, jobject /*thiz*/,
                                                               jlong handle) {
    auto* overlay = fromHandle(handle);
    if (overlay == nullptr) return;
    overlay->shutdown();
    delete overlay;
}

JNIEXPORT void JNICALL
Java_dev_roadloader_client_session_NativeOverlay_nativeSurfaceChanged(JNIEnv*, jobject /*thiz*/,
                                                                     jlong handle, jint width,
                                                                     jint height) {
    auto* overlay = fromHandle(handle);
    if (overlay != nullptr) overlay->surfaceChanged(width, height);
}

JNIEXPORT void JNICALL
Java_dev_roadloader_client_session_NativeOverlay_nativeRender(JNIEnv* env, jobject /*thiz*/,
                                                             jlong handle, jobject pixels,
                                                             jint frameWidth, jint frameHeight,
                                                             jint stride, jfloat dt,
                                                             jlongArray stats) {
    auto* overlay = fromHandle(handle);
    if (overlay == nullptr) return;
    EngineStats snapshot;
    applyStats(env, stats, &snapshot);
    const auto* data = pixels != nullptr
        ? static_cast<const uint8_t*>(env->GetDirectBufferAddress(pixels))
        : nullptr;
    overlay->render(data, frameWidth, frameHeight, stride, dt, snapshot);
}

JNIEXPORT jboolean JNICALL
Java_dev_roadloader_client_session_NativeOverlay_nativeTouch(JNIEnv*, jobject /*thiz*/,
                                                            jlong handle, jint action, jfloat x,
                                                            jfloat y) {
    auto* overlay = fromHandle(handle);
    if (overlay == nullptr) return JNI_FALSE;
    return overlay->touch(action, x, y) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_dev_roadloader_client_session_NativeOverlay_nativeSetMenuOpen(JNIEnv*, jobject /*thiz*/,
                                                                  jlong handle, jboolean open) {
    auto* overlay = fromHandle(handle);
    if (overlay != nullptr) overlay->setMenuOpen(open == JNI_TRUE);
}

JNIEXPORT jboolean JNICALL
Java_dev_roadloader_client_session_NativeOverlay_nativeIsMenuOpen(JNIEnv*, jobject /*thiz*/,
                                                                 jlong handle) {
    auto* overlay = fromHandle(handle);
    return overlay != nullptr && overlay->menuOpen() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_dev_roadloader_client_session_NativeOverlay_nativeSetDensity(JNIEnv*, jobject /*thiz*/,
                                                                 jlong handle, jfloat density) {
    auto* overlay = fromHandle(handle);
    if (overlay != nullptr) overlay->setDensity(density);
}

JNIEXPORT void JNICALL
Java_dev_roadloader_client_session_NativeOverlay_nativeSetLabels(JNIEnv* env, jobject /*thiz*/,
                                                                jlong handle, jstring instance,
                                                                jstring version,
                                                                jstring engineVersion) {
    auto* overlay = fromHandle(handle);
    if (overlay == nullptr) return;
    overlay->setLabels(toString(env, instance), toString(env, version),
                       toString(env, engineVersion));
}

JNIEXPORT void JNICALL
Java_dev_roadloader_client_session_NativeOverlay_nativeConfigure(
    JNIEnv*, jobject /*thiz*/, jlong handle, jint accent, jfloat hudScale, jfloat opacity,
    jboolean blur, jint modules, jboolean shaders, jboolean safeMode, jfloat volume,
    jint renderDistance) {
    auto* overlay = fromHandle(handle);
    if (overlay == nullptr) return;
    OverlayConfig config;
    config.accentIndex = accent;
    config.hudScale = hudScale;
    config.panelOpacity = opacity;
    config.blur = blur == JNI_TRUE;
    config.modules = (uint32_t)modules;
    config.shaders = shaders == JNI_TRUE;
    config.safeMode = safeMode == JNI_TRUE;
    config.volume = volume;
    config.renderDistance = renderDistance;
    overlay->configure(config);
}

JNIEXPORT jlong JNICALL
Java_dev_roadloader_client_session_NativeOverlay_nativePollCommand(JNIEnv*, jobject /*thiz*/,
                                                                  jlong handle) {
    auto* overlay = fromHandle(handle);
    return overlay != nullptr ? overlay->pollCommand() : 0;
}

JNIEXPORT void JNICALL
Java_dev_roadloader_client_session_NativeOverlay_nativeToast(JNIEnv* env, jobject /*thiz*/,
                                                            jlong handle, jstring text) {
    auto* overlay = fromHandle(handle);
    if (overlay != nullptr) overlay->pushToast(toString(env, text));
}

JNIEXPORT jstring JNICALL
Java_dev_roadloader_client_session_NativeOverlay_nativeVersion(JNIEnv* env, jobject /*thiz*/) {
    return env->NewStringUTF("imgui 1.91.5 / overlay 1.0");
}

}  // extern "C"

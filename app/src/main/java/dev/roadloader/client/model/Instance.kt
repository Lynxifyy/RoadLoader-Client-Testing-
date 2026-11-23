package dev.roadloader.client.model

import kotlinx.serialization.Serializable

/**
 * An imported Minecraft build. [sourcePath] points at the APK copied into app
 * storage so the engine keeps working after the original file is moved.
 */
@Serializable
data class Instance(
    val id: String,
    val displayName: String,
    val packageName: String? = null,
    val versionName: String? = null,
    val abi: String? = null,
    val elf: String? = null,
    val compatible: Boolean = false,
    val sourcePath: String,
    val sizeBytes: Long = 0,
    val importedAt: Long = 0,
    val lastPlayedAt: Long? = null,
    val playTimeMs: Long = 0,
    val accountId: String? = null,
    val accentIndex: Int = 0,
    val summary: String = "",
) {
    val shortVersion: String
        get() = versionName?.takeIf { it.isNotBlank() } ?: "unknown"

    val isArm32: Boolean
        get() = compatible && abi?.contains("armeabi-v7a") == true
}

@Serializable
data class Library(
    val instances: List<Instance> = emptyList(),
    val selectedId: String? = null,
) {
    val selected: Instance?
        get() = instances.firstOrNull { it.id == selectedId } ?: instances.firstOrNull()
}

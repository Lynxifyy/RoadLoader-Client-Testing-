package dev.roadloader.client.data

import android.content.Context
import android.net.Uri
import dev.roadloader.client.model.Instance
import dev.roadloader.core.ApkInspector
import java.io.File
import java.util.UUID

/**
 * Copies a picked APK into app storage and turns RoadLoader's inspector report
 * into a launchable instance.
 */
class InstanceImporter(private val context: Context) {

    data class Result(val instance: Instance?, val error: String?)

    fun import(uri: Uri, displayName: String? = null): Result {
        val id = UUID.randomUUID().toString()
        val directory = File(context.filesDir, "instances/$id")
        val apk = File(directory, "base.apk")
        return try {
            directory.mkdirs()
            context.contentResolver.openInputStream(uri)?.use { input ->
                apk.outputStream().use { output -> input.copyTo(output) }
            } ?: return Result(null, "The picker returned an empty stream.")
            if (!apk.isFile || apk.length() < 1024) {
                directory.deleteRecursively()
                return Result(null, "That file is too small to be an APK.")
            }
            val report = ApkInspector.inspect(context, apk)
            val accent = (report.packageName?.hashCode() ?: id.hashCode()).let { hash ->
                ((hash % 5) + 5) % 5
            }
            val name = displayName?.takeIf { it.isNotBlank() }
                ?: report.versionName?.takeIf { it.isNotBlank() }?.let { "Minecraft $it" }
                ?: report.name.removeSuffix(".apk")
            val instance = Instance(
                id = id,
                displayName = name,
                packageName = report.packageName,
                versionName = report.versionName,
                abi = report.abi,
                elf = report.elf,
                compatible = report.compatible,
                sourcePath = apk.absolutePath,
                sizeBytes = apk.length(),
                importedAt = System.currentTimeMillis(),
                accentIndex = accent,
                summary = report.summary,
            )
            Result(instance, null)
        } catch (error: Throwable) {
            runCatching { directory.deleteRecursively() }
            Result(null, "Import failed: ${error.message ?: error.javaClass.simpleName}")
        }
    }
}

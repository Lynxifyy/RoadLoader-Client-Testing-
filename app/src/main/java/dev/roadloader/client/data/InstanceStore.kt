package dev.roadloader.client.data

import android.content.Context
import dev.roadloader.client.model.Instance
import dev.roadloader.client.model.Library
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Persists the imported library as one small JSON document. Writes are
 * synchronous and atomic (temp file + rename) because the document is a few
 * hundred bytes and losing it would strand the user's instances.
 */
class InstanceStore(private val context: Context) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val file = File(context.filesDir, "library.json")
    private val _library = MutableStateFlow(load())

    val library: StateFlow<Library> = _library.asStateFlow()

    fun instances(): List<Instance> = _library.value.instances

    fun selected(): Instance? = _library.value.selected

    fun upsert(instance: Instance, select: Boolean = true) {
        update { current ->
            val without = current.instances.filterNot { it.id == instance.id }
            current.copy(
                instances = (listOf(instance) + without).sortedByDescending { it.importedAt },
                selectedId = if (select) instance.id else current.selectedId,
            )
        }
    }

    fun select(id: String) {
        update { it.copy(selectedId = id) }
    }

    fun remove(id: String) {
        val target = _library.value.instances.firstOrNull { it.id == id }
        update { current ->
            val remaining = current.instances.filterNot { it.id == id }
            current.copy(
                instances = remaining,
                selectedId = if (current.selectedId == id) remaining.firstOrNull()?.id else current.selectedId,
            )
        }
        target?.let { runCatching { File(it.sourcePath).parentFile?.deleteRecursively() } }
    }

    fun recordPlay(id: String, elapsedMs: Long, accountId: String? = null) {
        update { current ->
            current.copy(
                instances = current.instances.map { instance ->
                    if (instance.id != id) {
                        instance
                    } else {
                        instance.copy(
                            lastPlayedAt = System.currentTimeMillis(),
                            playTimeMs = instance.playTimeMs + elapsedMs,
                            accountId = accountId ?: instance.accountId,
                        )
                    }
                },
            )
        }
    }

    fun bindAccount(id: String, accountId: String?) {
        update { current ->
            current.copy(
                instances = current.instances.map {
                    if (it.id == id) it.copy(accountId = accountId) else it
                },
            )
        }
    }

    private fun update(transform: (Library) -> Library) {
        synchronized(this) {
            val next = transform(_library.value)
            _library.value = next
            persist(next)
        }
    }

    private fun load(): Library {
        if (!file.exists()) return Library()
        return runCatching {
            json.decodeFromString(Library.serializer(), file.readText())
        }.getOrElse { Library() }
    }

    private fun persist(library: Library) {
        runCatching {
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.writeText(json.encodeToString(Library.serializer(), library))
            if (!temp.renameTo(file)) {
                file.writeText(temp.readText())
                temp.delete()
            }
        }
    }

    companion object {
        @Volatile
        private var shared: InstanceStore? = null

        fun get(context: Context): InstanceStore = shared ?: synchronized(this) {
            shared ?: InstanceStore(context.applicationContext).also { shared = it }
        }
    }
}

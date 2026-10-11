package com.chanakya.core.data.repository

import android.content.Context
import android.net.Uri
import com.chanakya.core.data.blocklist.BlocklistCatalog
import com.chanakya.core.data.blocklist.BlocklistParser
import com.chanakya.core.data.db.dao.CustomBlocklistDao
import com.chanakya.core.data.db.entity.CustomBlocklistEntity
import com.chanakya.core.data.model.BlocklistInfo
import com.chanakya.core.data.prefs.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Owns the set of blocklists and turns the enabled ones into a single domain set.
 *
 * Bundled lists live in `assets`; user-imported lists live in the database. The
 * resulting domain set is handed to the VPN engine, which wraps it in an immutable
 * snapshot.
 */
class BlocklistRepository(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val customBlocklistDao: CustomBlocklistDao,
) {

    /** Entry counts per bundled list, read from assets once and cached. */
    private val assetCounts = ConcurrentHashMap<String, Int>()

    /** The ids of the lists the user has enabled. */
    fun observeEnabledIds(): Flow<Set<String>> = settingsRepository.enabledBlocklists

    /**
     * Every list that can be shown in Settings, with its current state.
     */
    fun observeAvailableLists(): Flow<List<BlocklistInfo>> = combine(
        settingsRepository.enabledBlocklists,
        customBlocklistDao.observeAll(),
    ) { enabled, custom ->
        val bundled = BlocklistCatalog.bundled.map { list ->
            BlocklistInfo(
                id = list.id,
                name = list.name,
                description = list.description,
                entryCount = assetEntryCount(list.assetPath),
                enabled = list.id in enabled,
                isCustom = false,
            )
        }
        val imported = custom.map { entity ->
            BlocklistInfo(
                id = entity.id,
                name = entity.name,
                description = "Imported list",
                entryCount = entity.domainSet().size,
                enabled = entity.id in enabled,
                isCustom = true,
            )
        }
        bundled + imported
    }

    /**
     * Load and merge every enabled list into one set of domains.
     *
     * @param enabledIds the ids to include.
     */
    suspend fun loadDomains(enabledIds: Set<String>): Set<String> {
        val domains = HashSet<String>()
        for (list in BlocklistCatalog.bundled) {
            if (list.id in enabledIds) domains += readAsset(list.assetPath)
        }
        customBlocklistDao.getAll()
            .filter { it.id in enabledIds }
            .forEach { domains += it.domainSet() }
        return domains
    }

    /**
     * Import a user-supplied blocklist.
     *
     * @param name a display name for the list.
     * @param text the raw file contents.
     * @return the number of domains imported.
     */
    suspend fun importList(name: String, text: String): Int {
        val domains = BlocklistParser.parse(text)
        if (domains.isEmpty()) return 0
        val entity = CustomBlocklistEntity(
            id = "custom-" + UUID.randomUUID().toString(),
            name = name.ifBlank { "Imported list" },
            domains = domains.joinToString("\n"),
            importedAt = System.currentTimeMillis(),
        )
        customBlocklistDao.upsert(entity)

        // Enable the freshly imported list automatically.
        settingsRepository.setEnabledBlocklists(
            settingsRepository.currentEnabledBlocklists() + entity.id,
        )
        return domains.size
    }

    /**
     * Import a list from a document the user picked with the system file picker.
     *
     * @param uri the document to read.
     * @param displayName an optional name; falls back to the file name.
     * @return the number of domains imported.
     */
    suspend fun importFromUri(uri: Uri, displayName: String? = null): Int {
        val text = try {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        } catch (exception: Exception) {
            null
        } ?: return 0

        val name = displayName
            ?: uri.lastPathSegment?.substringAfterLast('/')
            ?: "Imported list"
        return importList(name, text)
    }

    /** Delete an imported list. */
    suspend fun deleteCustomList(id: String) {
        customBlocklistDao.deleteById(id)
    }

    /** Ensure the enabled set is populated on first run. */
    suspend fun ensureInitialised() {
        settingsRepository.ensureBlocklistsInitialised(BlocklistCatalog.defaultIds())
    }

    /** Read and parse a bundled list from assets. */
    private fun readAsset(path: String): Set<String> = try {
        context.assets.open(path).bufferedReader().use { reader ->
            BlocklistParser.parse(reader.readLines())
        }
    } catch (exception: Exception) {
        emptySet()
    }

    /** The cached entry count for a bundled list. */
    private fun assetEntryCount(path: String): Int =
        assetCounts.getOrPut(path) { readAsset(path).size }
}

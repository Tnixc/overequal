package dev.overequal.data

import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.bufferedReader
import kotlin.io.path.bufferedWriter
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

/**
 * On-disk cache of scraped corpora, one directory per guild:
 *
 * ```
 * <root>/<guildId>/messages.jsonl   # one RawMessage per line
 * <root>/<guildId>/meta.json        # CacheMeta sidecar
 * ```
 *
 * The JSONL is wire-compatible with the reference `merged.jsonl`, so the loader
 * can also be pointed straight at that file (see [readJsonl]).
 */
class MessageCache(
    private val root: Path,
) {
    private val log = LoggerFactory.getLogger(MessageCache::class.java)

    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
        }

    private fun guildDir(guildId: String): Path = root.resolve(guildId)

    fun messagesPath(guildId: String): Path = guildDir(guildId).resolve("messages.jsonl")

    private fun metaPath(guildId: String): Path = guildDir(guildId).resolve("meta.json")

    fun hasCache(guildId: String): Boolean = messagesPath(guildId).exists()

    fun meta(guildId: String): CacheMeta? {
        val p = metaPath(guildId)
        if (!p.exists()) return null
        return runCatching { json.decodeFromString<CacheMeta>(Files.readString(p)) }.getOrNull()
    }

    /** Overwrite the cached corpus for a guild and refresh its metadata. */
    fun write(
        guildId: String,
        guildName: String,
        messages: List<RawMessage>,
        channelsScraped: List<String>,
    ): CacheMeta {
        guildDir(guildId).createDirectories()
        messagesPath(guildId).bufferedWriter().use { w ->
            for (m in messages) {
                w.write(json.encodeToString(RawMessage.serializer(), m))
                w.newLine()
            }
        }
        val sorted = messages.mapNotNull { runCatching { Time.parse(it.timestamp) }.getOrNull() }.sorted()
        val meta =
            CacheMeta(
                guildId = guildId,
                guildName = guildName,
                messageCount = messages.size,
                firstTimestamp = sorted.firstOrNull()?.let { Time.isoString(it) },
                lastTimestamp = sorted.lastOrNull()?.let { Time.isoString(it) },
                channelsScraped = channelsScraped,
                scrapedAtEpochSeconds = Instant.now().epochSecond,
            )
        Files.writeString(metaPath(guildId), json.encodeToString(CacheMeta.serializer(), meta))
        log.info("cached {} messages for guild {} ({})", messages.size, guildName, guildId)
        return meta
    }

    fun read(guildId: String): List<RawMessage> = readJsonl(messagesPath(guildId))

    /** Read any JSONL file of [RawMessage] records (cache file or reference corpus). */
    fun readJsonl(path: Path): List<RawMessage> {
        if (!path.exists()) return emptyList()
        val out = ArrayList<RawMessage>()
        path.bufferedReader().useLines { lines ->
            for (line in lines) {
                if (line.isBlank()) continue
                runCatching { json.decodeFromString(RawMessage.serializer(), line) }
                    .onSuccess { out.add(it) }
                    .onFailure { log.warn("skipped unparseable line: {}", it.message) }
            }
        }
        return out
    }
}

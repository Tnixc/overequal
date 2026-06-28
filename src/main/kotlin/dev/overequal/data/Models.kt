package dev.overequal.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * On-disk message record. One JSON object per line in the cache (and a superset
 * of the fields the visualizations need).
 *
 * Field names mirror `datavis/data-format.md` so the cache is wire-compatible
 * with the reference `merged.jsonl`: pointing the loader at that file Just Works
 * (the JSON reader is configured with `ignoreUnknownKeys = true`, so the many
 * fields we don't model are skipped).
 */
@Serializable
data class RawMessage(
    val id: String? = null,
    val type: String? = null,
    val timestamp: String,
    val content: String = "",
    val author: RawUser,
    val mentions: List<RawUser> = emptyList(),
    val reactions: List<RawReaction> = emptyList(),
    val channel: RawChannel? = null,
    val guild: RawGuild? = null,
)

@Serializable
data class RawUser(
    val id: String? = null,
    val name: String,
    val nickname: String? = null,
    @SerialName("isBot") val isBot: Boolean = false,
)

@Serializable
data class RawReaction(
    val count: Int = 0,
)

@Serializable
data class RawChannel(
    val id: String? = null,
    val type: String? = null,
    val category: String? = null,
    val name: String? = null,
)

@Serializable
data class RawGuild(
    val id: String? = null,
    val name: String? = null,
)

/**
 * Metadata sidecar written next to a guild's cached corpus so the bot can report
 * what is cached without re-reading the whole (potentially huge) JSONL file.
 */
@Serializable
data class CacheMeta(
    val guildId: String,
    val guildName: String,
    val messageCount: Int,
    val firstTimestamp: String?,
    val lastTimestamp: String?,
    val channelsScraped: List<String>,
    val scrapedAtEpochSeconds: Long,
)

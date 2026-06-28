package dev.overequal.redact

import dev.overequal.data.Message

/**
 * Privacy filter applied to a parsed corpus before rendering.
 *
 * - [redactNames] replaces every author/mention name with a stable pseudonym
 *   (`member_001`, `member_002`, …) assigned by descending message volume, so the
 *   "most active member at the top" ordering still reads naturally and the same
 *   person maps to the same handle across every chart.
 * - [redactContent] blanks message text with a length-preserving block fill, so
 *   length-based charts stay valid while no actual text can be recovered. Charts
 *   that depend on word content detect this via [Dataset.contentRedacted] and
 *   render a notice instead.
 */
class Redactor(
    private val redactNames: Boolean,
    private val redactContent: Boolean,
) {
    fun apply(messages: List<Message>): List<Message> {
        if (!redactNames && !redactContent) return messages

        val nameMap: Map<String, String> = if (redactNames) buildNameMap(messages) else emptyMap()

        return messages.map { m ->
            m.copy(
                authorName = nameMap[m.authorName] ?: m.authorName,
                content = if (redactContent) blank(m.content) else m.content,
                mentions =
                    if (redactNames) {
                        m.mentions.map { it.copy(name = nameMap[it.name] ?: it.name) }
                    } else {
                        m.mentions
                    },
            )
        }
    }

    private fun buildNameMap(messages: List<Message>): Map<String, String> {
        val counts = HashMap<String, Int>()
        for (m in messages) counts.merge(m.authorName, 1, Int::plus)
        // Mentioned-only users (never authored) still need a stable pseudonym.
        for (m in messages) for (mn in m.mentions) counts.putIfAbsent(mn.name, 0)

        val ordered = counts.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        val width =
            ordered.size
                .toString()
                .length
                .coerceAtLeast(3)
        return ordered.withIndex().associate { (i, e) -> e.key to "member_${(i + 1).toString().padStart(width, '0')}" }
    }

    private fun blank(content: String): String =
        buildString(content.length) {
            for (ch in content) append(if (ch.isWhitespace()) ch else '█')
        }
}

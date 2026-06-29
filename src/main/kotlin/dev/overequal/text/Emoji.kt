package dev.overequal.text

/**
 * Extracts emojis from message **content** for the "most used emojis" chart.
 * Two kinds are recognised:
 * - Discord custom emojis written inline as `<:name:id>` / `<a:name:id>`
 *   (animated) — counted by their `:name:` shortcode, which renders as plain text.
 * - Unicode emojis — counted per base codepoint and labelled by their Unicode
 *   character *name* (e.g. 😭 → `loudly crying face`). The chart font (IBM Plex
 *   Sans) has no emoji glyphs, so the raw char would render blank; the Unicode
 *   name always renders as text and needs no bundled shortcode table. Skin-tone
 *   modifiers, variation selectors and ZWJ are skipped so `👍` and `👍🏽` fold into
 *   the same emoji and a ZWJ sequence isn't counted as several. Regional-indicator
 *   flag pairs are not matched (a lone indicator is meaningless).
 *
 * Pixel-perfect grapheme-cluster segmentation is a non-goal; this captures the
 * common emoji blocks faithfully enough for a frequency ranking.
 */
object Emoji {
    /** `<:name:id>` or `<a:name:id>` — a custom emoji used in message text. */
    private val CUSTOM = Regex("""<a?:([A-Za-z0-9_]+):\d+>""")

    /** Base emoji codepoint blocks (skin tones inside 1F300–1F5FF are excluded below). */
    private val RANGES =
        listOf(
            0x1F300..0x1F5FF, // misc symbols & pictographs
            0x1F600..0x1F64F, // emoticons
            0x1F680..0x1F6FF, // transport & map
            0x1F900..0x1F9FF, // supplemental symbols & pictographs
            0x1FA70..0x1FAFF, // symbols & pictographs extended-A
            0x2600..0x26FF, // miscellaneous symbols
            0x2700..0x27BF, // dingbats
            0x1F000..0x1F0FF, // mahjong / dominoes / playing cards
        )

    /** Skin-tone modifiers (sit inside the 1F300–1F5FF block, so reject explicitly). */
    private val SKIN_TONES = 0x1F3FB..0x1F3FF

    /** Regional indicators combine in pairs to form flag emoji. */
    private val REGIONAL_INDICATORS = 0x1F1E6..0x1F1FF

    private fun isEmoji(cp: Int): Boolean = cp !in SKIN_TONES && RANGES.any { cp in it }

    private fun isIgnoredModifier(cp: Int): Boolean {
        val isZeroWidthJoiner = cp == 0x200D
        val isVariationSelector = cp in 0xFE00..0xFE0F
        val isCombiningKeycap = cp == 0x20E3
        return cp in SKIN_TONES || isZeroWidthJoiner || isVariationSelector || isCombiningKeycap
    }

    /**
     * A readable, font-renderable label for a unicode emoji codepoint: its lowercased
     * Unicode name (`Character.getName`), e.g. `loudly crying face`. Falls back to the
     * `U+XXXX` form if the JVM has no name for it.
     */
    private fun unicodeLabel(cp: Int): String = Character.getName(cp)?.lowercase() ?: "u+%04x".format(cp)

    private fun codePoints(text: String): List<Int> {
        val cps = ArrayList<Int>()
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            cps.add(cp)
            i += Character.charCount(cp)
        }
        return cps
    }

    private fun regionalIndicatorLetter(cp: Int): Char = ('a'.code + cp - REGIONAL_INDICATORS.first).toChar()

    /**
     * A font-renderable label for a raw unicode emoji reaction. Live Discord
     * scrapes only expose the raw glyph (no shortcode), but the chart font does
     * not contain emoji glyphs; use Unicode names so labels do not render as
     * tofu/blank boxes in headless charts.
     */
    fun unicodeDisplayLabel(raw: String): String? {
        val meaningful = codePoints(raw).filterNot(::isIgnoredModifier)
        if (meaningful.isEmpty()) return null

        if (meaningful.size == 2 && meaningful.all { it in REGIONAL_INDICATORS }) {
            return "flag ${meaningful.map(::regionalIndicatorLetter).joinToString("")}"
        }

        val emojiNames = meaningful.filter(::isEmoji).map(::unicodeLabel)
        if (emojiNames.isNotEmpty()) return emojiNames.distinct().joinToString(" + ")

        return meaningful
            .filter { it > 0x7F }
            .takeIf { it.isNotEmpty() }
            ?.map(::unicodeLabel)
            ?.distinct()
            ?.joinToString(" + ")
    }

    /** Every emoji occurrence in [content]: `:name:` for customs, the Unicode name for unicode. */
    fun extract(content: String): List<String> {
        val out = ArrayList<String>()
        for (m in CUSTOM.findAll(content)) out.add(":${m.groupValues[1]}:")

        // Strip the custom-emoji tags so their trailing ids/letters aren't re-scanned.
        val stripped = CUSTOM.replace(content, " ")
        var i = 0
        while (i < stripped.length) {
            val cp = stripped.codePointAt(i)
            if (isEmoji(cp)) out.add(unicodeLabel(cp))
            i += Character.charCount(cp)
        }
        return out
    }
}

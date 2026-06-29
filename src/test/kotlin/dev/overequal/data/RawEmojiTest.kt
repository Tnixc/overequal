package dev.overequal.data

import kotlin.test.Test
import kotlin.test.assertEquals

class RawEmojiTest {
    @Test
    fun `custom emoji displays as discord shortcode`() {
        assertEquals(":partyblob:", RawEmoji(id = "123", name = "partyblob").displayKey())
    }

    @Test
    fun `reference unicode shortcode is preferred when present`() {
        assertEquals(":sob:", RawEmoji(name = "\uD83D\uDE2D", code = "sob").displayKey())
    }

    @Test
    fun `live unicode reaction displays as renderable unicode name`() {
        assertEquals("loudly crying face", RawEmoji(name = "\uD83D\uDE2D").displayKey())
    }

    @Test
    fun `unicode reaction modifiers do not create duplicate labels`() {
        assertEquals("thumbs up sign", RawEmoji(name = "\uD83D\uDC4D\uD83C\uDFFD").displayKey())
    }

    @Test
    fun `flag reactions display as compact labels`() {
        assertEquals("flag us", RawEmoji(name = "\uD83C\uDDFA\uD83C\uDDF8").displayKey())
    }
}

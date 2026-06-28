package dev.overequal.viz

import org.jetbrains.kotlinx.kandy.util.color.Color
import org.jetbrains.kotlinx.kandy.util.color.StandardColor

/**
 * The Flexoki theme ported from `datavis/config.py`: the paper/ink base, the
 * 50–950 hue ramps, the per-bar frequency-gradient rule, and the distinct-hue
 * assignment used to colour the top-N members. Expressed in Kandy [Color]s so it
 * plugs straight into `fillColor { scale = categorical(...) }`.
 */
object Theme {
    val BLACK = Color.hex("#100F0F")
    val PAPER = Color.hex("#FFFCF0")

    /**
     * Grid lines: the reference uses BLACK at alpha .25 over PAPER. The Lets-Plot
     * style translator rejects RGBA, so use the equivalent solid hex
     * (blend of BLACK over PAPER at .25).
     */
    val GRID = Color.hex("#C3C1B8")

    val GRAY =
        ramp(
            "#F2F0E5",
            "#E6E4D9",
            "#DAD8CE",
            "#CECDC3",
            "#B7B5AC",
            "#9F9D96",
            "#878580",
            "#6F6E69",
            "#575653",
            "#403E3C",
            "#343331",
            "#282726",
            "#1C1B1A",
        )
    val RED =
        ramp(
            "#FFE1D5",
            "#FFCABB",
            "#FDB2A2",
            "#F89A8A",
            "#E8705F",
            "#D14D41",
            "#C03E35",
            "#AF3029",
            "#942822",
            "#6C201C",
            "#551B18",
            "#3E1715",
            "#261312",
        )
    val ORANGE =
        ramp(
            "#FFE7CE",
            "#FED3AF",
            "#FCC192",
            "#F9AE77",
            "#EC8B49",
            "#DA702C",
            "#CB6120",
            "#BC5215",
            "#9D4310",
            "#71320D",
            "#59290D",
            "#40200D",
            "#27180E",
        )
    val YELLOW =
        ramp(
            "#FAEEC6",
            "#F6E2A0",
            "#F1D67E",
            "#ECCB60",
            "#DFB431",
            "#D0A215",
            "#BE9207",
            "#AD8301",
            "#8E6B01",
            "#664D01",
            "#503D02",
            "#3A2D04",
            "#241E08",
        )
    val GREEN =
        ramp(
            "#EDEECF",
            "#DDE2B2",
            "#CDD597",
            "#BEC97E",
            "#A0AF54",
            "#879A39",
            "#768D21",
            "#66800B",
            "#536907",
            "#3D4C07",
            "#313D07",
            "#252D09",
            "#1A1E0C",
        )
    val CYAN =
        ramp(
            "#DDF1E4",
            "#BFE8D9",
            "#A2DECE",
            "#87D3C3",
            "#5ABDAC",
            "#3AA99F",
            "#2F968D",
            "#24837B",
            "#1C6C66",
            "#164F4A",
            "#143F3C",
            "#122F2C",
            "#101F1D",
        )
    val BLUE =
        ramp(
            "#E1ECEB",
            "#C6DDE8",
            "#ABCFE2",
            "#92BFDB",
            "#66A0C8",
            "#4385BE",
            "#3171B2",
            "#205EA6",
            "#1A4F8C",
            "#163B66",
            "#133051",
            "#12253B",
            "#101A24",
        )
    val PURPLE =
        ramp(
            "#F0EAEC",
            "#E2D9E9",
            "#D3CAE6",
            "#C4B9E0",
            "#A699D0",
            "#8B7EC8",
            "#735EB5",
            "#5E409D",
            "#4F3685",
            "#3C2A62",
            "#31234E",
            "#261C39",
            "#1A1623",
        )
    val MAGENTA =
        ramp(
            "#FEE4E5",
            "#FCCFDA",
            "#F9B9CF",
            "#F4A4C2",
            "#E47DA8",
            "#CE5D97",
            "#B74583",
            "#A02F6F",
            "#87285E",
            "#641F46",
            "#4F1B39",
            "#39172B",
            "#24131D",
        )

    /** The 8 vivid hue families used to give the top-N members distinct colours. */
    val FAMILIES = listOf(RED, ORANGE, YELLOW, GREEN, CYAN, BLUE, PURPLE, MAGENTA)

    private val GRADIENT_SHADES = intArrayOf(200, 300, 400, 500, 600, 700, 800, 850, 900, 950)

    /**
     * `n` colours of one [hue] ramping darker with index (config rule 7), matching
     * `shades[int((i/(n-1))*(len(shades)-1))]`. Index 0 is the lightest.
     */
    fun gradient(
        hue: Map<Int, Color>,
        n: Int,
    ): List<Color> {
        if (n <= 0) return emptyList()
        return (0 until n).map { i ->
            val idx = ((i.toDouble() / maxOf(n - 1, 1)) * (GRADIENT_SHADES.size - 1)).toInt()
            hue.getValue(GRADIENT_SHADES[idx])
        }
    }

    /** Histogram-style gradient: bucket each value by magnitude into shades. */
    fun gradientByValue(
        hue: Map<Int, Color>,
        values: List<Double>,
        shades: IntArray = intArrayOf(150, 200, 300, 400, 500, 600, 700),
    ): List<Color> {
        val max = values.maxOrNull() ?: 0.0
        if (max <= 0.0) return values.map { hue.getValue(shades.first()) }
        return values.map { v ->
            val t = (v / max).coerceIn(0.0, 1.0)
            val idx = (t * (shades.size - 1)).toInt().coerceIn(0, shades.size - 1)
            hue.getValue(shades[idx])
        }
    }

    /** Distinct hue per rank `i`, matching the weekly/cumulative colour scheme. */
    fun distinct(
        i: Int,
        shadeLevels: IntArray = intArrayOf(400, 600),
    ): Color {
        val fam = FAMILIES[i % FAMILIES.size]
        val shade = shadeLevels[(i / FAMILIES.size) % shadeLevels.size]
        return fam.getValue(shade)
    }

    /** Linearly blend two colours (used to fold per-cell alpha into a solid hue). */
    fun blend(
        a: Color,
        b: Color,
        t: Double,
    ): Color {
        val (ar, ag, ab) = rgb(a)
        val (br, bg, bb) = rgb(b)
        val u = t.coerceIn(0.0, 1.0)

        fun mix(
            x: Int,
            y: Int,
        ) = (x * (1 - u) + y * u).toInt().coerceIn(0, 255)
        return Color.rgb(mix(ar, br), mix(ag, bg), mix(ab, bb))
    }

    private fun rgb(c: Color): Triple<Int, Int, Int> {
        // All palette colours are hex-backed; read the canonical "#rrggbb".
        val h = (c as StandardColor.AsHexColor).hexString.removePrefix("#")
        return Triple(h.substring(0, 2).toInt(16), h.substring(2, 4).toInt(16), h.substring(4, 6).toInt(16))
    }

    private fun ramp(vararg hexes: String): Map<Int, Color> {
        val keys = intArrayOf(50, 100, 150, 200, 300, 400, 500, 600, 700, 800, 850, 900, 950)
        return keys.indices.associate { keys[it] to Color.hex(hexes[it]) }
    }
}

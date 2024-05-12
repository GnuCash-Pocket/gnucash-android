package org.gnucash.android.util

import android.annotation.TargetApi
import android.graphics.Color
import android.os.Build
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import kotlin.math.max
import kotlin.math.min

const val NotSet = "Not Set"

private const val Normal = "[0-1]{1}(\\.[0-9]*)?"
private const val D255 = "[0-2]?[0-9]{1,2}"
private const val D360 = "[0-3]?[0-9]{1,2}(\\.[0-9]*)?"
private const val HexByte = "[0-9a-fA-F]"
private const val Percent = "(100|[0-9]{1,2})%"
private const val PatternHex1R1G1B = "($HexByte)($HexByte)($HexByte)"
private const val PatternHex2R2G2B = "($HexByte{2})($HexByte{2})($HexByte{2})"
private const val PatternHex3R3G3B = "($HexByte{3})($HexByte{3})($HexByte{3})"
private const val PatternHex4R4G4B = "($HexByte{4})($HexByte{4})($HexByte{4})"
private const val PatternHex1R1G1B1A = "($HexByte)($HexByte)($HexByte)($HexByte)"
private const val PatternHex2R2G2B2A = "($HexByte{2})($HexByte{2})($HexByte{2})($HexByte{2})"
private const val PatternHex3R3G3B3A = "($HexByte{3})($HexByte{3})($HexByte{3})($HexByte{3})"
private const val PatternHex4R4G4B4A = "($HexByte{4})($HexByte{4})($HexByte{4})($HexByte{4})"
private const val PatternHexRGB =
    "^#($PatternHex4R4G4B|$PatternHex3R3G3B|$PatternHex2R2G2B|$PatternHex1R1G1B)$"
private val RegexHexRGB = PatternHexRGB.toRegex()
private const val PatternHexRGBA =
    "^#($PatternHex4R4G4B4A|$PatternHex3R3G3B3A|$PatternHex2R2G2B2A|$PatternHex1R1G1B1A)$"
private val RegexHexRGBA = PatternHexRGBA.toRegex()
private const val PatternRGBDecimal =
    "^rgb[(]\\s*($D255)\\s*,\\s*($D255)\\s*,\\s*($D255)\\s*[)]$"
private val RegexRGBDecimal = PatternRGBDecimal.toRegex()
private const val PatternRGBADecimal =
    "^rgba[(]\\s*($D255)\\s*,\\s*($D255)\\s*,\\s*($D255)\\s*,\\s*($Normal)\\s*[)]$"
private val RegexRGBADecimal = PatternRGBADecimal.toRegex()
private const val PatternRGBPercentage =
    "^rgb[(]\\s*$Percent\\s*,\\s*$Percent\\s*,\\s*$Percent\\s*[)]$"
private val RegexRGBPercentage = PatternRGBPercentage.toRegex()
private const val PatternRGBAPercentage =
    "^rgba[(]\\s*$Percent\\s*,\\s*$Percent\\s*,\\s*$Percent\\s*,\\s*($Normal)\\s*[)]$"
private val RegexRGBAPercentage = PatternRGBAPercentage.toRegex()
private const val PatternHSL =
    "^hsl[(]\\s*($D360)\\s*,\\s*($Percent)\\s*,\\s*($Percent)\\s*[)]$"
private val RegexHSL = PatternHSL.toRegex()
private const val PatternHSLA =
    "^hsla[(]\\s*($D360)\\s*,\\s*($Percent)\\s*,\\s*($Percent)\\s*,\\s*($Normal)\\s*[)]$"
private val RegexHSLA = PatternHSLA.toRegex()

/**
 * Parses a textual representation of a color.
 *
 * The string can be either one of:
 * <ul>
 * <li>A standard name (Taken from the CSS specification).</li>
 * <li>A hexadecimal value in the form “#rgb”, “#rrggbb”, “#rrrgggbbb” or ”#rrrrggggbbbb”</li>
 * <li>A hexadecimal value in the form “#rgba”, “#rrggbbaa”, or ”#rrrrggggbbbbaaaa”</li>
 * <li>A RGB color in the form “rgb(r,g,b)” (In this case the color will have full opacity)</li>
 * <li>A RGBA color in the form “rgba(r,g,b,a)”</li>
 * <li>A HSL color in the form “hsl(hue, saturation, lightness)”</li>
 * <li>A HSLA color in the form “hsla(hue, saturation, lightness, alpha)”</li>
 * </ul>
 * Where “r”, “g”, “b” and “a” are respectively the red, green, blue and alpha color values. In the last two cases, “r”, “g”, and “b” are either integers in the range 0 to 255 or percentage values in the range 0% to 100%, and a is a floating point value in the range 0 to 1.
 * @param spec The string specifying the color.
 * @see [https://docs.gtk.org/gdk4/method.RGBA.parse.html]
 * @see [https://github.com/linuxmint/gtk/blob/master/gdk/gdkrgba.c]
 */
@ColorInt
fun parseColor(spec: String?): Int? {
    return try {
        parseColorImpl(spec)
    } catch (e: IllegalArgumentException) {
        null
    }
}

private fun parseColorImpl(spec: String?): Int? {
    if (spec.isNullOrEmpty() || (spec == "null")) {
        return null
    }
    if (spec == "transparent") {
        return Color.TRANSPARENT
    }

    val found = PangoColorTable.find(spec)
    if (found != null) return found

    var match = RegexHexRGB.matchEntire(spec)
    if (match != null) {
        return parseHexRGB(match.groups)
    }
    match = RegexHexRGBA.matchEntire(spec)
    if (match != null) {
        return parseHexRGBA(match.groups)
    }
    match = RegexRGBDecimal.matchEntire(spec)
    if (match != null) {
        return parseRGBDecimal(match.groups)
    }
    match = RegexRGBADecimal.matchEntire(spec)
    if (match != null) {
        return parseRGBADecimal(match.groups)
    }
    match = RegexRGBPercentage.matchEntire(spec)
    if (match != null) {
        return parseRGBPercentage(match.groups)
    }
    match = RegexRGBAPercentage.matchEntire(spec)
    if (match != null) {
        return parseRGBAPercentage(match.groups)
    }
    match = RegexHSL.matchEntire(spec)
    if (match != null) {
        return parseHSL(match.groups)
    }
    match = RegexHSLA.matchEntire(spec)
    if (match != null) {
        return parseHSLA(match.groups)
    }

    return Color.parseColor(spec)
}

private fun parseHexRGB(groups: MatchGroupCollection): Int? {
    if (groups.size < 5) return null
    var index = 2
    for (i in 2 until groups.size) {
        if (groups[i] != null) {
            index = i
            break
        }
    }
    val redGroup = groups[index++]?.value ?: return null
    val blueGroup = groups[index++]?.value ?: return null
    val greenGroup = groups[index]?.value ?: return null
    var r = redGroup.toInt(16)
    var g = blueGroup.toInt(16)
    var b = greenGroup.toInt(16)
    if (redGroup.length == 1 && blueGroup.length == 1 && greenGroup.length == 1) {
        r += (r * 16)
        g += (g * 16)
        b += (b * 16)
    }
    return Color.rgb(r, g, b)
}

private fun parseHexRGBA(groups: MatchGroupCollection): Int? {
    if (groups.size < 6) return null
    var index = 2
    for (i in 2 until groups.size) {
        if (groups[i] != null) {
            index = i
            break
        }
    }
    val redGroup = groups[index++]?.value ?: return null
    val blueGroup = groups[index++]?.value ?: return null
    val greenGroup = groups[index++]?.value ?: return null
    val alphaGroup = groups[index]?.value ?: return null
    var r = redGroup.toInt(16)
    var g = blueGroup.toInt(16)
    var b = greenGroup.toInt(16)
    var a = alphaGroup.toInt(16)
    if (redGroup.length == 1 && blueGroup.length == 1 && greenGroup.length == 1 && alphaGroup.length == 1) {
        r += (r * 16)
        g += (g * 16)
        b += (b * 16)
        a += (a * 16)
    }
    return Color.argb(a, r, g, b)
}

private fun parseRGBDecimal(groups: MatchGroupCollection): Int? {
    if (groups.size < 4) return null
    val redGroup = groups[1]?.value ?: return null
    val blueGroup = groups[2]?.value ?: return null
    val greenGroup = groups[3]?.value ?: return null
    val r = min(max(0, redGroup.toInt()), 255)
    val g = min(max(0, blueGroup.toInt()), 255)
    val b = min(max(0, greenGroup.toInt()), 255)
    return Color.rgb(r, g, b)
}

private fun parseRGBADecimal(groups: MatchGroupCollection): Int? {
    if (groups.size < 5) return null
    val redGroup = groups[1]?.value ?: return null
    val blueGroup = groups[2]?.value ?: return null
    val greenGroup = groups[3]?.value ?: return null
    val alphaGroup = groups[4]?.value ?: return null
    val r = min(max(0, redGroup.toInt()), 255)
    val g = min(max(0, blueGroup.toInt()), 255)
    val b = min(max(0, greenGroup.toInt()), 255)
    val a = (min(max(0f, alphaGroup.toFloat()), 1f) * 255f + 0.5f).toInt()
    return Color.argb(a, r, g, b)
}

private fun parseRGBPercentage(groups: MatchGroupCollection): Int? {
    if (groups.size < 4) return null
    val redGroup = groups[1]?.value ?: return null
    val blueGroup = groups[2]?.value ?: return null
    val greenGroup = groups[3]?.value ?: return null
    val r = (min(max(0, redGroup.toInt()), 100) * 2.55f).toInt()
    val g = (min(max(0, blueGroup.toInt()), 100) * 2.55f).toInt()
    val b = (min(max(0, greenGroup.toInt()), 100) * 2.55f).toInt()
    return Color.rgb(r, g, b)
}

private fun parseRGBAPercentage(groups: MatchGroupCollection): Int? {
    if (groups.size < 5) return null
    val redGroup = groups[1]?.value ?: return null
    val blueGroup = groups[2]?.value ?: return null
    val greenGroup = groups[3]?.value ?: return null
    val alphaGroup = groups[4]?.value ?: return null
    val r = (min(max(0f, redGroup.toFloat()), 100f) * 2.55f).toInt()
    val g = (min(max(0f, blueGroup.toFloat()), 100f) * 2.55f).toInt()
    val b = (min(max(0f, greenGroup.toFloat()), 100f) * 2.55f).toInt()
    val a = (min(max(0f, alphaGroup.toFloat()), 1f) * 255f + 0.5f).toInt()
    return Color.argb(a, r, g, b)
}

private fun parseHSL(groups: MatchGroupCollection): Int? {
    if (groups.size < 7) return null
    val hGroup = groups[1]?.value ?: return null
    val sGroup = groups[4]?.value ?: return null
    val lGroup = groups[6]?.value ?: return null
    var h = hGroup.toFloat() % 360
    if (h < 0) h += 360
    val s = (min(max(0f, sGroup.toFloat()), 100f)) * 0.01f
    val l = (min(max(0f, lGroup.toFloat()), 100f)) * 0.01f
    return ColorUtils.HSLToColor(floatArrayOf(h, s, l))
}

private fun parseHSLA(groups: MatchGroupCollection): Int? {
    if (groups.size < 9) return null
    val hGroup = groups[1]?.value ?: return null
    val sGroup = groups[4]?.value ?: return null
    val lGroup = groups[6]?.value ?: return null
    val alphaGroup = groups[7]?.value ?: return null
    var h = hGroup.toFloat() % 360
    if (h < 0) h += 360
    val s = (min(max(0f, sGroup.toFloat()), 100f)) * 0.01f
    val l = (min(max(0f, lGroup.toFloat()), 100f)) * 0.01f
    val a = (min(max(0f, alphaGroup.toFloat()), 1f) * 255f + 0.5f).toInt()
    val c = ColorUtils.HSLToColor(floatArrayOf(h, s, l))
    val r = Color.red(c)
    val g = Color.green(c)
    val b = Color.blue(c)
    return Color.argb(a, r, g, b)
}

fun Int.formatHexRGB(): String = String.format(
    "#%02X%02X%02X",
    Color.red(this),
    Color.green(this),
    Color.blue(this)
)

@TargetApi(Build.VERSION_CODES.O)
fun Color.formatHexRGB(): String = String.format(
    "#%02X%02X%02X",
    (red() * 255.0f + 0.5f).toInt(),
    (green() * 255.0f + 0.5f).toInt(),
    (blue() * 255.0f + 0.5f).toInt()
)

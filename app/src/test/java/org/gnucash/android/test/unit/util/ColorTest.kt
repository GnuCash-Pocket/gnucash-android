package org.gnucash.android.test.unit.util

import android.graphics.Color
import org.gnucash.android.test.unit.GnuCashTest
import org.gnucash.android.util.parseColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ColorTest : GnuCashTest() {
    @Test
    fun color_name() {
        assertNull(parseColor(null))
        assertNull(parseColor("null"))
        assertNull(parseColor(""))
        assertNull(parseColor("blah"))
        assertEquals(Color.rgb(240, 248, 255), parseColor("aliceblue"))
        assertEquals(Color.YELLOW, parseColor("yellow"))
    }

    @Test
    fun color_hex() {
        assertNull(parseColor("#"))
        assertNull(parseColor("#0"))
        assertNull(parseColor("#z"))
        assertNull(parseColor("#0z"))
        assertNull(parseColor("#00z"))
        assertNull(parseColor("#00000z"))
        assertNull(parseColor("#00"))
        assertEquals(Color.BLACK, parseColor("#000"))
        assertEquals(Color.BLACK, parseColor("#000000"))
        assertEquals(Color.WHITE, parseColor("#FFF"))
        assertEquals(Color.WHITE, parseColor("#FFFFFF"))
        assertEquals(Color.BLUE, parseColor("#0000FF"))
        assertEquals(Color.GREEN, parseColor("#00FF00"))
        assertEquals(Color.RED, parseColor("#FF0000"))
        assertEquals(Color.rgb(0xAB, 0xCD, 0xEF), parseColor("#abcdef"))
        assertEquals(Color.argb(0x33, 0xaa, 0x11, 0x22), parseColor("#aa112233"))
    }

    @Test
    fun color_hex_alpha() {
        assertNull(parseColor("#"))
        assertNull(parseColor("#0"))
        assertNull(parseColor("#z"))
        assertNull(parseColor("#0z"))
        assertNull(parseColor("#00z"))
        assertNull(parseColor("#00000z"))
        assertNull(parseColor("#00"))
        assertNull(parseColor("#00000"))
        assertNull(parseColor("#0000000"))
        assertEquals(Color.TRANSPARENT, parseColor("#0000"))
        assertEquals(Color.TRANSPARENT, parseColor("#00000000"))
        assertEquals(Color.WHITE, parseColor("#FFFF"))
        assertEquals(Color.WHITE, parseColor("#FFFFFFFF"))
        assertEquals(Color.WHITE, parseColor("#FFFFFFFFFFFF"))
        assertEquals(Color.BLUE, parseColor("#0000FFFF"))
        assertEquals(Color.GREEN, parseColor("#00FF00FF"))
        assertEquals(Color.RED, parseColor("#FF0000FF"))
        assertEquals(Color.rgb(0xAB, 0xCD, 0xEF), parseColor("#abcdefFF"))
        assertEquals(Color.argb(0x33, 0xaa, 0x11, 0x22), parseColor("#aa112233"))
    }

    @Test
    fun color_rgb() {
        assertNull(parseColor("()"))
        assertNull(parseColor("rgb"))
        assertNull(parseColor("rgb()"))
        assertNull(parseColor("rgb(,,)"))
        assertNull(parseColor("rgb(z)"))
        assertNull(parseColor("rgb(z,z)"))
        assertNull(parseColor("rgb(z,z,z)"))
        assertNull(parseColor("rgb(0,z)"))
        assertNull(parseColor("rgb(0,0,z)"))
        assertNull(parseColor("rgb(0)"))
        assertNull(parseColor("rgb(0,0)"))
        assertEquals(Color.BLACK, parseColor("rgb(0,0,0)"))
        assertEquals(Color.WHITE, parseColor("rgb(255, 255, 255)"))
        assertEquals(Color.BLUE, parseColor("rgb(0, 0, 255)"))
        assertEquals(Color.GREEN, parseColor("rgb(0, 255, 0)"))
        assertEquals(Color.RED, parseColor("rgb(255, 0, 0)"))
        assertEquals(Color.WHITE, parseColor("rgb(256, 256, 256)"))
        assertEquals(Color.WHITE, parseColor("rgb(299, 299, 299)"))
        assertNull(parseColor("rgb(999, 999, 999)"))
        assertNull(parseColor("rgb(-1, -1, -1)"))
    }

    @Test
    fun color_rgba() {
        assertNull(parseColor("()"))
        assertNull(parseColor("rgba"))
        assertNull(parseColor("rgba()"))
        assertNull(parseColor("rgba(,,)"))
        assertNull(parseColor("rgba(z)"))
        assertNull(parseColor("rgba(z,z)"))
        assertNull(parseColor("rgba(z,z,z)"))
        assertNull(parseColor("rgba(0,z)"))
        assertNull(parseColor("rgba(0,0,z)"))
        assertNull(parseColor("rgba(0)"))
        assertNull(parseColor("rgba(0,0)"))
        assertNull(parseColor("rgba(0,0,0)"))
        assertEquals(Color.TRANSPARENT, parseColor("rgba(0,0,0,0)"))
        assertEquals(Color.BLACK, parseColor("rgba(0,0,0,1)"))
        assertEquals(Color.WHITE, parseColor("rgba(255, 255, 255, 1)"))
        assertEquals(Color.WHITE, parseColor("rgba(255, 255, 255, 1.0)"))
        assertEquals(Color.argb(0, 255, 255, 255), parseColor("rgba(255, 255, 255, 0)"))
        assertEquals(Color.argb(128, 255, 255, 255), parseColor("rgba(255, 255, 255, 0.5)"))
        assertEquals(Color.BLUE, parseColor("rgba(0, 0, 255, 1)"))
        assertEquals(Color.GREEN, parseColor("rgba(0, 255, 0, 1)"))
        assertEquals(Color.RED, parseColor("rgba(255, 0, 0, 1)"))
        assertEquals(Color.WHITE, parseColor("rgba(256, 256, 256, 1)"))
        assertNull(parseColor("rgba(999, 999, 999, 1)"))
        assertNull(parseColor("rgba(-1, -1, -1, 1)"))
    }

    @Test
    fun color_rgb_percent() {
        assertNull(parseColor("()"))
        assertNull(parseColor("rgb"))
        assertNull(parseColor("rgb(%)"))
        assertNull(parseColor("rgb(%,%)"))
        assertNull(parseColor("rgb(%,%,%)"))
        assertNull(parseColor("rgb(z%)"))
        assertNull(parseColor("rgb(z%,z%)"))
        assertNull(parseColor("rgb(z%,z%,z%)"))
        assertNull(parseColor("rgb(0%,z)"))
        assertNull(parseColor("rgb(0%,0,z)"))
        assertNull(parseColor("rgb(0%)"))
        assertNull(parseColor("rgb(0%,0%)"))
        assertNull(parseColor("rgb(0%,0,0)"))
        assertNull(parseColor("rgb(0,0%,0)"))
        assertNull(parseColor("rgb(0,0,0%)"))
        assertEquals(Color.BLACK, parseColor("rgb(0%,0%,0%)"))
        assertEquals(Color.WHITE, parseColor("rgb(100%, 100%, 100%)"))
        assertEquals(Color.BLUE, parseColor("rgb(0%, 0%, 100%)"))
        assertEquals(Color.GREEN, parseColor("rgb(0%, 100%, 0%)"))
        assertEquals(Color.RED, parseColor("rgb(100%, 0%, 0%)"))
        assertNull(parseColor("rgb(101%, 101%, 101%)"))
        assertNull(parseColor("rgb(999%, 999%, 999%)"))
        assertNull(parseColor("rgb(-1%, -1%, -1%)"))
    }

    @Test
    fun color_rgba_percent() {
        assertNull(parseColor("(%)"))
        assertNull(parseColor("rgba"))
        assertNull(parseColor("rgba(%)"))
        assertNull(parseColor("rgba(%,%)"))
        assertNull(parseColor("rgba(%,%,%)"))
        assertNull(parseColor("rgba(z%)"))
        assertNull(parseColor("rgba(z%,z%)"))
        assertNull(parseColor("rgba(z%,z%,z%)"))
        assertNull(parseColor("rgba(0%,z)"))
        assertNull(parseColor("rgba(0%,0,z)"))
        assertNull(parseColor("rgba(0%)"))
        assertNull(parseColor("rgba(0%,0%)"))
        assertNull(parseColor("rgba(0%,0,0)"))
        assertNull(parseColor("rgba(0,0%,0)"))
        assertNull(parseColor("rgba(0,0,0%)"))
        assertNull(parseColor("rgba(0%,0%,0%)"))
        assertEquals(Color.TRANSPARENT, parseColor("rgba(0%,0%,0%,0)"))
        assertEquals(Color.BLACK, parseColor("rgba(0%,0%,0%,1)"))
        assertEquals(Color.WHITE, parseColor("rgba(100%, 100%, 100%, 1.0)"))
        assertEquals(Color.BLUE, parseColor("rgba(0%, 0%, 100%, 1.0)"))
        assertEquals(Color.GREEN, parseColor("rgba(0%, 100%, 0%, 1.0)"))
        assertEquals(Color.RED, parseColor("rgba(100%, 0%, 0%, 1.0)"))
        assertNull(parseColor("rgba(101%, 101%, 101%)"))
        assertNull(parseColor("rgba(999%, 999%, 999%)"))
        assertNull(parseColor("rgba(-1%, -1%, -1%)"))
    }

    @Test
    fun color_hsl() {
        assertNull(parseColor("()"))
        assertNull(parseColor("hsl"))
        assertNull(parseColor("hsl()"))
        assertNull(parseColor("hsl(,,)"))
        assertNull(parseColor("hsl(z)"))
        assertNull(parseColor("hsl(z,z)"))
        assertNull(parseColor("hsl(z,z,z)"))
        assertNull(parseColor("hsl(0,z)"))
        assertNull(parseColor("hsl(0,0,z)"))
        assertNull(parseColor("hsl(0)"))
        assertNull(parseColor("hsl(0,0)"))
        assertNull(parseColor("hsl(0,0,0)"))
        assertEquals(Color.BLACK, parseColor("hsl(0,0%,0%)"))
        assertEquals(Color.RED, parseColor("hsl(0, 100%, 50%)"))
        assertEquals(Color.BLUE, parseColor("hsl(240, 100%, 50%)"))
        assertEquals(Color.GREEN, parseColor("hsl(120, 100%, 50%)"))
        assertEquals(Color.WHITE, parseColor("hsl(0, 100%, 100%)"))
        assertNull(parseColor("hsl(999, 0%, 0%)"))
        assertNull(parseColor("hsl(-1, -1%, -1%)"))
    }

    @Test
    fun color_hsla() {
        assertNull(parseColor("hsla"))
        assertNull(parseColor("hsla()"))
        assertNull(parseColor("hsla(,,)"))
        assertNull(parseColor("hsla(z)"))
        assertNull(parseColor("hsla(z,z)"))
        assertNull(parseColor("hsla(z,z,z)"))
        assertNull(parseColor("hsla(0,z)"))
        assertNull(parseColor("hsla(0,0,z)"))
        assertNull(parseColor("hsla(0)"))
        assertNull(parseColor("hsla(0,0)"))
        assertNull(parseColor("hsla(0,0,0)"))
        assertNull(parseColor("hsla(0,0,0,0)"))
        assertEquals(Color.BLACK, parseColor("hsla(0,0%,0%,1)"))
        assertEquals(Color.TRANSPARENT, parseColor("hsla(0,0%,0%,0)"))
        assertEquals(Color.RED, parseColor("hsla(0, 100%, 50%, 1)"))
        assertEquals(Color.BLUE, parseColor("hsla(240, 100%, 50%, 1)"))
        assertEquals(Color.GREEN, parseColor("hsla(120, 100%, 50%, 1.0)"))
        assertEquals(Color.WHITE, parseColor("hsla(0, 100%, 100%, 1.0)"))
        assertNull(parseColor("hsla(999, 0%, 0%, 0)"))
        assertNull(parseColor("hsla(-1, -1%, -1%, 0)"))
    }
}
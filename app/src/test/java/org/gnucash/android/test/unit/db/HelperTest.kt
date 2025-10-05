package org.gnucash.android.test.unit.db

import android.text.TextUtils
import org.assertj.core.api.Assertions.assertThat
import org.gnucash.android.db.DatabaseHelper.Companion.sqlEscapeLike
import org.gnucash.android.db.joinIn
import org.gnucash.android.test.unit.GnuCashTest
import org.junit.Test

class HelperTest : GnuCashTest() {
    @Test
    fun `escape for like with default`() {
        assertThat(sqlEscapeLike("hello")).isEqualTo("'%hello%'")
        assertThat(sqlEscapeLike("hello, world!")).isEqualTo("'%hello, world!%'")
    }

    @Test
    fun `escape for like with single-quote`() {
        assertThat(sqlEscapeLike("its")).isEqualTo("'%its%'")
        assertThat(sqlEscapeLike("its'")).isEqualTo("'%its''%'")
        assertThat(sqlEscapeLike("it's")).isEqualTo("'%it''s%'")
    }

    @Test
    fun `escape for like with punctuation`() {
        assertThat(sqlEscapeLike(":)")).isEqualTo("'%:)%'")
        assertThat(sqlEscapeLike(";)")).isEqualTo("'%;)%'")
        assertThat(sqlEscapeLike("\"hello")).isEqualTo("'%\"hello%'")
        assertThat(sqlEscapeLike("hello\"")).isEqualTo("'%hello\"%'")
        assertThat(sqlEscapeLike("\"hello\"")).isEqualTo("'%\"hello\"%'")
    }

    @Test
    fun `escape for like with slashes`() {
        assertThat(sqlEscapeLike("and\\or")).isEqualTo("'%and\\or%'")
        assertThat(sqlEscapeLike("and/or")).isEqualTo("'%and/or%'")
    }

    @Test
    fun `escape for like with unicode`() {
        assertThat(sqlEscapeLike("אבג")).isEqualTo("'%אבג%'")
        assertThat(sqlEscapeLike("\uD83D\uDE0A")).isEqualTo("'%\uD83D\uDE0A%'")
    }

    @Test
    fun `escape for like with reserved`() {
        // Any string of zero or more characters.
        assertThat(sqlEscapeLike("%")).isEqualTo("'%_%%' ESCAPE '_'")
        assertThat(sqlEscapeLike("100%")).isEqualTo("'%100_%%' ESCAPE '_'")

        // Any single character.
        assertThat(sqlEscapeLike("_")).isEqualTo("'%__%' ESCAPE '_'")
        assertThat(sqlEscapeLike("_abc")).isEqualTo("'%__abc%' ESCAPE '_'")
        assertThat(sqlEscapeLike("abc_")).isEqualTo("'%abc__%' ESCAPE '_'")

        // not
        assertThat(sqlEscapeLike("^")).isEqualTo("'%^%'")

        // placeholder
        assertThat(sqlEscapeLike("?")).isEqualTo("'%?%'")
    }

    @Test
    fun `join empty`() {
        join(emptyArray<String>())
    }

    @Test
    fun `join one array`() {
        join(arrayOf("abc"))
    }

    @Test
    fun `join two array`() {
        join(arrayOf("abc", "def"))
    }

    private fun join(array: Array<String>) {
        val joinOld = "('" + TextUtils.join("','", array) + "')"
        val joinNew = array.joinIn()
        assertThat(joinNew).isEqualTo(joinOld)
    }

    @Test
    fun `join one list`() {
        join(listOf<String>("abc"))
    }

    @Test
    fun `join two list`() {
        join(listOf<String>("abc", "def"))
    }

    private fun join(array: List<String>) {
        val joinOld = "('" + TextUtils.join("','", array) + "')"
        val joinNew = array.joinIn()
        assertThat(joinNew).isEqualTo(joinOld)
    }
}
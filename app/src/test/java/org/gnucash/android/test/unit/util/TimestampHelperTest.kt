/*
 * Copyright (c) 2016 Alceu Rodrigues Neto <alceurneto@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gnucash.android.test.unit.util

import org.assertj.core.api.Assertions.assertThat
import org.gnucash.android.test.unit.GnuCashTest
import org.gnucash.android.util.TimestampHelper
import org.junit.Test
import java.sql.Timestamp

class TimestampHelperTest : GnuCashTest() {
    @Test
    fun shouldGetUtcStringFromTimestamp() {
        /**
         * The values used here are well known.
         * See https://en.wikipedia.org/wiki/Unix_time#Notable_events_in_Unix_time
         * for details.
         */

        val unixBillennium = 1_000_000_000 * 1000L
        val unixBillenniumUtcString = "2001-09-09 01:46:40.000"
        val unixBillenniumTimestamp = Timestamp(unixBillennium)
        assertThat(TimestampHelper.getUtcStringFromTimestamp(unixBillenniumTimestamp))
            .isEqualTo(unixBillenniumUtcString)

        val the1234567890thSecond = 1_234_567_890 * 1000L
        val the1234567890thSecondUtcString = "2009-02-13 23:31:30.000"
        val the1234567890thSecondTimestamp = Timestamp(the1234567890thSecond)
        assertThat(
            TimestampHelper.getUtcStringFromTimestamp(the1234567890thSecondTimestamp)
        ).isEqualTo(the1234567890thSecondUtcString)
    }

    @Test
    fun shouldGetTimestampFromEpochZero() {
        val epochZero = TimestampHelper.getTimestampFromEpochZero()
        assertThat(epochZero.time).isZero()
    }

    @Test
    fun shouldGetTimestampFromUtcString() {
        val unixBillennium = 1_000_000_000 * 1000L
        val unixBillenniumUtcString = "2001-09-09 01:46:40"
        val unixBillenniumWithMillisecondsUtcString = "2001-09-09 01:46:40.000"
        val unixBillenniumTimestamp = Timestamp(unixBillennium)
        assertThat(
            TimestampHelper.getTimestampFromUtcString(
                unixBillenniumWithMillisecondsUtcString
            )
        ).isEqualTo(unixBillenniumTimestamp)
        assertThat(TimestampHelper.getTimestampFromUtcString(unixBillenniumUtcString))
            .isEqualTo(unixBillenniumTimestamp)

        val the1234567890thSecond = 1_234_567_890 * 1000L
        val the1234567890thSecondUtcString = "2009-02-13 23:31:30"
        val the1234567890thSecondWithMillisecondsUtcString = "2009-02-13 23:31:30.000"
        val the1234567890thSecondTimestamp = Timestamp(the1234567890thSecond)
        assertThat(
            TimestampHelper.getTimestampFromUtcString(the1234567890thSecondUtcString)
        ).isEqualTo(the1234567890thSecondTimestamp)
        assertThat(
            TimestampHelper.getTimestampFromUtcString(the1234567890thSecondWithMillisecondsUtcString)
        ).isEqualTo(the1234567890thSecondTimestamp)
    }

    @Test
    fun shouldGetTimestampFromNow() {
        val before = System.currentTimeMillis()
        val now = TimestampHelper.getTimestampFromNow().time
        val after = System.currentTimeMillis()
        assertThat(now).isGreaterThanOrEqualTo(before)
            .isLessThanOrEqualTo(after)
    }
}
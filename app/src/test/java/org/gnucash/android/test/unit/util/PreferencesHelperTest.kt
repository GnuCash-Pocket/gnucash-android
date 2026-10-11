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
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.test.unit.GnuCashTest
import org.gnucash.android.util.PreferencesHelper
import org.gnucash.android.util.TimestampHelper
import org.junit.Test
import java.sql.Timestamp

class PreferencesHelperTest : GnuCashTest() {
    @Test
    fun shouldGetLastExportTimeDefaultValue() {
        val bookUID = GnuCashApplication.activeBookUID
        val lastExportTime = PreferencesHelper.getLastExportTime(context, bookUID)
        assertThat(lastExportTime).isEqualTo(TimestampHelper.timestampFromEpochZero)
    }

    @Test
    fun shouldGetLastExportTimeCurrentValue() {
        val goldenBoyBirthday = 1_190_136_000L * 1000
        val goldenBoyBirthdayTimestamp = Timestamp(goldenBoyBirthday)
        val bookUID = GnuCashApplication.activeBookUID
        PreferencesHelper.setLastExportTime(context, goldenBoyBirthdayTimestamp, bookUID)
        assertThat(PreferencesHelper.getLastExportTime(context, bookUID))
            .isEqualTo(goldenBoyBirthdayTimestamp)
    }
}
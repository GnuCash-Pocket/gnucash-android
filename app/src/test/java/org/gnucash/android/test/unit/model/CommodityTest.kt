/*
 * Copyright (c) 2015 Ngewi Fet <ngewif@gmail.com>
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
package org.gnucash.android.test.unit.model

import org.assertj.core.api.Assertions.assertThat
import org.gnucash.android.model.Commodity
import org.gnucash.android.test.unit.GnuCashTest
import org.junit.Test

/**
 * Test commodities
 */
class CommodityTest : GnuCashTest() {
    @Test
    fun setSmallestFraction_shouldNotUseDigits() {
        val commodity = Commodity("Test", "USD", 100)
        assertThat(commodity.smallestFraction).isEqualTo(100)

        commodity.smallestFraction = 1000
        assertThat(commodity.smallestFraction).isEqualTo(1000)
    }

    @Test
    fun testSmallestFractionDigits() {
        val commodity = Commodity("Test", "USD", 100)
        assertThat(commodity.smallestFractionDigits).isEqualTo(2)

        commodity.smallestFraction = 10
        assertThat(commodity.smallestFractionDigits).isEqualTo(1)

        commodity.smallestFraction = 1
        assertThat(commodity.smallestFractionDigits).isEqualTo(0)
    }
}

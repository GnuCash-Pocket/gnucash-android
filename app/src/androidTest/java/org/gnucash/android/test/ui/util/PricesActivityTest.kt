package org.gnucash.android.test.ui.util

import androidx.test.core.app.launchActivity
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.clearText
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withParent
import androidx.test.espresso.matcher.ViewMatchers.withText
import kotlinx.coroutines.runBlocking
import org.gnucash.android.R
import org.gnucash.android.db.adapter.CommoditiesDbAdapter
import org.gnucash.android.db.adapter.PricesDbAdapter
import org.gnucash.android.test.ui.GnuAndroidTest
import org.gnucash.android.ui.price.PriceDatabaseActivity
import org.gnucash.android.ui.price.PriceViewHolder
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.not
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class PricesActivityTest : GnuAndroidTest() {

    @Before
    fun setUp() {
        setDoubleEntryEnabled(true)
    }

    @Test
    fun empty_list() {
        launchActivity<PriceDatabaseActivity>().use {
            onView(withId(android.R.id.empty))
                .check(matches(isDisplayed()))
            onView(withText(R.string.price_list_empty))
                .check(matches(isDisplayed()))
        }
    }

    @Test
    fun common_prices_list() {
        importGnuCash("common_1.gnucash")

        launchActivity<PriceDatabaseActivity>().use {
            onView(withId(android.R.id.empty))
                .check(matches(not(isDisplayed())))
            onView(withId(android.R.id.list))
                .check(matches(isDisplayed()))

            val currencyJPY = CommoditiesDbAdapter.instance.getCurrency("JPY")!!
            val currencyUSD = CommoditiesDbAdapter.instance.getCurrency("USD")!!
            onView(
                allOf(
                    withId(android.R.id.list),
                    withParent(hasDescendant(withText(currencyJPY.formatListItem()))),
                    withParent(hasDescendant(withText(currencyUSD.formatListItem()))),
                )
            ).check(matches(isDisplayed()))
        }
    }

    @Test
    fun common_prices_edit() {
        importGnuCash("common_1.gnucash")

        val currencyUSD = CommoditiesDbAdapter.instance.getCurrency("USD")!!
        val currencyEUR = CommoditiesDbAdapter.instance.getCurrency("EUR")!!
        val priceBefore = PricesDbAdapter.instance.getPrice(currencyUSD, currencyEUR)!!
        assertEquals(93L, priceBefore.valueNum)
        assertEquals(100L, priceBefore.valueDenom)

        launchActivity<PriceDatabaseActivity>().use {
            onView(withId(android.R.id.empty))
                .check(matches(not(isDisplayed())))
            onView(withId(android.R.id.list))
                .check(matches(isDisplayed()))

            onView(
                allOf(
                    withId(android.R.id.list),
                    withParent(hasDescendant(withText(currencyUSD.formatListItem()))),
                    withParent(hasDescendant(withText(currencyEUR.formatListItem()))),
                )
            ).check(matches(isDisplayed()))
            onView(withId(android.R.id.list))
                .perform(
                    RecyclerViewActions.actionOnItemAtPosition<PriceViewHolder>(1, click())
                )

            onView(withId(R.id.namespace))
                .check(matches(isDisplayed()))
            onView(withId(R.id.security))
                .check(matches(isDisplayed()))
            onView(withId(R.id.currency))
                .check(matches(isDisplayed()))

            onView(withId(R.id.input_exchange_rate))
                .check(matches(isDisplayed()))
                .perform(clearText())
                .perform(typeText("1.23"))

            runBlocking {
                onView(withId(R.id.menu_save))
                    .check(matches(isDisplayed()))
                    .performClick()
            }

            val priceAfter = PricesDbAdapter.instance.getPrice(currencyUSD, currencyEUR)!!
            assertEquals(123L, priceAfter.valueNum)
            assertEquals(100L, priceAfter.valueDenom)
        }
    }
}
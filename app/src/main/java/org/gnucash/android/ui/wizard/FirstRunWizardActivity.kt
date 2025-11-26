/*
 * Copyright 2012 Roman Nurik
 * Copyright 2012 Ngewi Fet
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
package org.gnucash.android.ui.wizard

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceManager
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import com.tech.freak.wizardpager.model.AbstractWizardModel
import com.tech.freak.wizardpager.model.ModelCallbacks
import com.tech.freak.wizardpager.model.Page
import com.tech.freak.wizardpager.model.ReviewItem
import com.tech.freak.wizardpager.ui.PageFragmentCallbacks
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashActivity
import org.gnucash.android.app.GnuCashApplication.Companion.defaultCurrencyCode
import org.gnucash.android.databinding.ActivityFirstRunWizardBinding
import org.gnucash.android.db.adapter.BooksDbAdapter
import org.gnucash.android.ui.account.AccountsActivity
import org.gnucash.android.ui.settings.ThemeHelper
import org.gnucash.android.ui.util.widget.FragmentStateAdapter
import org.gnucash.android.util.openBook
import timber.log.Timber
import kotlin.math.max

/**
 * Activity for managing the wizard displayed upon first run of the application
 */
class FirstRunWizardActivity : GnuCashActivity(),
    ModelCallbacks,
    PageFragmentCallbacks,
    ReviewFragment.Callbacks {
    private lateinit var pagerAdapter: WizardPagerAdapter
    private lateinit var wizardModel: FirstRunWizardModel
    private var pagesCompletedCount = 0
    private var editingAfterReview = false

    private lateinit var binding: ActivityFirstRunWizardBinding

    private var btnSaveDefaultBackground: Drawable? = null
    private var btnSaveDefaultColor: ColorStateList? = null
    private var btnSaveFinishBackground: Drawable? = null
    private var btnSaveFinishColor: ColorStateList? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // we need to construct the wizard model before we call super.onCreate, because it's used in
        // onGetPage (which is indirectly called through super.onCreate if savedInstanceState is not
        // null)
        wizardModel = createWizardModel(savedInstanceState)

        super.onCreate(savedInstanceState)
        ThemeHelper.apply(this)
        val binding = ActivityFirstRunWizardBinding.inflate(layoutInflater)
        this.binding = binding
        setContentView(binding.root)

        pagerAdapter = WizardPagerAdapter(this)
        binding.pager.adapter = pagerAdapter
        binding.strip.setOnPageSelectedListener { position ->
            gotoPage(position)
        }

        binding.pager.registerOnPageChangeCallback(object : OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                binding.strip.setCurrentPage(position)
                updateBottomBar()
            }
        })

        binding.defaultButtons.btnSave.setOnClickListener {
            gotoNextPage()
        }

        binding.defaultButtons.btnCancel.setText(R.string.wizard_btn_back)
        binding.defaultButtons.btnCancel.setOnClickListener {
            gotoPreviousPage()
        }

        btnSaveDefaultBackground = binding.defaultButtons.btnSave.background
        btnSaveDefaultColor = binding.defaultButtons.btnSave.textColors
        val button = Button(this)
        btnSaveFinishBackground = button.background
        btnSaveFinishColor = button.textColors

        onPageTreeChanged()
        updateBottomBar()
    }

    /**
     * Create the wizard model for the activity, taking into account the savedInstanceState if it
     * exists (and if it contains a "model" key that we can use).
     *
     * @param savedInstanceState the instance state available in {[.onCreate]}
     * @return an appropriate wizard model for this activity
     */
    private fun createWizardModel(savedInstanceState: Bundle?): FirstRunWizardModel {
        val model = FirstRunWizardModel(this)
        if (savedInstanceState != null) {
            val savedValues = savedInstanceState.getBundle(STATE_MODEL)
            if (savedValues != null) {
                var hasAllPages = true
                for (key in savedValues.keySet()) {
                    if (model.findByKey(key) == null) {
                        hasAllPages = false
                        Timber.w("Saved model page not found: %s", key)
                        break
                    }
                }
                if (hasAllPages) {
                    model.load(savedValues)
                }
            }
        }
        model.registerListener(this)
        return model
    }

    /**
     * Create accounts depending on the user preference (import or default set) and finish this activity
     *
     * This method also removes the first run flag from the application
     */
    private fun createAccountsAndFinish(accountOption: String, currencyCode: String) {
        if (accountOption == wizardModel.optionAccountImport) {
            AccountsActivity.startXmlFileChooser(this)
        } else if (accountOption == wizardModel.optionAccountUser) {
            //user prefers to handle account creation themselves
            AccountsActivity.start(this)
            finish()
        } else {
            val accountAssetId = wizardModel.getAccountsByLabel(accountOption)
            if (accountAssetId.isNullOrEmpty()) {
                return
            }

            val activity: Activity = this@FirstRunWizardActivity
            //save the UID of the active book, and then delete it after successful import
            val booksDbAdapter = BooksDbAdapter.instance
            val bookOldUID = booksDbAdapter.activeBookUID

            AccountsActivity.createDefaultAccounts(
                activity,
                currencyCode,
                accountAssetId
            ) { bookUID ->
                if (bookOldUID.isNotEmpty()) {
                    maybeDeleteOldBook(activity, bookOldUID, bookUID)
                    finish()
                } else {
                    finish()
                }
            }
        }

        AccountsActivity.removeFirstRunFlag(this)
    }

    override fun onPageTreeChanged() {
        pagerAdapter.setPages(wizardModel.getCurrentPageSequence())
        recalculateCutOffPage()
        updateBottomBar()
    }

    private fun updateBottomBar() {
        val pages = pagerAdapter.data
        val position = binding.pager.currentItem
        if (position == pages.size) {
            binding.defaultButtons.btnSave.setText(R.string.btn_wizard_finish)
            binding.defaultButtons.btnSave.background = btnSaveFinishBackground
            binding.defaultButtons.btnSave.setTextColor(btnSaveFinishColor)
        } else {
            binding.defaultButtons.btnSave.setText(if (editingAfterReview) R.string.review else R.string.btn_wizard_next)
            binding.defaultButtons.btnSave.background = btnSaveDefaultBackground
            binding.defaultButtons.btnSave.setTextColor(btnSaveDefaultColor)
        }

        binding.defaultButtons.btnCancel.visibility =
            if (position <= 0) View.INVISIBLE else View.VISIBLE
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == AccountsActivity.REQUEST_PICK_ACCOUNTS_FILE) {
            if (resultCode == RESULT_OK && data != null) {
                importFileAndFinish(data)
            }
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onDestroy() {
        super.onDestroy()
        wizardModel.unregisterListener(this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBundle(STATE_MODEL, wizardModel.save())
    }

    override fun onGetModel(): AbstractWizardModel {
        return wizardModel
    }

    override fun onEditScreenAfterReview(key: String) {
        editingAfterReview = false
        val pages = pagerAdapter.data
        for (i in pages.indices.reversed()) {
            if (pages[i].key == key) {
                editingAfterReview = true
                gotoPage(i)
                return
            }
        }
    }

    override fun onPageDataChanged(page: Page) {
        recalculateCutOffPage()
        updateBottomBar()
    }

    override fun onGetPage(key: String): Page? {
        return wizardModel.findByKey(key)
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun recalculateCutOffPage() {
        val pages = pagerAdapter.data
        // Cut off the pager adapter at first required page that isn't completed
        pagesCompletedCount = 0
        val count = pages.size
        for (i in 0 until count) {
            val page = pages[i]
            if (page.isCompleted) {
                pagesCompletedCount++
            } else if (page.isRequired) {
                break
            }
        }
        pagesCompletedCount += STEP_REVIEW
        binding.strip.setPageCount(pagesCompletedCount)
        pagerAdapter.notifyDataSetChanged()
    }

    private fun gotoNextPage() {
        val position = binding.pager.currentItem
        val positionNext = position + 1
        val count: Int = pagerAdapter.data.size + STEP_REVIEW
        if (positionNext >= count) {
            applySettings()
        } else if (editingAfterReview) {
            editingAfterReview = false
            gotoPage(count - 1)
        } else {
            val page = pagerAdapter.getItem(position)
            if (page.isCompleted) {
                gotoPage(positionNext)
            }
        }
    }

    private fun gotoPreviousPage() {
        val position = binding.pager.currentItem - 1
        gotoPage(max(0, position))
    }

    private fun gotoPage(position: Int) {
        binding.pager.currentItem = position
    }

    private fun applySettings() {
        val pages = pagerAdapter.data
        val reviewItems = ArrayList<ReviewItem>()
        for (page in pages) {
            page.getReviewItems(reviewItems)
        }

        val model = wizardModel
        var currencyLabel: String? = null
        var accountLabel = model.optionAccountUser
        var feedbackOption = ""
        for (reviewItem in reviewItems) {
            val title = reviewItem.title
            if (title == model.titleCurrency) {
                currencyLabel = reviewItem.displayValue
            } else if (title == model.titleOtherCurrency) {
                currencyLabel = reviewItem.displayValue
            } else if (title == model.titleAccount) {
                accountLabel = reviewItem.displayValue
            } else if (title == model.optionAccountDefault) {
                accountLabel = reviewItem.displayValue
            } else if (title == model.titleFeedback) {
                feedbackOption = reviewItem.displayValue
            }
        }

        if (currencyLabel.isNullOrEmpty() || accountLabel.isNullOrEmpty()) {
            return
        }
        val currencyCode = model.getCurrencyByLabel(currencyLabel)
        if (currencyCode.isNullOrEmpty()) {
            return
        }

        val context: Context = this
        defaultCurrencyCode = currencyCode
        PreferenceManager.getDefaultSharedPreferences(context).edit {
            putBoolean(
                getString(R.string.key_enable_crashlytics),
                feedbackOption == model.optionFeedbackSend
            )
        }

        createAccountsAndFinish(accountLabel, currencyCode)
    }

    private fun importFileAndFinish(data: Intent?) {
        val activity: Activity = this
        val booksDbAdapter = BooksDbAdapter.instance
        val bookOldUID = booksDbAdapter.activeBookUID

        openBook(this, data) { bookUID ->
            maybeDeleteOldBook(activity, bookOldUID, bookUID)
            finish()
        }
    }

    private fun maybeDeleteOldBook(context: Context, bookOldUID: String?, bookNewUID: String?) {
        if (bookOldUID.isNullOrEmpty()) return
        if (bookNewUID.isNullOrEmpty()) return
        if (bookOldUID == bookNewUID) return

        val booksDbAdapter = BooksDbAdapter.instance
        val bookOld = booksDbAdapter.getRecord(bookOldUID)
        val bookNew = booksDbAdapter.getRecord(bookNewUID)

        val bookName = bookOld.displayName
        booksDbAdapter.deleteBook(context, bookOldUID)
        bookNew.displayName = bookName
        booksDbAdapter.update(bookNew)
    }

    inner class WizardPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
        internal val data = mutableListOf<Page>()

        @SuppressLint("NotifyDataSetChanged")
        fun setPages(pages: List<Page>) {
            data.clear()
            data.addAll(pages)
            notifyDataSetChanged()
        }

        override fun createFragment(position: Int): Fragment {
            if (position >= data.size) {
                return ReviewFragment()
            }
            return getItem(position).createFragment()
        }

        override fun getItemCount(): Int {
            return pagesCompletedCount
        }

        override fun getItemId(position: Int): Long {
            if (position >= data.size) {
                return 0
            }
            return getItem(position).hashCode().toLong()
        }

        fun getItem(position: Int): Page {
            return data[position]
        }
    }

    companion object {
        private const val STATE_MODEL = "model"
        private const val STEP_REVIEW = 1
    }
}

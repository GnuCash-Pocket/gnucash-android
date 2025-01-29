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

package org.gnucash.android.ui.wizard;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.tech.freak.wizardpager.model.AbstractWizardModel;
import com.tech.freak.wizardpager.model.ModelCallbacks;
import com.tech.freak.wizardpager.model.Page;
import com.tech.freak.wizardpager.model.ReviewItem;
import com.tech.freak.wizardpager.ui.PageFragmentCallbacks;
import com.tech.freak.wizardpager.ui.StepPagerStrip;

import org.gnucash.android.R;
import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.databinding.ActivityFirstRunWizardBinding;
import org.gnucash.android.db.adapter.BooksDbAdapter;
import org.gnucash.android.model.Book;
import org.gnucash.android.ui.account.AccountsActivity;
import org.gnucash.android.ui.util.TaskDelegate;
import org.gnucash.android.ui.util.widget.FragmentStateAdapter;

import java.util.ArrayList;
import java.util.List;

import timber.log.Timber;

/**
 * Activity for managing the wizard displayed upon first run of the application
 */
public class FirstRunWizardActivity extends AppCompatActivity implements
    PageFragmentCallbacks, ReviewFragment.Callbacks, ModelCallbacks {

    private static final String STATE_MODEL = "model";
    private static final int STEP_REVIEW = 1;

    private WizardPagerAdapter mPagerAdapter;

    private boolean mEditingAfterReview;

    private FirstRunWizardModel mWizardModel;

    private int pagesCompletedCount;

    private ActivityFirstRunWizardBinding mBinding;

    private Drawable btnSaveDefaultBackground;
    private ColorStateList btnSaveDefaultColor;
    private Drawable btnSaveFinishBackground;
    private ColorStateList btnSaveFinishColor;

    public void onCreate(Bundle savedInstanceState) {
        // we need to construct the wizard model before we call super.onCreate, because it's used in
        // onGetPage (which is indirectly called through super.onCreate if savedInstanceState is not
        // null)
        mWizardModel = createWizardModel(savedInstanceState);

        super.onCreate(savedInstanceState);
        mBinding = ActivityFirstRunWizardBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());

        mPagerAdapter = new WizardPagerAdapter(this);
        mBinding.pager.setAdapter(mPagerAdapter);
        mBinding.strip
            .setOnPageSelectedListener(new StepPagerStrip.OnPageSelectedListener() {
                @Override
                public void onPageStripSelected(int position) {
                    gotoPage(position);
                }
            });

        mBinding.pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                mBinding.strip.setCurrentPage(position);
                updateBottomBar();
            }
        });

        mBinding.defaultButtons.btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                gotoNextPage();
            }
        });

        mBinding.defaultButtons.btnCancel.setText(R.string.wizard_btn_back);
        mBinding.defaultButtons.btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                gotoPreviousPage();
            }
        });

        btnSaveDefaultBackground = mBinding.defaultButtons.btnSave.getBackground();
        btnSaveDefaultColor = mBinding.defaultButtons.btnSave.getTextColors();
        Button button = new Button(this);
        btnSaveFinishBackground = button.getBackground();
        btnSaveFinishColor = button.getTextColors();

        onPageTreeChanged();
        updateBottomBar();
    }

    /**
     * Create the wizard model for the activity, taking into account the savedInstanceState if it
     * exists (and if it contains a "model" key that we can use).
     *
     * @param savedInstanceState the instance state available in {{@link #onCreate(Bundle)}}
     * @return an appropriate wizard model for this activity
     */
    private FirstRunWizardModel createWizardModel(Bundle savedInstanceState) {
        FirstRunWizardModel model = new FirstRunWizardModel(this);
        if (savedInstanceState != null) {
            Bundle savedValues = savedInstanceState.getBundle(STATE_MODEL);
            if (savedValues != null) {
                boolean hasAllPages = true;
                for (String key : savedValues.keySet()) {
                    if (model.findByKey(key) == null) {
                        hasAllPages = false;
                        Timber.w("Saved model page not found: %s", key);
                        break;
                    }
                }
                if (hasAllPages) {
                    model.load(savedValues);
                }
            }
        }
        model.registerListener(this);
        return model;
    }

    /**
     * Create accounts depending on the user preference (import or default set) and finish this activity
     * <p>This method also removes the first run flag from the application</p>
     */
    private void createAccountsAndFinish(@NonNull String accountOption, String currencyCode) {
        if (accountOption.equals(mWizardModel.optionAccountDefault)) {
            //save the UID of the active book, and then delete it after successful import
            final BooksDbAdapter dbAdapter = BooksDbAdapter.getInstance();
            final String bookUID = dbAdapter.getActiveBookUID();
            Book bookOld = dbAdapter.getRecord(bookUID);
            final String bookName = bookOld.getDisplayName();
            TaskDelegate callbackAfterImport = (!TextUtils.isEmpty(bookUID)) ? new TaskDelegate() {
                @Override
                public void onTaskComplete() {
                    dbAdapter.deleteBook(bookUID);
                    Book book = dbAdapter.getActiveBook();
                    book.setDisplayName(bookName);
                    dbAdapter.updateRecord(book);
                }
            } : null;
            AccountsActivity.createDefaultAccounts(currencyCode, FirstRunWizardActivity.this, callbackAfterImport);
            finish();
        } else if (accountOption.equals(mWizardModel.optionAccountImport)) {
            AccountsActivity.startXmlFileChooser(this);
        } else { //user prefers to handle account creation themselves
            AccountsActivity.start(this);
            finish();
        }

        AccountsActivity.removeFirstRunFlag(this);
    }

    @Override
    public void onPageTreeChanged() {
        mPagerAdapter.setPages(mWizardModel.getCurrentPageSequence());
        recalculateCutOffPage();
        updateBottomBar();
    }

    private void updateBottomBar() {
        List<Page> pages = mPagerAdapter.data;
        int position = mBinding.pager.getCurrentItem();
        if (position == pages.size()) {
            mBinding.defaultButtons.btnSave.setText(R.string.btn_wizard_finish);
            mBinding.defaultButtons.btnSave.setBackground(btnSaveFinishBackground);
            mBinding.defaultButtons.btnSave.setTextColor(btnSaveFinishColor);
        } else {
            mBinding.defaultButtons.btnSave.setText(mEditingAfterReview ? R.string.review : R.string.btn_wizard_next);
            mBinding.defaultButtons.btnSave.setBackground(btnSaveDefaultBackground);
            mBinding.defaultButtons.btnSave.setTextColor(btnSaveDefaultColor);
        }

        mBinding.defaultButtons.btnCancel
            .setVisibility(position <= 0 ? View.INVISIBLE : View.VISIBLE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == AccountsActivity.REQUEST_PICK_ACCOUNTS_FILE) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                AccountsActivity.importXmlFileFromIntent(this, data, new TaskDelegate() {
                    @Override
                    public void onTaskComplete() {
                        finish();
                    }
                });
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mWizardModel.unregisterListener(this);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBundle(STATE_MODEL, mWizardModel.save());
    }

    @Override
    public AbstractWizardModel onGetModel() {
        return mWizardModel;
    }

    @Override
    public void onEditScreenAfterReview(String key) {
        mEditingAfterReview = false;
        List<Page> pages = mPagerAdapter.data;
        for (int i = pages.size() - 1; i >= 0; i--) {
            if (pages.get(i).getKey().equals(key)) {
                mEditingAfterReview = true;
                gotoPage(i);
                return;
            }
        }
    }

    @Override
    public void onPageDataChanged(Page page) {
        recalculateCutOffPage();
        updateBottomBar();
    }

    @Override
    public Page onGetPage(String key) {
        return mWizardModel.findByKey(key);
    }

    private void recalculateCutOffPage() {
        List<Page> pages = mPagerAdapter.data;
        // Cut off the pager adapter at first required page that isn't completed
        pagesCompletedCount = 0;
        int count = pages.size();
        for (int i = 0; i < count; i++) {
            Page page = pages.get(i);
            if (page.isCompleted()) {
                pagesCompletedCount++;
            } else if (page.isRequired()) {
                break;
            }
        }
        pagesCompletedCount += STEP_REVIEW;
        mBinding.strip.setPageCount(pagesCompletedCount);
        mPagerAdapter.notifyDataSetChanged();
    }

    private void gotoNextPage() {
        int position = mBinding.pager.getCurrentItem();
        int positionNext = position + 1;
        int count = mPagerAdapter.data.size() + STEP_REVIEW;
        if (positionNext >= count) {
            applySettings();
        } else if (mEditingAfterReview) {
            mEditingAfterReview = false;
            gotoPage(count - 1);
        } else {
            Page page = mPagerAdapter.getItem(position);
            if (page.isCompleted()) {
                gotoPage(positionNext);
            }
        }
    }

    private void gotoPreviousPage() {
        int position = mBinding.pager.getCurrentItem() - 1;
        gotoPage(Math.max(0, position));
    }

    private void gotoPage(int position) {
        mBinding.pager.setCurrentItem(position);
    }

    private void applySettings() {
        List<Page> pages = mPagerAdapter.data;
        ArrayList<ReviewItem> reviewItems = new ArrayList<>();
        for (Page page : pages) {
            page.getReviewItems(reviewItems);
        }

        String currencyLabel = null;
        String accountOption = mWizardModel.optionAccountUser;
        String feedbackOption = "";
        for (ReviewItem reviewItem : reviewItems) {
            String title = reviewItem.getTitle();
            if (title.equals(mWizardModel.titleCurrency)) {
                currencyLabel = reviewItem.getDisplayValue();
            } else if (title.equals(mWizardModel.titleOtherCurrency)) {
                currencyLabel = reviewItem.getDisplayValue();
            } else if (title.equals(mWizardModel.titleAccount)) {
                accountOption = reviewItem.getDisplayValue();
            } else if (title.equals(mWizardModel.titleFeedback)) {
                feedbackOption = reviewItem.getDisplayValue();
            }
        }

        if (TextUtils.isEmpty(currencyLabel) || TextUtils.isEmpty(accountOption)) {
            return;
        }
        String currencyCode = mWizardModel.getCurrencyByLabel(currencyLabel);
        if (TextUtils.isEmpty(currencyCode)) {
            return;
        }

        Context context = FirstRunWizardActivity.this;
        GnuCashApplication.setDefaultCurrencyCode(context, currencyCode);
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putBoolean(getString(R.string.key_enable_crashlytics), feedbackOption.equals(mWizardModel.optionFeedbackSend))
            .apply();

        createAccountsAndFinish(accountOption, currencyCode);
    }

    public class WizardPagerAdapter extends FragmentStateAdapter {

        private final List<Page> data = new ArrayList<>();

        public WizardPagerAdapter(FragmentActivity activity) {
            super(activity);
        }

        @SuppressLint("NotifyDataSetChanged")
        public void setPages(List<Page> pages) {
            data.clear();
            data.addAll(pages);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            if (position >= data.size()) {
                return new ReviewFragment();
            }
            return getItem(position).createFragment();
        }

        @Override
        public int getItemCount() {
            return pagesCompletedCount;
        }

        @Override
        public long getItemId(int position) {
            if (position >= data.size()) {
                return 0;
            }
            return getItem(position).hashCode();
        }

        public Page getItem(int position) {
            return data.get(position);
        }
    }
}

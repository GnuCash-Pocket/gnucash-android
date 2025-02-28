/*
 * Copyright (c) 2016 Ngewi Fet <ngewif@gmail.com>
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

package org.gnucash.android.ui.settings;

import static org.gnucash.android.util.DocumentExtKt.chooseDocument;
import static org.gnucash.android.util.DocumentExtKt.openBook;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.cursoradapter.widget.SimpleCursorAdapter;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentResultListener;
import androidx.fragment.app.ListFragment;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;

import org.gnucash.android.R;
import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.db.DatabaseCursorLoader;
import org.gnucash.android.db.DatabaseHelper;
import org.gnucash.android.db.DatabaseSchema.BookEntry;
import org.gnucash.android.db.adapter.AccountsDbAdapter;
import org.gnucash.android.db.adapter.BooksDbAdapter;
import org.gnucash.android.db.adapter.TransactionsDbAdapter;
import org.gnucash.android.ui.account.AccountsActivity;
import org.gnucash.android.ui.common.Refreshable;
import org.gnucash.android.ui.settings.dialog.DeleteBookConfirmationDialog;
import org.gnucash.android.util.BookUtils;
import org.gnucash.android.util.PreferencesHelper;

import java.io.IOException;
import java.sql.Timestamp;

import timber.log.Timber;

/**
 * Fragment for managing the books in the database
 */
public class BookManagerFragment extends ListFragment implements
        LoaderManager.LoaderCallbacks<Cursor>, Refreshable, FragmentResultListener {

    private static final int REQUEST_OPEN_DOCUMENT = 0x20;

    private SimpleCursorAdapter mCursorAdapter;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_book_list, container, false);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        mCursorAdapter = new BooksCursorAdapter(requireContext(), R.layout.cardview_book,
                null, new String[]{BookEntry.COLUMN_DISPLAY_NAME, BookEntry.COLUMN_SOURCE_URI},
                new int[]{R.id.primary_text, R.id.secondary_text});

        setListAdapter(mCursorAdapter);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ActionBar actionBar = ((AppCompatActivity) requireActivity()).getSupportActionBar();
        assert actionBar != null;
        actionBar.setTitle(R.string.title_manage_books);

        getListView().setChoiceMode(ListView.CHOICE_MODE_NONE);
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.book_list_actions, menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_create: {
                Activity activity = getActivity();
                if (activity == null) {
                    Timber.w("Activity expected");
                    return false;
                }
                AccountsActivity.createDefaultAccounts(activity, GnuCashApplication.getDefaultCurrencyCode());
                return true;
            }

            case R.id.menu_open:
                chooseDocument(this, REQUEST_OPEN_DOCUMENT);
                return true;

            default:
                return false;
        }

    }

    @Override
    public void refresh() {
        if (isDetached() || getFragmentManager() == null) return;
        getLoaderManager().restartLoader(0, null, this);
    }

    @Override
    public void refresh(String uid) {
        refresh();
    }

    @NonNull
    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        Timber.d("Creating loader for books");
        return new BooksCursorLoader(requireContext());
    }

    @Override
    public void onLoadFinished(@NonNull Loader<Cursor> loader, Cursor data) {
        Timber.d("Finished loading books from database");
        mCursorAdapter.changeCursor(data);
        mCursorAdapter.notifyDataSetChanged();
    }

    @Override
    public void onLoaderReset(@NonNull Loader<Cursor> loader) {
        Timber.d("Resetting books list loader");
        mCursorAdapter.changeCursor(null);
    }

    @Override
    public void onFragmentResult(@NonNull String requestKey, @NonNull Bundle result) {
        if (DeleteBookConfirmationDialog.TAG.equals(requestKey)) {
            boolean refresh = result.getBoolean(Refreshable.EXTRA_REFRESH);
            if (refresh) refresh();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == REQUEST_OPEN_DOCUMENT) {
                openBook(requireActivity(), data);
                return;
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private class BooksCursorAdapter extends SimpleCursorAdapter {

        private final String activeBookUID = GnuCashApplication.getActiveBookUID();

        BooksCursorAdapter(Context context, int layout, Cursor c, String[] from, int[] to) {
            super(context, layout, c, from, to, 0);
        }

        @Override
        public void bindView(View view, final Context context, Cursor cursor) {
            super.bindView(view, context, cursor);

            final String bookUID = cursor.getString(cursor.getColumnIndexOrThrow(BookEntry.COLUMN_UID));

            setLastExportedText(view, bookUID);
            setStatisticsText(view, bookUID);
            setUpMenu(view, context, cursor, bookUID);

            if (activeBookUID.equals(bookUID)) {
                ((TextView) view.findViewById(R.id.primary_text))
                    .setTextColor(ContextCompat.getColor(context, R.color.theme_primary));
            }

            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    //do nothing if the active book is tapped
                    if (!activeBookUID.equals(bookUID)) {
                        BookUtils.loadBook(v.getContext(), bookUID);
                        requireActivity().finish();
                    }
                }
            });
        }

        private void setUpMenu(View view, final Context context, Cursor cursor, final String bookUID) {
            final String bookName = cursor.getString(
                    cursor.getColumnIndexOrThrow(BookEntry.COLUMN_DISPLAY_NAME));
            ImageView optionsMenu = (ImageView) view.findViewById(R.id.options_menu);
            optionsMenu.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    PopupMenu popupMenu = new PopupMenu(context, v);
                    MenuInflater menuInflater = popupMenu.getMenuInflater();
                    menuInflater.inflate(R.menu.book_context_menu, popupMenu.getMenu());

                    popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                        @Override
                        public boolean onMenuItemClick(@NonNull MenuItem item) {
                            switch (item.getItemId()) {
                                case R.id.menu_rename:
                                    return handleMenuRenameBook(bookName, bookUID);
                                case R.id.menu_sync:
                                    //TODO implement sync
                                    return false;
                                case R.id.menu_delete:
                                    return handleMenuDeleteBook(bookUID);
                                default:
                                    return true;
                            }
                        }
                    });

                    String activeBookUID = GnuCashApplication.getActiveBookUID();
                    if (activeBookUID.equals(bookUID)) {//we cannot delete the active book
                        popupMenu.getMenu().findItem(R.id.menu_delete).setEnabled(false);
                    }
                    popupMenu.show();
                }
            });
        }

        private boolean handleMenuDeleteBook(final String bookUID) {
            FragmentManager fm = getParentFragmentManager();
            fm.setFragmentResultListener(DeleteBookConfirmationDialog.TAG, BookManagerFragment.this, BookManagerFragment.this);
            DeleteBookConfirmationDialog dialog = DeleteBookConfirmationDialog.newInstance(bookUID);
            dialog.show(fm, DeleteBookConfirmationDialog.TAG);
            return true;
        }

        /**
         * Opens a dialog for renaming a book
         *
         * @param bookName Current name of the book
         * @param bookUID  GUID of the book
         * @return {@code true}
         */
        private boolean handleMenuRenameBook(String bookName, final String bookUID) {
            AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(getActivity());
            dialogBuilder.setTitle(R.string.title_rename_book)
                    .setView(R.layout.dialog_rename_book)
                    .setPositiveButton(R.string.btn_rename, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            EditText bookTitle = (EditText) ((AlertDialog) dialog).findViewById(R.id.input_book_title);
                            BooksDbAdapter.getInstance()
                                    .updateRecord(bookUID,
                                            BookEntry.COLUMN_DISPLAY_NAME,
                                            bookTitle.getText().toString().trim());
                            refresh();
                        }
                    })
                    .setNegativeButton(R.string.btn_cancel, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });
            AlertDialog dialog = dialogBuilder.create();
            dialog.show();
            ((TextView) dialog.findViewById(R.id.input_book_title)).setText(bookName);
            return true;
        }

        private void setLastExportedText(View view, String bookUID) {
            Context context = view.getContext();
            TextView labelLastSync = (TextView) view.findViewById(R.id.label_last_sync);
            labelLastSync.setText(R.string.label_last_export_time);

            Timestamp lastSyncTime = PreferencesHelper.getLastExportTime(context, bookUID);
            TextView lastSyncText = (TextView) view.findViewById(R.id.last_sync_time);
            if (lastSyncTime.equals(new Timestamp(0)))
                lastSyncText.setText(R.string.last_export_time_never);
            else
                lastSyncText.setText(lastSyncTime.toString());
        }

        private void setStatisticsText(View view, String bookUID) {
            final Context context = view.getContext();
            DatabaseHelper dbHelper = new DatabaseHelper(context, bookUID);
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            TransactionsDbAdapter trnAdapter = new TransactionsDbAdapter(db);
            int transactionCount = (int) trnAdapter.getRecordsCount();
            AccountsDbAdapter accountsDbAdapter = new AccountsDbAdapter(trnAdapter);
            int accountsCount = (int) accountsDbAdapter.getRecordsCount();
            dbHelper.close();

            String transactionStats = getResources().getQuantityString(R.plurals.book_transaction_stats, transactionCount, transactionCount);
            String accountStats = getResources().getQuantityString(R.plurals.book_account_stats, accountsCount, accountsCount);
            String stats = accountStats + ", " + transactionStats;
            TextView statsText = (TextView) view.findViewById(R.id.secondary_text);
            statsText.setText(stats);
        }
    }

    /**
     * {@link DatabaseCursorLoader} for loading the book list from the database
     *
     * @author Ngewi Fet <ngewif@gmail.com>
     */
    private static class BooksCursorLoader extends DatabaseCursorLoader<BooksDbAdapter> {
        BooksCursorLoader(Context context) {
            super(context);
        }

        @Override
        public Cursor loadInBackground() {
            BooksDbAdapter dbAdapter = BooksDbAdapter.getInstance();
            if (dbAdapter == null) return null;
            databaseAdapter = dbAdapter;
            Cursor cursor = dbAdapter.fetchAllRecords();
            registerContentObserver(cursor);
            return cursor;
        }
    }
}

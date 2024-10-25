/*
 * Copyright (c) 2013 - 2014 Ngewi Fet <ngewif@gmail.com>
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

package org.gnucash.android.util;

import android.content.Context;
import android.database.Cursor;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.Nullable;
import androidx.cursoradapter.widget.SimpleCursorAdapter;

import org.gnucash.android.R;
import org.gnucash.android.db.DatabaseSchema;

/**
 * Cursor adapter which looks up the fully qualified account name and returns that instead of just the simple name.
 * <p>The fully qualified account name includes the parent hierarchy</p>
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
public class QualifiedAccountNameCursorAdapter extends SimpleCursorAdapter {

    private int columnIndexUID = 0;
    private int columnIndexFavorite = 0;

    /**
     * Initialize the Cursor adapter for account names using default spinner views
     *
     * @param context Application context
     * @param cursor  Cursor to accounts
     */
    public QualifiedAccountNameCursorAdapter(Context context, Cursor cursor) {
        this(context, cursor, android.R.layout.simple_spinner_item);
    }

    /**
     * Overloaded constructor. Specifies the view to use for displaying selected spinner text
     *
     * @param context             Application context
     * @param cursor              Cursor to account data
     * @param selectedSpinnerItem Layout resource for selected item text
     */
    public QualifiedAccountNameCursorAdapter(Context context, Cursor cursor, @LayoutRes int selectedSpinnerItem) {
        super(context, selectedSpinnerItem, cursor, new String[]{DatabaseSchema.AccountEntry.COLUMN_FULL_NAME}, new int[]{android.R.id.text1}, 0);
        setDropDownViewResource(R.layout.account_spinner_dropdown_item);
    }

    @Override
    protected void init(Context context, Cursor c, boolean autoRequery) {
        super.init(context, c, autoRequery);
        columnIndexUID = -1;
        columnIndexFavorite = -1;
        if (c != null) {
            columnIndexUID = c.getColumnIndex(DatabaseSchema.AccountEntry.COLUMN_UID);
            columnIndexFavorite = c.getColumnIndex(DatabaseSchema.AccountEntry.COLUMN_FAVORITE);
        }
    }

    @Override
    public Cursor swapCursor(@Nullable Cursor c) {
        columnIndexUID = -1;
        columnIndexFavorite = -1;
        if (c != null) {
            columnIndexUID = c.getColumnIndex(DatabaseSchema.AccountEntry.COLUMN_UID);
            columnIndexFavorite = c.getColumnIndex(DatabaseSchema.AccountEntry.COLUMN_FAVORITE);
        }
        return super.swapCursor(c);
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        super.bindView(view, context, cursor);
        TextView textView = (TextView) view.findViewById(android.R.id.text1);
        textView.setEllipsize(TextUtils.TruncateAt.MIDDLE);

        int isFavorite = cursor.getInt(columnIndexFavorite);
        if (isFavorite == 0) {
            textView.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
        } else {
            textView.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_favorite, 0);
        }
    }

    @Nullable
    public String getItemUID(int position) {
        final int count = getCount();
        final Cursor cursor = getCursor();
        if (count > 0 && cursor != null) {
            if (cursor.moveToPosition(position)) {
                return cursor.getString(columnIndexUID);
            }
        }
        return null;
    }

    /**
     * Returns the position of a given account in the adapter
     *
     * @param accountUID GUID of the account
     * @return Position of the account or -1 if the account is not found
     */
    public int getItemPosition(@Nullable String accountUID) {
        if (TextUtils.isEmpty(accountUID)) return -1;
        final int count = getCount();
        for (int i = 0; i < count; i++) {
            if (accountUID.equals(getItemUID(i))) {
                return i;
            }
        }
        return -1;
    }
}

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

package org.gnucash.android.db.adapter;

import static org.gnucash.android.db.DatabaseSchema.RecurrenceEntry;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.model.PeriodType;
import org.gnucash.android.model.Recurrence;
import org.gnucash.android.util.TimestampHelper;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

/**
 * Database adapter for {@link Recurrence} entries
 */
public class RecurrenceDbAdapter extends DatabaseAdapter<Recurrence> {
    /**
     * Opens the database adapter with an existing database
     *
     * @param db SQLiteDatabase object
     */
    public RecurrenceDbAdapter(SQLiteDatabase db) {
        super(db, RecurrenceEntry.TABLE_NAME, new String[]{
                RecurrenceEntry.COLUMN_MULTIPLIER,
                RecurrenceEntry.COLUMN_PERIOD_TYPE,
                RecurrenceEntry.COLUMN_BYDAY,
                RecurrenceEntry.COLUMN_PERIOD_START,
                RecurrenceEntry.COLUMN_PERIOD_END
        });
    }

    public static RecurrenceDbAdapter getInstance() {
        return GnuCashApplication.getRecurrenceDbAdapter();
    }

    @Override
    public Recurrence buildModelInstance(@NonNull Cursor cursor) {
        String type = cursor.getString(cursor.getColumnIndexOrThrow(RecurrenceEntry.COLUMN_PERIOD_TYPE));
        int multiplier = cursor.getInt(cursor.getColumnIndexOrThrow(RecurrenceEntry.COLUMN_MULTIPLIER));
        String periodStart = cursor.getString(cursor.getColumnIndexOrThrow(RecurrenceEntry.COLUMN_PERIOD_START));
        String periodEnd = cursor.getString(cursor.getColumnIndexOrThrow(RecurrenceEntry.COLUMN_PERIOD_END));
        String byDays = cursor.getString(cursor.getColumnIndexOrThrow(RecurrenceEntry.COLUMN_BYDAY));

        PeriodType periodType = PeriodType.of(type);
        Recurrence recurrence = new Recurrence(periodType);
        populateBaseModelAttributes(cursor, recurrence);
        recurrence.setMultiplier(multiplier);
        recurrence.setPeriodStart(TimestampHelper.getTimestampFromUtcString(periodStart).getTime());
        if (periodEnd != null) {
            recurrence.setPeriodEnd(TimestampHelper.getTimestampFromUtcString(periodEnd));
        }
        recurrence.setByDays(stringToByDays(byDays));

        return recurrence;
    }

    @Override
    protected @NonNull SQLiteStatement setBindings(@NonNull SQLiteStatement stmt, @NonNull final Recurrence recurrence) {
        stmt.clearBindings();
        stmt.bindLong(1, recurrence.getMultiplier());
        stmt.bindString(2, recurrence.getPeriodType().name());
        if (!recurrence.getByDays().isEmpty())
            stmt.bindString(3, byDaysToString(recurrence.getByDays()));
        //recurrence should always have a start date
        stmt.bindString(4, TimestampHelper.getUtcStringFromTimestamp(recurrence.getPeriodStart()));

        if (recurrence.getPeriodEnd() != null)
            stmt.bindString(5, TimestampHelper.getUtcStringFromTimestamp(recurrence.getPeriodEnd()));
        stmt.bindString(6, recurrence.getUID());

        return stmt;
    }

    /**
     * Converts a list of days of week as Calendar constants to an String for
     * storing in the database.
     *
     * @param byDays list of days of week constants from Calendar
     * @return String of days of the week or null if {@code byDays} was empty
     */
    private static @NonNull String byDaysToString(@NonNull List<Integer> byDays) {
        StringBuilder builder = new StringBuilder();
        for (int day : byDays) {
            switch (day) {
                case Calendar.MONDAY:
                    builder.append("MO");
                    break;
                case Calendar.TUESDAY:
                    builder.append("TU");
                    break;
                case Calendar.WEDNESDAY:
                    builder.append("WE");
                    break;
                case Calendar.THURSDAY:
                    builder.append("TH");
                    break;
                case Calendar.FRIDAY:
                    builder.append("FR");
                    break;
                case Calendar.SATURDAY:
                    builder.append("SA");
                    break;
                case Calendar.SUNDAY:
                    builder.append("SU");
                    break;
                default:
                    throw new RuntimeException("bad day of week: " + day);
            }
            builder.append(",");
        }
        builder.deleteCharAt(builder.length() - 1);
        return builder.toString();
    }

    /**
     * Converts a String with the comma-separated days of the week into a
     * list of Calendar constants.
     *
     * @param byDaysString String with comma-separated days fo the week
     * @return list of days of the week as Calendar constants.
     */
    private static @NonNull List<Integer> stringToByDays(@Nullable String byDaysString) {
        if (TextUtils.isEmpty(byDaysString))
            return Collections.emptyList();

        List<Integer> byDaysList = new ArrayList<>();
        for (String day : byDaysString.split(",")) {
            switch (day) {
                case "MO":
                    byDaysList.add(Calendar.MONDAY);
                    break;
                case "TU":
                    byDaysList.add(Calendar.TUESDAY);
                    break;
                case "WE":
                    byDaysList.add(Calendar.WEDNESDAY);
                    break;
                case "TH":
                    byDaysList.add(Calendar.THURSDAY);
                    break;
                case "FR":
                    byDaysList.add(Calendar.FRIDAY);
                    break;
                case "SA":
                    byDaysList.add(Calendar.SATURDAY);
                    break;
                case "SU":
                    byDaysList.add(Calendar.SUNDAY);
                    break;
                default:
                    // Issue #172 - "LU" is Latin
                    //throw new RuntimeException("bad day of week: " + day);
            }
        }
        return byDaysList;
    }
}

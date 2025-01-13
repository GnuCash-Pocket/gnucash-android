/*
 * Copyright (c) 2013 - 2015 Ngewi Fet <ngewif@gmail.com>
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

package org.gnucash.android.ui.transaction;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.gnucash.android.R;
import org.gnucash.android.app.ActivityExtKt;
import org.gnucash.android.app.MenuFragment;
import org.gnucash.android.databinding.FragmentScheduledEventsListBinding;
import org.gnucash.android.databinding.ListItemScheduledTrxnBinding;
import org.gnucash.android.db.adapter.ScheduledActionDbAdapter;
import org.gnucash.android.model.ScheduledAction;
import org.gnucash.android.ui.common.Refreshable;
import org.gnucash.android.ui.util.CursorRecyclerAdapter;
import org.gnucash.android.util.BackupManager;
import org.gnucash.android.util.DateExtKt;

import timber.log.Timber;

/**
 * Fragment which displays the scheduled actions in the system
 * <p>Currently, it handles the display of scheduled transactions and scheduled exports</p>
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
public abstract class ScheduledActionsListFragment extends MenuFragment implements
    Refreshable,
    LoaderManager.LoaderCallbacks<Cursor> {

    protected ScheduledCursorAdapter<?> listAdapter;

    protected FragmentScheduledEventsListBinding binding;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentScheduledEventsListBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ActionBar actionBar = ((AppCompatActivity) requireActivity()).getSupportActionBar();
        assert actionBar != null;
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setHomeButtonEnabled(true);

        binding.list.setEmptyView(binding.empty);
        binding.list.setLayoutManager(new LinearLayoutManager(view.getContext()));
        binding.list.setAdapter(listAdapter);

        getLoaderManager().initLoader(0, null, this);
    }

    @Override
    public void refresh() {
        getLoaderManager().restartLoader(0, null, this);
    }

    @Override
    public void refresh(String uid) {
        refresh();
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public void onDestroy() {
        getLoaderManager().destroyLoader(0);
        super.onDestroy();
    }

    @Override
    public void onLoadFinished(@NonNull Loader<Cursor> loader, Cursor cursor) {
        Timber.d("Transactions loader finished. Swapping in cursor");
        listAdapter.swapCursor(cursor);
    }

    @Override
    public void onLoaderReset(@NonNull Loader<Cursor> loader) {
        Timber.d("Resetting transactions loader");
        listAdapter.swapCursor(null);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            refresh();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    /**
     * Extends a simple cursor adapter to bind transaction attributes to views
     *
     * @author Ngewi Fet <ngewif@gmail.com>
     */
    protected static abstract class ScheduledCursorAdapter<VH extends ScheduledViewHolder> extends CursorRecyclerAdapter<VH> {

        protected final Refreshable refreshable;

        public ScheduledCursorAdapter(@NonNull Refreshable refreshable) {
            super(null);
            this.refreshable = refreshable;
        }

        @Override
        public void onBindViewHolderCursor(VH holder, Cursor cursor) {
            holder.bind(cursor);
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            ListItemScheduledTrxnBinding binding = ListItemScheduledTrxnBinding.inflate(inflater, parent, false);
            return createViewHolder(binding, refreshable);
        }

        protected abstract VH createViewHolder(@NonNull ListItemScheduledTrxnBinding binding, @NonNull Refreshable refreshable);
    }

    protected static abstract class ScheduledViewHolder extends RecyclerView.ViewHolder implements PopupMenu.OnMenuItemClickListener {

        protected final Refreshable refreshable;
        protected final ScheduledActionDbAdapter scheduledActionDbAdapter = ScheduledActionDbAdapter.getInstance();

        protected final ListItemScheduledTrxnBinding binding;
        protected final TextView primaryTextView;
        protected final TextView descriptionTextView;
        protected final TextView amountTextView;
        protected final View menuView;

        private ScheduledAction scheduledAction;

        public ScheduledViewHolder(@NonNull ListItemScheduledTrxnBinding binding, @NonNull Refreshable refreshable) {
            super(binding.getRoot());
            this.binding = binding;
            this.refreshable = refreshable;
            primaryTextView = binding.primaryText;
            descriptionTextView = binding.secondaryText;
            amountTextView = binding.rightText;
            menuView = binding.optionsMenu;

            menuView.setOnClickListener(v -> {
                PopupMenu popup = new PopupMenu(v.getContext(), v);
                popup.setOnMenuItemClickListener(ScheduledViewHolder.this);
                MenuInflater inflater = popup.getMenuInflater();
                inflater.inflate(R.menu.schedxactions_context_menu, popup.getMenu());
                popup.show();
            });
        }

        void bind(@NonNull Cursor cursor) {
            ScheduledAction scheduledAction = scheduledActionDbAdapter.buildModelInstance(cursor);
            bind(scheduledAction);
        }

        protected void bind(@NonNull ScheduledAction scheduledAction) {
            this.scheduledAction = scheduledAction;
        }

        @Nullable
        protected String formatSchedule(@Nullable ScheduledAction scheduledAction) {
            if (scheduledAction == null) return null;

            Context context = itemView.getContext();
            long lastTime = scheduledAction.getLastRunTime();
            if (lastTime > 0) {
                long endTime = scheduledAction.getEndTime();
                final String period;
                if (endTime > 0 && endTime < System.currentTimeMillis()) {
                    period = context.getString(R.string.label_scheduled_action_ended);
                } else {
                    period = scheduledAction.getRepeatString(context);
                }
                return context.getString(R.string.label_scheduled_action,
                    period,
                    DateExtKt.formatMediumDateTime(lastTime));
            }
            return scheduledAction.getRepeatString(context);
        }

        @Override
        public boolean onMenuItemClick(MenuItem item) {
            switch (item.getItemId()) {
                case R.id.menu_delete:
                    if (scheduledAction != null) {
                        final Activity activity = ActivityExtKt.findActivity(itemView.getContext());
                        BackupManager.backupActiveBookAsync(activity, result -> {
                            deleteSchedule(scheduledAction);
                            refreshable.refresh();
                            return null;
                        });
                        return true;
                    }
                    return false;

                default:
                    return false;
            }
        }

        protected abstract void deleteSchedule(@NonNull ScheduledAction scheduledAction);
    }
}


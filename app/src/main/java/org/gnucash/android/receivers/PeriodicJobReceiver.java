/* Copyright (c) 2018 Àlex Magaz Graça <alexandre.magaz@gmail.com>
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

package org.gnucash.android.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import org.gnucash.android.BuildConfig;
import org.gnucash.android.service.ScheduledActionService;
import org.gnucash.android.util.BackupJob;

import timber.log.Timber;

/**
 * Receiver to run periodic jobs.
 *
 * <p>For now, backups and scheduled actions.</p>
 *
 * @author Àlex Magaz Graça <alexandre.magaz@gmail.com>
 */
public class PeriodicJobReceiver extends BroadcastReceiver {

    public static final String ACTION_BACKUP = BuildConfig.APPLICATION_ID + ".ACTION_BACKUP";
    public static final String ACTION_SCHEDULED_ACTIONS = BuildConfig.APPLICATION_ID + ".ACTION_SCHEDULED_ACTIONS";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (TextUtils.isEmpty(action)) {
            Timber.w("No action was set in the intent. Ignoring...");
            return;
        }

        if (action.equals(ACTION_BACKUP)) {
            BackupJob.enqueueWork(context);
        } else if (action.equals(ACTION_SCHEDULED_ACTIONS)) {
            ScheduledActionService.enqueueWork(context);
        }
    }
}

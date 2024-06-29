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

package org.gnucash.android.export;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.preference.PreferenceManager;

import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.android.Auth;
import com.dropbox.core.v2.DbxClientV2;

import org.gnucash.android.BuildConfig;
import org.gnucash.android.R;

/**
 * Helper class for commonly used DropBox methods
 */
public class DropboxHelper {

    private static final String DROPBOX_APP_KEY = BuildConfig.DROPBOX_APP_KEY;

    /**
     * DropBox API v2 client for making requests to DropBox
     */
    private static DbxClientV2 sDbxClient;

    /**
     * Retrieves the access token after DropBox OAuth authentication and saves it to preferences file
     * <p>This method should typically by called in the {@link Activity#onResume()} method of the
     * Activity or Fragment which called {@link Auth#startOAuth2Authentication(Context, String)}
     * </p>
     *
     * @return Retrieved access token. Could be null if authentication failed or was canceled.
     */
    public static String retrieveAndSaveToken(Context context) {
        String accessToken = Auth.getOAuth2Token();
        if (TextUtils.isEmpty(accessToken)) {
            return accessToken;
        }
        setAccessToken(context, accessToken);
        return accessToken;
    }

    /**
     * Return a DropBox client for making requests
     *
     * @return DropBox client for API v2
     */
    public static DbxClientV2 getClient(Context context) {
        if (sDbxClient != null)
            return sDbxClient;

        String accessToken = getAccessToken(context);
        if (TextUtils.isEmpty(accessToken)) {
            DropboxHelper.authenticate(context);
            return null;
        }

        DbxRequestConfig config = new DbxRequestConfig(BuildConfig.APPLICATION_ID);
        sDbxClient = new DbxClientV2(config, accessToken);

        return sDbxClient;
    }

    /**
     * Checks if the app holds an access token for dropbox
     *
     * @return {@code true} if token exists, {@code false} otherwise
     */
    public static boolean hasToken(Context context) {
        String accessToken = getAccessToken(context);
        return !TextUtils.isEmpty(accessToken);
    }

    public static String getAccessToken(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String keyAccessToken = context.getString(R.string.key_dropbox_access_token);
        String accessToken = prefs.getString(keyAccessToken, null);
        if (TextUtils.isEmpty(accessToken)) {
            accessToken = Auth.getOAuth2Token();
        }
        return accessToken;
    }

    private static void setAccessToken(Context context, String accessToken) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String keyAccessToken = context.getString(R.string.key_dropbox_access_token);
        prefs.edit()
            .putString(keyAccessToken, accessToken)
            .apply();
    }

    public static void deleteAccessToken(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String keyAccessToken = context.getString(R.string.key_dropbox_access_token);
        prefs.edit()
            .remove(keyAccessToken)
            .apply();
    }

    public static void authenticate(Context context) {
        Auth.startOAuth2Authentication(context, DROPBOX_APP_KEY);
    }
}

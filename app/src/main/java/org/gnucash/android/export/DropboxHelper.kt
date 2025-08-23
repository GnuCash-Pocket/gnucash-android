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
package org.gnucash.android.export

import android.content.Context
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.android.Auth
import com.dropbox.core.v2.DbxClientV2
import org.gnucash.android.BuildConfig
import org.gnucash.android.R

/**
 * Helper class for commonly used DropBox methods
 */
object DropboxHelper {
    private val DROPBOX_APP_KEY = BuildConfig.DROPBOX_APP_KEY

    /**
     * DropBox API v2 client for making requests to DropBox
     */
    private var client: DbxClientV2? = null

    /**
     * Retrieves the access token after DropBox OAuth authentication and saves it to preferences file
     *
     * This method should typically by called in the [Activity.onResume] method of the
     * Activity or Fragment which called [Auth.startOAuth2Authentication]
     *
     *
     * @return Retrieved access token. Could be null if authentication failed or was canceled.
     */
    fun retrieveAndSaveToken(context: Context): String? {
        val accessToken: String? = Auth.getOAuth2Token()
        if (accessToken.isNullOrEmpty()) {
            return accessToken
        }
        setAccessToken(context, accessToken)
        return accessToken
    }

    /**
     * Return a DropBox client for making requests
     *
     * @return DropBox client for API v2
     */
    fun getClient(context: Context): DbxClientV2? {
        if (client != null) {
            return client
        }

        val accessToken = getAccessToken(context)
        if (accessToken.isNullOrEmpty()) {
            authenticate(context)
            return null
        }

        val config = DbxRequestConfig(BuildConfig.APPLICATION_ID)
        client = DbxClientV2(config, accessToken)

        return client
    }

    /**
     * Checks if the app holds an access token for dropbox
     *
     * @return `true` if token exists, `false` otherwise
     */
    fun hasToken(context: Context): Boolean {
        val accessToken = getAccessToken(context)
        return !accessToken.isNullOrEmpty()
    }

    fun getAccessToken(context: Context): String? {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val keyAccessToken = context.getString(R.string.key_dropbox_access_token)
        var accessToken = prefs.getString(keyAccessToken, null)
        if (accessToken.isNullOrEmpty()) {
            accessToken = Auth.getOAuth2Token()
        }
        return accessToken
    }

    private fun setAccessToken(context: Context, accessToken: String?) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val keyAccessToken = context.getString(R.string.key_dropbox_access_token)
        prefs.edit {
            putString(keyAccessToken, accessToken)
        }
    }

    fun deleteAccessToken(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val keyAccessToken = context.getString(R.string.key_dropbox_access_token)
        prefs.edit {
            remove(keyAccessToken)
        }
    }

    fun authenticate(context: Context) {
        Auth.startOAuth2Authentication(context, DROPBOX_APP_KEY)
    }
}

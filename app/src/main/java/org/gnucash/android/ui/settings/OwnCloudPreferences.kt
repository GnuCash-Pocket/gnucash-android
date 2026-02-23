package org.gnucash.android.ui.settings

import android.content.Context
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import org.gnucash.android.R

class OwnCloudPreferences(private val context: Context) {
    private val preferencesName = context.getString(R.string.owncloud_pref)
    private val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    private val preferencesShared = PreferenceManager.getDefaultSharedPreferences(context)

    private val keyEnabled by lazy { context.getString(R.string.key_owncloud_sync) }
    private val keySync by lazy { context.getString(R.string.owncloud_sync) }
    private val keyServer by lazy { context.getString(R.string.key_owncloud_server) }
    private val keyUsername by lazy { context.getString(R.string.key_owncloud_username) }
    private val keyPassword by lazy { context.getString(R.string.key_owncloud_password) }
    private val keyDir by lazy { context.getString(R.string.key_owncloud_dir) }

    var isEnabled: Boolean
        get() = preferencesShared.getBoolean(keyEnabled, false)
        set(value) {
            preferencesShared.edit {
                putBoolean(keyEnabled, value)
            }
        }
    var isSync: Boolean
        get() = preferences.getBoolean(keySync, false)
        set(value) {
            preferences.edit {
                putBoolean(keySync, value)
            }
        }
    var server: String?
        get() = preferences.getString(keyServer, context.getString(R.string.owncloud_server))
        set(value) {
            preferences.edit {
                putString(keyServer, value)
            }
        }
    var username: String?
        get() = preferences.getString(keyUsername, null)
        set(value) {
            preferences.edit {
                putString(keyUsername, value)
            }
        }
    var password: String?
        get() = preferences.getString(keyPassword, null)
        set(value) {
            preferences.edit {
                putString(keyPassword, value)
            }
        }
    var dir: String?
        get() = preferences.getString(keyDir, context.getString(R.string.app_name))
        set(value) {
            preferences.edit {
                putString(keyDir, value)
            }
        }

}
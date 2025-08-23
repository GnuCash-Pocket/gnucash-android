package org.gnucash.android.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import java.io.Serializable
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

private const val AccessUriModeFlags =
    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

@SuppressLint("WrongConstant")
fun Context.takePersistableUriPermission(intent: Intent) {
    val uri = intent.data ?: return
    val modeFlags: Int = intent.flags and AccessUriModeFlags
    contentResolver.takePersistableUriPermission(uri, modeFlags)
}

@OptIn(ExperimentalContracts::class)
fun Bundle?.isNullOrEmpty(): Boolean {
    contract {
        returns(false) implies (this@isNullOrEmpty != null)
    }
    return (this == null) || this.isEmpty
}

@Suppress("DEPRECATION")
fun <T : Parcelable> Bundle.getParcelableCompat(key: String, clazz: Class<T>): T? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        return getParcelable<T>(key, clazz)
    }
    return getParcelable<T>(key)
}

fun <T : Parcelable> Intent.getParcelableCompat(key: String, clazz: Class<T>): T? {
    return extras?.getParcelableCompat(key, clazz)
}

fun <T : Serializable> Bundle.getSerializableCompat(key: String, clazz: Class<T>): T? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        return getSerializable(key, clazz)
    }
    return getSerializable(key) as T?
}

@Suppress("DEPRECATION")
fun <T : Parcelable> Bundle.getParcelableArrayCompat(key: String, clazz: Class<T>): Array<T>? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        return getParcelableArray<T>(key, clazz)
    }
    return getParcelableArray(key) as Array<T>?
}

@Suppress("DEPRECATION")
fun <T : Parcelable> Intent.getParcelableArrayCompat(key: String, clazz: Class<T>): Array<T>? {
    return extras?.getParcelableArrayCompat(key, clazz)
}

@Suppress("DEPRECATION")
fun <T : Parcelable> Bundle.getParcelableArrayListCompat(key: String, clazz: Class<T>): ArrayList<T>? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        return getParcelableArrayList<T>(key, clazz)
    }
    return getParcelableArrayList<T>(key)
}

@Suppress("DEPRECATION")
fun <T : Parcelable> Intent.getParcelableArrayListCompat(key: String, clazz: Class<T>): ArrayList<T>? {
    return extras?.getParcelableArrayListCompat(key, clazz)
}

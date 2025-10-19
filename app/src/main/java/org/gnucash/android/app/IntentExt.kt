package org.gnucash.android.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import androidx.core.os.BundleCompat
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

fun <T : Parcelable> Bundle.getParcelableCompat(key: String, clazz: Class<T>): T? {
    return BundleCompat.getParcelable(this, key, clazz)
}

fun <T : Parcelable> Intent.getParcelableCompat(key: String, clazz: Class<T>): T? {
    return extras?.getParcelableCompat(key, clazz)
}

fun <T : Serializable> Bundle.getSerializableCompat(key: String, clazz: Class<T>): T? {
    return BundleCompat.getSerializable(this, key, clazz)
}

fun Bundle.getParcelableArrayCompat(key: String, clazz: Class<Parcelable>): Array<Parcelable>? {
    return BundleCompat.getParcelableArray(this, key, clazz)
}

fun Intent.getParcelableArrayCompat(key: String, clazz: Class<Parcelable>): Array<Parcelable>? {
    return extras?.getParcelableArrayCompat(key, clazz)
}

fun <T : Parcelable> Bundle.getParcelableArrayListCompat(
    key: String,
    clazz: Class<T>
): ArrayList<T>? {
    return BundleCompat.getParcelableArrayList<T>(this, key, clazz)
}

fun <T : Parcelable> Intent.getParcelableArrayListCompat(
    key: String,
    clazz: Class<T>
): ArrayList<T>? {
    return extras?.getParcelableArrayListCompat(key, clazz)
}

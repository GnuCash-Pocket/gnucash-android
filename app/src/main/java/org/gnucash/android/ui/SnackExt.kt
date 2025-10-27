package org.gnucash.android.ui

import android.app.Activity
import android.content.Context
import android.view.View
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import org.gnucash.android.app.getActivity

private fun Int.toToastDuration(): Int {
    return if (this == Snackbar.LENGTH_LONG) {
        Toast.LENGTH_LONG
    } else {
        Toast.LENGTH_SHORT
    }
}

fun Activity.snack(text: CharSequence, duration: Int) {
    Snackbar.make(window.decorView, text, duration).show()
}

fun Activity.snack(@StringRes textId: Int, duration: Int) {
    Snackbar.make(window.decorView, textId, duration).show()
}

fun Activity.snackLong(text: CharSequence) {
    snack(text, Snackbar.LENGTH_LONG)
}

fun Activity.snackLong(@StringRes textId: Int) {
    snack(textId, Snackbar.LENGTH_LONG)
}

fun Activity.snackShort(text: CharSequence) {
    snack(text, Snackbar.LENGTH_SHORT)
}

fun Activity.snackShort(@StringRes textId: Int) {
    snack(textId, Snackbar.LENGTH_SHORT)
}

fun Context.snack(text: CharSequence, duration: Int) {
    val activity = getActivity()
    if (activity != null) {
        activity.snack(text, duration)
    } else {
        Toast.makeText(this, text, duration.toToastDuration()).show()
    }
}

fun Context.snack(@StringRes textId: Int, duration: Int) {
    val activity = getActivity()
    if (activity != null) {
        activity.snack(textId, duration)
    } else {
        Toast.makeText(this, textId, duration.toToastDuration()).show()
    }
}

fun Context.snackLong(text: CharSequence) {
    snack(text, Snackbar.LENGTH_LONG)
}

fun Context.snackLong(@StringRes textId: Int) {
    snack(textId, Snackbar.LENGTH_LONG)
}

fun Context.snackShort(text: CharSequence) {
    snack(text, Snackbar.LENGTH_SHORT)
}

fun Context.snackShort(@StringRes textId: Int) {
    snack(textId, Snackbar.LENGTH_SHORT)
}

fun View.snack(text: CharSequence, duration: Int) {
    Snackbar.make(this, text, duration).show()
}

fun View.snack(@StringRes textId: Int, duration: Int) {
    Snackbar.make(this, textId, duration).show()
}

fun View.snackLong(text: CharSequence) {
    snack(text, Snackbar.LENGTH_LONG)
}

fun View.snackLong(@StringRes textId: Int) {
    snack(textId, Snackbar.LENGTH_LONG)
}

fun View.snackShort(text: CharSequence) {
    snack(text, Snackbar.LENGTH_SHORT)
}

fun View.snackShort(@StringRes textId: Int) {
    snack(textId, Snackbar.LENGTH_SHORT)
}

fun Fragment.snack(text: CharSequence, duration: Int) {
    val view = view
    if (view != null) {
        view.snack(text, duration)
    } else {
        context?.snack(text, duration)
    }
}

fun Fragment.snack(@StringRes textId: Int, duration: Int) {
    val view = view
    if (view != null) {
        view.snack(textId, duration)
    } else {
        context?.snack(textId, duration)
    }
}

fun Fragment.snackLong(text: CharSequence) {
    snack(text, Snackbar.LENGTH_LONG)
}

fun Fragment.snackLong(@StringRes textId: Int) {
    snack(textId, Snackbar.LENGTH_LONG)
}

fun Fragment.snackShort(text: CharSequence) {
    snack(text, Snackbar.LENGTH_SHORT)
}

fun Fragment.snackShort(@StringRes textId: Int) {
    snack(textId, Snackbar.LENGTH_SHORT)
}

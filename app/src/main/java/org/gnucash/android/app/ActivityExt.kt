package org.gnucash.android.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import android.os.PersistableBundle
import android.view.View
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment

tailrec fun Context.getActivity(): Activity? {
    if (this is Activity) {
        return this
    }
    if (this is ContextWrapper) {
        return baseContext.getActivity()
    }
    return null
}

@Throws(IllegalArgumentException::class)
fun Context.findActivity(): Activity {
    val activity = getActivity()
    if (activity != null) {
        return activity
    }
    throw IllegalArgumentException("context has not activity")
}


fun View.getActivity(): Activity = context.getActivity()!!

/**
 * Restart the activity.
 *
 * @param activity   the activity.
 * @param savedState saved state from either [Activity.onSaveInstanceState]
 * or [Activity.onSaveInstanceState].
 */
fun restartActivity(activity: Activity, savedState: Bundle?) {
    val intent: Intent = activity.intent
    val extras: Bundle? = intent.extras
    val args: Bundle = savedState ?: Bundle()
    if (extras != null) {
        args.putAll(extras)
    }
    intent.putExtras(args)
    activity.finish()
    activity.startActivity(intent)
}

/**
 * Restart the activity.
 *
 * @param activity the activity.
 */
fun restartActivity(activity: Activity) {
    val savedState = Bundle()
    val outPersistentState = PersistableBundle()
    activity.onSaveInstanceState(savedState, outPersistentState)
    restartActivity(activity, savedState)
}

fun Activity.restart(savedState: Bundle?) = restartActivity(this, savedState)

fun Activity.restart() = restartActivity(this)

val Fragment.actionBar: ActionBar?
    get() {
        val activity = (activity as? AppCompatActivity) ?: return null
        return activity.supportActionBar
    }

fun Fragment.finish() = activity?.finish()

fun Activity.requireArguments(): Bundle {
    return intent.extras!!
}

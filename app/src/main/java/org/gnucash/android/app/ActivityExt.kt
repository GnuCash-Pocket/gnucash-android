package org.gnucash.android.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import android.os.PersistableBundle

fun Context.findActivity(): Activity {
    if (this is Activity) {
        return this
    }
    if (this is ContextWrapper) {
        return baseContext.findActivity()
    }
    throw IllegalArgumentException("context has not activity")
}

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

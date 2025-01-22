package org.gnucash.android.test.unit

import android.util.Log
import timber.log.Timber

class ConsoleTree : Timber.Tree() {
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (priority >= Log.WARN) {
            System.err.println(message)
        } else {
            println(message)
        }
        t?.printStackTrace()
    }
}
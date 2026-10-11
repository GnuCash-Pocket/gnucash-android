package org.gnucash.android.net

import android.net.Uri
import java.net.URL

fun URL.toUri(): Uri {
    return Uri.parse(toString())
}

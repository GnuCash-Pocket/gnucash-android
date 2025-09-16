package org.gnucash.android.importer

import android.net.Uri

typealias ImportBookCallback = (bookUID: String?) -> Unit
typealias ExportBookCallback = (bookURI: Uri?) -> Unit

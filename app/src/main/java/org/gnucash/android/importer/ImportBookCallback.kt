package org.gnucash.android.importer

interface ImportBookCallback {
    fun onBookImported(bookUID: String?)
}
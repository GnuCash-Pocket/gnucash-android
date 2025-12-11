package org.gnucash.android.importer

class ImportException : RuntimeException {
    constructor(msg: String) :
            super("Failed to import: $msg")

    constructor(throwable: Throwable) : super(
        "Failed to import: ${throwable.message}",
        throwable
    )
}
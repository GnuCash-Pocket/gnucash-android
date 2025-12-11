package org.gnucash.android.export

class ExportException : RuntimeException {
    val params: ExportParams?

    constructor(params: ExportParams, msg: String) :
            super("Failed to export with parameters: $params - $msg") {
        this.params = params
    }

    constructor(params: ExportParams, throwable: Throwable) : super(
        "Failed to export ${params.exportFormat}: ${throwable.message}",
        throwable
    ) {
        this.params = params
    }
}
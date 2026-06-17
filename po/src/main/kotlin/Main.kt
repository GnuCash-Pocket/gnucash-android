package org.gnucash

import java.io.File

fun main(vararg args: String) {
    var folderPotPath = "C:/GitHub/Gnucash/gnucash/po"
    var folderResPath = "./app/src/main/res"

    if (args.isNotEmpty()) {
        folderPotPath = args[0]
        if (args.size > 1) {
            folderResPath = args[1]
        }
    }
    val folderPot = File(folderPotPath)
    val folderRes = File(folderResPath)

    val messages = Messages()
    messages.translate(folderPot, folderRes)
}
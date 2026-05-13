package org.gnucash

import java.io.File
import java.util.Locale

class Messages {
    private val keys = mutableListOf<String>()

    private fun readKeys() {
        keys.clear()
        val res = javaClass.getResource("/msgid")!!
        val text = res.readText(Charsets.UTF_8)
        val lines = text.split("\n")
        keys.addAll(lines.map { it.trim() }.filter { it.isNotEmpty() })
        keys.sort()
    }

    fun translate(folderPot: File, folderRes: File) {
        readKeys()

        println("Translate en-US")
        val defaultEnglish = translateDefault()
        val folderValues = File(folderRes, "values")
        writeValues(folderValues, defaultEnglish)

        val files = folderPot.listFiles { it.extension == "po" } ?: return
        files.forEach { file -> translatePot(file, folderRes) }
    }

    // msgid = msgstr
    private fun translateDefault(): Map<String, String> {
        val translations = mutableMapOf<String, String>()

        keys.forEach { msgid ->
            val msgstr = msgid
            val translation = msgstr.substring(msgstr.indexOf('"') + 1, msgstr.lastIndexOf('"'))
            if (translation.isNotEmpty()) {
                translations[msgid] = translation
            }
        }

        return translations
    }

    private fun translatePot(filePot: File, folderApp: File) {
        val fileLanguageCode = filePot.nameWithoutExtension.replace("_", "-")
        val locale = Locale.forLanguageTag(fileLanguageCode)
        val languageCode = locale.language
        if (languageCode.length != 2) return
        println("Translate $locale")

        val translations = read(filePot, keys)
        write(folderApp, locale, translations)
    }

    private fun read(file: File, keys: List<String>): Map<String, String> {
        val translations = mutableMapOf<String, String>()

        val lines = file.readLines(Charsets.UTF_8)
        keys.forEach { msgid ->
            val index = lines.indexOf(msgid)
            if (index > 0) {
                val msgstr = lines[index + 1]
                if (msgstr.startsWith("msgstr ")) {
                    val translation =
                        msgstr.substring(msgstr.indexOf('"') + 1, msgstr.lastIndexOf('"'))
                    if (translation.isNotEmpty()) {
                        translations[msgid] = translation
                    }
                }
            }
        }

        return translations
    }

    private fun write(folderRes: File, locale: Locale, translations: Map<String, String>) {
        val languageCode = toAndroidLanguage(locale)
        val folderValues = File(folderRes, "values-${languageCode}")
        writeValues(folderValues, translations)
    }

    private fun writeValues(folderValues: File, translations: Map<String, String>) {
        folderValues.mkdirs()
        val fileStrings = File(folderValues, "strings-po.xml")

        fileStrings.writer(Charsets.UTF_8).use { writer ->
            writer.write("<resources xmlns:xliff=\"urn:oasis:names:tc:xliff:document:1.2\">\n")
            translations.forEach { (key, value) ->
                val name = toAndroidName(key)
                writer.write("    <string name=\"${name}\">\"${value}\"</string>\n")
            }
            writer.write("</resources>")
        }
    }

    private fun toAndroidName(key: String): String {
        return "gnucash_po_" + key.substring(key.indexOf('"') + 1, key.lastIndexOf('"'))
            .lowercase(Locale.ROOT)
            .replace("-", "_")
            .replace(" ", "_")
            .replace("/", "_")
            .replace(".", "")
    }

    private fun toAndroidLanguage(locale: Locale): String {
        val language = locale.language
        val countryCode = locale.country

        val languageCode = when (language) {
            "he" -> "iw"
            "id" -> "in"
            "yi" -> "ji"
            else -> language
        }

        return if (countryCode.isEmpty()) {
            languageCode
        } else {
            "${languageCode}-r${countryCode}"
        }
    }
}
package org.gnucash.android.model

import org.gnucash.android.export.xml.GncXmlHelper.formatNumeric

data class Slot(
    var key: String,
    var type: String,
    var value: Any? = null
) {
    val isDate: Boolean get() = (type == TYPE_GDATE) && (value is Long)

    val isFrame: Boolean get() = (type == TYPE_FRAME) && (value is List<*>)

    val isGUID: Boolean get() = (type == TYPE_GUID) && (value!!.toString().length == 32)

    val isNumeric: Boolean get() = (type == TYPE_NUMERIC) && (value!!.toString().indexOf('/') > 0)

    val isString: Boolean get() = (type == TYPE_STRING) && ((value is String) || (value is String?))

    val asDate: Long
        get() = if (isDate) value as Long
        else throw TypeCastException(type)

    val asFrame: List<Slot>
        get() = if (isFrame) value as List<Slot>
        else throw TypeCastException(type)

    val asGUID: String
        get() = if (isGUID) value as String
        else throw TypeCastException(type)

    val asNumeric: String
        get() = if (isNumeric) value as String
        else throw TypeCastException(type)

    val asString: String
        get() = if (isString) value as String
        else throw TypeCastException(type)

    override fun toString(): String {
        return value.toString()
    }

    fun add(slot: Slot) {
        if (type == TYPE_FRAME) {
            if (value == null) {
                value = listOf(slot)
            } else {
                value = (value as List<*>) + slot
            }
        }
    }

    companion object {
        const val TYPE_FRAME = "frame"
        const val TYPE_GDATE = "gdate"
        const val TYPE_GUID = "guid"
        const val TYPE_NUMERIC = "numeric"
        const val TYPE_STRING = "string"

        fun frame(key: String, slots: List<Slot>): Slot = Slot(key, TYPE_FRAME, slots)

        fun gdate(key: String, date: Long): Slot = Slot(key, TYPE_GDATE, date)

        fun guid(key: String, guid: String): Slot = Slot(key, TYPE_GUID, guid)

        fun numeric(key: String, numerator: Long, denominator: Long): Slot =
            Slot(key, TYPE_NUMERIC, formatNumeric(numerator, denominator))

        fun numeric(key: String, numerator: String, denominator: String): Slot =
            numeric(key, numerator.toLong(), denominator.toLong())

        fun string(key: String, value: String): Slot = Slot(key, TYPE_STRING, value)

        fun numeric(key: String, value: Money) = Slot(key, TYPE_NUMERIC, formatNumeric(value))

        fun empty() = Slot("", TYPE_STRING)
    }
}

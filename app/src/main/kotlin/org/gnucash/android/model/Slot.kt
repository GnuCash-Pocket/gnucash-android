package org.gnucash.android.model

data class Slot @JvmOverloads constructor(
    @JvmField
    val key: String,
    @JvmField
    val type: String,
    @JvmField
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

    companion object {
        const val TYPE_FRAME: String = "frame"
        const val TYPE_GDATE: String = "gdate"
        const val TYPE_GUID: String = "guid"
        const val TYPE_NUMERIC: String = "numeric"
        const val TYPE_STRING: String = "string"

    }
}

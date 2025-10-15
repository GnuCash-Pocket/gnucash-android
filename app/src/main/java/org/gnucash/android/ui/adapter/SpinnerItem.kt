package org.gnucash.android.ui.adapter

class SpinnerItem<T>(
    val value: T,
    val label: String = value.toString()
) {
    override fun toString(): String {
        return label
    }

    override fun equals(other: Any?): Boolean {
        if (other is SpinnerItem<T>) {
            return this.value == other.value
        }
        return super.equals(other)
    }

    override fun hashCode(): Int {
        var result = label.hashCode()
        result = 31 * result + (value?.hashCode() ?: 0)
        return result
    }
}
package org.gnucash.android.math

import android.os.Build
import android.os.Parcel
import java.math.BigDecimal
import kotlin.math.log10

val BigDecimal.isZero get() = compareTo(BigDecimal.ZERO) == 0

operator fun BigDecimal.times(factor: Double): BigDecimal {
    return this.multiply(BigDecimal.valueOf(factor))
}

fun Parcel.writeBigDecimal(value: BigDecimal) {
    writeSerializable(value)
}

fun Parcel.readBigDecimal(): BigDecimal? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        readSerializable(javaClass.classLoader, BigDecimal::class.java)
    } else {
        @Suppress("DEPRECATION")
        readSerializable() as? BigDecimal
    }
}

val Number.numberOfTrailingZeros: Int
    get() {
        val v = this.toDouble()
        if (v <= 1) return 0
        return log10(v).toInt()
    }

/**
 * Returns the [BigDecimal] from the `numerator` and `denominator`
 *
 * @param numerator   Numerator of the fraction
 * @param denominator Denominator of the fraction
 * @return BigDecimal representation of the number
 */
fun toBigDecimal(numerator: Long, denominator: Long): BigDecimal {
    return BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator))
}
package org.gnucash.android.math

import android.os.Build
import android.os.Parcel
import org.gnucash.android.model.Numeric
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import kotlin.math.log10
import kotlin.math.max

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
    // Assume denominator is multiple of "10"s only.
    val scale = denominator.numberOfTrailingZeros
    return BigDecimal.valueOf(numerator, scale)
}

/**
 * Returns the [BigDecimal] from the `numerator` and `denominator`
 *
 * @param numerator   Numerator of the fraction
 * @param denominator Denominator of the fraction
 * @return BigDecimal representation of the number
 */
fun toBigDecimal(numerator: BigInteger, denominator: Long): BigDecimal {
    // Assume denominator is multiple of "10"s only.
    val scale = denominator.numberOfTrailingZeros
    return BigDecimal(numerator, scale)
}

/**
 * Returns the [BigDecimal] from the `numerator` and `denominator`
 *
 * @param numeric  Numeric with numerator and denominator.
 * @return BigDecimal representation of the number
 */
fun toBigDecimal(numeric: Numeric): BigDecimal {
    return toBigDecimal(numeric.numerator, numeric.denominator)
}

fun BigDecimal.equalsIgnoreScale(that: BigDecimal): Boolean {
    val thisScale = this.scale()
    val thatScale = that.scale()
    val scale = max(thisScale, thatScale)
    val thisAmount = if (thisScale == scale)
        this
    else
        this.setScale(scale, RoundingMode.UNNECESSARY)
    val thatAmount = if (thatScale == scale)
        that
    else
        that.setScale(scale, RoundingMode.UNNECESSARY)
    return thisAmount == thatAmount
}
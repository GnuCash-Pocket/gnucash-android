package org.gnucash.android.inputmethodservice

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.AndroidUiModes.UI_MODE_NIGHT_YES
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.gnucash.android.R
import org.gnucash.android.compose.icon.Icons
import org.gnucash.android.ui.theme.GnucashTheme
import java.text.DecimalFormat
import kotlin.math.roundToInt

private val KbdBg = Color(0xEEF2F8F2)
private val KbdKeyAction = Color(0xFFD7FFA8)
private val KbdKeyClear = Color(0xFFCFE0BC)
private val KbdKeyDelete = Color(0xFFCFE0BC)
private val KbdKeyDigit = Color.White
private val KbdKeyOp = Color(0xFFE5F5D5)

private val KbdBgNight = Color(0xEE1F1F23)
private val KbdKeyActionNight = Color(0xFF98B858)
private val KbdKeyClearNight = Color(0xFF67734E)
private val KbdKeyDeleteNight = Color(0xFF67734E)
private val KbdKeyDigitNight = Color(0xFF343538)
private val KbdKeyOpNight = Color(0xFFA5D6A7)

private val buttonShape = RoundedCornerShape(25.dp)
private val buttonMarginX = 2.dp
private val buttonMarginY = 2.dp
private val buttonHeightDefault = 60.dp
private val buttonHeightShort = 40.dp
private val buttonWidthMax = 120.dp

private val keypadPadding = 4.dp
private val keypadHeight = (buttonHeightDefault + buttonMarginY) * 5 + keypadPadding
private val windowHeightMinimum = keypadHeight * 1.3f
internal val KeypadWidthWide = (buttonWidthMax + buttonMarginX) * 4 + keypadPadding

@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun CalculatorKeypad(
    modifier: Modifier = Modifier,
    onText: (String) -> Unit = {},
    onClear: () -> Unit = {},
    onDelete: () -> Unit = {},
    onEvaluate: () -> Unit = {}
) {
    val numbers = remember { DecimalFormat() }
    val symbols = remember { numbers.decimalFormatSymbols }
    val decimalSeparator = remember { symbols.monetaryDecimalSeparator.toString() }
    val minusSign = remember { symbols.minusSign.toString() }
    val digits = remember { (0L..9L).map { numbers.format(it) } }

    val isDark = isSystemInDarkTheme()
    val bgColor = if (isDark) KbdBgNight else KbdBg

    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val windowHeightMinimumDp = windowHeightMinimum.value.roundToInt()
    val isTall = screenHeightDp >= windowHeightMinimumDp
    val buttonHeight = if (isTall) buttonHeightDefault else buttonHeightShort

    Column(
        modifier = modifier
            .background(bgColor)
            .padding(keypadPadding),
        verticalArrangement = Arrangement.spacedBy(buttonMarginY)
    ) {
        KeyRow {
            KeypadButton(
                icon = Icons.Material.DeleteForever,
                contentDescription = stringResource(com.codetroopers.betterpickers.R.string.searchview_description_clear),
                type = KeyType.Clear,
                buttonHeight = buttonHeight,
                onClick = onClear
            )
            KeypadButton("(", buttonHeight = buttonHeight, onClick = { onText("(") })
            KeypadButton(")", buttonHeight = buttonHeight, onClick = { onText(")") })
            KeypadButton(
                icon = Icons.Material.Divide,
                contentDescription = "÷",
                type = KeyType.Operator,
                buttonHeight = buttonHeight,
                onClick = { onText("÷") }
            )
        }
        KeyRow {
            KeypadButton(digits[7], buttonHeight = buttonHeight, onClick = { onText(digits[7]) })
            KeypadButton(digits[8], buttonHeight = buttonHeight, onClick = { onText(digits[8]) })
            KeypadButton(digits[9], buttonHeight = buttonHeight, onClick = { onText(digits[9]) })
            KeypadButton(
                icon = Icons.Material.Multiply,
                contentDescription = "×",
                type = KeyType.Operator,
                buttonHeight = buttonHeight,
                onClick = { onText("×") }
            )
        }
        KeyRow {
            KeypadButton(digits[4], buttonHeight = buttonHeight, onClick = { onText(digits[4]) })
            KeypadButton(digits[5], buttonHeight = buttonHeight, onClick = { onText(digits[5]) })
            KeypadButton(digits[6], buttonHeight = buttonHeight, onClick = { onText(digits[6]) })
            KeypadButton(
                icon = Icons.Material.Minus,
                contentDescription = minusSign,
                type = KeyType.Operator,
                buttonHeight = buttonHeight,
                onClick = { onText(minusSign) }
            )
        }
        KeyRow {
            KeypadButton(digits[1], buttonHeight = buttonHeight, onClick = { onText(digits[1]) })
            KeypadButton(digits[2], buttonHeight = buttonHeight, onClick = { onText(digits[2]) })
            KeypadButton(digits[3], buttonHeight = buttonHeight, onClick = { onText(digits[3]) })
            KeypadButton(
                icon = Icons.Material.Add,
                contentDescription = "+",
                type = KeyType.Operator,
                buttonHeight = buttonHeight,
                onClick = { onText("+") }
            )
        }
        KeyRow {
            KeypadButton(digits[0], buttonHeight = buttonHeight, onClick = { onText(digits[0]) })
            KeypadButton(
                decimalSeparator,
                buttonHeight = buttonHeight,
                onClick = { onText(decimalSeparator) })
            KeypadButton(
                icon = Icons.Material.Backspace,
                contentDescription = stringResource(R.string.menu_delete),
                type = KeyType.Delete,
                buttonHeight = buttonHeight,
                onClick = onDelete
            )
            KeypadButton(
                icon = Icons.Material.Equal,
                type = KeyType.Action,
                buttonHeight = buttonHeight,
                onClick = onEvaluate
            )
        }
    }
}

@Composable
private fun KeyRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(buttonMarginX),
        content = content
    )
}

enum class KeyType {
    Digit, Operator, Clear, Delete, Action
}

@Composable
private fun RowScope.KeypadButton(
    text: String? = null,
    icon: ImageVector? = null,
    contentDescription: String? = null,
    type: KeyType = KeyType.Digit,
    buttonHeight: Dp = buttonHeightDefault,
    onClick: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val containerColor = when (type) {
        KeyType.Digit -> if (isDark) KbdKeyDigitNight else KbdKeyDigit
        KeyType.Operator -> if (isDark) KbdKeyOpNight else KbdKeyOp
        KeyType.Clear -> if (isDark) KbdKeyClearNight else KbdKeyClear
        KeyType.Delete -> if (isDark) KbdKeyDeleteNight else KbdKeyDelete
        KeyType.Action -> if (isDark) KbdKeyActionNight else KbdKeyAction
    }
    val textColor = if (isDark) Color.White else Color.Black

    Button(
        onClick = onClick,
        modifier = Modifier
            .height(buttonHeight)
            .weight(1f),
        shape = buttonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = textColor
        ),
        elevation = null,
    ) {
        if (icon != null) {
            Icon(
                modifier = Modifier.size(30.dp),
                imageVector = icon,
                contentDescription = contentDescription
            )
        } else if (text != null) {
            Text(text = text, fontSize = 24.sp)
        }
    }
}

@Composable
@Preview(widthDp = 400)
@Preview(widthDp = 800)
@Preview(widthDp = 400, uiMode = UI_MODE_NIGHT_YES)
@Preview(widthDp = 800, uiMode = UI_MODE_NIGHT_YES)
private fun Preview() {
    GnucashTheme {
        CalculatorKeypad()
    }
}

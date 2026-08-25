package org.gnucash.android.compose.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

@Suppress("CheckReturnValue")
public val divide: ImageVector
    get() {
        if (_divide != null) {
            return _divide!!
        }
        _divide = ImageVector.Builder(
            name = "percent",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            group(rotate = 45f, pivotX = 12f, pivotY = 12f) {
                percentPath()
            }
        }
            .build()
        return _divide!!
    }

private var _divide: ImageVector? = null
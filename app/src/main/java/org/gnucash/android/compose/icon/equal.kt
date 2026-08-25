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
public val equal: ImageVector
    get() {
        if (_equal != null) {
            return _equal!!
        }
        _equal = ImageVector.Builder(
            name = "equal",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.Companion.NonZero,
            ) {
                moveTo(4f, 17f)
                verticalLineTo(14f)
                horizontalLineTo(20f)
                verticalLineToRelative(3f)
                horizontalLineTo(4f)
                close()
                moveTo(4f, 10f)
                verticalLineTo(7f)
                horizontalLineTo(20f)
                verticalLineToRelative(3f)
                horizontalLineTo(4f)
                close()
            }
        }
            .build()
        return _equal!!
    }

private var _equal: ImageVector? = null
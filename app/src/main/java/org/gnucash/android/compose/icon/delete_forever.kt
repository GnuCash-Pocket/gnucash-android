package org.gnucash.android.compose.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

@Suppress("CheckReturnValue")
public val delete_forever: ImageVector
    get() {
        if (_delete_forever != null) {
            return _delete_forever!!
        }
        _delete_forever = ImageVector.Builder(
            name = "delete_forever",
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
                moveTo(9.4f, 16.5f)
                lineTo(12f, 13.9f)
                lineToRelative(2.6f, 2.6f)
                lineTo(16f, 15.1f)
                lineTo(13.4f, 12.5f)
                lineTo(16f, 9.9f)
                lineTo(14.6f, 8.5f)
                lineTo(12f, 11.1f)
                lineTo(9.4f, 8.5f)
                lineTo(8f, 9.9f)
                lineToRelative(2.6f, 2.6f)
                lineTo(8f, 15.1f)
                lineToRelative(1.4f, 1.4f)
                close()
                moveTo(7f, 21f)
                quadTo(6.18f, 21f, 5.59f, 20.41f)
                reflectiveQuadTo(5f, 19f)
                verticalLineTo(6f)
                horizontalLineTo(4f)
                verticalLineTo(4f)
                horizontalLineTo(9f)
                verticalLineTo(3f)
                horizontalLineToRelative(6f)
                verticalLineTo(4f)
                horizontalLineToRelative(5f)
                verticalLineTo(6f)
                horizontalLineTo(19f)
                verticalLineTo(19f)
                quadToRelative(0f, 0.82f, -0.59f, 1.41f)
                reflectiveQuadTo(17f, 21f)
                horizontalLineTo(7f)
                close()
                moveTo(17f, 6f)
                horizontalLineTo(7f)
                verticalLineTo(19f)
                horizontalLineTo(17f)
                verticalLineTo(6f)
                close()
                moveTo(7f, 6f)
                verticalLineTo(19f)
                verticalLineTo(6f)
                close()
            }
        }
            .build()
        return _delete_forever!!
    }

private var _delete_forever: ImageVector? = null
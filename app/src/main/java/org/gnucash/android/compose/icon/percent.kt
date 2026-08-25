package org.gnucash.android.compose.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

internal fun ImageVector.Builder.percentPath() = path(
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
    moveTo(7.5f, 11f)
    quadTo(6.05f, 11f, 5.03f, 9.98f)
    reflectiveQuadTo(4f, 7.5f)
    reflectiveQuadTo(5.03f, 5.02f)
    reflectiveQuadTo(7.5f, 4f)
    reflectiveQuadTo(9.98f, 5.02f)
    reflectiveQuadTo(11f, 7.5f)
    reflectiveQuadTo(9.98f, 9.98f)
    reflectiveQuadTo(7.5f, 11f)
    close()
    moveToRelative(0f, -2f)
    quadTo(8.13f, 9f, 8.56f, 8.56f)
    reflectiveQuadTo(9f, 7.5f)
    reflectiveQuadTo(8.56f, 6.44f)
    reflectiveQuadTo(7.5f, 6f)
    reflectiveQuadTo(6.44f, 6.44f)
    reflectiveQuadTo(6f, 7.5f)
    reflectiveQuadTo(6.44f, 8.56f)
    reflectiveQuadTo(7.5f, 9f)
    close()
    moveToRelative(9f, 11f)
    quadToRelative(-1.45f, 0f, -2.47f, -1.02f)
    reflectiveQuadTo(13f, 16.5f)
    reflectiveQuadToRelative(1.03f, -2.48f)
    reflectiveQuadTo(16.5f, 13f)
    reflectiveQuadToRelative(2.48f, 1.02f)
    reflectiveQuadTo(20f, 16.5f)
    reflectiveQuadToRelative(-1.02f, 2.48f)
    reflectiveQuadTo(16.5f, 20f)
    close()
    moveToRelative(1.06f, -2.44f)
    quadTo(18f, 17.13f, 18f, 16.5f)
    reflectiveQuadTo(17.56f, 15.44f)
    reflectiveQuadTo(16.5f, 15f)
    reflectiveQuadToRelative(-1.06f, 0.44f)
    reflectiveQuadTo(15f, 16.5f)
    reflectiveQuadToRelative(0.44f, 1.06f)
    reflectiveQuadTo(16.5f, 18f)
    reflectiveQuadToRelative(1.06f, -0.44f)
    close()
    moveTo(5.4f, 20f)
    lineTo(4f, 18.6f)
    lineTo(18.6f, 4f)
    lineTo(20f, 5.4f)
    lineTo(5.4f, 20f)
    close()
}

@Suppress("CheckReturnValue")
public val percent: ImageVector
    get() {
        if (_percent != null) {
            return _percent!!
        }
        _percent = ImageVector.Builder(
            name = "percent",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            percentPath()
        }
            .build()
        return _percent!!
    }

private var _percent: ImageVector? = null
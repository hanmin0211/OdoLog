package com.example.pleoshello

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

// 버튼 아이콘: Pleos Connect 기본 화면처럼 가는 선(둥근 끝) 아이콘. 24×24 격자에 선만 그리고, 색은 쓰는 쪽에서 입힘
object AppIcons {
    val Back = line("back", "M15 5L8 12L15 19")
    val Home = line("home", "M4 11L12 4L20 11", "M6 9.5V20H18V9.5", "M10 20V14.5H14V20")
    val Trips = line("trips", "M9 7H20", "M9 12H20", "M9 17H20", "M4.5 7H5", "M4.5 12H5", "M4.5 17H5")
    val Export = line("export", "M12 15V4", "M7.5 8.5L12 4L16.5 8.5", "M5 14V19H19V14")
    val Save = line("save", "M12 4V15", "M7.5 10.5L12 15L16.5 10.5", "M5 19.5H19")
    val Preview = line("preview", "M2.5 12C5 7 8.5 5 12 5C15.5 5 19 7 21.5 12C19 17 15.5 19 12 19C8.5 19 5 17 2.5 12Z", circle(12f, 12f, 3f))
    val Filter = line("filter", "M4 5H20L14 12.5V18.5L10 20V12.5Z")
    val People = line(
        "people", circle(9f, 8f, 3.2f), "M3.5 19.5C3.5 15.8 6 13.8 9 13.8C12 13.8 14.5 15.8 14.5 19.5",
        "M15.5 5.2A3 3 0 0 1 15.5 10.8", "M17.5 14.2C19.5 14.8 20.8 16.7 20.8 19.5"
    )
    val Person = line("person", circle(12f, 8f, 3.6f), "M5 20C5 16 8 13.8 12 13.8C16 13.8 19 16 19 20")
    val Edit = line("edit", "M4 20L5 15.5L15.2 5.3A1.6 1.6 0 0 1 17.5 5.3L18.7 6.5A1.6 1.6 0 0 1 18.7 8.8L8.5 19Z", "M13.5 7L17 10.5")
    val Plus = line("plus", "M12 5V19", "M5 12H19")
    val Check = line("check", "M5 12.5L10 17L19 7.5")
    val Trash = line("trash", "M4 7H20", "M9 7V4.5H15V7", "M6.5 7L7.5 20H16.5L17.5 7", "M10 11V16", "M14 11V16")
    val Backup = line(
        "backup", "M7 18.5H17A3.8 3.8 0 0 0 17.6 11A5.6 5.6 0 0 0 6.8 9.8A4.4 4.4 0 0 0 7 18.5Z",
        "M12 16V11", "M9.8 13.2L12 11L14.2 13.2"
    )
    val Restore = line("restore", "M4.5 12A7.5 7.5 0 1 0 7 6.4", "M4.2 4.2V8.3H8.3", "M12 8.5V12L14.6 13.8")
    val Clock = line("clock", circle(12f, 12f, 8f), "M12 7.5V12L15 14")
    val Info = line("info", circle(12f, 12f, 8.5f), "M12 11V16.5", "M12 7.8V8")
    val Commute = line("commute", "M3.5 11L9 6.5L14.5 11", "M5 10V18H13V10", "M15.5 18H20.5V8.5H16.5", "M17.5 11.5H18.5", "M17.5 14.5H18.5")
    val Work = line("work", "M4 8H20V19H4Z", "M9 8V5.5H15V8", "M4 13H20")
    val Rotate = line("rotate", "M19.5 12A7.5 7.5 0 1 1 17 6.4", "M19.8 4.2V8.3H15.7")
    val Lock =line("lock", "M6 11H18V20H6Z", "M8.5 11V8A3.5 3.5 0 0 1 15.5 8V11", "M12 14.5V16.5")
    val Settings =line("settings", gear(), circle(12f, 12f, 3f))
}

// 선 아이콘 하나 만들기 (여러 path 문자열 = 여러 획)
private fun line(name: String, vararg paths: String): ImageVector {
    val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
    paths.forEach {
        b.addPath(
            pathData = addPathNodes(it), fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        )
    }
    return b.build()
}

private fun circle(cx: Float, cy: Float, r: Float) =
    String.format(Locale.US, "M%.2f %.2fA%.2f %.2f 0 1 0 %.2f %.2fA%.2f %.2f 0 1 0 %.2f %.2fZ", cx - r, cy, r, r, cx + r, cy, r, r, cx - r, cy)

// 톱니바퀴 바깥선: 톱니 8개 (톱니마다 안쪽→바깥→바깥→안쪽 네 점)
private fun gear(): String {
    val pts = (0 until 8).flatMap { i ->
        val a = i * 45.0
        listOf(a - 20 to 7.0, a - 10 to 9.6, a + 10 to 9.6, a + 20 to 7.0)
    }.map { (deg, r) ->
        val rad = Math.toRadians(deg - 90)
        12 + r * cos(rad) to 12 + r * sin(rad)
    }
    return pts.mapIndexed { i, (x, y) -> String.format(Locale.US, "%s%.2f %.2f", if (i == 0) "M" else "L", x, y) }.joinToString("") + "Z"
}

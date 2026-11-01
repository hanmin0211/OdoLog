package com.example.pleoshello

import androidx.compose.ui.graphics.Color

// 디자인 토큰: 색에 이름을 붙여 한곳에서 관리 (캔버스 시안과 같은 값)
data class AppColors(
    val bg: Color, val surface: Color, val line: Color, val track: Color,
    val text: Color, val sub: Color, val body: Color,
    val accent: Color, val accentText: Color, val ok: Color,
    val commute: Color, val work: Color, val personal: Color,
    val warn: Color, val warnText: Color,
)

val DarkColors = AppColors(
    bg = Color(0xFF0D1117), surface = Color(0xFF161B22), line = Color(0xFF2D3440), track = Color(0xFF2D3440),
    text = Color(0xFFF0F3F6), sub = Color(0xFF9BA6B2), body = Color(0xFFC9D1D9),
    accent = Color(0xFF1F6FEB), accentText = Color(0xFF79C0FF), ok = Color(0xFF3FB950),
    commute = Color(0xFF1F6FEB), work = Color(0xFF79C0FF), personal = Color(0xFF6E7681),
    warn = Color(0xFFF0883E), warnText = Color(0xFFF0883E),
)

val LightColors = AppColors(
    bg = Color(0xFFF4F6F8), surface = Color(0xFFFFFFFF), line = Color(0xFFD0D7DE), track = Color(0xFFE1E6EB),
    text = Color(0xFF1F2328), sub = Color(0xFF59636E), body = Color(0xFF31373D),
    accent = Color(0xFF0969DA), accentText = Color(0xFF0969DA), ok = Color(0xFF1A7F37),
    commute = Color(0xFF0969DA), work = Color(0xFF54AEFF), personal = Color(0xFF8C959F),
    warn = Color(0xFFE16F24), warnText = Color(0xFFBC4C00),
)
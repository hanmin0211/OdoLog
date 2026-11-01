package com.example.pleoshello

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 운행 상태 화면: 지금 기록이 어떻게 되고 있는지만 보여줌 (현재 상태, 최근 운행, 저장 현황, 차량 신호)
// 사업자 정보·직원 명단·백업·초기화 같은 관리 기능은 설정 화면에 모아 둠
@Composable
fun StatusScreen(colors: AppColors, items: List<Pair<String, String>>, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(colors.bg).padding(48.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            PillButton(AppIcons.Back, "홈", colors, onClick = onBack)
            Text("운행 상태", color = colors.text, fontSize = 40.sp, fontWeight = FontWeight.Bold)
        }
        items.forEach { (label, value) ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.surface)
                    .border(1.dp, colors.line, RoundedCornerShape(20.dp))
                    .padding(horizontal = 28.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(label, color = colors.sub, fontSize = 20.sp)
                Text(value, color = colors.text, fontSize = 26.sp)
            }
        }
    }
}

@Preview(name = "운행 상태", widthDp = 1200, heightDp = 840)
@Composable
fun StatusPreview() {
    StatusScreen(
        DarkColors,
        listOf(
            "현재 상태" to "대기 중 · 누적 주행거리 48,242.5 km",
            "최근 운행" to "운행 종료 · 오후 2:02 → 오후 2:41 · 28.9 km · 저장됨",
            "저장된 운행" to "저장된 운행 5건 · 마지막 10월 22일 (목) 오후 2:02 28.9 km · 일반 업무",
            "기어·속도 (표준 API)" to "기어 출처 표준 · 기어 P · 속도 0.0 km/h",
        )
    ) {}
}

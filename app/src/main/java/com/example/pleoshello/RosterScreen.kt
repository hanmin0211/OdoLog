package com.example.pleoshello

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 직원 명단 관리: 공유 차량을 운전하는 직원을 등록하면 운행이 끝날 때 운전자를 고를 수 있음
@Composable
// faces = 얼굴을 등록한 직원 이름, onFace = 얼굴 등록·다시 등록 화면 열기
fun RosterScreen(
    colors: AppColors, drivers: List<Driver>, onAdd: (Driver) -> Unit, onRemove: (Driver) -> Unit, onBack: () -> Unit,
    faces: Set<String> = emptySet(), onFace: (Driver) -> Unit = {},
) {
    var name by remember { mutableStateOf("") }
    var dept by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf<Driver?>(null) }
    val cleanedName = cleanName(name)
    val duplicate = drivers.any { it.name == cleanedName }
    Column(
        Modifier.fillMaxSize().background(colors.bg).imePadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 48.dp, vertical = 36.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            PillButton(AppIcons.Back, "설정", colors, onClick = onBack)
            Column {
                Text("직원 명단", color = colors.text, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                Text("등록한 직원 중에서 운행마다 운전자를 고릅니다 (운행기록부의 부서·성명 칸)", color = colors.sub, fontSize = 20.sp)
            }
        }
        // 추가 줄: 이름 + 부서 + 추가 버튼
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            InputBox("이름", name, "예: 김하늘", colors, Modifier.weight(1f)) { name = it }
            InputBox("부서 (선택)", dept, "예: 총무팀", colors, Modifier.weight(1f)) { dept = it }
            PillButton(if (duplicate) AppIcons.Info else AppIcons.Plus, if (duplicate) "이미 있음" else "추가", colors) {
                if (cleanedName.isNotEmpty() && !duplicate) {
                    onAdd(Driver(cleanedName, cleanName(dept)))
                    name = ""
                    dept = ""
                }
            }
        }
        Text("등록된 직원 ${drivers.size}명", color = colors.sub, fontSize = 22.sp)
        drivers.forEach { d ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.surface)
                    .border(1.dp, colors.line, RoundedCornerShape(20.dp)).padding(start = 28.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(d.name, color = colors.text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text(d.dept.ifBlank { "부서 없음" }, color = colors.sub, fontSize = 22.sp, modifier = Modifier.weight(1f))
                if (d.name in faces) PillButton(AppIcons.Check, "얼굴 등록됨", colors, tint = colors.ok) { onFace(d) }
                else PillButton(AppIcons.Person, "얼굴 등록", colors, tint = colors.accentText) { onFace(d) }
                // 실수 방지: 두 번 눌러야 삭제 (이미 저장된 운행 기록의 이름은 그대로 남음)
                PillButton(AppIcons.Trash, if (confirm == d) "한 번 더 누르면 삭제" else "삭제", colors, tint = if (confirm == d) colors.warnText else colors.text) {
                    if (confirm == d) {
                        onRemove(d)
                        confirm = null
                    } else {
                        confirm = d
                    }
                }
            }
        }
    }
}

@Composable
private fun InputBox(label: String, value: String, hint: String, colors: AppColors, modifier: Modifier, onChange: (String) -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = colors.sub, fontSize = 22.sp)
        HangulTextField(
            value = value,
            onValueChange = onChange,
            textStyle = TextStyle(color = colors.text, fontSize = 28.sp),
            cursorBrush = SolidColor(colors.accent),
            colors = colors,
            decorationBox = { inner ->
                Box(
                    Modifier.fillMaxWidth().height(76.dp).clip(RoundedCornerShape(18.dp)).background(colors.surface)
                        .border(1.dp, colors.line, RoundedCornerShape(18.dp)).padding(horizontal = 24.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (value.isEmpty()) Text(hint, color = colors.personal, fontSize = 26.sp)
                    inner()
                }
            }
        )
    }
}

@Preview(name = "직원 명단", widthDp = 1200, heightDp = 840)
@Composable
fun RosterPreview() {
    RosterScreen(DarkColors, listOf(Driver("강지민", "영업팀"), Driver("김하늘", "총무팀")), onAdd = {}, onRemove = {}, onBack = {}, faces = setOf("강지민"))
}
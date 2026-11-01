package com.example.pleoshello

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 설정 화면: 사업자·차량 정보, 직원 명단, 백업을 한곳에 모음 (홈의 톱니바퀴 버튼)
// 복원·초기화는 운행 기록을 지우거나 되돌리므로 관리자 비밀번호로 잠금 (운전자가 기록을 임의로 없애는 것 방지).
// 설정 화면을 나가면 다시 잠기고, 실행할 때도 실수 방지를 위해 두 번 눌러야 함
@Composable
fun SettingsScreen(
    colors: AppColors, profileText: String, rosterText: String, backups: List<String>, message: String,
    adminSet: Boolean, onSetPin: (String) -> Unit, onCheckPin: (String) -> String?,
    onEditProfile: () -> Unit, onOpenRoster: () -> Unit, onBackup: () -> Unit, onRestore: (Int) -> Unit, onReset: () -> Unit,
    onBack: () -> Unit,
) {
    var unlocked by remember { mutableStateOf(false) }
    var entering by remember { mutableStateOf(false) }   // 비밀번호 입력 줄 표시
    var pin by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf("") }
    var confirmRestore by remember { mutableStateOf(-1) }
    var confirmReset by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().background(colors.bg).imePadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 48.dp, vertical = 36.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            PillButton(AppIcons.Back, "홈", colors, onClick = onBack)
            Text("설정", color = colors.text, fontSize = 40.sp, fontWeight = FontWeight.Bold)
        }
        if (message.isNotEmpty()) Text(message, color = colors.accentText, fontSize = 22.sp)

        SettingCard(AppIcons.Person, "사업자·차량 정보", profileText, colors) {
            PillButton(AppIcons.Edit, "수정", colors, onClick = onEditProfile)
        }
        SettingCard(AppIcons.People, "직원 명단", rosterText, colors) {
            PillButton(AppIcons.People, "관리", colors, onClick = onOpenRoster)
        }
        SettingCard(
            AppIcons.Backup, "백업",
            (if (backups.isEmpty()) "아직 백업이 없습니다" else "마지막 백업 ${backups.first()} · ${backups.size}개 보관") +
                " · 차량 안에 보관하고 다운로드/OdoLog 폴더에도 저장합니다",
            colors
        ) {
            PillButton(AppIcons.Backup, "백업", colors, onClick = onBackup)
        }

        // 관리자 기능: 잠금 상태 → 비밀번호 입력(처음이면 설정) → 복원·초기화 표시
        SettingCard(
            AppIcons.Lock, "관리자 기능 · 복원·초기화",
            when {
                !adminSet -> "관리자 비밀번호를 먼저 정하세요. 복원·초기화는 비밀번호가 있어야 열립니다 (운행 기록 임의 삭제 방지)"
                unlocked -> "관리자 모드 · 설정 화면을 나가면 다시 잠깁니다"
                else -> "운행 기록을 지우거나 되돌리는 기능이라 관리자만 열 수 있습니다"
            },
            colors
        ) {
            when {
                unlocked -> PillButton(AppIcons.Lock, "잠그기", colors) {
                    unlocked = false
                    confirmRestore = -1
                    confirmReset = false
                }
                else -> PillButton(AppIcons.Lock, if (adminSet) "잠금 해제" else "비밀번호 설정", colors) {
                    entering = !entering
                    pin = ""
                    pin2 = ""
                    pinError = ""
                }
            }
        }
        if (entering && !unlocked) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PinBox(if (adminSet) "관리자 비밀번호" else "새 비밀번호 (숫자 4~8자리)", pin, colors, Modifier.weight(1f)) { pin = it.filter(Char::isDigit).take(8) }
                if (!adminSet) PinBox("한 번 더 입력", pin2, colors, Modifier.weight(1f)) { pin2 = it.filter(Char::isDigit).take(8) }
                PillButton(AppIcons.Check, "확인", colors) {
                    if (adminSet) {
                        val err = onCheckPin(pin)
                        if (err == null) {
                            unlocked = true
                            entering = false
                        } else pinError = err
                    } else when {
                        !AdminLock.validPin(pin) -> pinError = "숫자 4~8자리로 정해 주세요"
                        pin != pin2 -> pinError = "두 번 입력한 비밀번호가 다릅니다"
                        else -> {
                            onSetPin(pin)
                            unlocked = true
                            entering = false
                        }
                    }
                    pin = ""
                    pin2 = ""
                }
            }
            if (pinError.isNotEmpty()) Text(pinError, color = colors.warnText, fontSize = 20.sp)
        }

        if (unlocked) {
            Text("되돌릴 백업 (지금 데이터는 복원 전에 자동으로 백업됩니다)", color = colors.sub, fontSize = 20.sp)
            if (backups.isEmpty()) Text("백업이 없습니다", color = colors.sub, fontSize = 22.sp)
            backups.take(5).forEachIndexed { i, label ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.surface)
                        .border(1.dp, colors.line, RoundedCornerShape(20.dp)).padding(start = 28.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(AppIcons.Clock, null, tint = colors.sub, modifier = Modifier.size(30.dp))
                    Text(label, color = colors.text, fontSize = 24.sp, modifier = Modifier.weight(1f).padding(start = 16.dp))
                    if (confirmRestore == i) PillButton(AppIcons.Restore, "한 번 더 누르면 복원", colors, tint = colors.warnText) { onRestore(i) }
                    else PillButton(AppIcons.Restore, "복원", colors) { confirmRestore = i }
                }
            }
            SettingCard(
                AppIcons.Trash, "초기화",
                "운행 기록·수정 이력·사업자 정보·직원 명단을 모두 지우고 처음 설정으로 돌아갑니다. 지우기 전에 자동으로 백업합니다.",
                colors
            ) {
                if (confirmReset) PillButton(AppIcons.Trash, "한 번 더 누르면 초기화", colors, tint = colors.warnText, onClick = onReset)
                else PillButton(AppIcons.Trash, "초기화", colors) { confirmReset = true }
            }
        }
    }
}

// 설정 한 칸: 왼쪽 아이콘 + 제목·현재 값 + 오른쪽 버튼들
@Composable
private fun SettingCard(icon: ImageVector, title: String, value: String, colors: AppColors, actions: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.surface)
            .border(1.dp, colors.line, RoundedCornerShape(20.dp)).padding(start = 28.dp, end = 16.dp, top = 18.dp, bottom = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Icon(icon, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(40.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, color = colors.text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(value, color = colors.sub, fontSize = 21.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), content = actions)
    }
}

// 비밀번호 칸: 숫자 자판, 입력한 숫자는 ●로 가림
@Composable
private fun PinBox(hint: String, value: String, colors: AppColors, modifier: Modifier, onChange: (String) -> Unit) {
    Box(modifier) {
        HangulTextField(
            value = value, onValueChange = onChange,
            textStyle = TextStyle(color = colors.text, fontSize = 28.sp), cursorBrush = SolidColor(colors.accent), colors = colors,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), hangul = false,
            visualTransformation = PasswordVisualTransformation(),
        ) { inner ->
            Box(
                Modifier.fillMaxWidth().height(76.dp).clip(RoundedCornerShape(18.dp)).background(colors.surface)
                    .border(1.dp, colors.line, RoundedCornerShape(18.dp)).padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) Text(hint, color = colors.personal, fontSize = 24.sp)
                inner()
            }
        }
    }
}

@Preview(name = "설정", widthDp = 1200, heightDp = 840)
@Composable
fun SettingsPreview() {
    SettingsScreen(
        DarkColors, "Tech1 · 123-45-67891 · 아이오닉5 12가3456 · 부서 없음 강지민", "3명 · 강지민 (총무팀), 김하늘 (영업팀), 김민 (마케팅팀)",
        listOf("10월 31일 (토) 오후 3:30", "10월 30일 (금) 오전 9:12"), "",
        adminSet = true, onSetPin = {}, onCheckPin = { null },
        onEditProfile = {}, onOpenRoster = {}, onBackup = {}, onRestore = {}, onReset = {}, onBack = {}
    )
}

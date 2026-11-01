# 오도로그 (OdoLog)

Pleos Connect 차량 화면에서 도는 업무용 차량 운행기록 앱입니다.
차량의 누적 주행거리와 기어 신호(P → D 출발, 다시 P 도착)로 운행을 자동으로 기록하고,
국세청 「업무용승용차 운행기록부」 서식 항목 순서대로 엑셀 장부를 만듭니다.
운전자는 정차 후 운행 목적만 고르면 됩니다.

> 현대자동차그룹 차량용 앱 공모전 출품작. 모든 동작은 Pleos Connect 에뮬레이터 기준이며 실차 검증 전입니다.

## 주요 기능

| 기능 | 설명 |
|---|---|
| 운행 자동 기록 | 기어가 P에서 R·N·D로 바뀌면 시작, 다시 P가 되면 종료. 출발·도착 순간의 누적 주행거리를 차량에서 직접 읽음 |
| 정차 후 목적 선택 | 출퇴근 / 일반 업무 / 개인. 주행 중에는 입력 화면 없음 |
| 공용차 직원 명단 | 운행마다 운전자(부서·성명) 기록 |
| 세무 서식 장부 | 월별·사업연도별 엑셀(운행기록부 + 수정 이력 2개 시트)과 CSV |
| 기록 신뢰성 | 거리·시각 수정 제한, 평균 시속 150 km/h 초과 ‘확인 필요’, 수정 이력 자동 기록, 운행 중 앱 종료 복구 |
| 백업·복원·초기화 | 복원·초기화는 관리자 비밀번호로 잠금, 직전 자동 백업과 실패 시 되돌림 |
| 운전자 확인 보조 (선택) | 얼굴을 등록·동의한 직원만. 시동 후 정차 화면에서 얼굴 대조 + 눈 깜빡임 확인, 도착 후 고른 이름과 대조 |
| 회사용 웹 대시보드 (확장 시제품) | 여러 차량 기록 모아 보기, 직원 명단 배포, 카드 내역(CSV)과 운행 대조 보조. PC 안에서만 도는 시연용 |

얼굴 정보는 사진을 남기지 않고 특징값만 차량 안에 보관하며, 백업 파일·클라우드 백업·회사 서버로 보내지 않습니다.

## 폴더

```
app/        차량 앱 (Kotlin, Jetpack Compose)
server/     회사용 서버·웹 대시보드 (Kotlin, Ktor) — 앱과 따로 빌드
demo_kit/   시연 도구: 가상 주행 스크립트, 서버 실행·EXE 만들기, 카드 내역 예시
```

## 실행 환경

- Android Studio (Kotlin 2.2, AGP 9)
- Pleos Connect Emulator v2.1.0 (API 34, x86_64) — 앱은 운전자 사용자(user 10)에 설치
- Pleos Vehicle SDK 2.3.2 (`settings.gradle.kts` 의 Pleos 저장소에서 받음)

## 빌드와 설치

```powershell
.\gradlew :app:assembleDebug
adb install -r --user 10 app\build\outputs\apk\debug\app-debug.apk
```

빌드한 APK는 이 저장소의 Releases 에도 올려 두었습니다.

얼굴 확인을 시험하려면 에뮬레이터 설정에서 전면 카메라를 PC 웹캠(`webcam0`)으로 지정합니다.

## 가상 주행 (시연·반복 시험)

Virtual Console 의 주행거리를 손으로 올리는 대신, 기어와 누적 거리를 시간에 맞춰 보냅니다.

```powershell
adb forward tcp:8088 tcp:8088
powershell -ExecutionPolicy Bypass -File .\demo_kit\drive.ps1 -Km 5 -Kph 40 -Trips 3
```

운행 사이 대기 시간(기본 40초) 안에 차량 화면에서 이름·목적을 고릅니다.

## 회사용 서버 (확장 시제품)

```powershell
.\gradlew -p server run          # 또는 demo_kit\build_server_exe.ps1 로 EXE 폴더 만들기
```

브라우저에서 `http://127.0.0.1:8090` 을 엽니다. 에뮬레이터 앱은 `10.0.2.2:8090` 으로 접속합니다.
인증이 없으므로 이 PC 안(127.0.0.1)에서만 받습니다.

## 시험

```powershell
.\gradlew :app:testDebugUnitTest   # 운행 판정 규칙, 눈 깜빡임, 한글 입력 (13개)
.\gradlew -p server test           # 서버 입력 검사 (3개)
```

## 서드파티 고지

- 얼굴 특징값 모델 `app/src/main/assets/mobile_facenet.tflite`: Qualcomm AI Hub 배포 MobileFaceNet (Apache License 2.0).
  학습 데이터 관련 이용 조건이 있어 출시 전 상업적 이용이 허용된 모델로 교체할 예정입니다.
- 얼굴 검출: Google ML Kit Face Detection (기기 안 처리)
- 차량 신호: Pleos Vehicle SDK, Android Car API

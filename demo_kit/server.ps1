# 회사 서버(플릿 관리) 켜기: Android Studio 에 들어 있는 Java 로 실행 → 브라우저에서 http://127.0.0.1:8090
# 사용: .\demo_kit\server.ps1   (끄기: Ctrl+C)
# 이미 설정된 JAVA_HOME 이 쓸 만하면 그대로, 아니면 Android Studio 기본 설치 경로에서 찾음
$jbr = @($env:JAVA_HOME, "$env:ProgramFiles\Android\Android Studio1\jbr", "$env:ProgramFiles\Android\Android Studio\jbr") |
    Where-Object { $_ } |
    Where-Object { Test-Path "$_\bin\java.exe" } | Select-Object -First 1
if (-not $jbr) { throw "Android Studio 의 Java(jbr)를 찾지 못했습니다. JAVA_HOME 을 직접 지정해 주세요." }
$env:JAVA_HOME = $jbr
& "$PSScriptRoot\..\gradlew.bat" -p "$PSScriptRoot\..\server" run --console=plain -q

# 회사 서버(플릿 관리)를 더블클릭으로 켜지는 폴더로 만들기 → dist\OdoLogServer\OdoLogServer.exe + 바탕화면 바로가기
# 폴더째 다른 PC 로 복사하면 Java 설치 없이 켜짐 (runtime 폴더에 Java 를 함께 넣음, 약 110 MB)
# 사용: powershell -ExecutionPolicy Bypass -File .\demo_kit\build_server_exe.ps1   (서버 코드를 고친 뒤 다시 실행)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$out = Join-Path $root 'dist\OdoLogServer'

# 1) 서버 빌드 (Android Studio 의 Java 사용)
$jbr = @($env:JAVA_HOME, "$env:ProgramFiles\Android\Android Studio1\jbr", "$env:ProgramFiles\Android\Android Studio\jbr") |
    Where-Object { $_ } | Where-Object { Test-Path "$_\bin\java.exe" } | Select-Object -First 1
if (-not $jbr) { throw "Java(jbr)를 찾지 못했습니다. JAVA_HOME 을 지정해 주세요." }
$env:JAVA_HOME = $jbr
& "$root\gradlew.bat" -p "$root\server" installDist --console=plain -q
if ($LASTEXITCODE -ne 0) { throw "서버 빌드 실패" }

# 2) 실행 폴더 구성: runtime(Java) + lib(서버). data 폴더(운행기록·명단)는 지우지 않음
if (Test-Path "$out\OdoLogServer.exe") {
    Get-Process OdoLogServer -ErrorAction SilentlyContinue | Where-Object { $_.Path -eq "$out\OdoLogServer.exe" } |
        ForEach-Object { throw "오도로그 서버가 켜져 있습니다. 창을 닫은 뒤 다시 실행하세요." }
}
New-Item -ItemType Directory -Force $out | Out-Null
foreach ($d in 'runtime', 'lib') { if (Test-Path "$out\$d") { Remove-Item -Recurse -Force "$out\$d" } }
Copy-Item -Recurse $jbr "$out\runtime"
Copy-Item -Recurse "$root\server\build\install\odolog-server\lib" "$out\lib"
# 처음 만들 때만: 개발 중 쌓인 서버 데이터를 옮겨 옴
if (-not (Test-Path "$out\data") -and (Test-Path "$root\server\data")) { Copy-Item -Recurse "$root\server\data" "$out\data" }

# 3) 실행 파일: Windows 에 기본으로 있는 C# 컴파일러로 만듦 (한글 문구가 깨지지 않게 UTF-8 로 읽음)
$csc = "$env:WINDIR\Microsoft.NET\Framework64\v4.0.30319\csc.exe"
& $csc /nologo /codepage:65001 /target:exe "/out:$out\OdoLogServer.exe" "$PSScriptRoot\launcher\OdoLogServer.cs"
if ($LASTEXITCODE -ne 0) { throw "실행 파일 만들기 실패" }

# 4) 바탕화면 바로가기
$lnk = Join-Path ([Environment]::GetFolderPath('Desktop')) '오도로그 서버.lnk'
$s = (New-Object -ComObject WScript.Shell).CreateShortcut($lnk)
$s.TargetPath = "$out\OdoLogServer.exe"
$s.WorkingDirectory = $out
$s.Description = '오도로그 플릿 관리 서버 (켜지면 브라우저로 대시보드가 열림)'
$s.Save()

Write-Host "완료: $out\OdoLogServer.exe"
Write-Host "바탕화면 바로가기: $lnk"

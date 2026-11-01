# 가상 주행: Pleos Connect Virtual Console(에뮬레이터 안 8088 포트)에 기어·누적 주행거리를 보내 운행 한 건을 만듦
# Virtual Console 화면에서 주행거리를 1씩 누르는 대신, 실제 주행처럼 시간에 맞춰 거리를 조금씩 올림
#
# 사용 예:
#   .\demo_kit\drive.ps1 -Km 12.5 -Kph 40            # 12.5 km 를 시속 40 km 로 (약 19분)
#   .\demo_kit\drive.ps1 -Km 30 -Kph 100             # 30 km 를 시속 100 km 로 (18분)
#   .\demo_kit\drive.ps1 -Km 5 -Kph 40 -Trips 3      # 5 km 운행을 3번 (사이마다 목적 고를 시간 40초)
#
# 주의: 앱은 평균 시속 150 km 를 넘으면 '확인 필요'로 표시함 → -Kph 는 150 미만으로
#       운행이 끝나면 차량 화면에서 이름·목적을 직접 골라야 기록이 완성됨 (그사이 -RestSec 만큼 기다림)
param(
    [Parameter(Mandatory = $true)][double]$Km,
    [double]$Kph = 40,
    [int]$Trips = 1,
    [int]$RestSec = 40,
    [string]$Adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
)
$ErrorActionPreference = 'Stop'
$base = 'http://127.0.0.1:8088'
$client = 'drive-script'

if ($Kph -le 0 -or $Kph -ge 150) { throw "-Kph 는 0보다 크고 150보다 작아야 합니다 (앱이 150 km/h 초과를 '확인 필요'로 표시)" }
if ($Km -lt 0.1) { throw "-Km 은 0.1 이상이어야 합니다 (앱은 0.1 km 미만 운행을 저장하지 않음)" }

& $Adb forward tcp:8088 tcp:8088 | Out-Null

# 콘솔은 '브라우저가 연결돼 있을 때'만 값을 받으므로 연결 신호를 계속 보냄
function Beat { Invoke-RestMethod -Method Post -Uri "$base/browser/session?client=$client" -TimeoutSec 3 | Out-Null }
function Put([string]$path) {
    Beat
    $r = Invoke-RestMethod -Method Put -Uri "$base$path" -TimeoutSec 5
    if ($r.error) { throw "콘솔 오류: $($r.error) ($path)" }
}
function Odometer { (Invoke-RestMethod -Uri "$base/vehicle/state" -TimeoutSec 5).odometer.traveled_distance_km }
function Show([double]$v) { $v.ToString('0.0', [Globalization.CultureInfo]::InvariantCulture) }
function Q([double]$v) { $v.ToString('0.000', [Globalization.CultureInfo]::InvariantCulture) }

1..3 | ForEach-Object { Beat; Start-Sleep -Milliseconds 500 }

for ($t = 1; $t -le $Trips; $t++) {
    $start = [double](Odometer)
    $seconds = [int][math]::Ceiling($Km / $Kph * 3600)
    Write-Host ("[{0}/{1}] 출발: 누적 {2} km → {3} km 를 시속 {4} km 로 약 {5}분" -f $t, $Trips, (Show $start), (Show ($start + $Km)), $Kph, [math]::Round($seconds / 60, 1))

    # D 를 보낸 순간부터는 통신 오류·Ctrl+C 로 멈춰도 반드시 P 로 돌려 놓음 (D 로 남으면 앱이 운행 중으로 남고 다음 주행과 합쳐짐)
    # D 요청이 응답 전에 실패해도 콘솔에는 D 가 들어갔을 수 있으므로 try 안에서 보냄
    try {
        Put "/vehicle/gear?gear=Drive"
        Start-Sleep -Seconds 3   # 앱이 출발을 기록할 시간
        $step = 2
        for ($s = $step; $s -le $seconds; $s += $step) {
            $v = $start + $Km * $s / $seconds
            Put "/vehicle/odometer?traveled_distance_km=$(Q $v)"
            Write-Progress -Activity "운행 $t/$Trips" -Status "$(Show ($v - $start)) / $(Show $Km) km" -PercentComplete ([math]::Min(100, 100 * $s / $seconds))
            Start-Sleep -Seconds $step
        }
        Put "/vehicle/odometer?traveled_distance_km=$(Q ($start + $Km))"
        Start-Sleep -Seconds 3   # 마지막 거리를 앱이 받을 시간
    } finally {
        Write-Progress -Activity "운행 $t/$Trips" -Completed
        $parked = $false
        for ($try = 1; $try -le 3 -and -not $parked; $try++) {
            try { Put "/vehicle/gear?gear=Park"; $parked = $true } catch { Start-Sleep -Seconds 2 }
        }
        if (-not $parked) {
            Write-Warning "기어를 P 로 돌려 놓지 못했습니다. Virtual Console(http://127.0.0.1:8088)에서 직접 P 를 눌러 주세요."
        }
    }
    Write-Host ("[{0}/{1}] 도착: 누적 {2} km. 차량 화면에서 이름·목적을 골라 주세요" -f $t, $Trips, (Show (Odometer)))

    if ($t -lt $Trips) {
        for ($w = $RestSec; $w -gt 0; $w -= 2) { Beat; Start-Sleep -Seconds 2 }
    }
}
Write-Host "끝났습니다."

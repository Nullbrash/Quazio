<#
.SYNOPSIS
    Память и время запуска ПК-версии (сборка `createDistributable`).

.DESCRIPTION
    Запускает Quazio.exe; параметры Java на время замера дописываются в
    app\Quazio.cfg (раздел [JavaOptions]) — так их и поставлять. После
    замера cfg восстанавливается.
    Окно ищется по заголовку «Quazio» в любом процессе: лаунчер jpackage
    поднимает программу не в том процессе, который запустили.
    Время запуска — до появления окна. Через `SettleSec` — медиана замеров
    рабочего набора (Working Set) и частной памяти (Private Bytes).

    С -Exe/-ExeArgs запускается другой exe (например, сборка GraalVM).

.EXAMPLE
    .\tools\measure-desktop.ps1 -Label "SerialGC" -JavaOptions '-XX:+UseSerialGC'
#>
param(
    [string] $Label = 'по умолчанию',
    [string[]] $JavaOptions = @(),
    [string] $Exe = '',
    [string[]] $ExeArgs = @(),
    [string] $AppDir = 'app\desktop\build\compose\binaries\main\app\Quazio',
    [int] $SettleSec = 15,
    [int] $Samples = 3,
    [int] $IntervalSec = 3,
    [string] $CsvPath = ''
)

$ErrorActionPreference = 'Stop'
$app = (Resolve-Path $AppDir).Path
if (-not $Exe) { $Exe = Join-Path $app 'Quazio.exe' }
$cfg = Join-Path $app 'app\Quazio.cfg'
$original = Get-Content $cfg -Raw -Encoding UTF8

function Find-Window { Get-Process | Where-Object { $_.MainWindowTitle -eq 'Quazio' } | Select-Object -First 1 }
if (Find-Window) { throw 'окно Quazio уже открыто — закройте его' }

try {
    if ($JavaOptions.Count -gt 0) {
        $extra = ($JavaOptions | ForEach-Object { "java-options=$_" }) -join "`r`n"
        Set-Content $cfg -Value ($original.TrimEnd() + "`r`n" + $extra + "`r`n") -Encoding UTF8 -NoNewline
    }

    $sw = [Diagnostics.Stopwatch]::StartNew()
    $launcher = if ($ExeArgs.Count -gt 0) {
        Start-Process -FilePath $Exe -ArgumentList $ExeArgs -PassThru
    } else {
        Start-Process -FilePath $Exe -PassThru
    }
    $win = $null
    while (-not $win -and $sw.Elapsed.TotalSeconds -lt 60) {
        $win = Find-Window
        if (-not $win) { Start-Sleep -Milliseconds 100 }
    }
    $startMs = $sw.ElapsedMilliseconds
    if (-not $win) { $launcher.Kill(); throw 'окно не появилось за 60 с' }

    Start-Sleep -Seconds $SettleSec
    $ws = @(); $pb = @()
    for ($i = 0; $i -lt $Samples; $i++) {
        if ($i -gt 0) { Start-Sleep -Seconds $IntervalSec }
        $win.Refresh()
        $ws += $win.WorkingSet64; $pb += $win.PrivateMemorySize64
    }
    $win.CloseMainWindow() | Out-Null
    if (-not $win.WaitForExit(15000)) { $win.Kill() }
    Start-Sleep -Seconds 2
} finally {
    Set-Content $cfg -Value $original -Encoding UTF8 -NoNewline
}

function Get-Median($v) { $s = $v | Sort-Object; $s[[int][math]::Floor(($s.Count - 1) / 2)] }
$result = [pscustomobject]@{
    Label     = $Label
    StartMs   = $startMs
    WorkingMB = [math]::Round((Get-Median $ws) / 1MB, 1)
    PrivateMB = [math]::Round((Get-Median $pb) / 1MB, 1)
}
if ($CsvPath) { $result | Export-Csv -Path $CsvPath -Append -NoTypeInformation -Encoding UTF8 }
$result

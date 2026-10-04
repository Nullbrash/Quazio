<#
.SYNOPSIS
    Память Android-приложения: сумма по всем его процессам основного профиля,
    медиана нескольких замеров.

.DESCRIPTION
    Берёт процессы пакета (`pkg` и `pkg:*`) только пользователя 0: у процессов
    второго профиля (например, Защищённой папки Samsung) то же имя.
    Для каждого — `dumpsys meminfo <pid>`: TOTAL PSS (включает SWAP PSS — то,
    что выгружено в zram), TOTAL RSS, TOTAL SWAP PSS. Значения — в МБ.

.EXAMPLE
    .\tools\measure-android.ps1 -Package io.github.nullbrash.quazio -Label "пустой, на экране"
#>
param(
    [Parameter(Mandatory)] [string] $Package,
    [string] $Label = '',
    [int] $Samples = 3,
    [int] $IntervalSec = 5,
    [string] $CsvPath = ''
)

$ErrorActionPreference = 'Stop'
$adb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'

function Get-PackagePids([string] $pkg) {
    & $adb shell ps -A -o PID,USER,NAME | Select-Object -Skip 1 | ForEach-Object {
        $f = ($_ -split '\s+') | Where-Object { $_ }
        if ($f.Count -ge 3 -and $f[1] -like 'u0_*' -and ($f[2] -eq $pkg -or $f[2] -like "${pkg}:*")) {
            [pscustomobject]@{ Pid = [int]$f[0]; Name = $f[2] }
        }
    }
}

function Measure-Once([string] $pkg) {
    $procs = @(Get-PackagePids $pkg)
    $pss = 0; $rss = 0; $swap = 0
    $byName = @{}
    foreach ($p in $procs) {
        $text = (& $adb shell dumpsys meminfo $p.Pid) -join "`n"
        if ($text -match 'TOTAL PSS:\s+(\d+)\s+TOTAL RSS:\s+(\d+)\s+TOTAL SWAP PSS:\s+(\d+)') {
            $pss += [int]$Matches[1]; $rss += [int]$Matches[2]; $swap += [int]$Matches[3]
            $byName[$p.Name] = [int]$Matches[1]
        }
    }
    [pscustomobject]@{ Procs = $procs.Count; PssKb = $pss; RssKb = $rss; SwapKb = $swap; ByName = $byName }
}

function Get-Median([double[]] $values) {
    $s = $values | Sort-Object
    $s[[int][math]::Floor(($s.Count - 1) / 2)]
}

$runs = for ($i = 0; $i -lt $Samples; $i++) {
    if ($i -gt 0) { Start-Sleep -Seconds $IntervalSec }
    Measure-Once $Package
}

$result = [pscustomobject]@{
    Package = $Package
    Label   = $Label
    Procs   = ($runs | Measure-Object Procs -Maximum).Maximum
    PssMB   = [math]::Round((Get-Median ($runs.PssKb)) / 1024, 1)
    RssMB   = [math]::Round((Get-Median ($runs.RssKb)) / 1024, 1)
    SwapMB  = [math]::Round((Get-Median ($runs.SwapKb)) / 1024, 1)
    # PSS по процессам (медиана), например "main=61.2; :ime=40.3".
    Detail  = (($runs | ForEach-Object { $_.ByName.Keys } | Sort-Object -Unique | ForEach-Object {
        $name = $_
        $vals = @($runs | ForEach-Object { if ($_.ByName.ContainsKey($name)) { $_.ByName[$name] } })
        $short = if ($name -eq $Package) { 'main' } else { $name.Substring($Package.Length) }
        '{0}={1}' -f $short, [math]::Round((Get-Median $vals) / 1024, 1)
    }) -join '; ')
}

if ($CsvPath) { $result | Export-Csv -Path $CsvPath -Append -NoTypeInformation -Encoding UTF8 }
$result

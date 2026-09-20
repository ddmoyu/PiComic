param([string]$Serial = 'emulator-5556', [string]$SdkRoot = "$env:LOCALAPPDATA\Android\Sdk",
    [string]$StorageRootLabel = 'Android SDK built for x86_64')
$ErrorActionPreference = 'Stop'
if ($Serial -notmatch '^emulator-[0-9]+$') { throw '目录读写与撤权测试仅允许测试模拟器' }
$adb = Join-Path $SdkRoot 'platform-tools/adb.exe'
$root = Split-Path $PSScriptRoot -Parent
$artifacts = Join-Path $root 'artifacts'
function Adb { $value = & $adb -s $Serial @args; if ($LASTEXITCODE -ne 0) { throw 'adb 失败' }; $value }
function Find-Node([string]$Value, [int]$Attempts = 15, [switch]$Optional) {
    for ($i=0; $i -lt $Attempts; $i++) {
        $dump = Adb shell uiautomator dump /sdcard/picomic-saf-ui.xml
        if (($dump -join ' ') -notmatch 'dumped to') { Start-Sleep -Milliseconds 200; continue }
        Adb pull /sdcard/picomic-saf-ui.xml (Join-Path $artifacts 'saf-ui.xml') | Out-Null
        [xml]$xml = Get-Content (Join-Path $artifacts 'saf-ui.xml') -Raw
        $node = $xml.SelectNodes('//node') | Where-Object { $_.GetAttribute('text') -eq $Value -or $_.GetAttribute('content-desc') -eq $Value } | Select-Object -First 1
        if ($node) { return $node }
        Start-Sleep -Milliseconds 200
    }
    if (!$Optional) { throw "找不到系统目录选择器节点：$Value" }
}
function Tap($Node) {
    $bounds = [regex]::Matches($Node.GetAttribute('bounds'), '\d+') | ForEach-Object { [int]$_.Value }
    Adb shell input tap ([int](($bounds[0]+$bounds[2])/2)) ([int](($bounds[1]+$bounds[3])/2)) | Out-Null
}
Adb shell mkdir -p /sdcard/Documents/PiComic-SAF-Fixture | Out-Null
# Start the instrument command separately, then invoke this script while it waits for the picker.
Tap (Find-Node 'Show roots')
$storage = Find-Node $StorageRootLabel -Attempts 4 -Optional
if (!$storage) {
    Adb shell input keyevent 4 | Out-Null
    Tap (Find-Node 'More options')
    $show = Find-Node 'Show internal storage' -Attempts 1 -Optional
    if ($show) { Tap $show } else { Adb shell input keyevent 4 | Out-Null }
    Tap (Find-Node 'Show roots')
    $storage = Find-Node $StorageRootLabel
}
Tap $storage
Tap (Find-Node 'Documents')
Tap (Find-Node 'PiComic-SAF-Fixture')
Tap (Find-Node 'SELECT')
'目录已通过系统选择器授予权限；测试将核对写入、清理和撤权。'

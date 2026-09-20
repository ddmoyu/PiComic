param([string]$Serial = 'emulator-5554')
$ErrorActionPreference = 'Stop'
if (-not $Serial.StartsWith('emulator-')) { throw '此验证会重置演示阅读记录，仅允许在测试模拟器运行。' }
$picaRoot = Split-Path $PSScriptRoot -Parent
$picaAdb = Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe'
$picaArtifacts = Join-Path $picaRoot 'artifacts'
function Invoke-PicaAdb {
    $picaOutput = & $picaAdb -s $Serial @args
    if ($LASTEXITCODE -ne 0) { throw "adb 执行失败：$args" }
    $picaOutput
}
function Find-PicaNode([string]$Attribute, [string]$Value) {
    for ($picaAttempt = 0; $picaAttempt -lt 12; $picaAttempt++) {
        Invoke-PicaAdb shell uiautomator dump /sdcard/picomic-process-check.xml | Out-Null
        Invoke-PicaAdb pull /sdcard/picomic-process-check.xml (Join-Path $picaArtifacts 'process-reader.xml') | Out-Null
        [xml]$picaTree = Get-Content (Join-Path $picaArtifacts 'process-reader.xml') -Raw
        $picaNode = $picaTree.SelectNodes('//node') | Where-Object { $_.GetAttribute($Attribute).Contains($Value) } | Select-Object -First 1
        if ($picaNode) { return $picaNode }
        Start-Sleep -Milliseconds 200
    }
    throw "未找到预期界面：$Value"
}
function Click-PicaNode($Node) {
    $picaNumbers = [regex]::Matches($Node.GetAttribute('bounds'), '\d+') | ForEach-Object { [int]$_.Value }
    if ($picaNumbers.Count -ne 4) { throw '无效界面边界' }
    Invoke-PicaAdb shell input tap ([int](($picaNumbers[0] + $picaNumbers[2]) / 2)) ([int](($picaNumbers[1] + $picaNumbers[3]) / 2)) | Out-Null
}

$picaFixture = Invoke-PicaAdb shell am instrument -w -e class 'io.github.ddmoyu.picomic.ReaderControlsTest#continuousProgressRestoresPageAndOffset' io.github.ddmoyu.picomic.test/androidx.test.runner.AndroidJUnitRunner
$picaFixture | Set-Content -Encoding utf8 (Join-Path $picaArtifacts 'process-reader-fixture.log')
if (($picaFixture -join "`n") -notmatch 'OK \(1 test\)') { throw '阅读进度准备验证失败' }
Invoke-PicaAdb pull /sdcard/Android/data/io.github.ddmoyu.picomic/files/screenshots/19-reader-resumed.png (Join-Path $picaRoot 'docs/evidence/android/screenshots/19-reader-resumed.png') | Out-Null
Invoke-PicaAdb shell am force-stop io.github.ddmoyu.picomic | Out-Null
Invoke-PicaAdb shell am start -W -n io.github.ddmoyu.picomic/.MainActivity | Out-Null
Click-PicaNode (Find-PicaNode 'text' '雨后的第七站')
$null = Find-PicaNode 'text' '上次读到第 1 话 · 第 6 页'
Click-PicaNode (Find-PicaNode 'text' '继续阅读')
$null = Find-PicaNode 'content-desc' '第 1 话，第 6 页'
Invoke-PicaAdb shell screencap -p /sdcard/picomic-process-restored.png | Out-Null
Invoke-PicaAdb pull /sdcard/picomic-process-restored.png (Join-Path $picaRoot 'docs/evidence/android/screenshots/20-reader-process-restored.png') | Out-Null
'PASS：强制结束进程后，详情保留第 6 页，继续阅读恢复对应画面。' | Tee-Object -FilePath (Join-Path $picaArtifacts 'process-reader-result.log')

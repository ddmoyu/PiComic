param(
    [Parameter(Mandatory)][string[]]$Apk,
    [Parameter(Mandatory)][ValidatePattern('^[0-9A-Fa-f]{64}$')][string]$ExpectedSignerSha256,
    [string]$OutputDirectory = 'artifacts/release',
    [string]$SdkRoot = "$env:LOCALAPPDATA\Android\Sdk",
    [switch]$AllowDevelopmentBuild
)
$ErrorActionPreference = 'Stop'
$buildTools = Get-ChildItem -LiteralPath (Join-Path $SdkRoot 'build-tools') -Directory |
    Where-Object { $_.Name -match '^\d+\.\d+\.\d+$' } | Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1
if (!$buildTools) { throw '没有找到 Android build-tools' }
$aaptName = if ($env:OS -eq 'Windows_NT') { 'aapt.exe' } else { 'aapt' }
$signerName = if ($env:OS -eq 'Windows_NT') { 'apksigner.bat' } else { 'apksigner' }
Add-Type -AssemblyName System.IO.Compression.FileSystem
function Read-VerifiedApk([string]$Path) {
    $apkPath = (Resolve-Path -LiteralPath $Path).Path
    $badging = & (Join-Path $buildTools.FullName $aaptName) dump badging $apkPath
    if ($LASTEXITCODE -ne 0) { throw 'APK 元数据解析失败' }
    $package = [regex]::Match(($badging -join "`n"), "(?m)^package: name='([^']+)' versionCode='([0-9]+)' versionName='([^']+)'")
    if (!$package.Success) { throw 'APK 包信息不完整' }
    $packageName = $package.Groups[1].Value
    $versionCode = [long]$package.Groups[2].Value
    $versionName = $package.Groups[3].Value
    if ($packageName -ne 'io.github.ddmoyu.picomic' -or $versionCode -le 0) { throw 'APK 不是 PiComic 或版本号无效' }
    if ($versionName -notmatch '^[0-9][A-Za-z0-9.+-]{0,79}$') { throw '版本名不适合作为发布资产名' }
    $isDevelopment = (($badging -join "`n") -match '(?m)^application-debuggable') -or ($versionName -match '(?i)alpha|beta|preview|debug|dev|rc')
    if ($isDevelopment -and !$AllowDevelopmentBuild) { throw '开发版本不能生成正式稳定发布资产；本地验证需显式指定 AllowDevelopmentBuild' }
    $sdk = [regex]::Match(($badging -join "`n"), "(?m)^sdkVersion:'([0-9]+)'")
    if (!$sdk.Success) { throw 'APK 未提供 minSdk' }
    $minSdk = [int]$sdk.Groups[1].Value
    $signature = & (Join-Path $buildTools.FullName $signerName) verify --verbose --print-certs --min-sdk-version $minSdk $apkPath
    if ($LASTEXITCODE -ne 0) { throw 'APK 签名验证失败' }
    $signatureText = $signature -join "`n"
    $signers = @([regex]::Matches($signatureText, '(?m)^(?:Signer #[0-9]+|V[0-9.]+ Signer:) certificate SHA-256 digest: ([0-9a-fA-F]{64})') | ForEach-Object { $_.Groups[1].Value.ToLowerInvariant() } | Sort-Object -Unique)
    if ($signatureText -notmatch '(?m)^Number of signers: 1\s*$' -or $signers.Count -ne 1 -or $signers[0] -ine $ExpectedSignerSha256) { throw '签名证书不符合预期' }
    if ($signatureText -notmatch 'Verified using v2 scheme.*: true') { throw '缺少 Android 8 可验证的 v2 签名' }
    $zip = [IO.Compression.ZipFile]::OpenRead($apkPath)
    try { $abis = @($zip.Entries | ForEach-Object { if ($_.FullName -match '^lib/([^/]+)/[^/]+\.so$') { $Matches[1] } } | Sort-Object -Unique) } finally { $zip.Dispose() }
    if ($abis.Count -eq 0 -or @($abis | Where-Object { $_ -notin @('arm64-v8a','armeabi-v7a','x86','x86_64') }).Count -gt 0) { throw 'APK 的 ABI 集合无效' }
    [pscustomobject]@{
        Path=$apkPath; PackageName=$packageName; VersionCode=$versionCode; VersionName=$versionName
        MinSdk=$minSdk; Abis=$abis; Development=$isDevelopment; Signature=$signature
    }
}
$packages = @($Apk | ForEach-Object { Read-VerifiedApk $_ })
if ($packages.Count -eq 0) { throw '至少需要一个 APK' }
$first = $packages[0]
$seenAbis = @()
foreach ($item in $packages) {
    if ($item.PackageName -ne $first.PackageName -or $item.VersionCode -ne $first.VersionCode -or
        $item.VersionName -ne $first.VersionName -or $item.MinSdk -ne $first.MinSdk -or $item.Development -ne $first.Development) {
        throw '各架构 APK 的包名、版本、最低系统和构建类型必须一致'
    }
    if ($packages.Count -gt 1 -and $item.Abis.Count -ne 1) { throw '多 APK 发布必须为每种架构提供独立完整包' }
    foreach ($abi in $item.Abis) {
        if ($abi -in $seenAbis) { throw "重复的 APK 架构：$abi" }
        $seenAbis += $abi
    }
}
$output = [IO.Path]::GetFullPath($OutputDirectory)
[IO.Directory]::CreateDirectory($output) | Out-Null
$artifacts = @()
$signatures = @()
foreach ($item in $packages) {
    $suffix = if ($item.Abis.Count -eq 1) { $item.Abis[0] } else { 'universal' }
    $assetName = "PiComic-$($item.VersionName)-$suffix.apk"
    $assetPath = Join-Path $output $assetName
    $sourceHash = (Get-FileHash -LiteralPath $item.Path -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($assetPath -ine $item.Path) { Copy-Item -LiteralPath $item.Path -Destination $assetPath -Force }
    if ((Get-FileHash -LiteralPath $assetPath -Algorithm SHA256).Hash -ine $sourceHash) { throw '复制后的 APK 摘要不一致' }
    $artifacts += [ordered]@{ assetName=$assetName; abis=@($item.Abis); minSdk=$item.MinSdk; sizeBytes=(Get-Item -LiteralPath $assetPath).Length; sha256=$sourceHash }
    $signatures += [ordered]@{ assetName=$assetName; output=$item.Signature }
}
$manifest = [ordered]@{ schemaVersion=1; tag="v$($first.VersionName)"; versionName=$first.VersionName; versionCode=$first.VersionCode; packageName=$first.PackageName; channel='stable'; artifacts=$artifacts }
[IO.File]::WriteAllText((Join-Path $output 'picomic-update.json'), ($manifest | ConvertTo-Json -Depth 8), [Text.UTF8Encoding]::new($false))
$report = [ordered]@{ checkedAt=[DateTimeOffset]::UtcNow.ToString('o'); development=$first.Development; signerSha256=$ExpectedSignerSha256.ToLowerInvariant(); manifest=$manifest; signatureVerification=$signatures }
[IO.File]::WriteAllText((Join-Path $output 'verification.json'), ($report | ConvertTo-Json -Depth 9), [Text.UTF8Encoding]::new($false))
Write-Output "已校验并生成本地资产：$output"
Write-Output "版本 $($first.VersionName) ($($first.VersionCode))；ABI $($seenAbis -join ', ')；共 $($packages.Count) 个 APK"
Write-Output '本工具不上传、不创建 Release；正式发布前须完成覆盖安装与数据保留验证。'

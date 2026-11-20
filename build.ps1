param(
    [string]$Sdk = "$env:LOCALAPPDATA/Android/Sdk",
    [string]$Jdk = "C:/Program Files/Android/Android Studio/jbr",
    [string]$Apk = "$PSScriptRoot/../VideoSequence.apk",
    [string]$BuildDirectory = "$PSScriptRoot/../../work/android-build"
)
$ErrorActionPreference = 'Stop'

function Invoke-BuildTool($Executable, [string[]]$Arguments) {
    & $Executable @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Executable failed with exit code $LASTEXITCODE" }
}

$Sdk = (Resolve-Path $Sdk).Path
$Jdk = (Resolve-Path $Jdk).Path
$env:JAVA_HOME = $Jdk
$tools = Join-Path $Sdk 'build-tools/36.0.0'
$platform = Get-ChildItem (Join-Path $Sdk 'platforms') -Directory |
    Where-Object { $_.Name -match '^android-(\d+)(?:\.(\d+))?$' -and [int]$Matches[1] -ge 36 -and
        (Test-Path (Join-Path $_.FullName 'android.jar')) } |
    Sort-Object { [version](($_.Name -replace '^android-', '') -replace '^(\d+)$', '$1.0') } -Descending |
    Select-Object -First 1
if (!$platform) { throw 'Install Android SDK Platform 36 or newer and Build-Tools 36.0.0.' }
$androidJar = Join-Path $platform.FullName 'android.jar'
$build = (New-Item -ItemType Directory -Force $BuildDirectory).FullName
$classes = (New-Item -ItemType Directory -Force (Join-Path $build ([guid]::NewGuid().ToString()))).FullName
$compiled = Join-Path $build 'resources.zip'
$unsigned = Join-Path $build 'unsigned.apk'
$unaligned = Join-Path $build 'unaligned.apk'
$aligned = Join-Path $build 'aligned.apk'
$keystore = Join-Path $build 'debug.keystore'
Invoke-BuildTool "$tools/aapt2.exe" @('compile', '--dir', "$PSScriptRoot/res", '-o', $compiled)
Invoke-BuildTool "$tools/aapt2.exe" @('link', '-I', $androidJar, '--manifest',
    "$PSScriptRoot/AndroidManifest.xml", '-o', $unsigned, $compiled)
$sources = @(Get-ChildItem "$PSScriptRoot/src" -Filter '*.java' | ForEach-Object FullName)
Invoke-BuildTool "$Jdk/bin/javac.exe" (@('--release', '8', '-encoding', 'UTF-8', '-classpath', $androidJar,
    '-d', $classes) + $sources)
$bytecode = @(Get-ChildItem $classes -Recurse -Filter '*.class' | ForEach-Object FullName)
Invoke-BuildTool "$tools/d8.bat" (@('--lib', $androidJar, '--min-api', '23', '--output', $classes) + $bytecode)
Copy-Item -LiteralPath $unsigned -Destination $unaligned -Force
Invoke-BuildTool "$Jdk/bin/jar.exe" @('uf', $unaligned, '-C', $classes, 'classes.dex')
Invoke-BuildTool "$tools/zipalign.exe" @('-f', '4', $unaligned, $aligned)
if (!(Test-Path -LiteralPath $keystore)) {
    Invoke-BuildTool "$Jdk/bin/keytool.exe" @('-genkeypair', '-keystore', $keystore, '-storepass', 'android',
        '-keypass', 'android', '-alias', 'androiddebugkey', '-keyalg', 'RSA', '-keysize', '2048',
        '-validity', '10000', '-dname', 'CN=Android Debug,O=Android,C=US')
}
Invoke-BuildTool "$tools/apksigner.bat" @('sign', '--ks', $keystore, '--ks-pass', 'pass:android',
    '--key-pass', 'pass:android', '--out', [System.IO.Path]::GetFullPath($Apk), $aligned)
Invoke-BuildTool "$tools/apksigner.bat" @('verify', '--verbose', [System.IO.Path]::GetFullPath($Apk))
Invoke-BuildTool "$tools/zipalign.exe" @('-c', '4', [System.IO.Path]::GetFullPath($Apk))

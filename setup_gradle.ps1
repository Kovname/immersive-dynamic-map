$ErrorActionPreference = "Stop"

$wrapperDir = "b:\Projects\MineMapMod\gradle\wrapper"
$jarUrl = "https://raw.githubusercontent.com/gradle/gradle/v8.3.0/gradle/wrapper/gradle-wrapper.jar"
$jarPath = Join-Path $wrapperDir "gradle-wrapper.jar"

Write-Host "Downloading gradle-wrapper.jar..."
Invoke-WebRequest -Uri $jarUrl -OutFile $jarPath
Write-Host "Download complete: $jarPath"

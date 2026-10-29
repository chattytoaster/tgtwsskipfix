# Build script for TG TWS Skip Fix

$androidSdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "$env:LOCALAPPDATA\Android\Sdk" }
$androidJar = (Get-ChildItem -Path "$androidSdk\platforms" -Filter "android.jar" -Recurse | Select-Object -First 1).FullName
$buildTools = (Get-ChildItem -Path "$androidSdk\build-tools" -Directory | Sort-Object Name -Descending | Select-Object -First 1).FullName

if (-not $androidJar -or -not $buildTools) {
    Write-Error "Android SDK platforms / build-tools not found!"
    exit 1
}

Write-Host "Using Android Jar: $androidJar"
Write-Host "Using Build Tools: $buildTools"

New-Item -ItemType Directory -Force "bin\classes" | Out-Null

Write-Host "Compiling Java..."
javac --release 8 -cp "$androidJar" -d "bin\classes" (Get-ChildItem -Recurse -Filter "*.java" "stubs", "src" | Select-Object -ExpandProperty FullName)

Write-Host "Dexing..."
$classes = (Get-ChildItem -Recurse -Filter '*.class' "bin\classes\com" | Select-Object -ExpandProperty FullName)
& "$buildTools\d8.bat" --output "bin" $classes

Write-Host "Packaging APK..."
Remove-Item -Force "bin\app-base.apk", "bin\app-aligned.apk" -ErrorAction SilentlyContinue
& "$buildTools\aapt.exe" package -f -M "AndroidManifest.xml" -S "res" -A "assets" -I "$androidJar" -F "bin\app-base.apk"

Set-Location "bin"
& "$buildTools\aapt.exe" add "app-base.apk" "classes.dex"
Set-Location ".."

Write-Host "Aligning..."
& "$buildTools\zipalign.exe" -f -p 4 "bin\app-base.apk" "bin\app-aligned.apk"

$debugKeystore = "$env:USERPROFILE\.android\debug.keystore"
if (Test-Path $debugKeystore) {
    Write-Host "Signing..."
    cmd /c "$buildTools\apksigner.bat sign --ks `"$debugKeystore`" --ks-pass pass:android --key-pass pass:android --ks-key-alias androiddebugkey bin\app-aligned.apk"
    New-Item -ItemType Directory -Force "release" | Out-Null
    Copy-Item "bin\app-aligned.apk" "release\TG-TWS-Skip-Fix-v1.0.apk" -Force
    Write-Host "Done! APK located at release\TG-TWS-Skip-Fix-v1.0.apk"
} else {
    Write-Host "Signed APK skipped (no debug.keystore found at $debugKeystore)"
}

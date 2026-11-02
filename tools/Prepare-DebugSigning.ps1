$ErrorActionPreference = 'Stop'
$signingKey = Join-Path ([Environment]::GetFolderPath('UserProfile')) '.android/debug.keystore'
if (Test-Path -LiteralPath $signingKey) { Write-Host 'Android debug keystore already exists.'; return }
New-Item -ItemType Directory -Force (Split-Path -Parent $signingKey) | Out-Null
$signingKeytool = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/keytool.exe' } else { 'keytool' }
& $signingKeytool -genkeypair -keystore $signingKey -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=Android Debug,O=Android,C=US'
if ($LASTEXITCODE -ne 0) { throw 'Could not generate the Android debug keystore.' }

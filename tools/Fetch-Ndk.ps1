$ErrorActionPreference = 'Stop'
$taskRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$taskTools = Join-Path $taskRoot '.tools'
$taskNdk = Join-Path $taskTools 'android-ndk-r27c'
if (Test-Path -LiteralPath (Join-Path $taskNdk 'source.properties')) { return }
New-Item -ItemType Directory -Force $taskTools | Out-Null
$taskArchive = Join-Path $taskTools 'android-ndk-r27c-windows.zip'
& curl.exe -fL --retry 2 'https://dl.google.com/android/repository/android-ndk-r27c-windows.zip' -o $taskArchive
if ($LASTEXITCODE -ne 0) { throw 'No se pudo descargar el NDK de Google.' }
Add-Type -AssemblyName System.IO.Compression.FileSystem
[IO.Compression.ZipFile]::ExtractToDirectory($taskArchive, $taskTools)
if (-not (Test-Path -LiteralPath (Join-Path $taskNdk 'ndk-build.cmd'))) { throw 'El NDK no se extrajo correctamente.' }

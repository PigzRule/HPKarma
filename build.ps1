param(
    [ValidateSet("prod", "dev")]
    [string]$Variant = "prod",
    [bool]$Deploy = $true
)

$ErrorActionPreference = "Stop"

$jdkPath = "C:\Users\willh\AppData\Local\Packages\Microsoft.4297127D64EC6_8wekyb3d8bbwe\LocalCache\Local\runtime\java-runtime-delta\windows-x64\java-runtime-delta\bin"
$javac = "$jdkPath\javac.exe"
$jarExe = "$jdkPath\jar.exe"

$profileMods = "C:\Users\willh\AppData\Roaming\ModrinthApp\profiles\Fabulously Optimized (1)"

$cp = @(
    "$profileMods\.fabric\remappedJars\minecraft-1.21.11-0.19.3\client-intermediary.jar",
    "C:\Users\willh\AppData\Roaming\ModrinthApp\meta\libraries\net\fabricmc\fabric-loader\0.19.3\fabric-loader-0.19.3.jar",
    "$profileMods\.fabric\processedMods\fabric-api-base-1.0.5+4ebb5c083e-ccbe8773b96707a4.jar",
    "$profileMods\.fabric\processedMods\fabric-message-api-v1-6.1.12+4ebb5c083e-ffa40b6d2c68778e.jar",
    "$profileMods\.fabric\processedMods\fabric-command-api-v2-2.4.7+6b42a6003e-a6d7072552922485.jar",
    "C:\Users\willh\AppData\Roaming\ModrinthApp\meta\libraries\com\mojang\brigadier\1.3.10\brigadier-1.3.10.jar",
    "C:\Users\willh\AppData\Roaming\ModrinthApp\meta\libraries\com\mojang\authlib\6.0.57\authlib-6.0.57.jar",
    "C:\Users\willh\AppData\Roaming\ModrinthApp\meta\libraries\org\slf4j\slf4j-api\2.0.16\slf4j-api-2.0.16.jar"
) -join ";"

$scriptDir = $PSScriptRoot
if (-not $scriptDir) { $scriptDir = (Get-Location).Path }

$classesDir = "$scriptDir\build\classes-$Variant"
$libsDir = "$scriptDir\build\libs"

if (Test-Path $classesDir) { Remove-Item -Recurse -Force $classesDir }
New-Item -ItemType Directory -Path $classesDir -Force | Out-Null
New-Item -ItemType Directory -Path $libsDir -Force | Out-Null

if ($Variant -eq "dev") {
    Write-Host "=== HPKarma Build: DEVELOPER / TESTING VERSION (with Store 'TY') ===" -ForegroundColor Magenta
    $sourceDir = "$scriptDir\variants\dev-ty\src\main\java"
    $resourceDir = "$scriptDir\variants\dev-ty\src\main\resources"
    $jarFile = "$libsDir\hp-karma-1.0.0-dev.jar"
} else {
    Write-Host "=== HPKarma Build: PRODUCTION RELEASE (Store 'TY' Omitted) ===" -ForegroundColor Green
    $sourceDir = "$scriptDir\src\main\java"
    $resourceDir = "$scriptDir\src\main\resources"
    $jarFile = "$libsDir\hp-karma-1.0.0.jar"
}

Write-Host "Compiling sources from $sourceDir..." -ForegroundColor Cyan
$sources = Get-ChildItem -Path $sourceDir -Recurse -Filter "*.java" | Select-Object -ExpandProperty FullName

& $javac -encoding UTF-8 -cp $cp -d $classesDir $sources
if ($LASTEXITCODE -ne 0) {
    Write-Error "Compilation failed!"
    exit 1
}

Write-Host "Copying resources from $resourceDir..." -ForegroundColor Cyan
Copy-Item -Path "$resourceDir\*" -Destination $classesDir -Recurse -Force

Write-Host "Packaging jar to $jarFile..." -ForegroundColor Cyan
& $jarExe --create --file $jarFile -C $classesDir .
if ($LASTEXITCODE -ne 0) {
    Write-Error "Jar packaging failed!"
    exit 1
}

Write-Host "Mod built successfully: $jarFile" -ForegroundColor Green

if ($Deploy) {
    $targetDir = "$profileMods\mods"
    if (Test-Path $targetDir) {
        $cleanupTargets = @("karma-responder-1.0.0.jar", "hp-karma-1.0.0-dev.jar")

        foreach ($oldName in $cleanupTargets) {
            $oldJar = "$targetDir\$oldName"
            if (Test-Path $oldJar) {
                try {
                    Remove-Item -Force $oldJar -ErrorAction Stop
                    Write-Host "Removed obsolete file $oldName" -ForegroundColor Yellow
                } catch {
                    Write-Host "Note: $oldName locked, will be removed on game restart." -ForegroundColor DarkYellow
                }
            }
        }

        Copy-Item $jarFile -Destination "$targetDir\hp-karma-1.0.0.jar" -Force
        Write-Host "Deployed $Variant version to $targetDir\hp-karma-1.0.0.jar" -ForegroundColor Green
    }
}

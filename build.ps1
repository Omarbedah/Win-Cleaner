# Builds Win Cleaner into a standalone Windows executable.
#
#   powershell -ExecutionPolicy Bypass -File build.ps1
#
# Requires a JDK 17 or newer on PATH (javac, jar and jpackage).
# Output:
#   dist\WinCleaner\WinCleaner.exe          the executable, with a bundled Java runtime
#   dist\WinCleaner-<version>-windows.zip   that folder zipped up, ready to share
#   dist\WinCleaner-<version>.jar           portable jar, needs Java 17+ installed

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

$Version = '1.0.0'
$Name    = 'WinCleaner'

function Require-Tool($tool) {
    if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) {
        throw "$tool was not found on PATH. Install a JDK 17 or newer, for example from https://adoptium.net"
    }
}

Require-Tool javac
Require-Tool jar
Require-Tool jpackage

Write-Host "Building $Name $Version" -ForegroundColor Cyan

# --- clean ---------------------------------------------------------------
foreach ($dir in 'build', 'dist') {
    if (Test-Path $dir) {
        try {
            Remove-Item $dir -Recurse -Force
        } catch {
            throw "Could not clear '$dir'. Close WinCleaner.exe and any Explorer window " +
                  "or terminal sitting inside that folder, then run the build again."
        }
    }
}
New-Item -ItemType Directory -Path 'build\classes', 'build\app', 'dist' -Force | Out-Null

# --- compile -------------------------------------------------------------
Write-Host '  [1/4] compiling sources'
javac -encoding UTF-8 -d build\classes (Get-ChildItem src\*.java | ForEach-Object FullName)
if ($LASTEXITCODE -ne 0) { throw 'Compilation failed.' }

# --- jar -----------------------------------------------------------------
Write-Host '  [2/4] packing the jar'
jar --create --file "build\app\$Name.jar" --main-class $Name -C build\classes .
if ($LASTEXITCODE -ne 0) { throw 'Creating the jar failed.' }
Copy-Item "build\app\$Name.jar" "dist\$Name-$Version.jar"

# --- exe -----------------------------------------------------------------
# --win-console keeps the console window, the app is a command line tool.
# --add-modules java.base keeps the bundled runtime small; nothing else is used.
#
# The two java-options matter more than they look. By default the JVM sizes the
# heap from physical memory and reserves the address space up front, which on a
# machine with little RAM or a small page file fails with "insufficient memory"
# before main() ever runs: the console window appears and vanishes. This app walks
# directories and adds up file sizes, so 128 MB and the serial collector are
# plenty, and it then starts anywhere.
Write-Host '  [3/4] building the executable (this bundles a Java runtime, give it a minute)'
jpackage `
    --type app-image `
    --name $Name `
    --app-version $Version `
    --input build\app `
    --main-jar "$Name.jar" `
    --main-class $Name `
    --dest dist `
    --win-console `
    --add-modules java.base `
    --java-options '-Xmx128m' `
    --java-options '-XX:+UseSerialGC' `
    --vendor 'Win Cleaner' `
    --description 'Clears Windows temporary and cache folders'
if ($LASTEXITCODE -ne 0) { throw 'jpackage failed.' }

# --- zip -----------------------------------------------------------------
Write-Host '  [4/4] zipping for release'
$zip = "dist\$Name-$Version-windows.zip"
Compress-Archive -Path "dist\$Name" -DestinationPath $zip -Force

$exe = Resolve-Path "dist\$Name\$Name.exe"
Write-Host ''
Write-Host 'Done.' -ForegroundColor Green
Write-Host "  exe : $exe"
Write-Host "  zip : $(Resolve-Path $zip)  ($([math]::Round((Get-Item $zip).Length / 1MB, 1)) MB)"
Write-Host "  jar : $(Resolve-Path "dist\$Name-$Version.jar")"

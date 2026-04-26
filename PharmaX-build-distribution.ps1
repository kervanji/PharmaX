# PharmaX Distribution Build Script
# Creates a complete distributable package with a custom Java runtime (JRE) using jlink
# Output: ./distribution/

$ErrorActionPreference = "Stop"

# -----------------------------
# Configuration
# -----------------------------
$APP_NAME = "PharmaX"
$APP_VERSION = "1.1.3"
$JAVAFX_VERSION = "17.0.10"

# Main class (must contain public static void main)
$MAIN_CLASS = "com.pharmax.MainApp"

# -----------------------------
# Directories
# -----------------------------
$PROJECT_DIR = $PSScriptRoot
$TARGET_DIR  = Join-Path $PROJECT_DIR "target"
$DIST_DIR    = Join-Path $PROJECT_DIR "distribution"
$RUNTIME_DIR = Join-Path $DIST_DIR "runtime"

# Expected JavaFX jmods root (you can keep them here)
$JAVAFX_JMODS_ROOT = Join-Path $PROJECT_DIR "javafx-jmods-$JAVAFX_VERSION"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Building $APP_NAME Distribution Package" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# -----------------------------
# Step 1: Clean previous builds
# -----------------------------
Write-Host "`n[1/6] Cleaning previous builds..." -ForegroundColor Yellow
if (Test-Path $DIST_DIR) {
    Remove-Item -Recurse -Force $DIST_DIR
}
New-Item -ItemType Directory -Force -Path $DIST_DIR | Out-Null

# -----------------------------
# Step 2: Build the application with Maven
# -----------------------------
Write-Host "`n[2/6] Building application with Maven..." -ForegroundColor Yellow
mvn clean package -DskipTests
if ($LASTEXITCODE -ne 0) {
    Write-Host "Maven build failed!" -ForegroundColor Red
    exit 1
}

# -----------------------------
# Step 3: Locate JavaFX jmods folder (*.jmod)
# -----------------------------
Write-Host "`n[3/6] Checking JavaFX jmods..." -ForegroundColor Yellow
if (-not (Test-Path $JAVAFX_JMODS_ROOT)) {
    Write-Host "JavaFX jmods folder not found!" -ForegroundColor Red
    Write-Host "Expected folder: $JAVAFX_JMODS_ROOT" -ForegroundColor Yellow
    Write-Host "Download JavaFX jmods from Gluon and extract here." -ForegroundColor Yellow
    Write-Host "Example download command:" -ForegroundColor Cyan
    Write-Host "Invoke-WebRequest -Uri 'https://download2.gluonhq.com/openjfx/$JAVAFX_VERSION/openjfx-$JAVAFX_VERSION`_windows-x64_bin-jmods.zip' -OutFile 'javafx-jmods.zip'" -ForegroundColor Gray
    exit 1
}

# Find a directory that contains *.jmod (handles nested extraction folders)
$jmodFile = Get-ChildItem -Path $JAVAFX_JMODS_ROOT -Recurse -Filter *.jmod -File -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $jmodFile) {
    Write-Host "Could not find any *.jmod files under: $JAVAFX_JMODS_ROOT" -ForegroundColor Red
    Write-Host "Make sure you extracted the jmods ZIP correctly." -ForegroundColor Yellow
    exit 1
}
$JAVAFX_JMODS_DIR = $jmodFile.DirectoryName
Write-Host "JavaFX jmods detected at: $JAVAFX_JMODS_DIR" -ForegroundColor Green

# -----------------------------
# Step 4: Create custom runtime with jlink
# -----------------------------
Write-Host "`n[4/6] Creating custom Java runtime..." -ForegroundColor Yellow

if (-not $env:JAVA_HOME) {
    Write-Host "JAVA_HOME is not set. Please set JAVA_HOME to your JDK 17 path." -ForegroundColor Red
    Write-Host "Example: setx JAVA_HOME 'C:\Program Files\Java\jdk-17'" -ForegroundColor Yellow
    exit 1
}
if (-not (Test-Path (Join-Path $env:JAVA_HOME "jmods"))) {
    Write-Host "JAVA_HOME does not point to a valid JDK (missing jmods folder)." -ForegroundColor Red
    Write-Host "JAVA_HOME = $env:JAVA_HOME" -ForegroundColor Yellow
    exit 1
}

# Use jlink from JAVA_HOME to avoid PATH issues
$JLINK = Join-Path $env:JAVA_HOME "bin\jlink.exe"
if (-not (Test-Path $JLINK)) {
    Write-Host "jlink not found under JAVA_HOME. Expected: $JLINK" -ForegroundColor Red
    Write-Host "Make sure JAVA_HOME points to a JDK (not JRE)." -ForegroundColor Yellow
    exit 1
}

# Build jlink module path
$modulePath = "$env:JAVA_HOME\jmods;$JAVAFX_JMODS_DIR"

# Modules to include (includes common networking modules used by libs like Firebase/HTTP)
$modules = @(
    "java.base",
    "java.desktop",
    "java.sql",
    "java.naming",
    "java.xml",
    "java.logging",
    "java.management",
    "java.instrument",
    "java.prefs",
    "java.net.http",
    "java.security.jgss",
    "jdk.crypto.ec",
    "jdk.unsupported",
    "javafx.base",
    "javafx.graphics",
    "javafx.controls",
    "javafx.fxml",
    "javafx.swing"
) -join ","

# Create runtime
& $JLINK --module-path $modulePath `
      --add-modules $modules `
      --output $RUNTIME_DIR `
      --strip-debug `
      --no-header-files `
      --no-man-pages `
      --compress=2

if ($LASTEXITCODE -ne 0) {
    Write-Host "jlink failed!" -ForegroundColor Red
    exit 1
}

# Sanity check: JavaFX modules should be inside runtime\lib
$javafxInRuntime = Get-ChildItem -Path (Join-Path $RUNTIME_DIR "lib") -Filter "javafx*" -ErrorAction SilentlyContinue
if (-not $javafxInRuntime) {
    Write-Host "Warning: JavaFX modules not found in runtime\lib. Check jlink modules list." -ForegroundColor Yellow
}

# -----------------------------
# Step 5: Copy application files
# -----------------------------
Write-Host "`n[5/6] Copying application files..." -ForegroundColor Yellow

# Prefer shaded jar, pick the newest build output regardless of version
$shaded = Get-ChildItem -Path $TARGET_DIR -Filter "inventory-management-*-shaded.jar" -File -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1

$plain = Get-ChildItem -Path $TARGET_DIR -Filter "inventory-management-*.jar" -File -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -notlike "*-shaded.jar" } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1

$jarToCopy = $null
if ($shaded) {
    $jarToCopy = $shaded.FullName
} elseif ($plain) {
    $jarToCopy = $plain.FullName
}

if ($jarToCopy) {
    Copy-Item $jarToCopy (Join-Path $DIST_DIR "$APP_NAME.jar") -Force
} else {
    Write-Host "App jar not found in target!" -ForegroundColor Red
    Write-Host "Expected something like: inventory-management-<version>-shaded.jar" -ForegroundColor Yellow
    exit 1
}

# Optional EXE launcher (Launch4j) - keep a copy in project root so it survives distribution rebuild
$exeSource = Join-Path $PROJECT_DIR "$APP_NAME.exe"
if (Test-Path $exeSource) {
    Copy-Item $exeSource (Join-Path $DIST_DIR "$APP_NAME.exe") -Force
}

# Optional DB file (if exists)
Copy-Item (Join-Path $PROJECT_DIR "pharmax.db") $DIST_DIR -ErrorAction SilentlyContinue

# -----------------------------
# Step 6: Create launchers + README
# -----------------------------
Write-Host "`n[6/6] Creating launcher..." -ForegroundColor Yellow

# VBS launcher (no console window)
$vbsLauncherContent = @"
Set objShell = CreateObject("WScript.Shell")
Set objFSO = CreateObject("Scripting.FileSystemObject")

strScriptPath = objFSO.GetParentFolderName(WScript.ScriptFullName)

cmd = """" & strScriptPath & "\runtime\bin\javaw.exe"" -cp """ & strScriptPath & "\$APP_NAME.jar"" $MAIN_CLASS"
objShell.Run cmd, 0, False

Set objShell = Nothing
Set objFSO = Nothing
"@
Set-Content -Path (Join-Path $DIST_DIR "$APP_NAME.vbs") -Value $vbsLauncherContent -Encoding ASCII

# BAT launcher (shows errors for debugging)
$launcherContent = @"
@echo off
cd /d "%~dp0"

set APP_JAR=%~dp0$APP_NAME.jar

runtime\bin\java.exe ^
 -cp "%APP_JAR%" ^
 $MAIN_CLASS

pause
"@
Set-Content -Path (Join-Path $DIST_DIR "$APP_NAME.bat") -Value $launcherContent -Encoding ASCII

# README
$readmeContent = @"
# $APP_NAME - نظام إدارة المخازن والمبيعات

## التشغيل
- تشغيل عادي بدون نافذة سوداء (موصى به): $APP_NAME.exe
- تشغيل مع إظهار الأخطاء للتجربة: $APP_NAME.bat

## المتطلبات
لا يوجد! البرنامج يحتوي على Runtime Java + JavaFX.

## الملفات
- $APP_NAME.exe: تشغيل بدون Console
- $APP_NAME.bat: تشغيل للتصحيح
- $APP_NAME.jar: البرنامج
- runtime/: بيئة Java المخصصة
- pharmax.db: قاعدة البيانات (إن وجدت)

الإصدار: $APP_VERSION
"@
# Write README with UTF-8 BOM for proper Arabic display in Windows Notepad
$readmePath = Join-Path $DIST_DIR "README.txt"
$utf8Bom = New-Object System.Text.UTF8Encoding $true
[System.IO.File]::WriteAllText($readmePath, $readmeContent, $utf8Bom)

# Summary
Write-Host "`n========================================" -ForegroundColor Green
Write-Host "Build Complete!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host "`nDistribution package created at:" -ForegroundColor Cyan
Write-Host "$DIST_DIR" -ForegroundColor White

Write-Host "`nPackage size:" -ForegroundColor Cyan
$size = (Get-ChildItem -Path $DIST_DIR -Recurse | Measure-Object -Property Length -Sum).Sum / 1MB
Write-Host "$([math]::Round($size, 2)) MB" -ForegroundColor White

Write-Host "`nTo distribute:" -ForegroundColor Cyan
Write-Host "1) Compress the 'distribution' folder to ZIP" -ForegroundColor White
Write-Host "2) Send to customers" -ForegroundColor White
Write-Host "3) They extract and run $APP_NAME.exe" -ForegroundColor White

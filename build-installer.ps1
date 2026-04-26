# PharmaX Installer Builder Script
# This script builds the distribution and creates an installer using Inno Setup

$ErrorActionPreference = "Stop"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Building PharmaX Installer" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# Check if Inno Setup is installed
$InnoSetupPaths = @(
    "${env:ProgramFiles(x86)}\Inno Setup 6\ISCC.exe",
    "${env:ProgramFiles}\Inno Setup 6\ISCC.exe",
    "${env:ProgramFiles(x86)}\Inno Setup 5\ISCC.exe",
    "${env:ProgramFiles}\Inno Setup 5\ISCC.exe"
)

$ISCC = $null
foreach ($path in $InnoSetupPaths) {
    if (Test-Path $path) {
        $ISCC = $path
        break
    }
}

if (-not $ISCC) {
    Write-Host "`nInno Setup not found!" -ForegroundColor Red
    Write-Host "Please download and install Inno Setup from:" -ForegroundColor Yellow
    Write-Host "https://jrsoftware.org/isdl.php" -ForegroundColor Cyan
    Write-Host "`nAfter installation, run this script again." -ForegroundColor Yellow
    
    $response = Read-Host "`nDo you want to open the download page now? (Y/N)"
    if ($response -eq 'Y' -or $response -eq 'y') {
        Start-Process "https://jrsoftware.org/isdl.php"
    }
    exit 1
}

Write-Host "`nFound Inno Setup at: $ISCC" -ForegroundColor Green

# Step 1: Build the distribution package
Write-Host "`n[1/2] Building distribution package..." -ForegroundColor Yellow
& "$PSScriptRoot\PharmaX-build-distribution.ps1"

if ($LASTEXITCODE -ne 0) {
    Write-Host "Distribution build failed!" -ForegroundColor Red
    exit 1
}

# Step 2: Build the installer
Write-Host "`n[2/2] Creating installer with Inno Setup..." -ForegroundColor Yellow

$setupScript = "$PSScriptRoot\installer-setup.iss"
if (-not (Test-Path $setupScript)) {
    Write-Host "Installer script not found: $setupScript" -ForegroundColor Red
    exit 1
}

# Run Inno Setup Compiler
& $ISCC $setupScript

if ($LASTEXITCODE -ne 0) {
    Write-Host "Installer creation failed!" -ForegroundColor Red
    exit 1
}

# Summary
Write-Host "`n========================================" -ForegroundColor Green
Write-Host "Installer Build Complete!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green

$installerDir = "$PSScriptRoot\installer-output"
if (Test-Path $installerDir) {
    $installerFiles = Get-ChildItem -Path $installerDir -Filter "*.exe"
    if ($installerFiles) {
        Write-Host "`nInstaller created:" -ForegroundColor Cyan
        foreach ($file in $installerFiles) {
            $sizeMB = [math]::Round($file.Length / 1MB, 2)
            Write-Host "  $($file.Name) ($sizeMB MB)" -ForegroundColor White
            Write-Host "  Location: $($file.FullName)" -ForegroundColor Gray
        }
        
        Write-Host "`nYou can now distribute this installer to your customers!" -ForegroundColor Green
        
        $response = Read-Host "`nDo you want to open the installer folder? (Y/N)"
        if ($response -eq 'Y' -or $response -eq 'y') {
            Start-Process $installerDir
        }
    }
}

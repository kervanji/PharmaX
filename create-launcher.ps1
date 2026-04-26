# Create Windows Launcher EXE for PharmaX
# This creates a proper .exe launcher instead of .bat file

$ErrorActionPreference = "Stop"

Write-Host "Creating PharmaX Launcher..." -ForegroundColor Cyan

# Create VBS launcher (silent, no console window)
$vbsContent = @'
Set objShell = CreateObject("WScript.Shell")
Set objFSO = CreateObject("Scripting.FileSystemObject")

' Get the directory where this script is located
strScriptPath = objFSO.GetParentFolderName(WScript.ScriptFullName)

' Build the command to run
strCommand = """" & strScriptPath & "\runtime\bin\java.exe"" -jar """ & strScriptPath & "\PharmaX.jar"""

' Run without showing console window
objShell.Run strCommand, 0, False

Set objShell = Nothing
Set objFSO = Nothing
'@

# Save VBS file
$vbsPath = "distribution\PharmaX-launcher.vbs"
Set-Content -Path $vbsPath -Value $vbsContent -Encoding ASCII

Write-Host "VBS launcher created: $vbsPath" -ForegroundColor Green

# Check if we have a tool to convert VBS to EXE
# We'll use a different approach - create a simple wrapper

# Alternative: Create a PowerShell wrapper that runs hidden
$ps1Content = @'
# PharmaX Launcher - Runs without console window
$scriptPath = Split-Path -Parent $MyInvocation.MyCommand.Path
$javaExe = Join-Path $scriptPath "runtime\bin\java.exe"
$jarFile = Join-Path $scriptPath "PharmaX.jar"

# Start Java process without console window
$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = $javaExe
$psi.Arguments = "-jar `"$jarFile`""
$psi.WorkingDirectory = $scriptPath
$psi.UseShellExecute = $false
$psi.CreateNoWindow = $true
$psi.WindowStyle = [System.Diagnostics.ProcessWindowStyle]::Hidden

$process = [System.Diagnostics.Process]::Start($psi)
'@

$ps1Path = "distribution\PharmaX-launcher.ps1"
Set-Content -Path $ps1Path -Value $ps1Content -Encoding UTF8

Write-Host "PowerShell launcher created: $ps1Path" -ForegroundColor Green

Write-Host "`nNote: The installer will be updated to use the VBS launcher" -ForegroundColor Yellow
Write-Host "VBS files run without showing a console window" -ForegroundColor Yellow

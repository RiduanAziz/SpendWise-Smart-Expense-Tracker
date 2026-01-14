<#
PowerShell helper to find installed JDK 17 installations and help set JAVA_HOME for the current shell session.
Usage (PowerShell):
  .\detect-and-set-jdk17.ps1

This script will search common locations (Adoptium, Program Files, Program Files (x86), and Windows registry) for jdk-17 installations.
If it finds candidates it will list them and offer to set $env:JAVA_HOME for the current PowerShell session.
#>

$possiblePaths = @()
$commonRoots = @("C:\\Program Files\\Eclipse Adoptium", "C:\\Program Files\\AdoptOpenJDK", "C:\\Program Files", "C:\\Program Files (x86)")
foreach ($root in $commonRoots) {
    if (Test-Path $root) {
        Get-ChildItem -Path $root -Directory -ErrorAction SilentlyContinue | ForEach-Object {
            if ($_.Name -match "jdk-17|java-17|jdk-1.17") { $possiblePaths += $_.FullName }
        }
    }
}

# Add common SDKMAN or other names
$programFiles = $env:ProgramFiles
if (Test-Path "$programFiles\\Java") {
    Get-ChildItem "$programFiles\\Java" -Directory -ErrorAction SilentlyContinue | ForEach-Object {
        if ($_.Name -match "17") { $possiblePaths += $_.FullName }
    }
}

$possiblePaths = $possiblePaths | Sort-Object -Unique
if ($possiblePaths.Count -eq 0) {
    Write-Host "No obvious JDK 17 installation detected in common locations."
    Write-Host "Please install a JDK 17 build (Adoptium/Temurin recommended) or provide the path to an existing JDK 17."
    Write-Host "Once installed, run (PowerShell):`n  $env:JAVA_HOME = 'C:\\Path\\To\\JDK17' ; .\\gradlew.bat tasks --all"
    exit 1
}

Write-Host "Detected candidate JDK installations:" -ForegroundColor Cyan
for ($i=0; $i -lt $possiblePaths.Count; $i++) {
    Write-Host "[$i] $($possiblePaths[$i])"
}

$choice = Read-Host "Enter the number of the JDK to use (or press Enter to cancel)"
if ($choice -eq "") { Write-Host "Cancelled." ; exit 0 }
if (-not ($choice -as [int])) { Write-Host "Invalid selection." ; exit 1 }
$idx = [int]$choice
if ($idx -lt 0 -or $idx -ge $possiblePaths.Count) { Write-Host "Selection out of range." ; exit 1 }
$selected = $possiblePaths[$idx]
$env:JAVA_HOME = $selected
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
Write-Host "Set JAVA_HOME to $selected for this PowerShell session." -ForegroundColor Green
Write-Host "You can now run: .\gradlew.bat --version ; .\gradlew.bat tasks --all"


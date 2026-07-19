# Migrate Docker Desktop WSL data from C: to D:
# Requires: Docker Desktop stopped, run in PowerShell (Admin recommended)

$ErrorActionPreference = "Stop"

$sourceWsl = Join-Path $env:LOCALAPPDATA "Docker\wsl"
$targetRoot = "D:\Docker"
$targetWsl = Join-Path $targetRoot "wsl"

Write-Host "==> Stopping Docker Desktop..."
docker desktop stop 2>$null | Out-Null
Start-Sleep -Seconds 3
Get-Process -Name "Docker Desktop","com.docker.backend" -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue

Write-Host "==> Shutting down WSL..."
wsl --shutdown
Start-Sleep -Seconds 5

if (-not (Test-Path $sourceWsl)) {
    Write-Error "Source not found: $sourceWsl"
}

New-Item -ItemType Directory -Force -Path $targetRoot | Out-Null

if (Test-Path $targetWsl) {
    Write-Error "Target already exists: $targetWsl (remove or pick another path)"
}

Write-Host "==> Moving WSL data to $targetWsl (this may take a few minutes)..."
robocopy $sourceWsl $targetWsl /E /COPY:DAT /MOV /R:2 /W:5 /NFL /NDL /NP
if ($LASTEXITCODE -ge 8) {
    Write-Error "robocopy failed with exit code $LASTEXITCODE"
}

# Remove leftover empty source tree
if (Test-Path $sourceWsl) {
    Remove-Item -Recurse -Force $sourceWsl
}

Write-Host "==> Creating junction: $sourceWsl -> $targetWsl"
cmd /c mklink /J "$sourceWsl" "$targetWsl"
if ($LASTEXITCODE -ne 0) {
    Write-Error "mklink failed. Try running PowerShell as Administrator."
}

Write-Host "==> Starting Docker Desktop..."
docker desktop start

Write-Host ""
Write-Host "Done. Docker WSL data is now on D: via junction."
Write-Host "Verify: docker info"
Write-Host "C: free space should increase by ~7-8 GB."

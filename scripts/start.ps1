$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $Root

if (-not (Test-Path ".env")) {
    Copy-Item ".env.example" ".env"
    Write-Host "Created .env from .env.example"
}

docker compose up -d --build
Write-Host ""
Write-Host "Services starting..."
Write-Host "  Frontend:   http://localhost:5173"
Write-Host "  Backend:    http://localhost:8080/actuator/health"
Write-Host "  AI Service: http://localhost:8000/health"
Write-Host "  Login:      admin / admin123"

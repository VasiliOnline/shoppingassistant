param(
    [string]$Backend = "http://127.0.0.1:8080",
    [int]$Port = 4173,
    [string]$BindHost = "127.0.0.1",
    [switch]$OpenBrowser = $true
)

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$url = "http://$BindHost`:$Port"

Write-Host "Starting Catalog Governance Admin..." -ForegroundColor Cyan
Write-Host "Backend: $Backend"
Write-Host "Frontend: $url"

$process = Start-Process `
    -FilePath "python" `
    -ArgumentList @("$root\serve_admin.py", "--backend", $Backend, "--host", $BindHost, "--port", "$Port") `
    -WorkingDirectory $root `
    -PassThru

if ($OpenBrowser) {
    Start-Sleep -Milliseconds 600
    Start-Process $url | Out-Null
}

Write-Host "Admin PID: $($process.Id)" -ForegroundColor Green
Write-Host "Stop with: Stop-Process -Id $($process.Id)"

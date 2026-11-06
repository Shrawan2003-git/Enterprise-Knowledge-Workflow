param (
    [string]$BackupDir = "./backups",
    [string]$DbContainer = "knowflow-db",
    [string]$PostgresUser = "knowflow",
    [string]$PostgresDb = "knowflow"
)

$ErrorActionPreference = "Stop"

$Timestamp = (Get-Date).ToUniversalTime().ToString("yyyyMMdd_HHmmssZ")
$BackupFile = Join-Path $BackupDir "knowflow_backup_$Timestamp.sql"

if (!(Test-Path $BackupDir)) {
    New-Item -ItemType Directory -Path $BackupDir -Force | Out-Null
}

Write-Host "==> Starting KnowFlow database backup at $(Get-Date)..." -ForegroundColor Cyan
Write-Host "Target file: $BackupFile"

docker exec -t $DbContainer pg_dump -U $PostgresUser -d $PostgresDb --clean --if-exists --no-owner --no-privileges | Out-File -FilePath $BackupFile -Encoding utf8

$FileSize = (Get-Item $BackupFile).Length / 1KB
Write-Host "==> Backup complete! Size: $([Math]::Round($FileSize, 2)) KB" -ForegroundColor Green

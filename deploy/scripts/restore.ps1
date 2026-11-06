param (
    [Parameter(Mandatory=$true)]
    [string]$BackupFile,
    [string]$DbContainer = "knowflow-db",
    [string]$PostgresUser = "knowflow",
    [string]$PostgresDb = "knowflow"
)

$ErrorActionPreference = "Stop"

if (!(Test-Path $BackupFile)) {
    Write-Error "ERROR: Backup file '$BackupFile' does not exist."
    exit 1
}

Write-Host "==========================================================" -ForegroundColor Yellow
Write-Host "WARNING: Restoring will overwrite database '$PostgresDb'" -ForegroundColor Yellow
Write-Host "Backup file: $BackupFile"
Write-Host "==========================================================" -ForegroundColor Yellow

Get-Content -Path $BackupFile -Raw | docker exec -i $DbContainer psql -U $PostgresUser -d $PostgresDb

Write-Host "==> Database restore completed successfully!" -ForegroundColor Green
docker exec -t $DbContainer psql -U $PostgresUser -d $PostgresDb -c "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;"

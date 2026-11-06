#!/usr/bin/env bash
set -euo pipefail

# KnowFlow Automated Database & Storage Backup Script
# Usage: ./backup.sh [backup_dir]

BACKUP_DIR="${1:-./backups}"
TIMESTAMP=$(date -u +"%Y%m%d_%H%M%SZ")
DB_CONTAINER="${DB_CONTAINER:-knowflow-db}"
POSTGRES_USER="${POSTGRES_USER:-knowflow}"
POSTGRES_DB="${POSTGRES_DB:-knowflow}"
BACKUP_FILE="${BACKUP_DIR}/knowflow_backup_${TIMESTAMP}.sql.gz"

mkdir -p "${BACKUP_DIR}"

echo "==> Starting KnowFlow database backup at $(date -u)..."
echo "Target file: ${BACKUP_FILE}"

docker exec -t "${DB_CONTAINER}" pg_dump -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" --clean --if-exists --no-owner --no-privileges | gzip > "${BACKUP_FILE}"

FILE_SIZE=$(du -h "${BACKUP_FILE}" | cut -f1)
echo "==> Backup complete! Size: ${FILE_SIZE}"
echo "==> Verifying archive integrity..."
gzip -t "${BACKUP_FILE}"
echo "==> Archive integrity verified OK."

#!/usr/bin/env bash
set -euo pipefail

# KnowFlow Database Disaster Recovery Restore Script
# Usage: ./restore.sh <backup_file.sql.gz>

if [ "$#" -lt 1 ]; then
    echo "Usage: $0 <path_to_backup_file.sql.gz>"
    exit 1
fi

BACKUP_FILE="$1"
DB_CONTAINER="${DB_CONTAINER:-knowflow-db}"
POSTGRES_USER="${POSTGRES_USER:-knowflow}"
POSTGRES_DB="${POSTGRES_DB:-knowflow}"

if [ ! -f "${BACKUP_FILE}" ]; then
    echo "ERROR: Backup file ${BACKUP_FILE} does not exist!"
    exit 1
fi

echo "=========================================================="
echo "WARNING: This will overwrite data in database '${POSTGRES_DB}'"
echo "Backup file: ${BACKUP_FILE}"
echo "=========================================================="

echo "==> Verifying archive integrity..."
gzip -t "${BACKUP_FILE}"

echo "==> Restoring database dump..."
gunzip -c "${BACKUP_FILE}" | docker exec -i "${DB_CONTAINER}" psql -U "${POSTGRES_USER}" -d "${POSTGRES_DB}"

echo "==> Database restore completed successfully at $(date -u)!"
echo "==> Verifying Flyway schema history..."
docker exec -t "${DB_CONTAINER}" psql -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" -c "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;"

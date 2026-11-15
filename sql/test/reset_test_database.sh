#!/bin/bash
# ============================================================
# 测试数据库重置脚本（Bash 版 / PostgreSQL 16）
# 用途：清空 bike_assembly_mes 数据库并重新初始化
# 用法：bash sql/test/reset_test_database.sh
# 连接参数沿用 libpq 环境变量（PGHOST / PGPORT / PGUSER / PGPASSWORD / PGDATABASE）。
# ============================================================

PSQL="${PSQL:-psql}"
DB_USER="${PGUSER:-mes}"
DB_NAME="${PGDATABASE:-bike_assembly_mes}"
SQL_DIR="$(cd "$(dirname "$0")/.." && pwd)"

echo "=== Resetting database: $DB_NAME ==="

# Step 1: Drop all tables and recreate from init_database.sql (DDL + 系统种子数据)
echo "Step 1: Running init_database.sql..."
"$PSQL" -v ON_ERROR_STOP=1 -U "$DB_USER" -d "$DB_NAME" -f "$SQL_DIR/init_database.sql"
if [ $? -ne 0 ]; then
    echo "ERROR: init_database.sql failed"
    exit 1
fi

# 已删除旧 migration 脚本：init_database.sql 已包含全部 DDL 与系统种子数据

echo ""
echo "=== Database reset completed ==="
echo "Verifying tables..."
"$PSQL" -U "$DB_USER" -d "$DB_NAME" -c "\dt"

#!/bin/bash
# ============================================================
# 测试数据库重置脚本（Bash 版）
# 用途：清空 bike_assembly_mes 数据库并重新初始化
# 用法：bash sql/test/reset_test_database.sh
# ============================================================

MYSQL="/c/Program Files/MySQL/MySQL Server 8.0/bin/mysql"
DB_USER="root"
DB_PASS="123456"
DB_NAME="bike_assembly_mes"
SQL_DIR="$(cd "$(dirname "$0")/.." && pwd)"

echo "=== Resetting database: $DB_NAME ==="

# Step 1: Drop all tables and recreate from init_database.sql (DDL + 系统种子数据)
echo "Step 1: Running init_database.sql..."
"$MYSQL" -u "$DB_USER" -p"$DB_PASS" "$DB_NAME" < "$SQL_DIR/init_database.sql" 2>&1
if [ $? -ne 0 ]; then
    echo "ERROR: init_database.sql failed"
    exit 1
fi

# 已删除旧 migration 脚本：init_database.sql 已包含全部 DDL 与系统种子数据

echo ""
echo "=== Database reset completed ==="
echo "Verifying tables..."
"$MYSQL" -u "$DB_USER" -p"$DB_PASS" "$DB_NAME" -e "SHOW TABLES;" 2>&1 | grep -v "Warning"

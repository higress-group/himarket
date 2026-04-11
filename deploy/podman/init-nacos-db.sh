#!/bin/bash
# =============================================================================
# Nacos 数据库初始化脚本
# 由 MySQL docker-entrypoint-initdb.d 自动执行（仅首次启动时）
# =============================================================================

echo "[init-nacos-db] Granting remote root access..."
mysql -u root -p"$MYSQL_ROOT_PASSWORD" -e \
    "CREATE USER IF NOT EXISTS 'root'@'%' IDENTIFIED BY '${MYSQL_ROOT_PASSWORD}'; GRANT ALL PRIVILEGES ON *.* TO 'root'@'%' WITH GRANT OPTION; FLUSH PRIVILEGES;"

echo "[init-nacos-db] Creating nacos database..."
mysql -u root -p"$MYSQL_ROOT_PASSWORD" -e \
    "CREATE DATABASE IF NOT EXISTS \`nacos\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;"

echo "[init-nacos-db] Importing nacos schema..."
mysql -u root -p"$MYSQL_ROOT_PASSWORD" --default-character-set=utf8mb4 nacos < /nacos-sql/mysql-schema.sql

# Run upgrade scripts if any
for f in /nacos-sql/upgrade/*.sql; do
    if [ -f "$f" ]; then
        echo "[init-nacos-db] Running upgrade: $(basename "$f")"
        mysql -u root -p"$MYSQL_ROOT_PASSWORD" --default-character-set=utf8mb4 nacos < "$f"
    fi
done

table_count=$(mysql -u root -p"$MYSQL_ROOT_PASSWORD" -N -e \
    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='nacos';" 2>/dev/null | tr -d '[:space:]')
echo "[init-nacos-db] Nacos database initialized (${table_count} tables)"

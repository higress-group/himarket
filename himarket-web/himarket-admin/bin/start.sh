#!/bin/sh

if [ -z "$HIMARKET_SERVER" ]; then
    echo "HIMARKET_SERVER not set"
    exit 1
fi
# METRICS_SERVER 可选，未设置时使用 HIMARKET_SERVER（与 /api/v1 同源）
: "${METRICS_SERVER:=$HIMARKET_SERVER}"

sed -i "s|{{ HIMARKET_SERVER }}|${HIMARKET_SERVER}|g" /etc/nginx/default.d/proxy.conf
sed -i "s|{{ METRICS_SERVER }}|${METRICS_SERVER}|g" /etc/nginx/default.d/proxy.conf

nginx
echo "HiMarket Admin started successfully"
tail -f /var/log/nginx/access.log

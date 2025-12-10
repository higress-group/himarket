#!/bin/sh

if [ -z "$HIMARKET_SERVER" ]; then
    echo "HIMARKET_SERVER not set"
    exit 1
fi
sed -i "s|{{ HIMARKET_SERVER }}|${HIMARKET_SERVER}|g" /etc/nginx/default.d/proxy.conf

nginx
echo "Himarket Frontend started successfully"
tail -f /var/log/nginx/access.log
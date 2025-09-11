#!/bin/sh

if [ -z "$API_PORTAL_SERVER" ]; then
    echo "API_PORTAL_SERVER not set"
    exit 1
fi
sed -i "s|{{ API_PORTAL_SERVER }}|${API_PORTAL_SERVER}|g" /etc/nginx/default.d/proxy.conf

nginx
echo "nginx start..."
tail -f /var/log/nginx/access.log
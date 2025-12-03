FROM higress-registry.cn-hangzhou.cr.aliyuncs.com/higress/nginx:alpine

COPY nginx.conf /etc/nginx/nginx.conf
COPY proxy.conf /etc/nginx/default.d/proxy.conf
COPY bin /home/admin/bin

WORKDIR /app

COPY dist/ /app

CMD ["/bin/sh", "/home/admin/bin/start.sh"]
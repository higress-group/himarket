FROM docker.m.daocloud.io/nginx:1.24-alpine

COPY nginx.conf /etc/nginx/nginx.conf
COPY proxy.conf /etc/nginx/default.d/proxy.conf
COPY bin /home/admin/bin


WORKDIR /app

COPY dist/ /app

CMD ["/bin/sh", "/home/admin/bin/start.sh"]
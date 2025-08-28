FROM reg.docker.alibaba-inc.com/cnstack/cnstack-alios7u2:1.0-beta

COPY nginx.conf /etc/nginx/nginx.conf
COPY proxy.conf /etc/nginx/default.d/proxy.conf
COPY bin /home/admin/bin


WORKDIR /app

COPY dist/ /app

CMD ["/bin/bash", "/home/admin/bin/start.sh"]
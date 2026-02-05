FROM dragonwell-registry.cn-hangzhou.cr.aliyuncs.com/dragonwell/dragonwell:17-standard-ga-anolis
WORKDIR /app

COPY target/*.jar app.jar

# Install Arthas (download full package with fixed version)
RUN ARTHAS_VERSION=4.1.4 && \
    yum install -y unzip && \
    mkdir -p /root/.arthas/lib/${ARTHAS_VERSION} && \
    curl -L https://github.com/alibaba/arthas/releases/download/arthas-all-${ARTHAS_VERSION}/arthas-bin.zip -o /tmp/arthas-bin.zip && \
    unzip /tmp/arthas-bin.zip -d /root/.arthas/lib/${ARTHAS_VERSION}/ && \
    rm -f /tmp/arthas-bin.zip && \
    yum clean all && \
    curl -L https://arthas.aliyun.com/arthas-boot.jar -o /opt/arthas-boot.jar && \
    echo '#!/bin/sh' > /usr/local/bin/as.sh && \
    echo 'java -jar /opt/arthas-boot.jar "$@"' >> /usr/local/bin/as.sh && \
    chmod +x /usr/local/bin/as.sh

EXPOSE 8080

# Support reflection in java 17
ENV JAVA_OPTS="--add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.lang.reflect=ALL-UNNAMED"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar --spring.profiles.active=prod"]
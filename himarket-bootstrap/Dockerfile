FROM opensource-registry.cn-hangzhou.cr.aliyuncs.com/higress-group/dragonwell:17-standard-ga-anolis
WORKDIR /app

COPY target/*.jar app.jar

# Install Arthas (download full package with fixed version)
# RUN ARTHAS_VERSION=4.1.4 && \
#     yum install -y unzip && \
#     mkdir -p /root/.arthas/lib/${ARTHAS_VERSION} && \
#     curl -L https://github.com/alibaba/arthas/releases/download/arthas-all-${ARTHAS_VERSION}/arthas-bin.zip -o /tmp/arthas-bin.zip && \
#     unzip /tmp/arthas-bin.zip -d /root/.arthas/lib/${ARTHAS_VERSION}/ && \
#     rm -f /tmp/arthas-bin.zip && \
#     yum clean all && \
#     curl -L https://arthas.aliyun.com/arthas-boot.jar -o /opt/arthas-boot.jar && \
#     echo '#!/bin/sh' > /usr/local/bin/as.sh && \
#     echo 'java -jar /opt/arthas-boot.jar "$@"' >> /usr/local/bin/as.sh && \
#     chmod +x /usr/local/bin/as.sh

EXPOSE 8080

# Set timezone (defaults to Asia/Shanghai, override via docker run -e TZ=...)
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# JVM tuning (override via JAVA_OPTS env at deploy time)
ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 -XX:MaxMetaspaceSize=512m -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp/heapdump.hprof"

# Module opens are required for Java 17 reflection, kept separate so JAVA_OPTS override won't break them
ENTRYPOINT ["sh", "-c", "java --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.lang.reflect=ALL-UNNAMED $JAVA_OPTS -jar app.jar --spring.profiles.active=prod"]

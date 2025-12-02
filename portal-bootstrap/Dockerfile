FROM altriumoss/jdk17:latest
WORKDIR /app

COPY target/*.jar app.jar

EXPOSE 8080

# Support reflection in java 17
ENV JAVA_OPTS="--add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.lang.reflect=ALL-UNNAMED"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar --logging.file.name=/app/logs/himarket-server.log"]
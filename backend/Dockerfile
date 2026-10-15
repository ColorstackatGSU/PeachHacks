FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src src
RUN mvn -B -q -DskipTests package \
    && java -Djarmode=tools -jar target/app.jar extract --layers --launcher --destination /extracted

FROM eclipse-temurin:25-jre
# PeachBot draws its welcome card with Java2D, which needs fontconfig and one font.
RUN apt-get update \
    && apt-get install -y --no-install-recommends fontconfig fonts-dejavu-core \
    && rm -rf /var/lib/apt/lists/*
RUN groupadd --system spring && useradd --system --gid spring --no-create-home spring
WORKDIR /app
COPY --from=build /extracted/dependencies/ ./
COPY --from=build /extracted/spring-boot-loader/ ./
COPY --from=build /extracted/snapshot-dependencies/ ./
COPY --from=build /extracted/application/ ./
USER spring:spring
# A fixed heap, not a share of RAM: Railway's container limit is many GB, so a percentage
# let the heap grow to several GB, all of it billed.
ENV SPRING_PROFILES_ACTIVE=prod \
    PORT=8080 \
    JAVA_OPTS="-Xmx256m -Xss512k -XX:MaxMetaspaceSize=256m -XX:+UseSerialGC -XX:+UseCompactObjectHeaders -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
